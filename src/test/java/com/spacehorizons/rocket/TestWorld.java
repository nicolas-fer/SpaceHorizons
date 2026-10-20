package com.spacehorizons.rocket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mundo de brinquedo para os testes de montagem e de projeto: tudo é ar, menos o que foi posto. {@link #design} monta um foguete
 * de projeto (motor de um tamanho, coluna de tanques, cockpit, paraquedas, cascos) sobre uma plataforma, o que o painel e o simulador
 * da missão avaliam sem o jogo.
 */
public final class TestWorld implements RocketAssembly.Grid {
    private final Map<Long, RocketBlockKind> blocks = new HashMap<>();

    public TestWorld put(int x, int y, int z, RocketBlockKind kind) {
        this.blocks.put(RocketAssembly.pack(x, y, z), kind);
        return this;
    }

    public TestWorld fill(int x0, int y0, int z0, int w, int h, int d, RocketBlockKind kind) {
        for (int y = y0; y < y0 + h; y++) {
            for (int z = z0; z < z0 + d; z++) {
                for (int x = x0; x < x0 + w; x++) {
                    put(x, y, z, kind);
                }
            }
        }
        return this;
    }

    @Override
    public RocketBlockKind kindAt(int x, int y, int z) {
        return this.blocks.getOrDefault(RocketAssembly.pack(x, y, z), RocketBlockKind.AIR);
    }

    /** Onde fica o cockpit de {@link #design}. */
    public record Design(TestWorld world, int cockpitX, int cockpitY, int cockpitZ) {
        public RocketAssembly.Report analyze() {
            return RocketAssembly.analyze(this.world, List.of(new int[] {this.cockpitX, this.cockpitY, this.cockpitZ}));
        }
    }

    /**
     * Foguete de projeto sobre uma plataforma 7x7: motor de lado {@code engineSide} (1: um bloco; 2 e 3: cubo cheio; 0: uma camada 3x3 de
     * motores avulsos, como o foguete da missão), coluna de tanques
     * {@code tankW x tankH x tankD} (largura em x, altura, profundidade em z) em cima dele, cockpit no topo da coluna, paraquedas (se
     * {@code parachute}) em cima do cockpit e {@code hulls} cascos em placas 3x3 acima do cockpit/paraquedas.
     */
    public static Design design(int engineSide, int tankW, int tankH, int tankD, int hulls, boolean parachute) {
        TestWorld w = new TestWorld().fill(-3, 0, -3, 7, 1, 7, RocketBlockKind.PAD);
        int engineHeight = Math.max(1, engineSide);
        if (engineSide == 0) {
            w.fill(0, 1, 0, 3, 1, 3, RocketBlockKind.ENGINE);
        } else {
            w.fill(0, 1, 0, engineSide, engineSide, engineSide, RocketBlockKind.ENGINE);
        }
        int tankY = 1 + engineHeight;
        w.fill(0, tankY, 0, tankW, tankH, tankD, RocketBlockKind.FUEL_TANK);
        int cockpitY = tankY + tankH;
        w.put(0, cockpitY, 0, RocketBlockKind.COCKPIT);
        int y = cockpitY + 1;
        if (parachute) {
            w.put(0, y++, 0, RocketBlockKind.PARACHUTE);
        }
        int left = hulls;
        for (int layer = 0; left > 0; layer++, y++) {
            for (int i = 0; i < 9 && left > 0; i++, left--) {
                w.put(-1 + i % 3, y, -1 + i / 3, RocketBlockKind.HULL);
            }
        }
        return new Design(w, 0, cockpitY, 0);
    }
}
