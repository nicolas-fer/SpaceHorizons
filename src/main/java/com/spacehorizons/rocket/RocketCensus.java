package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Contagem dos blocos de um foguete por papel, mais as ESTRUTURAS multibloco que eles formam ({@link Multiblocks}): tanques em colunas
 * e motores em cubos. {@code tanks} e {@code engines} continuam sendo o total de BLOCOS químicos (os do Tier 1); as listas dizem quais
 * deles estão dentro de uma estrutura (o resto vale um a um). Entrada de {@link RocketSpecs#statsFor}.
 *
 * <p>{@code tier2}: as contagens e as estruturas dos motores e tanques do Tier 2 e dos geradores RTG, por papel de bloco ({@link Tier2}).
 * {@link #allEngines()} e {@link #allTanks()} somam todos os tipos.
 */
public record RocketCensus(int cockpits, int engines, int tanks, int parachutes, int legs, int hulls, int structures,
        List<Multiblocks.Box> tankStructures, List<Multiblocks.Box> engineStructures, Tier2 tier2) {

    public static final RocketCensus EMPTY = new RocketCensus(0, 0, 0, 0, 0, 0, 0);

    /** Os papéis do Tier 2 que o censo conta à parte. */
    public static final List<RocketBlockKind> TIER2_KINDS = List.of(RocketBlockKind.NUCLEAR_ENGINE, RocketBlockKind.ION_ENGINE,
            RocketBlockKind.FUSION_ENGINE, RocketBlockKind.HYDROGEN_TANK, RocketBlockKind.XENON_TANK, RocketBlockKind.RTG);

    /** Contagens e estruturas dos papéis do Tier 2 (os ausentes valem zero e sem estruturas). */
    public record Tier2(Map<RocketBlockKind, Integer> counts, Map<RocketBlockKind, List<Multiblocks.Box>> structures) {
        public static final Tier2 NONE = new Tier2(Map.of(), Map.of());

        public Tier2 {
            Map<RocketBlockKind, Integer> c = new EnumMap<>(RocketBlockKind.class);
            counts.forEach((k, v) -> {
                if (v != null && v != 0) {
                    c.put(k, v);
                }
            });
            Map<RocketBlockKind, List<Multiblocks.Box>> s = new EnumMap<>(RocketBlockKind.class);
            structures.forEach((k, v) -> {
                if (v != null && !v.isEmpty()) {
                    s.put(k, List.copyOf(v));
                }
            });
            counts = Map.copyOf(c);
            structures = Map.copyOf(s);
        }

        public int count(RocketBlockKind kind) {
            return this.counts.getOrDefault(kind, 0);
        }

        public List<Multiblocks.Box> structures(RocketBlockKind kind) {
            return this.structures.getOrDefault(kind, List.of());
        }

        public int total() {
            return this.counts.values().stream().mapToInt(Integer::intValue).sum();
        }

        Tier2 plus(RocketBlockKind kind, int count) {
            Map<RocketBlockKind, Integer> c = new EnumMap<>(RocketBlockKind.class);
            c.putAll(this.counts);
            c.merge(kind, count, Integer::sum);
            return new Tier2(c, this.structures);
        }

        Tier2 withStructures(Multiblocks.Analysis analysis) {
            Map<RocketBlockKind, List<Multiblocks.Box>> s = new EnumMap<>(RocketBlockKind.class);
            for (RocketBlockKind kind : TIER2_KINDS) {
                if (kind.isEngine() || kind.isTank()) {
                    s.put(kind, analysis.of(kind).boxes());
                }
            }
            return new Tier2(this.counts, s);
        }
    }

    public RocketCensus {
        tankStructures = List.copyOf(tankStructures);
        engineStructures = List.copyOf(engineStructures);
        tier2 = tier2 == null ? Tier2.NONE : tier2;
    }

    /** Só peças do Tier 1. */
    public RocketCensus(int cockpits, int engines, int tanks, int parachutes, int legs, int hulls, int structures,
            List<Multiblocks.Box> tankStructures, List<Multiblocks.Box> engineStructures) {
        this(cockpits, engines, tanks, parachutes, legs, hulls, structures, tankStructures, engineStructures, Tier2.NONE);
    }

    /** Sem estruturas formadas (todos os tanques e motores avulsos). */
    public RocketCensus(int cockpits, int engines, int tanks, int parachutes, int legs, int hulls, int structures) {
        this(cockpits, engines, tanks, parachutes, legs, hulls, structures, List.of(), List.of());
    }

    /** Total de blocos do foguete. */
    public int total() {
        return this.cockpits + this.engines + this.tanks + this.parachutes + this.legs + this.hulls + this.structures + this.tier2.total();
    }

    /** Blocos do papel dado (químicos pelos campos, Tier 2 pelo {@link Tier2}). */
    public int count(RocketBlockKind kind) {
        return switch (kind) {
            case COCKPIT -> this.cockpits;
            case ENGINE -> this.engines;
            case FUEL_TANK -> this.tanks;
            case PARACHUTE -> this.parachutes;
            case LEG -> this.legs;
            case HULL -> this.hulls;
            case STRUCTURE -> this.structures;
            default -> this.tier2.count(kind);
        };
    }

    /** As estruturas formadas do papel dado (motores ou tanques; vazio para os outros). */
    public List<Multiblocks.Box> structuresOf(RocketBlockKind kind) {
        if (kind == RocketBlockKind.FUEL_TANK) {
            return this.tankStructures;
        }
        if (kind == RocketBlockKind.ENGINE) {
            return this.engineStructures;
        }
        return this.tier2.structures(kind);
    }

    /** Blocos do papel dado que valem um a um (fora de qualquer estrutura). */
    public int looseOf(RocketBlockKind kind) {
        return count(kind) - structuresOf(kind).stream().mapToInt(Multiblocks.Box::volume).sum();
    }

    /** Blocos de motor de todos os tipos. */
    public int allEngines() {
        int n = this.engines;
        for (RocketBlockKind kind : TIER2_KINDS) {
            if (kind.isEngine()) {
                n += this.tier2.count(kind);
            }
        }
        return n;
    }

    /** Blocos de tanque de todos os tipos. */
    public int allTanks() {
        int n = this.tanks;
        for (RocketBlockKind kind : TIER2_KINDS) {
            if (kind.isTank()) {
                n += this.tier2.count(kind);
            }
        }
        return n;
    }

    /** Geradores RTG. */
    public int rtgs() {
        return this.tier2.count(RocketBlockKind.RTG);
    }

    /** Há peças do Tier 2. */
    public boolean hasTier2() {
        return this.tier2.total() > 0;
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
        if (!(this.cockpits == other.cockpits && this.engines == other.engines && this.tanks == other.tanks && this.parachutes == other.parachutes
                && this.legs == other.legs && this.hulls == other.hulls && this.structures == other.structures
                && shapes(this.tankStructures).equals(shapes(other.tankStructures)) && shapes(this.engineStructures).equals(shapes(other.engineStructures))
                && this.tier2.counts().equals(other.tier2.counts()))) {
            return false;
        }
        for (RocketBlockKind kind : TIER2_KINDS) {
            if (!shapes(this.tier2.structures(kind)).equals(shapes(other.tier2.structures(kind)))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> shapes(List<Multiblocks.Box> boxes) {
        return boxes.stream().map(b -> b.w() + "x" + b.h() + "x" + b.d()).sorted().toList();
    }

    /** O mesmo censo com as estruturas dadas (as contagens de blocos não mudam). */
    public RocketCensus withStructures(Multiblocks.Analysis analysis) {
        return new RocketCensus(this.cockpits, this.engines, this.tanks, this.parachutes, this.legs, this.hulls, this.structures,
                analysis.tanks().boxes(), analysis.engines().boxes(), this.tier2.withStructures(analysis));
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
            case NUCLEAR_ENGINE, ION_ENGINE, FUSION_ENGINE, HYDROGEN_TANK, XENON_TANK, RTG -> new RocketCensus(this.cockpits, this.engines,
                    this.tanks, this.parachutes, this.legs, this.hulls, this.structures, this.tankStructures, this.engineStructures,
                    this.tier2.plus(kind, count));
            default -> this;
        };
    }

    private RocketCensus copy(int cockpits, int engines, int tanks, int parachutes, int legs, int hulls, int structures) {
        return new RocketCensus(cockpits, engines, tanks, parachutes, legs, hulls, structures, this.tankStructures, this.engineStructures, this.tier2);
    }

    /** Contagem por papel, sem estruturas (use {@link #withStructures} para somá-las). */
    public static RocketCensus of(Iterable<RocketBlockKind> kinds) {
        int[] n = new int[RocketBlockKind.values().length];
        for (RocketBlockKind kind : kinds) {
            n[kind.ordinal()]++;
        }
        Map<RocketBlockKind, Integer> t2 = new EnumMap<>(RocketBlockKind.class);
        for (RocketBlockKind kind : TIER2_KINDS) {
            t2.put(kind, n[kind.ordinal()]);
        }
        return new RocketCensus(n[RocketBlockKind.COCKPIT.ordinal()], n[RocketBlockKind.ENGINE.ordinal()],
                n[RocketBlockKind.FUEL_TANK.ordinal()], n[RocketBlockKind.PARACHUTE.ordinal()], n[RocketBlockKind.LEG.ordinal()],
                n[RocketBlockKind.HULL.ordinal()], n[RocketBlockKind.STRUCTURE.ordinal()], List.of(), List.of(), new Tier2(t2, Map.of()));
    }

    /** As células de cada papel de motor e tanque (packed), para {@link Multiblocks#analyze(Map)}. */
    static Map<RocketBlockKind, long[]> cellsByKind(Map<RocketBlockKind, List<Long>> lists) {
        Map<RocketBlockKind, long[]> out = new EnumMap<>(RocketBlockKind.class);
        lists.forEach((k, v) -> out.put(k, v.stream().mapToLong(Long::longValue).toArray()));
        return out;
    }

    /** Lista vazia por papel, para juntar células. */
    static Map<RocketBlockKind, List<Long>> emptyCellLists() {
        Map<RocketBlockKind, List<Long>> out = new EnumMap<>(RocketBlockKind.class);
        for (RocketBlockKind kind : RocketBlockKind.values()) {
            if (kind.isEngine() || kind.isTank()) {
                out.put(kind, new ArrayList<>());
            }
        }
        return out;
    }
}
