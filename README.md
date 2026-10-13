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

## Fase 2 (estado atual): atmosfera, malha orbital e nuvens curvas

Vista em jogo pelo autoteste (5 rodadas de prints entre 0 e 40.000 blocos de altitude):

- **Atmosfera** (`client/atmosphere/`): `AtmosphereModel` (espessura 1.800 e altitude base 160
  para a Terra) alimenta a neblina vanilla e a do DH (`dh/DhFogConfig`), a cor do céu e o
  limbo. A neblina some com a altitude, o céu escurece até preto e as estrelas aparecem.
  O limbo é um brilho azul em volta do planeta, desenhado em tela cheia após o céu.
- **Malha orbital** (`client/planetmesh/`): grade polar centrada na câmera, gerada em
  background de `PlanetTerrain`/`SurfacePalette` e posicionada por `CurvatureMath`, o mesmo
  mapeamento do terreno. Preenche do fim do DH até o horizonte real. Acima de 2.000 blocos
  de altitude a malha usa o raio interno mínimo e cobre também o centro (o DH e os chunks
  desenham por cima, então só custa triângulos).
- **Nuvens curvas** (`mixin/CloudRendererMixin` + patch do `clouds.vsh`): usam a mesma
  fórmula de curvatura do terreno. Com o DH carregado, as nuvens que se veem são as do próprio
  DH (planas, fora do nosso mapeamento); por isso `DhFogConfig.updateClouds` as desliga via API
  acima da base da atmosfera, onde ficariam soltas fora da silhueta do planeta.

Resultado visto nos prints: a 600 blocos o horizonte já é curvo e o céu azul-escuro; a
2.000 o céu é preto com estrelas; a 5.000 se vê a curvatura com halo azul; a 15.000 e a
40.000 o planeta aparece inteiro como esfera com o limbo brilhando.

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

Leva uns 5 a 6 minutos. Cria o mundo `SpaceAuto` com o tipo Space Horizons, sobe a 600,
2.000, 5.000, 15.000 e 40.000 blocos (prints olhando para o horizonte e para baixo), testa
nuvens, costura em X, borda DH/chunks e barreira polar, grava `run/screenshots/autotest_*.png`
e as linhas `[AUTOTEST]` no log, e fecha o jogo na linha `[AUTOTEST] FIM`.

## Limitações conhecidas

- **Fase 2 (vista nos prints, sem medição de desempenho)**: a malha orbital é mais grossa
  que os LODs do DH (grade de 384 segmentos) e o terreno amostrado desliza algumas dezenas
  de blocos entre reconstruções; a costura com o DH não foi examinada de perto. O custo de
  CPU das reconstruções da malha não foi medido. A iluminação da malha é radial, sem sombra
  de relevo, e o terminador do limbo vem do ponto sob a câmera. Nuvens não escurecem no espaço
  e só existem dentro do raio de renderização; entidades continuam sem curvatura. A textura
  das nuvens tem período 3072, que não divide a circunferência, então pode haver emenda na
  costura em X. Sodium e Iris não foram testados com a atmosfera e a malha.
- **Servidor dedicado e carregamento sem DH/Sodium** não foram executados; só o cliente com DH
  foi exercitado pelo autoteste. Build e testes unitários passam.
- **Biomas duplicados**: o mapa `biomes` do datapack (`dimension/earth.json`) deve
  espelhar `SurfacePalette.biome()`. Nada garante isso automaticamente.
- **Custo**: `surfaceHeight` e `surfaceType` recalculam o ruído. O gerador chama os
  dois, então gasta cerca de 2x. Uma amostra combinada é a próxima otimização.
- **Costura só para jogadores**: entidades soltas não atravessam a costura. Veículos
  não são movidos com o jogador: ao cruzar a costura ou a barreira polar, o jogador
  pode ser desmontado. Corrigir exige teleportar o veículo raiz junto (próxima fase).
- **Cliente e costura**: o cliente ainda precisa receber os chunks do outro lado.
  Os tickets espelhados só garantem geração no servidor.
- **Curvatura e Sodium**: com Sodium instalado, o terreno é desenhado pelo Sodium
  e a curvatura não aparece nos chunks. O log avisa, mas os LODs do DH continuam
  curvos, e a borda entre chunks planos e LODs curvos fica visível.
- **Curvatura só no terreno**: entidades, block entities, partículas e o céu
  continuam planos. A 200 blocos a diferença é de cerca de 2 blocos, e a 500 blocos
  chega a ~12 blocos, então uma vaca fica flutuando sobre o chão curvado.
- **Quantização do DH**: `earthCurveRatio` é inteiro, então o raio do DH tem erro de
  até ~0,1% (ratio 615 contra 614,5 no planeta). Medido em teste: erro de solo abaixo
  de 1 bloco até 4.000 blocos. Não foi verificado no shader do DH em jogo.
- **Iris**: fora de escopo nesta fase.
- **Ruído**: Perlin 3D clássico, não OpenSimplex2.
- **Ordem de carga**: o `dimension_type/earth.json` segue o overworld do 26.3,
  mas ainda não foi validado contra o schema em jogo.
