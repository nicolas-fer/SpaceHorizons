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
| S6 | Spike da âncora (**aprovado**, ver o resultado) | P | `-PshOnly=shipanchor`: entidade para a 60 blocos do chão, ancora, o robô anda, abre o baú e liga a fornalha, desancora, voa 100 blocos e ancora de novo; romaneio (`ShipManifest`) com 0 perdas nas duas idas; conversão < 1 s para 8.000 blocos; o jogador em pé fica a < 0,1 bloco da posição relativa ao convés |
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

## Resultado do spike S6 (22/10/2026): aprovado

**Veredito: a âncora no ar funciona.** Autoteste `-PshOnly=shipanchor` (`ShipAnchorDevtest`, `run-test/`): **38 verificações, 0 falhas**. A nave de
~4.000 blocos para a 60 blocos do chão com o jogador em pé no convés, ancora, o robô anda (2,8 e 7,9 blocos com as teclas, pés sempre no piso),
abre um baú (807 itens, iguais ao servidor) e liga a fornalha (acesa no servidor, cozinhando), desancora, voa 100 blocos e ancora de novo; depois
monta pela ponte no ar (agachado, mão vazia) e ancora com o piloto sentado; uma nave de ~8.000 blocos mede o tempo.

| Troca | Romaneio | Conversão (parede) | Ticks de job | Tick do servidor (média / máx) | Posição relativa ao convés (servidor / cliente) |
|---|---|---|---|---|---|
| ancorar 1 (em pé, nave fora da grade e girada 4°) | 0 perdas (4.003 blocos, 50 block entities, 33.969 itens) | 281 ms | 5 | 8,5 / 10,6 ms | 0,0000 / 0,0000 |
| desancorar (comando, volta ao convés) | mesmos blocos e itens; caixa do mundo vazia | 84 ms (montagem num tick) | — | — | 0,0000 / 0,0000 |
| ancorar 2 (depois de voar 100 blocos) | 0 perdas | 312 ms | 5 | 7,9 / 10,4 ms | 0,0000 / 0,0000 |
| ancorar 3 (piloto sentado, depois da ponte) | 0 perdas | 312 ms | 5 | 8,4 / 8,9 ms | em pé dentro da nave |
| nave grande (`ship genrocket 8000`: 7.997 blocos, 74 block entities, 50.559 itens) | 0 perdas | **450 ms** | 8 | 9,1 / 14,1 ms | — |

Também verificado: a âncora é **recusada sem volume livre** (uma pedra encostada no casco) antes de tocar no mundo, e a nave segue normal; areia e
cascalho pendurados no teto **não caem** e uma fonte de água no piso **não escorre** (60 ticks); a checagem de nascimento natural (evento
`SpawnPlacementCheck`) **recusa zumbi no teto e dentro** e aceita fora; a ponte fica no bloco registrado da âncora; o jogador sai do convés em cima de um
bloco real (no chão no cliente).

Regressão (`./gradlew regression -PshSuites=ship,shipground,shipascent,shipdescent,shipconvert`, 6,4 min): ship 30/0, shipground 16/0, shipascent 23/0,
shipdescent 40/0, shipconvert 151/0. `./gradlew test build` passa (inclui `ShipAnchorsTest`, 3 casos).

### Como funciona

- **Ancorar** (`ship/ShipAnchors.request`, comando `/spacehorizons ship anchor`): só nave (`isShip`), inclinação até 15°, até 2 b/t, fora do estaleiro e
  do espaço. O retrato é girado ao quarto de volta mais perto e o canto mínimo é o do centro arredondado à grade (`RocketLanding.originFor`, como o
  pouso). **Pré-verificação de volume livre no ar** (`preCheck`): cada bloco da nave cai em ar carregado dentro da altura de construção, e cada vizinho por
  face que não é da nave também é ar (a montagem de volta levaria o vizinho junto). Recusa com a posição e o bloco.
- **Sem teleporte**: antes da conversão a entidade é **acomodada** na grade em 10 ticks (posição e atitude vão suavemente, `smoothstep` e `slerp`, ao
  ponto exato onde os blocos vão aparecer; a nave fica congelada e a tripulação a pé continua andando no convés parado, porque `RocketEntity.tick`
  passou a chamar `DeckCrew.afterRocketTick` também congelada). No teste a acomodação foi de 0,45 bloco e 4°. Sem desvio, a conversão começa no mesmo
  tick. A acomodação (0,5 s) fica fora do tempo da conversão da tabela.
- **Conversão** (`ship/AnchorJob`, na fila de `ShipConversions`, orçamento padrão 1.500 blocos/tick e 20 ms): as colocações do pouso
  (`RocketLanding.plan`, agora público: retrato girado, combustível repartido pelos tanques, carga dos motores), sólidos antes dos presos, NBT de cada
  block entity conferido na hora, aviso aos vizinhos e conferência contra o romaneio (`ShipManifest`); avisar e conferir custam 1/4 da colocação. Num
  tick só, no fim: quem anda no convés sai dele no mesmo ponto do mundo (`DeckCrew.leave`, a pose da entidade já é a da grade), os sentados ficam em pé
  no chão livre mais perto da ponte, a entidade some e a âncora é registrada. Com perda ou erro, os blocos colocados saem e a entidade fica.
- **Enquanto ancorada** (`ShipAnchors`, `SavedData` `spacehorizons:ship_anchors` no Overworld, com um índice estático para o caminho quente):
  `mixin/travel/AnchorTickMixin` descarta, dentro da caixa da nave, os ticks agendados de fluido e de blocos que caem (`FallingBlock`); os outros ticks
  (redstone) seguem. Mobs: `MobSpawnEvent.SpawnPlacementCheck` com FAIL para nascimento natural, da geração e patrulha na caixa com 1 bloco de folga
  (o pé de um mob no teto fica um acima da caixa).
- **Desancorar**: pela ponte, como na 2a-i (agachado + mão vazia): `ShipAssembler.assembleFor` vê que a ponte é de uma nave ancorada e chama
  `ShipAnchors.unanchor`, que usa a montagem da nave sem exigir a plataforma (`RocketAssembly.Rules.needPad`, `ShipAssembly.scanAirborne`). Quem montou
  senta; quem estava em pé na caixa entra no convés na mesma posição (`DeckCrew.boardAtWorld`). A entidade nasce `LANDED` no ar (parada, sem cair:
  o `LANDED` não integra a física) e decola com empuxo. Comando de dev: `/spacehorizons ship unanchor` (ninguém senta).
- **Decisão A3** (gerador antigravidade): continua recomendada, não implementada; no spike a âncora é o comando de dev. A 2f põe a tecla, o botão no
  painel e, se aceita, o bloco gerador.

### Limites e o que não foi verificado

- Só giro em Y ao quarto de volta (como o pouso); a nave do teste estava a 4° (0 quarto de volta). Um giro de 1 a 3 quartos de volta só tem o teste
  unitário da pose (`ShipAnchorsTest`), não o autoteste.
- Fluidos e blocos que caem ficam parados **na caixa inteira** da nave, inclusive água posta pelo jogador lá dentro (não escorre nada). A montagem de
  volta recusa areia, cascalho e fluido (são terreno ou fluido para a busca), como no chão; o teste os tira antes de desancorar.
- A âncora continua registrada se o jogador quebrar a nave ancorada (a caixa segue protegida até alguém desancorar); blocos postos encostados nela
  entram na próxima montagem (a busca é por face), e terreno encostado recusa.
- Um foguete pousado no ar pelo clique agachado na entidade (`RocketLanding.land`, caminho antigo) vira blocos sem registrar âncora.
- Não testados: dois clientes (um observador vendo a troca), servidor dedicado, reabrir o mundo com a nave ancorada (o registro persiste, mas não foi
  exercitado), a pré-verificação perto do teto de construção e de chunks não carregados, e o jogador andando (em vez de parado) no instante da troca.
- A fornalha acesa aparece no print pelas partículas de fogo; a face acesa não se distingue bem com a luz do teto.

Prints (examinados): `art/_work/shipanchor_prints/autotest_shipanchor_01_dentro.png` (interior, mensagem "Nave ancorada no ar: 4003 blocos"),
`_02_fora.png` (a nave parada no ar sobre o mar, sombra no chão), `_04_fornalha_acesa.png`, `_05_fora_depois_de_voar.png`; `_03_bau_aberto.png` não foi examinado (o baú aberto é conferido pela tela no cliente).

## Arquivos que mudam

`rocket/RocketEntity`, `mixin/rocket/RocketPositionMixin`, `ship/ShipFrames` (referencial do convés), mixins de movimento e colisão do
jogador, `mixin/travel/ServerGamePacketListenerImplMixin` (validação de movimento a bordo), `network/` (posição local dos tripulantes),
`rocket/RocketLanding` e `ship/ShipConversions` (âncora), `ship/ShipDescent` e `ship/ShipAscent` (tripulação em pé).

Feito no S5: `ship/deck/*` (referencial, colisão, servidor, pacotes, linha do tempo), `client/ship/DeckClient` e `DeckCamera`, `rocket/RocketEntity`
(pose suavizada, selo do tick, roteiro de teste), `rocket/RocketPortrait` (colisores), `client/rocket/RocketRenderer` e `CockpitCamera`,
`mixin/CameraMixin`, `mixin/travel/{ServerGamePacketListenerImpl,DeckMove,DeckSend,DeckAnimation,DeckCulling,ConnectionLatency}Mixin`,
`network/NetLatency`, `rocket/RocketCabinOxygen`, `client/devtest/{ShipDeckDevtest,AutoTest}`. Testes: `DeckCollisionTest`, `DeckPoseTimelineTest`, `DeckCameraTest`.

## Resultado da fase 2f (22/10/2026): âncora completa

Autoteste `-PshOnly=shipanchor2` (`ShipAnchor2Devtest`, `run-test/`): **24 verificações, 0 falhas**. O jogador pilota (sentado) uma nave parada no ar e aperta a tecla de ancorar (H, `RocketKeys.ANCHOR`, apertada de verdade por `KeyMapping.click`).

- **Gerador de antigravidade** (`antigravity_generator`, textura provisória por recolor do bloco de titânio; receita: 4 placas de aço, 4 circuitos, pérola do Ender). **Energia (decisão A3)**: o bloco guarda uma carga (propriedade `charged`); ancorar gasta a carga de todos os geradores da nave; cada um se recarrega sozinho em 400 ticks (20 s) com o bloco no mundo (nave ancorada ou pousada, tick agendado). Em voo (entidade) não recarrega: depois de desancorar é preciso pousar/ancorar de novo para recarregar. Não gasta combustível. RTG do Tier 2 como fonte em voo: não feito.
- **Estados** (`AnchorStatus.classify`, testado): ordem paraquedas, rápido demais (> 2 b/t), inclinada (> 15°), falta gerador, gerador sem carga, sem espaço livre, pode ancorar; mais "ancorando" e "ancorada". O servidor manda o estado a cada 1 s ao piloto (`AnchorStatusPayload`, junto da telemetria) e na hora da recusa; o painel (`AnchorHud`) mostra a caixa "Âncora" no alto e a recusa sai na barra, com os blocos que impedem (até 16) destacados por partículas vermelhas por 8 s.
- **Tecla só ancora**; desancorar continua pela ponte (agachado, mão vazia). O comando de dev `ship anchor` segue sem exigir gerador.
- **Limites do S6 corrigidos**: a âncora é esquecida (conferência a cada 1 s) se a ponte registrada for quebrada; o pouso antigo (`RocketLanding.land`) de uma nave sem chão sob a base registra a âncora. **Não feito**: congelar fluido só nos blocos da nave (continua na caixa inteira).
- Verificado no autoteste: sem gerador (recusa), pedra encostada (recusa, blocos destacados = a pedra), rápido demais (recusa; nave indo e voltando a 2,5 b/t), ancorada pela tecla (romaneio 0 perdas, carga gasta), desancorar com o piloto (gerador sem carga, painel e recusa "sem carga"), recarga com a nave em blocos, pouso antigo registrando âncora, ponte quebrada desfazendo o registro. Prints em `run-test/screenshots/autotest_shipanchor2_*.png`.
- Não verificado: as partículas de destaque não aparecem no print (a câmera do cockpit olhava o interior; só o estado é conferido), dois clientes, servidor dedicado, giro de 1 a 3 quartos de volta, a tecla em nave sem tripulação no cliente remoto.

## Resultado da fase 2e, frente 3 (26/10/2026): porta, baú e assento com a nave em movimento

Autoteste `-PshOnly=shipdeckuse` (`ShipDeckUseDevtest`, `run-test/`): **30 verificações, 0 falhas**. Uma sala de 9x7x9 (casco, porta de carvalho, baú, fornalha, `ship_seat` e a ponte) sobe a 10 b/t, gira 1 grau por tick e fica inclinada 20 graus; o jogador está no convés, e o clique usa o mesmo caminho do jogador (`DeckAim.useNow`, o que o evento de uso chama), com o olhar apontado de verdade para o bloco.

- **Mira** (`client/ship/DeckAim`, `ship/deck/PortraitRay`, `PortraitShapes`): o raio do olhar é refeito em coordenadas locais da nave (no convés, o olhar local do jogador; de fora, o olhar do mundo levado ao referencial da nave pela pose) contra as formas de SELEÇÃO do retrato (a porta aberta é uma placa fina; a colisão do convés continua separada), por DDA célula a célula. Quem estiver mais perto, mundo ou retrato, ganha (no convés só vale o retrato). O contorno preto aparece no bloco mirado (`RocketRenderer`, linhas do vanilla dentro da pose da nave). Medido: a mira acerta a porta a 1,503 blocos (esperado 1,5), o baú, a fornalha, o assento e a ponte.
- **Clique** (`DeckUsePayload` cliente para servidor, `PortraitUse`): o servidor confere a nave, o bloco e o alcance (alcance de bloco + 3, pelo olho local aceito) e age. O clique do vanilla é cancelado só quando a mira acertou o retrato (e o jogador não está agachado).
- **Porta, alçapão e portão** (`PortraitUse.toggle`): o servidor alterna `OPEN` no retrato (as duas metades da porta; a de ferro não abre à mão; o servidor não fecha a porta sobre um tripulante). O retrato é imutável, então a mudança gera outro (`RocketPortrait.withChanges`, id novo = chave do cache de malha) e vai aos clientes em `PortraitDeltaPayload` (id de base, id novo e `PortraitChange`s), nunca o retrato inteiro: **a porta custa 57 bytes; o retrato inteiro da nave de teste de 4.000 blocos custa 77.918 bytes**. O cliente aplica sobre o retrato que tem (guarda o delta se o retrato grande ainda chega em partes) e o desenho mostra a malha antiga até a nova assar (sem piscar a nave). Medido: porta fechada barra o jogador (x máximo 2,700 contra a porta em 3,0), aberta pelo clique deixa passar (x final 4,815, 0 recusas do servidor, 0 correções do cliente), retrato do cliente com o mesmo id do servidor.
- **Baú, barril e todo block entity com inventário em grade de 9 colunas** (`PortraitContainers`, `SharedProxies`): abrir cria um contêiner proxy com o conteúdo do NBT do retrato e abre o menu vanilla (`ChestMenu`) sobre ele; cada mudança devolve o NBT ao retrato no servidor (delta aos clientes) e fechar solta o proxy. O inventário existe num lugar só (o retrato): dois jogadores no mesmo baú recebem a MESMA instância do proxy (`SharedProxies`, testado). Medido: o menu abriu com 12 diamantes e 20 pedregulhos do retrato; tirar os diamantes e pôr 7 tábuas deixou o NBT do servidor com 0 diamantes e 7 tábuas, os totais de baú + jogador iguais antes e depois (maçãs 3, diamantes 12, pedregulhos 20, tábuas 7: nada duplicado nem perdido), o NBT do cliente igual ao do servidor (5 deltas), proxies 1 aberto e 0 depois de fechar.
- **Assento e ponte** (`RocketEntity.sitAt`, `seatOf`, `removePassenger`): clicar num `ship_seat` ou no cockpit do retrato senta o jogador como passageiro da nave; o assento de cada passageiro vai num dado sincronizado (`SEATS`, "uuid=posição") e o `positionRider` põe cada um no assento dele pela pose (o piloto é quem está na ponte). Cada assento tem no máximo um passageiro; mais de um jogador senta em assentos diferentes. Levantar com a nave em voo devolve o jogador ao convés em pé, no ponto livre mais perto do assento (`DeckStand`, testado); quem sentou na ponte levanta com a tecla de sair (controle de voo) em voo. Pousada, sai como sempre. Medido: sentou no assento com erro de posição de 0,278 bloco (servidor e cliente iguais, na altura de sentar do vanilla), não pilota e deixou o convés a pé; um segundo passageiro na ponte ao mesmo tempo ficou a 0,069 do assento dele; levantar (a tecla Shift de verdade) voltou ao convés a 1,00 bloco do assento, cabendo em pé; sentar na ponte fez do jogador o piloto no cliente e no servidor.
- **Outras máquinas** (fornalha, bancada, alavanca, botão, bigorna, qualquer block entity que o proxy não sirva): o clique em movimento mostra na barra de ação "A nave precisa estar ancorada, pousada ou no espaço" (só no convés e em naves; num foguete visto de fora o clique continua sentando o piloto). Medido: 1 aviso, a fornalha não abriu.
- **Conversão**: depois do pouso (`RocketLanding.land`), os blocos do mundo têm a porta aberta (as duas metades) e o baú com 0 diamantes, 7 tábuas e 20 pedregulhos; a fornalha ficou intacta. O retrato é a única fonte, então a âncora e o estaleiro já levam o NBT novo (a âncora foi verificada no `shipanchor2`, 24 verificações, 0 falhas).
- Prints (`run-test/shipdeckuse/screenshots/`): `sdu_1_contorno_porta`, `sdu_3_contorno_bau`, `sdu_4_menu_bau_em_voo`, `sdu_5_aviso_fornalha`, `sdu_6_sentado`, `sdu_2_porta_aberta`.
- Testes unitários: `PortraitRayTest` (7: bloco, face, o mais perto, alcance, forma parcial, origem dentro da caixa), `PortraitDeltaTest` (3: ida e volta do delta, cópia da grade), `SharedProxiesTest` (4: o proxy compartilhado e o ponto em pé).
- Não verificado: dois clientes de verdade no mesmo baú (só o proxy compartilhado por teste unitário e um porco como segundo passageiro), a mira de quem olha a nave de fora (o código serve aos dois, o autoteste só cobre o convés), "o mundo mais perto ganha" em jogo (só o raio puro), baú duplo (cada metade abre o próprio inventário), o aviso de máquina em nave `ship` real, a tecla de sair da ponte em voo com o voo real (o roteiro da nave não roda o controle de voo; o autoteste levanta o piloto pelo servidor), servidor dedicado. Limites: o clique não toca em pistão, redstone nem sons de porta (nenhum som é tocado ainda); fechar a porta com alguém no vão é recusado em silêncio.
