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
  posições do `SolarSystem` (da Terra, a Lua no zênite do spawn; da Lua, a Terra com halo de atmosfera).
  A lua vanilla é escondida nos planetas do mod. A esfera de um corpo visto de longe e a malha orbital do
  mesmo corpo visto de perto usam o mesmo terreno, a mesma paleta e o mesmo sol.
- **Sol único** (`client/space/SpaceSun`): a direção do sol é uma só no referencial do espaço (o sol do
  Overworld no spawn da Terra, avançando com o relógio do Overworld). Dentro da atmosfera da Terra vale o sol
  local do vanilla; no vácuo e na Lua vale o do espaço. Por isso a iluminação não muda na troca de dimensão.
  Consequência física: com a Terra ao meio-dia a face da Lua voltada para ela está no escuro (lua nova).
  As estrelas também são fixas no espaço (`mixin/SkyRendererMixin`): o campo do vanilla é girado pela
  rotação entre o referencial do spawn da Terra e o da câmera, então as mesmas estrelas aparecem no mesmo lugar
  antes e depois da troca.
- **Viagem** (`space/travel/`): ao entrar na esfera de influência de outro corpo, o servidor transfere o jogador
  (`BodyTransfer`: posição, velocidade e olhar convertidos pelo `SpaceFrame`) e o cliente não abre a tela
  de carregamento (`mixin/travel/ClientPacketListenerMixin`; o aviso do servidor já leva posição e olhar
  de destino, para o primeiro quadro depois do respawn sair do lugar certo). A velocidade de voo cresce com a altitude.

### Como viajar

Em criativo e como operador, em um mundo do tipo Space Horizons:

| Comando | O que faz |
| --- | --- |
| `/spacehorizons fly moon` | piloto automático da Terra à Lua (cerca de 1.000 ticks); a troca de dimensão ocorre sozinha a ~8.400 blocos acima da Lua |
| `/spacehorizons fly earth` | volta para a Terra |
| `/spacehorizons fly stop` | cancela o piloto automático |
| `/spacehorizons goto moon` | teleporta para a superfície da Lua (face voltada para a Terra); mostra a tela de carregamento, de propósito |
| `/spacehorizons goto earth` | teleporta para a superfície da Terra |

Também dá para voar reto para cima a partir do spawn da Terra: a velocidade sobe com a altitude e, ao chegar à
esfera de influência da Lua, a troca acontece igual.

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

Leva uns 7 minutos. O roteiro principal é o da fase 3: prints do zênite do chão (a Lua como esfera), `/spacehorizons fly moon`
com prints periódicos e a cada 3 ticks em volta da transferência (o log registra `TROCA DE DIMENSAO` e `TELA ABERTA`, que
não deve aparecer), chegada na Lua (crateras, horizonte, Terra no céu), pulo no chão lunar (altura máxima no log),
`/spacehorizons fly earth` e a volta, depois uma regressão curta da fase 2 (2.000 e 30.000 blocos, costura em X). O roteiro
antigo da fase 2, mais longo, era: cria o mundo `SpaceAuto` com o tipo Space Horizons, sobe a 600, 2.000, 5.000,
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

## Limitações da fase 3

- **Verificação**: a viagem Terra para Lua para Terra foi vista em jogo (autoteste, prints a cada 3 ticks em volta das
  duas trocas): sem tela de carregamento, sem `moved too quickly`, mesma esfera, cor, tamanho e orientação antes e depois.
  Duas correções finais NÃO foram revistas em jogo por terem sido feitas depois da quinta e última rodada: as estrelas
  do primeiro quadro depois da ida Terra para Lua (usam agora a posição do jogador, e não a da Camera, que ainda está no
  mundo antigo) e o teste unitário da orientação vertical (`BodyTransferTest`). Na volta as estrelas já coincidiam.
- **Lua nova no meio-dia**: pelo sol único, a face da Lua voltada para a Terra fica no escuro quando é meio-dia no spawn
  da Terra. A iluminação dos chunks e dos LODs na Lua é a do vanilla (dia permanente), então ao descer a malha orbital
  (escura) e os chunks (claros) não combinam. Falta ligar a luz do chão lunar ao sol do espaço.
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
- **Só Terra e Lua** no `SolarSystem` (corpos estáticos); sem órbitas, sem Sol como corpo, sem outros planetas.
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
