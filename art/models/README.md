# Modelos de bloco das máquinas (fontes)

Modelos customizados (elements) das máquinas e peças do foguete. Fluxo:

1. `build_models.py` escreve a geometria em `src/<nome>.json` (modelo Java; UV de cada face pela posição
   do elemento no bloco, para a textura 16x16 continuar coerente entre as peças; `cullface` nas faces que
   encostam na borda).
2. Importado para `<nome>.bbmodel` (MCP headless `bbmodel`: `bbmodel_import_java_block`, com
   `resource_packs: ["src/main/resources"]` para embutir as texturas), revisado com `bbmodel_validate`
   e `bbmodel_contact_sheet`.
3. Exportado do `.bbmodel` para `src/main/resources/assets/spacehorizons/models/block/<nome>.json`
   (`bbmodel_export_java_block`, `export_groups: false`).

O `.bbmodel` abre direto no Blockbench (formato Java Block/Item) para editar à mão; depois de editar,
exporte de novo para `models/block/`. Se mexer no `build_models.py`, rode-o e refaça os passos 2 e 3.

| Modelo | Orientação | Peças |
|---|---|---|
| `refinery` (+ `refinery_on`) | facing (blockstate) | base, corpo com a fornalha na frente, faixa de alerta, coluna de destilação de cobre com anel, chaminé, tubulação lateral |
| `oxygen_compressor` (+ `_on`) | facing (blockstate) | base, corpo com a ventoinha, topo, dois cilindros de O2 com válvulas e tubos |
| `rocket_cockpit` | facing (blockstate) | corpo com janela recuada e moldura saliente, ombro com visor de vidro, cúpula |
| `rocket_engine` | — | placa de montagem, câmara, garganta, sino em dois estágios, quatro hastes |
| `rocket_fuel_tank` | — | anéis de topo e base, tanque recuado, quatro colunas de canto |
| `rocket_parachute` | — | base, pacote, domo, tampa, cintas em cruz |
| `rocket_landing_leg` | — (simétrico) | placa de montagem, amortecedor, pistão, sapata, quatro escoras inclinadas (22,5°) |
| `launch_pad` | — | deck com faixa de alerta, base de concreto, quatro pilares |

`refinery_on` e `oxygen_compressor_on` são filhos do modelo apagado que trocam só a textura `#front`.

Toda a geometria fica dentro do bloco (0..16) e a forma de colisão continua o cubo cheio padrão; os blocos
usam `noOcclusion()` (em `ModBlocks`) para os vizinhos não terem faces cortadas atrás das partes recuadas.
O casco (`rocket_hull`) e o bloco de aço continuam cubos (`cube_all`): o casco forma a pele contínua do
foguete. As peças do foguete aparecem no retrato do `RocketRenderer`, que tessela os mesmos modelos de bloco.
