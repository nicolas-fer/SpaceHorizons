# Missão completa à Lua e de volta (provada em jogo)

Terra, órbita baixa, transferência para a Lua (TLI), captura, pouso suave na Lua, decolagem, volta, reentrada e pouso suave na Terra, com
um foguete só, pela física e pelas zonas de verdade (`rocket/*`, `space/flight/*`). Rodada em jogo pelo cenário
`./gradlew runClientTest -PshOnly=mission` (`client/devtest/MissionDevtest`, piloto em `MissionPilot`) e, sem o jogo, por
`MissionSimTest` (a mesma `FlightMachine`, as mesmas zonas, terreno analítico).

## O foguete

O foguete de teste A do voo (4 motores, 12 tanques avulsos, Δv 253 b/t cheio) **não basta**: o mesmo piloto o leva à Lua, pousa, decola e
volta, mas chega à Terra sem Δv para o pouso (`MissionSimTest`). A missão pede ~264 b/t, e o painel só promete a missão inteira com 5% de
folga (276,4 b/t, `MissionBudget`).

O foguete da missão (voo `M`, `FlightDevtest`) é 3x3, **agora com os multiblocos**: camada de 9 motores
avulsos embaixo (a camada plana não forma o cubo 3x3x3, ver `docs/DESIGN_FOGUETE.md`), **uma coluna de tanque 3x3x5** (45 blocos, 180.000 mB),
cockpit e 8 cascos no andar de cima, paraquedas e 2 pernas (66 blocos).

| | antes dos multiblocos (45 tanques avulsos) | agora (coluna 3x3x5 formada) |
| --- | --- | --- |
| Massa seca dos tanques | 9,0 t (45 x 0,2) | 5,16 t (`0,2 x 45 x (0,4 + 0,6 x 78 / 270)`, 78 faces expostas) |
| Massa seca total | 16,3 t | 12,46 t |
| Massa cheia | 160,3 t | 156,46 t (144 t de combustível) |
| Empuxo | 22,5 | 22,5 |
| Δv cheio | 292,6 b/t | **323,9 b/t** |
| TWR na Terra | 1,75 | 1,80 |

O Δv novo vem de `128 x ln(156,46 / 12,46)` (ve = 128 b/t, massas de `FlightConstants`; conferido com os 263,97 + 59,9 medidos em jogo); o
`MissionSim.ROCKET_M` dos testes ainda usa os 45 tanques avulsos (Δv 292,6) e continua verde, e o teste
`projetoDeLuaPousoEVoltaFechaAMissaoInteiraNoSimulador` voa o projeto novo (coluna formada) até o fim.

## Resultado em jogo

A missão em jogo (`-PshOnly=mission`) com o foguete novo (coluna 3x3x5) fechou com **Δv total 263,97 b/t de 323,9 e sobraram 59,9 b/t**
(o primeiro fechamento, abaixo, com o foguete antigo, gastou 263,1 de 292,6 e sobraram 29,5). O Δv de cada fase é quase só da manobra, então a
tabela por fase da primeira rodada continua valendo como referência (a rodada nova só difere 0,87 b/t no total); não foi refeita fase a fase.

### Primeira rodada (foguete antigo; relógio de lançamento 68.300, 52.289 ticks, 249 s a 300 ticks/s)

| Fase | Resultado | Δv (b/t) |
| --- | --- | --- |
| Subida | corte com Ap 6.011 a 3.257 de altitude, 362 ticks | 63,8 |
| Circularização | Ap 6.011, Pe 5.231, tick 924 | 14,9 |
| **Órbita baixa** | | **78,8** |
| TLI | janela 362 ticks depois da órbita, queima de 93 ticks | 26,8 |
| Correção de meio de curso 1 (250 ticks depois) | leva o periastro de −25.993 a 1.499 pelo lado de fora da Lua | 3,07 |
| Correção 2 (6.000 ticks) | periastro 2.385 a 1.498 | 0,04 |
| Voo à Lua | entra na esfera no tick 18.124 (62.000 de altitude, 17,8 b/t relativa), Pe 1.496 | |
| Captura (retrógrada, no periastro, 29 ticks) | Ap 1.497, Pe 1.492, v 18,04 | 9,73 |
| Freio na órbita (anula a velocidade ao chão da Lua) | v ao chão 0,6 b/t a 1.490 de altitude | 18,63 |
| Descida motorizada | entra na dimensão da Lua a 420 de altura, 5,3 b/t; **pouso: vertical −0,66, horizontal 0,01, inclinação 4,1°** | 5,67 |
| Decolagem da Lua | corte com Ap 1.790 | 6,58 |
| Circularização lunar | Ap 1.791, Pe 1.531 | 16,30 |
| Queima de volta (espera 4.945 ticks pelo ponto) | periastro da Terra 995 de altitude (alvo 1.000) | 8,16 |
| Correções da volta | nenhuma necessária (erro < 25 blocos) | 0 |
| Reentrada (chega a 14.000 de altitude, 109,7 b/t) | freio com o motor de 3.000 a 1.200; entra na dimensão da Terra a 1.246, 14,7 b/t | 1,65 + 87,8 |
| Pouso na Terra | paraquedas abre a 8 b/t, **vertical −0,44, horizontal 0,01, inclinação 1,4°**, 5.276 mB restantes | |
| **Total** | **263,1 de 292,6** (sobram 29,5); com o foguete novo, 263,97 de 323,9 (sobram 59,9) | |

Zonas: LEO e a captura ficam na dimensão de espaço; a descida lunar cruza para `spacehorizons:moon` a 420 de altura (altitude de entrada
1.000, entrada lenta a 300 sobre o terreno); a decolagem sai para o espaço aos 1.250. O foguete espera ao máximo 5 ticks pelo piloto nas
travessias (`Foguete chegou à dimensão ... esperou 5 ticks`), nunca segura por chunks, não abre tela de carregamento (o log `CLIENTE` de cada
print mostra `tela=nenhuma`) e o cliente acompanha em 3ª pessoa o tempo todo. Nenhum bug de física, de zonas, de `SpaceParking`, de
`ZoneSwitch` ou de pouso lunar apareceu: a descida de 5,3 b/t chega a tempo da pré-carga (40 chunks, impacto previsto 38 ticks antes).

## O que a missão mostrou (e o que foi corrigido)

- **A TLI do planejador mira o centro da Lua projetado no plano da órbita, e a Lua fica a 5° do equador**: na chegada ela está até
  ~100.000 blocos fora do plano da nave (de `bN` = 0 a ±104.000 conforme o relógio). Corrigir isso no meio do caminho custa muitos b/t
  (estimativa: a ~5.000 blocos por b/t de Δv normal, mais de 13 b/t por 68.000 blocos; a correção simulada com a Lua longe do nó não convergiu a um custo útil). Com o relógio certo (a Lua cruza o equador perto da chegada) o `bN` fica em ±2.000 e
  a correção custa só ~3 b/t, que é deslocar o ponto de passagem para o lado de fora da Lua (de 5.000 a 70.000 blocos de impacto). O
  relógio de lançamento 68.300 (e o 12.000, simulado: a espera pela TLI é maior) dá essa janela com a órbita equatorial do spawn; a Lua cruza o equador duas vezes por volta
  (~99.000 ticks), então uma janela boa aparece a cada ~50.000 ticks. É a regra do roteiro abaixo.
- `space/flight/LunarPlanner` (novo): previsão da chegada (entrada na esfera, periastro, vetor de impacto no plano B), correção de meio de
  curso (Gauss-Newton sobre o plano B, a órbita retrógrada vem do lado de fora) e queima de volta (varre o ponto da órbita lunar e o Δv até o
  periastro da Terra acertar). Testes em `LunarPlannerTest`.
- Em jogo, a circularização com o nariz no prógrado afundava a nave durante a queima (a gravidade que a velocidade ainda não equilibra) e a
  órbita saía com o apoastro dezenas de milhares de blocos acima; o piloto agora queima na horizontal segurando a altitude. Isso é do piloto
  de teste, não do jogo, mas é o que o jogador também tem de fazer.

## Roteiro da missão (orçamento de Δv por fase, foguete de 324 b/t)

1. **Plataforma**: monte o foguete (cockpit, motores, tanques, paraquedas, pernas) e abasteça. Para a ida e volta o Δv precisa de ≥ 276 (o painel promete com 5% de folga):
   o foguete de 253 não fecha o pouso lunar. Lance para o **leste** (o planejador avisa se a órbita for para o oeste).
2. **Subida até a órbita baixa** (~79 b/t, ~900 ticks): curva de gravidade para o leste (vertical até 100, inclina até a horizontal aos 3.000),
   corte com Ap 6.000; no apoastro, queime na horizontal até Pe 5.200. Fique acima de 5.000 (altitude de saída).
3. **Janela da TLI** (~27 b/t): o painel "Moon planner" mostra a fase atual e a ideal e acende "queime agora". Lance perto do relógio em
   que a Lua cruza o plano do equador na chegada; fora dele, a correção do passo 4 custa muito mais.
4. **Correção de meio de curso** (~3 b/t, a ~250 ticks da TLI) e uma pequena (≤ 0,1) na metade: leva o periastro a ~1.500 pelo lado de fora
   da Lua (órbita lunar retrógrada). O voo dura ~17.000 ticks até a esfera (~14 min a 20 ticks/s).
5. **Captura** (~10 b/t): retrógrado no periastro (queima de ~30 ticks) até a órbita circular a 1.500 (18 b/t, período ~9.600 ticks).
6. **Pouso lunar** (~24 b/t): aponte o retrógrado em relação ao chão (a Lua gira junto com a órbita, 1,7 b/t) e freie até ~1 b/t; caia
   de motor desligado e freie perto do chão para ≤ 1 b/t vertical e ≤ 0,5 horizontal, de pé (≤ 15°). Não há paraquedas (sem atmosfera).
   A dimensão da Lua entra a ~1.000 de altitude e 300 de altura sobre o terreno.
7. **Decolagem lunar** (~23 b/t): vertical, inclina para o leste, corte com Ap ~1.800, circularize acima de 1.300 (acima de 1.250 o foguete
   fica no espaço aberto).
8. **Volta** (~8 b/t): o planejador de volta acha o ponto da órbita lunar (espera até uma órbita, ~10.000 ticks): queima prógrada, voo de
   ~21.000 ticks, periastro da Terra ~1.000 de altitude (dentro da atmosfera de 1.800 mas acima da entrada forçada).
9. **Reentrada e pouso** (~90 b/t): chega a ~110 b/t (85 ao chão). A atmosfera (arrasto fraco num foguete pesado) quase não ajuda: freie
   retrógrado com o motor de ~3.000 a ~1.200 de altura até a horizontal ao chão ficar ≤ 25 b/t (a dimensão da Terra só carrega o terreno
   assim), abra o paraquedas abaixo de 8 b/t e deixe o motor só para suavizar o fim. Guarde ≥ 90 b/t para isso.

Total ~264 b/t (263,97 na rodada com o foguete novo); as fases mais caras são a órbita baixa (79), a reentrada (90) e o pouso e decolagem lunares (47).

## Como rodar

```bash
./gradlew runClientTest -PshOnly=mission --console=plain > run-client.log 2>&1
```

O cenário lança com `time set 68300`, roda a 300 ticks/s (`/tick rate`) e volta a 20 ticks/s em cada marco para o print (`ms_01_pad` a
`ms_18_earth_landed`). Procure `[AUTOTEST] MISSION` no log (cada fase com Ap/Pe, Δv da fase, Δv total e combustível). O teste unitário sem
o jogo é `MissionSimTest`; o do planejador, `LunarPlannerTest`.

Não verificado: o jogador no teclado (o piloto é do servidor), o painel guiando a correção de meio de curso e a volta (não há planejador
delas no painel; só o da TLI), outros relógios de lançamento em jogo, a reentrada com paraquedas só (sem freio no espaço) a 110 b/t.

## Plantas embutidas e painel de projeto (guia para montar o foguete)

Com `docs/PLANO_TIERS.md` (seção 8) o jogador não precisa descobrir o foguete: o item `rocket_blueprint` (planta) abre o menu de plantas e a
`launch_pad` mostra o holograma (peça que falta = fantasma, errada = vermelho). Plantas embutidas (`rocket/blueprint/Blueprints`, as mesmas
que o `MissionBudgetTest` voa no simulador), cada uma com uma base de plataformas:

| Planta | Projeto | Blocos (+ plataformas) | Destino prometido pelo painel |
| --- | --- | --- | --- |
| `suborbital` | motor 1x1, coluna 1x1x8, cockpit, paraquedas | 11 + 9 | salto suborbital |
| `orbital` | motor 2x2x2, coluna 1x1x4, cockpit, paraquedas | 14 + 16 | órbita baixa (Δv 164,9 b/t) |
| `lunar` | camada 3x3 de motores, coluna 3x3x5, cockpit, paraquedas, 8 cascos (sem pernas) | 64 + 25 | Lua, pouso e volta (é este roteiro) |

A `lunar` é trancada em sobrevivência até a história (livre no criativo). Sem as pernas do foguete `M` ela pesa 12,26 t seca e dá Δv ~325,8.

O **painel de projeto** (olhar a plataforma de mão vazia, clique direito fixa) calcula o Δv, o TWR e o destino do que está montado, com os
mesmos números da montagem (`RocketSpecs.statsFor`), e usa as fases desta missão como orçamento (`MissionBudget`, margem de 5%):

| Destino | Δv medido (b/t) | Exigido (+5%) |
| --- | --- | --- |
| salto suborbital | 50,0 | 52,5 |
| órbita baixa (TWR >= 1,2) | 78,8 | 82,7 |
| Lua e volta, sem pousar (paraquedas) | 216,0 | 226,8 |
| Lua, pouso e volta (paraquedas) | 263,2 | 276,4 |

Os orçamentos do painel ainda usam as fases da primeira rodada (263,2 no total; a rodada nova deu 263,97, dentro dos 5% de folga). O painel também
aponta no bloco certo o que impede ou piora o foguete (sem cockpit, motor sem tanque, tanque avulso que não fechou a coluna, falta de
paraquedas, TWR baixo). Limites: o `MissionPilot` só foi ajustado ao foguete `M` (um motor 3x3x3 de TWR 4 bate no pouso lunar) e o painel não considera a janela da Lua.
