package com.spacehorizons.space.travel;

import java.util.List;

import com.spacehorizons.space.SolarSystem;

/**
 * Regra de qual corpo "manda" num ponto do espaço: o de menor distância-ao-centro dividida pelo próprio raio
 * (o corpo grande domina de mais longe, o pequeno só de perto). Uma histerese impede a troca de ficar
 * pulando de um lado para o outro perto da fronteira: para sair do corpo atual, o candidato precisa ser
 * claramente melhor. Os corpos se movem: todas as contas valem para o mesmo instante {@code clock} (ticks do Overworld).
 */
public final class SphereOfInfluence {
    /** O candidato precisa ter razão menor que (1 - HYSTERESIS) vezes a do corpo atual. */
    public static final double HYSTERESIS = 0.1;

    private SphereOfInfluence() {
    }

    /** Distância ao centro do corpo, em raios dele. */
    public static double ratio(SolarSystem.Body body, double clock, double sx, double sy, double sz) {
        SolarSystem.Pose pose = body.poseAt(clock);
        double dx = sx - pose.x();
        double dy = sy - pose.y();
        double dz = sz - pose.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz) / body.planet().radius();
    }

    /** Corpo dominante sem histerese. */
    public static SolarSystem.Body nearest(List<SolarSystem.Body> bodies, double clock, double sx, double sy, double sz) {
        SolarSystem.Body best = null;
        double bestRatio = Double.MAX_VALUE;
        for (SolarSystem.Body body : bodies) {
            double r = ratio(body, clock, sx, sy, sz);
            if (r < bestRatio) {
                bestRatio = r;
                best = body;
            }
        }
        return best;
    }

    /** Corpo dominante com histerese: devolve {@code current} a menos que outro seja claramente mais próximo. */
    public static SolarSystem.Body dominant(List<SolarSystem.Body> bodies, SolarSystem.Body current, double clock, double sx,
            double sy, double sz) {
        SolarSystem.Body best = current;
        double bestRatio = ratio(current, clock, sx, sy, sz) * (1.0 - HYSTERESIS);
        for (SolarSystem.Body body : bodies) {
            if (body == current) {
                continue;
            }
            double r = ratio(body, clock, sx, sy, sz);
            if (r < bestRatio) {
                bestRatio = r;
                best = body;
            }
        }
        return best;
    }

    public static SolarSystem.Body dominant(SolarSystem.Body current, double clock, double sx, double sy, double sz) {
        return dominant(SolarSystem.BODIES, current, clock, sx, sy, sz);
    }
}
