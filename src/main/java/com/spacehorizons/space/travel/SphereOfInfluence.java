package com.spacehorizons.space.travel;

import java.util.List;

import com.spacehorizons.space.SolarSystem;

/**
 * Regra de qual corpo "manda" num ponto do espaço: o de menor distância-ao-centro dividida pelo próprio raio
 * (o corpo grande domina de mais longe, o pequeno só de perto). Uma histerese impede a troca de ficar
 * pulando de um lado para o outro perto da fronteira: para sair do corpo atual, o candidato precisa ser
 * claramente melhor.
 */
public final class SphereOfInfluence {
    /** O candidato precisa ter razão menor que (1 - HYSTERESIS) vezes a do corpo atual. */
    public static final double HYSTERESIS = 0.1;

    private SphereOfInfluence() {
    }

    /** Distância ao centro do corpo, em raios dele. */
    public static double ratio(SolarSystem.Body body, double sx, double sy, double sz) {
        double dx = sx - body.x();
        double dy = sy - body.y();
        double dz = sz - body.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz) / body.planet().radius();
    }

    /** Corpo dominante sem histerese. */
    public static SolarSystem.Body nearest(List<SolarSystem.Body> bodies, double sx, double sy, double sz) {
        SolarSystem.Body best = null;
        double bestRatio = Double.MAX_VALUE;
        for (SolarSystem.Body body : bodies) {
            double r = ratio(body, sx, sy, sz);
            if (r < bestRatio) {
                bestRatio = r;
                best = body;
            }
        }
        return best;
    }

    /** Corpo dominante com histerese: devolve {@code current} a menos que outro seja claramente mais próximo. */
    public static SolarSystem.Body dominant(List<SolarSystem.Body> bodies, SolarSystem.Body current, double sx, double sy,
            double sz) {
        SolarSystem.Body best = current;
        double bestRatio = ratio(current, sx, sy, sz) * (1.0 - HYSTERESIS);
        for (SolarSystem.Body body : bodies) {
            if (body == current) {
                continue;
            }
            double r = ratio(body, sx, sy, sz);
            if (r < bestRatio) {
                bestRatio = r;
                best = body;
            }
        }
        return best;
    }

    public static SolarSystem.Body dominant(SolarSystem.Body current, double sx, double sy, double sz) {
        return dominant(SolarSystem.BODIES, current, sx, sy, sz);
    }
}
