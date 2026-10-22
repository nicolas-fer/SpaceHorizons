package com.spacehorizons.rocket;

import com.spacehorizons.space.flight.Propellant;

/**
 * Os tipos de motor do foguete e os números de cada tamanho (docs/PLANO_TIERS.md, "Tier 2: conteúdo"; o químico é o do Tier 1,
 * docs/DESIGN_FOGUETE.md). Lógica pura. Os três tamanhos seguem a regra do Tier 1 ("peças maiores rendem mais"): o cubo 2x2x2 tem o
 * empuxo de 8 blocos x 1,2, a massa de 8 blocos x 0,8 e o Isp + {@code ispStep}; o 3x3x3, 27 x 1,5, 27 x 0,7 e o Isp + 2 {@code ispStep}.
 *
 * <ul>
 *   <li><b>Químico</b>: o motor do Tier 1 (2,5; 0,6 t; Isp 1.600).</li>
 *   <li><b>Nuclear</b> (NERVA): o dobro do Isp do químico, empuxo por massa de 40% do químico; queima hidrogênio e gasta o núcleo
 *       ({@link Propellant#NUCLEAR_CORE}, as barras carregadas no motor).</li>
 *   <li><b>Iônico</b>: Isp altíssimo (7,5x o químico) e empuxo minúsculo (1/25 do químico): não decola de lugar nenhum, só serve no espaço;
 *       queima xenônio e precisa da energia dos geradores RTG ({@link #powerDemand}): com menos geradores que o pedido, empurra na proporção.</li>
 *   <li><b>Fusão</b>: Isp de 6,25x o químico com o empuxo por massa perto do químico; o topo da progressão. Queima o hélio-3 que o
 *       próprio motor guarda (não há tanque de He-3: {@link #chargePerBlockMb}).</li>
 * </ul>
 */
public enum EngineType {
    CHEMICAL("chemical", RocketBlockKind.ENGINE, Propellant.CHEMICAL, 2.5, 0.6, 1600.0, 100.0, false),
    NUCLEAR("nuclear", RocketBlockKind.NUCLEAR_ENGINE, Propellant.HYDROGEN, 2.0, 1.2, 3200.0, 200.0, true),
    ION("ion", RocketBlockKind.ION_ENGINE, Propellant.XENON, 0.1, 0.4, 12000.0, 1000.0, false),
    FUSION("fusion", RocketBlockKind.FUSION_ENGINE, Propellant.HELIUM3, 4.0, 1.0, 10000.0, 1000.0, false);

    /** Multiplicadores do empuxo e da massa do cubo 2x2x2 e do 3x3x3 sobre a soma dos blocos avulsos. */
    public static final double THRUST_FACTOR_2 = 1.2;
    public static final double THRUST_FACTOR_3 = 1.5;
    public static final double MASS_FACTOR_2 = 0.8;
    public static final double MASS_FACTOR_3 = 0.7;

    /** Energia que um gerador RTG fornece (unidades de "um motor iônico 1x1"). */
    public static final int RTG_POWER = 1;
    /** Energia pedida pelo motor iônico 1x1, 2x2x2 e 3x3x3 (o cubo pede menos por bloco: 8 x 0,75 e 27 x 0,6). */
    public static final int ION_POWER_1 = 1;
    public static final int ION_POWER_2 = 6;
    public static final int ION_POWER_3 = 16;

    /** Vida útil de uma barra de combustível nuclear: mB de hidrogênio que ela aquece. Cada bloco de motor nuclear leva uma barra. */
    public static final int ROD_CORE_MB = 32_000;
    /** Hélio-3 que cada bloco de motor de fusão guarda, em mB (um galão por bloco). */
    public static final int FUSION_CHARGE_PER_BLOCK_MB = 1_000;

    private final String key;
    private final RocketBlockKind kind;
    private final Propellant propellant;
    private final double thrust1;
    private final double mass1;
    private final double isp1;
    private final double ispStep;
    private final boolean needsCore;

    EngineType(String key, RocketBlockKind kind, Propellant propellant, double thrust1, double mass1, double isp1, double ispStep,
            boolean needsCore) {
        this.key = key;
        this.kind = kind;
        this.propellant = propellant;
        this.thrust1 = thrust1;
        this.mass1 = mass1;
        this.isp1 = isp1;
        this.ispStep = ispStep;
        this.needsCore = needsCore;
    }

    public String key() {
        return this.key;
    }

    public RocketBlockKind kind() {
        return this.kind;
    }

    public Propellant propellant() {
        return this.propellant;
    }

    /** O motor gasta o núcleo nuclear junto com o hidrogênio. */
    public boolean needsCore() {
        return this.needsCore;
    }

    /** O tipo de motor de um papel de bloco, ou null se não é motor. */
    public static EngineType of(RocketBlockKind kind) {
        for (EngineType t : values()) {
            if (t.kind == kind) {
                return t;
            }
        }
        return null;
    }

    /** Empuxo (t·blocos/tick²) do motor de lado 1, 2 ou 3. */
    public double thrust(int side) {
        return switch (side) {
            case 3 -> 27 * this.thrust1 * THRUST_FACTOR_3;
            case 2 -> 8 * this.thrust1 * THRUST_FACTOR_2;
            default -> this.thrust1;
        };
    }

    /** Massa seca (t) do motor de lado 1, 2 ou 3. */
    public double massT(int side) {
        return switch (side) {
            case 3 -> 27 * this.mass1 * MASS_FACTOR_3;
            case 2 -> 8 * this.mass1 * MASS_FACTOR_2;
            default -> this.mass1;
        };
    }

    /** Isp (ticks) do motor de lado 1, 2 ou 3. */
    public double isp(int side) {
        return switch (side) {
            case 3 -> this.isp1 + 2 * this.ispStep;
            case 2 -> this.isp1 + this.ispStep;
            default -> this.isp1;
        };
    }

    /** Energia pedida (só o iônico; zero nos outros) pelo motor de lado 1, 2 ou 3. */
    public int powerDemand(int side) {
        if (this != ION) {
            return 0;
        }
        return switch (side) {
            case 3 -> ION_POWER_3;
            case 2 -> ION_POWER_2;
            default -> ION_POWER_1;
        };
    }

    /** O que cada bloco deste motor guarda dentro dele, em mB: o núcleo (nuclear), o hélio-3 (fusão), nada (os outros). */
    public int chargePerBlockMb() {
        return switch (this) {
            case NUCLEAR -> ROD_CORE_MB;
            case FUSION -> FUSION_CHARGE_PER_BLOCK_MB;
            default -> 0;
        };
    }

    /** O combustível guardado no próprio motor ({@link Propellant#NUCLEAR_CORE} ou {@link Propellant#HELIUM3}), ou null. */
    public Propellant chargePropellant() {
        return switch (this) {
            case NUCLEAR -> Propellant.NUCLEAR_CORE;
            case FUSION -> Propellant.HELIUM3;
            default -> null;
        };
    }
}
