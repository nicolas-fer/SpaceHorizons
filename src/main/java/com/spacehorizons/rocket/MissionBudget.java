package com.spacehorizons.rocket;

/**
 * O orçamento de Δv de cada destino, para o painel de projeto dizer o que o foguete alcança SEM prometer o que ele não faz
 * (docs/PLANO_TIERS.md, seção 4, e docs/MISSAO_LUA.md). Lógica pura.
 *
 * <p>Os números por fase são os MEDIDOS pela missão completa (Terra, órbita baixa, Lua, pouso, volta, pouso na Terra) rodada pela mesma
 * {@code FlightMachine} do jogo, no simulador {@code MissionSim} (e em jogo, {@code -PshOnly=mission}); o {@code MissionBudgetTest} refaz a
 * missão e confere que cada soma bate com o que o simulador gastou, e que os foguetes de teste caem no destino que o simulador mostra
 * (o A, de Δv 253, vai à Lua e volta mas não fecha o pouso lunar; o da missão, de 292, fecha). O painel usa o MESMO cálculo da montagem
 * ({@link RocketSpecs#statsFor} sobre o {@link RocketCensus}), então o Δv que ele mostra é o do foguete montado.
 *
 * <p>Margem: o destino só é prometido com {@value #MARGIN} vezes o Δv medido (5% de folga), porque o jogador não pilota como o piloto de
 * teste. Regras de empuxo (não simuladas, conservadoras): até TWR 1 não decola; de 1 a {@value #MIN_TWR_ORBIT} só vai até o salto.
 */
public final class MissionBudget {
    /** Os destinos, do pior ao melhor; o nome do enum é a chave de tradução ({@code design.spacehorizons.destination.<nome>}). */
    public enum Destination {
        /** Empuxo/peso na Terra menor ou igual a 1 (ou sem Δv): o foguete não sai do chão. */
        NO_LIFTOFF,
        /** Sai do chão mas não chega a 5.000 blocos de altitude (o limite da dimensão da Terra). */
        HOP,
        /** Salto suborbital: sobe acima da atmosfera e cai de volta. */
        SUBORBITAL,
        /** Chega à órbita baixa (apoastro 6.000, periastro 5.200). */
        ORBIT,
        /** Vai à Lua (captura em órbita lunar) e volta à Terra, sem pousar na Lua. */
        MOON_RETURN,
        /** Vai à Lua, pousa, decola e volta à Terra (a missão completa). */
        MOON_LANDING_RETURN
    }

    /** Folga sobre o Δv medido: o destino só é prometido com 5% a mais. */
    public static final double MARGIN = 1.05;
    /** Abaixo (ou igual) disto o foguete não sai do chão (a regra da decolagem da FlightMachine: empuxo × acelerador > gravidade). */
    public static final double MIN_TWR_LIFTOFF = 1.0;
    /** Da órbita em diante o foguete precisa de empuxo/peso na Terra de pelo menos isto (a missão medida usou 1,75). */
    public static final double MIN_TWR_ORBIT = 1.2;

    // ---- Δv por fase, em blocos/tick (docs/MISSAO_LUA.md, tabela "Resultado em jogo") ----
    /** Salto suborbital: subir acima dos 5.000 de altitude de saída (vertical, com ~16 de perda de gravidade). */
    public static final double SUBORBITAL_HOP = 50.0;
    /** Órbita baixa: subida (63,8) mais circularização (14,9). */
    public static final double LOW_ORBIT = 78.8;
    /** Transferência para a Lua: TLI (26,8) e as duas correções de meio de curso (3,07 e 0,04). */
    public static final double TRANSFER = 26.8 + 3.07 + 0.04;
    /** Captura em órbita lunar (retrógrada, no periastro). */
    public static final double CAPTURE = 9.73;
    /** Pouso lunar: freio na órbita (18,63) e descida motorizada (5,67). */
    public static final double LUNAR_LANDING = 18.63 + 5.67;
    /** Decolagem lunar (6,58) e circularização (16,30). */
    public static final double LUNAR_ASCENT = 6.58 + 16.30;
    /** Queima de volta à Terra, da órbita lunar. */
    public static final double RETURN_BURN = 8.16;
    /** Reentrada e pouso na Terra com paraquedas (freio de 1,65 e 87,8). */
    public static final double REENTRY = 1.65 + 87.8;
    /** Altitude de saída da dimensão da Terra, em blocos (o salto precisa passar dela). */
    public static final double SPACE_ALTITUDE = 5000.0;

    private MissionBudget() {
    }

    /** O Δv medido (sem margem) para chegar a {@code destination}; 0 para quem não decola. */
    public static double measured(Destination destination) {
        return switch (destination) {
            case NO_LIFTOFF -> 0.0;
            case HOP -> 0.0;
            case SUBORBITAL -> SUBORBITAL_HOP;
            case ORBIT -> LOW_ORBIT;
            case MOON_RETURN -> LOW_ORBIT + TRANSFER + CAPTURE + RETURN_BURN + REENTRY;
            case MOON_LANDING_RETURN -> LOW_ORBIT + TRANSFER + CAPTURE + LUNAR_LANDING + LUNAR_ASCENT + RETURN_BURN + REENTRY;
        };
    }

    /** O Δv que o foguete precisa ter para o destino ser prometido (medido com a margem). */
    public static double required(Destination destination) {
        return measured(destination) * MARGIN;
    }

    /** Este destino inclui a volta à Terra (e portanto o paraquedas)? */
    public static boolean includesEarthReturn(Destination destination) {
        return destination == Destination.MOON_RETURN || destination == Destination.MOON_LANDING_RETURN;
    }

    /**
     * O melhor destino que o foguete alcança.
     *
     * @param deltaV    Δv com os tanques cheios (blocos/tick), de {@code RocketStats.deltaV}
     * @param twr       empuxo/peso na Terra com os tanques cheios, de {@code RocketStats.twr}
     * @param parachute o foguete tem paraquedas (a volta à Terra da missão lunar abre o paraquedas)
     */
    public static Destination reachable(double deltaV, double twr, boolean parachute) {
        if (!(twr > MIN_TWR_LIFTOFF) || !(deltaV > 0.0)) {
            return Destination.NO_LIFTOFF;
        }
        Destination best = Destination.HOP;
        for (Destination d : new Destination[] {Destination.SUBORBITAL, Destination.ORBIT, Destination.MOON_RETURN, Destination.MOON_LANDING_RETURN}) {
            boolean enough = deltaV >= required(d);
            boolean thrust = d.ordinal() < Destination.ORBIT.ordinal() || twr >= MIN_TWR_ORBIT;
            boolean chute = !includesEarthReturn(d) || parachute;
            if (enough && thrust && chute) {
                best = d;
            } else {
                break;
            }
        }
        return best;
    }

    /** O próximo destino acima de {@code current}, ou null se já é o melhor. */
    public static Destination next(Destination current) {
        Destination[] all = Destination.values();
        return current.ordinal() + 1 < all.length ? all[current.ordinal() + 1] : null;
    }
}
