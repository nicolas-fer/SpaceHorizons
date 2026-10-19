# Desenho técnico do foguete (etapas 3a, 3b, 3c)

Base: `docs/PLANO_SOBREVIVENCIA.md` e as pesquisas em `docs/pesquisa/foguete-*.md` (1: viagem existente, 2: render do retrato, 3: veículo e entrada, 4: dimensão de espaço, 5: física orbital). Este documento fecha as decisões e define os **contratos** entre as peças, para que partes diferentes possam ser feitas em paralelo.

## 1. Decisões

| Tema | Decisão |
|---|---|
| Autoridade | Servidor autoritativo. O piloto é o `getControllingPassenger()`, mas o foguete sobrescreve `isClientAuthoritative()`/`isLocalClientAuthoritative()` para `false`. Cliente interpola (`LinearInterpolationHandler`), atitude sincronizada. |
| Física | Patched conics: estado sempre **relativo ao corpo dominante**, em eixos INERCIAIS (referencial **I**, ver a linha "Referenciais"). A Lua tem órbita kepleriana circular em I (`inertialPoseAt`), nunca integrada. Velocity-Verlet (KDK), dt = 1 tick. |
| Referenciais | **I** (inercial): o sol fixo (em −X), a Terra gira para o leste; é onde o foguete é integrado e a Lua orbita (raio 600.000, 5°, período `2π·sqrt(a³/μ)` = 98.983 ticks, ~4,12 dias, prógrada, 38,09 b/t). **E** (preso à Terra): o dos lugares (dimensões, `SpaceDimension`, render, jogador, piloto automático criativo); a Terra fica parada e o sol dá uma volta por dia. Ponte: `space/InertialFrame`, `r_E = Ry(φ)·r_I` com `φ = ângulo_do_sol − π` (o sol de E continua EXATAMENTE o de `SunModel`; em `clock = 18.000` E = I). A taxa `φ̇` é a do sol do vanilla (Bézier): 0,67 a 1,19 vez a média 2π/24.000, então a velocidade do chão no equador varia de 18,2 a 32,5 b/t (média 27,3). Velocidade: `v_E = Ry(φ)·v_I + φ̇·Ry'(φ)·r_I`. O estado de voo vive em I; a posição na dimensão vem de I→E a cada tick (`FrameBridge`). |
| Gravidade | `mu = G0 · surfaceGravity · R²`, `G0 = 0,08 b/t²`. Terra 8,7034e8, Lua 8,9754e6 (Terra 10x maior: R ≈ 104.304, Lua R ≈ 26.076). |
| Esfera de influência (física) | Laplace: 96.270 blocos da Lua (distância Terra–Lua 600.000), histerese 5% (entra a 91.457, sai a 101.085). A regra antiga (`SphereOfInfluence`) continua só para o autopiloto criativo. |
| Inclinação/painel | Polo de referência `P = −norte` do corpo, em I (os eixos fixos no corpo são de mão esquerda). Órbita para leste = 0°. A Lua orbita para **leste** em I (prógrada, 5° de inclinação): o planejador manda lançar para leste, o sentido mais barato (a pista já tem ~27 b/t para leste). |
| Zonas | Dimensão do planeta até a altitude de saída (`5000 · R/R_Terra`: Terra 5.000, Lua 1.250). Acima disso, dimensão `spacehorizons:space`. Volta ao corpo abaixo de `0,8 ×` a altitude de saída **e só com velocidade horizontal ao chão ≤ 25 b/tick** (`rocket/ZoneSwitch`); entrada forçada a ≤ 1.000 sobre o terreno (`0,25 ×` a altitude de entrada) com qualquer velocidade. Saída antecipada (≥ 1.000 de altitude, `0,2 ×` a de saída) quando a horizontal passa de 40 b/tick (histerese 25/40). Passagens sem tela de carregamento (payload existente). |
| Dimensão de espaço | Tipo `spacehorizons:space`, gerador `minecraft:flat` vazio, `height 16`, `min_y 0`, `has_skylight true`, `has_ceiling true`, `skybox overworld`, sem nuvens/chuva, bioma sem spawns. Eixos: `(x,y,z)_dim = (sz, sx + 20000, sy)` (base local do spawn da Terra + viés de Y). Registrada no world preset (mundos novos; sem ela o foguete não sai da dimensão do planeta e avisa). |
| Chunks | Tickets `ENDER_PEARL` à frente do foguete (1, 2, 4, 8, 16, 32 ticks; raio 2 nos dois primeiros, 0 nos outros) e **gating** (segura o passo da simulação se faltar chunk até 4 ticks à frente, teto de 200 ticks com aviso). `chunkSource.move(pilot)` a cada tick. `isAlwaysTicking() = true`. Nos passos até 4 ticks os tickets e o portão cobrem a **pegada** (caixa do foguete + 2 blocos), não só o chunk central: o vanilla testa os chunks da caixa do foguete e do piloto com `getChunk` e bloqueia esperando se algum ainda gera. Bordas de ação (pulso do SAS) de um tick segurado são devolvidas, senão se perdiam. |
| Render | Tier 1: retrato assado uma vez (BFS de luz, culling com level falso) e reproduzido por `submitCustomGeometry` nos RenderTypes `*MovingBlock` (curvatura automática do shader). Tier 2 (VBO próprio + colocação rígida) só se os prints mostrarem problema. |
| Atitude | Quaternion **inercial** no servidor (SAS e manobras triviais). Para render/câmera o servidor sincroniza o quaternion **local da dimensão** (`EntityDataSerializers.QUATERNION`). |
| Combustível | `ve = Isp · G0`, Isp do motor básico 1.600 ticks (ve = 128 b/t; a velocidade orbital da Terra 10x maior sobe √10, de 28,9 para 91,3 b/t, e o Isp sobe junto para manter a mesma folga de Δv). Combustível 0,8 t por 1.000 mB. Massas: cockpit 0,8 t, motor 0,6 t, tanque 0,2 t seco (4.000 mB), paraquedas 0,1 t, perna 0,1 t, outros blocos 0,1 t. Empuxo do motor 2,5 t·b/t². |
| Arrasto | `v ← v / (1 + k0·ρ·|v|)` com `ρ = AtmosphereModel.density(h)`; `k0 = G0/vt²`; vt do foguete 16, com paraquedas aberto 0,5. |
| Reentrada | Aquecimento só visual e aviso no painel nesta versão (sem dano). |
| Pouso | Contato quando a base do foguete encosta no chão. Pouso suave se `|v_vertical| ≤ 1,0 b/t` (20 b/s), `|v_horizontal ao chão| ≤ 0,5 b/t` e inclinação ≤ 15°; senão batida. |
| Batida | O foguete se desfaz: os blocos caem como itens no ponto da batida (sem explosão de terreno), o piloto toma dano proporcional à velocidade (como queda). |
| Desmontagem | Só pousado, de pé, com o comando "desmontar" do piloto: yaw arredondado a 90°, blocos recolocados em dois passes; recusa se o espaço estiver ocupado por bloco não substituível. |
| Paraquedas | Bloco `rocket_parachute`; abre manualmente (tecla) só com `ρ > 0` (atmosfera) e `|v| < 8 b/t`, rampa de 80 ticks. |
| Teclas (padrão) | W/S arfagem, A/D guinada, Q/E rolagem, Shift/Ctrl acelerador ±, Z cheio, X corta, T SAS (desligado → estabilizar → prógrado → retrógrado), P paraquedas, G desmontar (pousado), V câmera. Shift **não** desmonta dentro do foguete. |
| Painel (HUD) | Altitude, velocidade vertical e horizontal (ao chão), velocidade orbital, apoastro/periastro (altitudes), inclinação, combustível (mB e Δv restante), TWR, acelerador, modo SAS, corpo dominante, aviso de calor, e o "planejador" da Lua (ângulo de fase atual vs. ideal e a indicação "queime agora para leste"). A velocidade vertical e a horizontal são relativas ao CHÃO (que gira); a velocidade orbital é a inercial (em I). |

## 2. Pacotes e contratos

### 2.1 `com.spacehorizons.space.flight` (física pura, Java sem Minecraft além de `SolarSystem`, `SpaceFrame`, `PlanetDefinition`, `AtmosphereModel`)

```java
public record V3(double x, double y, double z)               // plus, minus, times, dot, cross, length, unit, ZERO
public final class FlightConstants { G0 = 0.08; FUEL_T_PER_MB = 0.0008; SOI_HYSTERESIS = 0.05; }
public record BodyPhysics(SolarSystem.Body body, double mu, double radius, double soiRadius, double exitAltitude)
    static BodyPhysics of(SolarSystem.Body b); gravityAt, circularSpeed, escapeSpeed, period, altitude(r)
public record Orbit(... a, e, inclination, trueAnomaly, periapsis, apoapsis, period, bound)
    static Orbit fromState(double mu, V3 r, V3 v, V3 pole); timeTo(nu); trueAnomalyAtRadius(r)
public final class Tsiolkovsky { deltaV, finalMass, burnTime }
public record RocketStats(double dryMassT, double thrust, double ispTicks, double fuelCapacityMb,
                          double dragK0, double parachuteK0, int engines, int tanks, int parachutes, int legs)
    double exhaustVelocity(); double wetMass(double fuelMb); double deltaV(double fuelMb); double twr(double fuelMb, double g)
public record FlightState(SolarSystem.Body body, V3 r, V3 v, double fuelMb, double heat)   // relativo ao corpo, eixos do espaço
public record Control(double throttle, V3 thrustDirection, boolean parachute)            // direção inercial unitária
public final class FlightIntegrator { static FlightState step(FlightState s, Control c, RocketStats st, double clock); }
public final class SoiTracker { static FlightState update(FlightState s, double clock); }  // Laplace + histerese
public final class FrameBridge {
    record Local(double x, double y, double z, double vx, double vy, double vz)        // dimensão do corpo; v métrica local
    static Local toDimension(SolarSystem.Body b, double clock, V3 r, V3 v);
    static FlightState fromDimension(SolarSystem.Body b, double clock, Local l, double fuelMb);
    static V3 spaceDimToSpace(double x, double y, double z); static double[] spaceToSpaceDim(V3 abs);  // SpaceDimension
}
public final class Zones { exitAltitude(planet); boolean shouldLeave(...); @Nullable Body shouldEnter(clock, absPos) }
public final class AttitudeMath { // quaternions inerciais <-> local da dimensão; SAS (prógrado/retrógrado/estabilizar)
    static Quaternionf inertialToLocal(Body b, double clock, double x, double z, Quaternionfc inertial); ... }
public final class TransferPlanner { // Lua: ângulo de fase atual e ideal, Δv de TLI, "queime agora" (janela ±)
    static Plan moon(FlightState s, double clock); }
public final class LandingRules { static Outcome contact(double vVertical, double vHorizontal, double tiltDeg); }
```

### 2.2 `com.spacehorizons.rocket` (conteúdo e entidade; comum)

- Blocos (em `ContentIds` e na tag `spacehorizons:rocket_parts`): `launch_pad`, `rocket_cockpit` (assento; ponto de montagem e de câmera), `rocket_engine` (aponta para baixo do foguete), `rocket_fuel_tank` (guarda mB; enche com galão por clique), `rocket_parachute`, `rocket_landing_leg`, `rocket_hull` (casco de aço). Qualquer outro bloco ligado também entra no retrato como estrutura (massa padrão), exceto a tag `spacehorizons:rocket_forbidden` (bedrock, blocos com dureza −1, fluidos).
- `RocketAssembly`: BFS a partir do cockpit por faces, para nos blocos `launch_pad` e no ar; teto de 1.024 blocos e 100.000 iterações; valida tudo antes de remover (exceções tipadas com mensagem traduzível: sem motor, sem tanque, sem cockpit, grande demais, encostado em terreno, bloco proibido, chunk não carregado, pacote grande demais).
- `RocketPortrait`: retrato denso (tamanho X·Y·Z, paleta de BlockState, NBT de block entities sem x/y/z), `id` estável (UUID), NBT com paleta e `Pos` em long, codec de rede compactado. `RocketStats` calculado a partir do retrato.
- Remoção do mundo: dois passes (presos primeiro), flags `UPDATE_MOVE_BY_PISTON|UPDATE_SUPPRESS_DROPS|UPDATE_KNOWN_SHAPE|UPDATE_CLIENTS|UPDATE_IMMEDIATE`, um passe final de `markAndNotifyBlock`. Reposição: dois passes inversos, `state.rotate`, NBT com x/y/z de destino, flag contra execução dupla.
- `RocketEntity extends Entity implements IEntityWithComplexSpawn`:
  - Registro: `MobCategory.MISC`, `clientTrackingRange(16)`, `updateInterval(1)`.
  - Dados sincronizados: `ATTITUDE` (`QUATERNION`, local da dimensão), `THROTTLE` (float), `FUEL_MB` (float), `FLIGHT_MODE` (byte: `LANDED`, `FLIGHT`, `PARACHUTE`), `SAS_MODE` (byte), `HEAT` (float). O retrato vai no spawn data.
  - Salvo inteiro em `addAdditionalSaveData`/`readAdditionalSaveData` (retrato, estado de voo inercial, combustível, atitude inercial, modos), porque a troca de dimensão recria a entidade por NBT.
  - Overrides obrigatórios: `isClientAuthoritative/isLocalClientAuthoritative = false`, `isAlwaysTicking = true`, `isNoGravity = true`, `isFlyingVehicle = true`, `getKnownMovement = getDeltaMovement()`, `getControllingPassenger` = jogador no cockpit, `createInterpolationHandler` linear, `getDimensions` pelo retrato, `positionRider` pelo cockpit girado, `getDismountLocationForPassenger` seguro.
  - API para a física (etapa 3b): `RocketFlight flight()` (estado de voo e controle no servidor), `void applyControl(RocketControlPayload)`, `RocketPortrait portrait()`, `RocketStats stats()`.
- `RocketControlPayload(float pitch, float yaw, float roll, float throttle, byte flags)` cliente→servidor (bits: 0 SAS próximo modo, 1 paraquedas, 2 desmontar, 3 acelerador cheio, 4 corta). Enviado ao mudar e a cada 5 ticks; o servidor zera comandos sem pacote há 10 ticks.

### 2.3 Cliente

- `client/rocket`: `RocketRenderer` (Tier 1), `PortraitMesh`, `PortraitLevel`, `RocketMeshCache`; controles (`RocketKeys`, mascarar Shift/Ctrl em `MovementInputUpdateEvent`), câmera (`ViewportEvent.ComputeCameraAngles` com o quaternion local + `CalculateDetachedCameraDistanceEvent`), `RocketHud`.
- **Cliente do piloto (fase B2, implementado)**: `RocketKeys` (Q/E, Z, X, T, P, G, V na categoria "Space Horizons — Foguete"; contexto próprio, só valem pilotando), `RocketPilot` (lê as teclas, mantém o acelerador, manda o `RocketControlPayload`: ao mudar e a cada 5 ticks, flags de borda uma vez por aperto; mascara Shift/sprint), `RocketCamera` + `CockpitCamera` (câmera pela atitude interpolada; conta pura e testada), `RocketPanel` + `RocketPanelModel` (HUD; unidades em b/s), `RocketEffects` (chama, fumaça, reentrada, som). **Sentidos dos comandos que o servidor deve aplicar** (eixos do corpo, `AttitudeMath.rotateBody`): `pitch > 0` (W) é rotação de mão direita em torno de +X (o nariz vai para +Z), `yaw > 0` (D) em torno de +Z (o nariz vai para −X), `roll > 0` (E) em torno de +Y (o nariz); o "cima" da tela do cockpit é −Z do retrato. O acelerador vai em `throttle` (0 a 1, já integrado no cliente, Shift/Ctrl ±0,02 por tick); `FLAG_FULL_THROTTLE`/`FLAG_CUT_THROTTLE` acompanham um `throttle` já ajustado. `FLAG_DISASSEMBLE` só sai com o foguete em `LANDED` (o cliente avisa e não manda em voo).
- `client/space`: `SkyObserver` (planeta ou espaço) usado pelo `BodyRenderer`; `SpaceSkyClient` (sol/estrelas/fog no espaço; lua vanilla cancelada).

### 2.4 Guardas no código existente

- `TravelServer.onPlayerTick`: pular jogador com `getVehicle() instanceof RocketEntity`.
- `PlanetWrap`: não mover passageiros; o foguete faz o próprio wrap em X (teleporte na mesma dimensão leva o passageiro).
- `Autopilot.start`: recusar dentro de foguete.
- `PlanetGravity`: 0 no espaço. Oxigênio/compressor: espaço é vácuo.

## 3. Ordem de implementação

1. **Fase A (paralela):** A1 física pura (`space.flight`) com testes; A2 foguete de blocos (conteúdo, montagem, retrato, entidade, render Tier 1, montar/desmontar, protótipo 3a-1 no autoteste); A3 dimensão de espaço e céu de dentro dela (dimensão, `SkyObserver`, `BodyRenderer`, gravidade/oxigênio no espaço, comando de teste, autoteste com prints).
2. **Fase B (paralela, depois de juntar A):** B1 voo no servidor (controle de voo na entidade usando A1, combustível, gating de chunks, pouso/batida/paraquedas, passagens de zona entre dimensões com passageiro, guardas); B2 cliente (teclas, payload, câmera, HUD, chama e som do motor).
3. **Fase C:** revisão adversarial e autoteste de missão completa (montar, abastecer, decolar, órbita, transferência, pouso na Lua, volta, pouso na Terra) com um piloto de teste automático no autoteste.

## 4. Voo no servidor (fase B1): decisões e contratos

- **Máquina de estados pura** (`rocket/FlightMachine`): `LANDED → FLIGHT → PARACHUTE → pouso suave | batida`, função de passo sobre um `Snapshot` imutável (modo, `FlightState` inercial, atitude inercial, giro, SAS, acelerador, pedido de paraquedas, zona). Quem chama confirma ou descarta o resultado (por exemplo, segura o passo esperando chunk do destino). A cola com o mundo está em `RocketFlightController`; o estado, o comando e o NBT em `RocketFlight`.
- **Decolagem**: pousado só sai do chão com empuxo × acelerador > gravidade local (TWR efetivo > 1). O estado inercial nasce de `FrameBridge.fromDimension` (inclui ω×r: na Terra ~27 b/t para leste no equador, vezes cos(latitude)) e a atitude local vira inercial (em I). Pouso suave, batida, "velocidade horizontal ao chão", arrasto e aquecimento usam a velocidade RELATIVA ao chão/ar, que gira com o corpo (`FrameBridge.rotationVelocity`). Com `/time set` o relógio salta e o giro E↔I também: o estado de voo, guardado em I, não muda e só a posição E é re-derivada (um foguete em voo pode ver o chão "pular"); veja o README.
- **Atitude** (`AttitudeControl`): o comando é a TAXA de giro nos eixos do corpo (arfagem +X, guinada +Z, rolagem +Y, os sinais de `AttitudeMath.rotateBody`), máx. 0,04 rad/tick e aceleração 0,01 rad/tick². Sem SAS o giro persiste; ESTABILIZAR zera os eixos sem comando; PRÓGRADO/RETRÓGRADO apontam o nariz (velocidade relativa ao chão na dimensão do corpo, orbital no espaço) quando o piloto não comanda.
- **Comando**: os bits de ação (próximo SAS, desmontar) valem na BORDA de subida; o paraquedas fica ARMADO até poder abrir (atmosfera e |v| < 8 b/t). O acelerador é o que o cliente manda (0..1); os bits cheio/corta são informativos.
- **Zonas**: `Zones.shouldLeave/shouldEnter`; a troca leva o foguete COM o piloto por `rocket.teleport(TeleportTransition)`, avisando o piloto antes (`TravelTransferPayload` com a posição do assento no destino, sem tela de carregamento). O instantâneo novo é gravado ANTES do teleporte (a entidade é recriada por NBT). A entidade recém-chegada fica parada até o piloto estar pronto (`TravelTransfers.isReady`) e rastrear o foguete (teto de 100 ticks). Sem a dimensão de espaço no mundo, o foguete não troca e o piloto é avisado uma vez.
- **Chunks** (`ChunkGate`): tickets `ENDER_PEARL` em 0, 1, 2, 4, 8, 16, 32 ticks à frente (raio 2 até 2 ticks, 0 depois), passo segurado se faltar chunk em 1, 2 ou 4 ticks (teto de 200, com aviso), e o chunk de destino de uma troca também precisa estar carregado. Mixin `ChunkMap.isChunkTracked`: o piloto sempre rastreia o chunk do próprio foguete.
- **Contato com o chão** (abaixo de 400 de altitude): varre os blocos sólidos (e fluidos) sob a pegada da caixa, na janela vertical que a base atravessou no tick. NÃO use o mapa de alturas do vanilla: neste mundo ele não acompanhou blocos postos acima do terreno gerado (a plataforma de lançamento ficava invisível). `LandingRules.contact` decide pouso suave (fica `LANDED`, de pé, velocidade zero) ou batida (blocos viram itens, dano ao piloto, entidade removida).
- **Telemetria**: `RocketTelemetryBuilder` monta o `RocketTelemetryPayload` (Orbit, Δv, TWR, `TransferPlanner.moon` em órbita da Terra); enviado ao piloto a cada 4 ticks.
- **Guardas**: `TravelServer` ignora o piloto de foguete; `PlanetWrap` não mexe nele (o foguete faz o próprio wrap em X, por teleporte na mesma dimensão); `Autopilot.start` recusa dentro do foguete.

## Reentrada e subida rápidas (Terra 10x maior)

A órbita da Terra é ~91 b/tick (1.800 blocos/s) e o gerador de chunks do planeta não acompanha: a reentrada vinda de órbita cruzava os chunks da Terra a ~89 b/tick abaixo de 4.000 e o servidor bloqueava gerando (medido no autoteste `flight`: 6 avisos "Can't keep up", de 2 s a 7,7 s, tick máximo de 1,5 s, e a subida do voo C atravessava a dimensão da Terra a 70 a 77 b/tick).

Decisão (`rocket/ZoneSwitch`, usada pela `FlightMachine`): **a dimensão do planeta só serve a voo lento**.

- **Descida virtual no espaço**: o arrasto, o calor e o paraquedas dependem do estado de voo (corpo dominante e altitude), não da dimensão, então já funcionam na dimensão de espaço (vazia, sem chunk de terreno). O foguete só entra na dimensão da Terra abaixo de 4.000 se a horizontal ao chão for ≤ 25 b/tick, ou à força a ≤ 1.000 sobre o terreno (altura do mapa do planeta, `PlanetTerrains`). Quem chega rápido demais à entrada forçada paga a espera do portão de chunks: o piloto deve frear de retrógrado no espaço (o arrasto sozinho leva de 91 a ~59 b/tick até 1.000).
- **Subida**: sai para o espaço antes dos 5.000 se, acima de 1.000, a horizontal passa de 40 b/tick. A faixa 25 a 40 é a histerese (não há troca de ida e volta).
- **Pegada de chunks** no portão (acima) e **devolução das bordas** do SAS quando o passo é segurado.
- **Pré-carga** da dimensão do planeta (`RocketFlightController.preloadBelow`) enquanto o foguete ainda está no espaço, só com horizontal ≤ 50 b/tick. Pedir tickets à frente em velocidade orbital foi medido e é pior (um tick de 43 s: ~300 chunks por probe de raio 2).
- Descartado: reduzir distância de simulação/visão (como o `Autopilot`): o README já registra que não mudou de forma mensurável; o gargalo era o `getChunk` bloqueante da caixa do foguete/piloto.
