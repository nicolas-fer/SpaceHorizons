# Onde estamos (Space Horizons)

Painel único do projeto: o que está pronto, o que está em andamento e o que vem depois. Os detalhes técnicos continuam nos
planos de `docs/`; aqui fica só o estado. **Atualize este arquivo a cada etapa concluída ou começada.**

Última atualização: 21/10/2026.

Legenda: ✅ concluído e verificado em jogo · 🟡 em andamento · ⬜ planejado · 🧪 pronto em código, falta verificar em jogo · ⛔ bloqueado

## Agora

| | O quê | Onde |
|---|---|---|
| 🟡 | **Naves, fase 2b: descida e pouso** (estaleiro → entidade → blocos no chão). Parte 1 (código e testes unitários) pronta em branch; autoteste `shipdescent` sendo montado em partes (1: até virar entidade; 2: pouso e desmontagem; 3: desorbitação real e regressão). | `docs/PLANO_NAVES.md` |
| ✅ | **Plano: andar na nave dentro do planeta** decidido: convés andável estilo Create em movimento (máquinas param), âncora em blocos reais parada no ar. | `docs/PLANO_NAVE_ATMOSFERA.md` |

## Próximos passos (em ordem)

1. Fechar a fase 2b das naves (autoteste órbita → descida → pouso → desmontagem sem perder blocos, regressão verde) e mesclar.
2. Nave andável no planeta (`docs/PLANO_NAVE_ATMOSFERA.md`): spike do convés (S5), spike da âncora (S6), depois as fases 2e a 2g.
3. Naves 2c (EVA perto do casco) e 2d (outras naves e foguetes vistos de dentro).
4. Tiers: plantas holográficas de montagem → gerador de planetas por parâmetros e **Marte** → sistema solar centrado no sol.

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
| ⬜ | Plantas holográficas de montagem | Tiers, seção 4 |
| ⬜ | Gerador de planetas por parâmetros e Marte | Tiers, etapa 2 |
| ⬜ | Sistema solar centrado no sol (viagem a Marte) | Tiers, etapa 3 |
| ⬜ | Base lunar (salas seladas, energia, recursos lunares, combustível na Lua) | Sobrevivência, etapa 4 |
| ⬜ | História (estruturas abandonadas, diários, sinal lunar) | Sobrevivência, etapa 5 |
| ⬜ | Separação de estágios | Sobrevivência, etapa 6 |
| ⬜ | LODs do planeta inteiro com gerador próprio | `docs/PLANO_LOD_PLANETA.md` (polimento) |

### Tier 2: motores além da realidade

| Status | Etapa |
|---|---|
| ⬜ | Motores nucleares, iônicos e de fusão; planetas distantes do sistema solar (Tiers, etapa 4) |

### Tier 3: naves andáveis, `docs/PLANO_NAVES.md`

| Status | Fase | Entrega |
|---|---|---|
| ✅ | 0 | Spikes: céu virtual (S1), estaleiro (S2), conversão entidade ↔ blocos (S3) |
| ✅ | 1a a 1e | Estaleiro, blocos da nave, voo virtual, céu pela atitude, pilotar andando por dentro |
| ✅ | Polimento | Vidro conectado, motores pela direção do bocal, luz na nave de teste |
| ✅ | 2a-i | Montar no chão (agachado + mão vazia na ponte) e decolar como entidade |
| ✅ | 2a-ii | Subir até 5.000 e virar blocos no estaleiro; acelerador 0 na troca; circularizar com SAS horizontal |
| 🟡 | 2b | Descida e pouso: código pronto (🧪), autoteste em montagem |
| ⬜ | 2c | EVA perto do casco e jogador livre no espaço |
| ⬜ | 2d | Outras naves e foguetes vistos de dentro |
| ⬜ | S5, S6, 2e a 2g | Andar na nave dentro do planeta: convés andável (estilo Create) e âncora no ar |
| ⬜ | 3 | Dobra, escudos, hangar, luz do sol, ar selado; voo assistido estilo No Man's Sky |

### Tier 4: dobra e sistemas procedurais

| Status | Etapa |
|---|---|
| ⬜ | Spike de dimensão em tempo real; se passar, motor de dobra e sistemas estelares procedurais |

## Pendências e riscos conhecidos

- **Nave**: só o piloto senta (passageiros extras em pé); estaleiro escuro por dentro (sem luz do céu, D8); `ship torocket` depois da subida
  não testado; a órbita da subida de teste fica excêntrica (falta corte proporcional na queima); SAS horizontal não testado por jogador.
- **Motor de impulso com bocal para cima/baixo**: a orientação do modelo não foi vista em print.
- **Foguete gigante** (34 de largura): desmontagem recusada em terreno irregular (`SPACE_OCCUPIED`).
- **Risco aberto do S2**: uma perda de blocos no estaleiro numa rodada, não reproduzida (`docs/PLANO_NAVES.md`, resultado do S2).
- **Documentação**: o README tem seções repetidas (espaço aberto, sobrevivência, como rodar e como testar aparecem duas vezes);
  `docs/MISSAO_LUA.md` tem números do foguete antigo.
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
passou/falhou por trecho. Última rodada verde (21/10/2026): ship 30, shipflight 30, shipconvert 151, shipground 16, shipascent 22,
multiblock 169, todos com 0 falhas. Um trecho sozinho: `./gradlew runClientTest -PshOnly=<trecho>`. Os autotestes nunca tocam em `run/`
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
