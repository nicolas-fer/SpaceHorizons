# Space Horizons

Mod NeoForge para Minecraft 26.3 (Java 25) que busca um **espaço contínuo**: planetas
curvos e finitos, integrados ao Distant Horizons (DH), e depois órbita e viagem
interplanetária sem trocar de representação de repente (nada de skybox em PNG).

## Fase 1 (estado atual)

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

## Como testar a fase 1

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

## Limitações conhecidas

- **Nada foi executado no cliente nem no servidor nesta fase** (verificação de
  runtime pendente). O build, os testes unitários e o carregamento sem DH e sem
  Sodium foram conferidos só por código e compilação.
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
