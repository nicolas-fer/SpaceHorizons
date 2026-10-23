package com.spacehorizons.moonbase.machine;

import com.spacehorizons.content.ContentIds;
import com.spacehorizons.moonbase.energy.EnergyMath;
import com.spacehorizons.moonbase.energy.EnergyMath.Role;

/**
 * Os blocos de energia e as máquinas da base lunar. Todos compartilham o mesmo block entity ({@code MoonBlockEntity}): o tipo
 * decide o papel na energia ({@link Role}), a capacidade e os slots. Os três primeiros não têm menu.
 *
 * Slots do contêiner (3): 0 = entrada principal, 1 = entrada/saída auxiliar, 2 = saída; cada máquina usa só alguns ({@link #slotUsed}).
 * <pre>
 *  oxygen_generator   0 tanque de oxigênio (esvaziado no reservatório)   1 gelo (vira oxigênio)
 *  helium3_extractor  0 regolito (blocos da tag helium3_sources)         2 galão de hélio-3 (enche)
 *  electrolyzer       0 gelo   1 tanque de oxigênio (enche)              2 galão de hidrogênio (enche)
 *  lunar_refinery     0 gelo                                             2 galão de combustível químico (enche)
 * </pre>
 */
public enum MachineKind {
    SOLAR_PANEL(ContentIds.SOLAR_PANEL, Role.PRODUCER, EnergyMath.PANEL_CAPACITY, false),
    BATTERY(ContentIds.BATTERY, Role.STORAGE, EnergyMath.BATTERY_CAPACITY, false),
    POWER_CABLE(ContentIds.POWER_CABLE, Role.CABLE, EnergyMath.CABLE_CAPACITY, false),
    OXYGEN_GENERATOR(ContentIds.OXYGEN_GENERATOR, Role.CONSUMER, EnergyMath.MACHINE_CAPACITY, true),
    HELIUM3_EXTRACTOR(ContentIds.HELIUM3_EXTRACTOR, Role.CONSUMER, EnergyMath.MACHINE_CAPACITY, true),
    ELECTROLYZER(ContentIds.ELECTROLYZER, Role.CONSUMER, EnergyMath.MACHINE_CAPACITY, true),
    LUNAR_REFINERY(ContentIds.LUNAR_REFINERY, Role.CONSUMER, EnergyMath.MACHINE_CAPACITY, true);

    public static final int SLOT_COUNT = 3;
    private static final MachineKind[] VALUES = values();

    private final String id;
    private final Role role;
    private final int capacity;
    private final boolean hasMenu;

    MachineKind(String id, Role role, int capacity, boolean hasMenu) {
        this.id = id;
        this.role = role;
        this.capacity = capacity;
        this.hasMenu = hasMenu;
    }

    /** Id do bloco (sem namespace). */
    public String id() {
        return id;
    }

    public Role role() {
        return role;
    }

    /** Capacidade de energia em UE. */
    public int capacity() {
        return capacity;
    }

    public boolean hasMenu() {
        return hasMenu;
    }

    /** O contêiner deste tipo usa o slot {@code slot}? */
    public boolean slotUsed(int slot) {
        return switch (this) {
            case OXYGEN_GENERATOR -> slot == 0 || slot == 1;
            case HELIUM3_EXTRACTOR, LUNAR_REFINERY -> slot == 0 || slot == 2;
            case ELECTROLYZER -> true;
            default -> false;
        };
    }

    /** O slot é de saída (recebe o produto: tanque ou galão que enche)? Entradas puras (gelo, regolito) não saem por funil. */
    public boolean slotIsOutput(int slot) {
        return switch (this) {
            case HELIUM3_EXTRACTOR, LUNAR_REFINERY -> slot == 2;
            case ELECTROLYZER -> slot == 1 || slot == 2;
            default -> false;
        };
    }

    public static MachineKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : SOLAR_PANEL;
    }
}
