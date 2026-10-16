# Relatório técnico: viagem e espaço existentes, e como reaproveitá-los num foguete-entidade (3a, 3b, 3c)

Base de caminhos: `E:/git/Personal/Games/MineMods/SpaceHorizons/src/main/java/com/spacehorizons/`, abreviada para `…/`. Não editei nada no projeto. Os extras (fontes do MC e do NeoForge, um script de contas) ficaram no scratchpad.

Legenda:
- **[C]** confirmado lendo o código (mod, MC 26.3 ou NeoForge 26.3.0.76).
- **[N]** número que calculei a partir das fórmulas confirmadas.
- **[I]** inferência ou proposta minha, que não existe no código.

---

## 0. Resumo

1. **Serve direto para (a) e (b).** `SolarSystem`, `SpaceFrame`, `SphereOfInfluence` e `BodyTransfer` são Java puro, em double, sem estado e testados [C]. Dão posição, velocidade e olhar em qualquer corpo e em qualquer instante do relógio.
2. **O que falta:**
   - Dinâmica de voo. Nada integra forças, atitude ou elementos de Kepler. `FlightPlan` é cinemático: ele escolhe a velocidade por altitude e move a posição [C].
   - Uma "dimensão de espaço" como referencial. Toda a API exige `SolarSystem.Body`, que exige `PlanetDefinition` [C]. Não há conversão planeta↔espaço-como-dimensão.
   - Travessia de veículo. Hoje toda travessia derruba o jogador do veículo (seção 6.3).
3. **Para (c), a vanilla 26.3 já faz o trabalho pesado.** `Entity.teleport(TeleportTransition)` ejeta os passageiros e teleporta cada um com `asPassenger=true`. Depois cria uma entidade nova (`getType().create` + `restoreFrom`) e re-senta todos com `force` [C, `Entity.java` 3177-3243]. O que falta é mandar o `TravelTransferPayload` por passageiro antes da chamada e neutralizar três lugares do mod que derrubam o jogador do veículo.
4. **Cuidado com a Lua.** Ela é cinemática, não kepleriana. Sua velocidade oscila entre 8,5 e 16,7 blocos/tick [N]. Isso decide o referencial recomendado para a simulação (8.1).

---

## 1. Convenções

- **Unidades [C]:** blocos e ticks do jogo, tudo em double.
- **`clock`:** ticks do relógio do Overworld, com fração.
  - No servidor, `SpaceClock.ticks(Level)` devolve `ClockInstance.totalTicks() + partialTick()`. Funciona em qualquer nível.
  - No cliente, `SpaceSun.clock(ClientLevel)` soma o parcial do frame.
  - `/time set` e o relógio pausado afetam sol e Lua.
- **Três sistemas de eixos [C]:**
  - **Espaço:** referencial da Terra. A Terra fica parada na origem com pose identidade. Não há rotação da Terra, então não existe ganho de velocidade tangencial no lançamento.
  - **Fixo no corpo:** cartesiano, com (lat0, lon0) em +X, norte em +Y e leste em +Z.
  - **Local:** eixos do MC num ponto. x = leste, y = cima (radial), z = norte (+lat). No mod +Z é o norte; na vanilla +Z é o sul.
- **Mapeamento esférico verdadeiro (`SpaceFrame`) [C]:** `lon = x/R`, `lat = z/R`, `r = R + (y − seaLevel)`.
  - `toBodyFixed` devolve `(r·cosφ·cosλ, r·sinφ, r·cosφ·sinλ)`.
  - `fromBodyFixed` devolve `x = λ·R` em (−C/2, C/2], `y = r − R + seaLevel` e `z = φ·R`.
  - `toBodyFixed` não precisa de wrap em X. `fromBodyFixed` devolve x já em (−C/2, C/2]; `TravelMath.normalizeX` normaliza para [−C/2, C/2).
- **Constantes do `PlanetDefinition` [C]** (`…/planet/PlanetDefinition.java`, `Planets.java`):

| | Terra | Lua |
|---|---|---|
| `circumferenceBlocks` | 65.536 | 16.384 |
| `radius()` | 10.430,4 | 2.607,6 |
| `seaLevel` | 63 | 63 |
| `maxLatitudeDegrees` | 70 | 75 |
| `surfaceGravity` | 1,0 | 0,165 |

  `surfaceGravity` é uma fração da gravidade vanilla. `PlanetGravity` documenta a base como 0,08 blocos/tick².

---

## 2. SolarSystem (`…/space/SolarSystem.java`)

**Corpos [C]:**
- `SolarSystem.EARTH`: pose constante, `Pose(0,0,0,IDENTITY)`.
- `SolarSystem.MOON`: `motion = SolarSystem::moonPose`.
- `BODIES = List.of(EARTH, MOON)`, fixo no código.
- `bodyOf(PlanetDefinition): Optional<Body>`.

**Tipos e métodos públicos [C]:**
```java
record Pose(double x, double y, double z, double[] rotation)   // rotation: 3x3 em ordem de linhas
  void rotate(double bx,double by,double bz,double[] out)      // eixos do corpo -> espaço (só rotação)
  void unrotate(double vx,double vy,double vz,double[] out)    // espaço -> corpo (transposta)
class Body
  PlanetDefinition planet()
  Pose poseAt(double clock)                       // cache volátil de UM instante
  void positionAt(double clock,double[] out)      // centro do corpo no espaço
  void velocityAt(double clock,double[] out)      // diferença central, h = 0,5 tick, sem cache
  Pose referencePose(); double x(),y(),z()        // configuração de referência (Lua em +X): só testes e contas estáticas
static double moonElongation(double clock), moonAngle(double clock), moonIlluminatedFraction(double clock)
```

**Constantes [C]:**
- `EARTH_MOON_DISTANCE = 60_000`.
- `SYNODIC_PERIOD_TICKS = 8 × 24000 = 192000`.
- `ORBIT_INCLINATION = 5°`.
- `FULL_MOON_TICK = 18000`.

**Órbita da Lua [C]:**
- `moonAngle(clock) = SunModel.angle(clock) + moonElongation(clock)`.
- `moonElongation = −π − 2π·(clock − 18000)/192000`.
- A pose é `R = Rz(5°)·Ry(θ)` e a posição é `D·(cosi·cosθ, sini·cosθ, −sinθ)`. As colunas da rotação são:
  - +X do corpo: direção Terra→Lua.
  - +Y: normal da órbita.
  - +Z: leste.
- A rotação é sincronizada, então o ponto lon=π (x = ±C/2 na Lua) aponta sempre para a Terra.

**Consequências para a simulação:**
- **[C]** `SunModel.angle` usa a Bézier não linear do sol vanilla. Portanto a Lua não segue uma órbita kepleriana. A velocidade do centro oscila: o teste `moonMovesAboutTenToFifteenBlocksPerTick` só afirma `>8` e `<22`, e o README repete "8 a 22".
- **[N]** Pela fórmula, a faixa real é 8,49 a 16,72 blocos/tick, com média de 13,74 e razão máx/mín de 1,97. A aceleração do centro fica entre 0,0012 e 0,0047 blocos/tick². A gravidade da Terra à distância da Lua seria 0,0024. O README (8 a 22) parece citar o limite do teste, não o intervalo real.
- **[C]** O cache de `poseAt` guarda um instante só. Cliente e servidor em threads diferentes com `clock` distintos recalculam. É seguro (volátil, imutável), só não é grátis. Cada `SunModel.angle` faz bisseção de 60 iterações.

---

## 3. SpaceFrame (`…/space/SpaceFrame.java`)

Todas as funções são `public static void`, escrevem em `double[] out` e alocam arrays pequenos internamente [C]. Linhas marcadas "c" têm três sobrecargas: com `Pose`, com `double clock`, ou sem nenhum dos dois (configuração de referência, só para testes).

| Função | O que faz |
|---|---|
| `toBodyFixed(Body, x,y,z, out)` / `fromBodyFixed(Body, bx,by,bz, out)` | dimensão ↔ fixo no corpo, sem tempo |
| `toSpace(Body, Pose\|clock\|—, x,y,z, out)` / `fromSpace(...)` (c) | dimensão ↔ espaço. `fromSpace` devolve x em (−C/2, C/2] |
| `spaceToBodyFixed(Body, clock, sx,sy,sz, out)` / `bodyFixedToSpace(...)` | ponto do espaço ↔ fixo no corpo |
| `vectorToBodyFixed(Body, clock, vx,vy,vz, out)` / `vectorFromBodyFixed(...)` | direção ou velocidade, só rotação |
| `groundVelocity(Body, clock, sx,sy,sz, out)` | velocidade, no espaço, do ponto fixo no corpo que está em (sx,sy,sz) agora |
| `localBasis(Body, x, z, out[9])` | east, up, north (3 doubles cada) nos eixos fixos no corpo |
| `bodyToLocal(Body, camX,camZ, bx,by,bz, out)` / `localToBody(...)` | vetor fixo no corpo ↔ eixos MC do ponto (x,z) |
| `spaceToLocal(Body, Pose\|clock\|—, camX,camZ, vx,vy,vz, out)` / `localToSpace(...)` (c) | vetor do espaço ↔ eixos MC do ponto |
| `meshToLocal(Body observer, double clock, camX,camZ, Body target, double[9] out)` | `basisLocal · R(observer)ᵀ · R(target)`: leva eixos fixos em `target` aos eixos MC do observador |

**Detalhes [C]:**
- `groundVelocity` é a diferença central, com meio passo de 0,5, de `bodyFixedToSpace` em `clock±0,5` para o mesmo ponto fixo no corpo. Vale `centro + ω×r`. É exatamente zero na Terra e coincide com `velocityAt` no centro da Lua; ambos os casos têm teste.
- Funções de vetor (`localToSpace`, `spaceToLocal`, …) só rotacionam; funções de ponto (`toSpace`, `fromSpace`) também transladam.
- `out` não pode ser o array de entrada em `rotate`/`unrotate`.

**Jacobiano posição×velocidade [I], derivado das fórmulas acima:**
- `bodyToLocal` produz velocidade local em unidades verdadeiras (blocos de arco).
- A coordenada x da dimensão anda a `dx/dt = v_leste · R/(r·cosφ)`, `dz/dt = v_norte · R/r` e `dy/dt = v_cima`.
- Em altitude 5.000 no equador, x anda a 0,676 de v_leste. Em lat 70°, o fator cresce por 1/0,342.
- O código existente não aplica esse Jacobiano. O `BodyTransfer` devolve `vx,vy,vz` locais verdadeiros.
- Para `Entity.setDeltaMovement`, use a diferença de coordenadas entre dois ticks.

---

## 4. SphereOfInfluence (`…/space/travel/SphereOfInfluence.java`)

**Assinaturas [C]:**
```java
static double ratio(Body body, double clock, double sx,double sy,double sz)          // |p - centro(clock)| / raio
static Body nearest(List<Body> bodies, double clock, double sx,double sy,double sz)  // sem histerese
static Body dominant(List<Body> bodies, Body current, double clock, double sx,double sy,double sz)
static Body dominant(Body current, double clock, double sx,double sy,double sz)      // usa SolarSystem.BODIES
HYSTERESIS = 0.1   // o candidato precisa ter razão < 0,9 × a do corpo atual
```

**Geometria [N]:**
- A regra "menor distância÷raio" faz o domínio da Lua ser uma esfera de Apolônio. Ela tem raio 16.000 e centro a 64.000 da Terra. Isso vai de 48.000 a 80.000 da Terra sobre o eixo.
- A Lua domina do lado da Terra a 12.000 do centro dela, e do lado de trás a 20.000. A esfera não é centrada na Lua.
- Com histerese, na fronteira Terra→Lua a troca ocorre a 11.020 do centro da Lua, ou 8.412 de altitude. Na volta, a 13.043, ou 10.435 de altitude. Isso bate com o README ("~8.400" e "~10.400").
- Um `/tp` acima de ~38.000 blocos de altitude na Terra transfere o jogador na hora.

---

## 5. BodyTransfer (`…/space/travel/BodyTransfer.java`)

**Assinatura [C]:**
```java
record BodyTransfer(Body target, double x,y,z, double vx,vy,vz, float yRot, float xRot)
static BodyTransfer compute(Body from, Body to, double clock, double x,double y,double z,
                            double vx,double vy,double vz, float yRot, float xRot)
```

**Algoritmo [C]:**
1. `toSpace(from)` seguido de `fromSpace(to)`, com `x = TravelMath.normalizeX(to, ·)`. A posição no espaço é conservada.
2. Velocidade: `v_espaço = localToSpace(from, v_local) + groundVelocity(from)`. No destino, `v_local' = spaceToLocal(to, v_espaço − groundVelocity(to))`.
   - As velocidades do MC são sempre relativas ao chão do corpo.
   - A velocidade no espaço se conserva (há teste).
3. Olhar: vetor de visada e vetor "para cima" da câmera sem rolagem passam pelo espaço (`convert`, privado).
   - O yaw sai de `TravelMath.yawOf(x,z)` e o pitch de `TravelMath.pitchOf(y)`.
   - Se `|horizontal| < 0,02` (~1,1° da vertical), o yaw vem do "para cima" da câmera.

**Limitações para um foguete [C/I]:**
- **[C]** Só traduz Body→Body (Terra↔Lua).
- **[C]** Não tem rolagem, só yaw e pitch.
- **[C]** `convert` é privado.
- **[I]** Faltam a variante para espaço-como-dimensão (8.3) e a conversão da atitude completa (8.3, item 5).

---

## 6. Travessia sem tela de carregamento

### 6.1 Servidor (`…/space/travel/TravelTransfers.java`, `TravelTransferPayload.java`, `TravelServer.java`) [C]

```java
static @Nullable ServerLevel findLevel(MinecraftServer, PlanetDefinition)  // por TIPO de dimensão, via Planets.forLevel
static boolean isReady(ServerPlayer)                                       // connection.hasClientLoaded() && !isChangingDimension()
static boolean transfer(ServerPlayer, BodyTransfer, ServerLevel toLevel)
```

`transfer` faz, na ordem:
1. `PacketDistributor.sendToPlayer(player, new TravelTransferPayload(x, y, z, yRot, xRot))`.
2. `player.teleport(new TeleportTransition(toLevel, pos, vel, yRot, xRot, DO_NOTHING))`. É o construtor de 6 argumentos, então `asPassenger=false`.

O payload é `record TravelTransferPayload(double x,y,z, float yRot,xRot)`. O tipo é `spacehorizons:travel_transfer`, registrado com `event.registrar("1").playToClient(TYPE, CODEC)` e sem handler no servidor.

Dentro de `ServerPlayer.teleport` (26.3, `ServerPlayer.java` 1176-1238) [C]:
1. `CommonHooks.onTravelToDimension` dispara o `EntityTravelToDimensionEvent`, que pode ser cancelado (devolve null).
2. `removeVehicle()` roda se `!asPassenger` (confirmado nas linhas 1189-1191).
3. Se a dimensão for outra:
   - `isChangingDimension = true`.
   - Envio de `ClientboundRespawnPacket(createCommonSpawnInfo(newLevel), (byte)3)`, `ClientboundChangeDifficultyPacket` e o nível de permissão.
   - `oldLevel.removePlayerImmediately(CHANGED_DIMENSION)`, `revive()`, `setServerLevel`.
   - `connection.teleport(...)` envia `ClientboundPlayerPositionPacket` com `awaitingTeleport`.
   - `newLevel.addDuringTeleport(this)`.
   - Em seguida vêm as habilidades, `sendLevelInfo`, `sendAllPlayerInfo`, os efeitos e a ressincronização de attachments do NeoForge.
4. `isChangingDimension` só volta a false em `handleAcceptTeleportPacket` (confirmação do cliente).
   - Enquanto está true, o jogador é invulnerável e `shouldCheckPlayerMovement` devolve false.
   - `TravelTransfers.isReady` também espera essa confirmação.

### 6.2 Cliente (`…/client/travel/TravelClient.java`, `TravelClientState.java`, `…/mixin/travel/ClientPacketListenerMixin.java`) [C]

1. `RegisterClientPayloadHandlersEvent.register(TYPE, handler)` roda na thread principal (`HandlerThread.MAIN`, padrão). O handler faz `TravelClientState.markPending(x,y,z,yRot,xRot)`: guarda os valores, `pending=true`, validade de 3 s.
2. `handleRespawn` HEAD:
   - O mixin chama `consumePending()` e guarda o resultado em `seamless`.
   - `consumePending()` devolve true uma vez, se não expirou.
3. `startWaitingForNewLevel(...)` é cancelado quando `seamless`. Sem `LevelLoadTracker` e sem tela.
4. `handleRespawn` TAIL, se `seamless`:
   - `TravelClientState.applyTo(LocalPlayer)` faz `snapTo(x,y,z,yRot,xRot)`.
   - Depois chama `notifyPlayerLoaded()`.
   - O vanilla põe `clientLoaded=false` em `handleRespawn` (linha 1308). Sem o `notifyPlayerLoaded`, o `LocalPlayer.tick()` e o `sendChanges()` não rodam (linhas 235 e 267).
5. O cliente processa o respawn criando um `ClientLevel` novo e um `LocalPlayer` novo (`ClientPacketListener` 1255-1352). Entidades e chunks do mundo antigo somem.

**Ordem dos pacotes [C]:** como o aviso vai na mesma conexão, a ordem é: payload → `Respawn` → `ChangeDifficulty` → permissões → `PlayerPosition` → abilities/level info/player info → chunks e entidades do nível novo.

### 6.3 O que acontece hoje com veículos e passageiros [C]

- `TravelTransfers.transfer` derruba o jogador do veículo. O veículo fica na dimensão antiga.
- `TravelServer.onPlayerTick` roda `Autopilot.tick`, `FlightBoost.update` e `TravelTransfers.checkSphereOfInfluence` para todo jogador em nível-planeta, sem checar `getVehicle()`.
  - **Efeito:** um passageiro de foguete acima de ~37.600 blocos de altitude na Terra seria transferido sozinho e derrubado do assento. A velocidade usada seria `player.getKnownMovement()`, que não representa o foguete.
  - `FlightBoost` mexe nas `Abilities.flyingSpeed` até de jogador em sobrevivência acima de 800 blocos.
- `PlanetWrap.wrapX` e `enforcePole` usam `player.teleportTo(level, ...)`, que chega em `Entity.teleport` e faz `stopRiding()` (`Entity.java` 3182-3184).
  - **Efeito:** passageiro é derrubado na costura x=±C/2 e na barreira polar.
  - O comentário do código e o README admitem isso. Só jogadores são movidos.
- `PlanetCommands`: `goto` usa `teleportTo` sem payload, então mostra a tela de propósito.
- `Autopilot.start` exige `player.mayFly()`. A cada tick ele manda `connection.teleport(PositionMoveRotation, Relative.ROTATION)`, o que não serve para quem é passageiro.

### 6.4 Pré-carregamento de chunks que o `Autopilot` já usa [C]

- `requestIfMissing`: `level.hasChunk(cx,cz)` e, se faltar, `getChunkSource().addTicketWithRadius(TicketType.PORTAL, new ChunkPos(cx,cz), 0)`.
- `TicketType.PORTAL` tem timeout de 300 ticks e flags 15 (persist, loading, simulation, keep-dimension-active).
- Existe também `ServerChunkCache.addTicketAndLoadWithRadius(TicketType, ChunkPos, int): CompletableFuture<?>`, útil para esperar o carregamento.
- Constantes do `Autopilot`: `GATE_STEPS={1,2,4}`, `REQUEST_STEPS={8,16,32,64}`, `MAX_HELD_TICKS=200`.
- O gate está preso a `Session`; só a ideia é reaproveitável.
- `adjustDistances` muda a view/simulation distance do servidor inteiro via `PlayerList.setViewDistance`. É um efeito global.

---

## 7. FlightPlan, FlightGuide, Autopilot, FlightBoost, TravelMath

**`FlightPlan` (`…/space/travel/FlightPlan.java`) [C]:**
```java
static FlightPlan create(Body frame, List<Body> bodies, double clock, double[] start /*fixo no corpo*/,
                         double initialSpeed, Body targetBody, double tx,double ty,double tz)
boolean advance(double clock)   // um tick; true ao chegar
double[] position(), velocity()  // referencial fixo no corpo
double speed(); boolean isFinished(); List<double[]> waypoints(); Body frame(), targetBody()
boolean targetIsStatic()
```
- O voo é cinemático no referencial fixo no corpo.
- Alvo em outro corpo: o plano faz perseguição com antecipação (`targetInFrame(clock+τ)`, `τ ≤ 20.000` ticks).
- Constantes: `ALTITUDE_SPEED_FACTOR=0,04`, `DISTANCE_SPEED_FACTOR=0,06`, `MIN_SPEED=0,5`, `MAX_SPEED=60`, `ACCELERATION_FACTOR=1,06`, `ACCELERATION_ADD=0,15`, `DETOUR_ALTITUDE=6000`, `ASCENT_ALTITUDE=3000`.
- Os desvios usam `SURFACE_MARGIN=50` de margem sobre os raios. Não há física.

**`FlightGuide` (`…/space/travel/FlightGuide.java`) [C]:**
```java
FlightGuide(Body start, double x,y,z, double clock, Body target, double tx,ty,tz, Predicate<Body> available)
Step tick(double clock, float yRot, float xRot)   // Step(BodyTransfer state, boolean transferred, boolean arrived)
Body body(); FlightPlan plan()
```
- É agnóstico a jogador. Em cada tick devolve a posição já em coordenadas da dimensão do corpo atual (`state.target()`).
- Na troca de corpo (SOI com histerese e `available`) devolve `transferred=true` e o plano novo já refeito no referencial do corpo novo.

**`TravelMath` (`…/space/travel/TravelMath.java`) [C]:**
```java
static void lookVector(float yRot,float xRot,double[] out)   // (-sin yaw·cos pitch, -sin pitch, cos yaw·cos pitch)
static float yawOf(double x,double z); static float pitchOf(double y)
static double normalizeX(Body body,double x)                 // [-C/2, C/2)
static void facingPoint(Body body,double[] out)              // {x,z}: ponto do corpo voltado para o outro corpo; Terra: spawn
static double flightSpeedMultiplier(double altitude)         // só para o voo criativo
```
`MOVEMENT_CHECK_ALTITUDE=128`: acima disso o `ServerGamePacketListenerImplMixin` desliga o "moved too quickly". Isso vale só para movimento do jogador, não de veículo [C]. Nunca foi executado em servidor dedicado.

**Dados de pouso já prontos [C]:**
- `PlanetTerrains.get(planet).surfaceHeight(int x, int z)`, usado pelo `Autopilot`.
- `PlanetMapService.landingY(ServerLevel, int x, int z)`, usado em `goto`.

---

## 8. O que serve para (a), (b) e (c)

### 8.1 (a) Simular posição e velocidade do foguete

**Serve (todos [C]):**
- Efemérides: `Body.positionAt`, `Body.velocityAt` e `Body.poseAt`.
- Conversões: `SpaceFrame.toSpace`/`fromSpace`, `localToSpace`/`spaceToLocal`, `vectorFromBodyFixed`/`vectorToBodyFixed`.
- Velocidade do chão: `SpaceFrame.groundVelocity`.
- Corpo dominante: `SphereOfInfluence.dominant`.
- Parâmetros: `PlanetDefinition.radius()`, `.surfaceGravity()`, `.seaLevel()`.
- Atmosfera: `AtmosphereModel.of(planet).density(alt)` e `.isVacuum(alt)`. A espessura da Terra é 1.800.
- Relógio: `SpaceClock.ticks(level)`.

**Não existe [I]:** integrador, forças (gravidade, empuxo, arrasto), atitude, apoastro/periastro e SAS.

**Referencial recomendado [I], com base nas contas acima:**
- Integre num referencial inercial centrado no corpo dominante e não girante. O estado é `r = P − centro(clock)` e `v = V_espaço − velocidade_do_centro(clock)`. Use `positionAt` e `velocityAt`.
- Na Terra isso é o próprio espaço, sem custo, porque a Terra é imóvel e `groundVelocity` vale 0.
- Na Lua, não integre no referencial fixo no corpo, que gira e orbita. O `FlightPlan` usa esse referencial porque o voo dele é guiado e cinemático.
- Não integre no referencial da Terra com só a gravidade da Lua ligada dentro da SOI dela. A Lua é acelerada de forma prescrita (0,0012 a 0,0047 blocos/tick²) e o foguete não seria. A deriva em relação à Lua apareceria sozinha.
- Na troca de corpo dominante, converta com `r' = P − centro_novo`, `v' = V − velocidade_do_centro_novo`.

**Exemplo [I]:**
```java
static final double G0 = 0.08;  // blocos/tick² (base do Attributes.GRAVITY, ver PlanetGravity)
static double mu(SolarSystem.Body b) { double R = b.planet().radius(); return G0 * b.planet().surfaceGravity() * R * R; }

/** Relativo ao centro do corpo, eixos do espaço (não girante). */
static void relative(SolarSystem.Body b, double clock, double[] pSp, double[] vSp, double[] r, double[] v) {
    double[] c = new double[3], cv = new double[3];
    b.positionAt(clock, c); b.velocityAt(clock, cv);
    for (int i = 0; i < 3; i++) { r[i] = pSp[i] - c[i]; v[i] = vSp[i] - cv[i]; }
}
// passo semi-implícito (dt = 1 tick): v += a·dt; p += v·dt
// a = -mu·r/|r|³ + empuxo/massa + arrasto(AtmosphereModel.density(alt), v rel. ao ar = v_rel na Terra)
// apsides: h = r×v; e = (v×h)/mu - r/|r|; a = -mu/(2·(v²/2 - mu/|r|)); rp = a(1-e); ra = a(1+e); altitude = dist - raio
```

**Números de calibração [N]**, com `GM = g0·R²`:

| Grandeza | Valor |
|---|---|
| GM Terra | 8,70e6 |
| GM Lua | 8,98e4 |
| Velocidade circular na superfície da Terra | 28,9 blocos/tick (578 blocos/s) |
| Velocidade de escape na superfície da Terra | 40,9 blocos/tick |
| Velocidade circular a 5.000 de altitude | 23,75 blocos/tick (período ~4.082 ticks) |
| Velocidade circular na distância da Lua | 12,04 blocos/tick |
| Velocidade circular na superfície da Lua | 5,87 blocos/tick |
| Hohmann, órbita a 5.000 até 60.000 (só referência) | dv ≈ 6,2 blocos/tick, ~7.800 ticks |

A Hohmann acima ignora que a Lua oscila de 8,5 a 16,7 blocos/tick; é só referência de escala. Outros pontos:
- O ritmo de 24 blocos/tick em órbita equivale a ~1,5 chunk por tick. Isso é inviável na dimensão do planeta (o `Autopilot` já sofre com geração de chunk) e justifica a dimensão de espaço sem worldgen [I].
- `/time set` desloca a Lua de repente. Use o `clock` só para as poses e um `dt` fixo de 1 tick para integrar. Com relógio pausado a Lua congela [C, doc de `SpaceClock`; comportamento do integrador é [I]].

### 8.2 (b) Posição de entidade na dimensão a cada tick

**Serve [C]:** `SpaceFrame.fromSpace(body, clock, P, out)`, `TravelMath.normalizeX`, `SpaceFrame.localBasis` e `bodyToLocal`, `TravelMath.yawOf` e `pitchOf`.

**Exemplo [I]:**
```java
double clock = SpaceClock.ticks(level);
double[] d = new double[3];
SpaceFrame.fromSpace(body, clock, P[0], P[1], P[2], d);
double x = TravelMath.normalizeX(body, d[0]), y = d[1], z = d[2];
double dx = TravelMath.normalizeX(body, x - rocket.getX());   // diferença com wrap, para deltaMovement
Vec3 delta = new Vec3(dx, y - rocket.getY(), z - rocket.getZ());
if (Math.abs(x - rocket.getX()) > body.planet().halfCircumference()) {
    // costura: Entity.teleportTo(ServerLevel, x, y, z, Relative.union(Relative.DELTA, Relative.ROTATION), yRot, xRot, false)
    // vai por teleportSameDimension, que leva os passageiros junto
} else {
    rocket.setPos(x, y, z);
}
rocket.setDeltaMovement(delta);
```
- Atitude: derive yaw/pitch do eixo "frente" do foguete nos eixos locais com `yawOf` e `pitchOf`. A rolagem vai em dado sincronizado próprio. Mesmo com atitude inercial constante, o yaw e o pitch locais variam com a posição (a base local gira) [I].
- A entidade não tem gravidade (`PlanetGravity` só atua em `LivingEntity`), então toda a física é do foguete.

**Pontos sensíveis [C/I]:**
- **Polos.** Perto dos polos o mapeamento é degenerado (`atan2` do longitude). Também não há barreira para entidades (`enforcePole` é só para jogadores; clamp em `maxLatitudeBlocks−2`). Em atmosfera, limite a latitude [I].
- **Costura.** Não há wrap automático de entidade; é preciso fazer na simulação [C, README e `PlanetWrap`].
- **Rastreamento.** A distância usada em `ChunkMap.TrackedEntity.updatePlayer` é horizontal (x e z) e o alcance considera o `clientTrackingRange` dos passageiros [C].
- **Movimento sincronizado.** O foguete deve ser servidor-autoritativo: `getControllingPassenger()` deve devolver null. O default de `Entity` já devolve null; com null, `isLocalInstanceAuthoritative()` e `canSimulateMovement()` não dão autoridade ao cliente [C, `Entity.java` 3728-3752].

### 8.3 (c) Trocar o foguete e o passageiro de dimensão sem tela de carregamento

**Serve sem mudança [C]:**
- `TravelTransferPayload`, `TravelClient`, `TravelClientState` e `ClientPacketListenerMixin`. O mixin só se baseia no payload, não em quem é o jogador.
- `Entity.teleport(TeleportTransition)` do vanilla, que já leva os passageiros (passos descritos abaixo).

**Sequência de `Entity.teleportCrossDimension` (`Entity.java` 3209-3243) [C]:**
1. Ejeta os passageiros (`ejectPassengers`).
2. Para cada passageiro, chama `passenger.teleport(calculatePassengerTransition(...))`. É `ServerPlayer.teleport` com `asPassenger=true`, e portanto manda o Respawn.
3. Cria `newEntity = getType().create(newLevel, DIMENSION_TRAVEL)`.
4. `newEntity.restoreFrom(this)`, que faz `saveWithoutId` → `load`.
5. `removeAfterChangingDimensions()`, `teleportSetPosition(...)` e `newLevel.addDuringTeleport(newEntity)`.
6. Re-senta cada passageiro com `startRiding(newEntity, true, false)`.
7. Chama `transition.postTeleportTransition().onTransition(newEntity)`.

**Efeito dos passos [C]:**
- A ordem difere da descrição do plano: o vanilla remove a entidade antiga antes de adicionar a nova.
- O estado inteiro do foguete tem de viver no save/load da entidade (`restoreFrom`).
- `startRiding` exige `EntityType.canSerialize()` no servidor.
- O `ServerEntity.sendChanges` envia o `ClientboundSetPassengersPacket` depois que o passageiro volta a sentar.
- `calculatePassengerTransition` posiciona cada passageiro por `posição_do_foguete_destino + (posição_do_passageiro − posição_do_foguete)`, com o deslocamento no referencial antigo. Para um assento que gira com a atitude, isso só vale como posição provisória; o `positionRider` corrige depois.
- Se `getType().create` devolver null, a função devolve null depois de os passageiros já terem sido teleportados e com o foguete ainda na dimensão antiga. Passageiros ficam presos sem o foguete. Pré-valide.
- O NeoForge dispara `EntityTravelToDimensionEvent` (cancelável) para o foguete e para cada passageiro.

**Código de exemplo [I]:**
```java
public static boolean transferRocket(RocketEntity rocket, ServerLevel to, SpaceTransfer.BodyState t, float yRot, float xRot) {
    to.getChunkSource().addTicketWithRadius(TicketType.PORTAL,
            ChunkPos.containing(BlockPos.containing(t.x(), t.y(), t.z())), 2);
    for (Entity e : rocket.getIndirectPassengers()) {
        if (e instanceof ServerPlayer p) {
            // posição do ASSENTO no destino (não a do foguete); mesma conexão => o aviso chega antes do Respawn
            PacketDistributor.sendToPlayer(p, new TravelTransferPayload(seatX, seatY, seatZ, p.getYRot(), p.getXRot()));
        }
    }
    TeleportTransition tt = new TeleportTransition(to, new Vec3(t.x(), t.y(), t.z()),
            new Vec3(t.vx(), t.vy(), t.vz()), yRot, xRot,
            TeleportTransition.DO_NOTHING.then(e -> ((RocketEntity) e).onArrived()));
    return rocket.teleport(tt) != null;
}
```
A atitude completa (rolagem inclusive) precisa ser refeita no frame novo em `onArrived`. A conversão pode ser composta das funções existentes:
```java
// eixo a eixo: localToSpace(A, clock, xA, zA, eixoA, tmp) -> spaceToLocal(B, clock, xB, zB, tmp, eixoB)
// equivale a v_B = meshToLocal(B, clock, xB, zB, A) · localToBody(A, xA, zA, v_A)
```

**Conversão planeta↔espaço [I]:** o espaço-como-dimensão tem a mesma matemática do `BodyTransfer`, só que sem o segundo `Body`. Esboço, com o mesmo núcleo do `BodyTransfer`:
```java
final class SpaceTransfer {
    static final double[] OFFSET = {0, 20_000, 0}; // valor a decidir, ver 9.1
    record State(double x,double y,double z,double vx,double vy,double vz) {}      // espaço, velocidade ABSOLUTA
    record BodyState(double x,double y,double z,double vx,double vy,double vz) {}  // dimensão do corpo, velocidade local relativa ao chão

    static State fromBody(Body from, double clock, double x,double y,double z, double vx,double vy,double vz) {
        double[] p = new double[3]; SpaceFrame.toSpace(from, clock, x, y, z, p);
        double[] v = new double[3]; SpaceFrame.localToSpace(from, clock, x, z, vx, vy, vz, v);
        double[] g = new double[3]; SpaceFrame.groundVelocity(from, clock, p[0], p[1], p[2], g);
        return new State(p[0]+OFFSET[0], p[1]+OFFSET[1], p[2]+OFFSET[2], v[0]+g[0], v[1]+g[1], v[2]+g[2]);
    }
    static BodyState toBody(Body to, double clock, State s) {
        double sx = s.x()-OFFSET[0], sy = s.y()-OFFSET[1], sz = s.z()-OFFSET[2];
        double[] l = new double[3]; SpaceFrame.fromSpace(to, clock, sx, sy, sz, l);
        double x = TravelMath.normalizeX(to, l[0]);
        double[] g = new double[3]; SpaceFrame.groundVelocity(to, clock, sx, sy, sz, g);
        double[] v = new double[3]; SpaceFrame.spaceToLocal(to, clock, x, l[2], s.vx()-g[0], s.vy()-g[1], s.vz()-g[2], v);
        return new BodyState(x, l[1], l[2], v[0], v[1], v[2]);
    }
}
```
Teste unitário natural: planeta A → espaço → planeta B deve igualar `BodyTransfer.compute(A, B, ...)` (mesmo estilo de `BodyTransferTest`).

**Guardas obrigatórios no código existente [C, com a correção em [I]]:**
- `TravelServer.onPlayerTick`: antes de `Autopilot`, `FlightBoost` e `checkSphereOfInfluence`, retornar se `player.getVehicle() instanceof RocketEntity`.
- `PlanetWrap.onLevelTick`: não chamar `wrapX` nem `enforcePole` para passageiros. O foguete faz o próprio wrap pela entidade-raiz.
- `Autopilot.start`: recusar, ou delegar ao módulo de computador de bordo, quando o jogador estiver num foguete.

**Corpos no céu da dimensão de espaço [C/I]:**
- `BodyRenderer.renderInternal` retorna se `Planets.forLevel(level)` está vazio, e `PlanetMeshRenderer` também reseta. Nenhum renderizador funciona na dimensão de espaço sem mexer neles.
- `BodySky.fromSpacePoint(Body target, double clock, sx, sy, sz)` já existe e é testado (`BodySkyTest`). Hoje não é usado em jogo, só nos testes. Devolve direção, distância e raio angular nos eixos do espaço.
- Para o espaço-como-dimensão: a rotação da malha de cada corpo é só `Pose.rotation` do corpo-alvo (os eixos da câmera são os do espaço), sem `meshToLocal`. O sol vem de `SunModel.directionInSpace(clock, out)`. Os dois corpos entram como "outros", sem o caso `self`.

---

## 9. Riscos e perguntas em aberto

### 9.1 Dimensão de espaço
1. **Abaixo do mundo.** `Entity.checkBelowWorld` roda com `y < minY − 64`. Para entidades comuns, `onBelowWorld` faz `discard()`. Para `LivingEntity` (jogador) causa dano `fellOutOfWorld` de 4 por tick [C, `Entity.java` 609-613 e 684, `LivingEntity.java` 2278]. Coordenadas do espaço têm y negativo grande (a Terra ocupa y ∈ ±10.430 se a origem for o centro dela). Opções [I]:
   - Deslocar a origem em y (`OFFSET`).
   - Sobrescrever `checkBelowWorld` no foguete e cancelar o dano `FELL_OUT_OF_WORLD` do jogador na dimensão de espaço (evento `LivingIncomingDamageEvent`).
   - `DimensionType` limita `min_y` a [−2032, 2031] [C]; não resolve ±60.000.
2. **Chunks vazios.** Entidades só têm tick e rastreamento em chunks carregados. A dimensão de espaço precisa de chunks vazios gerados, e a geração é barata mas não é zero (gerador de vazio). Pergunta: quantos chunks por tick a 24 blocos/tick? [I]
3. **Mundos antigos.** As dimensões existentes vêm de `data/spacehorizons/worldgen/world_preset/earth.json`. Como a dimensão de espaço entra em mundos já criados com o tipo Space Horizons? Não verifiquei como o 26.3 trata `dimension/*.json` de datapack em mundos existentes.
4. **Lookup.** `TravelTransfers.findLevel` busca pelo tipo de dimensão via `Planets.forLevel`. A dimensão de espaço precisa de um equivalente (tipo próprio) [I].

### 9.2 Transições
5. **Entrada em dimensão de planeta.** A 5.000 de altitude, a velocidade circular na Terra é 23,75 blocos/tick. Sem frear antes, a dimensão do planeta teria que gerar chunks a ~1,5 chunk/tick. O `Autopilot` precisa de gate e reduz distâncias por causa disso. A política de troca (limite de velocidade horizontal, altitude) é decisão de projeto [I].
6. **SOI vs. altitude.** A SOI atual é muito acima de 5.000 (altitude ~37.600 na Terra, ~9.400 na Lua). Na dimensão de espaço a SOI só escolhe o corpo da gravidade; a troca para a dimensão do planeta deve ser por altitude e velocidade [I].
7. **Falha no meio da travessia.** Vide 8.3 (`create` devolvendo null). Pré-valide `getType().create` e `canSerialize` [C/I].
8. **Vários passageiros.** Cada jogador precisa do próprio payload com a posição do assento no destino. Se o assento gira com a atitude, a posição do passageiro difere do `calculatePassengerTransition` [I].
9. **Relógio no primeiro quadro.** O README admite que o relógio do cliente novo pode ainda não ter chegado no primeiro quadro depois da troca. Para a Lua desenhada na dimensão de espaço isso pode errar a pose por um quadro [C, README].

### 9.3 Física e unidades
10. **Velocidade do chão.** `BodyTransfer` e o `Autopilot` usam velocidade local verdadeira. Para `deltaMovement` de entidade use o Jacobiano da seção 3 ou a diferença de coordenadas [I].
11. **Número da Lua.** README/teste "8 a 22" vs [N] 8,5 a 16,7. Vale um teste que fixe o intervalo real antes de calibrar manobras.
12. **Servidor dedicado.** Nunca executado (README). O mixin `ServerGamePacketListenerImplMixin` e o fluxo de pacotes só foram exercitados em singleplayer.
13. **Entrada do piloto.** O cliente vanilla já manda `ServerboundPlayerInputPacket(Input)` enquanto o jogador é passageiro (`LocalPlayer.sendChanges`). O servidor guarda em `ServerPlayer.getLastClientInput()`: `Input(forward, backward, left, right, jump, shift, sprint)` [C]. Um pacote próprio ainda serve para acelerador contínuo e SAS.

### 9.4 Perguntas para decidir antes de implementar
- A origem e o offset da dimensão de espaço (item 1).
- Se o foguete pousa e decola da Lua em coordenadas fixas no corpo (preso ao chão) e só vira estado inercial ao decolar [I].
- Quem faz o wrap em X do foguete: o próprio tick da simulação (recomendado) ou um evento de nível.
- Como a atmosfera da Terra entra no arrasto: `AtmosphereModel.density` vai de 1 a 0 entre 160 e 1.800 blocos de altitude [C], bem abaixo dos 5.000 da troca.