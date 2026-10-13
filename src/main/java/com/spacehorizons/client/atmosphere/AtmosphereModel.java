package com.spacehorizons.client.atmosphere;

import java.util.Map;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

import net.minecraft.resources.Identifier;

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
 */
public record AtmosphereModel(double thickness, double baseAltitude, boolean present) {
    /** Espessura da atmosfera do planeta de teste (R ≈ 10.430): "espaço" a partir de ~1.800 blocos. */
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

    /** Sem atmosfera: espessura e base são só valores válidos para o construtor, nada deles é usado. */
    public static final AtmosphereModel NONE = new AtmosphereModel(1.0, 0.0, false);

    private static final Map<Identifier, AtmosphereModel> BY_PLANET = Map.of(
            Planets.EARTH.id(), new AtmosphereModel(EARTH_THICKNESS, EARTH_BASE_ALTITUDE),
            Planets.MOON.id(), NONE);

    public AtmosphereModel(double thickness, double baseAltitude) {
        this(thickness, baseAltitude, true);
    }

    public AtmosphereModel {
        if (baseAltitude < 0 || thickness <= baseAltitude) {
            throw new IllegalArgumentException("espessura deve ser maior que a altitude base: " + thickness + " / " + baseAltitude);
        }
    }

    /** Atmosfera de um planeta; planetas fora do catálogo usam a da Terra de teste. */
    public static AtmosphereModel of(PlanetDefinition planet) {
        return BY_PLANET.getOrDefault(planet.id(), new AtmosphereModel(EARTH_THICKNESS, EARTH_BASE_ALTITUDE));
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
