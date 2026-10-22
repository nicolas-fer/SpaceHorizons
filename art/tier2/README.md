# Tier 2: blocos, itens, tanques e motores (fontes de arte)

Arte dos ids de `docs/TIER2_IDS.md`. Texturas com a skill `minecraft-entity` (modo rápido, templates e paleta de
`art/textures/`; no máximo 3 variantes por textura no Gemini; forma vanilla ou de item do mod = `recolor`, sem gerador);
modelos escritos por script (`build_models.py`, reaproveita `Model` de `art/multiblocos/build_engines.py`) e importados
com o MCP headless `bbmodel`; renders com o renderizador isométrico de `art/multiblocos/iso.py`. Nada é gravado em
`assets/` sem a escolha do usuário (página do `escolha.py`).

## Arquivos

| Arquivo | O que é |
|---|---|
| `jobs.json` | Campos de prompt de cada textura gerada e os recolors (base e cor, com `cor_alt` = segunda cor). |
| `run_jobs.py` | Roda os jobs um por vez, pausa de 20 s entre os do gerador, pula os já feitos, para no primeiro erro (cota: mensagem exata). Saída em `art/_work/tier2/out/`; chamadas contadas em `art/_work/tier2/chamadas.log`. |
| `build_models.py` | Geometria: motores (1x1, 2x2, 3x3; propostas A e B) e `rtg_generator` (A e B) em `models/src/*.json`. |
| `models/*.bbmodel` | Os mesmos modelos importados no Blockbench (`bbmodel_import_java_block`; texturas ainda não escolhidas ficam como caminho). |
| `render.py` | Render (vista de cima e de baixo) de um modelo com um conjunto de texturas. |
| `build_escolha.py` | Monta `art/_work/escolha_tier2/` (imagens + `escolha.json`). |
| `apply_escolha.py` | Grava a escolha em `assets/` com os ids do contrato. |

## Histórico da geração (22/10/2026)

Na primeira rodada o Gemini parou no `ion_grid` (`429: Your project has exceeded its monthly spending cap.`). Depois que o
teto subiu, as 8 texturas dos motores iônico e de fusão foram geradas (`run_jobs.py ion_grid ... fusion_plate --force`,
3 variantes cada). Para não mudar as letras já publicadas na página, nessas 8 texturas a ordem é: o bruto único da
primeira rodada do `ion_grid` (guardado em `art/_work/tier2/out/_texturas/ion_grid_v0/`), as opções de recolor
(`FALLBACK` em `build_escolha.py`) e por fim as variantes do gerador. As renders usam como padrão a primeira variante do
gerador ("voto 1") de cada textura.

Caminho manual, se a API falhar de novo: `python art/tier2/run_jobs.py --so-prompt` grava os `prompt.txt` que faltam em
`art/_work/tier2/out/_texturas/<job>/`; gere à mão e continue com
`texture.py <kind> --out art/_work/tier2/out/<job>.png --from-raw <imagens>`.

`build_escolha.py` sobrescreve as imagens no lugar e troca o `escolha.json` de uma vez (pode rodar com a página no ar;
`escolhas.json` nunca é tocado). O `escolha.py serve` monta o HTML só ao subir: opções novas aparecem depois de reiniciar
o servidor. `--limpar` apaga `var/`, `render/` e `previa/` antes (só com o servidor parado).

## Motores

Uma forma desenhada em coordenadas 0..32 e escalada: 1x1 (escala 0,5, modelo 0..16, com cullface), 2x2 (escala 1,
mestre no canto mínimo, 0..32), 3x3 (escala 1,5, mestre no centro, -16..32). Sem rotação; caixas cortadas nas fronteiras
dos blocos, UV pela posição (texturas 16x16 1:1); as faces de boca (exit, grid, plasma vistas de baixo) usam a textura
inteira esticada. As quatro texturas de cada motor servem aos três tamanhos.

| Motor | Texturas (`textures/block/<motor>_<chave>.png`) | A | B |
|---|---|---|---|
| `nuclear_engine` | core, nozzle, exit, plate | escudo, reator alto, bocal longo e estreito, tubos de H2, turbobomba | reator com 8 tambores de controle e cintas, bocal mais longo com 2 anéis |
| `ion_engine` | grid, body, plate, pipe | propulsor único raso e largo, grade na boca toda, PPU e tanques de Xe | cacho de 4 propulsores com grade própria e PPU central |
| `fusion_engine` | ring, body, plasma, plate | câmara com toro, coluna de plasma entre 3 bobinas abertas | toro quadrado largo com grampos, coluna mais grossa, bobinas largas |

`textures/block/<motor>.png` recebe uma cópia da textura principal (core/body/ring) para a partícula do modelo.

`rtg_generator`: texturas `rtg_generator` (aletas e corpo) e `rtg_generator_top`, mais `machine_casing` e `hazard_stripes`.
A = cilindro com 8 aletas em cruz; B = caixa com 12 aletas em pente.

## Tanques

`hydrogen_tank` e `xenon_tank` seguem o `rocket_fuel_tank`: bases `<id>_frame.png` (lado) e `<id>_frame_end.png`
(topo/fundo) no formato de moldura 4 (`art/multiblocos/tank_ct.py`: ladrilho 8x8 sem emenda + moldura lisa). Opções:
as candidatas do gerador convertidas e o ladrilho do tanque atual recolorido (azul-gelo / violeta). Depois de aplicar:

```bash
python tools/gen_connected_block.py --name hydrogen_tank --texture spacehorizons:block/hydrogen_tank_frame \
    --texture-end spacehorizons:block/hydrogen_tank_frame_end --frame 4
python tools/gen_connected_block.py --name xenon_tank --texture spacehorizons:block/xenon_tank_frame \
    --texture-end spacehorizons:block/xenon_tank_frame_end --frame 4
```

(ou `apply_escolha.py --ct`). O script sobrescreve `blockstates/<id>.json`.

## Minério

`mars_titanium_ore` não chama o gerador: os veios são a máscara do `iron_ore` vanilla (pixels diferentes do `stone`),
recoloridos em titânio, compostos sobre o regolito escolhido. A escolha é o estilo do veio.

## Refazer e aplicar

```bash
python art/tier2/run_jobs.py                       # texturas (GEMINI_API_KEY); pula as já feitas
python art/tier2/build_models.py                   # modelos; reimporte models/src/*.json para models/*.bbmodel se mudar
python art/tier2/build_escolha.py                  # pasta da escolha
python ~/.claude/skills/minecraft-entity/scripts/escolha.py serve art/_work/escolha_tier2
python art/tier2/apply_escolha.py art/_work/escolha_tier2/escolhas.json --dry-run
python art/tier2/apply_escolha.py art/_work/escolha_tier2/escolhas.json [--ct]
```
