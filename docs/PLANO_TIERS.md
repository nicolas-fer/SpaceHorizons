# Plano de progressão por tiers do SpaceHorizons (v1)

Decidido em 20/10/2026. Organiza a progressão inteira do mod em quatro tiers, do foguete real à nave com motor de dobra.
Os planos de cada parte continuam nos seus documentos: `docs/PLANO_SOBREVIVENCIA.md` (Tier 1: materiais, oxigênio, foguete,
base e história), `docs/DESIGN_FOGUETE.md` (física e contratos do foguete), `docs/MISSAO_LUA.md` (missão à Lua provada),
`docs/PLANO_NAVES.md` (naves andáveis, base do Tier 3) e `docs/PLANO_LOD_PLANETA.md` (LOD dos planetas).

## 1. Os tiers

| Tier | Tema | Destinos | Veículos | Física e pilotagem |
|---|---|---|---|---|
| 1 | Foguetes reais, como os de hoje | Órbita, Lua, **Marte** | Foguete químico de blocos, um estágio e depois estágios | Gravidade real (patched conics), estilo KSP simplificado, SAS e níveis de ajuda |
| 2 | Além da realidade: foguetes que ainda não temos | Planetas distantes do sistema solar | Motores nucleares, iônicos, de fusão | A mesma física do Tier 1, com Isp e empuxo muito maiores |
| 3 | Naves eficazes e fáceis de manusear | Todo o sistema solar | Naves andáveis por dentro (caminho C do `PLANO_NAVES.md`) | Voo assistido no estilo das naves do No Man's Sky: aponta e vai, o jogo cuida da órbita |
| 4 | Motor de dobra | Outros sistemas estelares, **procedurais** como no No Man's Sky | Naves do Tier 3 com motor de dobra | Salto de dobra entre sistemas; dentro do sistema, o voo do Tier 3 |

- **Tier 1** já existe em grande parte: o foguete de hoje com órbita, Lua e pouso (missão Terra → Lua → Terra provada em jogo,
  `docs/MISSAO_LUA.md`). Faltam multiblocos, guias de montagem, separação de estágios, a base lunar e a história
  (`PLANO_SOBREVIVENCIA.md`, etapas 4 e 5) e Marte.
- **Marte fica no Tier 1**, como na vida real, e **chegar a Marte abre o Tier 2**.
- **Satélites ficam de fora** por enquanto (sem função de jogo definida).

## 2. Regras gerais

- **Montagem sempre livre, com blocos**, em todos os tiers. Não há nave pronta.
- **Guias de montagem desde o Tier 1** (seção 4): ajudam sem tirar a liberdade.
- **Um tier não deixa o anterior obsoleto**: as peças de cada tier exigem materiais que só existem nos destinos do tier
  anterior (por exemplo, o motor do Tier 2 pede um minério de Marte). O jogador passa por todos.
- **Peças maiores rendem mais**: um multibloco maior tem números melhores por massa, o que dá motivo para construir grande.

## 3. Multiblocos: tanques e motores

A montagem continua a mesma (blocos livres sobre a plataforma, clique no cockpit). Tanques e motores passam a formar
**estruturas multibloco** que, montadas, ganham visual arrojado e conectado.

- **Forma por regra, não por lista fixa.** Tanque: qualquer coluna retangular cheia do mesmo tipo de bloco, com largura e
  profundidade de 1 a 3 e altura de 4 a 12 (exemplos: 1x1x8, 2x2x8, 3x3x10). A capacidade sai do volume; formas maiores
  têm mais combustível por tonelada de casco. Motor: tamanhos definidos por tier (por exemplo 1x1, 2x2, 3x3 de base), com
  empuxo e Isp por tamanho. Os intervalos exatos ficam para o design da etapa.
- **Visual**:
  - **Tanques**: texturas conectadas (sem emenda entre os blocos da estrutura formada), barato e bonito.
  - **Motores**: modelo próprio da estrutura formada (tubeira grande, feito no Blockbench pela skill `minecraft-entity`).
  - Em ambos, um bloco mestre desenha o conjunto e os outros ficam invisíveis enquanto formados.
- **Mudança técnica no foguete**: hoje o retrato (`RocketPortrait`) é desenhado bloco a bloco e o `RocketCensus` conta blocos.
  Os dois precisam reconhecer estruturas formadas: o renderer desenha o modelo grande também em voo, e o censo conta
  estruturas (capacidade e empuxo por estrutura) além de blocos. O pouso devolve os blocos com a estrutura formada de novo.

## 4. Guias de montagem

1. **Planta holográfica**: o jogador escolhe um projeto e aparecem blocos-fantasma translúcidos onde cada peça vai (como as
   esquemáticas do Create ou o Litematica). Falta = marcado; errado = vermelho. É sugestão, não trava: dá para desviar.
2. **Painel de projeto na plataforma de lançamento** (o guia mais importante, porque ensina o porquê): olhando a estrutura,
   mostra em tempo real massa, empuxo, relação empuxo/peso, Δv e **destinos alcançáveis** ("chega à órbita", "chega à Lua e
   volta", "não decola"), e aponta problemas no bloco certo ("falta paraquedas", "motor sem tanque", "encostado no terreno").
   **Regra**: o painel usa exatamente o mesmo cálculo da montagem real (`RocketCensus`, `RocketSpecs`); os destinos saem do
   simulador da missão (`MissionSimTest`, `LunarPlanner`), para o painel nunca prometer o que o foguete não faz.
3. **De onde vêm as plantas** (ligado à história, `PLANO_SOBREVIVENCIA.md` etapa 5): uma ou duas básicas no início (salto
   suborbital, foguete orbital); as outras em diários e terminais pelo mundo (por exemplo, a do foguete lunar numa base
   abandonada). O jogador também salva a própria nave como planta, para reconstruir ou compartilhar.
4. **Por tier**: plantas fixas e o painel no Tier 1; plantas **modulares** (cabine, hangar, motor de dobra) que se encaixam no
   Tier 3 e 4, mais perto do No Man's Sky, ainda montadas bloco a bloco.

## 5. O que cada tier exige de técnica

- **Gerador de planetas por parâmetros** (Marte e tudo depois): terreno, cores, crateras, gravidade, atmosfera e água a partir
  de parâmetros, reaproveitando o que a Lua já tem (`MoonTerrain`, malha orbital, LOD, mapa, céu). Planeta feito à mão não
  escala; este gerador é o investimento central do Tier 1 em diante e a base do Tier 4.
- **Sistema solar centrado no sol** (Marte em diante): hoje é Terra + Lua com o sol fixo. Planetas orbitando o sol e esferas de
  influência encadeadas (Sol → Terra → Lua, Sol → Marte). A matemática atual (patched conics, `space/flight`) se estende. As
  distâncias reais são impossíveis: a escala será comprimida, e a viagem a Marte precisa caber em tempo de jogo razoável.
- **Tier 2**: só números e conteúdo novos (motores, combustíveis, planetas); a física do Tier 1 serve.
- **Tier 3**: as naves andáveis do `PLANO_NAVES.md` (estaleiro, céu virtual, conversão foguete ↔ blocos: spikes S1, S2 e S3
  aprovados) mais um **modo de voo assistido** em cima da mesma física.
- **Tier 4**: sistemas estelares procedurais exigem dimensões criadas com o jogo rodando. O Minecraft registra as dimensões
  na criação do mundo e o NeoForge não suporta oficialmente criar outras depois (há mods que fazem). **Spike obrigatório antes
  de comprometer**: criar, carregar, salvar e descarregar uma dimensão em tempo real. Plano B: um conjunto de dimensões
  reaproveitadas (o sistema visitado ocupa um "slot" e os planetas são gerados pela semente do sistema).

## 6. Ordem

| # | Etapa | Tier |
|---|---|---|
| 1 | Multiblocos de tanque e motor e guias de montagem (painel primeiro, depois plantas) | 1 (multiblocos, painel e plantas holográficas feitos: seções 7 e 8) |
| 2 | Gerador de planetas por parâmetros e Marte | 1 (gerador e Marte feitos, Marte fora da órbita: seção 10) |
| 3 | Sistema solar centrado no sol (viagem a Marte) | 1 |
| 4 | Motores e planetas do Tier 2 | 2 (motores, combustíveis e materiais de Marte: seção 9; planetas pendentes) |
| 5 | Naves andáveis com voo assistido (`PLANO_NAVES.md`) | 3 |
| 6 | Spike de dimensão em tempo real; se passar, dobra e sistemas procedurais | 4 |

A base lunar, a história e a separação de estágios (`PLANO_SOBREVIVENCIA.md`, etapas 4 a 6) entram no Tier 1, intercaladas
com as etapas acima conforme a jogabilidade pedir.

## 7. Etapa 1 (multiblocos e painel): o que foi feito

Implementado e verificado em jogo (`-PshOnly=multiblock`); números, fórmulas e ids em `docs/DESIGN_FOGUETE.md` ("Multiblocos, retrato grande e painel de projeto").

- **Retrato e censo entendem estruturas e perdem o limite de 64**: a montagem comum aceita até 8.192 blocos e uma caixa de 128 x 256 x 128 (decisão D2 do
  `PLANO_NAVES.md`, o que destrava a fase 2a das naves); o retrato grande vai ao cliente em partes quando passa de 960 KiB (teto de 16 MiB); o censo
  (`RocketCensus`, `ShipCensus`) conta estruturas, e o retrato, a montagem e o painel dão o mesmo censo.
- **Tanque**: coluna cheia 1 a 3 de lado x 4 a 12 de altura; capacidade de 4.000 mB por bloco (pelo volume); casco `0,2 t * V * (0,4 + 0,6 A / 6V)`; textura conectada;
  combustível da estrutura (galão em qualquer bloco, repartido por igual; o pouso devolve proporcional por bloco).
- **Motor**: cubos 2x2x2 (empuxo 24, 3,84 t, Isp 1.700) e 3x3x3 (101,25, 11,34 t, Isp 1.800), contra o 1x1 (2,5, 0,6 t, 1.600); bloco mestre (canto mínimo ou centro)
  desenha o modelo e os outros ficam invisíveis, no mundo, no retrato em voo, no estaleiro e no pouso.
- **Painel de projeto**: aparece olhando a plataforma de mão vazia, e o clique de mão vazia o fixa; mesmo cálculo da montagem; destinos de `MissionBudget` (medidos
  no simulador da missão); problemas no bloco certo.
- **Arte provisória**: textura do tanque derivada por processamento (`tools/derive_tank_frame.py`) e modelos dos motores por ampliação dos elements do motor atual
  (`tools/gen_engine_structures.py`); a arte final entra nos mesmos ids sem código.
- **Plantas holográficas** (seção 4, item 1): feitas depois, na seção 8.

Verificação: 839 testes unitários passando; autoteste `-PshOnly=multiblock` com 169 verificações (formação no mundo, galão na estrutura, projeto com defeitos, e para cada projeto o painel, a montagem, o voo curto, o pouso e as estruturas formadas de novo; o foguete grande de 1.650 blocos e o gigante de ~6.700 blocos, com o retrato em partes); `flight` (A a D), `mission` (263,97 b/t, pouso suave nos dois corpos, agora com a coluna 3x3x5 formada), `shipconvert` e `ship` sem falhas. No gigante (34 de largura) o pouso em terreno irregular pode ser recusado com `SPACE_OCCUPIED` (o foguete fica pousado intacto); o autoteste aceita essa recusa.

## 8. Plantas holográficas (seção 4, itens 1 e 3): o que foi feito

Implementado em código e no autoteste `-PshOnly=blueprint`; detalhes técnicos em `docs/DESIGN_FOGUETE.md` ("Plantas holográficas de montagem").

- **Planta** (`rocket/blueprint/Blueprint`): lista de blocos relativos à plataforma-âncora (a `launch_pad` em que a planta é posta fica em 0, 0, 0), com o
  estado de cada bloco (string de bloco do Minecraft, normalizada) e o papel na montagem; inclui a base de plataformas e as peças dos multiblocos bloco a
  bloco (a estrutura formada é derivada). Salva em NBT/JSON (paleta + papéis + posições empacotadas) e vai na rede em varints. O destino do projeto da planta
  sai do MESMO cálculo do painel sobre a planta montada (`RocketDesign.analyze` sobre a planta como mundo).
- **Item `rocket_blueprint`** (componente `rocket_blueprint`): clique no ar abre o menu das plantas ("livro"); clique numa plataforma ativa a planta nela;
  com a planta em branco, salva o foguete que está sobre a plataforma (a mesma busca do painel e da montagem, com as plataformas sob ele); agachado + clique
  tira a planta. Receita: 2 papéis + corante azul (desbloqueada ao pegar uma plataforma). Aba do criativo com a em branco e as três embutidas; JEI separa as
  variantes pelo componente.
- **Plantas embutidas** (`Blueprints`): salto suborbital, foguete orbital e foguete lunar, exatamente os projetos que o simulador da missão já voou
  (`MissionBudgetTest`): o destino prometido é o voado. **Gancho de história**: a lunar deve vir de um terminal numa base abandonada na Lua
  (`PLANO_SOBREVIVENCIA.md`, etapa 5); fica trancada no menu em sobrevivência e livre no criativo até o jogador levar o anexo do terminal da Base
  Selene-1, que entrega `RocketBlueprintItem.stackOf(Blueprints.LUNAR)` e a destrava (feito: "Etapa 5: a trama" em `PLANO_SOBREVIVENCIA.md`).
- **Holograma**: fantasmas translúcidos onde falta bloco, vermelho onde o bloco está errado, nada onde está certo; é só sugestão (não trava a montagem).
  Renderer do block entity da plataforma, malha por planta assada uma vez, por quadro só os intervalos dos blocos que faltam ou estão errados (até 4.096
  blocos por planta, até 96 blocos de distância), comparação planta × mundo a cada 10 ticks.
- **Rede**: a planta ativa é um block entity da plataforma (`LaunchPadBlockEntity`), salvo com o mundo e sincronizado pelo pacote de block entity; só o servidor
  a muda; a escolha no menu é pedida ao servidor (`BlueprintChoicePayload`), que confere o item e a trava.
- **Painel de projeto**: com uma planta no conjunto de plataformas, o painel mostra o nome dela, "faltam N de T blocos" / "M errados" (ou "planta completa") e o
  destino do projeto da planta. O painel também abre olhando a plataforma com a planta na mão.
- **Arte provisória**: o item usa o mapa vanilla recolorido (`tools/derive_blueprint_item.py`: azul para a planta com projeto, cinza-azulado para a em branco);
  a arte final entra nos mesmos arquivos (`textures/item/rocket_blueprint.png` e `rocket_blueprint_blank.png`) pela skill `minecraft-entity`.
- **Limites**: a planta não gira (vale na orientação em que foi salva); estados sem modelo de bloco (baús) não ganham fantasma, mas contam no progresso; a
  planta fica na plataforma depois da decolagem (o holograma volta a mostrar o foguete para reconstruir) até ser tirada; plantas modulares (Tier 3 e 4) não foram feitas.

Verificação: 872 testes unitários passando (`BlueprintTest`: formato em NBT, JSON e rede; plantas embutidas = projetos voados no simulador; planta × mundo
com falta, errado e certo; salvar a estrutura como planta e reaplicar); autoteste `-PshOnly=blueprint` com 34 verificações e 9 prints (menu, holograma
na plataforma vazia, dois blocos errados em vermelho com "faltam 4 de 30 / 2 errados" no painel, planta completa sem fantasmas, montagem e voo curto até
68 blocos com pouso, holograma de volta depois da decolagem, planta salva de um foguete de 83 peças com baú e escada, reaplicada noutra plataforma, completada
com o mesmo projeto do original, e agachado + clique tirando a planta); `-PshOnly=multiblock` continua com 169 verificações sem falha.

## 9. Tier 2: conteúdo (motores, combustíveis, materiais de Marte)

Design de 22/10/2026 (ids em `docs/TIER2_IDS.md`, o contrato com a arte). A física é a do Tier 1 (Tsiolkovsky, `FlightIntegrator`, `FlightMachine`):
só entram números novos e um combustível por tipo de motor. Unidades do jogo: empuxo em t·b/t², massa em t, Isp em ticks (`ve = Isp · 0,08`),
combustível em mB.

### 9.1 Motores por tamanho

As três formas de cada motor seguem a regra do químico ("peças maiores rendem mais"): o cubo 2x2x2 tem o empuxo de 8 blocos x 1,2, a massa
de 8 blocos x 0,8 e Isp + 1 passo; o 3x3x3, 27 x 1,5, 27 x 0,7 e Isp + 2 passos. Números em `rocket/EngineType`.

| Motor | Forma | Empuxo | Massa seca | Isp | Empuxo/t | Combustível |
|---|---|---|---|---|---|---|
| químico (Tier 1) | 1x1 / 2x2x2 / 3x3x3 | 2,5 / 24 / 101,25 | 0,6 / 3,84 / 11,34 | 1.600 / 1.700 / 1.800 | 4,17 / 6,25 / 8,93 | químico (tanque de combustível) |
| nuclear (NERVA) | idem | 2,0 / 19,2 / 81 | 1,2 / 7,68 / 22,68 | 3.200 / 3.400 / 3.600 | 1,67 / 2,5 / 3,57 | hidrogênio + núcleo (barras) |
| iônico | idem | 0,1 / 0,96 / 4,05 | 0,4 / 2,56 / 7,56 | 12.000 / 13.000 / 14.000 | 0,25 / 0,375 / 0,54 | xenônio + energia (RTG) |
| fusão | idem | 4,0 / 38,4 / 162 | 1,0 / 6,4 / 18,9 | 10.000 / 11.000 / 12.000 | 4,0 / 6,0 / 8,57 | hélio-3 (guardado no motor) |

- **Nuclear**: o dobro do Isp do químico em cada tamanho, empuxo por tonelada de 40% do químico. Queima hidrogênio enquanto o **núcleo** durar:
  cada bloco do motor recebe uma barra `nuclear_fuel_rod` (clique com a barra; vai num bloco vazio do conjunto), e a barra aquece **32.000 mB de
  hidrogênio** (a vida útil). O núcleo é um "combustível" sem massa (`Propellant.NUCLEAR_CORE`), gasto na mesma quantidade em mB que o hidrogênio:
  sem núcleo o motor para, mesmo com hidrogênio. Um 2x2x2 leva 8 barras (256.000 mB), o dobro de um tanque 2x2x8.
- **Iônico**: Isp 7,5x o químico, empuxo 1/25: empuxo por tonelada de 0,25 a 0,54, que com a carga não passa nem da gravidade da Lua (0,013 b/t²):
  **não decola de lugar nenhum**, só serve no espaço (num foguete que decola com outro motor, ou como estágio de cruzeiro). Precisa de
  energia: **cada gerador `rtg_generator` no foguete fornece 1**, o 1x1 pede 1, o 2x2x2 pede 6 e o 3x3x3 pede 16. Com menos geradores que o pedido,
  todos os motores iônicos empurram na proporção (o Isp não muda); sem gerador, não empurram (`RocketSpecs.ionPowerFraction`). O RTG pesa 0,5 t.
- **Fusão**: Isp de 6,25x o químico com o empuxo por tonelada perto do químico; o topo da progressão. Queima hélio-3 que o **próprio motor guarda**
  (não há tanque de He-3 no contrato de ids): **1.000 mB por bloco**, carregado com o galão de He-3 (clique no motor; enche os blocos do conjunto em
  ordem). Por isso um foguete só com motor de fusão pode ser montado sem tanque. Se um tanque de He-3 for desejado depois, é um id novo
  (`helium3_tank`) e entra pela mesma classe dos tanques.

### 9.2 Combustíveis e tanques

| Combustível | Densidade (t por 1.000 mB) | Onde fica | Massa seca do tanque por bloco |
|---|---|---|---|
| químico | 0,8 | `rocket_fuel_tank` | 0,2 t |
| hidrogênio líquido | 0,4 (leve: o nuclear precisa de tanque grande) | `hydrogen_tank` | 0,25 t (isolamento criogênico) |
| xenônio | 1,2 (gás denso sob pressão) | `xenon_tank` | 0,3 t (vaso de pressão) |
| hélio-3 | 0,8 | dentro do `fusion_engine` (1.000 mB por bloco) | (no motor) |
| núcleo nuclear | 0 (vida útil, em mB de hidrogênio) | dentro do `nuclear_engine` (32.000 mB por bloco) | (no motor) |

- `hydrogen_tank` e `xenon_tank` são o MESMO bloco do tanque químico (`RocketFuelTankBlock` com outro combustível): 4.000 mB por bloco, a mesma regra
  de coluna (1 a 3 de lado, 4 a 12 de altura), o mesmo fator de casco `0,4 + 0,6 A / 6V` sobre a massa seca do bloco, textura conectada (`_frame`,
  `_frame_end`, moldura 2), a carga é da estrutura e volta proporcional ao pousar. Cada tipo forma colunas só com o seu bloco (hidrogênio sobre
  xenônio são duas colunas), e só aceita o galão do seu combustível.
- Galões (`hydrogen_canister`, `xenon_canister`, `helium3_canister`): o mesmo galão de 1.000 mB do combustível químico, com o seu combustível.
- Saves: a entidade e o voo guardam o químico onde sempre guardaram (`FuelMb`, `Fuel`) e os outros combustíveis em chaves novas (`Fuel_<tipo>`);
  um save antigo carrega como só químico.

### 9.3 Motores misturados

Cada tipo de motor queima o seu combustível (`EngineGroup`: um grupo por tipo, com o empuxo somado e o Isp harmônico dos tamanhos). Com tudo a 100%,
a vazão de massa soma e o Isp do conjunto é `ΣF / Σ(F/Isp)` dos grupos que AINDA têm combustível. O Δv do painel e da telemetria é calculado em
**fases** (`RocketStats.deltaV(FuelLoad)`): enquanto todos os grupos queimam, Tsiolkovsky com o Isp do conjunto; quando o primeiro combustível acaba
(ou o núcleo), o grupo dele para e os outros seguem com a massa que sobrou (o combustível que nenhum motor queima é peso morto). No voo, o
`FlightIntegrator` faz o mesmo tick a tick (cada grupo pede `F·acelerador/ve` do seu combustível; o tick em que um acaba é parcial). O foguete só
químico continua com a conta antiga, bit a bit. A decolagem exige que os motores COM combustível vençam a gravidade.

Exemplos (painel, tanques cheios e motores carregados, com cockpit e paraquedas):

| Foguete | Massa seca / cheia | TWR na Terra | Δv |
|---|---|---|---|
| químico 2x2x2 + tanque 2x2x8 | 8,74 / 111,1 t | 2,70 | 346 b/t (6.917 b/s) |
| nuclear 2x2x2 + hidrogênio 2x2x8 (8 barras) | 13,58 / 64,8 t | 3,70 | 425 b/t (8.499 b/s) |
| iônico 1x1 + 1 RTG + xenônio 1x1x4 | 2,82 / 22,0 t | 0,057 (não decola) | 1.973 b/t (39.460 b/s) |
| fusão 3x3x3 (27.000 mB de He-3) | 19,8 / 41,4 t | 48,9 | 708 b/t (14.162 b/s) |

### 9.4 Materiais de Marte e receitas

Os materiais existem como blocos e itens; o worldgen de Marte é da frente do gerador de planetas.

- `mars_regolith` (pá): sem toque de seda, 10% de chance de `rare_earth_dust` (fortuna como o cascalho com o sílex). `mars_titanium_ore` (picareta de
  ferro): `raw_titanium` com fortuna. `raw_titanium` ou o minério → `titanium_ingot` (fornalha ou alto-forno). `titanium_block` = 9 lingotes.
  `titanium_plate` = martelo de forja + 2 lingotes (como a placa de aço).
- Peças (todas pedem titânio ou terras raras de Marte: o Tier 2 não deixa o Tier 1 obsoleto e exige ter ido a Marte):
  - `nuclear_fuel_rod`: 6 terras raras + 2 placas de titânio + 1 pó de pedra luminosa (barra de tório). `superconductor_coil`: 4 cobre + 4 terras raras
    + 1 placa de titânio.
  - `nuclear_engine`: 4 placas de titânio + circuito + **motor químico** + 2 lingotes de titânio. `ion_engine`: 4 placas de titânio + circuito +
    2 terras raras + motor químico. `fusion_engine`: 4 placas de titânio + 4 bobinas + circuito + **motor nuclear**.
  - `hydrogen_tank` (2 por receita): 4 placas de aço + 4 de titânio + gelo compactado. `xenon_tank` (2): 4 placas de aço + 4 de titânio + bloco de cobre.
  - `rtg_generator`: 4 placas de titânio + circuito + 2 terras raras + 1 barra nuclear (a fonte de calor).
  - Galões vazios: pepita de aço + 4 placas de aço + 1 de titânio.
- **De onde vem cada combustível**:
  - hidrogênio: numa bancada, galão de hidrogênio + 2 baldes de água + 1 redstone → galão cheio (eletrólise; os baldes voltam vazios).
  - xenônio: galão de xenônio + 4 regolitos marcianos + 1 terras raras → galão cheio (o gás preso no regolito de Marte).
  - **hélio-3 (da Lua)**: clique direito com o galão de He-3 no solo da Lua (tag `spacehorizons:helium3_sources`; hoje a pedra da Lua, e o regolito
    lunar quando o gerador de planetas o criar): o bloco é processado (some) e o galão ganha 125 mB (8 blocos = 1 galão). Só funciona na dimensão
    da Lua.
- Conquistas de receita para todas as receitas (pai `minecraft:recipes/root`, como as outras).

### 9.5 Arte provisória

Gerada por `tools/gen_tier2_placeholders.py` (recolor de texturas do mod e do vanilla da versão, por processamento simples; modelos dos motores =
cópias retexturizadas dos do motor químico) nos nomes do contrato; a arte final grava por cima, sem código. Sem `--force` o script não sobrescreve.

### 9.6 Limites desta etapa

- O piloto de teste da missão (`MissionPilot`) e o `MissionBudget` continuam medidos com o foguete químico; os destinos do painel para foguetes do
  Tier 2 vêm do orçamento de Δv (e do TWR na Terra), não de um voo simulado com esses motores. Os destinos além da Lua (planetas do Tier 2) ainda
  não existem.
- As naves (`ship/*`) ignoram as peças do Tier 2 (contam como estrutura); o estaleiro não tem tanque de hidrogênio nem motores novos.
- Uma barra nuclear entra só num bloco de motor com o núcleo vazio (não se completa um núcleo gasto pela metade).


## 10. Etapa 2 (gerador de planetas e Marte): o que foi feito

Formato e parâmetros em `docs/GERADOR_PLANETAS.md`.

- **Planeta por parâmetros, data-driven**: `data/spacehorizons/planet/<id>.json` (lido do jar por `planet/spec/PlanetSpec`, com validação e
  mensagens de erro) descreve circunferência, gravidade, estilo, relevo, paleta de superfície e subsolo (blocos com reserva), água,
  atmosfera, veios de minério e duração do dia. `Planets` só guarda os ids.
- **Generalizado a partir da Lua**: `ParametricTerrain` substitui o `MoonTerrain` (as constantes viraram parâmetros; com o JSON da Lua o relevo
  é idêntico, conferido por soma de controle), com cordilheiras e vulcões em escudo opcionais e calota configurável; `SurfacePalette` por
  planeta alimenta chunks (`PlanetChunkGenerator`, agora com subsolo e veios), LOD do DH (`PlanetLodGenerator`) e malha orbital
  (`PlanetMeshPalette`, cor declarada ou cor de mapa do topo); `AtmosphereModel` ganhou `breathable` (oxigênio) e a cor do espalhamento e a
  profundidade óptica do limbo (shader `atmosphere/limb`); o céu de um planeta fora do sistema vem de um corpo destacado
  (`SolarSystem.skyBodyOf`) que só gira com o dia dele. A Terra continua vanilla; o JSON dela repete os números de antes.
- **Marte** (`spacehorizons:mars`, world preset para mundos novos): R ≈ 55.412 (0,53 da Terra do mod), gravidade 0,38, dia de 24.660 ticks,
  planícies escuras, terras altas, crateras, cordilheiras, vulcões de 110 blocos com caldeira, calotas de gelo, céu caramelo, atmosfera fina
  sem oxigênio, poeira no bioma, veio de titânio. `mars_regolith` e `mars_titanium_ore` com reserva vanilla (`red_sand`/terracota/`iron_ore`)
  até o conteúdo do Tier 2 existir. Acesso por `/spacehorizons goto mars [x z]`; fora da física orbital até a etapa 3.

Verificação (22/10/2026): 898 testes unitários passando (novos: formato e recusa de parâmetros inválidos, Marte determinístico, periódico em X,
faixas de altura, tipos e paleta, crateras e vulcões, veios, céu destacado, dados do jogo coerentes com os parâmetros; a Lua idêntica por
soma de controle). Autoteste `-PshOnly=mars` com 10 verificações passando (dimensão, gravidade 0,0304, chão da paleta, pé no chão, sol a pino
ao meio-dia, cratera achada, relevo do mundo = função em 49 colunas, veio achado, veio gerado = função 37/37, vulcão achado); prints do chão de
dia, do céu (meio-dia, pôr do sol, noite), da cratera vista da borda, do veio, de 1.000 e 5.000 de altitude e de um vulcão. Tick de Marte
~3 ms, geração de terreno ~6 ms por chunk. `to_d` (Terra: chão, costura, latitudes, Lua no céu, malha orbital a 5.000 e 15.000) e
`moonback` sem erros. A 1.000 de altitude, logo depois do teleporte, aparece o vão entre os chunks e a malha orbital enquanto o DH gera os
LODs (o mesmo comportamento conhecido da Terra, `docs/PLANO_LOD_PLANETA.md`).
