# Onde estamos (Space Horizons)

Painel único do projeto: o que está pronto, o que está em andamento e o que vem depois. Os detalhes técnicos continuam nos
planos de `docs/`; aqui fica só o estado. **Atualize este arquivo a cada etapa concluída ou começada.**

Última atualização: 22/10/2026.

Legenda: ✅ concluído e verificado em jogo · 🟡 em andamento · ⬜ planejado · 🧪 pronto em código, falta verificar em jogo · ⛔ bloqueado

## Agora

| | O quê | Onde |
|---|---|---|
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

1. Nave andável no planeta (`docs/PLANO_NAVE_ATMOSFERA.md`): fases 2e (convés na nave real, 2 clientes) e 2g (trocas sem costura).
2. Naves 2c (EVA perto do casco).
3. Desorbitação real no autoteste da descida (queima retrógrada saindo de órbita fechada) e corte proporcional na circularização.
4. Tiers: planetas distantes do sistema solar (etapa 4).

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
| ⬜ | 2e, 2g | Andar na nave dentro do planeta: âncora no ar, convés na nave real, trocas sem costura |
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
- **Convés (S5)**: observadores (2º cliente) veriam o tripulante atrás do casco (falta mandar o estado local a eles); embarque só por comando;
  portas, baús e assentos em movimento não funcionam; atrito e efeitos de bloco vêm do mundo sob a posição; câmera em 3ª pessoa atravessa o casco;
  tela de congelamento (gelo nas bordas) a bordo no print; tremor medido sensível a carga da máquina.
- **Âncora (S6/2f)**: o gerador não recarrega em voo (falta RTG); textura do gerador provisória; o congelamento de fluidos vale para a caixa inteira (inclusive água do jogador); a âncora fica registrada se a nave for quebrada;
  giro de 1 a 3 quartos, 2 clientes, reabrir o mundo com nave ancorada e jogador andando na troca não testados.
- **Naves 2d**: casco de cor chapada (sem textura); sem paralaxe da câmera (até ~2° a 300 blocos); tremor em órbita real, naves grandes e
  impostor além de 5 km não medidos; o piloto automático não recebe os outros veículos.
- **Voo a partir de Marte (vídeo do usuário, 22/10)**: o pisca de Marte e o acelerador "real 0%" foram corrigidos (`marsorbit`); a física que
  explode com combustível 0 e a câmera em geometria gigante NÃO foram reproduzidas nos autotestes (causa desconhecida).
- **Base lunar**: menus das máquinas, JEI e a conquista `sealed_room` não vistos em jogo; salas de laje/vidraça vazam por regra; energia some ao quebrar máquina.
- **História**: o Observatório só nasce com "gerar estruturas" ligado; mundos antigos não ganham os lugares; reabrir o mundo com progresso e 2 jogadores não testados.
- **Arte provisória a fazer pela skill**: blocos da base lunar, terminal e diário da história, gerador antigravidade.
- **Regressão instável sob carga**: com 3 clientes em paralelo, `ship` (tremor no giro) e `tier2` (galão de hidrogênio na coluna) falharam
  uma vez cada e passaram ao rodar de novo ou sozinhos; não investigado.
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
passou/falhou por trecho. Última rodada verde (21/10/2026): ship 30, shipflight 30, shipconvert 151, shipground 16, shipascent 23,
shipdescent 40, multiblock 169, todos com 0 falhas (9,3 min). Um trecho sozinho: `./gradlew runClientTest -PshOnly=<trecho>`. Os autotestes nunca tocam em `run/`
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
