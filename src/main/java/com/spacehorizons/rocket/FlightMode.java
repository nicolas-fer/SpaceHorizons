package com.spacehorizons.rocket;

/** Modo de voo do foguete, sincronizado como um byte. */
public enum FlightMode {
    LANDED((byte) 0),
    FLIGHT((byte) 1),
    PARACHUTE((byte) 2);

    private final byte id;

    FlightMode(byte id) {
        this.id = id;
    }

    public byte id() {
        return this.id;
    }

    /** O modo de um id; ids desconhecidos (dado corrompido ou de versão futura) viram {@link #LANDED}. */
    public static FlightMode byId(byte id) {
        for (FlightMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return LANDED;
    }
}
