package com.spacehorizons.rocket;

/** Contagem dos blocos de um foguete por papel. Entrada de {@link RocketSpecs#statsFor}. */
public record RocketCensus(int cockpits, int engines, int tanks, int parachutes, int legs, int hulls, int structures) {

    public static final RocketCensus EMPTY = new RocketCensus(0, 0, 0, 0, 0, 0, 0);

    /** Total de blocos do foguete. */
    public int total() {
        return this.cockpits + this.engines + this.tanks + this.parachutes + this.legs + this.hulls + this.structures;
    }

    /** Conta mais um bloco do papel dado (papéis que não são parte do foguete são ignorados). */
    public RocketCensus plus(RocketBlockKind kind, int count) {
        return switch (kind) {
            case COCKPIT -> new RocketCensus(this.cockpits + count, this.engines, this.tanks, this.parachutes, this.legs, this.hulls, this.structures);
            case ENGINE -> new RocketCensus(this.cockpits, this.engines + count, this.tanks, this.parachutes, this.legs, this.hulls, this.structures);
            case FUEL_TANK -> new RocketCensus(this.cockpits, this.engines, this.tanks + count, this.parachutes, this.legs, this.hulls, this.structures);
            case PARACHUTE -> new RocketCensus(this.cockpits, this.engines, this.tanks, this.parachutes + count, this.legs, this.hulls, this.structures);
            case LEG -> new RocketCensus(this.cockpits, this.engines, this.tanks, this.parachutes, this.legs + count, this.hulls, this.structures);
            case HULL -> new RocketCensus(this.cockpits, this.engines, this.tanks, this.parachutes, this.legs, this.hulls + count, this.structures);
            case STRUCTURE -> new RocketCensus(this.cockpits, this.engines, this.tanks, this.parachutes, this.legs, this.hulls, this.structures + count);
            default -> this;
        };
    }

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
