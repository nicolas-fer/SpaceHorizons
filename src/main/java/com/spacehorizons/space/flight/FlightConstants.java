package com.spacehorizons.space.flight;

/**
 * Constantes da física de voo e do foguete (decisões de {@code docs/DESIGN_FOGUETE.md}). Unidades do jogo: blocos, ticks
 * e toneladas; força em t·blocos/tick² e combustível em mB.
 */
public final class FlightConstants {
    /** Gravidade de referência (blocos/tick²): a base do {@code Attributes.GRAVITY}; também o g0 do Isp. */
    public static final double G0 = 0.08;

    /** Massa do combustível (t por mB): 0,8 t por galão de 1.000 mB. */
    public static final double FUEL_T_PER_MB = 0.0008;

    /** Histerese da esfera de influência (fração do raio): entra a 95% do raio, sai a 105%. */
    public static final double SOI_HYSTERESIS = 0.05;

    // ------------------------------------------------------------------ zonas

    /** Altitude de saída da dimensão da Terra (blocos); os outros corpos escalam pelo raio. */
    public static final double EXIT_ALTITUDE_EARTH = 5_000.0;

    /** Voltar à dimensão do corpo só abaixo desta fração da altitude de saída (histerese das passagens). */
    public static final double ZONE_ENTER_FACTOR = 0.8;

    // ------------------------------------------------------------------ foguete (massas em toneladas)

    /** Impulso específico do motor básico em ticks: ve = Isp · G0 = 40 blocos/tick. */
    public static final double ISP_TICKS = 500.0;
    /** Empuxo de um motor (t·blocos/tick²). */
    public static final double ENGINE_THRUST = 2.5;
    public static final double ENGINE_MASS_T = 0.6;
    public static final double COCKPIT_MASS_T = 0.8;
    /** Massa seca do tanque (a carga é à parte, por {@link #FUEL_T_PER_MB}). */
    public static final double TANK_DRY_MASS_T = 0.2;
    public static final double TANK_CAPACITY_MB = 4_000.0;
    public static final double PARACHUTE_MASS_T = 0.1;
    public static final double LEG_MASS_T = 0.1;
    /** Qualquer outro bloco ligado ao foguete (casco, estrutura). */
    public static final double BLOCK_MASS_T = 0.1;

    // ------------------------------------------------------------------ arrasto e paraquedas

    /**
     * Coeficiente geométrico do arrasto: {@code k0 = κ · área frontal / massa} (κ em t/bloco³). Calibrado para que um
     * foguete de 9 blocos² de seção e 22 t tenha velocidade terminal de ~16 blocos/tick ao nível do mar.
     */
    public static final double DRAG_AREA_FACTOR = 7.6e-4;
    /** Limites da velocidade terminal ao nível do mar (blocos/tick): impedem foguetes absurdos de quebrar a simulação. */
    public static final double MIN_TERMINAL_SPEED = 6.0;
    public static final double MAX_TERMINAL_SPEED = 40.0;
    /** Velocidade terminal com o paraquedas aberto (blocos/tick): pouso a 10 blocos/s. */
    public static final double PARACHUTE_TERMINAL_SPEED = 0.5;
    /** O paraquedas só abre abaixo desta velocidade (blocos/tick), com atmosfera. */
    public static final double PARACHUTE_OPEN_MAX_SPEED = 8.0;
    /** Ticks da rampa de abertura do paraquedas. */
    public static final double PARACHUTE_RAMP_TICKS = 80.0;

    // ------------------------------------------------------------------ aquecimento (índice visual)

    /** Velocidade de referência do índice de calor: a circular na superfície da Terra (blocos/tick). */
    public static final double HEAT_REFERENCE_SPEED = 28.9;
    /** Ganho e decaimento por tick do medidor: {@code H += A·Q − B·H} (constante de tempo de 100 ticks). */
    public static final double HEAT_GAIN = 0.04;
    public static final double HEAT_DECAY = 0.01;
    /** Teto do medidor (1,0 é o limite "perigoso"; o índice nunca passa disto). */
    public static final double HEAT_MAX = 2.0;
    /** A partir daqui o painel avisa e o foguete brilha. */
    public static final double HEAT_WARNING = 0.7;

    // ------------------------------------------------------------------ pouso

    /** Pouso suave: |v vertical| (blocos/tick), |v horizontal ao chão| e inclinação máximos. */
    public static final double LANDING_MAX_VERTICAL = 1.0;
    public static final double LANDING_MAX_HORIZONTAL = 0.5;
    public static final double LANDING_MAX_TILT_DEG = 15.0;

    private FlightConstants() {
    }
}
