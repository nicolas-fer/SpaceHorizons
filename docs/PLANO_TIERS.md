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
| 1 | Multiblocos de tanque e motor e guias de montagem (painel primeiro, depois plantas) | 1 |
| 2 | Gerador de planetas por parâmetros e Marte | 1 |
| 3 | Sistema solar centrado no sol (viagem a Marte) | 1 |
| 4 | Motores e planetas do Tier 2 | 2 |
| 5 | Naves andáveis com voo assistido (`PLANO_NAVES.md`) | 3 |
| 6 | Spike de dimensão em tempo real; se passar, dobra e sistemas procedurais | 4 |

A base lunar, a história e a separação de estágios (`PLANO_SOBREVIVENCIA.md`, etapas 4 a 6) entram no Tier 1, intercaladas
com as etapas acima conforme a jogabilidade pedir.
