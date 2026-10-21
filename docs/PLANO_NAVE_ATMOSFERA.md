# Plano: andar na nave dentro do planeta (v2)

Estudo de 21/10/2026, revisado no mesmo dia com o usuário. Pergunta: dá para a tripulação andar dentro da nave enquanto ela sobe,
desce, paira ou voa dentro da dimensão de um planeta, como já acontece no espaço?

**Resposta: sim, com duas técnicas.** Em movimento, um **convés andável no estilo do Create**, com o jogador no referencial da nave.
Parada no ar, uma **âncora** que transforma a nave em blocos reais. No espaço continua o estaleiro, como hoje.

## Por que andar funciona no espaço e não no planeta

No espaço a nave é feita de **blocos reais parados** no estaleiro (`spacehorizons:shipyard`); quem se move é o céu
(`SkyObserver.forShip`, `SkyAttitude`, `SkyRendererMixin`). No planeta a nave é uma `RocketEntity` desenhada pelo retrato, e uma
entidade em movimento não tem chão para pisar.

## Os regimes

| Situação | Técnica | Andar | Máquinas |
|---|---|---|---|
| Em movimento no planeta, a qualquer velocidade (subida, descida, voo) | **Convés andável** na entidade, estilo Create | Sim | Não: só porta, baú e assento |
| Parada no ar | **Âncora**: a nave vira blocos reais no ar | Sim | Sim |
| Espaço | Estaleiro (já existe) | Sim | Sim |
| Pousada | Blocos no chão (já existe) | Sim | Sim |

**Decisão aceita**: com a nave em movimento no planeta as máquinas param (fornalha, redstone e afins só funcionam com a nave
ancorada, pousada ou no espaço). É a mesma regra das contraptions do Create.

### Convés andável (estilo Create, com o jogador no referencial da nave)

Os jogadores em pé no convés colidem com a grade de blocos do retrato da entidade, como nas contraptions do Create (colisão contra caixas
giradas, a nave pode girar em qualquer eixo).

**A diferença em relação ao Create é a velocidade.** As contraptions andam a 1 ou 2 blocos por tick; a nave sobe a 10 a 100 b/t. No Create
o cliente corrige o jogador pelo movimento da entidade com alguns ticks de atraso de rede: a 1 b/t o erro é de centímetros, a 50 b/t são
blocos, e o jogador atravessa o piso. Por isso o jogador a bordo tem **a posição guardada e simulada no referencial da nave**, no cliente e
no servidor: o movimento da nave entra como transformação na hora de desenhar e de sincronizar, não como um deslocamento a corrigir. Com
isso a velocidade da nave deixa de importar para a colisão.

- **Embarcar e desembarcar**: o jogador entra no referencial ao pisar no convés (ou ao levantar de um assento) e sai ao deixar a caixa da
  nave; perto do chão e devagar ele pode descer andando.
- **Interação**: com a nave em movimento os blocos são dados da entidade. Portas, baús e assentos funcionam por menus proxy e pelo
  estado do retrato; o resto espera a âncora, o pouso ou o espaço.
- **Rede**: posição dos tripulantes enviada em coordenadas locais da nave, para os outros clientes não os verem "deslizar" no convés.
- **Reaproveita**: `RocketEntity` e o retrato (`PortraitGrid`, `RocketMeshCache`), `RocketPositionMixin`, `ShipFrames` (local ↔ mundo,
  testada), `ServerGamePacketListenerImplMixin` (contra o falso "moved wrongly"), `positionRider`.
- **Riscos**: dois jogadores e servidor dedicado nunca foram testados no projeto; luz dentro da entidade (o retrato usa a luz do mundo em
  volta); câmera e física do jogador com rotação da nave (o jogador "de pé" num convés inclinado).

### Âncora no ar

Parada, a nave vira **blocos reais** no ar, pelo mesmo caminho do pouso (`RocketLanding.land` com a pré-verificação de espaço), só que sem
chão. A tripulação anda, as máquinas funcionam, o DH e o Sodium tratam a nave como mundo e outros jogadores a veem de verdade: uma base
flutuante. Para voar de novo, monta-se pela ponte (fase 2a-i).

- **Novo**: pré-verificação de volume livre no ar; impedir que areia, cascalho e fluidos da nave caiam; mobs que nasceriam no teto;
  transição sem teleporte (os blocos aparecem onde a entidade estava e a tripulação fica no mesmo lugar relativo, como `movePeople`).

### Descartados

- **Estaleiro dentro da atmosfera** (planeta desenhado pela malha analítica em volta do estaleiro): é exatamente onde se olha pela janela
  e se vê o chão chegando, e a malha é grosseira perto do terreno real com o DH.
- **Modelo do Valkyrien Skies** (blocos reais num estaleiro dentro da própria dimensão, desenhados e colididos na posição da nave): seria a
  única forma de as máquinas funcionarem em movimento, mas exige mixins em render (com Sodium e Iris), colisão, raycast, som, partículas,
  luz e mobs, e quebra com outros mods. Fica de fora por decisão.
- **Mover os blocos reais em passos**: pelo S3, 8.000 blocos custam de 0,6 a 1 s em lotes, e cada passo refaz luz, seções do Sodium e
  LODs do DH, com flicker.

## Fases

| Fase | Entrega | Esforço | Critério de aceite (autoteste) |
|---|---|---|---|
| Pré-requisito | Fase 2b das naves (descida e pouso) fechada e mesclada | — | `shipdescent` e regressão verdes |
| S5 | Spike do convés | M/G | `-PshOnly=shipdeck`: um robô anda em quadrado no convés por 2.000 ticks com a entidade subindo a 1, 10, 50 e 100 b/t, girando 20°/s em guinada e inclinando 30°, pairando e descendo; repetir com 100 ms de latência simulada. Critérios: 0 quedas pelo piso, altura do pé com p99 < 0,05 bloco e máximo < 0,15, deriva parado < 0,02 bloco/tick, 0 correções "moved wrongly", câmera dentro de 5% da medida de tremor do `ship`. Morre se houver queda ou o p99 passar de 0,1 bloco |
| S6 | Spike da âncora | P | `-PshOnly=shipanchor`: entidade para a 60 blocos do chão, ancora, o robô anda, abre o baú e liga a fornalha, desancora, voa 100 blocos e ancora de novo; romaneio (`ShipManifest`) com 0 perdas nas duas idas; conversão < 1 s para 8.000 blocos; o jogador em pé fica a < 0,1 bloco da posição relativa ao convés |
| 2e | Convés andável | G | Critérios do S5 na nave real; subida e descida com tripulante **em pé** (a recusa da D4 cai); porta, baú e assento em movimento; 2 clientes (montar o autoteste de dois clientes faz parte do aceite) |
| 2f | Âncora no ar | P/M | S6 + regressão `ship*` verde; tecla e botão no painel; recusa sem volume livre |
| 2g | Trocas sem costura | M | Entrar e sair do convés, ancorar e desancorar, pousar e decolar sem salto (< 0,1 bloco no jogador, < 1° na câmera) |

Ordem: **S5 primeiro** (decide o plano), **S6** em seguida, depois **2e**, **2f** e **2g**.

## Decisões

| | Decisão | Estado |
|---|---|---|
| A1 | Máquinas param com a nave em movimento no planeta | **Decidida** (21/10/2026) |
| A2 | Interações em movimento: porta, baú e assento | Recomendada |
| A3 | A âncora exige um bloco (gerador antigravidade) e gasta energia, não combustível | Recomendada |
| A4 | Se o S5 falhar em alta velocidade: convés só abaixo de um limite (ex.: 25 b/t), tripulação sentada acima | Recomendada |

## Arquivos que mudam

`rocket/RocketEntity`, `mixin/rocket/RocketPositionMixin`, `ship/ShipFrames` (referencial do convés), mixins de movimento e colisão do
jogador, `mixin/travel/ServerGamePacketListenerImplMixin` (validação de movimento a bordo), `network/` (posição local dos tripulantes),
`rocket/RocketLanding` e `ship/ShipConversions` (âncora), `ship/ShipDescent` e `ship/ShipAscent` (tripulação em pé).
