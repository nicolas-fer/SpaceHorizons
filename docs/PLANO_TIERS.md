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
| 3 | Sistema solar centrado no sol (viagem a Marte) | 1 (feito: seção 11) |
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


## 11. Etapa 3 (sistema solar centrado no sol): design e o que foi feito

Design de 22/10/2026. Código em `space/Heliocentric`, `space/SolarSystem`, `space/flight/{SoiTracker,BodyPhysics,FrameBridge,MarsPlanner}`,
`rocket/FlightMachine`; contratos técnicos em `docs/DESIGN_FOGUETE.md` ("Sistema solar centrado no sol").

### 11.1 A escala (escolha documentada)

Distâncias reais são impossíveis (a Terra a 23.500 raios do sol) e o ano real não cabe no jogo. A escala é comprimida, e o que a decide é a
**Lua**: ela já existe (600.000 blocos da Terra, esfera de 96.270) e a esfera de influência da Terra, de Laplace contra o sol, tem de contê-la,
o que obriga um ano longo e uma órbita grande (a esfera de Laplace cresce com o período e quase não depende do raio da órbita).

| Grandeza | Valor | Por quê |
|---|---|---|
| Ano da Terra | 240.000 ticks (10 dias do jogo) | O menor que ainda deixa a esfera da Terra (~834.000) conter a Lua (600.000 + 96.270 × 1,05) |
| Raio da órbita da Terra | 4.000.000 blocos | 38 raios da Terra do mod |
| Raio da órbita de Marte | 6.096.000 (1,524 ×) | A razão real; período 451.532 ticks (1,881 ano, a razão real) |
| μ do sol | 4,3865e10 | Kepler: dá o ano acima no raio acima |
| Velocidade orbital | Terra 104,7 b/t; Marte 84,8 b/t | |
| Esfera de influência (Laplace) | Terra 833.852; Marte 520.268; Lua 96.271 | Não se tocam nem na oposição (distância mínima 2.096.000 contra 1,42 milhão das duas com histerese) |
| Período sinódico (de uma janela à próxima) | 512.299 ticks (21,3 dias do jogo) | |
| Raio físico do sol | 400.000 | Quem passa abaixo dele é queimado (batida no ponto de estacionamento) |

**Tempo de jogo.** Hohmann clássico entre as duas órbitas levaria 170.126 ticks (2 h 21 min a 20 ticks/s). Como a esfera da Terra é finita e grande
(21% do raio da órbita), a saída é uma troca de origem e não uma assíntota, e a transferência que a própria física dá é bem mais curta: **a
nave sai da esfera da Terra e chega à de Marte em ~67.000 ticks (56 min a 20 ticks/s)**, mais ~1.000 de subida e a espera da janela. A viagem
inteira (decolagem à captura e pouso) levou ~92.000 ticks no simulador. Isso cabe em "dezenas de minutos" só com **aceleração do tempo**: o
vanilla `/tick rate` (autoteste a 300 ticks/s, 15 vezes) leva a transferência a ~4 minutos. Não criei um comando próprio de aceleração: a
física é a mesma por tick, então `/tick rate N` já é a aceleração (ligue só com o foguete em cruzeiro, sem empuxo nem atmosfera; o cliente do
piloto precisa acompanhar os ticks).
A espera pela janela é o custo real: uma a cada 512.299 ticks (7,1 h a 20 ticks/s, 28 min a 300), e a janela dura ~52.000 ticks. A primeira
abre ~8.500 ticks depois do relógio 0 (`MARS_PHASE0`), então um mundo novo já nasce com a primeira janela aberta no primeiro dia.

### 11.2 Referenciais e esferas encadeadas

- **I** (sistema Terra-Lua, o de `InertialFrame`) NÃO muda: o sol fica fixo em −X, a Lua e as dimensões ficam como eram, e **o céu do dia na
  Terra e as fases da Lua são idênticos aos de antes** (a Terra e a Lua mantêm a direção do sol no infinito). **H** (heliocêntrico) é novo: origem
  no sol, eixos que não giram, onde o sol e Marte têm o estado de voo. I = `Ry(θ_T)·H`, com θ_T o ângulo da Terra na órbita: I é preso à linha
  Terra-sol e gira com ela, uma volta por ano. Dentro da esfera da Terra esse giro é desprezado (I vale como inercial, como valia com o sol
  parado); na troca de esfera os vetores só giram por `Ry(±θ_T)`, sem o termo ω×r.
- **SoiTracker**: Sol ⊃ Terra ⊃ Lua e Sol ⊃ Marte, entrada a 95% e saída a 105% da esfera, troca exata (posição e velocidade absolutas se
  conservam; a atitude gira junto entre I e H). O estado é relativo ao corpo dominante, como sempre; o sol é o dominante do espaço
  interplanetário.
- **Marte** deixa de ser corpo destacado: pose real (posição na órbita e rotação que mantém o dia de 24.660 ticks e a curva do sol do vanilla),
  fase real (a direção do sol é a verdadeira do centro de Marte) e céu com a Terra (de 0,8 a 7,6 graus de diâmetro) e a Lua.

### 11.3 Física, zonas e pouso

- **Zonas**: a dimensão de Marte vai do chão a 2.656 de altitude (a da Terra escalada pelo raio), a entrada acontece por raio como a da Lua
  (`Zones`, `ZoneSwitch`, com as alturas escaladas); o foguete interplanetário fica estacionado na dimensão de espaço (`SpaceParking`) e a
  posição real heliocêntrica vive no estado de voo, de onde o céu a lê (em E, a Terra no centro, até 10 milhões de blocos: `float` basta).
- **Atmosfera fina**: `surface_density` do planeta (Marte 0,15, Terra 1) multiplica a curva de densidade só no arrasto, no calor e no
  paraquedas (`BodyPhysics.dragDensity`). A velocidade terminal com o paraquedas em Marte é `0,5·sqrt(g_Marte/(G0·0,15)) = 0,8 b/t`, abaixo do
  limite de pouso suave (1,0); o corpo do foguete sem paraquedas chega a ~25 b/t, então o motor freia até <6 b/t e o paraquedas faz o resto.
- **Painel**: o planejador de Marte (caixa abaixo do da Lua) mostra a fase atual de Marte à frente da Terra contra a ideal do ponto da órbita, o
  Δv da injeção e o tempo até a próxima queima possível; "queime agora" acende o aviso.

### 11.4 O planejador (`MarsPlanner`) e o que a esfera grande muda

- A previsão usa as esferas da física e Kepler (nunca integra passo a passo): saída da esfera da Terra, varredura heliocêntrica até a esfera de
  Marte, hipérbole de chegada (periastro, parâmetro de impacto com sinal: positivo = órbita prógrada), correção de meio de curso (menor Δv que
  leva o periastro ao alvo) e o Δv da injeção por bissecção sobre a própria previsão.
- Com a esfera da Terra a 21% do raio da órbita, o Δv mínimo da injeção é o de uma elipse cujo apoastro é o raio de saída: **29,6 b/t a partir
  de 5.600 de altitude** (o painel sugere +1: 30,6). A nave sai com ~11 b/t relativos à Terra e segue uma elipse que cruza a órbita de Marte
  em 65.000 a 90.000 ticks. Qualquer ponto da órbita baixa entre ~165° e ~300° (do lado do sol, passada a metade da órbita) serve, cada um com
  a sua fase ideal (Marte de 34° a 66° à frente da Terra, a tabela é gerada pela física). A janela é larga (a fase anda 0,7° por 1.000 ticks).
- A Lua pode estar no caminho da saída: a previsão varre a perna da Terra e rejeita a janela se a trajetória entra na esfera da Lua (o piloto
  de teste, que a simulação sem o jogo pegou numa colisão, espera a próxima passagem).
- O planejador não vale com o relógio pausado; os planetas andam com o relógio.

### 11.5 Missão provada (22/10/2026)

**Simulador sem o jogo** (`MarsSimTest`, mesma `FlightMachine`, foguete M de 292,6 b/t): Terra → órbita baixa → espera da janela → injeção → quatro correções → esfera de
Marte → captura → descida → pouso; eventos `LIFTOFF (earth)`, `LEAVE_ZONE`, `ENTER_ZONE (mars)`, `SOFT_LANDING (mars)`. `MarsLaunchSimTest`: decolar de Marte, órbita e pouso de volta.

**Em jogo, autoteste `./gradlew runClientTest -PshOnly=marstrip`** (relógio de lançamento 20.000, 300 ticks/s, 91.506 ticks, 342 s):

| Fase | Resultado | Δv (b/t) |
|---|---|---|
| Subida e circularização | órbita de 6.035 x 5.702, tick 902 | 78,6 |
| Espera da janela | Marte 60,3° à frente da Terra; queima no tick 13.043 (a nave espera o ponto da órbita e a Lua fora do caminho) | |
| **Injeção** | 30,53 b/t em 102 ticks, apoastro 920.486 | 30,5 |
| Correções de meio de curso (4) | 0,003 + 0,019 + 0,003 + 0,009 b/t: periastro de Marte de 1.622 a 3.404 de altitude, prógrado | 0,03 |
| Voo | entra na esfera de Marte no tick 80.035 (66.888 ticks depois da injeção), v relativa 45 b/t | |
| Correção da chegada | 0,05 b/t (periastro 3.352 → 3.403) | 0,05 |
| **Captura** (retrógrada no periastro, 78 ticks) | órbita 3.529 x 3.267, v 39,8 | 29,6 |
| Freio contra o chão | 22,7 b/t, até 1,2 b/t | 22,7 |
| Descida | cai até 1.200 sobre o terreno, freia a <6 b/t, abre o paraquedas a 878 sobre o terreno (a 6 b/t), entra na dimensão de Marte a 781 de altitude | 5,3 |
| **Pouso** | vertical −0,72 b/t, horizontal 0,01, inclinação 0,31°, 37.587 mB sobrando | |
| **Total** | **166,8 de 292,6 b/t** | |

O céu no log (`CÉU`, a cada marco): da órbita baixa a Terra tem 142° de diâmetro aparente e Marte 1,2° (5,3 milhões de blocos, 88% iluminado); da esfera de
Marte a Terra tem 7,2° (fração iluminada 0,01: nova, vista de Marte) e a Lua 1,9°; Marte tem 13° na entrada da esfera e 144° em órbita. Prints `mt_01` a `mt_11`.

`mission`, `flight` (A a D), `moonback` e `mars` (10/10) rodados depois da mudança, sem falhas: a missão lunar fecha com 52.823 ticks e 9.307 mB sobrando (a de antes
terminava com o mesmo roteiro); a Terra e a Lua não mudaram.

### 11.6 Voar a partir de Marte (cenário `marsorbit`)

Decolar de Marte, subir ao espaço, ficar em órbita (3.000 ticks, com 8 prints de fora e 4 de dentro do foguete a cada ~1,3 s) e descer de volta, no ritmo normal de 20
ticks/s, com um contador de quadros do `BodyRenderer` (`BodyClient.diagFlips`) que registra cada vez que o conjunto de corpos desenhados muda de um quadro para o seguinte.
Resultado: decolagem no tick 9 com o acelerador aplicado (o painel mostra "100%" sem "(real 0%)"), subida a Ap 3.637 em 193 ticks (31,3 b/t), circularização em 41 ticks (7,5 b/t;
a subida de Marte custa 38,8 b/t contra 78,8 da Terra), 3.000 ticks em órbita a 3.637 de altitude e **0 mudanças de quadro nos milhares de quadros da órbita** (a esfera de Marte, desenhada o
tempo todo, vista de fora e de dentro), freio (23,7 b/t), descida com paraquedas e pouso (v vertical −0,74 b/t, 99.747 mB sobrando; 67,6 b/t no total).

Os quatro sintomas do voo a partir de Marte no `main` anterior (Marte "corpo destacado") e as causas:

1. **A esfera de Marte some e volta, em rajadas, às vezes por mais de 1 s.** Dois defeitos reais, achados e medidos neste cenário (o contador de mudanças), os dois no céu do foguete
   no espaço, onde a posição do observador vem do dado sincronizado do foguete: (a) o dado era a posição em E (preso à Terra), que perto de Marte anda ~1.400 blocos por tick (a Terra
   gira); o cliente interpola entre pacotes, então um pacote atrasado de um tick (o servidor engasga gerando o terreno da descida) deslocava o observador milhares de blocos, a
   distância de Marte caía abaixo do raio ("câmera dentro do corpo": o corpo é pulado) e o planeta piscava; medido antes da correção: 11 mudanças de quadro em ~760 quadros na descida. (b) Na
   chegada à dimensão de espaço a entidade é recriada e leva alguns quadros para ter posição virtual: sem ela o céu saía das coordenadas do ponto de estacionamento (milhões de
   blocos fora do sistema). Correção: o dado sincronizado passou a ser a posição em I (inercial, anda ~40 b/t), que o cliente leva a E com o relógio do próprio quadro
   (`RocketEntity.setVirtualSpacePosition(pos, clock)`, `ShipView`), e `ShipView` segura a última posição conhecida até a nova chegar. Depois: 0 mudanças na órbita e na descida
   no espaço; sobram as transições legítimas (a esfera aparece a 1.650 de altitude vindo da subida, some ao entrar na dimensão de Marte a ~790, 2 quadros de vão na chegada ao espaço).
   No `main` anterior Marte nem tinha zona de espaço (o foguete ficava na dimensão de Marte, 2 a 16 mil de altitude): lá o planeta só vinha da malha orbital, que depende da cobertura de
   chunks; não rodei o `main` anterior, então a participação dela no pisca que o usuário viu é inferência.
2. **Acelerador "100% (real 0%)" com combustível sobrando.** `RocketFlightController.tick` volta no início quando o nível não tem corpo no `SolarSystem` (`levelBody == null`: "um mundo sem
   planeta: o foguete só fica pousado"). Marte não estava em `BODIES`, então a dimensão de Marte nunca passava pelo passo do voo e o comando do piloto não chegava ao motor. Com Marte em
   `BODIES` o foguete decola (`MarsLaunchSimTest.withFuelTheEngineLiftsTheRocketOffMarsAtOnce`; em jogo, `marsorbit`).
3. **Com combustível 0 a física explode (TWR 274, subida de 2.989 b/s).** Não reproduzi nem identifiquei a causa no `main` anterior. O que está garantido agora: sem combustível o foguete
   pousado não decola e nada se move (`MarsLaunchSimTest.withoutFuelNothingLiftsAndNothingBlowsUp`), e em voo só a gravidade de Marte age (energia conservada a 5 unidades em 5.000 ticks,
   velocidade limitada). O "TWR" com tanque vazio é empuxo sobre a massa seca (alto por definição, e Marte tem 38% da gravidade da Terra); a subida de 2.989 b/s (149 b/t) não sai de nenhum
   cálculo desta branch.
4. **A câmera vai parar dentro de uma geometria cinza quadriculada gigante.** Não reproduzi como geometria externa. A única geometria cinza quadriculada nos prints (órbita, de dentro,
   `mo_07_orbit_first_person*`) é o interior da cabine do próprio foguete em primeira pessoa (também na Terra: `mt_03_leo`). Se o usuário viu algo maior, é compatível com a câmera
   dentro da malha do foguete ou da malha orbital quando a posição do observador saltava (causa 1), mas isso é hipótese.
