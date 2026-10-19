package com.spacehorizons.space.flight;

import com.spacehorizons.planet.atmosphere.AtmosphereModel;
import com.spacehorizons.space.SolarSystem;

/**
 * Física de um corpo para o voo: parâmetro gravitacional, raio, esfera de influência e altitude de saída da dimensão.
 *
 * <ul>
 *   <li>{@code mu = G0 · gravidade de superfície · R²} (blocos³/tick²), coerente com a gravidade que o jogador sente.
 *       Terra 8,7034e8; Lua 8,9754e6.</li>
 *   <li>Esfera de influência de Laplace: {@code d · (mu/mu_Terra)^0,4}. A da Lua tem 96.270 blocos; a da Terra é
 *       infinita (é o corpo central). A regra antiga ({@code SphereOfInfluence}) continua só para o autopiloto
 *       criativo.</li>
 *   <li>Altitude de saída: {@code 5000 · R / R_Terra} (Terra 5.000, Lua 1.250), acima dela o foguete vive na dimensão
 *       de espaço.</li>
 * </ul>
 *
 * Altitude é sempre {@code r − radius} (distância ao centro menos o raio), que na dimensão do corpo é {@code y − seaLevel}.
 */
public record BodyPhysics(SolarSystem.Body body, double mu, double radius, double soiRadius, double exitAltitude) {
    private static final BodyPhysics EARTH = build(SolarSystem.EARTH);
    private static final BodyPhysics MOON = build(SolarSystem.MOON);

    /** Física de um corpo (as duas do catálogo ficam em cache). */
    public static BodyPhysics of(SolarSystem.Body b) {
        if (b == SolarSystem.EARTH) {
            return EARTH;
        }
        if (b == SolarSystem.MOON) {
            return MOON;
        }
        return build(b);
    }

    private static double muOf(SolarSystem.Body b) {
        double radius = b.planet().radius();
        return FlightConstants.G0 * b.planet().surfaceGravity() * radius * radius;
    }

    private static BodyPhysics build(SolarSystem.Body b) {
        double radius = b.planet().radius();
        double mu = muOf(b);
        double soi;
        if (b == SolarSystem.EARTH) {
            soi = Double.POSITIVE_INFINITY;
        } else {
            double distance = Math.sqrt(b.x() * b.x() + b.y() * b.y() + b.z() * b.z());
            soi = distance * Math.pow(mu / muOf(SolarSystem.EARTH), 0.4);
        }
        double exit = FlightConstants.EXIT_ALTITUDE_EARTH * radius / SolarSystem.EARTH.planet().radius();
        return new BodyPhysics(b, mu, radius, soi, exit);
    }

    /** Aceleração da gravidade (blocos/tick²) à distância {@code r} do centro. */
    public double gravityAt(double r) {
        return mu / (r * r);
    }

    /** Velocidade de uma órbita circular de raio {@code r}. */
    public double circularSpeed(double r) {
        return Math.sqrt(mu / r);
    }

    /** Velocidade de escape à distância {@code r}. */
    public double escapeSpeed(double r) {
        return Math.sqrt(2.0 * mu / r);
    }

    /** Período (ticks) de uma órbita de semieixo maior {@code a}. */
    public double period(double a) {
        return 2.0 * Math.PI * Math.sqrt(a * a * a / mu);
    }

    /** Altitude (blocos acima do nível do mar) à distância {@code r} do centro. */
    public double altitude(double r) {
        return r - radius;
    }

    /** Distância ao centro para uma altitude. */
    public double radiusAt(double altitude) {
        return radius + altitude;
    }

    /** Gravidade na superfície (blocos/tick²): é {@code G0 · surfaceGravity}. */
    public double surfaceGravity() {
        return gravityAt(radius);
    }

    public AtmosphereModel atmosphere() {
        return AtmosphereModel.of(body.planet());
    }

    /** Raio de entrada na esfera de influência (com histerese): 95% do raio. */
    public double soiEnterRadius() {
        return soiRadius * (1.0 - FlightConstants.SOI_HYSTERESIS);
    }

    /** Raio de saída da esfera de influência (com histerese): 105% do raio. */
    public double soiExitRadius() {
        return soiRadius * (1.0 + FlightConstants.SOI_HYSTERESIS);
    }

    /**
     * Polo de referência das inclinações no instante {@code clock}, nos eixos inerciais (I): o SUL do corpo (a coluna 1 da
     * pose inercial, o norte, negada). Os eixos fixos no corpo são de mão esquerda (leste = +Z, norte = +Y, cima = +X em lon
     * 0), então com o polo no norte uma órbita para leste sairia com 180°. Com P = −norte, leste dá 0°; a Lua, que orbita
     * para leste em I (prógrada) num plano inclinado 5°, dá ~5° visto da Terra.
     */
    public V3 pole(double clock) {
        double[] rot = body.inertialPoseAt(clock).rotation();
        return new V3(-rot[1], -rot[4], -rot[7]);
    }
}
