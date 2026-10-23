# Gerador de planetas por parâmetros (formato)

Etapa 2 dos tiers (`docs/PLANO_TIERS.md`, seções 5 e 8). Cada planeta é um arquivo `src/main/resources/data/spacehorizons/planet/<id>.json`,
lido do jar por `planet/spec/PlanetSpec` na primeira vez que `planet/Planets` é usado. O catálogo (`Planets.EARTH`, `MOON`, `MARS`) só
guarda os ids; tudo o mais sai do JSON. Parâmetro inválido (faltando, fora da faixa, incoerente) derruba o carregamento com a mensagem
`parâmetros inválidos do planeta <id>: <motivo>` (testes em `planet/spec/PlanetSpecTest`).

**Por que do jar e não do datapack**: cliente e servidor precisam dos mesmos números antes de qualquer mundo carregar (malha orbital,
céu, curvatura, gravidade e oxigênio são estáticos por planeta). Um datapack não troca um planeta; mudar um planeta é mudar o JSON e
recompilar. A pasta `data/spacehorizons/planet/` não é registro do jogo: o Minecraft a ignora.

## O que mais um planeta novo precisa (fora do JSON)

1. O id em `Planets` (uma linha `register("<id>")`).
2. O tipo de dimensão `data/spacehorizons/dimension_type/<id>.json` com o MESMO id (é por ele que `Planets.forLevel` acha o planeta):
   altura `-64..320`, cores de céu, neblina e nuvens iguais às do `atmosphere` (o `PlanetResourcesTest` confere).
3. A dimensão no world preset (`worldgen/world_preset/earth.json`): gerador `spacehorizons:planet` com o mesmo `planet`, e a fonte de
   biomas `spacehorizons:planet` mapeando cada papel da paleta ao mesmo `biome` do JSON do planeta (o teste confere). Mundos já
   criados não ganham dimensões novas: só mundos novos.
4. Os biomas novos em `worldgen/biome/` (o atributo `minecraft:visual/sky_color` do bioma vence o do tipo de dimensão).
5. Enquanto o planeta não estiver em `space/SolarSystem.BODIES` (física orbital), o céu dele vem de um corpo destacado
   (`SolarSystem.skyBodyOf`) que só gira com `day_length_ticks`; o acesso é por comando (`/spacehorizons goto mars [x z]`).

## Campos

Todas as frequências do relevo são na esfera unitária (a volta inteira é 2π): frequência f dá feições de ~R/f blocos, então o mesmo
relevo serve a planetas de tamanhos diferentes (as alturas não escalam com o raio).

| Campo | Significado |
|---|---|
| `seed` | Semente do relevo e dos veios (fixa por planeta, não depende da semente do mundo) |
| `circumference` | Volta em X, em blocos, múltiplo de 16 (raio = C / 2π) |
| `sea_level` | Referência de altitude (e nível do mar, se houver oceano) |
| `max_latitude` | Graus até a calota intransponível (`(0, 90)`) |
| `gravity` | Fração da gravidade da Terra (atributo GRAVITY e dano de queda) |
| `style` | `earth` (mundo do gerador vanilla, com oceano; só a Terra) ou `parametric` (relevo analítico abaixo) |
| `day_length_ticks` | Dia local de um planeta fora do SolarSystem (padrão 24.000); os do sistema seguem a órbita |
| `terrain.amplitude` | Escala vertical (blocos) de quase tudo |
| `terrain.relief` | Obrigatório em `parametric`, proibido em `earth` |
| `surface.subsurface` | Rocha abaixo do enchimento |
| `surface.types.<papel>` | `top` (bloco do topo), `filler` (3 camadas abaixo), `biome`, `color` opcional (cor de longe na malha orbital e na esfera vista do espaço; sem ela, a cor de mapa do bloco de topo) |
| `water` | `ocean` (por enquanto `true` só no estilo `earth`), `shallow_color` e `deep_color` (água de longe) |
| `atmosphere` | `{"present": false}` ou: `thickness` e `base_altitude` da curva de densidade, `breathable` (o traje precisa de oxigênio se `false`), `scatter` (espalhamento do limbo por canal r, g, b), `optical_depth` (1 = Terra), `sky_color`, `fog_color`, `clouds` |
| `ores` | Lista de veios (só `parametric`): `block`, `min_y`, `max_y`, `scale` (blocos), `thickness`, `gate_scale`, `gate_threshold`, `salt` |

**Blocos com reserva**: qualquer bloco aceita um id ou uma lista; vale o primeiro que existir no registro quando o mundo carrega
(`SurfacePalette.resolve`). Marte pede `spacehorizons:mars_regolith` e `spacehorizons:mars_titanium_ore` e cai em `minecraft:red_sand`,
`minecraft:terracotta` e `minecraft:iron_ore` enquanto o conteúdo do Tier 2 não existe: quando os blocos entrarem, Marte passa a usá-los
sem mudar o JSON (a malha orbital usa a `color` declarada, então convém revisar as cores depois da arte final).

**Papéis da superfície** (`SurfaceType`): `regolith` (planície comum), `mare` (planície baixa e escura), `highland`, `crater_floor`,
`volcanic` (só com vulcões) e o da calota (`polar.cap_type`, por exemplo `polar_ice` ou `highland`). A paleta precisa de todos os que o
relevo produz. A Terra usa os papéis de Terra (`ocean`, `beach`, `plains`, `forest`, `desert`, `mountain`, `snow`, `polar_ice`).

### `terrain.relief`

Altura = `sea_level + base_height + terras altas − planícies baixas + detalhe + crateras [+ montanhas] [+ vulcões] + parede polar`,
limitada a `[sea_level + min_above_sea, max_height]` (`max_height` ≤ 315).

| Bloco | Campos |
|---|---|
| `lowlands` | fBm de `frequency`/`octaves`; máscara = smoothstep(`edge0`, `edge1`, −ruído); afunda `strength`·amplitude |
| `highlands` | Igual, com +ruído e fora das planícies baixas; sobe `strength`·amplitude |
| `detail` | fBm fina de `frequency`/`octaves`, `roughness`·amplitude, `lowland_smoothing` (0..1) a menos nas planícies, `highland_roughening` a mais nas terras altas |
| `polar` | Platô de `wall_height`·amplitude a partir de `wall_start` (fração da latitude máxima); de `cap_start` em diante a superfície é `cap_type` |
| `craters` | `scales`: lista de `{frequency, probability, depth, rim}` (até 6); raio em células `[radius_min, radius_min + radius_span]`; influência até `influence` raios (`influence·(radius_min+radius_span) ≤ 1`); borda gaussiana de `rim_width`; só crateras de raio ≥ `floor_min_radius` blocos pintam o fundo; soma limitada a `[-max_depth, +max_rise]` |
| `mountains` (opcional) | Ridged (1−\|ruído\|)² de `frequency`, sob a máscara smoothstep(`mask_edge0`, `mask_edge1`) de fBm de `mask_frequency`; `strength`·amplitude; acima de `highland_above`·amplitude vira `highland` |
| `volcanoes` (opcional) | Grade de `frequency`, chance `probability` por célula, raio `[radius_min, radius_min + radius_span]` células (≤ 0,5), `height` blocos, caldeira de `caldera_radius` do raio e `caldera_depth` da altura; até `type_radius` do raio é `volcanic` |

## Estruturas da história

O gerador paramétrico (`PlanetChunkGenerator`) não tem estruturas do vanilla. Os lugares da trama da Lua (base e sonda) são desenhados por ele mesmo, em posições fixas, depois da decoração de cada chunk (`StoryWorldgen.stampMoon`); só a Lua tem lugares hoje. A Terra usa a estrutura do vanilla `spacehorizons:story_site`. Detalhes em `docs/PLANO_SOBREVIVENCIA.md`, "Etapa 5: a trama".

## A Lua e a Terra

- **Lua** (`moon.json`): os números são as constantes do antigo `MoonTerrain`; o `ParametricTerrain` com eles dá exatamente o mesmo relevo
  (soma de controle de altura e tipo em 1,3 milhão de colunas, `MoonTerrainTest.matchesTheOriginalMoonTerrainExactly`). Mundos lunares já
  gerados continuam casando com os chunks novos.
- **Terra** (`earth.json`): o mundo continua vindo do gerador vanilla; o JSON dá a definição (mesmos números), a paleta de reserva da malha
  orbital (antes do mapa do planeta), a água e a atmosfera (mesmos valores de antes).

## Marte (`mars.json`)

| Grandeza | Valor | Por quê |
|---|---|---|
| Circunferência | 348.160 (R ≈ 55.412) | 0,531 da Terra do mod, a mesma razão da real (0,532); entre a Lua (0,25) e a Terra |
| Gravidade | 0,38 | Real |
| Dia | 24.660 ticks | O sol marciano (24 h 39 min) na escala do dia do jogo |
| Latitude máxima | 75° | Como a Lua |
| Relevo | amplitude 64; planícies baixas largas (frequência 0,9), terras altas (1,5), crateras em 4 escalas menos densas e mais rasas que as lunares, cordilheiras, vulcões em escudo de 110 blocos com caldeira, calotas de gelo a partir de 86% da latitude | Paisagem marciana em escala de jogo |
| Paleta | regolito: `mars_regolith` → `red_sand`; planície escura: terracota marrom; terra alta: terracota; fundo de cratera: terracota laranja; vulcão: terracota vermelha; calota: neve sobre gelo compactado; subsolo: `mars_regolith` → terracota | Ids trocáveis (reserva) |
| Atmosfera | presente, sem oxigênio, espessura 1.100, base 90, limbo (1,0; 0,60; 0,34) com profundidade 0,45, céu `#c89a6c` (caramelo), neblina `#d8ad82`, sem nuvens; poeira (`crimson_spore`) no bioma | Fina e avermelhada; o traje trata como vácuo |
| Minério | veio de `mars_titanium_ore` → `iron_ore`, y −50..110, tubos de ~2 blocos (0,66% dos blocos da faixa) | Base do motor do Tier 2 |

Marte ainda NÃO está na física orbital (etapa 3: sistema centrado no sol): sem zona de espaço, sem travessia, sem pouso de foguete; o céu
tem o sol (corpo destacado) e as estrelas, sem Terra nem Lua.
