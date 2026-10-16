# Space Horizons

Mod NeoForge para Minecraft 26.3 (Java 25) que busca um **espaço contínuo**: planetas
curvos e finitos, integrados ao Distant Horizons (DH), e depois órbita e viagem
interplanetária sem trocar de representação de repente (nada de skybox em PNG).

## Fase 1 (pronta)

Um planeta pequeno e curvo, onde andar reto em X dá a volta:

- **Planeta `spacehorizons:earth`**: circunferência de 65536 blocos (raio ≈ 10430),
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
  para a Terra) alimenta a neblina vanilla e a do DH (`dh/DhFogConfig`), a cor do céu e o
  limbo. O limbo (`shaders/atmosphere/limb.fsh`) amostra só o trecho do raio dentro da atmosfera,
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

- **A Lua** (`spacehorizons:moon`, no tipo de mundo Space Horizons): circunferência 16384 (raio ≈ 2608),
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
| `/spacehorizons fly moon` | piloto automático da Terra à Lua (de 1.000 a 2.700 ticks, conforme onde a Lua está); a troca de dimensão ocorre sozinha a ~8.400 blocos acima da Lua |
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
    (t = |z| / latitude máxima): trópicos no equador, neve perto de ±12.000 blocos, gelo no polo.
  - Estruturas ficam suprimidas a menos de 640 blocos da costura.
- **Mapa do planeta** (`planet/map/`, `network/`): o servidor amostra o gerador real numa grade de 1024x400 (célula de 64 blocos),
  em background (~75 s na primeira vez), guarda em `<mundo>/data/spacehorizons/planetmap_earth.bin` e manda ao cliente em pedaços.
  A malha orbital e as esferas dos corpos usam as cores dos biomas reais desse mapa (`PlanetTerrains.get`); o terreno analítico
  só vale enquanto o mapa não chega e para a Lua. O DH usa o gerador do mundo (não há mais override de LOD na Terra).
- **A Lua orbita** (`space/SolarSystem`, `space/SpaceClock`): órbita circular de raio 60.000, inclinada 5°, período sinódico de 8 dias
  (fases como as do vanilla: cheia à meia-noite do dia 0), no relógio do Overworld (`/time set` move sol e Lua). A Lua tem rotação
  sincronizada: a mesma face fica voltada para a Terra. `SpaceFrame`, `SunModel`, `BodyRenderer` e `SpaceSun` recebem o relógio.
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
  da Terra funcionam sem rotação. A Terra fica em y = 20.000 e a Lua orbita entre y ≈ -40.000 e +80.000 (ver "Limitações").
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

Autoteste: `./gradlew runClientTest -PshOnly=space` (apague `run/saves/SpaceAuto` e `run/screenshots/autotest_*` antes). Prefixos
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

- **Faixa de Y**: com o viés fixo de 20.000, a órbita da Lua leva Y de -40.000 a +80.000 (os eixos da dimensão são os do spawn da
  Terra, e a Lua orbita no plano leste-cima). Por isso `EntityMixin` desliga a morte abaixo do mundo no espaço. Uma convenção que
  pusesse o eixo polar da Terra em Y daria ±15.000 com o mesmo viés, mas exigiria girar o sol e as estrelas do vanilla.
- **Salto na saída a 5.000**: a esfera da Terra (agora até 1.024 segmentos, célula de 64 blocos, a do mapa) coincide em posição,
  cor e halo com a malha orbital, mas some o detalhe dos chunks e do DH do centro da vista (uns 500 blocos de lado).
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

Controles no cockpit: Shift/Ctrl acelerador, Z cheio, X corta, W/S arfagem, A/D guinada, Q/E rolagem, T modo do SAS
(estabilizar, prógrado, retrógrado), P paraquedas, V câmera, G desmontar (pousado).

Autoteste do voo: `./gradlew runClientTest -PshOnly=flight` (salto suborbital, batida e órbita completa com reentrada).

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
2. Ande reto em X para o leste. A costura fica em `x = 32768` (metade da
   circunferência). Para chegar mais rápido, use `/tp @s 32700 ~ 0` e ande um
   pouco mais.
3. Ao cruzar a costura você deve reaparecer em `x = -32768`, com a mesma direção.
4. Olhe para o horizonte: a curvatura deve aparecer nos chunks (o terreno some
   atrás do horizonte em vez de ficar plano). Com o DH instalado, os LODs distantes
   devem usar as mesmas cores.
5. Ande em Z até perto de ±70°: a barreira polar deve empurrar você de volta.

### Autoteste em jogo

```bash
rm -rf run/saves/SpaceAuto run/screenshots/autotest_*
./gradlew runClientTest --console=plain > run-client.log 2>&1
```

Leva uns 15 minutos. O roteiro (235+ passos, prefixos dos prints):

- `a_*`: Terra vanilla em 6 pontos (biomas, árvores, relevo), caverna (o teste procura ar coberto sob o spawn) e y=12.
- `b_*`: costura em x=32700/32760 e -32760 (de dia: é meia-noite no spawn), com o log `ALTURAS` (altura da superfície de 4 em 4
  blocos, salto máximo) e uma travessia real voando para leste.
- `c_*`: latitude em z=0, 8000, 12000 e -12000 (trópico, temperado, neve, gelo).
- `e_*`: a Lua vista do spawn no relógio em que ela está mais alta em cada um de cinco dias (fases 1,00 a 0,06); o log `ORBITA`
  traz posição no espaço, azimute, elevação, ângulo orbital e fração iluminada.
- `d_*`: malha orbital a 5.000 e 15.000 blocos (e a 90° de longitude e no polo) depois de esperar o mapa (`MAPA do planeta pronto`).
- `tr_out_*`, `tr_back_*`: `/spacehorizons fly moon` e `fly earth` com o tempo correndo (a Lua se move durante o voo); prints a cada
  3 ticks em volta da troca; o log registra `TROCA DE DIMENSAO`, `TELA ABERTA` (não deve aparecer), a velocidade relativa depois da
  troca e a altitude de chegada (`CHEGADA`).
- `g_*`: na Lua, a Terra no céu na direção calculada (zênite da face voltada para ela), em quatro relógios (fases opostas às da
  Lua), pulo e chão à meia-noite. `h_*`: chegada na Terra.

Antes de rodar, `rm -rf run/saves/SpaceAuto run/screenshots/autotest_*`. A regra `advance_time` é desligada e o horário
fixado com `/time set`, exceto durante os voos.

Combinações (as flags `-P` ficam no `build.gradle`):

| Configuração | Comando |
| --- | --- |
| Padrão (DH) | `./gradlew runClientTest --console=plain` |
| Sem DH | `./gradlew runClientTest -PshDh=false --console=plain` |
| Sodium | `./gradlew runClientTest -PshSodium=true --console=plain` |
| Iris, shaders desligados | `./gradlew runClientTest -PshIris=true --console=plain` |

Apague `run/saves/SpaceAuto` e `run/screenshots/autotest_*` antes de cada uma e copie os prints
antes da próxima. Procure `ERROR`, `Couldn't compile pipeline` e `Mixin apply` no log.

## Limitações da fase 4

- **Decoração perto da costura**: carvers de caverna, árvores e minérios usam sementes por chunk, não são periódicos. Terreno,
  clima e bioma batem na costura (faixa de mistura de 2.048 blocos), mas árvores e cavernas diferem entre os dois lados.
  Estruturas são suprimidas a 640 blocos da costura.
- **Relevo na faixa da costura** é a mistura de dois terrenos independentes, não terreno vanilla puro (~3% da Terra).
- **Mapa do planeta**: células de 64 blocos (rios estreitos somem), bioma amostrado no ponto exato, cores por tabela
  aproximada (`BiomeColors`). A calota além de 70° é pintada de gelo e aparece como uma mancha clara de contorno retangular visto
  de 15.000 blocos sobre o polo. O primeiro cálculo leva ~75 s; depois sai do cache em disco.
- **Spawn de teste**: `/spacehorizons goto earth` leva a (0, 0), que é oceano tropical com uma ilha de badlands.
- **Lua em órbita**: o ângulo usa a curva Bézier do sol, então a velocidade orbital oscila (8 a 22 blocos/tick); as fases são
  exatamente periódicas. O sol da Lua oscila ±5° de declinação por dia terrestre (o eixo da Lua é a normal da órbita).
  No primeiro quadro depois de uma troca de dimensão, o relógio do cliente novo pode ainda não ter chegado.
- **Piloto automático**: as velocidades de cruzeiro mudam em degraus (por exemplo 38 para 83 blocos/tick logo depois da troca, e
  frenagem forte nos últimos ~1.000 blocos); a chegada é parada a 300 blocos sobre o chão.
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
- **Teleporte para dentro de uma esfera de influência**: `/tp` a mais de ~38.000 blocos de altitude na Terra (ou ~10.400
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
