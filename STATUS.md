# Onde estamos (Space Horizons)

Painel único do projeto: o que está pronto, o que está em andamento e o que vem depois. Os detalhes técnicos continuam nos
planos de `docs/`; aqui fica só o estado. **Atualize este arquivo a cada etapa concluída ou começada.**

Última atualização: 22/10/2026.

Legenda: ✅ concluído e verificado em jogo · 🟡 em andamento · ⬜ planejado · 🧪 pronto em código, falta verificar em jogo · ⛔ bloqueado

## Agora

| | O quê | Onde |
|---|---|---|
| ✅ | **Spike S5: convés andável aprovado**: o jogador anda no convés da entidade a 1, 10, 50 e 100 b/t, com e sem 100 ms de latência: 0 quedas, pé a 0,0000 do piso, 0 correções; a velocidade não importa (A4 sem efeito). Autoteste `shipdeck` (fora da regressão padrão, ~15 min). | `docs/PLANO_NAVE_ATMOSFERA.md` |
| ✅ | **Conteúdo do Tier 2**: motores nuclear, iônico e de fusão (1x1, 2x2x2, 3x3x3), tanques de hidrogênio e xenônio, RTG, materiais de Marte, combustível por tipo com Δv em fases no painel. Autoteste `tier2` 37/37, `multiblock` 169/169, `flight` A–D. | `docs/PLANO_TIERS.md` (seção 9), `docs/TIER2_IDS.md` |
| ✅ | **Arte do Tier 2** escolhida pelo usuário e aplicada; `tier2` 37/0 na regressão de 22/10. | `art/tier2/` |
| ✅ | **Gerador de planetas por parâmetros e Marte** (sol de Marte corrigido no autoteste depois do merge, `mars` 10/0) (planetas em `data/spacehorizons/planet/*.json`; Lua idêntica; Marte por `/spacehorizons goto mars`, fora da órbita até a etapa 3). Autoteste `mars` 10/10. | `docs/GERADOR_PLANETAS.md` |
| ✅ | **Plantas holográficas de montagem**: item `rocket_blueprint` (menu; salto suborbital, orbital e lunar, esta trancada em sobrevivência até a história), holograma na `launch_pad` (falta = fantasma, errado = vermelho), salvar o próprio foguete como planta, progresso e destino no painel. Autoteste `blueprint` 34/0, `multiblock` 169/0. Arte do item provisória. | `docs/PLANO_TIERS.md` (seção 8) |
| ✅ | **Naves, fase 2b: descida e pouso** (estaleiro → entidade → pouso no motor → blocos no chão), autoteste `shipdescent` 40/0 na regressão. | `docs/PLANO_NAVES.md` |
| ✅ | **Plano: andar na nave dentro do planeta** decidido: convés andável estilo Create em movimento (máquinas param), âncora em blocos reais parada no ar. | `docs/PLANO_NAVE_ATMOSFERA.md` |

## Próximos passos (em ordem)

1. Nave andável no planeta (`docs/PLANO_NAVE_ATMOSFERA.md`): spike da âncora (S6), depois as fases 2e (convés na nave real, 2 clientes) a 2g.
2. Naves 2c (EVA perto do casco) e 2d (outras naves e foguetes vistos de dentro).
3. Desorbitação real no autoteste da descida (queima retrógrada saindo de órbita fechada) e corte proporcional na circularização.
4. Tiers: sistema solar centrado no sol (viagem a Marte: órbita, zona de espaço e pouso em Marte).
5. Arte final do item `rocket_blueprint` (skill `minecraft-entity`).

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
| ⬜ | Sistema solar centrado no sol (viagem a Marte) | Tiers, etapa 3 |
| ⬜ | Base lunar (salas seladas, energia, recursos lunares, combustível na Lua) | Sobrevivência, etapa 4 |
| ⬜ | História (estruturas abandonadas, diários, sinal lunar) | Sobrevivência, etapa 5 |
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
| ⬜ | 2d | Outras naves e foguetes vistos de dentro |
| ✅ | S5 | Spike do convés andável aprovado (22/10/2026) |
| ⬜ | S6, 2e a 2g | Andar na nave dentro do planeta: âncora no ar, convés na nave real, trocas sem costura |
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
- **Documentação**: o README tem seções repetidas (espaço aberto, sobrevivência, como rodar e como testar aparecem duas vezes);
  `docs/MISSAO_LUA.md` tem números do foguete antigo.
- **Plantas holográficas**: a planta não gira; baús e blocos sem modelo não ganham fantasma (contam no progresso); a planta lunar trancada em
  sobrevivência não foi testada (autoteste em criativo); plantas muito grandes, Sodium e Iris não medidos.
- **Tier 2**: destinos do painel para foguetes do Tier 2 vêm do orçamento de Δv (sem voo simulado); naves ignoram peças do Tier 2 e a conversão
  para o estaleiro só atualiza o combustível químico; hélio-3 (clique na Lua) não testado em jogo; carga do motor não aparece no painel.
- **Convés (S5)**: observadores (2º cliente) veriam o tripulante atrás do casco (falta mandar o estado local a eles); embarque só por comando;
  portas, baús e assentos em movimento não funcionam; atrito e efeitos de bloco vêm do mundo sob a posição; câmera em 3ª pessoa atravessa o casco;
  tela de congelamento (gelo nas bordas) a bordo no print; tremor medido sensível a carga da máquina.
- **Regressão instável sob carga**: com 3 clientes em paralelo, `ship` (tremor no giro) e `tier2` (galão de hidrogênio na coluna) falharam
  uma vez cada e passaram ao rodar de novo ou sozinhos; não investigado.
- **Marte (visual)**: vão escuro entre os chunks e a malha orbital enquanto o DH gera; sem física orbital, foguete ou pouso em Marte (etapa 3).
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
