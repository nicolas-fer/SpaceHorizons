package com.spacehorizons.rocket;

import java.util.List;

/**
 * Contagem dos blocos de um foguete por papel, mais as ESTRUTURAS multibloco que eles formam ({@link Multiblocks}): tanques em colunas
 * e motores em cubos. {@code tanks} e {@code engines} continuam sendo o total de BLOCOS; as listas dizem quais deles estão dentro de uma
 * estrutura (o resto vale um a um). Entrada de {@link RocketSpecs#statsFor}.
 */
public record RocketCensus(int cockpits, int engines, int tanks, int parachutes, int legs, int hulls, int structures,
        List<Multiblocks.Box> tankStructures, List<Multiblocks.Box> engineStructures) {

    public static final RocketCensus EMPTY = new RocketCensus(0, 0, 0, 0, 0, 0, 0);

    public RocketCensus {
        tankStructures = List.copyOf(tankStructures);
        engineStructures = List.copyOf(engineStructures);
    }

    /** Sem estruturas formadas (todos os tanques e motores avulsos). */
    public RocketCensus(int cockpits, int engines, int tanks, int parachutes, int legs, int hulls, int structures) {
        this(cockpits, engines, tanks, parachutes, legs, hulls, structures, List.of(), List.of());
    }

    /** Total de blocos do foguete. */
    public int total() {
        return this.cockpits + this.engines + this.tanks + this.parachutes + this.legs + this.hulls + this.structures;
    }

    /** Blocos de tanque dentro de uma coluna formada. */
    public int formedTankBlocks() {
        return this.tankStructures.stream().mapToInt(Multiblocks.Box::volume).sum();
    }

    /** Tanques que valem um a um (fora de qualquer coluna formada). */
    public int looseTanks() {
        return this.tanks - formedTankBlocks();
    }

    /** Blocos de motor dentro de um cubo formado. */
    public int formedEngineBlocks() {
        return this.engineStructures.stream().mapToInt(Multiblocks.Box::volume).sum();
    }

    /** Motores que valem um a um (1x1). */
    public int soloEngines() {
        return this.engines - formedEngineBlocks();
    }

    /** Quantos motores formados de cada lado (2 ou 3). */
    public int engineStructuresOfSide(int side) {
        return (int) this.engineStructures.stream().filter(b -> b.side() == side).count();
    }

    /**
     * Os dois censos têm as mesmas contagens e as mesmas estruturas, só pelo TAMANHO delas (largura, altura, profundidade), sem olhar onde estão: o
     * retrato guarda as caixas em coordenadas locais e a busca no mundo, nas do mundo.
     */
    public boolean sameShape(RocketCensus other) {
        return this.cockpits == other.cockpits && this.engines == other.engines && this.tanks == other.tanks && this.parachutes == other.parachutes
                && this.legs == other.legs && this.hulls == other.hulls && this.structures == other.structures
                && shapes(this.tankStructures).equals(shapes(other.tankStructures)) && shapes(this.engineStructures).equals(shapes(other.engineStructures));
    }

    private static List<String> shapes(List<Multiblocks.Box> boxes) {
        return boxes.stream().map(b -> b.w() + "x" + b.h() + "x" + b.d()).sorted().toList();
    }

    /** O mesmo censo com as estruturas dadas (as contagens de blocos não mudam). */
    public RocketCensus withStructures(Multiblocks.Analysis analysis) {
        return new RocketCensus(this.cockpits, this.engines, this.tanks, this.parachutes, this.legs, this.hulls, this.structures,
                analysis.tanks().boxes(), analysis.engines().boxes());
    }

    /** Conta mais um bloco do papel dado (papéis que não são parte do foguete são ignorados). As estruturas não mudam. */
    public RocketCensus plus(RocketBlockKind kind, int count) {
        return switch (kind) {
            case COCKPIT -> copy(this.cockpits + count, this.engines, this.tanks, this.parachutes, this.legs, this.hulls, this.structures);
            case ENGINE -> copy(this.cockpits, this.engines + count, this.tanks, this.parachutes, this.legs, this.hulls, this.structures);
            case FUEL_TANK -> copy(this.cockpits, this.engines, this.tanks + count, this.parachutes, this.legs, this.hulls, this.structures);
            case PARACHUTE -> copy(this.cockpits, this.engines, this.tanks, this.parachutes + count, this.legs, this.hulls, this.structures);
            case LEG -> copy(this.cockpits, this.engines, this.tanks, this.parachutes, this.legs + count, this.hulls, this.structures);
            case HULL -> copy(this.cockpits, this.engines, this.tanks, this.parachutes, this.legs, this.hulls + count, this.structures);
            case STRUCTURE -> copy(this.cockpits, this.engines, this.tanks, this.parachutes, this.legs, this.hulls, this.structures + count);
            default -> this;
        };
    }

    private RocketCensus copy(int cockpits, int engines, int tanks, int parachutes, int legs, int hulls, int structures) {
        return new RocketCensus(cockpits, engines, tanks, parachutes, legs, hulls, structures, this.tankStructures, this.engineStructures);
    }

    /** Contagem por papel, sem estruturas (use {@link #withStructures} para somá-las). */
    public static RocketCensus of(Iterable<RocketBlockKind> kinds) {
        int[] n = new int[RocketBlockKind.values().length];
        for (RocketBlockKind kind : kinds) {
            n[kind.ordinal()]++;
        }
        return new RocketCensus(n[RocketBlockKind.COCKPIT.ordinal()], n[RocketBlockKind.ENGINE.ordinal()],
                n[RocketBlockKind.FUEL_TANK.ordinal()], n[RocketBlockKind.PARACHUTE.ordinal()], n[RocketBlockKind.LEG.ordinal()],
                n[RocketBlockKind.HULL.ordinal()], n[RocketBlockKind.STRUCTURE.ordinal()]);
    }
}
