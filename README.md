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

Leva uns 8 minutos. Cria o mundo `SpaceAuto` com o tipo Space Horizons, sobe a 600, 2.000, 5.000,
15.000 e 40.000 blocos (horizonte e para baixo), olha o céu a 600 (faixas), compara a malha a
15.000 em x=0 e x=3000 (continentes), invoca armor stands a 50, 100, 150 e 200 blocos (assentamento
no chão curvo), tira prints de nuvens, testa costura em X, borda DH/chunks e barreira polar, grava
`run/screenshots/autotest_*.png` e as linhas `[AUTOTEST]` no log, e fecha na linha `[AUTOTEST] FIM`.
A regra `advance_time` é desligada e o horário fixado em meio-dia no início.

Combinações (as flags `-P` ficam no `build.gradle`):

| Configuração | Comando |
| --- | --- |
| Padrão (DH) | `./gradlew runClientTest --console=plain` |
| Sem DH | `./gradlew runClientTest -PshDh=false --console=plain` |
| Sodium | `./gradlew runClientTest -PshSodium=true --console=plain` |
| Iris, shaders desligados | `./gradlew runClientTest -PshIris=true --console=plain` |

Apague `run/saves/SpaceAuto` e `run/screenshots/autotest_*` antes de cada uma e copie os prints
antes da próxima. Procure `ERROR`, `Couldn't compile pipeline` e `Mixin apply` no log.

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
