# Onde estamos (Space Horizons)

Painel único do projeto: o que está pronto, o que está em andamento e o que vem depois. Os detalhes técnicos continuam nos
planos de `docs/`; aqui fica só o estado. **Atualize este arquivo a cada etapa concluída ou começada.**

Última atualização: 27/10/2026.

Legenda: ✅ concluído e verificado em jogo · 🟡 em andamento · ⬜ planejado · 🧪 pronto em código, falta verificar em jogo · ⛔ bloqueado

## Agora

| | O quê | Onde |
|---|---|---|
| ✅ | **Naves 2g: trocas sem costura**: entrar e sair do convés, sentar e levantar, ancorar e desancorar, pousar e decolar com a câmera abaixo de 0,05 bloco e 0,4° por quadro (antes: até 11,9 blocos e 117°). Mistura do casco e da câmera nas trocas de fonte; montar agachado pela ponte não derruba mais o piloto no tick seguinte. Autoteste `shipseam` 46/0 (roda sozinho no fim da regressão). | `docs/PLANO_NAVE_ATMOSFERA.md` |
| ✅ | **Naves 2e: convés andável na nave real**: outros clientes veem o tripulante no convés (erro p99 0,0007 bloco a 100 b/t, contra até 96 do vanilla; autoteste de 2 clientes `./gradlew shipdeck2`, A 32/0 e B 20/0); embarque e desembarque automáticos, subida e descida com tripulante em pé (a recusa da D4 caiu), atrito e queda pelo bloco do retrato (`shipdeckreal` 27/0, `shipdescent` 52/0); porta, baú e assento em movimento, mira e contorno no retrato, aviso nas máquinas (`shipdeckuse` 30/0). | `docs/PLANO_NAVE_ATMOSFERA.md` |
| ✅ | **Base lunar**: salas seladas com ar (gerador de oxigênio, até 1.000 blocos), energia por adjacência (painel solar, bateria, cabo), eletrolisador, refinaria lunar, extrator de hélio-3 e gelo lunar em crateras polares. Autoteste `moonbase` 24/0, `tier2` 37/0. Arte provisória. | `docs/PLANO_SOBREVIVENCIA.md` (etapa 4) |
| ✅ | **História (sinal lunar)**: 9 registros (diários e terminais), Observatório Aurora na Terra, Base Selene-1 e sonda Vigia-3 na Lua, 6 conquistas, planta lunar destravada pelo terminal da base, gancho para Marte. Autoteste `story` 45/0. Arte do terminal e do diário provisória. | `docs/PLANO_SOBREVIVENCIA.md` (etapa 5) |
| ✅ | **Naves 2f: âncora completa**: bloco `antigravity_generator` (carga gasta ao ancorar, recarga de 20 s com o bloco no mundo), tecla H e estado no painel, recusa com destaque; limites do S6 corrigidos (exceto o congelamento de fluido só nos blocos da nave). Autoteste `shipanchor2` 24/0; regressão ship* sem falhas. | `docs/PLANO_NAVE_ATMOSFERA.md` |
| ✅ | **Sistema solar centrado no sol**: Sol e Marte na física (SOIs encadeadas, zona de espaço, pouso com paraquedas, planejador no painel). Autotestes `marstrip` (viagem completa, 166,8 b/t, pouso −0,72 b/t) e `marsorbit` (decolar de Marte, órbita e descida, sem pisca de planeta). Saves: a posição virtual no NBT passou de E para I (foguete estacionado em save antigo pode reaparecer deslocado). | `docs/PLANO_TIERS.md` (seção 11) |
| ✅ | **Naves 2d**: outras naves e foguetes vistos de dentro da nave, do foguete e por um jogador livre no espaço, na posição relativa (estado a 5 Hz, casco esparso sob demanda). Autoteste `shipsee` 24/0. | `docs/PLANO_NAVES.md` |
| ✅ | **Documentação limpa**: README sem seções repetidas (com índice), `MISSAO_LUA.md` com os números atuais e as plantas embutidas. Arte da planta: o usuário manteve a atual. | `README.md`, `docs/MISSAO_LUA.md` |
| ✅ | **Spike S6: âncora no ar aprovado**: a nave parada vira blocos reais no ar e volta pela ponte; romaneio 0 perdas, conversão 281–312 ms (4.003 blocos) e 450 ms (7.997), jogador a 0,0000 bloco da posição no convés; areia, água e mobs contidos. Autoteste `shipanchor` 38/0; regressão ship* verde. | `docs/PLANO_NAVE_ATMOSFERA.md` |
| ✅ | **Spike S5: convés andável aprovado**: o jogador anda no convés da entidade a 1, 10, 50 e 100 b/t, com e sem 100 ms de latência: 0 quedas, pé a 0,0000 do piso, 0 correções; a velocidade não importa (A4 sem efeito). Autoteste `shipdeck` (fora da regressão padrão, ~15 min). | `docs/PLANO_NAVE_ATMOSFERA.md` |
| ✅ | **Conteúdo do Tier 2**: motores nuclear, iônico e de fusão (1x1, 2x2x2, 3x3x3), tanques de hidrogênio e xenônio, RTG, materiais de Marte, combustível por tipo com Δv em fases no painel. Autoteste `tier2` 37/37, `multiblock` 169/169, `flight` A–D. | `docs/PLANO_TIERS.md` (seção 9), `docs/TIER2_IDS.md` |
| ✅ | **Arte do Tier 2** escolhida pelo usuário e aplicada; `tier2` 37/0 na regressão de 22/10. | `art/tier2/` |
| ✅ | **Gerador de planetas por parâmetros e Marte** (sol de Marte corrigido no autoteste depois do merge, `mars` 10/0) (planetas em `data/spacehorizons/planet/*.json`; Lua idêntica; Marte por `/spacehorizons goto mars`, fora da órbita até a etapa 3). Autoteste `mars` 10/10. | `docs/GERADOR_PLANETAS.md` |
| ✅ | **Plantas holográficas de montagem**: item `rocket_blueprint` (menu; salto suborbital, orbital e lunar, esta trancada em sobrevivência até a história), holograma na `launch_pad` (falta = fantasma, errado = vermelho), salvar o próprio foguete como planta, progresso e destino no painel. Autoteste `blueprint` 34/0, `multiblock` 169/0. Arte do item provisória. | `docs/PLANO_TIERS.md` (seção 8) |
| ✅ | **Naves, fase 2b: descida e pouso** (estaleiro → entidade → pouso no motor → blocos no chão), autoteste `shipdescent` 40/0 na regressão. | `docs/PLANO_NAVES.md` |
| ✅ | **Plano: andar na nave dentro do planeta** decidido: convés andável estilo Create em movimento (máquinas param), âncora em blocos reais parada no ar. | `docs/PLANO_NAVE_ATMOSFERA.md` |

## Próximos passos (em ordem)

1. Naves 2c (EVA perto do casco).
2. Desorbitação real no autoteste da descida (queima retrógrada saindo de órbita fechada) e corte proporcional na circularização.
3. Tiers: planetas distantes do sistema solar (etapa 4).

## Mapa geral

### Tier 1: foguetes reais (órbita, Lua, Marte), `docs/PLANO_TIERS.md` e `docs/PLANO_SOBREVIVENCIA.md`

| Status | Etapa | Observação |
|---|---|---|
| ✅ | Planeta curvo e finito, integrado ao Distant Horizons | Fases 1 a 4 do README |
| ✅ | Terra idêntica ao Overworld, 10× maior (R ≈ 104.304) | Limitações no README, "Escala" |
| ✅ | Lua física em órbita kepleriana, Terra girando na física | |
| ✅ | Fundação, materiais, refinaria e combustível | Etapas 0 e 1 da sobrevivência |
| ✅ | Traje, oxigênio, vácuo e compressor | Etapa 2 |
| ✅ | Foguete de blocos, voo pilotável estilo KSP, dimensão de espaço, pouso | Etapas 3a, 3b, 3c; `docs/DESIGN_FOGUETE.md` |
| ✅ | Missão Terra → Lua → Terra provada em jogo (263 b/t de Δv) | `docs/MISSAO_LUA.md` |
| ✅ | Reentrada sem travadas, foguete estacionado no espaço, piloto automático criativo | |
| ✅ | Multiblocos de tanque e motor, foguetes grandes (até 8.192 blocos), painel de projeto | Etapa 1 dos tiers |
| ✅ | SAS horizontal (circularizar) no foguete e na nave | 21/10/2026 |
| ✅ | Plantas holográficas de montagem | Autoteste `blueprint` 34/0 (22/10/2026); arte do item provisória |
| ✅ | Gerador de planetas por parâmetros e Marte | Autoteste `mars` 10/10 (22/10/2026); Marte fora da órbita até a etapa 3 |
| ✅ | Sistema solar centrado no sol (viagem a Marte) | Autotestes `marstrip` e `marsorbit` (22/10/2026) |
| ✅ | Base lunar (salas seladas, energia, recursos lunares, combustível na Lua) | Autoteste `moonbase` 24/0 (22/10/2026); arte provisória |
| ✅ | História (estruturas abandonadas, diários, sinal lunar) | Autoteste `story` 45/0 (22/10/2026); arte provisória |
| ⬜ | Separação de estágios | Sobrevivência, etapa 6 |
| ⬜ | LODs do planeta inteiro com gerador próprio | `docs/PLANO_LOD_PLANETA.md` (polimento) |

### Tier 2: motores além da realidade

| Status | Etapa |
|---|---|
| ✅ | Conteúdo: materiais de Marte, combustíveis, motores nucleares, iônicos e de fusão (autoteste `tier2` 37/37, arte final aplicada) |
| ⬜ | Planetas distantes do sistema solar (Tiers, etapa 4) |

### Tier 3: naves andáveis, `docs/PLANO_NAVES.md`

| Status | Fase | Entrega |
|---|---|---|
| ✅ | 0 | Spikes: céu virtual (S1), estaleiro (S2), conversão entidade ↔ blocos (S3) |
| ✅ | 1a a 1e | Estaleiro, blocos da nave, voo virtual, céu pela atitude, pilotar andando por dentro |
| ✅ | Polimento | Vidro conectado, motores pela direção do bocal, luz na nave de teste |
| ✅ | 2a-i | Montar no chão (agachado + mão vazia na ponte) e decolar como entidade |
| ✅ | 2a-ii | Subir até 5.000 e virar blocos no estaleiro; acelerador 0 na troca; circularizar com SAS horizontal |
| ✅ | 2b | Descida (critério de entrada do foguete), pouso no motor e desmontagem no chão sem perder blocos, NBT nem combustível |
| ⬜ | 2c | EVA perto do casco e jogador livre no espaço |
| ✅ | 2d | Outras naves e foguetes vistos de dentro (22/10/2026) |
| ✅ | S5 | Spike do convés andável aprovado (22/10/2026) |
| ✅ | S6 | Spike da âncora aprovado (22/10/2026) |
| ✅ | 2f | Âncora completa (22/10/2026) |
| ✅ | 2e | Convés andável na nave real: rede e 2 clientes, embarque automático, subida e descida em pé, porta, baú e assento em movimento (26/10/2026) |
| ✅ | 2g | Trocas sem costura: convés, assento, âncora, pouso e decolagem sem salto (27/10/2026) |
| ⬜ | 3 | Dobra, escudos, hangar, luz do sol, ar selado; voo assistido estilo No Man's Sky |

### Tier 4: dobra e sistemas procedurais

| Status | Etapa |
|---|---|
| ⬜ | Spike de dimensão em tempo real; se passar, motor de dobra e sistemas estelares procedurais |

## Pendências e riscos conhecidos

- **Nave**: só o piloto senta (passageiros extras em pé); estaleiro escuro por dentro (sem luz do céu, D8); `ship torocket` depois da subida
  não testado; a órbita da subida de teste fica excêntrica (falta corte proporcional na queima); SAS horizontal não testado por jogador.
- **Descida da nave** (fase 2b): a saída de órbita não é testada (o autoteste começa de um estado posto a 1.000 blocos, com o nariz para
  cima, porque a nave gira devagar); na desmontagem girada, colunas de tanques vizinhas podem se reagrupar de outro jeito (mesmos blocos e
  combustível); pouso e desmontagem pela tecla, por um jogador, não testados.
- **Motor de impulso com bocal para cima/baixo**: a orientação do modelo não foi vista em print.
- **Foguete gigante** (34 de largura): desmontagem recusada em terreno irregular (`SPACE_OCCUPIED`).
- **Risco aberto do S2**: uma perda de blocos no estaleiro numa rodada, não reproduzida (`docs/PLANO_NAVES.md`, resultado do S2).
- **Documentação**: a tabela por fase do `docs/MISSAO_LUA.md` ainda é da primeira rodada (foguete antigo); `MissionSim.ROCKET_M` usa tanques
  avulsos (Δv 292,6) e não o foguete atual.
- **Plantas holográficas**: a planta não gira; baús e blocos sem modelo não ganham fantasma (contam no progresso); a planta lunar trancada em
  sobrevivência não foi testada (autoteste em criativo); plantas muito grandes, Sodium e Iris não medidos.
- **Tier 2**: destinos do painel para foguetes do Tier 2 vêm do orçamento de Δv (sem voo simulado); naves ignoram peças do Tier 2 e a conversão
  para o estaleiro só atualiza o combustível químico; hélio-3 (clique na Lua) não testado em jogo; carga do motor não aparece no painel.
- **Convés (S5/2e)**: câmera em 3ª pessoa atravessa o casco; tela de congelamento (gelo nas bordas) a bordo no print; tremor medido sensível a
  carga da máquina; servidor dedicado, mais de um observador, vários tripulantes na subida e na descida, baú duplo e dois clientes reais no mesmo
  baú não testados; porta não toca som; o autoteste `shipdeck2` liga `setUsesAuthentication(false)` no servidor LAN do teste (só localhost).
- **Âncora (S6/2f)**: o gerador não recarrega em voo (falta RTG); textura do gerador provisória; o congelamento de fluidos vale para a caixa inteira (inclusive água do jogador); a âncora fica registrada se a nave for quebrada;
  giro de 1 a 3 quartos, 2 clientes, reabrir o mundo com nave ancorada e jogador andando na troca não testados.
- **Naves 2d**: casco de cor chapada (sem textura); sem paralaxe da câmera (até ~2° a 300 blocos); tremor em órbita real, naves grandes e
  impostor além de 5 km não medidos; o piloto automático não recebe os outros veículos.
- **Voo a partir de Marte (vídeo do usuário, 22/10)**: o pisca de Marte e o acelerador "real 0%" foram corrigidos (`marsorbit`); a física que
  explode com combustível 0 e a câmera em geometria gigante NÃO foram reproduzidas nos autotestes (causa desconhecida).
- **Base lunar**: menus das máquinas, JEI e a conquista `sealed_room` não vistos em jogo; salas de laje/vidraça vazam por regra; energia some ao quebrar máquina.
- **História**: o Observatório só nasce com "gerar estruturas" ligado; mundos antigos não ganham os lugares; reabrir o mundo com progresso e 2 jogadores não testados.
- **Arte provisória a fazer pela skill**: blocos da base lunar, terminal e diário da história, gerador antigravidade.
- **Regressão instável sob carga**: com 3 clientes em paralelo (ou outra sessão rodando clientes), `ship` (tremor no giro), `tier2` (galão de
  hidrogênio na coluna), `shipdeckreal` (queda no slime) e `shipsee` (caixas B1, B2, C2) já falharam e passaram ao rodar de novo ou sozinhos.
- **Trocas sem costura (2g)**: a troca de dimensão (subida ao estaleiro e descida) tem a tela de carregamento do vanilla (3 a 6 ticks) e a
  câmera quadro a quadro não foi medida nela; com o servidor 40+ ticks atrasado a linha do tempo da nave treme 0,1 a 0,35 bloco, com ou sem
  troca (por isso o `shipseam` roda sozinho); a 2,5 b/t o casco do vanilla treme até 0,45 bloco; servidor dedicado e jogador real não testados.
- **`shipsee` C1 (jogador livre olha a nave)**: falha sempre, já antes da 2e: depois do `goto space` o desenho usa as distâncias velhas do
  trecho anterior (estado do `VesselRenderer` ou do cliente).
- **Marte (visual)**: vão escuro entre os chunks e a malha orbital enquanto o DH gera.
- **Não testado**: servidor dedicado, dois jogadores reais, reabrir o mundo com nave em voo.

## Decisões em aberto

| | Decisão | Onde |
|---|---|---|
| D6 | Pouso de barriga permitido? Trem de pouso obrigatório para nave grande? | `docs/PLANO_NAVES.md` |
| D7 | Batida de nave grande: o que acontece com os blocos (caixas de destroços)? | `docs/PLANO_NAVES.md` |
| D8 | Luz do sol nas janelas e luz do céu dentro da nave | `docs/PLANO_NAVES.md` |
| A2 a A4 | Nave andável no planeta: interações em movimento, custo da âncora, limite de velocidade do convés | `docs/PLANO_NAVE_ATMOSFERA.md` |

## Como verificar

```bash
./gradlew build
```

```bash
./gradlew regression --console=plain
```

A regressão roda os autotestes das naves e dos multiblocos em paralelo (3 clientes, ~8 min) em `run-test/<trecho>/` e imprime
passou/falhou por trecho. Última rodada (27/10/2026, com a 2g, 19 trechos, 24 min): 0 falhas em ship 30, shipflight 30, shipconvert 151,
shipground 16, shipascent 27, shipdescent 52, shipdeckreal 27, shipdeckuse 30, multiblock 169, blueprint 34, tier2 37, mars 10, shipanchor 38,
shipanchor2 24, shipsee 24, moonbase 24, story 45; `shipseam` falhou sob carga (tremida de lag, não de troca) e passou sozinho, 46/0, que agora é
como a regressão o roda (depois dos outros); `marsorbit` ainda sem verificações no código commitado. Um trecho sozinho: `./gradlew runClientTest -PshOnly=<trecho>`. Os autotestes nunca tocam em `run/`
(mundos de quem joga).

## Mapa da documentação

| Arquivo | Conteúdo |
|---|---|
| `README.md` | Como jogar, controles, como rodar e testar, limitações por fase |
| `docs/PLANO_TIERS.md` | Progressão inteira em 4 tiers e a ordem das etapas |
| `docs/PLANO_SOBREVIVENCIA.md` | Tier 1: materiais, oxigênio, foguete, base, história |
| `docs/DESIGN_FOGUETE.md` | Física e contratos do foguete, reentrada, multiblocos |
| `docs/MISSAO_LUA.md` | Missão Terra → Lua → Terra com orçamento de Δv |
| `docs/PLANO_NAVES.md` | Naves andáveis: arquitetura, fases e resultados |
| `docs/PLANO_NAVE_ATMOSFERA.md` | Andar na nave dentro do planeta (estudo e plano) |
| `docs/PLANO_LOD_PLANETA.md` | LODs do planeta inteiro |
| `docs/TEXTURAS_CONECTADAS.md` | Texturas conectadas (vidro, tanques) |
| `docs/GERADOR_PLANETAS.md` | Planetas por parâmetros (formato JSON) e Marte |
| `docs/TIER2_IDS.md` | Ids do conteúdo do Tier 2 (contrato entre código e arte) |
