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
| A4 | Se o S5 falhar em alta velocidade: convés só abaixo de um limite (ex.: 25 b/t), tripulação sentada acima | **Não vale**: o S5 passou até 100 b/t, sem limite de velocidade (22/10/2026) |

## Resultado do spike S5 (22/10/2026): aprovado

**Veredito: o convés andável em movimento funciona a 1, 10, 50 e 100 b/t, com e sem 100 ms de latência por sentido, e a velocidade da nave não
importa para a colisão.** Em todos os 8 cenários: 0 quedas pelo piso, altura do pé 0,0000 (p99 e máximo) na simulação local, no servidor e na
posição do jogador no mundo vista pela pose da nave, deriva parado 0,00001 bloco/tick, 0 correções (do servidor ao cliente, estados recusados e
teletransportes) e nenhum aviso "moved wrongly" nem "moved too quickly" no log. O tremor da câmera passa no relógio do jogo (0,2% a 1,3%, contra
0,4% da referência `ship`) e, no relógio real, é idêntico ao de um controle perfeito (limite do vanilla, abaixo). A decisão A4 (limite de velocidade) cai.

### Arquitetura

- **Onde vive o referencial**: pacote `ship/deck/`. O estado do tripulante (`DeckState`: pés, velocidade, olhar, no chão) está em COORDENADAS DO
  RETRATO, e a pose da nave (`DeckFrame`: centro, atitude, pivô; usa `ShipFrames`) só entra na ponta: ao derivar a posição do mundo, ao desenhar e
  ao sincronizar. A nave pode girar em qualquer eixo: a caixa do jogador é alinhada aos eixos do retrato, então a colisão é caixa contra caixa.
- **Colisão** (`DeckCollision`, `DeckColliders`): o algoritmo do vanilla (Y primeiro, depois o horizontal maior, degrau de 0,6) sobre as caixas de
  colisão dos estados do retrato (`getCollisionShape` sem mundo, em cache por índice de paleta), em doubles, sem o jogo (testado). A gravidade, o
  atrito, o pulo e o sprint são os do vanilla: um mixin (`DeckMoveMixin`) redireciona o `Entity.move` do jogador local para a colisão local; o yaw
  do jogador é o local e a gravidade é "para baixo da nave". A aceleração da nave não é sentida (como no Create).
- **Servidor** (`DeckCrew`): o cliente manda o estado LOCAL a cada tick (`DeckMovePayload`) no lugar do pacote de posição do vanilla, que o servidor
  descarta para quem está no convés (`ServerGamePacketListenerImplMixin`; também some a regra "voando"). O servidor confere (`DeckMoveCheck`:
  finito, no máximo 4 b/t locais por tick de espera, a caixa não entra mais de 0,05 em bloco), aceita e, depois de cada tick da nave
  (`RocketEntity.tick`), DERIVA a posição do jogador no mundo pela pose daquele tick. Recusa devolve a última posição boa
  (`DeckCorrectionPayload`). Embarcar e desembarcar (`DeckCrew.board/leave`, comandos `/spacehorizons deck board|leave|info`) são decisões do servidor,
  avisadas por pacote; sentar, trocar de dimensão, sair da caixa (+3 blocos) ou a nave sumir tiram o jogador do convés (teletransporte do protocolo).
  A tripulação respira o ar da cabine (`RocketCabinOxygen`).
- **Cliente** (`DeckClient`, `DeckCamera`, `CameraMixin`): a posição do mundo do jogador é derivada no fim de cada tick (antiga e nova), o olho da
  câmera é a pose INTERPOLADA do quadro aplicada ao pé local interpolado, a câmera é a atitude composta com o olhar local
  (`CockpitCamera.deckRotation`; em terceira pessoa recua a partir do olho) e o modelo do jogador é girado pela atitude em torno dos pés e
  posto na posição exata do quadro (a interpolação linear da posição do tick corta o canto da trajetória quando a nave muda de velocidade:
  sem a correção o modelo ficava até 500 blocos fora do convés) sem ser descartado pelo campo de visão (`DeckCullingMixin`). A animação de
  caminhada usa o passo local (`DeckAnimationMixin`).
- **Linha do tempo da pose** (`DeckPoseTimeline`, `RocketEntity.POSE_STAMP`): com a rede atrasando os pacotes, um tick do cliente às vezes consome
  zero atualizações da nave e o seguinte duas, e a nave (e a câmera presa a ela) anda aos trancos: 100% de desvio de velocidade com 100 ms de
  latência. Enquanto há tripulação, a nave manda o tick do servidor de cada pose e o cliente desenha o casco, a câmera e o tripulante numa linha do
  tempo de ticks do servidor (interpolação e slerp entre dois ticks, 2 ticks atrás do relógio estimado, que segue o menor atraso de chegada com
  passos limitados). O relógio é um contador de ticks do cliente: o tempo de jogo (`getGameTime`) é corrigido pelo servidor e salta de 1 a 2 ticks.
  Sem tripulação a pé nada muda (o `RocketEntity` segue com a interpolação do vanilla).
- **Latência simulada**: `NetLatency` + `ConnectionLatencyMixin` atrasam em ordem todo pacote de jogo nas duas pontas da conexão (zero desliga).
- **Autoteste** `-PshOnly=shipdeck` (`ShipDeckDevtest`): o foguete oco de ~4.000 blocos de `ShipTestRocket` (26x28x26: piso de tijolo, baús, tanques,
  forno) vira uma entidade roteirizada (sobe 700 ticks, paira 300, desce 700, sobe 300, ricocheteando entre 500 e 4.500 de altitude, 1°/tick de guinada
  em torno do próprio eixo e 30° de inclinação fixa em 100 ticks); o jogador, em sobrevivência, está no referencial dela e um robô (as teclas do
  jogador, pulos nos cantos, paradas de 60 ticks) anda em quadrado por 2.000 ticks. `SH_DECK_CONFIGS="100:100:s"` (ambiente) escolhe cenários.

### Números (8 cenários de 2.000 ticks, máquina sem outros clientes; `run-test/` do autoteste)

| Nave | Latência | Quedas | Pé (cliente / servidor / visto) p99 e máx | Deriva parado | Correções | Tremor, relógio do jogo | Tremor, relógio real (controle) |
|---|---|---|---|---|---|---|---|
| 1 b/t | 0 | 0 | 0,0000 | 0,00001 | 0 | 1,3% | 41,9% (41,9%) |
| 10 b/t | 0 | 0 | 0,0000 | 0,00001 | 0 | 1,3% | 22,3% (22,3%) |
| 50 b/t | 0 | 0 | 0,0000 | 0,00001 | 0 | 1,3% | 29,8% (29,8%) |
| 100 b/t | 0 | 0 | 0,0000 | 0,00001 | 0 | 1,2% | 21,7% (21,7%) |
| 1 b/t | 100 ms | 0 | 0,0000 | 0,00001 | 0 | 1,3% | 32,7% (32,6%) |
| 10 b/t | 100 ms | 0 | 0,0000 | 0,00001 | 0 | 1,1% | 25,6% (24,3%) |
| 50 b/t | 100 ms | 0 | 0,0000 | 0,00001 | 0 | 1,1% | 29,8% (30,0%) |
| 100 b/t | 100 ms | 0 | 0,0000 | 0,00001 | 0 | 1,3% | 34,9% (34,9%) |

Leituras e ressalvas:

- **Pé na altura do piso**: 0,0000 por construção da simulação local; o que prova o resto é que a posição do jogador no mundo, trazida de volta pela
  pose da nave, e a do servidor batem com o estado local (diferença máxima 0,00001) e que o modelo desenhado fica a 0,00001 da posição exata. A
  interpolação linear do vanilla sozinha erraria de 0,5 a 500 blocos conforme a nave muda de velocidade (coluna "sem a correção" do log): é por
  isso que a pose é aplicada no desenho e não como deslocamento do jogador.
- **Tremor**: a referência `ship` mede 0,4% no relógio real porque o céu dela é função direta de `nanoTime`. O relógio do vanilla anda em
  milissegundos inteiros e, em quadros de ~8 ms, isso dá 20% a 40% de "tremor" em QUALQUER rotação perfeita medida em tempo real; o autoteste mede
  essa rotação perfeita no mesmo quadro (controle) e o convés dá o mesmo número, ou seja, não soma nada. No relógio do jogo (tick e parcial do
  quadro, em que o vanilla desenha tudo) o convés dá 0,2% a 1,3%. O critério "dentro de 5% da referência" vale nesta segunda medida.
  Sem a linha do tempo da pose, com 100 ms de latência, o mesmo número era 100% (trancos de 1 tick da nave).
- **Máquina ocupada**: com outros clientes rodando (a regressão de outro agente), os quadros passam de 8 para 16 a 30 ms e param até 180 ms: o
  tremor medido nesses cenários não vale (o controle também explode) e os estados aceitos por tick do servidor caem abaixo de 85%. As medidas de
  queda, pé, deriva e correções não dependem de tempo e passaram em todas as rodadas.
- A decisão A4 não é necessária: nenhum limite de velocidade foi preciso.

### O que falta (não verificado)

- **Outros clientes**: o servidor deriva a posição do jogador no mundo, mas o cliente de um observador interpola o jogador (3 ticks) e a nave (1 tick)
  por conta própria: a 100 b/t ele veria o tripulante até 200 blocos atrás do casco. Falta mandar o estado local aos observadores e posicionar o
  jogador remoto pela pose da nave (como o `DeckClient` faz com o local), descartando o pacote de posição vanilla dele. Não há autoteste de dois
  clientes ainda; também não foram testados servidor dedicado nem reabrir o mundo com tripulante a bordo.
- **Embarque automático**: só por comando e pelo autoteste; ao pisar no convés de uma nave devagar, ao levantar de um assento e ao sair andando.
  Ao sair a velocidade é zero (herdar a da nave arremessaria o jogador).
- **Interação**: nada de portas, baús e assentos em movimento (A2); o clique do jogador só enxerga blocos do mundo, não os do retrato.
- **Atrito e efeitos de bloco** vêm do bloco do MUNDO sob a posição derivada (ar em altitude; trocar na 2e); sem dano de queda a bordo; sem som
  de passos nem balanço de câmera; sem luz própria (o retrato usa a do mundo em volta); a câmera em terceira pessoa atravessa o casco.
- **Ao entrar e sair** do convés a nave troca da interpolação do vanilla para a linha do tempo (e de volta): um pulo único de até 2 ticks de
  movimento no desenho (fase 2g). Estalo de quadro maior que 2 ticks (estalo do cliente) também aparece como tranco.
- Chunks à frente da nave em voo horizontal rápido, dimensões diferentes e a troca planeta/espaço com tripulante a pé são da 2e.

## Arquivos que mudam

`rocket/RocketEntity`, `mixin/rocket/RocketPositionMixin`, `ship/ShipFrames` (referencial do convés), mixins de movimento e colisão do
jogador, `mixin/travel/ServerGamePacketListenerImplMixin` (validação de movimento a bordo), `network/` (posição local dos tripulantes),
`rocket/RocketLanding` e `ship/ShipConversions` (âncora), `ship/ShipDescent` e `ship/ShipAscent` (tripulação em pé).

Feito no S5: `ship/deck/*` (referencial, colisão, servidor, pacotes, linha do tempo), `client/ship/DeckClient` e `DeckCamera`, `rocket/RocketEntity`
(pose suavizada, selo do tick, roteiro de teste), `rocket/RocketPortrait` (colisores), `client/rocket/RocketRenderer` e `CockpitCamera`,
`mixin/CameraMixin`, `mixin/travel/{ServerGamePacketListenerImpl,DeckMove,DeckSend,DeckAnimation,DeckCulling,ConnectionLatency}Mixin`,
`network/NetLatency`, `rocket/RocketCabinOxygen`, `client/devtest/{ShipDeckDevtest,AutoTest}`. Testes: `DeckCollisionTest`, `DeckPoseTimelineTest`, `DeckCameraTest`.
