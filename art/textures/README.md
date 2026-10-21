# Texturas do SpaceHorizons (fontes)

Todas as texturas de bloco e de item do mod (16x16) foram refeitas com a skill `minecraft-entity`
(modo rápido de textura): os prompts padrão do usuário (estilo "Vanilla Moderno", templates da skill)
são preenchidos com os campos de cada textura, o Gemini gera 3 variantes (máximo, para poupar a cota) e o resultado é convertido em
pixel art 16x16 de verdade, com no máximo 7 cores. A variante de cada textura é escolhida pelo usuário na
página de escolha (`escolha.py`), não pelo agente.

## Arquivos

| Arquivo | O que é |
|---|---|
| `jobs.json` | Os campos de prompt de cada textura (`nome`, `descricao`, `detalhes`, `cores`, `orientacao`, `ref`) ou o recolor (`base`, `cor`). É o registro dos prompts usados. |
| `run_jobs.py` | Roda todos os jobs (ou só os citados) em paralelo com o `texture.py` da skill (4 variantes; `--variants N`). Saída de trabalho em `art/_work/` (fora do git). Recolor com `cor_alt` roda duas vezes (6 variantes). |
| `escolha.py` | Monta `art/_work/escolha/index.html`: cada textura com as variantes (letras) e a atual, ampliadas, em mosaico 3x3 e, nas texturas dos modelos de `art/models/`, o render do modelo com a variante. Gera também `escolhas_modelo.json` (tudo "atual"). |
| `reduce16.py` | Redução alternativa do bruto do Gemini: média de área (BOX) + quantização a 7 cores. Preserva melhor a estrutura de blocos (painéis, juntas) que a redução por voto quando o gerador desenha 40 a 80 células. |
| `candidates.py` | Monta a folha de candidatas de cada textura: 1-3 = variantes do `texture.py` (voto), 4-6 = os mesmos brutos pelo `reduce16.py`, em mosaico 2x2 para ver a emenda. |
| `picks.json` | Formato antigo (números 1-6, referentes a brutos que não existem mais). |
| `apply_picks.py` | Copia as escolhidas (letras do `escolhas_modelo.json`; "atual" mantém) para `src/main/resources/assets/spacehorizons/textures/` e deriva as frentes acesas quando a frente muda. |

## Refazer

```bash
# GEMINI_API_KEY no ambiente (variável de usuário); modelo padrão da skill: gemini-3.1-flash-image
python art/textures/run_jobs.py art/textures/jobs.json            # todos (≈30 s cada, 6 em paralelo)
python art/textures/run_jobs.py art/textures/jobs.json refinery   # só um
python art/textures/escolha.py                                     # página art/_work/escolha/index.html
# troque "atual" pela letra escolhida em art/_work/escolha/escolhas_modelo.json e aplique
python art/textures/apply_picks.py art/_work/escolha/escolhas_modelo.json
```

## Paleta comum

Cinza-aço azulado, cinza-claro, branco-amarelado nos brilhos, azul-ardósia e azul-marinho nas sombras
(hue shifting); acentos: laranja de segurança, amarelo de alerta, ciano de oxigênio e cobre.

## Variantes derivadas (sem gerador)

- `refinery_on.png`: a frente apagada escolhida com o vão da fornalha pintado num degradê de fogo.
- `oxygen_compressor_on.png`: a frente apagada com a grade da ventoinha e os tons ciano clareados.

Assim as frentes acesa e apagada ficam idênticas fora da parte que acende.

## Recolor (forma vanilla, material novo)

`steel_ingot` (iron_ingot), `steel_nugget` (iron_nugget) e `steel_blend` (gunpowder), recoloridos para
o aço azulado `#7F93A8` / `#6E7684` (variante de hue shifting escolhida em `picks.json`).

## Texturas novas de material (usadas pelos modelos das máquinas)

`machine_casing` (carcaça de aço grafite), `hazard_stripes` (faixa de alerta), `cockpit_glass`
(vidro da cabine), `copper_tank` (coluna de destilação de cobre) e `oxygen_cylinder` (cilindro ciano).

## Armadura vestida

`space_suit_humanoid` e `space_suit_humanoid_leggings` (`textures/entity/equipment/{humanoid,humanoid_leggings}/space_suit.png`,
64x32) são recolor das camadas de ferro vanilla (mesmo layout), em branco e cinza-azulado (seção "equipment"
da escolha). A camada atual tem a viseira desenhada; o recolor do ferro não tem.
