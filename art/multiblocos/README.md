# Multiblocos: tanque e motores (fontes de arte)

Arte da seção 3 de `docs/PLANO_TIERS.md`: tanque com texturas conectadas e motores 2x2 e 3x3 com modelo próprio da
estrutura formada. Texturas geradas com a skill `minecraft-entity` (modo rápido, templates e paleta de `art/textures/`);
modelos escritos por script e importados com o MCP headless `bbmodel`. Nada é gravado em `assets/` sem a escolha do
usuário (página do `escolha.py`).

## Arquivos

| Arquivo | O que é |
|---|---|
| `jobs.json` | Campos de prompt de cada textura (lado do tanque branco e isogrid, topo do tanque, tubeira, boca da tubeira, placa, turbobomba). |
| `run_jobs.py` | Roda os jobs um por vez com pausa (`--pausa S`, padrão 25 s), 4 variantes cada. Saída em `art/_work/multiblocos/out/`. |
| `candidatos.py` | Candidatas 16x16 de cada job: as variantes do `texture.py` (voto) e os mesmos brutos por média de área (BOX). |
| `tank_ct.py` | Converte uma candidata no formato das texturas conectadas (ladrilho 8x8 + moldura lisa de 4 px) e faz a prévia das colunas conectadas (usa `tools/gen_connected_block.py`). |
| `build_engines.py` | Geometria dos motores: `models/src/rocket_engine_<2x2|3x3>_<A|B>.json` (modelo Java pronto). |
| `models/*.bbmodel` | Os mesmos modelos importados no Blockbench (`bbmodel_import_java_block`, texturas da letra A embutidas). |
| `iso.py`, `render_engine.py` | Renderizador isométrico próprio (numpy) para as prévias e as renders de cada combinação. |
| `build_escolha.py` | Monta `art/_work/escolha_multiblocos/` (imagens + `escolha.json`). |
| `apply_escolha.py` | Grava a escolha em `assets/` com os ids abaixo. |

## Ids usados pelo código

| Id | Arquivo em `assets/spacehorizons/` |
|---|---|
| `rocket_fuel_tank_side_ct` | `textures/block/rocket_fuel_tank_side_ct.png` (lado; base do `gen_connected_block.py`) |
| `rocket_fuel_tank_end` | `textures/block/rocket_fuel_tank_end.png` (topo e fundo; base do `gen_connected_block.py`) |
| `rocket_engine_2x2` | `models/block/rocket_engine_2x2.json` + `textures/block/rocket_engine_2x2_{nozzle,exit,plate,pump}.png` |
| `rocket_engine_3x3` | `models/block/rocket_engine_3x3.json` + `textures/block/rocket_engine_3x3_{nozzle,exit,plate,pump}.png` |

Se o código usar outros ids, troque os nomes em `apply_escolha.py` e o prefixo em `Model.json()` de `build_engines.py`.

## Tanque: por que `--frame 4`

O `gen_connected_block.py` preenche as bordas abertas repetindo o interior com período `16 - 2*frame`. A emenda entre
dois blocos só some se `2*frame` for múltiplo do período: com `--frame 2` (período 12) sempre sobra emenda; com
`--frame 4` (período 8) é exata. Por isso as texturas do tanque têm um ladrilho 8x8 repetido no miolo e uma moldura de
4 px de uma cor só (um rebite no meio de cada lado). Moldura com faixas claro/escuro deixaria um entalhe nos cantos em
que só uma das bordas está fechada (o script mostra o canto original nesse caso). Depois de aplicar:

```bash
python tools/gen_connected_block.py --name <id do bloco> --texture spacehorizons:block/rocket_fuel_tank_side_ct \
    --texture-end spacehorizons:block/rocket_fuel_tank_end --frame 4
```

A opção "atual" usa as texturas atuais sem conversão (prévia com `--frame 2`, com emendas). Se lado e topo forem
"atual", use `--frame 2`; se só um for, o `apply_escolha.py` converte esse para o formato de moldura 4.

## Motores: bloco mestre e limites

- **2x2** (base 2x2, altura 2): o mestre é o bloco de menor x, y e z da estrutura; o modelo ocupa 0..32 nos três eixos.
- **3x3** (base 3x3, altura 3): o mestre é o bloco do centro (meio da base, camada do meio); o modelo ocupa -16..32.

Os dois cabem no limite de elements do Minecraft (-16..32), sem dividir em partes. Cada caixa é cortada nas fronteiras
dos blocos e o UV é a posição dentro do bloco, então as texturas 16x16 ficam 1:1 e contínuas; só a boca da tubeira
(`exit`, face de baixo da última camada do sino) usa a textura inteira esticada sobre a boca, para um brilho só no meio.
Nenhum element usa rotação; não há `cullface` (os outros blocos da estrutura ficam invisíveis enquanto formada).
Elements: 2x2 A 136, 2x2 B 175, 3x3 A 286, 3x3 B 327. A frente não importa (simétrico o bastante); o sino aponta para -y.

| Proposta | Forma |
|---|---|
| 2x2 A | placa cheia, domo do injetor, câmara, garganta, sino de 8 camadas, duas turbobombas verticais com tubos e escapes |
| 2x2 B | quadro aberto com cruz e pernas, câmara alta com coletor, sino de 9 camadas com anel, turbobomba deitada e escape |
| 3x3 A | placa cheia, domo, câmara, sino de 13 camadas com anel na boca, duas turbobombas verticais, linhas de propelente |
| 3x3 B | placa com degraus, quatro pernas e quadro, câmara com coletor, sino de 13 camadas com dois anéis, duas turbobombas deitadas |

## Refazer e aplicar

```bash
python art/multiblocos/run_jobs.py                 # texturas (GEMINI_API_KEY), um job por vez
python art/multiblocos/build_engines.py            # modelos; reimporte models/src/*.json para models/*.bbmodel se mudar
python art/multiblocos/build_escolha.py            # pasta da escolha
python ~/.claude/skills/minecraft-entity/scripts/escolha.py serve art/_work/escolha_multiblocos
python art/multiblocos/apply_escolha.py art/_work/escolha_multiblocos/escolhas.json --dry-run
python art/multiblocos/apply_escolha.py art/_work/escolha_multiblocos/escolhas.json
```
