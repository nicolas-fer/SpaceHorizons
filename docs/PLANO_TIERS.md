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
| 2 | Gerador de planetas por parâmetros e Marte | 1 |
| 3 | Sistema solar centrado no sol (viagem a Marte) | 1 |
| 4 | Motores e planetas do Tier 2 | 2 |
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
  (`PLANO_SOBREVIVENCIA.md`, etapa 5); até lá fica trancada no menu em sobrevivência e livre no criativo; o terminal só precisará entregar
  `RocketBlueprintItem.stackOf(Blueprints.LUNAR)`.
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

