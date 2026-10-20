package com.spacehorizons.ship;

/**
 * Contagem dos blocos de uma nave por papel (entrada de {@link ShipSpecs#statsFor}); o espelho de {@code RocketCensus} para as naves.
 * O ar não conta.
 */
public record ShipCensus(int bridges, int engines, int tanks, int windows, int seats, int airlocks, int structures) {

    public static final ShipCensus EMPTY = new ShipCensus(0, 0, 0, 0, 0, 0, 0);

    /** Total de blocos da nave. */
    public int total() {
        return this.bridges + this.engines + this.tanks + this.windows + this.seats + this.airlocks + this.structures;
    }

    /** Conta mais {@code count} blocos do papel dado (o ar é ignorado). */
    public ShipCensus plus(ShipBlockKind kind, int count) {
        return switch (kind) {
            case BRIDGE -> new ShipCensus(this.bridges + count, this.engines, this.tanks, this.windows, this.seats, this.airlocks, this.structures);
            case ENGINE -> new ShipCensus(this.bridges, this.engines + count, this.tanks, this.windows, this.seats, this.airlocks, this.structures);
            case FUEL_TANK -> new ShipCensus(this.bridges, this.engines, this.tanks + count, this.windows, this.seats, this.airlocks, this.structures);
            case WINDOW -> new ShipCensus(this.bridges, this.engines, this.tanks, this.windows + count, this.seats, this.airlocks, this.structures);
            case SEAT -> new ShipCensus(this.bridges, this.engines, this.tanks, this.windows, this.seats + count, this.airlocks, this.structures);
            case AIRLOCK -> new ShipCensus(this.bridges, this.engines, this.tanks, this.windows, this.seats, this.airlocks + count, this.structures);
            case STRUCTURE -> new ShipCensus(this.bridges, this.engines, this.tanks, this.windows, this.seats, this.airlocks, this.structures + count);
            case AIR -> this;
        };
    }

    /** Soma dois censos (as seções de uma caixa somam o da nave). */
    public ShipCensus plus(ShipCensus other) {
        return new ShipCensus(this.bridges + other.bridges, this.engines + other.engines, this.tanks + other.tanks, this.windows + other.windows,
                this.seats + other.seats, this.airlocks + other.airlocks, this.structures + other.structures);
    }

    public static ShipCensus of(Iterable<ShipBlockKind> kinds) {
        ShipCensus c = EMPTY;
        for (ShipBlockKind kind : kinds) {
            c = c.plus(kind, 1);
        }
        return c;
    }
}
