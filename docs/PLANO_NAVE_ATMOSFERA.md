# Plano: andar na nave dentro do planeta (v1)

Estudo de 21/10/2026. Pergunta: dá para a tripulação andar dentro da nave enquanto ela sobe, desce, paira ou voa baixo e devagar
dentro da dimensão de um planeta, como já acontece no espaço?

**Resposta: sim, com um híbrido de três regimes.** Nenhuma técnica sozinha cobre bem do voo alto e rápido até pairar a 3 blocos
do chão; cada faixa usa a técnica que funciona nela. O objetivo é que a tripulação passe **quase a missão inteira em pé e com as
máquinas funcionando**, ficando sentada só no trecho baixo e em movimento se o spike mais arriscado (S5) falhar.

## Por que andar funciona no espaço e não no planeta

No espaço a nave é feita de **blocos reais parados** no estaleiro (`spacehorizons:shipyard`); quem se move é o céu
(`SkyObserver.forShip`, `SkyAttitude`, `SkyRendererMixin`). No planeta a nave é uma `RocketEntity` desenhada pelo retrato, e uma
entidade em movimento não tem chão para pisar. Toda solução precisa ou manter a nave parada em blocos, ou pagar a colisão em movimento.

## Os três regimes

| Regime | Condição (a calibrar no S4) | Representação | Andar? | Máquinas? |
|---|---|---|---|---|
| Alto ou rápido | acima de **H1** sobre o terreno (ex.: 1.000) ou acima de **V1** (ex.: 25 b/t) | Estaleiro, com o planeta desenhado em volta (**A-lite**) e uma "sombra" da nave para quem está no chão | Sim | Sim |
| Baixo e lento | abaixo de H1 e de V1 | Entidade com convés andável (**B-lite**), se o S5 passar; senão entidade com a tripulação sentada (como hoje) | Sim (B-lite) | Não (só porta, baú, assento) |
| Parado no ar | velocidade ~0 e comando "ancorar" | Blocos reais no mundo do planeta (**âncora**, C-estático) | Sim | Sim |
| Pouso | contato | `RocketLanding` (já existe) | n/a | n/a |

### A-lite: o estaleiro desce para dentro da atmosfera

A nave continua blocos no estaleiro até H1 (hoje a troca é a 5.000 na subida e pelo critério do foguete na descida). O céu do
estaleiro passa a ser o de **dentro da atmosfera**: a malha analítica do planeta (`PlanetMeshRenderer`, a mesma do espaço), a cor e a
névoa da atmosfera (`AtmosphereClient`, que hoje trata o estaleiro como espaço), o sol e uma camada de nuvens virtual na altitude certa.
De cima, o detalhe de bloco não aparece; o S4 mede até que altura a ilusão aguenta.

- **Física**: o voo virtual (`ShipFlight`) ganha arrasto e o empuxo vertical dos bocais para baixo (`ShipLiftoff`), o mesmo integrador
  que a entidade já usa.
- **Quem está no chão** vê uma entidade-sombra só visual na dimensão do planeta, desenhada com o retrato (`RocketMeshCache`) e movida
  pelo estado virtual (o inverso do `SpaceParking`).
- **Troca de regime**: pré-carga do ponto de troca (`ChunkGate`, `LandingPreloader`), como a descida da fase 2b já faz.
- **Descartado: A completo** (chunks reais e LOD do DH de outra dimensão em volta do estaleiro). Exigiria um segundo `ClientLevel`,
  um segundo renderizador do Sodium e o DH alimentado por outro level, como o Immersive Portals: anos de trabalho e incompatível com
  Sodium, DH e Iris a cada versão.

### B-lite: convés móvel

A nave é a entidade, mas os tripulantes em pé ficam no referencial dela: a cada tick o deslocamento da nave é aplicado ao jogador antes
do movimento dele, e a colisão é feita contra as caixas da grade do retrato, no referencial local.

- **A simplificação que torna isso realista**: na atmosfera a nave fica **sempre de pé**, com guinada livre e arfagem e rolagem só
  visuais. Com rotação só em Y, o jogador vira um cilindro vertical e a colisão no referencial local é exata contra caixas alinhadas,
  sem a colisão com caixas giradas que torna o Create e o Valkyrien Skies tão caros. Combina com o voo assistido estilo No Man's Sky
  do Tier 3.
- **Reaproveita**: `RocketEntity` e o retrato, `RocketPositionMixin`, `ShipFrames` (local ↔ mundo), `ServerGamePacketListenerImplMixin`
  (contra o falso "moved wrongly").
- **Interação limitada**: com a nave em movimento os blocos são dados da entidade, não do mundo. Portas, baús e assentos por menus
  proxy; fornalha, redstone e máquinas não funcionam nesse regime (só no estaleiro e na âncora).
- **Riscos**: jitter de rede (a entidade é interpolada no cliente enquanto o jogador local roda no tick atual; cair pelo piso em
  aceleração é o bug clássico), dois jogadores e servidor dedicado (nunca testados no projeto).
- **Descartado: B completo** (máquinas funcionando e rotação livre): é refazer o Valkyrien Skies.

### Âncora no ar (C-estático)

Parada, a nave vira **blocos reais** no ar, pelo mesmo caminho do pouso (`RocketLanding.land` com a pré-verificação de espaço), só que
sem chão. A tripulação anda, as máquinas funcionam, o DH e o Sodium tratam a nave como mundo e outros jogadores a veem de verdade:
uma base flutuante. Para voar de novo, monta-se pela ponte (fase 2a-i). Barato e muito valioso.

- **Novo**: pré-verificação de volume livre no ar; impedir que areia, cascalho e fluidos da nave caiam; mobs que nasceriam no teto;
  transição sem teleporte (os blocos aparecem onde a entidade estava; a tripulação fica no mesmo lugar relativo, como `movePeople`).
- **Descartado: re-colocação contínua** (mover os blocos a cada N ticks para voar): pelo S3, 8.000 blocos custam de 0,6 a 1 s em lotes,
  e cada passo refaz luz, seções do Sodium e LODs do DH, com flicker. Serviria só como "elevador" de ajuste fino.

## Fases

| Fase | Entrega | Esforço | Critério de aceite (autoteste) |
|---|---|---|---|
| Pré-requisito | Fase 2b das naves (descida e pouso) fechada e mesclada | — | `shipdescent` e regressão verdes |
| S6 | Spike âncora | P | `-PshOnly=shipanchor`: entidade para a 60 blocos do chão, ancora, o robô anda, abre o baú e liga a fornalha, desancora, voa 100 blocos e ancora de novo; romaneio (`ShipManifest`) com 0 perdas nas duas idas; conversão < 1 s para 8.000 blocos; o jogador em pé fica a < 0,1 bloco da posição relativa ao convés |
| S4 | Spike estaleiro baixo | M | `-PshOnly=shipatmo`: nave de teste no estaleiro com o estado virtual a 3.000, 1.500, 800 e 400 blocos; print pela janela contra o print de um jogador na Terra na mesma posição e direção: horizonte com < 0,5° de diferença, cor média do céu com ΔE < 10, malha < 2 ms por quadro; troca estaleiro ↔ entidade sem salto (< 1° no céu). Morre se o "pulo" na troca for grosseiro mesmo a 1.500 |
| S5 | Spike convés móvel | M/G | `-PshOnly=shipdeck`: entidade por roteiro (subida 2 b/t, guinada 20°/s, descida 3 b/t, pairar, voo de 1 a 8 b/t), robô andando em quadrado no convés por 2.000 ticks: 0 quedas pelo piso, altura do pé com p99 < 0,05 bloco e máximo < 0,15, deriva parado < 0,02 bloco/tick, 0 correções "moved wrongly", câmera dentro de 5% da medida de tremor do `ship`; repetir com 100 ms de latência simulada. Morre se o p99 passar de 0,1 bloco a 8 b/t ou houver queda |
| 2e | Âncora no ar | P/M | S6 + regressão `ship*` verde; tecla e botão no painel; recusa sem volume livre |
| 2f | Estaleiro na atmosfera (A-lite) | G | Critérios do S4; subida e descida com tripulante **em pé** até H1 (a recusa da D4 passa a valer só abaixo de H1); um segundo ponto de vista no chão vê a sombra a < 2 blocos da posição virtual |
| 2g | Convés móvel (B-lite), só se o S5 passar | G | Critérios do S5 com 2 clientes (montar o autoteste de dois clientes faz parte do aceite) |
| 2h | Trocas de regime sem costura | M | Troca com < 1° no céu e < 0,1 bloco no jogador; pré-carga de chunks no ponto de troca |

Ordem: **S6 → 2e** (resultado garantido e barato), **S4 → 2f**, e **S5** como aposta medida; **2g** só se o S5 passar; **2h** fecha.

## Decisões para o usuário

| | Decisão | Recomendação |
|---|---|---|
| A1 | H1 e V1: altitude e velocidade da fronteira estaleiro ↔ entidade | Decidir com os números do S4 |
| A2 | Na atmosfera, a nave fica sempre de pé (guinada livre, arfagem e rolagem só visuais)? | Sim: é o que viabiliza o B-lite e combina com o voo assistido |
| A3 | Se o B-lite falhar, aceitar a tripulação sentada no trecho baixo e em movimento? | Sim (dura segundos a poucos minutos) |
| A4 | A âncora no ar é gratuita, gasta combustível ou exige um bloco (gerador antigravidade)? | Exigir um bloco e gastar energia, não combustível |
| A5 | No convés móvel, quais interações valem? | Porta, baú e assento |

## Arquivos que mudam

`ship/ShipFlight`, `ship/ShipFlightTicker` (física atmosférica, gatilhos de regime), `ship/ShipAscent`, `ship/ShipDescent` (fronteira
H1/V1), `client/ship/ShipSkyClient`, `client/space/SpaceSkyClient`, `client/planetmesh/PlanetMeshRenderer`, `client/atmosphere/AtmosphereClient`
(vista do planeta de dentro do estaleiro), `ship/ShipConversions`, `ship/YardToRocketJob`, `ship/RocketToYardJob` (trocas de regime),
`rocket/RocketEntity`, `rocket/RocketLanding` (convés móvel e âncora).
