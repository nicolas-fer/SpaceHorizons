package com.spacehorizons.planet.terrain;

import java.util.List;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetMath;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.spec.OreVeinSpec;

/**
 * Veios de minério do subsolo de um planeta por parâmetros ({@code "ores"} do JSON). Um bloco é minério do veio quando
 * duas superfícies de ruído 3D passam perto de zero ao mesmo tempo (|a| e |b| abaixo da espessura): a interseção de duas
 * superfícies é uma curva, então o veio sai como um tubo fino e sinuoso, e um terceiro ruído, bem mais largo, liga e desliga
 * os veios por região. X passa por wrapX antes, então o padrão é periódico na circunferência (só a fileira da costura pode
 * não casar, debaixo da terra).
 *
 * Java puro, sem estado mutável: seguro entre threads. O teste mais barato vem primeiro (a região, depois uma superfície),
 * então a maioria dos blocos custa uma ou duas avaliações de ruído.
 */
public final class OreVeins {
    private static final long A_SALT = 0x0BE5_A1A1_0000_0001L;
    private static final long B_SALT = 0x0BE5_B2B2_0000_0002L;
    private static final long GATE_SALT = 0x0BE5_C3C3_0000_0003L;

    private final PlanetDefinition planet;
    private final Vein[] veins;

    private record Vein(OreVeinSpec spec, GradientNoise3D a, GradientNoise3D b, GradientNoise3D gate) {
    }

    public OreVeins(PlanetDefinition planet, List<OreVeinSpec> specs) {
        this.planet = planet;
        this.veins = new Vein[specs.size()];
        for (int i = 0; i < specs.size(); i++) {
            OreVeinSpec spec = specs.get(i);
            long seed = planet.seed() + 0x9E37_79B9L * (spec.salt() + 1L);
            veins[i] = new Vein(spec, new GradientNoise3D(seed ^ A_SALT), new GradientNoise3D(seed ^ B_SALT),
                    new GradientNoise3D(seed ^ GATE_SALT));
        }
    }

    public boolean isEmpty() {
        return veins.length == 0;
    }

    /** Menor e maior Y em que algum veio pode aparecer (para o gerador pular o resto da coluna). */
    public int minY() {
        int min = Integer.MAX_VALUE;
        for (Vein v : veins) {
            min = Math.min(min, v.spec.minY());
        }
        return min;
    }

    public int maxY() {
        int max = Integer.MIN_VALUE;
        for (Vein v : veins) {
            max = Math.max(max, v.spec.maxY());
        }
        return max;
    }

    /**
     * Índice do veio cujo minério ocupa o bloco, ou -1. O primeiro veio da lista ganha quando dois se cruzam. Os filtros de tipo de
     * superfície ({@code surface_types}) não valem aqui (sem a coluna não há como saber); o gerador usa
     * {@link #veinAt(int, int, int, SurfaceType)}. A latitude mínima vale sempre.
     */
    public int veinAt(int x, int y, int z) {
        return veinAt(x, y, z, null);
    }

    /** Como {@link #veinAt(int, int, int)}, mas só aceita os veios cujo {@code surface_types} inclui {@code surface} (ou é vazio). */
    public int veinAt(int x, int y, int z, SurfaceType surface) {
        long wx = PlanetMath.wrapX(planet, x);
        double latitude = Math.abs(Math.toDegrees(PlanetMath.latitudeRadians(planet, z)));
        for (int i = 0; i < veins.length; i++) {
            Vein v = veins[i];
            OreVeinSpec s = v.spec;
            if (y < s.minY() || y > s.maxY() || latitude < s.minLatitude()) {
                continue;
            }
            if (surface != null && !s.surfaceTypes().isEmpty() && !s.surfaceTypes().contains(surface)) {
                continue;
            }
            double g = 1.0 / s.gateScale();
            if (v.gate.noise(wx * g, y * g, z * g) <= s.gateThreshold()) {
                continue;
            }
            double k = 1.0 / s.scale();
            double ax = wx * k;
            double ay = y * k;
            double az = z * k;
            if (Math.abs(v.a.noise(ax, ay, az)) >= s.thickness()) {
                continue;
            }
            if (Math.abs(v.b.noise(ax + 17.31, ay - 5.17, az + 9.73)) >= s.thickness()) {
                continue;
            }
            return i;
        }
        return -1;
    }
}
