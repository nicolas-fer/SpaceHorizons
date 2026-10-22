package com.spacehorizons.planet.atmosphere;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

/**
 * Modelo central da atmosfera de um planeta: tudo (neblina, cor do céu, estrelas, limbo) deriva da
 * mesma curva de densidade em função da altitude sobre o nível do mar. Java puro, sem classes do
 * Minecraft de render, para poder ser testado.
 *
 * Perfil: até {@code baseAltitude} a atmosfera é "cheia" (densidade 1, o visual do vanilla não muda
 * para quem anda em montanhas); dali até {@code thickness} a densidade cai suave a 0 e fica 0 acima.
 * A mesma curva está replicada em shaders/atmosphere/limb.fsh (atmosphereDensity).
 *
 * Mundos sem ar (a Lua) usam {@link #NONE}: densidade 0 em qualquer altitude, céu preto com estrelas, sem
 * neblina e sem limbo. {@code present = false} é o que liga esse comportamento.
 *
 * Os valores de cada planeta vêm dos parâmetros dele ({@code "atmosphere"} em {@code data/spacehorizons/planet/<id>.json}).
 * {@code breathable = false} (Marte) é ar de verdade para o céu e a neblina, mas sem oxigênio: o traje trata como vácuo.
 * {@code scatterR/G/B} é o espalhamento relativo por canal do limbo (Rayleigh azul na Terra, poeira avermelhada em Marte) e
 * {@code opticalDepth} a profundidade óptica vertical (1 na Terra; menor = halo mais fraco, atmosfera fina).
 */
public record AtmosphereModel(double thickness, double baseAltitude, boolean present, boolean breathable,
        double scatterR, double scatterG, double scatterB, double opticalDepth) {
    /** Espessura da atmosfera do planeta de teste (R ≈ 104.304): "espaço" a partir de ~1.800 blocos. */
    public static final double EARTH_THICKNESS = 1_800.0;
    /** Abaixo disso nada muda em relação ao vanilla (topo de montanha ainda é "chão"). */
    public static final double EARTH_BASE_ALTITUDE = 160.0;

    /**
     * Quanto a distância da neblina cresce quando a densidade chega a 0 (antes de ser desligada de vez).
     * Com 5.000 a neblina de 12 chunks (192 blocos) já passa de 900.000 blocos, muito além da circunferência.
     */
    public static final double FOG_FAR_FACTOR = 5_000.0;

    /** Com o céu abaixo deste brilho (0..1) as estrelas começam a aparecer. */
    private static final double STARS_SKY_BRIGHTNESS = 0.5;

    /** Espalhamento relativo por canal da Terra (Rayleigh aproximado: o azul espalha mais que o vermelho). */
    public static final double[] EARTH_SCATTER = {0.26, 0.50, 1.0};

    /** Sem atmosfera: espessura e base são só valores válidos para o construtor, nada deles é usado. */
    public static final AtmosphereModel NONE = new AtmosphereModel(1.0, 0.0, false, false, 0.0, 0.0, 0.0, 0.0);

    /** Atmosfera respirável com o limbo azul da Terra. */
    public AtmosphereModel(double thickness, double baseAltitude) {
        this(thickness, baseAltitude, true, true, EARTH_SCATTER[0], EARTH_SCATTER[1], EARTH_SCATTER[2], 1.0);
    }

    public AtmosphereModel {
        if (baseAltitude < 0 || thickness <= baseAltitude) {
            throw new IllegalArgumentException("espessura deve ser maior que a altitude base: " + thickness + " / " + baseAltitude);
        }
        if (breathable && !present) {
            throw new IllegalArgumentException("atmosfera respirável precisa existir (present = true)");
        }
        if (scatterR < 0 || scatterG < 0 || scatterB < 0 || opticalDepth < 0) {
            throw new IllegalArgumentException("espalhamento e profundidade óptica não podem ser negativos");
        }
    }

    /** Atmosfera de um planeta, dos parâmetros dele. */
    public static AtmosphereModel of(PlanetDefinition planet) {
        return Planets.spec(planet).atmosphere().model();
    }

    /**
     * O traje precisa de oxigênio aqui? Sim no vácuo e em qualquer altitude de uma atmosfera sem oxigênio (Marte). É o que
     * o oxigênio, o compressor e o dano usam; {@link #isVacuum} é só o visual (céu e neblina).
     */
    public boolean needsOxygen(double altitude) {
        return !breathable || isVacuum(altitude);
    }

    /** 0 dentro da atmosfera "cheia" até 1 no topo, já normalizado entre a base e a espessura. */
    private double depth(double altitude) {
        return clamp01((altitude - baseAltitude) / (thickness - baseAltitude));
    }

    /** Densidade relativa 1 → 0, com derivada 0 no topo (sem emenda visível ao sair da atmosfera). */
    public double density(double altitude) {
        if (!present) {
            return 0.0;
        }
        double k = 1.0 - depth(altitude);
        return k * k;
    }

    /** Acima da atmosfera: sem neblina, céu preto. */
    public boolean isVacuum(double altitude) {
        return !present || altitude >= thickness;
    }

    /** Fator 1 → 0 aplicado às cores do céu e da neblina; cai um pouco mais devagar que a densidade. */
    public double skyBrightness(double altitude) {
        if (!present) {
            return 0.0;
        }
        return Math.pow(1.0 - depth(altitude), 1.5);
    }

    /** 0..1: estrelas aparecem quando o céu escurece, saturando no espaço. */
    public double starVisibility(double altitude) {
        if (!present) {
            return 1.0;
        }
        return clamp01(1.0 - skyBrightness(altitude) / STARS_SKY_BRIGHTNESS);
    }

    /**
     * Multiplicador das distâncias de neblina (vanilla e nuvens). 1 dentro da atmosfera cheia e
     * {@link #FOG_FAR_FACTOR} quando a densidade chega a 0; geométrico, para a neblina sumir de forma gradual.
     */
    public double fogDistanceFactor(double altitude) {
        if (!present) {
            return FOG_FAR_FACTOR;
        }
        return Math.pow(FOG_FAR_FACTOR, 1.0 - density(altitude));
    }

    /** 0..1: intensidade do brilho do limbo; some perto do chão e satura a partir de metade da espessura. */
    public double limbVisibility(double altitude) {
        if (!present) {
            return 0.0;
        }
        return smoothstep(0.1 * thickness, 0.5 * thickness, altitude);
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }
}
