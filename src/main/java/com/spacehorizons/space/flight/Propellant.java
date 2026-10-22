package com.spacehorizons.space.flight;

/**
 * Os "combustíveis" do foguete (docs/PLANO_TIERS.md, "Tier 2: conteúdo"). Cada motor queima o seu ({@link EngineGroup}) e cada um tem a sua
 * densidade (toneladas por mB). Todos são medidos em mB; a ordem (o {@code ordinal}) é a do NBT e da rede, então só se acrescenta no fim.
 *
 * <ul>
 *   <li>{@link #CHEMICAL}: o combustível do Tier 1 (refinaria, galão de combustível, {@code rocket_fuel_tank}); 0,8 t por 1.000 mB.</li>
 *   <li>{@link #HYDROGEN}: hidrogênio líquido do motor nuclear ({@code hydrogen_tank}); leve, 0,4 t por 1.000 mB.</li>
 *   <li>{@link #XENON}: xenônio do motor iônico ({@code xenon_tank}); denso (gás sob pressão), 1,2 t por 1.000 mB.</li>
 *   <li>{@link #HELIUM3}: hélio-3 do motor de fusão, guardado no PRÓPRIO motor (não há tanque de He-3); 0,8 t por 1.000 mB.</li>
 *   <li>{@link #NUCLEAR_CORE}: a vida útil do núcleo do motor nuclear, em mB de hidrogênio que o núcleo ainda consegue aquecer (as barras
 *       {@code nuclear_fuel_rod} carregadas no motor). Não tem massa: o motor nuclear só empurra enquanto houver hidrogênio E núcleo.</li>
 * </ul>
 */
public enum Propellant {
    CHEMICAL("chemical", FlightConstants.FUEL_T_PER_MB),
    HYDROGEN("hydrogen", 0.0004),
    XENON("xenon", 0.0012),
    HELIUM3("helium3", 0.0008),
    NUCLEAR_CORE("nuclear_core", 0.0);

    private static final Propellant[] VALUES = values();

    private final String key;
    private final double tonsPerMb;

    Propellant(String key, double tonsPerMb) {
        this.key = key;
        this.tonsPerMb = tonsPerMb;
    }

    /** Nome curto (NBT, chaves de tradução {@code propellant.spacehorizons.<key>}). */
    public String key() {
        return this.key;
    }

    /** Massa por mB, em toneladas (zero no núcleo nuclear). */
    public double tonsPerMb() {
        return this.tonsPerMb;
    }

    /** É massa que o foguete carrega e queima (tudo menos o núcleo nuclear). */
    public boolean hasMass() {
        return this.tonsPerMb > 0.0;
    }

    public static Propellant byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : CHEMICAL;
    }

    public static Propellant byKey(String key) {
        for (Propellant p : VALUES) {
            if (p.key.equals(key)) {
                return p;
            }
        }
        return null;
    }

    public static int count() {
        return VALUES.length;
    }
}
