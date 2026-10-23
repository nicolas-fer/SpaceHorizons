# Space Horizons

Mod NeoForge para Minecraft 26.3 (Java 25) que busca um **espaço contínuo**: planetas
curvos e finitos, integrados ao Distant Horizons (DH), e depois órbita e viagem
interplanetária sem trocar de representação de repente (nada de skybox em PNG).

## Índice

- Fase 1 (pronta)
- Fase 2 (pronta) e polimento antes da fase 3
- Fase 3 (pronta, verificada em jogo)
- Fase 4: Terra idêntica ao Overworld, Lua em órbita
- Dimensão de espaço aberto (etapa 3c do foguete)
- Sobrevivência e foguete
- Polimento: cockpit, sol, Terra de perto e piloto automático
- Naves (fase 1 do `docs/PLANO_NAVES.md`)
- Gerador de planetas por parâmetros e Marte (etapa 2 dos tiers)
- Referenciais: inercial (I) e preso à Terra (E)
- Escala: Terra 10x maior (limitações)
- Como rodar
- Como testar
- Limitações da fase 4
- Limitações da fase 3
- Limitações conhecidas

## Fase 1 (pronta)

Um planeta pequeno e curvo, onde andar reto em X dá a volta:

- **Planeta `spacehorizons:earth`**: circunferência de 655360 blocos (raio ≈ 104304),
  nível do mar em y=63, latitude máxima de 70°. Mapeamento equirretangular:
  X é longitude (periódico), Z é latitude.
- **Terreno** (`planet/terrain/SphericalTerrain`): ruído Perlin 3D amostrado na
  esfera unitária, então é periódico em X por construção. Determinístico e thread-safe.
- **Worldgen** (`worldgen/`): gerador de chunks, source de biomas e datapack da
  dimensão (`data/spacehorizons/dimension*/earth.json`). Os tipos de superfície
  vêm de `SurfacePalette`, a mesma tabela que o DH usa nos LODs.
- **Costura em X** (`worldgen/PlanetWrap`): jogadores que passam de `x = C/2` voltam
  para `x - C` (e vice-versa), mantendo rotação e velocidade. Há uma barreira polar
  que empurra de volta perto da latitude máxima. Chunks do outro lado da costura
  são pré-carregados no servidor.
- **Curvatura** (`client/curvature/` e `mixin/`): o `terrain.vsh` do vanilla
  recebe a fórmula esférica de `planet/CurvatureMath` (uniforms `SpaceHorizons_PlanetRadius`
  e `SpaceHorizons_CameraAltitude`). Fora do planeta a curvatura fica desligada.
- **DH** (`dh/`): integração opcional. Sem o DH instalado o mod carrega normalmente,
  só não há LODs.

## Fase 2 (pronta) e polimento antes da fase 3

- **Atmosfera** (`client/atmosphere/`): `AtmosphereModel` (espessura 1.800 e altitude base 160
  para a Terra) alimenta a neblina vanilla, a cor do céu e o limbo. A neblina do DH fica desligada
  enquanto há um mundo aberto (`dh/DhFogConfig`, override da API; ao sair do mundo volta o valor do usuário). O limbo (`shaders/atmosphere/limb.fsh`) amostra só o trecho do raio dentro da atmosfera,
  com jitter por pixel e dither: as faixas concêntricas a 600 blocos sumiram.
- **Malha orbital** (`client/planetmesh/`): grade polar adaptativa (célula de ~1,5 pixel, anéis
  com segmentos dobrando, orçamento de 70.000 vértices), cor por supersampling de 4 amostras,
  terminando no horizonte da altitude. A mistura para uma carta lat/lon fixa
  (`PlanetMeshProjection`, entre 3.000 e 12.000 blocos de altitude e fora do alcance do DH)
  evita que os continentes "nadem" quando a câmera se move em órbita. O brilho especular da
  água foi reduzido (o clarão branco a 15.000 sumiu). Sem o DH, o raio interno da malha encolhe
  com a altura, porque o vanilla não desenha seções fora do render distance em 3D.
- **Nuvens**: num planeta, o mod desliga as nuvens do DH (`dh/DhCloudConfig`, por override da
  API, restaurado ao sair) e mantém as vanilla curvadas, com alcance mínimo de 128 chunks. O DH
  grava `cloudStatus=OFF` nas opções do usuário ao iniciar; o `DhCloudConfig` restaura as vanilla
  (FANCY se estiverem em OFF) uma vez por sessão no planeta.
- **Entidades, block entities, itens, partículas, texto no mundo, contornos** (`mixin/DynamicTransformMixin`,
  `ShaderManagerMixin`, `ShaderManagerConfigsMixin`): os floats de raio e altitude entram no bloco
  `DynamicTransforms` e os vsh de mundo chamam `spaceHorizonsWorldCurve`. Perto da câmera (8 a 48
  blocos) a curvatura é atenuada para a mão e o jogador em 3ª pessoa não deformarem; GUI
  (projeção ortográfica) não é curvada.
- **Sodium** (`client/compat/SodiumCurvature`, `mixin/sodium/`, `spacehorizons.sodium.mixins.json`):
  patch do `block_layer_opaque` e escrita dos floats no `u_Globals`, só com Sodium 0.9.x. Veja a
  limitação abaixo: **não foi possível rodar** com o NeoForge atual.

Resultado visto nos prints (autoteste, configurações padrão e sem DH): a 600 blocos o céu é um
gradiente suave; a 5.000 se vê a curvatura com halo; a 15.000 e a 40.000 o planeta aparece inteiro
com o limbo e sem clarão; entidades invocadas a 50 a 200 blocos ficam apoiadas no terreno curvo;
nuvens vanilla curvas com o DH ligado.

## Fase 3 (pronta, verificada em jogo)

Dois corpos, a Terra e a Lua, num mesmo referencial (`space/SolarSystem`, `space/SpaceFrame`), e a viagem
entre eles sem tela de carregamento.

- **A Lua** (`spacehorizons:moon`, no tipo de mundo Space Horizons): circunferência 163840 (raio ≈ 26076),
  sem água, sem atmosfera, gravidade 0,165 (`worldgen/PlanetGravity`: atributos de gravidade e de dano de queda).
  Terreno `planet/terrain/MoonTerrain`: crateras em 4 escalas, mares escuros, terras altas e regolito.
  O dimension_type `moon` tem céu preto com estrelas o tempo todo. Os LODs do DH e a malha orbital
  funcionam nela (a malha respeita `hasOcean()`).
- **Corpos no céu** (`client/space/`): de qualquer planeta, os outros corpos são esferas reais nas
  posições do `SolarSystem` (da Terra, a Lua em órbita, que só passa pelo zênite do spawn de tempos em tempos; da Lua, a Terra com halo de atmosfera, sempre no zênite da face voltada para ela).
  A lua vanilla é escondida nos planetas do mod. A esfera de um corpo visto de longe e a malha orbital do
  mesmo corpo visto de perto usam o mesmo terreno, a mesma paleta e o mesmo sol.
- **Sol único e posição real** (`space/SunModel`, `client/space/SpaceSun`): há um só sol, no plano equatorial do espaço, que
  coincide com o do vanilla no spawn da Terra e anda com o relógio do Overworld. Em qualquer ponto de qualquer planeta do mod
  o sol vem da posição da câmera (`SunModel.localDirection`), não de um relógio global igual no planeta inteiro: ao meio-dia
  do spawn é meia-noite em x=C/2 da Terra, e nas latitudes altas o sol traça um caminho inclinado. O céu (cor, nascer e pôr),
  as estrelas, o disco do sol e a luz do chão (lightmap) seguem esse mesmo sol. O ponto de integração é
  `mixin/AttributeTrackSamplerMixin`, por onde o cliente lê o tick do relógio de toda trilha de timeline (cor do céu, brilho do
  nascer/pôr, estrelas, `sky_light_factor`/`sky_light_color`): num planeta do mod o relógio do Overworld do cliente é trocado por
  `SunModel.effectiveClock` (o tick em que o sol do vanilla teria a mesma altura), mantendo o número do dia. Servidor, outros
  relógios e outras dimensões passam direto. O disco do sol e o brilho do pôr do sol recebem ainda um giro de azimute
  (`mixin/SkyRendererMixin`), e as estrelas ficam fixas no espaço. A malha orbital, os corpos vistos de fora (`BodyRenderer`),
  o limbo e o chão usam a mesma direção, então a troca de dimensão não tem salto. A curva do sol do vanilla 26.3 não é linear
  (Bézier cúbica em `sun_angle`); o `SunModel` usa a mesma.
- **A Lua tem dia e noite**: o `dimension_type` `moon` deixou de ser `has_fixed_time` e usa o relógio do Overworld; o timeline
  `spacehorizons:moon_day` só derruba a luz do céu (fator 0,24, tom azulado) quando o sol está abaixo do horizonte, e o céu
  continua preto com estrelas. Com a Terra ao meio-dia, a face voltada para ela está escura (lua nova, o chão confere com a
  malha orbital vista do espaço); à meia-noite do spawn, está clara e, vista da Terra, a Lua está cheia no zênite. Ao meio-dia
  a Lua vista do spawn passa na frente do sol (eclipse), o que é geometricamente correto neste sistema.
- **Viagem** (`space/travel/`): ao entrar na esfera de influência de outro corpo, o servidor transfere o jogador
  (`BodyTransfer`: posição, velocidade e olhar convertidos pelo `SpaceFrame`) e o cliente não abre a tela
  de carregamento (`mixin/travel/ClientPacketListenerMixin`; o aviso do servidor já leva posição e olhar
  de destino, para o primeiro quadro depois do respawn sair do lugar certo). A velocidade de voo cresce com a altitude.

### Como viajar

Em criativo e como operador, em um mundo do tipo Space Horizons:

| Comando | O que faz |
| --- | --- |
| `/spacehorizons fly moon` | piloto automático da Terra à Lua (~4.000 a 9.000 ticks conforme onde a Lua está, estimativa dos testes unitários; não medida em jogo); a troca de dimensão ocorre sozinha a ~94.000 blocos acima da Lua |
| `/spacehorizons fly earth` | volta para a Terra |
| `/spacehorizons fly stop` | cancela o piloto automático |
| `/spacehorizons goto moon` | teleporta para a superfície da Lua (face voltada para a Terra); mostra a tela de carregamento, de propósito |
| `/spacehorizons goto earth` | teleporta para a superfície da Terra |

Também dá para voar reto para cima a partir do spawn da Terra: a velocidade sobe com a altitude e, ao chegar à
esfera de influência da Lua, a troca acontece igual.

## Fase 4: Terra idêntica ao Overworld, Lua em órbita

A Terra deixou de ser um terreno simples e passou a usar o gerador completo do Overworld; a Lua passou a orbitar.

- **Terra vanilla** (`worldgen/PlanetNoiseChunkGenerator`, `worldgen/density/`): o gerador de chunks do Overworld
  (`NoiseBasedChunkGenerator`, com `noise_settings/earth.json`, copiado do Overworld) com biomas `multi_noise` do preset do
  Overworld, então há os mesmos biomas, rios, cavernas, minérios, árvores e estruturas. Três ajustes:
  - **Costura em X periódica**: o nó `spacehorizons:periodic_x` reescreve cada folha do roteador de densidade que lê X
    (ruídos, shifts, `BlendedNoise`, gradiente em X). Fora de ±1.024 blocos da costura o X é embrulhado em [-C/2, C/2); dentro da faixa,
    mistura de duas amostras (`cos·A + sin·B`, que preserva a variância). Clima, relevo e biomas batem dos dois lados (`SeamMath`, `PeriodicXSampler`).
  - **Clima por latitude** (`spacehorizons:latitude_cooling`, `LatitudeClimate`): soma ao parâmetro de temperatura `0,12 − 1,82·t²`
    (t = |z| / latitude máxima): trópicos no equador, neve perto de ±120.000 blocos, gelo no polo.
  - Estruturas ficam suprimidas a menos de 640 blocos da costura.
- **Mapa do planeta** (`planet/map/`, `network/`): o servidor amostra o gerador real numa grade de 2048x798 (célula de 320 blocos),
  em background (~140 s na primeira vez; 4096 colunas levaram 487 s e 13,9 MiB), guarda em `<mundo>/data/spacehorizons/planetmap_earth.bin` e manda ao cliente em pedaços.
  A malha orbital e as esferas dos corpos usam as cores dos biomas reais desse mapa (`PlanetTerrains.get`); o terreno analítico
  só vale enquanto o mapa não chega e para a Lua. O DH usa o gerador do mundo (não há mais override de LOD na Terra).
- **A Lua orbita** (`space/SolarSystem`, `space/SpaceClock`): órbita kepleriana circular de raio 600.000, inclinada 5°, período de 98.983
  ticks (4,12 dias), prógrada, cheia à meia-noite do dia 0, no relógio do Overworld (`/time set` move sol e Lua); ver "Referenciais: inercial
  (I) e preso à Terra (E)". A Lua tem rotação sincronizada: a mesma face fica voltada para a Terra. `SpaceFrame`, `SunModel`, `BodyRenderer`
  e `SpaceSun` recebem o relógio.
- **Viagem com alvo móvel** (`space/travel/FlightGuide`, `FlightPlan`, `BodyTransfer`): o plano mira onde o corpo estará, corrige a
  cada tick e na troca de dimensão a velocidade vira velocidade relativa ao chão do corpo novo (ver "Autoteste").

## Dimensão de espaço aberto (etapa 3c do foguete)

Entre a Terra e a Lua existe agora uma dimensão vazia, `spacehorizons:space`, de onde se vê a Terra e a Lua inteiras. Ela entra
no world preset (só mundos criados depois; num mundo antigo `/spacehorizons goto space` avisa que a dimensão não existe).

- **Dados**: `dimension_type/space.json` (altura 16, `min_y` 0, `has_skylight`, `has_ceiling` para desligar o clima, `skybox overworld`,
  nuvens com alfa 0, sem timeline), `worldgen/biome/space.json` (sem chuva e sem spawns) e o gerador `minecraft:flat` sem camadas.
  Altura 16 é o mínimo do codec: uma seção por chunk.
- **Coordenadas** (`space/SpaceDimension`): os eixos da dimensão são a base local da Terra no spawn (leste = +Z do espaço, cima = +X,
  norte = +Y) e Y tem um viés de 20.000: `(x, y, z) da dimensão = (sz, sx + 20.000, sy)`. Assim o sol, as estrelas e a vertical do spawn
  da Terra funcionam sem rotação. A Terra fica em y = 20.000 e a Lua orbita entre y ≈ -580.000 e +620.000 (ver "Limitações").
- **Céu** (`client/space/`): `SkyObserver` diz de onde se olha (chão de um planeta ou espaço aberto) e o `BodyRenderer` desenha os
  corpos pelo observador; no espaço a Terra (com halo) e a Lua são esferas reais, e o céu do vanilla traz só o sol e as estrelas
  (`SpaceSkyClient`: sem lua do vanilla, sem névoa, sem disco escuro, sem nuvens nem chuva).
- **Regras**: gravidade 0 (`PlanetGravity`), vácuo em todo o espaço (`OxygenSetup.isVacuumAt`, HUD, compressor), sem "moved too
  quickly" e sem morte por cair abaixo do mundo (`mixin/travel/EntityMixin`). No espaço o voo criativo anda pela altitude sobre o corpo
  mais próximo (a mesma regra dos planetas); não há piloto automático.
- **Travessia** (`BodyTransfer.toSpace/fromSpace`, `TravelTransfers.findSpace`): do planeta ao espaço e de volta no mesmo ponto do
  espaço, com a mesma velocidade e o mesmo olhar, sem tela de carregamento.

| Comando | O que faz |
| --- | --- |
| `/spacehorizons goto space` | de um planeta, vai ao espaço no mesmo ponto (travessia contínua) |
| `/spacehorizons goto space x y z` | vai ao ponto (coordenadas da dimensão de espaço, as do F3 de lá), parado |
| `/spacehorizons goto earth` / `moon` | do espaço, dentro da zona do corpo (acima da superfície até 5.000 na Terra e 1.250 na Lua), volta no mesmo ponto; fora dela, teleporta à superfície |

Autoteste: `./gradlew runClientTest -PshOnly=space`. Prefixos
dos prints: `sp_a_*` Terra a 4.900 antes da troca, `sp_b_*` o mesmo ponto no espaço, `sp_c_*` sol, estrelas e Lua perto da Terra,
`sp_e_*` meio do caminho, `sp_f_*` e `sp_g_*` perto da Lua (com a Lua em Y negativo e em Y positivo), `sp_fly_*` os voos, `sp_i*_*` a
volta pelo comando. O log traz `ESPACO` (posição, gravidade, clima, oxigênio), `VOO ... RESULTADO` (tempo de trabalho do tick do
servidor num voo em linha reta, com e sem tickets à frente) e `sp_* RESULTADO` (telas abertas durante a travessia).

Medido em jogo (autoteste, voo em linha reta no espaço empurrado pelo servidor, 200 ticks, distância de visão 16): com tickets à
frente (ENDER_PEARL em 1, 2, 4, 8, 16 e 32 ticks, passo segurado se faltar chunk) o tempo de trabalho do tick fica em ~1,3 a
1,9 ms (máximo 5 a 30 ms) a 300, 600 e 1.200 blocos/s, sem aviso "Can't keep up"; sem tickets, a 600 blocos/s, a média sobe para
6,6 a 7,8 ms com picos de 95 a 178 ms (o `getChunk` bloqueante do `Entity.setPosRaw`). Reduzir a distância de visão para 2 não
mudou o resultado de forma mensurável.

Limitações da dimensão de espaço:

- **Faixa de Y**: com o viés fixo de 20.000, a órbita da Lua leva Y de -580.000 a +620.000 (e o lado do nadir da Terra fica em Y negativo, de -84.000 a -109.000 nas zonas de saída) (os eixos da dimensão são os do spawn da
  Terra, e a Lua orbita no plano leste-cima). Por isso `EntityMixin` desliga a morte abaixo do mundo no espaço. Uma convenção que
  pusesse o eixo polar da Terra em Y daria ±15.000 com o mesmo viés, mas exigiria girar o sol e as estrelas do vanilla.
- **Salto na saída a 5.000**: a esfera da Terra (agora até 2.048 segmentos, célula de 320 blocos; o mapa tem 320) coincide em
  posição, cor e halo com a malha orbital, mas some o detalhe dos chunks e do DH do centro da vista. Com a Terra 10x maior os 5.000
  de altitude são só 1/21 do raio, então a esfera vista dali é bem mais grossa que a malha orbital que a precedia (facetas visíveis).
- **DH no espaço**: o DH trata a dimensão de espaço como uma dimensão qualquer (gerador flat vazio); não há gerador de LOD próprio.
  O log mostra só avisos "Renderer already started" do DH a cada troca de dimensão.
- **Mundos antigos**: a dimensão só existe em mundos criados depois (world preset).
- **Lua e clima**: `moon.json` tem `has_ceiling` falso, então a chuva do Overworld escurece o céu da Lua; no espaço `has_ceiling`
  verdadeiro evita isso. Não alterei a Lua.

## Sobrevivência e foguete

Plano completo em `docs/PLANO_SOBREVIVENCIA.md`; decisões e contratos do foguete em `docs/DESIGN_FOGUETE.md`.

- **Materiais e refinaria** (`content/`, `survival/fuel/`): minérios, placas e a refinaria que enche galões de combustível.
- **Oxigênio** (`survival/oxygen/`, `survival/suit/`, `survival/compressor/`): vácuo no espaço e na Lua, traje com tanque, compressor
  que enche tanques; o ar da cabine do foguete conta como fonte antes do traje.
- **JEI** (`compat/jei/`) e conquistas (`data/spacehorizons/advancement/`).
- **Foguete de blocos** (`rocket/`): monte cockpit, motores, tanques, cascos, paraquedas e pernas sobre uma plataforma de lançamento;
  clique com a mão vazia no cockpit e a estrutura vira uma entidade que sobe inteira. Voo pilotável no estilo KSP simplificado
  (gravidade real, SAS, apoastro e periastro no painel), travessia para a dimensão de espaço com o piloto sentado, paraquedas,
  pouso suave e batida que destrói o foguete.
- **Multiblocos, painel de projeto e foguetes grandes** (etapa 1 do `docs/PLANO_TIERS.md`, detalhes e números em `docs/DESIGN_FOGUETE.md`):
  tanques formam colunas 1 a 3 de lado e 4 a 12 de altura (capacidade pelo volume, casco mais leve quanto maior, textura conectada, o combustível é da
  estrutura) e motores formam cubos 2x2x2 e 3x3x3 (mais empuxo e Isp por massa; o bloco mestre desenha o modelo). Na `launch_pad`, olhar de mão vazia
  (ou clicar para fixar) abre o painel de projeto: massa, empuxo, TWR, Δv e o destino que o foguete alcança (não decola, salto suborbital, órbita, Lua e
  volta, Lua pouso e volta), com os problemas apontados no bloco certo; usa o mesmo cálculo da montagem. O foguete aceita até 8.192 blocos e 128 x 256 x 128
  (o retrato grande vai ao cliente em partes). Autoteste: `./gradlew runClientTest -PshOnly=multiblock`.
- **Plantas holográficas de montagem** (seção 4 do `docs/PLANO_TIERS.md`, detalhes em `docs/DESIGN_FOGUETE.md`): o item `rocket_blueprint` (2 papéis +
  corante azul) guarda um projeto. Clique no ar abre o menu das plantas (salto suborbital, foguete orbital e, no criativo, o lunar, que a história vai entregar);
  clique numa `launch_pad` mostra o holograma: fantasma onde falta bloco, vermelho onde está errado, some onde está certo (só sugestão). Com a planta em branco,
  o clique salva o foguete que está sobre a plataforma; agachado + clique tira a planta. O painel de projeto mostra quanto falta e o destino do projeto da
  planta. Autoteste: `./gradlew runClientTest -PshOnly=blueprint`.
- **Tier 2: motores nuclear, iônico e de fusão** (`docs/PLANO_TIERS.md`, seção 9; contratos em `docs/DESIGN_FOGUETE.md`; ids em `docs/TIER2_IDS.md`):
  os três motores têm 1x1, 2x2x2 e 3x3x3 como o químico e cada um queima o seu combustível. Nuclear: o dobro do Isp, queima hidrogênio
  (`hydrogen_tank`) enquanto o núcleo durar (clique com `nuclear_fuel_rod` num bloco do motor: 32.000 mB de hidrogênio por barra). Iônico: Isp altíssimo
  e empuxo minúsculo, não decola de lugar nenhum; queima xenônio (`xenon_tank`) com a energia de geradores `rtg_generator` (1 por motor 1x1, 6 pelo
  2x2x2, 16 pelo 3x3x3). Fusão: Isp e empuxo altos, queima o hélio-3 guardado no próprio motor (clique com o galão de He-3; não precisa de tanque).
  Hidrogênio: galão + 2 baldes de água + redstone na bancada; xenônio: galão + 4 regolitos marcianos + terras raras; hélio-3: o galão no solo da Lua
  (125 mB por bloco). As peças pedem titânio e terras raras de Marte (`mars_titanium_ore`, `mars_regolith`; blocos e itens já existem, o planeta
  não). O painel de projeto mostra cada tipo e o Δv com os motores misturados (em fases: quando um combustível acaba, o motor dele para).
  Arte provisória (recolor) por `tools/gen_tier2_placeholders.py`. Autoteste: `./gradlew runClientTest -PshOnly=tier2`.

Controles no cockpit: Shift/Ctrl acelerador, Z cheio, X corta, W/S arfagem, A/D guinada, Q/E rolagem, T modo do SAS
(estabilizar, prógrado, retrógrado, horizontal), P paraquedas, V câmera, G desmontar (pousado). O SAS horizontal aponta o nariz
para a horizontal local no sentido do movimento (no espaço, perpendicular ao radial); na subida vertical ele segura a atitude.

Autoteste do voo: `./gradlew runClientTest -PshOnly=flight` (salto suborbital, batida, órbita completa com reentrada e o voo D, a mesma descida sem o freio de horizontal no espaço).

**Missão completa à Lua e de volta** (`-PshOnly=mission`, provada em jogo): órbita baixa, TLI, captura, pouso suave na Lua, decolagem, volta, reentrada
e pouso suave na Terra, ~264 b/t de Δv (órbita baixa 79, TLI 27, correções 3, captura 10, pouso lunar 24, decolagem 23, volta 8, reentrada
e pouso 90). A primeira rodada (foguete antigo, 45 tanques avulsos, 292,6 b/t) gastou 263,1 em 52.289 ticks; com o foguete atual (coluna de tanque 3x3x5 formada,
Δv 323,9) a missão fechou com 263,97 e sobraram 59,9 b/t. O foguete de teste A (253 b/t) não fecha o pouso lunar. Números por fase, roteiro para o jogador, plantas
embutidas, painel de projeto e o que a missão mostrou (a janela da TLI precisa da Lua perto do plano do equador na chegada) em `docs/MISSAO_LUA.md`.
`space/flight/LunarPlanner` prevê a chegada à Lua, calcula a correção de meio de curso e a queima de volta.

## Polimento: cockpit, sol, Terra de perto e piloto automático

- **Mensagem de desmontar**: o vanilla mostrava "Aperte Shift para desmontar" ao montar e de novo a cada troca de dimensão (que reenvia
  os passageiros). No foguete isso é falso (Shift é o acelerador; desmontar é G, pousado). `ClientPacketListenerMixin` suprime a mensagem
  e a fala do narrador quando o jogador monta um `RocketEntity`; a dica certa é a do `RocketPilot.onBoard` ("G desmonta (pousado), V
  alterna a câmera").
- **Primeira pessoa no cockpit**: o olho vai para a janela do cockpit (`CockpitCamera.windowEye`: meio da abertura do modelo
  `rocket_cockpit`, 0,12 bloco à frente do plano dela, na direção do bloco), fora do casco, por `CameraMixin` +
  `RocketCamera.firstPersonEye` (mesma posição e atitude interpoladas do renderer do foguete). O olhar segue ao longo do nariz, como antes
  (os controles W/S/A/D/Q/E dependem dessa base); só o olho mudou, e o casco aparece de lado na parte de baixo da tela. A mão e o item
  somem enquanto se pilota (`RenderHandEvent`).
- **Sol redondo** (`client/space/SunDisc`, `SkyRendererMixin`): o quadrado do vanilla virava losango com a atitude da nave e quando se olha
  para cima. Agora é um disco de frente para o eixo da câmera (plano perpendicular a ele, então o contorno é um círculo na tela em
  qualquer ponto do FOV), núcleo branco-quente opaco de ~5,4 graus de raio, borda de 1 grau e halo até 27 graus. Vale no espaço, no
  estaleiro e nos céus dos planetas do mod (Terra e Lua); fora deles (Nether, End, Overworld vanilla) fica o sol do vanilla. A direção é a
  da pose que o vanilla já monta (SunModel/SkyObserver), então nada mudou na posição do sol.
- **Terra de perto** (`planetmesh/planet_mesh.*sh`, `space/body.*sh`): o mapa tem células de 320 blocos e de perto a malha orbital (e a
  esfera vista do espaço) virava um borrão de gradientes. Os shaders agora somam detalhe procedural: ruído de valor 3D em coordenadas
  FIXAS no corpo (5 oitavas de 800, 200, 50, 12,5 e 3 blocos) que escurece e clareia a cor da terra e da água, e desloca o limiar da
  máscara de água para uma costa irregular em vez do degradê reto de uma célula. Cada oitava some sozinha quando um pixel cobre mais
  que ~metade do seu comprimento de onda (sem aliasing; de longe o planeta volta a ser liso). O mesmo ruído nos dois shaders, na mesma
  posição fixa (a malha recebe a longitude e a latitude do ponto de construção em `MeshFrame`), então não há salto na troca de dimensão.
  Custo medido (`-PshOnly=earthdetail`, fps médio de 100 quadros, 1.000/3.000/10.000 de altitude, vsync em 260): antes 237/234/160/222/231/230,
  depois 228/210/163/220/250/247, sem diferença fora do ruído (o gargalo é o DH e a CPU); a malha não ganhou vértice nem tempo de
  reconstrução. Limite: o detalhe é textura, não dado novo: costas e lagos menores que 320 blocos aparecem inventados (plausíveis), não
  reais. O terreno real de perto continua sendo o do DH e dos chunks.
- **Piloto automático criativo** (`space/travel/Autopilot`): `fly moon` e `fly earth` travavam o servidor (ida: 7 avisos "Can't keep up" de 2 a
  14 s subindo pela dimensão da Terra a 80 b/tick, mais 4 na chegada à Lua e 3 na volta, 14 no total). Agora o voo sai da dimensão do
  corpo de origem a 20% da altitude de saída (1.000 na Terra, 250 na Lua, como a saída antecipada do foguete rápido) e fica na dimensão de
  espaço, com o jogador ESTACIONADO no ponto de `SpaceParking` e a posição real mandada ao cliente por `AutopilotSkyPayload` a cada tick
  (`client/travel/AutopilotView` → `ShipView`: o céu e os corpos saem dela); volta à dimensão do corpo de destino a 80% da altitude de saída
  (`Autopilot.wantSpace` decide, com a assimetria origem/destino que evita ir e vir). Um jogador que atravessava a dimensão de espaço de
  verdade a 80 b/tick fazia ticks de até 69 s: o estacionamento é o que resolve, como no foguete. Ao terminar, parar (`fly stop`) ou sair
  do mundo o jogador volta ao ponto real. O terreno do ponto de chegada é pedido (raio 2) enquanto o voo ainda está no espaço. Medido
  (`-PshOnly=moontrip`): 14 avisos antes, 0 depois, na ida e na volta.

## Naves (fase 1 do `docs/PLANO_NAVES.md`)

Naves de blocos andáveis, que voam no espaço. A nave é feita de blocos REAIS parados numa célula do estaleiro (`spacehorizons:shipyard`) e o que
voa é o estado virtual dela (órbita e atitude, `ship/ShipFlight`), com a mesma física do foguete (integrador, SAS, esfera de influência). Quem está
dentro anda normalmente (gravidade interna sempre ligada, luz só de bloco) e NÃO é empurrado pela aceleração: a nave é o referencial; o céu
pelas janelas (Terra, Lua, sol redondo, estrelas) gira com a atitude da nave.

- **Blocos** (aba "Space Horizons"): `ship_bridge` (ponte de comando; clicar mostra massa, empuxo, Δv e combustível do censo), `ship_impulse_engine`
  (8 de empuxo, Isp 1.000; empurra para o lado oposto ao bocal), `ship_window` (textura conectada: `docs/TEXTURAS_CONECTADAS.md`), `ship_seat` (assento do piloto) e `ship_airlock` (eclusa de uma célula, abre e fecha com o botão
  direito; o modelo é o mesmo aberta ou fechada). O combustível é o dos tanques do foguete (`rocket_fuel_tank`). Receitas simples e entradas no JEI.
- **Censo e especificação** (`ShipCensus`, `ShipSpecs`, `ShipCensusScanner`): conta os blocos da caixa da nave por papel, em lotes, lendo a paleta
  de cada seção; massa por bloco (ponte 2 t, motor 1,5 t, tanque 0,3 t, qualquer outro bloco 0,02 t), empuxo dos motores e capacidade dos tanques
  viram o mesmo `RocketStats` do foguete (Δv, TWR). `/spacehorizons ship census [id]` imprime os números.
- **Voo virtual** (`ShipFlight`, `ShipFlightTicker`): a cada tick do servidor cada nave que voa avança um passo; o estado fica no `ShipRecord`
  (grava de 100 em 100 ticks e ao parar o servidor); o combustível do voo e o dos tanques de blocos se acompanham (um galão despejado num tanque
  entra no voo). A PROA é definida pela ponte (o lado oposto ao `facing` dela) e cada motor de impulso empurra para o lado oposto ao bocal
  (`facing`); a soma vetorial dos motores é o empuxo (`ShipArrangement`, sem torque). O servidor manda o estado virtual a quem está dentro a cada 2 ticks
  (`ShipStatePayload`) e a telemetria ao piloto a cada 4.
- **Pilotar** (`ShipSeatEntity`, `client/ship/ShipPilot`, `ShipPanel`): clique direito no assento senta (uma entidade invisível e parada carrega o
  jogador; ninguém é teletransportado). As teclas são as do foguete: W/S arfagem, A/D guinada, Q/E rolagem, Shift/Ctrl sobem e descem o acelerador
  (alavanca: fica onde se deixa), Z cheio, X corta, T muda o SAS, G levanta do assento. O painel mostra altitude, velocidades, apoastro, periastro,
  inclinação, tempo até o apoastro, acelerador, combustível, TWR, Δv e SAS. Um piloto por nave; os outros andam livres. Quem se levanta deixa a
  queima ligada por 30 s (depois a nave corta o motor sozinha).
- **Nave de teste de criativo**: `/spacehorizons ship create flight` cria uma nave de 32x20x56 com ponte, três assentos, quatro motores, oito
  tanques cheios, janelas (parede de vidro contínuo), lanternas no teto e no piso (salas claras) e um anteparo com eclusa, já em órbita circular da Terra a 30.000 blocos (a Terra à direita); `ship goto <id>` entra.

**Fase 2a-i:** a nave monta no chão pela ponte (`ShipAssembly`, sem exigir tanque). Para montar: agache-se, de mão vazia, sobre uma `ship_bridge` fora do estaleiro, numa plataforma. A nave precisa de pelo menos um `ship_impulse_engine` com o bocal para baixo (ou um motor de foguete) para decolar; sem ele, a montagem é recusada antes de qualquer bloco sair. Depois de montada você senta nela e decola com o acelerador (empuxo dos bocais para baixo mais os motores de foguete, combustível dos tanques do retrato). Ao cruzar a altitude de saída (5.000 blocos na Terra) a nave vira blocos no estaleiro: a entidade congela, o job leva a nave para a célula (baús, tanques e mB iguais), o piloto senta na ponte e o voo segue no estado virtual da nave. Ver `docs/PLANO_NAVES.md`.

Autotestes: `-PshOnly=shipflight` (piloto de teste no servidor: censo, órbita estável, atitude, SAS, queima, combustível, gravação),
`-PshOnly=ship` (a nave inteira em jogo: janelas, sentar, girar com as teclas, SAS prógrado, queimar, levantar e andar durante a queima) e
`-PshOnly=shipground` (nave montada no chão pela ponte: recusa sem motor, retrato, mundo, piloto sentado e subida de 500 blocos; prints em `run-test/screenshots/autotest_sg_*`).
`-PshOnly=shipascent` (subida até a altitude de saída, conversão no estaleiro, baú, tanques, coluna, piloto na ponte e voo virtual; prints em `run-test/screenshots/autotest_sa_*`).

## Base lunar (etapa 4 da sobrevivência)

Salas seladas com ar, energia simples, recursos lunares e combustível produzido na Lua (design e números em `docs/PLANO_SOBREVIVENCIA.md`,
"Etapa 4"). Blocos: `solar_panel`, `battery`, `power_cable` (energia por adjacência, 8 UE/t por painel ao sol), `oxygen_generator` (pressuriza
a sala fechada em volta, até 1.000 blocos de ar; dentro dela não se gasta o tanque do traje nem se congela), `electrolyzer` (gelo vira
hidrogênio e oxigênio), `lunar_refinery` (gelo vira combustível do Tier 1: 10 gelos enchem um tanque de foguete) e `helium3_extractor`
(regolito vira hélio-3). O gelo lunar (`lunar_ice_ore`) fica sob o fundo das crateras de latitude 55 graus ou mais. Teste:
`./gradlew runClientTest -PshOnly=moonbase` (prints `autotest_moonbase_*`).

## Gerador de planetas por parâmetros e Marte (etapa 2 dos tiers)

Cada planeta é um JSON em `src/main/resources/data/spacehorizons/planet/<id>.json` (formato em `docs/GERADOR_PLANETAS.md`): circunferência,
gravidade, relevo (planícies, terras altas, crateras em várias escalas com densidade e tamanho, cordilheiras e vulcões opcionais, calota
polar), paleta de superfície e subsolo com blocos de reserva, água, atmosfera (densidade, espessura, cor do limbo, céu, neblina, nuvens, se
tem oxigênio), veios de minério e duração do dia. `planet/spec/PlanetSpec` lê e valida; `planet/terrain/ParametricTerrain` (a
generalização do antigo `MoonTerrain`) faz o relevo, e chunks (`PlanetChunkGenerator`), LODs do DH (`PlanetLodGenerator`), malha orbital
(`PlanetMeshPalette`), limbo da atmosfera e oxigênio leem os mesmos parâmetros. A Lua é descrita pelo mesmo formato e o relevo dela é
idêntico ao de antes (soma de controle no teste); a Terra continua vanilla.

**Marte** (`spacehorizons:mars`, só em mundos novos com o tipo "Space Horizons"): R ≈ 55.412 (0,53 da Terra do mod, a razão real),
gravidade 0,38, dia de 24.660 ticks, céu caramelo e atmosfera fina sem oxigênio (o traje trata como vácuo), poeira no ar, crateras, vulcões
em escudo, calotas de gelo e veios de titânio. Os blocos `mars_regolith` e `mars_titanium_ore` do Tier 2 entram sozinhos quando existirem;
até lá valem `red_sand`, terracota e `iron_ore`. Marte ainda não está na física orbital: chega-se por `/spacehorizons goto mars [x z]`, e o
céu tem o sol e as estrelas (sem Terra nem Lua). Autoteste: `./gradlew runClientTest -PshOnly=mars` (10 verificações; prints `autotest_mars_*`:
chão, céu de dia, pôr do sol e noite, cratera, veio de minério, 1.000 e 5.000 de altitude, vulcão).

## Referenciais: inercial (I) e preso à Terra (E)

A física do foguete e a órbita da Lua vivem num referencial INERCIAL (**I**); tudo que é "lugar" (dimensões, `SpaceDimension`, renderização,
coordenadas do jogador, piloto automático criativo) continua num referencial PRESO À TERRA (**E**, o de `SolarSystem.poseAt`). A ponte é
`space/InertialFrame`: `r_E = Ry(φ)·r_I`, com `φ = ângulo do sol − π` (eixo polar +Y). O sol de E é exatamente o de `SunModel` (nada
no céu do sol mudou); em I ele fica fixo, em −X, e a Terra gira para o leste. Em `clock = 18.000` (meia-noite do dia 0) E e I coincidem.

- **Rotação da Terra**: `φ̇` é a taxa do sol do vanilla (curva Bézier), então varia de 0,67 a 1,19 vez a média 2π/24.000 (mais lenta ao
  meio-dia). A velocidade do chão em I é `φ̇·R·cos(lat)`: no equador **18,2 a 32,5 b/tick, média 27,3** (um foguete parado na pista tem
  isso em I). Lançar para o leste é mais barato: a velocidade circular de uma órbita baixa (6.000 de altitude, 88,8 b/t) custa
  88,8 − 27,3 = **61,5** de Δv para o leste e 88,8 + 27,3 = **116,1** para o oeste, em média (56,3 a 121,3 conforme a hora; sem perdas por
  gravidade e arrasto). Foi escolhido manter a taxa do sol, e não uma rotação uniforme de 2π/24.000, para o sol ser fixo em I: com taxa
  uniforme o sol vanilla "tremeria" em I e, na Lua, o sol andaria para trás por instantes uma vez por dia. Trocar é mudar `InertialFrame.angle/rate`.
- **A Lua** (`SolarSystem`): círculo kepleriano em I, raio 600.000, inclinação 5°, período `2π·sqrt(a³/μ_Terra)` = **98.983 ticks (4,12 dias do
  jogo)**, prógrada (para o leste), **38,09 b/t** em I. Em E ela anda de **66,4 a 148,1 b/t** (a Terra gira por baixo). Rotação sincronizada
  (a mesma face para a Terra; o chão dela gira 1,66 b/t no equador em I). Fase inicial: o tick 18.000 do dia 0 é lua cheia à meia-noite (Lua em
  +X, sol em −X). Como o sol é fixo em I, o ciclo de fases é o período orbital (4,12 dias; o vanilla tem 8 fases em 8 dias, aqui não); em E a
  Lua nasce a leste a cada **31.677 ticks (1,32 dia)**. `SYNODIC_PERIOD_TICKS` virou `ORBITAL_PERIOD_TICKS`.
- **Foguete** (`space/flight/*`, `FrameBridge`): o `FlightState` é inercial (I). Posição E↔I por rotação por `φ`; velocidade
  `v_I = Rᵀ·v_E + ω × r` (`FrameBridge.rotationVelocity`). Apoastro, periastro, SOI e `TransferPlanner` são em I; pouso suave, batida, velocidade
  horizontal ao chão, arrasto e aquecimento usam a velocidade RELATIVA ao chão/ar (a atmosfera gira com a Terra). O SAS prógrado/retrógrado usa a
  velocidade ao chão dentro da dimensão do corpo e a inercial no espaço. Perto da Lua o corpo de referência é a Lua (pose inercial dela: o
  chão gira com o período orbital e o pouso usa a velocidade relativa ao chão lunar).
- **Transferência Terra→Lua** (planejador): a Lua é uniforme em I, então a fase ideal é constante (98,0°) e a transferência é a mesma a qualquer
  hora: Δv de TLI de **26,63 b/t** a partir da órbita baixa, 22.539 ticks de voo, entrada na esfera de influência 17.008 ticks depois da queima com
  **15,9 b/t de velocidade relativa** (antes, com a Lua cinemática em E: 83 a 146). É preciso orbitar para o **leste** (o planejador avisa
  "Órbita para oeste: vá para leste").
- **Dimensão de espaço**: a posição é E (como antes); o foguete é integrado em I e a posição na dimensão vem de I→E a cada tick (uma órbita
  baixa para o leste anda a ~63 b/t em E e 89 em I). Um foguete que segura a atitude inercial (SAS "estabilizar") gira devagar, com a Terra,
  nas coordenadas da dimensão (o céu do espaço é o de E).
- **Piloto automático criativo** (`space/travel/*`, `/spacehorizons fly`): é cinemático e continua em E (o servidor só enxerga E, e é o deslocamento
  em E por tick que custa chunks). Como em E a Lua anda 66 a 149 b/t, o plano não a persegue: mira o ponto de INTERCEPTAÇÃO
  (`FlightPlan.interceptTime`: o menor τ com `|Lua(t+τ) − nave| = v·τ`, varredura + bisseção) e assim o teto de velocidade deixa de precisar
  passar da Lua. O teto caiu de 200 para **80 b/tick**: a mira por interceptação alcança a Lua com qualquer teto. Medido em jogo (autoteste `moontrip`, relógio correndo): ida em 9.784 ticks de cliente (troca para a dimensão da Lua no tick 7.985) e volta em 6.528, chegando nos alvos (face da Lua voltada para a Terra; zênite do spawn). Nos ~3 min de voo sobre o planeta o servidor ficou atrasado ("Can't keep up", até 14 s) com a thread cheia em `ServerChunkCache.getChunk` (geração de chunks da Terra sob a rota), e no espaço vazio o tick voltou a 14 a 30% de ocupação; o custo por tick em função do teto NÃO foi remedido aqui (as medições de 60 b/t = 1,5 ms e 200 b/t = 24 ms são as de antes).
- **`/time set`**: move o sol e, agora, também a rotação E↔I. O estado de voo é guardado em I e não muda; só a posição E é re-derivada, então um
  foguete em voo cujo relógio salta horas vê o chão "pular" e pode bater ou aparecer longe. Não há tratamento especial (limitação aceita).

## Escala: Terra 10x maior (limitações)

A Terra tem circunferência 655.360 (R ≈ 104.304) e a Lua 163.840 (R ≈ 26.076) a 600.000 de distância; antes eram 65.536, 16.384 e 60.000.
**Mundos criados antes dessa mudança são incompatíveis** (mapa, terreno, chunks da costura e posições mudam): crie um mundo novo.
Alturas e a atmosfera não mudaram (saída para o espaço a 5.000 na Terra e 1.250 na Lua, atmosfera de 1.800).

- **Foguete**: a velocidade orbital da Terra subiu √10 (28,9 para 91,3 b/tick, ~1.827 b/s) e o Isp do motor também (500 para 1.600, ve = 128 b/t).
  Com isso o foguete mínimo (Δv 126) faz a órbita com um programa de subida bom (gasta ~111, sobram ~14); antes só fazia salto suborbital.
  A transferência Terra→Lua (TLI) leva 22.539 ticks (~19 min) e custa 26,6 b/t de Δv a partir da órbita baixa; com a Lua inercial (38 b/t, ver "Referenciais")
  chega-se à esfera dela com 15,9 b/t de velocidade relativa (com a Lua cinemática em E eram 83 a 146, e antes da escala ~6), a captura custa algumas
  dezenas de b/t no máximo (a velocidade de escape da Lua é 26 b/t na superfície). A pista da Terra já tem ~27 b/t para leste em I.
- **Foguete em velocidade orbital** (`rocket/ZoneSwitch`, ver `docs/DESIGN_FOGUETE.md`): a dimensão da Terra só carrega o terreno a voo lento e baixo, então o foguete que desce fica na dimensão de espaço (vazia; arrasto, calor e paraquedas já valem lá, a atmosfera vai até 1.800) até 1.200 blocos sobre o terreno, e só entra com horizontal ao chão ≤ 25 b/tick (entrada forçada a ≤ 600 sobre o terreno, qualquer velocidade). Sai para o espaço cedo se passar de 40 b/tick acima de 1.000 de altitude e de 1.500 sobre o terreno. Medido na primeira versão (troca a 4.000): 0 avisos "Can't keep up" (antes 6, de 2 a 7,7 s) e tick máximo de 292 ms (antes 1.557 ms).
- **Pré-carga do pouso** (`rocket/ImpactPredictor`, `LandingPreloader`, `LandingPreloadDriver`): descendo e abaixo de 5.000 de altitude, uma vez por segundo o impacto é previsto integrando a física de voo (gravidade e arrasto, sem empuxo nem paraquedas; até 1.200 ticks) e são pedidos, com tickets assíncronos `PORTAL` de raio 0, os ~7×7 chunks do impacto e o trecho final (últimos 1.500 blocos) da trajetória: no máximo 40 chunks novos por segundo e 96 pendentes; o que a previsão nova larga é solto, e tudo ao pousar ou bater. O painel mostra "Pouso: carregando N%" / "Pouso: pronto".
- **Voo rápido do piloto** (`rocket/FastFlight`, `client/rocket/FastFlightClient`, `dh/DhFastFlightConfig`): acima de 20 b/tick ao chão (volta abaixo de 10) o cliente do piloto reduz a distância de visão para 4 e a de simulação para 5 chunks e pausa a geração de LOD do Distant Horizons (a API 7.2 não tem pausa de fila: o override é `GeneratorPlan = DISABLED`; os LODs gerados continuam desenhados e o que já estava em geração termina). Tudo volta ao frear, pousar, descer do foguete ou sair do mundo.
- **Passo segurado** (`HELD`): quando o portão de chunks segura o passo, o foguete sincroniza `HELD`; o cliente pula para o último ponto recebido, fica ali e zera a velocidade. A causa do "vai e volta" gravado (fase 1 de diagnóstico, `JumpProbe` + `tools/analyze_jump.py`) era outra e está resolvida pelos três itens abaixo.
- **Foguete estacionado no espaço** (`rocket/SpaceParking`, `RocketFlightController`, `client/space/ShipView`, `SkyObserver.of`): na dimensão de espaço o foguete, o piloto e os passageiros ficam PARADOS num ponto fixo da dimensão (um por foguete, escolhido pelo UUID numa grade a partir de X/Z = 8.000.000, num chunk só, mantido por ticket); a posição real do voo vive só no estado inercial (`space/flight`, `FrameBridge`) e é sincronizada à parte (`RocketEntity.virtualSpacePosition`, dado `VIRTUAL_POS`, float; salva no NBT para atravessar a troca de dimensão). O céu (Terra, Lua, sol, estrelas) é desenhado por `SkyObserver.forShip` na posição real e com atitude IDENTIDADE (o foguete gira, o céu não gira com ele; a câmera do piloto continua livre), então nada se move na dimensão: sem passo segurado, sem chunk gerado e tick de ~0,7 ms a 20, 54 e 90 b/tick. A telemetria e o painel seguem mostrando altitude e velocidades reais. Terra→espaço entra no ponto estacionado (sem tela de carregamento); espaço→Terra (`ZoneSwitch`) sai na posição real convertida para a dimensão da Terra. A pré-carga do pouso (`LandingPreloader`, que usa o estado de voo) e o acelerador (`RocketFlight.control`) não mudam. **Limitação**: quem NÃO está num foguete na dimensão de espaço (`goto space`, voo criativo) continua na posição real e NÃO vê um foguete estacionado na posição certa (o foguete está no ponto fixo, a ~8 milhões de blocos); desenhar os outros foguetes na posição relativa fica para depois (`docs/PLANO_NAVES.md` 4.3).
- **Corredor contínuo de chunks** (`rocket/ChunkGate`, `ChunkGate.Requester`): na dimensão do corpo, em vez de sondas esparsas (ticks 1, 2, 4, 8, 16 e 32 à frente, que deixavam os chunks entre elas para a janela de 4 ticks, curta demais para a geração: anda um passo, espera outro, 50% de HOLD), o servidor pede a PEGADA do foguete ao longo da trajetória prevista, sem buracos, com antecedência de 12 a 80 ticks (4 mais o dobro da latência de geração medida entre pedir e carregar). Teto de 32 pedidos novos e 192 pendentes por tick (os que seguram o passo, a pegada nos ticks 0, 1, 2 e 4, são sempre pedidos), tickets renovados a cada 15 ticks e esquecidos fora do corredor; o orçamento encolhe com o tempo médio do tick do servidor (cheio até 50 ms, mínimo a 120 ms). **Limite medido**: nesta máquina o gerador do planeta sustenta ~370 a 390 blocos/s (~14 ms de thread principal por chunk novo); a 20 b/tick (400 b/s) o HOLD sumiu, acima disso o limite é o gerador e o servidor passa a segurar (54 b/tick) ou a andar com ticks mais lentos (90 b/tick).
- **Foguete e piloto andam juntos no cliente** (`mixin/travel/RocketPositionMixin`, `RocketEntity`): os pacotes de posição do foguete (sincronia, teleporte e delta) viram sempre um alvo de interpolação de UM passo por tick; o vanilla dava `snapTo` no foguete (mas não no piloto) quando o alvo estava a mais de 64 blocos, e o foguete aparecia até 80 blocos adiantado, atrasado ou andando para trás em relação à câmera. Saltos de milhares de blocos (a costura do planeta) zeram a posição anterior do foguete e do passageiro juntos.
- **Medido antes × depois** (autoteste `-PshOnly=jump<vel>` no espaço, `jumpe<vel>` dentro da dimensão da Terra a 700 de altitude, `jumpc` o voo C real; 40 s, 3ª pessoa, distância de visão reduzida pelo voo rápido; saltos = quadros com a distância câmera-foguete a mais de 10 blocos da mediana):

  | Cenário | Antes: HOLD / saltos | Depois: HOLD / saltos |
  | --- | --- | --- |
  | espaço 20 b/tick | 21% / 0 | 0% / 0, 0 chunks gerados, tick 0,7 ms |
  | espaço 54 b/tick | 50% / ~1% | 0% / 0, 0 chunks gerados, tick 0,8 ms |
  | espaço 90 b/tick | 50% / 0–0,1% | 0% / 0, 0 chunks gerados, tick 0,8 ms |
  | voo C real (decolagem à órbita) | 35% / 0–0,8% | 0,6% (6 de 927 ticks) / 0 |
  | Terra 20 b/tick (200 b/s antes) | 50% / 0,2% | 0% / 0 (336 b/s, 16,8 ticks/s) |
  | Terra 54 b/tick | 50% / 11,4% (3,5% dos quadros sem terreno) | 17% / 0 (375 b/s, 8,4 ticks/s) |
  | Terra 90 b/tick | 50% / 0,7% | 44% / 0 (371 b/s, 7,4 ticks/s) |

  Na Terra a velocidade média continua limitada pelo gerador (~380 b/s) a 54 e 90 b/tick; o ganho ali é a sincronia (0 saltos) e HOLD abaixo de 50%.
- **Medido no autoteste `flight`** (voos A, B, C e D; D = como C, sem o freio de horizontal no espaço): 0 avisos "Can't keep up" antes e depois; picos de tick na entrada na Terra de 638 ms (antes, entrada a 3.500 de altitude) para 170 a 360 ms (agora, entrada a 1.200); maior janela de 200 ticks de todo o autoteste 667 ms antes e 324 ms depois. Os 266 a 363 ms que restam na entrada são um `getChunk` bloqueante do `positionRider` do piloto. Os voos A a D pousaram suave. O render da Terra vista da dimensão de espaço a 1.200 a 3.000 (prints `fl_d1`, `fl_d2`) é a malha do planeta, borrada (células de 320 blocos), sem falhas.
- **Mapa do planeta**: 2048 colunas (célula de 320 blocos); 4096 levaria 487 s de bake.
- **Voo rápido** (medido no espaço vazio, 200 ticks): 60 b/tick (1.200 b/s) média de 1,5 ms por tick; 200 b/tick (4.000 b/s) média de 24 ms, 17 ticks acima de 100 ms e um "Can't keep up" de 2 s (47 mil chunks); 300 b/tick, tick de 4,8 s. O teto do piloto automático agora é 80 b/tick e a viagem Terra–Lua (`fly moon`, `fly earth`) foi exercitada em jogo (ver "Referenciais").
- **Dimensão de espaço**: Y vai de ~-580.000 a ~+620.000 (viés de 20.000 mantido); o lado do nadir da Terra fica em Y negativo.

## Como rodar

Requisitos: JDK 25 (o Gradle baixa o toolchain se preciso).

```bash
./gradlew runClient      # cliente de desenvolvimento
./gradlew runServer      # servidor de desenvolvimento (exige aceitar a EULA você mesmo)
./gradlew test           # testes unitários (JUnit 5)
./gradlew build          # compila, testa e empacota
```

O `runClient` já traz o Distant Horizons no `localRuntime` do build. Não rode
os comandos acima sem ter lido a EULA do Minecraft.

## Como testar

1. Entre no mundo como operador (nível 2) e rode `/spacehorizons goto earth`.
   Você nasce na superfície em `(0, 0)` da dimensão `spacehorizons:earth`.
2. Ande reto em X para o leste. A costura fica em `x = 327680` (metade da
   circunferência). Para chegar mais rápido, use `/tp @s 327600 ~ 0` e ande um
   pouco mais.
3. Ao cruzar a costura você deve reaparecer em `x = -327680`, com a mesma direção.
4. Olhe para o horizonte: a curvatura deve aparecer nos chunks (o terreno some
   atrás do horizonte em vez de ficar plano). Com o DH instalado, os LODs distantes
   devem usar as mesmas cores.
5. Ande em Z até perto de ±70°: a barreira polar deve empurrar você de volta.

### Descida e pouso da nave

Abaixo da altitude de entrada (4.000 na Terra) e descendo, com todos de bordo sentados, a nave do estaleiro vira entidade na Terra
quando cumpre o critério de entrada do foguete (até 1.200 blocos sobre o terreno com no máximo 25 b/t de horizontal, ou abaixo de
600). Com alguém em pé, a descida é recusada e a nave fica segura na altitude de entrada. O pouso é o do foguete: suave com até 1 b/t
na vertical, 0,5 na horizontal e 15° de inclinação; pousada, a tecla de desmontar (ou `rocket disassemble`) devolve os blocos ao
chão. Autoteste: `./gradlew runClientTest -PshOnly=shipdescent` (sobe, circulariza, desce, pousa e confere os blocos contra a foto
do chão).

### Convés andável (spike S5)

Num foguete em movimento no planeta, o jogador pode ficar a pé dentro da entidade (`docs/PLANO_NAVE_ATMOSFERA.md`): a posição dele é guardada
e simulada nas coordenadas do retrato, no cliente e no servidor, e o movimento da nave entra só como transformação na hora de desenhar e de
sincronizar. Por enquanto o embarque é manual, só para teste: dentro da caixa de uma nave, `/spacehorizons deck board` põe você no convés
(`deck leave` tira, `deck info` mostra as contagens). Autoteste (um robô anda em quadrado por 2.000 ticks na nave que sobe, paira e desce a 1, 10,
50 e 100 b/t girando e inclinada, com e sem 100 ms de latência por sentido): `./gradlew runClientTest -PshOnly=shipdeck`.

### Âncora no ar (spike S6)

Uma nave parada no ar vira blocos reais no ar (`docs/PLANO_NAVE_ATMOSFERA.md`): `/spacehorizons ship anchor` (a nave que você pilota, a do convés
onde anda, a que olha ou a mais perto) confere o volume livre (ar em cada bloco e um bloco de ar em volta), acomoda a nave na grade em meio segundo e
converte em lotes; quem está no convés fica no mesmo lugar, em cima dos blocos. Enquanto ancorada, areia e cascalho não caem, fluidos não escorrem e
mobs não nascem nela. Para voar de novo, agachado e de mão vazia na ponte (ou `/spacehorizons ship unanchor`, que deixa todos em pé no convés).
Autoteste: `./gradlew runClientTest -PshOnly=shipanchor`.

### Outras naves e foguetes no céu (fase 2d)

Quem está no espaço, num foguete estacionado ou dentro de uma nave do estaleiro vê as outras naves e os foguetes próximos (até 20 km) na posição relativa
certa, com o casco externo (pedido ao servidor dentro de 5 km; além disso, uma caixa mínima). Estado a 5 Hz e casco em partes pela rede; desenho em
`client/space/VesselRenderer`. Comandos de dev: `/spacehorizons vessel rocketboard <nave> <dx> <dy> <dz>`, `vessel yaw`, `vessel freeze on|off`, `vessel list`.
Autoteste (caixa na tela medida em pixels contra a calculada): `./gradlew runClientTest -PshOnly=shipsee`. Resultado em `docs/PLANO_NAVES.md`.

### Regressão em paralelo

```bash
./gradlew regression --console=plain > regression.log 2>&1
```

Roda os trechos `ship`, `shipflight`, `shipconvert`, `shipground`, `shipascent`, `shipdescent` e `multiblock` ao mesmo tempo, cada um no seu cliente e na
sua pasta `run-test/<trecho>/` (mundo, prints e `logs/latest.log` próprios), e no fim imprime uma tabela com quantas
verificações passaram e falharam em cada trecho. A tarefa falha se algum trecho falhar ou não registrar nenhuma verificação.
`-PshParallel=2` muda quantos clientes abrem juntos (padrão 3; cada um com até 5 GB de heap) e `-PshSuites=ship,multiblock`
troca a lista; um nome que o `AutoTest` não conhece é recusado antes de abrir o jogo (ele rodaria o roteiro inteiro).

### Autoteste em jogo

```bash
./gradlew runClientTest --console=plain > run-client.log 2>&1
```

Leva uns 15 minutos. O roteiro (235+ passos, prefixos dos prints):

- `a_*`: Terra vanilla em 6 pontos (biomas, árvores, relevo), caverna (o teste procura ar coberto sob o spawn) e y=12.
- `b_*`: costura em x=327600/327670 e -327670 (de dia: é meia-noite no spawn), com o log `ALTURAS` (altura da superfície de 4 em 4
  blocos, salto máximo) e uma travessia real voando para leste.
- `c_*`: latitude em z=0, 80000, 120000 e -120000 (trópico, temperado, neve, gelo).
- `e_*`: a Lua vista do spawn no relógio em que ela está mais alta em cada um de cinco dias (fases 1,00 a 0,06); o log `ORBITA`
  traz posição no espaço, azimute, elevação, ângulo orbital e fração iluminada.
- `d_*`: malha orbital a 5.000 e 15.000 blocos (e a 90° de longitude e no polo) depois de esperar o mapa (`MAPA do planeta pronto`).
- `tr_out_*`, `tr_back_*`: `/spacehorizons fly moon` e `fly earth` com o tempo correndo (a Lua se move durante o voo); prints a cada
  3 ticks em volta da troca; o log registra `TROCA DE DIMENSAO`, `TELA ABERTA` (não deve aparecer), a velocidade relativa depois da
  troca e a altitude de chegada (`CHEGADA`).
- `g_*`: na Lua, a Terra no céu na direção calculada (zênite da face voltada para ela), em quatro relógios (fases opostas às da
  Lua), pulo e chão à meia-noite. `h_*`: chegada na Terra.

O autoteste roda em `run-test/`, separado do `run/` do jogo normal: antes de abrir, a tarefa `prepareClientTest` recusa se outro autoteste está com um mundo aberto, apaga `run-test/saves/SpaceAuto` e os prints `run-test/screenshots/autotest_*`. A regra `advance_time` é desligada e o horário
fixado com `/time set`, exceto durante os voos.

Combinações (as flags `-P` ficam no `build.gradle`):

| Configuração | Comando |
| --- | --- |
| Padrão (DH) | `./gradlew runClientTest --console=plain` |
| Sem DH | `./gradlew runClientTest -PshDh=false --console=plain` |
| Sodium | `./gradlew runClientTest -PshSodium=true --console=plain` |
| Iris, shaders desligados | `./gradlew runClientTest -PshIris=true --console=plain` |

Copie os prints de `run-test/screenshots/`
antes da próxima. Procure `ERROR`, `Couldn't compile pipeline` e `Mixin apply` no log.

## Limitações da fase 4

- **Decoração perto da costura**: carvers de caverna, árvores e minérios usam sementes por chunk, não são periódicos. Terreno,
  clima e bioma batem na costura (faixa de mistura de 2.048 blocos), mas árvores e cavernas diferem entre os dois lados.
  Estruturas são suprimidas a 640 blocos da costura.
- **Relevo na faixa da costura** é a mistura de dois terrenos independentes, não terreno vanilla puro (~3% da Terra).
- **Mapa do planeta**: células de 320 blocos (rios e ilhas pequenas somem), bioma amostrado no ponto exato, cores por tabela
  aproximada (`BiomeColors`). A calota além de 70° é pintada de gelo e aparece como uma mancha clara de contorno retangular visto
  de 15.000 blocos sobre o polo. O primeiro cálculo leva ~140 s; depois sai do cache em disco.
- **Spawn de teste**: `/spacehorizons goto earth` leva a (0, 0), que é oceano tropical com uma ilha de badlands.
- **Lua em órbita**: em I é uniforme (38,09 b/t); em E (preso à Terra) anda de 66 a 148 b/t porque a taxa de giro da Terra segue a curva do sol do
  vanilla; as fases são exatamente periódicas (ciclo de 4,12 dias). O sol da Lua oscila ±5° de declinação por período orbital (o eixo da Lua é a
  normal da órbita).
  No primeiro quadro depois de uma troca de dimensão, o relógio do cliente novo pode ainda não ter chegado.
- **Piloto automático**: a velocidade é 0,03 b/tick por bloco de altitude, com teto de 80 b/tick (não precisa passar da Lua: mira a interceptação,
  ver "Referenciais"); a Terra–Lua leva ~9.000 ticks (testes unitários) (medido em jogo: 9.784 na ida e 6.528 na volta).
  A chegada é parada a 300 blocos sobre o chão.
- **DH**: um `NullPointerException` interno do DH (`FullDataUpdatePropagatorV2`) aparece no log no carregamento do mundo; não
  é do mod. Logo depois de um `/tp` longo, o DH pode mostrar por instantes LODs em posição errada até os chunks próximos carregarem.
- Testes unitários não carregam o `DensityFunction` (precisa do Bootstrap do FML); a reescrita `SeamRewrite` e os JSON só são
  exercitados em jogo.

## Limitações da fase 3

- **Verificação**: a viagem Terra para Lua para Terra foi vista em jogo (autoteste, prints a cada 3 ticks em volta das
  duas trocas): sem tela de carregamento, sem `moved too quickly`, mesma esfera, cor, tamanho e orientação antes e depois.
  Duas correções finais NÃO foram revistas em jogo por terem sido feitas depois da quinta e última rodada: as estrelas
  do primeiro quadro depois da ida Terra para Lua (usam agora a posição do jogador, e não a da Camera, que ainda está no
  mundo antigo) e o teste unitário da orientação vertical (`BodyTransferTest`). Na volta as estrelas já coincidiam.
- **Servidor no relógio real**: o sol por ponto só vale no cliente (renderização, céu e luz do chão). Spawn de mobs, camas,
  queimadura de mortos-vivos, `skyDarken` e afins seguem o relógio global do servidor. Na Lua o servidor vê sempre claro
  (o timeline lunar não mexe no nível de luz do céu do servidor).
- **Sol por ponto**: o horário efetivo vem só da altura do sol, então perto dos polos o brilho de nascer/pôr pode trocar de
  lado de repente. Os atributos são amostrados uma vez por tick; numa viagem de milhares de blocos por tick o céu atrasa 1 tick.
- **Olhar vertical na troca**: o Minecraft não tem rolagem de câmera; olhando a menos de ~1,1° da vertical, o yaw sai do
  vetor "para cima" da câmera e a direção do olhar pode errar até ~2°. Mais longe da vertical vale a direção exata
  e a imagem pode girar (o "em cima" de cada corpo é a sua radial).
- **Quadro da troca**: a esfera do próprio planeta (`client/space/BodyRenderer`, só acima da altitude em que a malha
  orbital começa) cobre os quadros em que a malha do nível novo ainda está sendo construída. A mão e o item do
  jogador não aparecem no primeiro quadro depois do respawn.
- **Pulo**: com gravidade 0,165 o pulo vai a ~5 blocos e dura ~56 ticks (a Terra: ~1,25 e ~12). Não há ajuste de
  altura de pulo, de velocidade de queda terminal nem de atrito.
- **Teleporte para dentro de uma esfera de influência**: `/tp` a mais de ~376.000 blocos de altitude na Terra (ou ~94.000
  na Lua) transfere o jogador na hora.
- **Tela de carregamento**: `/spacehorizons goto` continua mostrando a tela (de propósito); só a transferência
  natural e o piloto automático não mostram.
- **Só Terra e Lua** no `SolarSystem` (a Lua orbita desde a fase 4); sem Sol como corpo, sem outros planetas.
- **Servidor dedicado** não foi executado; o anti-cheat de movimento rápido é desligado acima de 128 blocos de altitude
  em planetas por um mixin, mas isso só foi exercitado em singleplayer.
- **Sodium** continua sem rodar no dev (ver abaixo); a Lua e a viagem foram testadas só com o DH e sem Sodium nem Iris.

## Limitações conhecidas

- **Sodium e Iris não rodam no dev com o NeoForge 26.3.0.76**: o Sodium 0.9.2 e o 0.9.3-alpha.1
  (e portanto o Iris 1.11.7, que o exige) travam no próprio mixin deles
  (`LevelExtractorMixin`: `extractVisibleBlockEntities(..., Frustum)` não existe nesse NeoForge).
  É incompatibilidade entre os mods e o NeoForge, não do SpaceHorizons. Nossa curvatura do
  Sodium (P7) **nunca foi executada**; só foi conferido por leitura que o texto do
  `block_layer_opaque.vsh/fsh` e do `globals.glsl` do 0.9.2 bate com o que o patch espera.
  Quando houver Sodium/Iris compatível, rode as linhas Sodium e Iris da tabela. Com shaderpack do
  Iris ligado, o terreno e as entidades do pack não são curvados.
- **Sem DH**: roda sem crash e sem erro de shader. O raio interno da malha ao subir sem DH foi
  corrigido depois da última rodada de jogo (o autoteste mostrava um buraco circular no centro
  entre 600 e 5.000 blocos) e **não foi reverificado em jogo**.
- **Desempenho da malha**: a primeira reconstrução levou ~75 ms (com DH) e ~94 ms (sem DH), acima
  do alvo de 50 ms; as médias seguintes não foram lidas. Reduza `PlanetMeshRenderer.MAX_VERTICES`
  se houver engasgo. `SphericalTerrain` recalcula o ruído em cada consulta (custo 2x).
- **Mistura lat/lon (P4)**: a carta fixa encolhe as formas leste-oeste por cos(latitude) fora do
  equador, e a faixa de transição estica a malha. Nos prints a 15.000, x=0 contra x=3000, as formas
  se mantêm e o mapa gira de forma coerente. Não foi medida a deriva quantitativamente.
- **Entidades**: o frustum culling usa posições planas (só o desenho é curvado); `world_border` não
  é curvada. Perto da câmera (< 48 blocos) a curvatura é atenuada. Os armor stands do autoteste
  aparecem pequenos e escuros nos prints; o assentamento é conferido a olho.
- **Nuvens**: acima de ~2.000 blocos somem pela neblina. Se as nuvens vanilla estavam em OFF, o mod
  as liga (FANCY) enquanto você está no planeta, porque o DH também grava OFF.
- **Sem normais por vértice** na malha orbital: iluminação radial, sem sombra de relevo.
- **Servidor dedicado** não foi executado; build e testes unitários passam.
- **Biomas duplicados**: o mapa `biomes` do datapack (`dimension/earth.json`) deve
  espelhar `SurfacePalette.biome()`. Nada garante isso automaticamente.
- **Costura só para jogadores**: entidades soltas e veículos não atravessam a costura (veículos
  desmontam o jogador). Corrigir exige teleportar o veículo raiz junto.
- **Quantização do DH**: `earthCurveRatio` é inteiro, erro de raio de até ~0,1%.
- **Ruído**: Perlin 3D clássico, não OpenSimplex2.
