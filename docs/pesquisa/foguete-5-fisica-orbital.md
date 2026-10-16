# Relatório técnico: física KSP-lite do SpaceHorizons (3a, 3b, 3c)

Frente: física pura e testável, sem Minecraft.

Convenção de marcas:
- **[C]** = confirmado no código lido ou em execução contra cópias das classes reais.
- **[S]** = simulação nos meus protótipos, fora do projeto.
- **[P]** = proposta ou inferência.

Nada do projeto foi alterado. Os protótipos estão em `C:\Users\nicol\AppData\Local\Temp\claude\E--git-Personal-Games-MineMods-SpaceHorizons\a4e627e3-157c-4438-91cf-69e4d19a2311\scratchpad\`:
- `proto\Proto.java`: elementos orbitais, Kepler, Verlet, troca de esfera de influência (SOI), mapeamento para a dimensão.
- `proto\Trip2.java`: viagem Terra→Lua com a Lua real.
- `proto\Burn.java`: queima finita.
- `p1.py` a `p10.py`: números, integradores, subida, reentrada e dimensionamento.

Para rodar: `javac -d out $(find stub -name "*.java")` (cópias dos fontes reais com `Identifier` simulado), depois `java -cp out Proto.java`.

## 0. Resumo: o que mais importa

1. **μ coerente com a gravidade sentida [C+P].** `μ = G0·surfaceGravity·R²` com G0 = 0,08 b/t². O resultado é μ_Terra = 8,7034·10⁶ e μ_Lua = 8,9754·10⁴ (b³/t²). A razão é exatamente 0,0103125, porque R_Lua/R_Terra = 1/4 e g_Lua/g_Terra = 0,165.
2. **O `SpaceFrame` não é inercial [C].** É o referencial preso à Terra: a Terra não gira nele, e o sol dá uma volta por dia.
   - Não existe bônus de lançamento para leste.
   - A Lua orbita para **oeste** neste referencial, com período 27.428,6 ticks. Ir à Lua exige lançar para oeste.
   - A física pode tratá-lo como inercial (corpo central parado).
3. **A Lua tem velocidade angular não uniforme [C].** O ângulo da Lua usa a curva de Bézier do sol.
   - Velocidade da Lua: 8,49 a 16,72 b/t (média 13,74).
   - O custo de captura lunar oscila de **1,3 a 5,9 b/t**, conforme a hora do dia da chegada (simulado com a pose real).
   - Isso vira "janela de transferência".
4. **N-corpos direto no referencial da Terra é inviável [S].** A Lua anda "nos trilhos" de forma não kepleriana. A aceleração residual sobre o foguete na Lua fica entre 7% e 17% da gravidade lunar, e um foguete pousado deslizaria. Use **patched conics**.
5. **Integrador: velocity-Verlet (kick-drift-kick), dt = 1 tick [S].**
   - Erro de fase de ~1 bloco em 10 órbitas baixas.
   - Erro de energia ~1e-13.
   - Arrasto implícito separado do passo.
6. **Eixos de mão invertida [C, confirmado rodando o protótipo].** No referencial fixo no corpo, leste=+Z, norte=+Y, cima=+X em lon 0. Isso é um sistema de mão esquerda.
   - Uma órbita equatorial para leste tem h = −Y, ou seja, inclinação 180° contra +Y.
   - O painel precisa usar o polo P = −Norte.
7. **O relógio do Overworld tem taxa, pausa e saltos [C].**
   - `rate` (NeoForge `setRate`, `/neoforge day`).
   - `ADVANCE_TIME` e `/time pause`.
   - `addTicks`, `setTotalTicks`, `moveToTimeMarker` (dormir).
   - Tudo isso desloca ou congela a Lua.
8. **Isp é a alavanca mestra do combustível [S].** Foguete de um estágio, ida e volta à Lua, com Isp em ticks:

| Isp (ticks) | Galões para a ida e volta | Carvão |
|---|---|---|
| 450 | 172 | 688 |
| 500 | 84 | 336 (5,2 pilhas) |
| 560 | 48 | 192 |

   - Com 380 ticks a viagem é inviável.
   - Recomendo 500 ticks (ve = 40 b/t) para o motor básico.

## 1. Base confirmada no código

| Item | Valor | Fonte |
|---|---|---|
| Terra | circunferência 65.536, R = 10.430,378, seaLevel 63, `surfaceGravity` 1,0 | `Planets.EARTH`, `PlanetDefinition.radius()` |
| Lua | circunferência 16.384, R = 2.607,595, `surfaceGravity` 0,165 | `Planets.MOON` |
| Distância Terra–Lua | `EARTH_MOON_DISTANCE` = 60.000 | `SolarSystem` |
| Inclinação da órbita lunar | 5° | `SolarSystem.ORBIT_INCLINATION` |
| Gravidade do jogador | `Attributes.GRAVITY` base 0,08, faixa −1..1, sincronizável | fontes do MC |
| Gravidade por planeta | modificador `ADD_MULTIPLIED_TOTAL` com `amount = fator − 1` | `PlanetGravity` |
| Física vanilla | `LivingEntity.travelInAir`: `y' = (y − gravidadeEfetiva)·0,98`; `Entity.getDefaultGravity()` = 0,0 | fontes do MC |
| Velocidade terminal do jogador | 49·g = 3,92 b/t na Terra | derivado de `travelInAir` |
| Conversões | 1 b/t = 20 b/s; 0,08 b/t² = 32 b/s² | trivial |
| Atmosfera da Terra | `AtmosphereModel.of(EARTH)`: espessura 1.800, base 160 | `AtmosphereModel` |
| Perfil de densidade | `density(h) = (1−d)²`, `d = clamp((h−160)/1640)`, 0 a partir de 1.800 | idem |
| Corpo dominante (voo criativo) | menor distância÷raio, histerese 10% | `SphereOfInfluence` |
| Conversão de velocidade | `BodyTransfer` soma e subtrai `SpaceFrame.groundVelocity` | `BodyTransfer` |
| Velocidade da Lua | `Body.velocityAt` por diferença central, h = 0,5 | `SolarSystem.Body` |
| Dimensões | `height` 384, `min_y` −64 (Terra e Lua) | `dimension_type/*.json` |
| Refinaria | carvão e carvão vegetal: 250 mB / 100 ticks; blaze powder: 500 mB; cana e alga seca: 50 mB / 40 ticks | `recipe/refining/*.json` |
| Galão | 1.000 mB (`CanisterFuel.CAPACITY`) | `CanisterFuel` |
| Tanque da refinaria | 4.000 mB (`RefineryLogic.TANK_CAPACITY`) | `RefineryLogic` |

Relógio do mundo (`ServerClockManager`, `ClockInstance`):
- `tick` soma `rate` ao `partialTick` e propaga a parte inteira para `totalTicks`.
- Só avança se `!paused` e `ADVANCE_TIME`, a menos que o relógio tenha as tags `IGNORES_PAUSE_COMMAND` ou `IGNORES_ADVANCE_TIME_RULE`.
- `SpaceClock.ticks` devolve `totalTicks + partialTick`.

## 2. Números (item 1)

### 2.1 Constantes e μ [C]

| Grandeza | Terra | Lua |
|---|---|---|
| Raio R (blocos) | 10.430,38 | 2.607,59 |
| g superfície | 0,08 b/t² (32 b/s²) | 0,0132 b/t² (5,28 b/s²) |
| μ = g·R² | 8.703.423,4 | 89.754,05 |
| v circular na superfície | 28,887 b/t (578 b/s) | 5,867 b/t |
| v de escape na superfície | 40,852 b/t | 8,297 b/t |
| Período rasante | 2.269 ticks (1,89 min) | 2.793 ticks (2,33 min) |

### 2.2 Órbitas circulares [C]

Fórmulas: `v_c = √(μ/r)`, `v_esc = √2·v_c`, `T = 2π·√(r³/μ)`. O tempo está em minutos de jogo a 20 TPS.

| Órbita (altitude sobre o nível do mar) | v_c (b/t) | v_esc (b/t) | Período (ticks / min) |
|---|---|---|---|
| Terra 1.800 (topo da atmosfera) | 26,676 | 37,73 | 2.881 / 2,40 |
| Terra 2.500 | 25,944 | 36,69 | 3.131 / 2,61 |
| Terra 5.000 (zona de espaço) | 23,750 | 33,59 | 4.082 / 3,40 |
| Terra 6.000 | 23,016 | 32,55 | 4.485 / 3,74 |
| Lua 300 | 5,556 | 7,857 | 3.288 / 2,74 |
| Lua 500 (LLO de referência) | 5,374 | 7,600 | 3.633 / 3,03 |
| Lua 1.000 | 4,988 | 7,054 | 4.544 / 3,79 |

- A escala de tempo própria é `√(R/g)` = 361 ticks, cerca de 18 s. Todas as queimas e subidas duram dezenas de segundos reais. Isso é inerente à gravidade do Minecraft num planeta de R = 10,4k, não um defeito.
- A Lua tem rotação sincronizada [C].
  - Período médio no referencial da Terra: 27.428,6 ticks (1,1429 dia).
  - Velocidade angular média 2,2907e-4 rad/tick, variando de 1,416e-4 a 2,787e-4 (mínimo em tick ≡ 6000 mod 24000, máximo em ≡ 18000).
  - Uma Lua kepleriana com este μ teria ω = 2,007e-4 e período 31.301. Ela é 14% mais rápida que o kepleriano; não importa porque está nos trilhos.

### 2.3 Esfera de influência [C+S]

- **Laplace (proposta para a dinâmica): 9.627,1 blocos.** É `a·(μ_L/μ_T)^0,4`. O raio de Hill é 9.055,2.
- Regra existente (razão dist÷raio): fronteira a 12.000 do lado da Terra e 20.000 do lado oposto. É uma esfera de Apolônio de raio 16.000, centrada 4.000 atrás da Lua.
  - Em 12.000 da Lua a maré da Terra (9,67e-4 b/t²) supera a atração da Lua (6,23e-4).
  - Em 9.627 a Lua vence (9,68e-4 contra 7,76e-4).
- **Recomendação:** dinâmica com Laplace + histerese de 5% (entra a 9.146, sai a 10.108). Mantenha `SphereOfInfluence` como está para o autopiloto criativo; `FlightPlanTest` depende dele.

### 2.4 Transferência de Hohmann até a Lua [C+S]

Fórmulas: `a_t = (r1+r2)/2`, `Δv1 = √(μ(2/r1 − 1/a_t)) − √(μ/r1)`, `t = π·√(a_t³/μ)`, ângulo de avanço `φ = π − ω̄·t`. Como a Lua se move, o alvo é o raio da órbita lunar (60.000).

| Saída | Δv1 (b/t) | Meio período | Vel. no apoastro | Avanço médio da Lua |
|---|---|---|---|---|
| LEO 2.500 | 7,335 | 7.415 ticks (6,18 min) | 7,172 | 82,7° |
| LEO 6.000 | 5,823 (e = 0,570, a_t = 38.215) | 7.955 ticks (6,63 min) | 7,897 | 75,6° |

- Sensibilidade (LEO 2.500): 1 b/t de erro no Δv1 move o apoastro 20.338 blocos e o tempo 3.102 ticks. Um erro de 0,1 b/t desloca o apoastro 2.034 blocos, aceitável frente à esfera de influência de 9.627. Mas é preciso uma correção de meio de curso (MCC).
- Plano de órbita: a Lua está inclinada 5° (5.229 blocos fora do plano equatorial no máximo). O custo de plano em separado seria 2·7,9·sin(2,5°) = 0,69 b/t no apoastro. Isso se dilui lançando já no plano lunar ou na MCC.
- **Simulação com a Lua real [S]:**
  - Verlet de 1 tick, SOI de Laplace, saída de LEO 6.000 com Δv de Hohmann.
  - Entrada na SOI após **8.600 a 8.900 ticks (7,2 a 7,4 min)**, depois do apoastro, porque a Lua alcança o foguete por trás.
  - O avanço necessário varia de 40° a 87° conforme a hora do dia (a velocidade da Lua varia ±33%).

Δv de captura a uma LLO circular de 500, em função da **hora do dia (tick mod 24000) da chegada à SOI** [S]:

| Hora da chegada | v_inf (b/t) | Δv captura (b/t) |
|---|---|---|
| 5.636 | ligado (energia < 0) | 1,27 |
| 7.118 | ligado | 1,46 |
| 8.643 | ligado | 2,10 |
| 10.208 | 3,41 | 2,96 |
| 11.778 | 5,25 | 3,86 |
| 13.338 | 6,57 | 4,68 |
| 14.881 | 7,51 | 5,31 |
| 17.916 | 8,27 | 5,86 |
| 20.886 | 7,62 | 5,39 |
| 23.796 | 5,65 | 4,10 |
| 1.234 | 4,14 | 3,28 |
| 2.669 | 1,90 | 2,46 |

- Média 3,8, mínimo 1,27 (chegada perto do meio-dia, Lua lenta), máximo 5,86 (perto da meia-noite, Lua rápida).
- A estimativa analítica com a velocidade média da Lua dá 4,68.
- Janela geométrica: abre a cada 27.429 ticks (22,9 min). O custo repete a cada 7 janelas = 8 dias de jogo (2,7 h reais).

### 2.5 Delta-v por fase

Todos os valores em b/t. A guiagem das simulações é simples (programa de arfagem, subida e circularização no apoastro), com precisão estimada de ±5%.

| Fase | Ideal | Simulado | Planejar |
|---|---|---|---|
| Terra→órbita de 2.500 | 31,6 de energia pura | 43,5 (TWR 3), 44,6 (2), 46,5 (1,5), 48,3 (1,3); sem arrasto: 39,6 | 46 |
| Terra→órbita de ~5.000 a 6.000 | 33,2 de energia pura | 46,9 (3), 47,6 (2), 49,7 (1,5), 51,1 (1,3) | 48 |
| Salto suborbital (apoastro 5.000 a 6.000) | 23,3 vertical | 32 a 37 | 35 |
| TLI | 5,82 (LEO 6.000) ou 7,34 (LEO 2.500) | = ideal | 5,9 |
| Captura em LLO 500 | 4,68 (analítico, v_Lua média) | 1,3 a 5,9 | 4,0 (pior caso 5,9) |
| Descida e pouso | 6,36 | 7,05 (TWR_Lua 24), 7,21 (3), 7,46 (2), 7,90 (1,5) | 7,2 |
| Decolagem Lua→LLO 500 | 6,32 | 7,63 (TWR_Lua 2) a 8,24 (12) | 7,9 |
| Retorno à Terra (TEI) | simétrico à captura | 1,3 a 5,9 | 4,0 (pior caso 5,9) |
| Correções | | | 1,0 |

Totais (LEO 6.000):

| Missão | Nominal | Pior janela |
|---|---|---|
| Órbita | 48 | |
| Lua só ida (pouso) | 66 | 68 |
| Ida e volta | 78 | 82 |

- A escolha de LEO (2.500 ou 6.000) quase não muda o total, porque troca Δv de subida por Δv de TLI.
- A subida dura 18 a 28 s (órbita de 2.500) ou 36 a 58 s (órbita de 6.000). A sequência completa da missão, com esperas de janela, leva 40 a 60 min reais.

## 3. Integrador e troca de corpo (item 2)

### 3.1 Comparação de integradores [S]

Órbita circular de 2.500, 10 períodos, dt = 1:

| Integrador | Resultado |
|---|---|
| Euler explícito | energia +17%, espirala para fora; inutilizável |
| Euler semi-implícito | ΔE/E 4e-12, mas o raio oscila 12.917 a 12.943 (±13 blocos); o apoastro e o periastro do painel tremeriam |
| Velocity-Verlet (KDK) | raio constante, erro de fase 1,09 blocos, ΔE/E 5e-14 |
| RK4 | erro nulo, 4 avaliações por passo |

- Elipse de transferência (e = 0,645), 10 períodos (148.000 ticks): Verlet 16 blocos; semi-implícito 37.
- Na LLO 500: Verlet 0,195 bloco.
- Hipérbole lunar: Verlet contra Kepler difere 0,009 bloco após 3.000 ticks.
- Uma elipse inteira de transferência (15.911 ticks): erro de 0,57 bloco e ΔE/E = 2,8e-13 [C, protótipo Java].

**Escolha [P]: velocity-Verlet com dt = 1 tick.**
- Empuxo como aceleração constante no tick, somada nos dois meios chutes.
- Arrasto como divisão (splitting) implícita: `v ← v / (1 + k·ρ·|v|·dt)`. É estável mesmo com o paraquedas (k·ρ·|v|·dt chega perto de 1).
- Substeps (até 8) só se `|v|·dt/r > 0,02` ou perto da superfície.
- RK4 só para visualização de trajetória, e ainda assim a previsão deve ser **analítica (Kepler)**, nunca numérica.

### 3.2 Queima finita [S, Burn.java]

TLI de 5,8234 b/t com `Δv` exato por tick (Tsiolkovsky) e empuxo seguindo o prograde instantâneo.

| Aceleração (b/t²) | TWR em órbita | Duração | Perda no apoastro |
|---|---|---|---|
| 0,30 | 9,3 | 18 ticks | −1 bloco |
| 0,10 | 3,1 | 54 ticks | −12 |
| 0,05 | 1,55 | 108 ticks | −49 |
| 0,02 | 0,62 | 271 ticks | −301 |
| 0,01 | 0,31 | 542 ticks | −1.154 |

- A perda só importa com TWR em órbita abaixo de 0,6.
- O "queime agora" do painel deve acender `burnTime/2` antes do nó, com `burnTime = (m0/ṁ)·(1 − e^(−Δv/ve))`.
- Para a TLI, limite o acelerador a ~25%: queima de 7 a 9 s em vez de 1,5 s, o que facilita o ajuste fino.

### 3.3 Patched conics e a Lua em movimento [C+P]

Estado sempre **relativo ao corpo dominante, em eixos inerciais não girantes paralelos aos do espaço**. A Lua é só translação e rotação cinemática (`poseAt`), nunca integrada.

Troca de corpo (código validado contra as classes reais; ida e volta com erro exato de 0):

```java
record Rel(SolarSystem.Body body, V3 r, V3 v) {}
static Rel switchSoi(Rel s, double clock) {
    BodyPhysics moon = BodyPhysics.of(SolarSystem.MOON);
    if (s.body() == SolarSystem.EARTH) {
        V3 rm = s.r().minus(pos(SolarSystem.MOON, clock));          // Body.positionAt
        if (rm.length() < moon.soiRadius() * (1 - HYST))
            return new Rel(SolarSystem.MOON, rm, s.v().minus(vel(SolarSystem.MOON, clock))); // Body.velocityAt
    } else if (s.r().length() > moon.soiRadius() * (1 + HYST)) {
        return new Rel(SolarSystem.EARTH, s.r().plus(pos(SolarSystem.MOON, clock)),
                s.v().plus(vel(SolarSystem.MOON, clock)));
    }
    return s;
}
```

Por que não N-corpos [S]:
- Aceleração cinemática do centro da Lua: 1,46e-3 a 4,66e-3 b/t².
- Atração da Terra na Lua: 2,418e-3.
- Resíduo na Lua: 0,95e-3 a 2,24e-3, ou seja, 7% a 17% de g_Lua. Um foguete parado no chão lunar deslizaria. Patched conics descarta a Terra dentro da SOI da Lua e cancela o problema.

Cuidados com o relógio [C]:
- A velocidade devolvida por `Body.velocityAt` é em blocos por tick de **relógio**. Multiplique por `dRelogio` por tick de jogo (`rate`, ou 0 se pausado) ao converter para velocidade por tick de jogo.
- Como o estado é relativo ao corpo, só três momentos dependem da Lua: troca de SOI, mapeamento para a dimensão, previsões.
- Detecte salto: `|Δrelógio − rate| > 1e-3`. Nesse caso reancore o estado pelo lado da dimensão ou aborte.

## 4. Elementos orbitais para o painel (item 3)

Dado `r`, `v` relativos ao corpo e `μ` (validado [C] no protótipo):

```
h  = r × v                      e⃗ = (v × h)/μ − r/|r|          e = |e⃗|
ε  = v²/2 − μ/r                 a = −μ/(2ε)                      (ε<0 ligada)
p  = |h|²/μ                     rp = p/(1+e)    ra = p/(1−e) (e<1; senão ∞)
T  = 2π·√(a³/μ)                 ν = ±acos(e⃗·r̂/e),  sinal pela r·v
i  = acos(h·P/|h|)              P = polo de referência (ver abaixo)
```

- Altitudes do painel: `rp − R` e `ra − R`. Para a Terra, periastro acima de 1.800 significa "estável, sem arrasto".
- Velocidade vertical: `v·r̂`. Velocidade horizontal ao chão: `|v − ω×r|`, com `ω×r = groundVelocity − velocityAt` [C].
- **Polo P = −Norte do corpo** (coluna 1 da `pose.rotation`, negada).
  - Isso corrige a mão invertida: órbita para leste dá i ≈ 0.
  - A órbita da Lua vista da Terra dá i = 180° − 5° = 175°.
  - Rotular "sentido LESTE/OESTE" (sinal de h·P) em vez de prógrado/retrógrado.
  - O planejador de transferência deve mandar lançar para o **OESTE**. Se lançar para leste, o v_inf de captura passa de 20 b/t e o Δv de captura vai a ~17 b/t.

Tempo até uma anomalia verdadeira ν_alvo (para "tempo até apoastro/periastro" e "queime em T−x") [C]:
- Elipse: `M = E − e·sinE` com `E = 2·atan2(√(1−e)·sin(ν/2), √(1+e)·cos(ν/2))`, `Δt = ((M_alvo − M) mod 2π)/n`, `n = √(μ/a³)`.
- Hipérbole: `M = e·sinhF − F`, `F = 2·atanh(√((e−1)/(e+1))·tan(ν/2))`, `n = √(μ/(−a)³)`. `Math.atanh` não existe no Java; use `0,5·ln((1+x)/(1−x))`.
- Raio→ν: `cosν = (p/r − 1)/e`. Serve para "entrada na SOI" e "impacto em X ticks" (r = R).

Verificações: no protótipo, a transferência a LEO 6.000 dá e = 0,57006, rp = 16.430,38, ra = 60.000, tempo até o apoastro 7.955,34 ticks, entrada na SOI em 4.279 ticks (sem a Lua se mover). Casos degenerados: e < 1e-9 (circular, ν = 0); órbitas parabólicas |ε| < 1e-12 precisam de guarda.

## 5. Combustível, massa e economia de jogo (item 4)

### 5.1 Equações

```
ve   = Isp_ticks · G0                    (G0 = 0,08 fixo; NÃO a gravidade do corpo)
ṁ    = F·aceleração / ve                (t/tick)
Δv   = ve · ln(m0/m1)                   m1 = m0·exp(−Δv/ve)
tqueima = (m0/ṁ)·(1 − exp(−Δv/ve))
TWR  = F / (m·g_local)
```

- Por tick: `m1 = m0 − ṁ`, `Δv_tick = ve·ln(m0/m1)`, aceleração média `Δv_tick/dt`. É exato e coerente com o impulso, e o último tick de uma queima é parcial.
- Se faltar combustível, o empuxo cai de forma proporcional. Guarde `fuelMb` como double; sincronize int.
- Combustível em massa: densidade **0,8 t por 1.000 mB** [P] (o galão passa a pesar 0,8 t).

### 5.2 Parâmetros propostos [P]

| Item | Valor |
|---|---|
| Isp motor básico | 500 ticks (ve = 40 b/t = 800 b/s) |
| Motor | empuxo 2,5 (t·b/t²), massa 0,6 t |
| Tanque | 4.000 mB (3,2 t de combustível), massa seca 0,2 t (fração estrutural 6%) |
| Cockpit | 0,8 t |
| Bloco estrutural padrão | 0,1 t |

- A fração estrutural do tanque limita a razão de massa máxima do foguete a ~17. Com 11% (tanque de 0,4 t) o foguete de ida e volta fica irreal.
- Aceleração máxima do foguete: sobe a 4,3 a 8,8 g no fim da queima (massa cai ~3 vezes). Sem dano no vanilla, mas convém um limitador de aceleração no controle automático.

### 5.3 Dimensionamento e economia [S]

Isp 500, TWR_0 ≈ 2, estrutura-base de 3,8 t:

| Missão | Δv | Razão | Tanques + motores | m seca / combustível / m0 (t) | Galões | Carvão | Refino em 1 refinaria |
|---|---|---|---|---|---|---|---|
| Salto suborbital | 35 | 2,40 | 3 + 1 | 5,0 / 9,6 / 14,6 | 12 | 48 | 4 min |
| Órbita (~6.000) | 48 | 3,32 | 5 + 2 | 6,0 / 16 / 22 | 20 | 80 | 7 min |
| Lua só ida | 66 | 5,21 | 10 + 3 | 7,6 / 32 / 39,6 | 40 | 160 | 13 min |
| Lua só ida (pior janela) | 68 | 5,47 | 11 + 3 | 7,8 / 35,2 / 43 | 44 | 176 | 15 min |
| Lua ida e volta | 78 | 7,03 | 21 + 5 | 11,0 / 67,2 / 78,2 | 84 | 336 (5,2 pilhas) | 28 min |
| Lua ida e volta (pior janela) | 82 | 7,77 | 28 + 6 | 13,0 / 89,6 / 102,6 | 112 | 448 (7 pilhas) | 37 min |

- **Foguete mínimo:** cockpit + 1 motor + 1 tanque + estrutura = 1,9 t seco, 5,1 t com os 4 galões da refinaria. Δv = 39,5, o suficiente para o suborbital (≥35). Marco natural: "encher o tanque da refinaria dá um voo ao espaço".
- 1 galão = 4 carvões = 400 ticks (20 s). O tanque da refinaria (4 galões) = 1.600 ticks (80 s). Uma refinaria produz 2,5 mB/tick = 180.000 mB/h reais.
- O combustível não é o gargalo de "umas horas de jogo". O gargalo é mineração de carvão (336 unidades), aço e circuitos. Ajuste fino: `FUEL_T_PER_MB` e Isp.
- **Sensibilidade ao Isp, ida e volta (nominal e pior janela):**

| Isp | Galões (nominal) | Galões (pior janela) |
|---|---|---|
| 450 | 172 | 368 |
| 500 | 84 | 112 |
| 560 | 48 | 64 |

- **Vazão:** com 5 motores (F = 12,5) e ve = 40, o consumo é 0,3125 t/tick = 390 mB/tick = 7.812 mB/s em aceleração máxima. A subida e a TLI consomem quase todo o combustível em segundos.
- **Pouso:** na Lua o TWR do foguete de ida e volta chega a ~52 (12,5/(0,0132·18,1 t)). O hover exige ~1,9% de empuxo. O acelerador precisa ser contínuo de 0 a 100% (sem mínimo) e de preferência com resposta quadrática, ou permitir desligar motores individualmente.

## 6. Atmosfera da Terra e reentrada (item 5)

### 6.1 Arrasto

```
a_drag = −k0 · ρ(h) · |v| · v          ρ = AtmosphereModel.of(planet).density(h),  h = r − R
k0     = G0 / vt²                      vt = velocidade terminal ao nível do mar (ρ=1)
```

Perfil de ρ [C]:

| Altitude | ρ |
|---|---|
| 0 a 160 | 1,0 |
| 500 | 0,629 |
| 800 | 0,372 |
| 1.000 | 0,238 |
| 1.200 | 0,134 |
| 1.500 | 0,034 |
| 1.700 | 0,0037 |
| ≥1.800 | 0 |

Valores de vt propostos [P]:

| Configuração | vt | k0 |
|---|---|---|
| Foguete em subida | 16 b/t (4,1× o jogador, que tem 3,92) | 3,1e-4 |
| Corpo em queda (sem cápsula) | 10 b/t | 8,0e-4 |
| Cápsula de reentrada | 5 b/t | 3,2e-3 |
| Paraquedas | 0,5 b/t | 0,32 |

- Geometria: `k0 = κ·A_frontal/m`. Com `κ = 7,6e-4 t/bloco³`, um foguete de 9 blocos² de seção e 22 t tem vt = 16.
- Perdas na subida [S]:

| vt | Perda por arrasto (b/t) |
|---|---|
| 16 | 2,2 a 3,0 |
| 8 | 6,2 a 7,4 (+4 no Δv total) |

- A Terra não gira no `SpaceFrame`, então a velocidade relativa ao ar é `v`. Não existe vento nem sustentação nesta versão.
- O perfil de densidade é visual: o shader `limb.fsh` replica a mesma curva. Mudar a curva visual muda a física. Considere um `DragProfile` próprio se quiser desacoplar.

### 6.2 Reentrada [S]

- **Retorno da Lua** (periastro de 100 a 1.500): velocidade de entrada 34,3 a 34,4 b/t, ângulo de −8° a −21°.
- **Desorbitar de LEO 2.500**: 26,0 a 27,0 b/t, ângulo de −1,2° a −5,7°.
- **Desaceleração balística de pico:** 1,2 g (LEO) a 2,4 g (retorno da Lua) com vt = 10; 1,3 a 3,4 g com vt = 5.
- **Aerocaptura:** com vt = 10, periastros acima de ~1.550 saem da atmosfera. Em 1.500 a nave sai com 28,3 b/t e fica ligada (apoastro ~27.750). Abaixo de 1.300 ela pousa. O corredor é de ~250 blocos, mas qualquer periastro baixo é sobrevivível.
- **Aquecimento leve:** índice `Q = √ρ·(v/28,9)³`, com 28,9 = v_c de superfície, e medidor `H' = A·Q·escudo − B·H`, com B = 0,01 (τ = 100 ticks). Pico de Q:

| Cenário | Pico de Q |
|---|---|
| LEO, vt = 10 | 0,15 a 0,19 |
| Retorno da Lua, vt = 10 | 0,37 a 0,56 |
| LEO, vt = 5 | 0,09 a 0,11 |
| Retorno da Lua, vt = 5 | 0,21 a 0,33 |

  Com A = 0,04, picos de H:

| Cenário | Pico de H |
|---|---|
| LEO, vt = 5 | 0,31 |
| Retorno da Lua, vt = 5 | 0,58 |
| LEO, vt = 10 | 0,6 |
| Retorno da Lua, vt = 10 (sem cápsula nem escudo) | 1,2 (limite 1,0) |

  Uma tag de bloco "escudo" multiplica A por ~0,4. Consequência sugerida: aviso e chamas com H > 0,7; perda de blocos ou dano ao tripulante só acima de 1.
- **Paraquedas:** abrir com `v < 8 b/t` e `h < 900`, com rampa de ~80 ticks. Pico de ~6 g na abertura, pouso a 0,5 b/t (10 b/s), descida de 60 a 80 s. Sem rampa, com vt 10 e abertura em h = 700 (v ≈ 15 b/t), a abertura dá 12 a 22 g.

## 7. Mapeamento para a dimensão e a rotação da Lua (item 6)

### 7.1 Código validado (erro de ida e volta ≤ 1e-11 para Terra e Lua) [C]

```java
record Local(double x, double y, double z, double vx, double vy, double vz) {}

// estado inercial relativo ao corpo (eixos do espaço) -> coordenadas da dimensão do corpo
static Local toDimension(SolarSystem.Body body, double clock, V3 r, V3 v) {
    SolarSystem.Pose pose = body.poseAt(clock);
    double[] b = new double[3];
    pose.unrotate(r.x(), r.y(), r.z(), b);                       // fixo no corpo
    double[] p = new double[3];
    SpaceFrame.fromBodyFixed(body, b[0], b[1], b[2], p);         // x em [-C/2, C/2], y altura, z
    double[] abs = new double[3];
    SpaceFrame.toSpace(body, clock, p[0], p[1], p[2], abs);
    double[] g = new double[3];
    SpaceFrame.groundVelocity(body, clock, abs[0], abs[1], abs[2], g);   // inclui o centro
    double[] c = new double[3];
    body.velocityAt(clock, c);
    double[] vg = {v.x() - (g[0]-c[0]), v.y() - (g[1]-c[1]), v.z() - (g[2]-c[2])}; // v - ω×r
    double[] vb = new double[3];
    pose.unrotate(vg[0], vg[1], vg[2], vb);
    double[] vl = new double[3];
    SpaceFrame.bodyToLocal(body, p[0], p[2], vb[0], vb[1], vb[2], vl);
    return new Local(p[0], p[1], p[2], vl[0], vl[1], vl[2]);
}
```

O inverso (`fromDimension`) usa `toBodyFixed`, `pose.rotate`, `localToBody`, soma `ω×r` e fecha com erro 1e-11. Está em `Proto.java`.

### 7.2 Cuidados

1. **Integrar em eixos inerciais.** Converta só para posição e exibição. No referencial girante da Lua entrariam Coriolis (2ωv = 2,3e-3 b/t² a 5 b/t, 17% de g_Lua) e Euler (ω muda ±33%).
2. **Rotação da Lua [C].**
   - O eixo é a coluna 1 da pose (norte lunar, fixo no espaço em (−sin5°, cos5°, 0)).
   - A velocidade de superfície no equador é 0,369 a 0,727 b/t (média 0,597), medida via `groundVelocity − velocityAt`.
   - Um jogador parado no chão tem `v_rel` ao chão = 0 e velocidade inercial relativa = ω·R·cos(lat).
3. **Ponto sub-terrestre fixo em lon = π** (x = ±8.192 na Lua): a Terra fica parada no céu lunar. Em compensação, a LLO de 500 desloca o solo 29° a 58° de longitude por órbita (média 47,7° em 3.633 ticks), e o pouso num ponto exige temporizar a passagem.
4. **Velocidade do Minecraft ≠ taxa de variação de coordenadas.** x = R·lon, então perto dos polos x anda 1/cos(lat) mais rápido. Para a entidade use posição absoluta a cada tick (`setPos`) e derive o `deltaMovement` por diferença de posições. O `BodyTransfer` atual usa a velocidade métrica local, o que é aceitável para o jogador perto do equador.
5. **Zona do espaço [P]:** a dimensão vazia pode usar **coordenadas inerciais do `SpaceFrame` diretamente** (mapeamento identidade, velocidade igual ao `deltaMovement`). O corpo dominante da física é independente da dimensão em que o foguete vive.
6. **Atitude [P]:** guarde-a em quaternion nos eixos inerciais (o SAS prograde, retrógrado, radial e normal ficam triviais) e converta para yaw/pitch/roll local só para exibir. A base local gira 2π por órbita em relação à inercial. Na atmosfera use velocidade em relação ao ar (= inercial na Terra); na Lua, em relação ao chão, subtraindo ω×r.
7. **Singularidades:** a latitude máxima da dimensão é 70° (`maxLatitudeBlocks` = 12.741). Voos inclinados na zona 1 (< 5.000) podem bater na calota; na zona 2 (inercial) o problema some.

## 8. API Java proposta: `com.spacehorizons.space.flight`

Só os adaptadores importam `SolarSystem` e `PlanetDefinition`. O núcleo usa doubles e `V3`.

```java
public record V3(double x, double y, double z) {           // plus, minus, times, dot, cross, length, unit
    public static final V3 ZERO = new V3(0, 0, 0);
}

public final class FlightConstants {
    public static final double G0 = 0.08;                    // b/t²; também o g0 do Isp
    public static final double FUEL_T_PER_MB = 0.0008;       // t/mB (galão = 0,8 t)
    public static final double SOI_HYSTERESIS = 0.05;
}

public record BodyPhysics(SolarSystem.Body body, double mu, double radius,
                          double soiRadius, double atmosphereTop) {
    public static BodyPhysics of(SolarSystem.Body b);        // mu = G0·surfaceGravity·R²; soi Laplace; atm = AtmosphereModel
    public double gravityAt(double r);                       // mu/r²
    public double circularSpeed(double r);                   // √(mu/r)
    public double escapeSpeed(double r);
    public double period(double a);                          // 2π√(a³/mu)
    public double altitude(double r);                        // r − radius  (= y − seaLevel)
}

public record Orbit(double mu, double a, double e, double inclination, double trueAnomaly,
                    double periapsis, double apoapsis, double period) {
    public static Orbit fromState(double mu, V3 r, V3 v, V3 pole);   // pole = −norte do corpo (eixos do espaço)
    public double timeTo(double trueAnomaly);                // elipse >= 0; hipérbole pode ser negativo
    public double trueAnomalyAtRadius(double r);             // NaN se não alcança
    public boolean bound();
}

public final class Kepler {
    public static V3[] propagate(double mu, V3 r0, V3 v0, double dt);   // variáveis universais; V3[]{r, v}
}

public final class Hohmann {
    public static double dv1(double mu, double r1, double r2);
    public static double dv2(double mu, double r1, double r2);
    public static double flightTime(double mu, double r1, double r2);
    public static double phaseAngle(double flightTime, double targetAngularRate);
}

public final class Tsiolkovsky {
    public static double deltaV(double ve, double m0, double m1);
    public static double finalMass(double m0, double ve, double dv);
    public static double burnTime(double m0, double flowTPerTick, double ve, double dv);
}

public record EngineSpec(double thrust, double ispTicks, double massT) {   // ve = ispTicks·G0
    public double exhaustVelocity();
}

public final class RocketMass {                              // massa seca dos blocos + combustível
    public static double dryMass(Iterable<BlockMass> blocks);
    public double fuelCapacityMb();
    public double wetMass(double fuelMb);
}

public final class AtmosphericDrag {
    public static V3 apply(V3 v, double h, double k0, AtmosphereModel atm, double dt);   // v/(1 + k0·ρ·|v|·dt)
}

public final class ReentryHeat {
    public static double heatIndex(double rho, double v);    // √ρ·(v/28,9)³
    public static double step(double h, double q, double a, double b, double shield);
}

public record FlightState(SolarSystem.Body body, V3 r, V3 v, double massT, double fuelMb,
                          double heat, V3 heading /*quaternion no código final*/) {}

public final class FlightIntegrator {
    public static FlightState step(FlightState s, double throttle, EngineSpec[] engines,
                                   double k0, double clock);               // KDK + empuxo exato + arrasto
}

public final class SoiTracker {
    public static FlightState update(FlightState s, double clock);         // patched conics com histerese
}

public final class FrameBridge {
    public static Local toDimension(SolarSystem.Body b, double clock, V3 r, V3 v);
    public static V3[] fromDimension(SolarSystem.Body b, double clock, Local l);
}

public final class Predictor {
    public static Encounter nextEncounter(FlightState s, double clock, int horizonTicks);   // SOI, periastro, impacto
}
```

Passo de física por tick, validado em `Burn.java` e nas simulações:

```java
// 1. empuxo (Tsiolkovsky exato no tick)
double mdot = Math.min(F*throttle/ve, fuelT);
double m1 = m0 - mdot;
double dv = ve*Math.log(m0/m1);
V3 aT = heading.times(dv);                                 // dt = 1
// 2. kick-drift-kick com a gravidade do corpo dominante
V3 g = r.times(-mu/Math.pow(r.length(), 3));
v = v.plus(g.plus(aT).times(0.5));
r = r.plus(v);
g = r.times(-mu/Math.pow(r.length(), 3));
v = v.plus(g.plus(aT).times(0.5));
// 3. arrasto implícito, só dentro da atmosfera
double h = r.length() - R;
if (h < atmTop) v = v.times(1.0/(1.0 + k0*atm.density(h)*v.length()));
// 4. calor, contato (GroundProbe) e troca de SOI
```

Contato: uma interface `GroundProbe` devolve o raio do chão no ponto. Pouso se velocidade vertical e horizontal ao chão e inclinação ficarem dentro de limites; senão, batida.

## 9. Testes unitários sugeridos (valores do protótipo)

- `BodyPhysics`: μ_Terra = 8,703423e6, μ_Lua = 8,975405e4 (razão 0,0103125), SOI Laplace = 9.627,1, v_c de superfície da Terra = 28,8865.
- LEO 6.000: v_c = 23,016, T = 4.485,45. TLI: Δv1 = 5,8234, T/2 = 7.955,34, e = 0,57006, ra = 60.000.
- `Orbit.fromState` em órbita circular: e < 1e-12; inclinação 180° contra +Y para uma órbita equatorial para leste; 90° em órbita polar (com P = −Y).
- Verlet contra Kepler: 15.911 ticks na transferência, diferença < 1 bloco, ΔE/E < 1e-12. Hipérbole lunar, 3.000 ticks, diferença < 0,02 bloco.
- SOI: ida e volta exata (erro 0) e histerese (nenhuma troca dupla).
- `FrameBridge`: ida e volta com erro < 1e-9 para Terra e Lua em relógios aleatórios.
- Regressão do `SolarSystem`: velocidade da Lua min 8,495, max 16,724, média 13,744; velocidade de superfície equatorial 0,369 a 0,727.
- `Tsiolkovsky`: `deltaV(40, 22, 7)` = 40·ln(22/7); queima com TWR_órbita ≥ 1,5 perde < 50 blocos de apoastro.
- Arrasto: a velocidade nunca troca de sinal com k0·ρ·|v|·dt grande.
- Dimensionamento: foguete mínimo (1,9 t seco, 4.000 mB, 1 motor) tem Δv ≥ 35.

## 10. Riscos e perguntas em aberto

**Riscos**
1. **Lua não uniforme (±33% de velocidade).** Custo de captura de 1,3 a 5,9 b/t e janela de custo que repete a cada 8 dias de jogo. Alternativas: aceitar e fazer um planejador de janelas; ou suavizar a Lua para uma taxa uniforme (o ângulo oscilaria até 12,6° em relação à fase do sol).
2. **Referencial não inercial e mão invertida.** Se o painel mostrar inclinação sem o polo P = −Norte, uma órbita para leste aparece como 180°.
3. **Relógio.** Pausa (`/time pause`, `ADVANCE_TIME`), taxa e saltos (dormir, `/time set`) deslocam ou congelam a Lua. Com `rate` ≠ 1 a velocidade da Lua por tick de jogo muda. Com a Lua congelada o transporte funciona, mas sem janelas.
4. **SOI física (9.627) ≠ regra existente (12.000).** Unificar muda o autopiloto criativo e seus testes (`FlightPlanTest`).
5. **Perfil de atmosfera é visual.** Compartilhar `AtmosphereModel` na física acopla visual e dinâmica; reentrada sai leve (1,2 a 3,4 g) porque a atmosfera é fina.
6. **Isp é crítico.** Com 380 ticks a ida e volta é inviável; com 450 exige 172 galões. Um estágio só tem pouca margem; separar estágios (etapa 6) relaxa isso.
7. **Hover lunar com TWR ~52.** Acelerador contínuo sem mínimo ou motores individuais.
8. **Altura da dimensão (384, `min_y` −64).** Entidades acima de y = 320 até 5.000+ são permitidas pelo teste de voo (`tp` a y = 15.000), mas a geração de chunks e o tick de entidades devem ser verificados na zona 1.
9. **Integração com o código existente.** `TravelTransfers.checkSphereOfInfluence` e `FlightBoost.update` agem sobre qualquer jogador em planeta. O passageiro do foguete precisa ser ignorado (checar `isPassenger()`).
10. **Precisão.** Doubles bastam (erros de 1e-11). A divisão do arrasto é de 1ª ordem e só vale na atmosfera.

**Perguntas em aberto**
1. Aceitar a Lua retrógrada neste referencial (lançar para oeste) ou inverter o sentido no `SolarSystem`?
2. Manter a variação de velocidade da Lua (janelas) ou uniformizar?
3. Isp do motor básico: 500 (84 galões, 28 min de refino na ida e volta) ou 560 (48 galões)?
4. Densidade do combustível e massas por bloco: os valores propostos servem ou prefere outro ponto de equilíbrio?
5. A dimensão de espaço usa coordenadas inerciais do `SpaceFrame` (proposta) ou outro referencial?
6. Política de relógio: congelar a física quando o relógio pausa, ignorar `rate` ou corrigir por `rate`? E o que fazer em salto de relógio com foguete em trânsito?
7. Unificar a regra de SOI (Laplace) e o autopiloto criativo, ou manter as duas?
8. O aquecimento de reentrada causa só aviso, ou também perda de blocos e dano ao tripulante? E o que conta como "escudo"?
9. Qual a altitude de órbita de referência: 2.500 ou ≥ 5.000, a depender de onde começa a dimensão de espaço?
10. Nível "assistido": o computador de bordo executa queimas (nós de manobra automáticos) ou só orienta o SAS?