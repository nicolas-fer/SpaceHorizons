package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.List;

import com.spacehorizons.rocket.Multiblocks;

/**
 * Contagem dos blocos de uma nave por papel (entrada de {@link ShipSpecs#statsFor}); o espelho de {@code RocketCensus} para as naves.
 * O ar não conta. Os tanques ({@code rocket_fuel_tank}) formam colunas multibloco ({@link Multiblocks}) pela mesma regra do foguete:
 * {@code tankStructures} diz quais blocos de tanque estão dentro de uma coluna formada (o resto vale um a um).
 */
public record ShipCensus(int bridges, int engines, int tanks, int windows, int seats, int airlocks, int structures,
        List<Multiblocks.Box> tankStructures) {

    public static final ShipCensus EMPTY = new ShipCensus(0, 0, 0, 0, 0, 0, 0);

    public ShipCensus {
        tankStructures = List.copyOf(tankStructures);
    }

    /** Sem colunas formadas (todos os tanques avulsos). */
    public ShipCensus(int bridges, int engines, int tanks, int windows, int seats, int airlocks, int structures) {
        this(bridges, engines, tanks, windows, seats, airlocks, structures, List.of());
    }

    /** Total de blocos da nave. */
    public int total() {
        return this.bridges + this.engines + this.tanks + this.windows + this.seats + this.airlocks + this.structures;
    }

    /** Blocos de tanque dentro de uma coluna formada. */
    public int formedTankBlocks() {
        return this.tankStructures.stream().mapToInt(Multiblocks.Box::volume).sum();
    }

    /** Tanques que valem um a um. */
    public int looseTanks() {
        return this.tanks - formedTankBlocks();
    }

    /** O mesmo censo com estas colunas formadas. */
    public ShipCensus withTankStructures(List<Multiblocks.Box> boxes) {
        return new ShipCensus(this.bridges, this.engines, this.tanks, this.windows, this.seats, this.airlocks, this.structures, boxes);
    }

    /** As contagens por papel são iguais (sem olhar as estruturas)? */
    public boolean sameCounts(ShipCensus other) {
        return this.bridges == other.bridges && this.engines == other.engines && this.tanks == other.tanks && this.windows == other.windows
                && this.seats == other.seats && this.airlocks == other.airlocks && this.structures == other.structures;
    }

    /** Conta mais {@code count} blocos do papel dado (o ar é ignorado). As colunas formadas não mudam. */
    public ShipCensus plus(ShipBlockKind kind, int count) {
        return switch (kind) {
            case BRIDGE -> copy(this.bridges + count, this.engines, this.tanks, this.windows, this.seats, this.airlocks, this.structures);
            case ENGINE -> copy(this.bridges, this.engines + count, this.tanks, this.windows, this.seats, this.airlocks, this.structures);
            case FUEL_TANK -> copy(this.bridges, this.engines, this.tanks + count, this.windows, this.seats, this.airlocks, this.structures);
            case WINDOW -> copy(this.bridges, this.engines, this.tanks, this.windows + count, this.seats, this.airlocks, this.structures);
            case SEAT -> copy(this.bridges, this.engines, this.tanks, this.windows, this.seats + count, this.airlocks, this.structures);
            case AIRLOCK -> copy(this.bridges, this.engines, this.tanks, this.windows, this.seats, this.airlocks + count, this.structures);
            case STRUCTURE -> copy(this.bridges, this.engines, this.tanks, this.windows, this.seats, this.airlocks, this.structures + count);
            case AIR -> this;
        };
    }

    private ShipCensus copy(int bridges, int engines, int tanks, int windows, int seats, int airlocks, int structures) {
        return new ShipCensus(bridges, engines, tanks, windows, seats, airlocks, structures, this.tankStructures);
    }

    /** Soma dois censos (as seções de uma caixa somam o da nave). */
    public ShipCensus plus(ShipCensus other) {
        List<Multiblocks.Box> boxes = new ArrayList<>(this.tankStructures);
        boxes.addAll(other.tankStructures);
        return new ShipCensus(this.bridges + other.bridges, this.engines + other.engines, this.tanks + other.tanks, this.windows + other.windows,
                this.seats + other.seats, this.airlocks + other.airlocks, this.structures + other.structures, boxes);
    }

    public static ShipCensus of(Iterable<ShipBlockKind> kinds) {
        ShipCensus c = EMPTY;
        for (ShipBlockKind kind : kinds) {
            c = c.plus(kind, 1);
        }
        return c;
    }
}
