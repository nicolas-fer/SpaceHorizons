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
| Zonas | Dimensão do planeta até a altitude de saída (`5000 · R/R_Terra`: Terra 5.000, Lua 1.250). Acima disso, dimensão `spacehorizons:space`. Volta ao corpo só a **1.200 sobre o terreno** (`0,3 ×` a altitude de entrada) **e com velocidade horizontal ao chão ≤ 25 b/tick** (`rocket/ZoneSwitch`); entrada forçada a ≤ 600 sobre o terreno com qualquer velocidade. Saída antecipada (≥ 1.000 de altitude, ≥ 1.500 sobre o terreno) quando a horizontal passa de 40 b/tick (histerese 25/40). Passagens sem tela de carregamento (payload existente). |
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
| Teclas (padrão) | W/S arfagem, A/D guinada, Q/E rolagem, Shift/Ctrl acelerador ±, Z cheio, X corta, T SAS (desligado → estabilizar → prógrado → retrógrado → horizontal), P paraquedas, G desmontar (pousado), V câmera. Shift **não** desmonta dentro do foguete. |
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
- `RocketAssembly`: BFS a partir do cockpit por faces, para nos blocos `launch_pad` e no ar; teto de 8.192 blocos, caixa de 128 x 256 x 128 e 400.000 iterações (eram 1.024 blocos e 64 de lado até a etapa 1 dos tiers; ver "Multiblocos, retrato grande e painel de projeto"); valida tudo antes de remover (exceções tipadas com mensagem traduzível: sem motor, sem tanque, sem cockpit, grande demais, encostado em terreno, bloco proibido, chunk não carregado, retrato grande demais).
- `RocketPortrait`: retrato denso (tamanho X·Y·Z, paleta de BlockState, NBT de block entities sem x/y/z), `id` estável (UUID), NBT com paleta e `Pos` em long, codec de rede compactado. `RocketStats` calculado a partir do retrato.
- Remoção do mundo: dois passes (presos primeiro), flags `UPDATE_MOVE_BY_PISTON|UPDATE_SUPPRESS_DROPS|UPDATE_KNOWN_SHAPE|UPDATE_CLIENTS|UPDATE_IMMEDIATE`, um passe final de `markAndNotifyBlock`. Reposição: dois passes inversos, `state.rotate`, NBT com x/y/z de destino, flag contra execução dupla.
- `RocketEntity extends Entity implements IEntityWithComplexSpawn`:
  - Registro: `MobCategory.MISC`, `clientTrackingRange(16)`, `updateInterval(1)`.
  - Dados sincronizados: `ATTITUDE` (`QUATERNION`, local da dimensão), `THROTTLE` (float), `FUEL_MB` (float), `FLIGHT_MODE` (byte: `LANDED`, `FLIGHT`, `PARACHUTE`), `SAS_MODE` (byte), `HEAT` (float), `HELD` (boolean), `VIRTUAL` (boolean) e `VIRTUAL_POS` (`VECTOR3`: a posição real no espaço, ver "Estacionamento no espaço"). O retrato vai no spawn data.
  - Salvo inteiro em `addAdditionalSaveData`/`readAdditionalSaveData` (retrato, estado de voo inercial, combustível, atitude inercial, modos), porque a troca de dimensão recria a entidade por NBT.
  - Overrides obrigatórios: `isClientAuthoritative/isLocalClientAuthoritative = false`, `isAlwaysTicking = true`, `isNoGravity = true`, `isFlyingVehicle = true`, `getKnownMovement = getDeltaMovement()`, `getControllingPassenger` = jogador no cockpit, `createInterpolationHandler` linear de UM passo (ver "Sincronia no cliente"), `getDimensions` pelo retrato, `positionRider` pelo cockpit girado, `getDismountLocationForPassenger` seguro.
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
- **Chunks** (`ChunkGate`): corredor contínuo de chunks à frente (pegada do foguete ao longo da trajetória, antecedência de 12 a 80 ticks pela latência de geração medida, tickets `ENDER_PEARL` com raio 2 até 2 ticks e 0 depois, no máximo 32 pedidos novos e 192 pendentes por tick, orçamento menor com o servidor lento; ver "Corredor de chunks"), passo segurado se faltar chunk na pegada em 0, 1, 2 ou 4 ticks (teto de 200, com aviso), e o chunk de destino de uma troca também precisa estar carregado. No espaço não há corredor: o foguete está estacionado e só o chunk dele é mantido. Mixin `ChunkMap.isChunkTracked`: o piloto sempre rastreia o chunk do próprio foguete.
- **Contato com o chão** (abaixo de 400 de altitude): varre os blocos sólidos (e fluidos) sob a pegada da caixa, na janela vertical que a base atravessou no tick. NÃO use o mapa de alturas do vanilla: neste mundo ele não acompanhou blocos postos acima do terreno gerado (a plataforma de lançamento ficava invisível). `LandingRules.contact` decide pouso suave (fica `LANDED`, de pé, velocidade zero) ou batida (blocos viram itens, dano ao piloto, entidade removida).
- **Telemetria**: `RocketTelemetryBuilder` monta o `RocketTelemetryPayload` (Orbit, Δv, TWR, `TransferPlanner.moon` em órbita da Terra); enviado ao piloto a cada 4 ticks.
- **Guardas**: `TravelServer` ignora o piloto de foguete; `PlanetWrap` não mexe nele (o foguete faz o próprio wrap em X, por teleporte na mesma dimensão); `Autopilot.start` recusa dentro do foguete.

## Reentrada e subida rápidas (Terra 10x maior)

A órbita da Terra é ~91 b/tick (1.800 blocos/s) e o gerador de chunks do planeta não acompanha: a reentrada vinda de órbita cruzava os chunks da Terra a ~89 b/tick abaixo de 4.000 e o servidor bloqueava gerando (medido no autoteste `flight`: 6 avisos "Can't keep up", de 2 s a 7,7 s, tick máximo de 1,5 s, e a subida do voo C atravessava a dimensão da Terra a 70 a 77 b/tick).

Decisão (`rocket/ZoneSwitch`, usada pela `FlightMachine`): **a dimensão do planeta só serve a voo lento e baixo**.

- **Descida virtual no espaço**: o arrasto, o calor e o paraquedas dependem do estado de voo (corpo dominante e altitude), não da dimensão, então já funcionam na dimensão de espaço (vazia, sem chunk de terreno; a atmosfera da Terra vai até 1.800, então o paraquedas pode abrir ainda no espaço e a troca não o fecha: teste `FlightMachineTest`). O foguete só entra na dimensão da Terra a **1.200 sobre o terreno** (`0,3 ×` a altitude de entrada; altura do mapa do planeta, `PlanetTerrains`) se a horizontal ao chão for ≤ 25 b/tick, ou à força a **600** (`0,15 ×`), qualquer velocidade. Não há mais entrada a 4.000: a troca baixa depende da pré-carga do pouso abaixo.
- **Subida**: sai para o espaço antes dos 5.000 se, acima de 1.000 de altitude E de 1.500 sobre o terreno (`1,25 ×` a altura de entrada: histerese em altura, o foguete que acabou de entrar à força não sai na hora), a horizontal passa de 40 b/tick. A faixa 25 a 40 é a histerese em velocidade.
- **Pegada de chunks** no portão e **devolução das bordas** do SAS quando o passo é segurado. A chegada de uma troca só vale com a pegada do foguete e o chunk do assento do piloto carregados.
- **Pré-carga do pouso** (`ImpactPredictor`, `LandingPreloader`, `LandingPreloadDriver`): só descendo e abaixo da altitude de saída do corpo (5.000 na Terra), 1 vez por segundo, integra `FlightIntegrator.step` (sem empuxo nem paraquedas, até 8.000 ticks; usa o resultado só se o impacto está a ≤ 1.200 ticks) e converte cada tick para as coordenadas da dimensão (`FrameBridge.toDimension`); o impacto é o primeiro ponto abaixo do terreno (`PlanetTerrains`). Pede tickets `PORTAL` (300 ticks de validade, renovados a cada segundo) de raio 0 em ~7×7 chunks em volta do impacto e no trecho final (1.500 blocos de altura, pegada de 3×3 por ponto), no máximo 400 chunks, 40 novos por segundo e 96 pendentes; solta o que a previsão nova não quer e tudo ao pousar. Nunca espera chunk (`hasChunk`). Telemetria: `landingState` (0, 1, 2) e `landingPercent`; o painel mostra "Pouso: carregando N%" ou "Pouso: pronto". Limite conhecido: a previsão ignora o paraquedas e a queima de freio, então durante uma frenagem ela erra para o lado do impacto mais cedo; a área e a atualização por segundo absorvem isso.
- **Pré-carga da dimensão do planeta** (`RocketFlightController.preloadBelow`) enquanto o foguete ainda está no espaço, só com horizontal ≤ 50 b/tick. Pedir tickets à frente em velocidade orbital foi medido e é pior (um tick de 43 s).
- **Voo rápido no cliente do piloto** (`FastFlight`, `FastFlightClient`, `DhFastFlightConfig`): acima de 20 b/tick ao chão (histerese: volta abaixo de 10), distância de visão 4 e de simulação 5 (o mínimo da opção) no cliente do piloto (no mundo local o servidor integrado lê as opções a cada tick; num dedicado o cliente reenvia com `broadcastOptions`) e `GeneratorPlan = DISABLED` no DH por override da API (a 7.2 não tem pausar fila). Restaura ao frear, pousar, descer ou sair do mundo. Isto reverte a decisão anterior de descartar a redução de distâncias: agora só vale para o piloto, em voo rápido.
- **Passo segurado** (`HELD`, dado sincronizado do `RocketEntity`): o servidor o marca nos três pontos em que segura o passo e o desmarca ao andar; o cliente (`RocketEntity.tick`) pula para o alvo da interpolação, cancela-a e zera a velocidade. A causa do "vai e volta" é explicada em "Sincronia no cliente".
- **Autoteste**: o cliente real do autoteste também manda o pacote de controle (com o acelerador guardado, que `RocketPilot.onBoard` reinicia só quando "deixa de pilotar") e sobrescrevia o piloto de teste em 1 de cada 5 ticks; o piloto de teste reaplica o comando antes do passo (`FlightDevtest.reapplyControl`) e `RocketPilot.forgetPilotingForTest` zera o acelerador entre voos.

## Estacionamento no espaço, corredor de chunks e sincronia no cliente

Diagnóstico (fase 1, `JumpProbe`, `tools/analyze_jump.py`): o aviso "Waiting for terrain" é o HOLD do `ChunkGate` (o cliente nunca ficou sem chunk) e os saltos visuais eram a dessincronia foguete/piloto no cliente. Três correções.

**1. Foguete estacionado na dimensão de espaço** (`rocket/SpaceParking`, `RocketFlightController`, `RocketFlight.spacePosition`).
- Na dimensão de espaço (`zoneBody == null`) a entidade, o piloto e os passageiros ficam num ponto fixo: `SpaceParking.pointFor(uuid)`, grade de 256×256 células de 1.024 blocos a partir de X/Z = 8.000.000 (longe de qualquer posição real do sistema e dos outros foguetes), Y = 8 (a dimensão tem 16 de altura), centro do chunk, mantido por ticket `ENDER_PEARL` a cada tick. O UUID atravessa a troca de dimensão (o id da entidade, não).
- A posição REAL (a de antes: `FrameBridge.toSpaceDimension`, coordenadas da dimensão de espaço) vive só no estado de voo inercial; o controlador a guarda em `RocketFlight.spacePos` (salva no grupo `Flight` do NBT, `SpaceX/Y/Z`) e a sincroniza como `VIRTUAL`/`VIRTUAL_POS` (float: o céu não precisa de mais; o valor também vai no NBT da entidade e acompanha a troca de dimensão). Nada anda na dimensão: sem passo segurado, sem chunk gerado.
- Cliente: `ShipView` lê a posição real interpolada (`RocketEntity.virtualSpacePosition`, entre ticks como a atitude) do foguete em que a câmera está montada, e `SkyObserver.of` monta `SkyObserver.forShip(clock, posE, identidade)`; `BodyRenderer` (Terra e Lua, com halo e scaled space), sol e estrelas (`SpaceSkyClient`, atitude identidade) saem dele. Não há malha orbital, curvatura, atmosfera nem nuvens no espaço, então nada mais lê a posição da câmera. `RocketCamera` e `CockpitCamera` continuam pela atitude do foguete; painel e telemetria vêm do estado de voo (altitude e velocidades reais).
- Transições: Terra→espaço (`LEAVE_ZONE`) leva o foguete ao ponto de estacionamento (o aviso ao piloto, `TravelTransferPayload`, já traz o assento no ponto; a chegada só vale com os chunks do ponto e do assento carregados), com `VIRTUAL_POS` = posição real; espaço→Terra (`ENTER_ZONE`, decidido por `ZoneSwitch`) sai na posição real convertida para a dimensão da Terra e limpa `VIRTUAL`. Sem tela de carregamento nas duas. Um mundo salvo com o foguete em voo no espaço na posição real o estaciona no primeiro tick.
- Pré-carga do pouso (`LandingPreloadDriver`, `ImpactPredictor`), voo rápido (`FastFlight*`, continua ligando pela velocidade da telemetria; no espaço não faz mais falta) e o acelerador (`RocketFlight.control`, `trackPilot`, `ThrottleWatch`) usam o estado de voo e não mudam.
- **Piloto automático** (`fly moon/earth`): também voa estacionado no espaço, com a posição real em `AutopilotSkyPayload` (ver o README, "Polimento").
- **Limitação**: jogadores que não estão num foguete na dimensão de espaço (`goto space`, voo criativo) continuam como antes (posição real) e não veem um foguete estacionado na posição certa; os outros foguetes só aparecem no ponto fixo. Desenhá-los na posição relativa é o item 4.3 de `docs/PLANO_NAVES.md`.

**2. Corredor de chunks** (`ChunkGate.corridor`, `ChunkGate.Requester`). Substitui as sondas esparsas (ticks 0, 1, 2, 4, 8, 16, 32 à frente, raio 0 fora dos 2 primeiros): os chunks entre as sondas só eram pedidos ao entrar na janela de 4 ticks e a geração demora mais, então o foguete andava um passo e esperava outro (HOLD de 50% de 20 a 90 b/tick, servidor ocioso). Agora: a pegada (`±(maior lado/2 + 2)`) amostrada a cada 8 blocos ao longo da trajetória prevista (velocidade do último passo) até `lookaheadTicks` (4 + 2 × latência de geração medida, entre 12 e 80), do mais perto ao mais longe; a pegada nos ticks 0, 1, 2 e 4 segura o passo (como antes). `Requester`: no máximo 32 pedidos novos e 192 pendentes por tick (os que seguram o passo sempre), tickets renovados a cada 15 ticks (o `ENDER_PEARL` vale 40), esquecidos 60 ticks depois de saírem do corredor, e `budgetScale` reduz o orçamento quando o tempo médio do tick passa de 50 ms (zero a 120 ms) para não arrastar o servidor dos outros jogadores. Um segundo `Requester` serve a pré-carga da dimensão do corpo enquanto o foguete ainda está no espaço (`preloadBelow`). Medido: ver a tabela do README; o limite é a vazão do gerador do planeta (~380 blocos/s nesta máquina), que a 54 e 90 b/tick deixa o HOLD em 17% e 44% com ticks mais lentos.

**3. Sincronia no cliente** (`mixin/travel/RocketPositionMixin`, `RocketEntity`). Causa: o foguete leva `snapTo` (a posição anterior dele é redefinida) quando o alvo dos pacotes de posição está a mais de 64 blocos (`handleEntityPositionSync`/`handleTeleportEntity`, `tooBigToInterpolate`), e a interpolação de 2 passos o deixava ~2 passos atrás a 54 b/tick; o jogador, posicionado por `positionRider` no tick, não. Resultado: num tick o jogador andava 2 passos e o foguete 1 (o desenho interpola `xo`→`x`), o foguete aparecia até ~81 blocos adiantado, atrasado ou andando para trás. Agora: os três pacotes de posição do foguete viram `moveOrInterpolateTo` (mixin; nunca `snapTo`), o handler é linear de UM passo (vai ao último alvo a cada tick) e o passageiro é posicionado a partir dele no mesmo tick; um salto de mais de 2.000 blocos no tick (a costura do planeta, um teleporte) zera a posição anterior do foguete E dos passageiros (`RocketEntity.positionRider`), sem varredura de uma ponta à outra.

## Multiblocos, retrato grande e painel de projeto (etapa 1 do `docs/PLANO_TIERS.md`)

### Estruturas multibloco (`rocket/Multiblocks`)

Por REGRA, não por lista. A mesma função pura (`Multiblocks.tileTanks` e `tileEngines`) serve ao mundo, à montagem, ao retrato em voo, ao censo da
nave e ao painel, então todos concordam. A partição é gulosa e determinística: percorre as células de baixo para cima (y, z, x) e, em cada célula
livre (o canto mínimo de uma caixa), toma a maior estrutura válida que cabe (maior volume; empate, maior base). O que não cabe em nenhuma estrutura
vale um a um, como antes.

- **Tanque**: coluna retangular CHEIA de `rocket_fuel_tank`, largura e profundidade de 1 a 3, altura de 4 a 12 (exemplos: 1x1x8, 2x2x8, 3x3x10).
  Mais alto que 12 parte em duas colunas; mais curto que 4 (ou com buraco, ou em L) fica avulso.
  - Capacidade: **4.000 mB por bloco, pelo volume** (uma coluna 2x2x8 guarda 128.000 mB).
  - Massa seca da coluna: `0,2 t * V * (0,4 + 0,6 * A / (6 V))`, com V o volume e A as faces expostas (`2(wd + wh + dh)`): o casco por bloco cai quanto maior
    a coluna, com piso de 40% do tanque avulso. 1x1x4 = 0,68 t (avulsos 0,8), 1x1x8 = 1,32 t (1,6), 2x2x8 = 4,0 t (6,4), 3x3x10 = 9,96 t (18), 3x3x12 = 12,7 t.
  - O combustível é DA ESTRUTURA: o galão enche qualquer bloco dela e a carga se reparte por igual entre os blocos (`TankStructures`); a barra de
    ação e a tela ao olhar mostram "Estrutura de tanque 2x2x8: X / Y mB"; desmontar o foguete devolve o combustível proporcional por bloco
    (`RocketFuel.distribute`, igual para todos).
  - Visual: textura conectada (sem emenda entre os blocos), pelo mecanismo genérico de `docs/TEXTURAS_CONECTADAS.md` (`RocketFuelTankBlock` usa
    `ConnectedFaces`; `rotate` gira as conexões horizontais para o pouso girado).
- **Motor**: cubo cheio de `rocket_engine` 2x2x2 ou 3x3x3 (a altura é igual ao lado); a camada plana de motores (como a do foguete da missão) NÃO forma
  e continua com motores avulsos. Números por tamanho:

  | | blocos | empuxo | massa seca | Isp | empuxo por tonelada |
  |---|---|---|---|---|---|
  | avulso 1x1 | 1 | 2,5 | 0,6 t | 1.600 | 4,17 |
  | 2x2x2 | 8 | 24,0 (8 x 2,5 x 1,2) | 3,84 t (8 x 0,6 x 0,8) | 1.700 | 6,25 |
  | 3x3x3 | 27 | 101,25 (27 x 2,5 x 1,5) | 11,34 t (27 x 0,6 x 0,7) | 1.800 | 8,93 |

  Os motores queimam juntos: a vazão de massa soma, então o Isp do conjunto é a média harmônica ponderada pelo empuxo, `ΣF / Σ(F/Isp)`
  (`RocketSpecs.ispOf`). As contagens de `RocketStats` (`engines`) continuam sendo de BLOCOS.
  - O bloco MESTRE desenha o modelo do conjunto e os outros ficam invisíveis (`RocketEngineBlock.FORM`: `solo`, `master_2`, `master_3`, `part`). O mestre do
    2x2x2 é o canto mínimo (o modelo se estende para +x, +y, +z; `FACING` gira o modelo quando a estrutura pousa girada); o do 3x3x3 é o bloco CENTRAL
    (um element só vai de -16 a 32, e o modelo de 48 pixels cabe centrado, simétrico no giro).
  - `EngineForming` mantém o estado no mundo ao pôr (`onPlace`) e ao quebrar (`affectNeighborsAfterRemoval`) um motor, com a mesma partição. A montagem
    REFAZ os estados do retrato pela partição (e as conexões dos tanques), então um foguete montado por comando também voa formado; o pouso e o estaleiro
    repõem os blocos já com os estados certos (sem refazer o conjunto a cada bloco: `EngineForming.suspended`). No retrato em voo o renderer desenha
    o modelo do mestre como qualquer outro bloco; a chama sai só da camada de baixo de cada motor formado.
- **Censo**: `RocketCensus` tem as contagens de blocos e as listas `tankStructures` e `engineStructures`; `RocketSpecs.statsFor` usa as estruturas (empuxo,
  Isp e massa por estrutura, os avulsos um a um). O retrato (`RocketPortrait.census`), a montagem (`RocketAssembly`) e o painel dão o MESMO censo (conferido
  em jogo). `ShipCensus` (naves) carrega as colunas de tanque achadas sobre as posições dos tanques do censo (`ShipSpecs.tankStructureMassT`, mesmo fator
  de casco, tanque da nave de 0,3 t) e as grava em `ShipFlightData` (`tank_structures`, opcional: naves antigas valem avulsas).

### Retrato grande (decisão D2 do `docs/PLANO_NAVES.md`)

- **Tetos**: 8.192 blocos, caixa de 128 x 256 x 128 (`RocketAssembly.MAX_BLOCKS`, `MAX_EXTENT_XZ`, `MAX_EXTENT_Y`), 400.000 consultas ao mundo
  (`MAX_ITERATIONS`) e 4.194.304 células (`PortraitGrid.MAX_VOLUME`). A grade densa guarda cada célula em 16 bits (a paleta tem no máximo 8.193 estados),
  então a caixa cheia custa 8 MiB. A montagem comum (`rocket_cockpit`) aceita até esse teto, e o retrato continua denso (a nave da fase 2a usa o mesmo).
- **Envio**: até 960 KiB (`RocketAssembler.INLINE_PORTRAIT_BYTES`, folga sobre o 1 MiB do cliente) o retrato vai INTEIRO no pacote de spawn, como antes. Acima
  disso o pacote de spawn leva só o id do retrato e as PARTES seguem (`PortraitParts`, `RocketPortraitPartPayload`): `RocketEntity.startSeenByPlayer` corta os
  bytes do retrato em partes de 256 KiB e as manda a cada jogador que passa a ver o foguete (inclusive depois da troca de dimensão, que recria a entidade);
  o cliente junta as partes em qualquer ordem (`PortraitParts.Receiver`, que recusa partes incoerentes) e a entidade pega o retrato quando completa. Teto do
  retrato codificado: 16 MiB (`MAX_PORTRAIT_BYTES`; a montagem é recusada acima com `PACKET_TOO_LARGE`, e a conversão nave para foguete usa o mesmo teto).
- O retrato codificado é calculado uma vez (`RocketPortrait.encoded`). O render, a luz do retrato (`PortraitLevel`) e a malha escalam com a caixa, não com o
  número de blocos.

### Painel de projeto na plataforma de lançamento

- **Abrir**: o painel (HUD no lado direito) aparece em tempo real enquanto se OLHA para uma `launch_pad` de MÃO VAZIA; o clique direito de mão vazia nela o FIXA
  (fica aberto andando em volta, até 48 blocos, mesmo com blocos na mão) e outro clique o solta. Com um item na mão e sem fixar nada aparece (não atrapalha quem
  constrói). `LaunchPadBlock` só avisa o gancho que o cliente liga (`RocketDesignClient`).
- **Cálculo**: o cliente pede o relatório a cada 10 ticks (`RocketDesignRequestPayload`) e o servidor responde (`RocketDesignPayload`; ignora pedidos a menos de 4
  ticks, de mais de 96 blocos ou de um bloco que não é plataforma). `RocketDesign.analyze` acha as peças encostadas na plataforma (e nas outras plataformas ligadas
  a ela), acha o cockpit e roda `RocketAssembly.analyze` A PARTIR DELE, exatamente a busca que a montagem de verdade faz (`scan` é `analyze` mais a recusa):
  o painel e a montagem não podem discordar. Os números saem de `RocketSpecs.statsFor` sobre o censo (o mesmo que o retrato e a entidade usam), com os tanques CHEIOS.
- **Destinos** (`MissionBudget`), a partir do Δv com os tanques cheios, o TWR na Terra e o paraquedas:

  | Destino | Δv medido (b/t) | Δv exigido (+5%) | de onde vem |
  |---|---|---|---|
  | não decola | | | TWR <= 1 (a regra da decolagem da `FlightMachine`) ou sem Δv |
  | decola, mas não sai da atmosfera | | | abaixo do salto |
  | salto suborbital | 50,0 | 52,5 | subir acima dos 5.000 de altitude de saída (vertical, com ~16 de perda de gravidade) |
  | chega à órbita | 78,8 | 82,7 | subida 63,8 + circularização 14,9 (`docs/MISSAO_LUA.md`); pede TWR >= 1,2 |
  | chega à Lua e volta | 216,0 | 226,8 | órbita + transferência 29,9 + captura 9,7 + volta 8,2 + reentrada 89,5; pede paraquedas |
  | chega à Lua, pousa e volta | 263,2 | 276,4 | acrescenta pouso lunar 24,3 e decolagem lunar 22,9 (em vez de ficar na órbita lunar); pede paraquedas |

  O `MissionBudgetTest` refaz a missão no simulador (`MissionSim`, a mesma `FlightMachine`) e confere cada soma, e voa projetos reais do painel: o que o painel
  promete (salto, órbita, Lua e volta sem pousar, missão inteira com o foguete tipo M e a coluna formada) o simulador cumpre, e o que ele nega (não decola) o simulador
  confirma. O foguete A (Δv 253) cai em "Lua e volta": o simulador mostra que ele fecha a volta sem pousar e não fecha o pouso lunar.
- **Problemas apontados no bloco certo** (destaque no mundo com um contorno de partículas: vermelho se impede a montagem, amarelo se é aviso): sem cockpit,
  mais de um cockpit, sem motor, sem tanque, longe da plataforma, grande demais, encostado no terreno (o bloco de terreno), bloco proibido, chunk não carregado,
  "falta paraquedas" (no bloco mais alto), "motor sem tanque" (o motor, ou o mestre da estrutura, sem tanque acima na coluna dele), "estrutura de tanque
  incompleta" (tanques avulsos que encostam em outro tanque: a coluna não fechou) e "empuxo/peso baixo" (no cockpit). Máximo de 24 problemas por relatório.
- Unidades: velocidades em b/s como o painel de voo (Δv de 292,6 b/t aparece como 5.852 b/s).

### Ids que a arte final preenche (sem mudar código)

- Tanque: `textures/block/rocket_fuel_tank_frame.png` (lados) e `rocket_fuel_tank_frame_end.png` (cima e baixo), 16x16 com moldura de 2 pixels; depois
  `python tools/gen_connected_block.py --name rocket_fuel_tank --texture spacehorizons:block/rocket_fuel_tank_frame --texture-end spacehorizons:block/rocket_fuel_tank_frame_end --frame 2 --render-type solid --particle spacehorizons:block/rocket_fuel_tank`
  regera o blockstate, os 96 modelos e as 32 texturas derivadas em `rocket_fuel_tank_ct/`. Hoje os dois PNG são derivados por `tools/derive_tank_frame.py`
  (moldura do anel de `machine_casing`, miolo do centro da textura do tanque).
- Motores: `models/block/rocket_engine_2x2.json` (a partir do canto mínimo, de 0 a 32 em x, y e z), `rocket_engine_3x3.json` (centrado no bloco do meio, de -16 a 32) e
  `rocket_engine_part.json` (vazio). Hoje os dois primeiros são os elements de `rocket_engine.json` ampliados 2x e 3x por `tools/gen_engine_structures.py`
  (que também regera `blockstates/rocket_engine.json`: `form` = solo, master_2, master_3, part; `facing` gira só o master_2).

### Limites conhecidos desta etapa

- O piloto de teste da missão (`MissionPilot`) só foi ajustado ao foguete tipo M: com um foguete de motor 3x3x3 e TWR 4 ele bate no pouso lunar (vv -1,47). A promessa
  do painel de "Lua, pouso e volta" para um projeto assim vem do orçamento de Δv, não de um voo simulado (o projeto com a camada 3x3 de motores e a coluna formada completa a missão).
- A regra de TWR >= 1,2 para a órbita e a de "sem paraquedas não promete a volta da Lua" são conservadoras e não foram simuladas além dos casos do teste.
- Um conjunto de motores (ou de tanques) com mais de 4.096 blocos ligados não é refeito de uma vez no mundo (a montagem refaz tudo do mesmo jeito).
- O painel não considera a janela da Lua (o relógio de lançamento, ver `docs/MISSAO_LUA.md`).

## Tier 2: combustível por tipo e motores novos (`docs/PLANO_TIERS.md`, seção 8)

Números, receitas e o porquê no plano; aqui ficam os contratos. A física é a mesma: só o combustível deixou de ser um número só.

### Física pura (`space/flight`)

- `Propellant`: `CHEMICAL` (0,0008 t/mB), `HYDROGEN` (0,0004), `XENON` (0,0012), `HELIUM3` (0,0008), `NUCLEAR_CORE` (0: a vida útil do núcleo
  do motor nuclear, em mB de hidrogênio). A ordem é a do NBT; só se acrescenta no fim.
- `FuelLoad`: mB de cada combustível (imutável, igualdade por conteúdo); `massT()`, `propellantMb()` (só os com massa), `clampTo`, `floor`.
- `EngineGroup(type, propellant, needsCore, thrust, ispTicks, blocks)`: os motores de um tipo, somados (Isp harmônico pelo empuxo entre os
  tamanhos). `needsCore` (nuclear): gasta `NUCLEAR_CORE` na mesma quantidade em mB que o hidrogênio; sem núcleo o grupo não empurra.
- `RocketStats` ganhou `groups` e `capacity` (`FuelLoad`). O construtor antigo de 10 argumentos continua (um grupo químico); `RocketStats.of(...)`
  monta a partir dos grupos (empuxo somado, Isp do conjunto, `fuelCapacityMb` = combustível com massa). Os métodos com `double fuelMb` são os do
  foguete químico, inalterados; os com `FuelLoad` (`wetMass`, `activeThrust`, `activeIsp`, `acceleration`, `twr`, `deltaV`, `firstBurnoutTicks`)
  servem a qualquer foguete. `deltaV(FuelLoad)` é em FASES (todos os grupos com combustível queimam juntos; quando o primeiro combustível acaba, o
  grupo dele para). `chemicalOnly()` diz se é o foguete do Tier 1.
- `FlightState` tem `FuelLoad fuel` como componente; `fuelMb()` é o químico, `withFuel(double)` troca o químico e mantém os outros,
  `withFuelLoad` troca tudo. Os construtores com `double fuelMb` continuam (químico).
- `FlightIntegrator.step`: com `chemicalOnly()` e carga só química, a conta de antes, bit a bit; senão `burn(...)`: cada grupo pede
  `F·acelerador/ve` toneladas do seu combustível, o combustível que não basta para o tick limita os grupos dele (o tick é parcial), e o Δv do tick é
  Tsiolkovsky com a vazão somada e a exaustão média pela vazão. A soma dos ticks bate com `deltaV(FuelLoad)` a ~1e-5 (o tick da troca de fase mistura
  as duas).

### Foguete (`rocket`)

- Papéis novos em `RocketBlockKind` (no fim, sem mudar os ordinais de antes): `NUCLEAR_ENGINE`, `ION_ENGINE`, `FUSION_ENGINE`, `HYDROGEN_TANK`,
  `XENON_TANK`, `RTG`; `isEngine()`/`isTank()` valem para todos os tipos.
- `EngineType`: números por tipo e tamanho (`thrust(side)`, `massT(side)`, `isp(side)`, `powerDemand(side)`, `chargePerBlockMb()`,
  `chargePropellant()`). O químico reproduz as constantes de `RocketSpecs`.
- `Multiblocks.analyze(Map<RocketBlockKind, long[]>)`: cada tipo de motor e de tanque é partido pela MESMA regra, só com os seus blocos;
  `Analysis.of(kind)` e `allEngineRoles`. `RocketCensus` ganhou `tier2` (contagens e estruturas por papel); `engines`/`tanks` continuam sendo os
  blocos químicos e `allEngines()`/`allTanks()`/`count(kind)`/`structuresOf(kind)`/`looseOf(kind)`/`rtgs()` cobrem tudo. A montagem exige
  `allEngines() > 0` e `allTanks() > 0`, exceto com motor de fusão (`RocketSpecs.carriesOwnFuel`: o He-3 fica no motor).
- `RocketSpecs.statsFor`: sem peças do Tier 2, exatamente o de antes; com elas, um `EngineGroup` por tipo, o iônico multiplicado por
  `ionPowerFraction` (energia dos RTG / pedida, até 1), massas dos tanques novos com o mesmo fator de casco, capacidade por combustível (tanques
  4.000 mB por bloco; núcleo 32.000 por bloco de motor nuclear; He-3 1.000 por bloco de fusão).
- Blocos: `hydrogen_tank`/`xenon_tank` são `RocketFuelTankBlock(props, Propellant)` com o MESMO block entity (`RocketFuelTankBlockEntity`,
  `fuel_mb`) e o mesmo item (`RocketFuelTankItem`); `TankStructures` só junta tanques do mesmo bloco. Os motores novos são `RocketEngineBlock`
  (iônico) e `ChargedEngineBlock(props, EngineType)` (nuclear e fusão, com `EngineChargeBlockEntity`: `charge_mb`, componente
  `engine_charge_mb` no item); `EngineForming` forma cada tipo com o seu bloco (o estado `form`/`facing` é o mesmo do motor químico). O galão de
  outro combustível é `FuelCanisterItem(props, Propellant)` (mesmo componente `fuel_mb`).
- Carga do foguete: `RocketEntity.fuel()`/`setFuel(FuelLoad)` (limitado a `stats.capacity()`); `fuelMb()`/`setFuelMb` continuam sendo o químico.
  NBT: `FuelMb` (químico, como antes) e `Fuel_<tipo>` para os outros; o voo (`RocketFlight`) guarda `Fuel` e `Fuel_<tipo>`. O dado sincronizado
  `FUEL_MB` é o total com massa. A montagem soma tanques por tipo e a carga dos motores (`RocketPortrait.fuelLoad()` lê o mesmo do NBT do retrato);
  o pouso devolve cada combustível aos blocos do seu tipo (`RocketFuel.distribute`) e o núcleo/He-3 aos motores.
- `FlightMachine.step(..., FuelLoad landedFuel, ...)`: a decolagem exige que os grupos COM combustível vençam a gravidade; o estado nasce com a
  carga inteira. A versão com `double landedFuelMb` continua (químico).
- Telemetria: o foguete químico como antes; com Tier 2, `fuelMb` = total com massa, Δv em fases e TWR dos grupos ativos.
- Painel (`DesignReport`): ganhou `lines` (uma `Line` por tipo de motor, de tanque e os RTG, com formas, empuxo, Isp, capacidade e energia); os
  campos de antes são os do químico; Δv, TWR e massas contam tudo. Avisos novos: `NO_ENGINE_FUEL` (motor sem nenhum tanque do seu combustível;
  `arg` = o papel do tanque que falta), `LOW_POWER` (iônicos com menos energia que a pedida; `arg` = %), e `ENGINE_WITHOUT_TANK`/`INCOMPLETE_TANK`
  olham o tanque do tipo certo.

### Ids que a arte final preenche (Tier 2)

Os nomes estão em `docs/TIER2_IDS.md`. Arquivos provisórios (gerados por `tools/gen_tier2_placeholders.py`, que não sobrescreve sem `--force`):

- Itens: `textures/item/<id>.png` para `raw_titanium`, `titanium_ingot`, `titanium_plate`, `rare_earth_dust`, `helium3_canister`,
  `hydrogen_canister`, `xenon_canister`, `nuclear_fuel_rod`, `superconductor_coil`.
- Blocos simples: `textures/block/mars_regolith.png`, `mars_titanium_ore.png`, `titanium_block.png`, `rtg_generator.png` (lados) e
  `rtg_generator_top.png` (cima e baixo).
- Tanques: `textures/block/<tanque>_frame.png` e `<tanque>_frame_end.png` (moldura de 2), `<tanque>.png` e `<tanque>_top.png` (o cubo do item);
  depois `python tools/gen_connected_block.py --name <tanque> --texture spacehorizons:block/<tanque>_frame --texture-end spacehorizons:block/<tanque>_frame_end --frame 2 --render-type solid --particle spacehorizons:block/<tanque>`.
- Motores: `models/block/<motor>.json` (1x1), `<motor>_2x2.json` (de 0 a 32, mestre no canto mínimo), `<motor>_3x3.json` (de -16 a 32, mestre no
  centro), `<motor>_part.json` (vazio). Os provisórios são os modelos do motor químico com as texturas `<motor>.png`, `<motor>_top.png`,
  `<motor>_bottom.png` e `<motor>_2x2_{nozzle,exit,plate,pump}.png`, `<motor>_3x3_{...}.png` recoloridas; a arte final pode trocar modelos e
  texturas à vontade, desde que os quatro ids de modelo existam (o blockstate os usa).
