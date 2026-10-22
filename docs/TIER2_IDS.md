# Ids do conteúdo do Tier 2 (contrato entre código e arte)

Fixado em 22/10/2026 para o código e a arte andarem em paralelo. O código registra estes ids com texturas provisórias; a arte
(skill `minecraft-entity`, página de escolha) grava os arquivos finais com os mesmos nomes. Números e receitas ficam em
`docs/PLANO_TIERS.md` (seção do Tier 2). Mudança de id: atualize este arquivo e avise os dois lados.

## Materiais de Marte (existem como blocos/itens; o planeta Marte vem depois)

| Id | Tipo | Notas de arte |
|---|---|---|
| `mars_regolith` | bloco | solo/pedra marciana, ferrugem avermelhada |
| `mars_titanium_ore` | bloco | minério no regolito marciano, veios claros |
| `raw_titanium` | item | como `raw_iron`, recolor |
| `titanium_ingot` | item | como `iron_ingot`, recolor cinza-azulado claro |
| `titanium_plate` | item | mesma forma da placa de aço do mod |
| `titanium_block` | bloco | como bloco de metal |
| `rare_earth_dust` | item | pó (como `redstone`/`glowstone_dust`), verde-acinzentado |

## Combustíveis e energia

| Id | Tipo | Notas de arte |
|---|---|---|
| `helium3_canister` | item | galão (forma do galão de combustível do mod), He-3 da Lua |
| `hydrogen_canister` | item | galão, hidrogênio líquido (azul gelo) |
| `xenon_canister` | item | galão, xenônio (violeta) |
| `nuclear_fuel_rod` | item | barra de combustível nuclear (verde brilhante discreto) |
| `superconductor_coil` | item | bobina de fusão (cobre/azul) |
| `hydrogen_tank` | bloco multibloco | tanque criogênico; mesma regra e texturas conectadas do `rocket_fuel_tank` (`_frame`, `_frame_end`, moldura 4) |
| `xenon_tank` | bloco multibloco | tanque esférico/pressurizado; mesmas regras de tanque |
| `rtg_generator` | bloco | gerador radioisotópico (energia do motor iônico), aletas |

## Motores do Tier 2 (multibloco, mesmos tamanhos do motor químico)

Cada motor tem o bloco avulso (`<id>`) e os modelos da estrutura formada `<id>_2x2` (2x2x2, mestre no canto mínimo, modelo em
0..32) e `<id>_3x3` (3x3x3, mestre no centro, modelo em -16..32), como `rocket_engine`.

| Id | Tema | Notas de arte |
|---|---|---|
| `nuclear_engine` | nuclear térmico (NERVA), usa hidrogênio | núcleo blindado com símbolo de radiação discreto, bocal longo |
| `ion_engine` | iônico, usa xenônio e energia | grade de íons azul brilhante, bocal raso e largo |
| `fusion_engine` | fusão, usa hélio-3 | anel magnético, plasma violeta/branco |
