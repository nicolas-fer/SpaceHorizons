# Blocos da nave (fontes de arte)

Texturas e propostas de modelo de `ship_bridge`, `ship_impulse_engine`, `ship_window`, `ship_seat` e
`ship_airlock`, feitas com a skill `minecraft-entity` (modo rápido de textura, mesmos templates e paleta de
`art/textures/`) e com o MCP headless `bbmodel`. Nada aqui é gravado em `assets/` sem a escolha do usuário.

## Arquivos

| Arquivo | O que é |
|---|---|
| `jobs.json` | Campos de prompt de cada textura (formato de `art/textures/jobs.json`). |
| `run_jobs.py` | Roda os jobs um por vez, com pausa entre eles (`--pausa S`, padrão 20 s), 4 variantes cada. Saída em `art/_work/naves/out/` (fora do git). |
| `build_ship_models.py` | Geometria das propostas A e B de cada bloco com modelo (`models/src/<bloco>_<P>.json`, modelo Java pronto). Reaproveita `el()`/`model()` de `art/models/build_models.py`. |
| `models/<bloco>_<P>.bbmodel` | Os mesmos modelos importados no formato do Blockbench (`bbmodel_import_java_block`), para editar à mão. |
| `escolha_naves.py` | Monta a página autocontida `art/_work/escolha_naves/index.html` (imagens em base64) e `escolhas.json`. |
| `escolhas_modelo.json` | Cópia do modelo de `escolhas.json` (campos vazios). |
| `apply_escolha.py` | Grava a escolha em `assets/` com os ids `ship_*`. |

## Texturas por bloco

| Bloco | Texturas | Faces |
|---|---|---|
| `ship_bridge` | `ship_bridge` (telas), `ship_bridge_side`, `ship_bridge_top` (teclado) | + `machine_casing` |
| `ship_impulse_engine` | `ship_impulse_engine` (bocal, north), `ship_impulse_engine_side` | + `machine_casing` |
| `ship_window` | `ship_window` | cubo (`cube_all`) |
| `ship_seat` | `ship_seat` (estofado), `ship_seat_frame` (estrutura) | + `machine_casing` |
| `ship_airlock` | `ship_airlock` (porta, north e south), `ship_airlock_side` | + `machine_casing`, `hazard_stripes` |

A frente de todos os modelos é a face north: o blockstate gira por `facing`, como a refinaria.

## Refazer e aplicar

```bash
python art/naves/run_jobs.py                       # gera as texturas (GEMINI_API_KEY)
python art/naves/build_ship_models.py              # fontes dos modelos
# (re)importar models/src/*.json para models/*.bbmodel com bbmodel_import_java_block, se a geometria mudou
python art/naves/escolha_naves.py                  # página art/_work/escolha_naves/index.html
# preencha art/_work/escolha_naves/escolhas.json (letras) e aplique:
python art/naves/apply_escolha.py art/_work/escolha_naves/escolhas.json --dry-run
python art/naves/apply_escolha.py art/_work/escolha_naves/escolhas.json
```

## Observações

- `ship_window` sai opaca do gerador. Para vidro translúcido, o bloco precisa de render type `translucent`
  (ou `cutout`) e a textura de alfa nos pixels do vidro.
- Vidro conectado (sem moldura entre janelas vizinhas) não existe nativo no NeoForge: precisa de código
  (modelo customizado/`BakedModel` que escolhe a textura pelas vizinhas, ou propriedades de blockstate
  `north/south/east/west/up/down` com um modelo por combinação) e de texturas extras de borda.
- `ship_airlock` foi pensado como bloco único. Se virar porta de dois blocos (metade de cima e de baixo,
  aberta/fechada), os modelos precisam ser refeitos.
