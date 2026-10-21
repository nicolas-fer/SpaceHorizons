package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.RocketAssembly;
import com.spacehorizons.rocket.RocketBlockKind;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketException.Reason;

/** Busca e validação da montagem de uma nave no chão, sobre uma grade de papéis (sem o jogo). */
class ShipAssemblyTest {
    /** Mundo de brinquedo: tudo é ar, menos o que foi posto. */
    private static final class World implements RocketAssembly.Grid {
        private final Map<String, RocketBlockKind> blocks = new HashMap<>();

        World put(int x, int y, int z, RocketBlockKind kind) {
            this.blocks.put(x + "," + y + "," + z, kind);
            return this;
        }

        @Override
        public RocketBlockKind kindAt(int x, int y, int z) {
            return this.blocks.getOrDefault(x + "," + y + "," + z, RocketBlockKind.AIR);
        }
    }

    /** Plataforma 3x3 em y=0 e, em cima dela, a ponte no centro com um casco de 2 blocos saindo para o lado. */
    private static World simpleShip() {
        World w = new World();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                w.put(x, 0, z, RocketBlockKind.PAD);
            }
        }
        return w.put(0, 1, 0, RocketBlockKind.COCKPIT).put(0, 1, 1, RocketBlockKind.STRUCTURE).put(0, 1, 2, RocketBlockKind.STRUCTURE);
    }

    private static Reason reasonOf(RocketAssembly.Grid grid, int x, int y, int z) {
        return assertThrows(RocketException.class, () -> ShipAssembly.scan(grid, x, y, z)).reason();
    }

    @Test
    void naveSoComPonteEPlataformaEhAchadaSemMotorNemTanque() throws Exception {
        RocketAssembly.Result r = ShipAssembly.scan(simpleShip(), 0, 1, 0);
        assertEquals(3, r.cells().size());
        assertEquals(RocketBlockKind.COCKPIT, r.cells().get(0).kind(), "a ponte é a primeira");
        assertEquals(0, r.census().engines());
        assertEquals(0, r.census().tanks());
        assertEquals(2, r.padContacts(), "a ponte e o casco em z=1 encostam na plataforma; o de z=2 está fora dela");
    }

    @Test
    void oFoguetoContinuaExigindoMotorETanque() {
        // a mesma nave, lida pela regra do foguete: a busca acha o casco, mas recusa por falta de motor e de tanque
        RocketException e = assertThrows(RocketException.class, () -> RocketAssembly.scan(simpleShip(), 0, 1, 0));
        assertEquals(Reason.NO_ENGINE, e.reason());
    }

    @Test
    void semPlataformaRecusa() {
        World w = new World().put(0, 1, 0, RocketBlockKind.COCKPIT).put(0, 1, 1, RocketBlockKind.STRUCTURE);
        assertEquals(Reason.NO_PAD, reasonOf(w, 0, 1, 0));
    }

    @Test
    void naoSeMontaComoCockpitQueNaoEhAPonte() {
        assertEquals(Reason.NO_COCKPIT, reasonOf(simpleShip(), 0, 1, 1));
    }

    @Test
    void duasPontesRecusam() {
        World w = simpleShip().put(0, 1, -1, RocketBlockKind.COCKPIT);
        assertEquals(Reason.MULTIPLE_COCKPITS, reasonOf(w, 0, 1, 0));
    }

    @Test
    void terrenoEncostadoRecusa() {
        World w = simpleShip().put(1, 1, 1, RocketBlockKind.TERRAIN);
        assertEquals(Reason.TOUCHES_TERRAIN, reasonOf(w, 0, 1, 0));
    }

    @Test
    void blocoProibidoRecusa() {
        World w = simpleShip().put(0, 1, 3, RocketBlockKind.FORBIDDEN);
        assertEquals(Reason.FORBIDDEN_BLOCK, reasonOf(w, 0, 1, 0));
    }

    @Test
    void chunkNaoCarregadoRecusa() {
        World w = simpleShip().put(0, 1, 3, RocketBlockKind.UNLOADED);
        assertEquals(Reason.UNLOADED_CHUNK, reasonOf(w, 0, 1, 0));
    }

    @Test
    void caixaMaisAltaQueOLimiteRecusa() {
        World w = new World();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                w.put(x, 0, z, RocketBlockKind.PAD);
            }
        }
        // ponte no topo de uma coluna de 257 blocos (altura 258 > 256)
        for (int y = 1; y <= 257; y++) {
            w.put(0, y, 0, RocketBlockKind.STRUCTURE);
        }
        w.put(0, 257, 0, RocketBlockKind.COCKPIT);
        assertEquals(Reason.TOO_LARGE, reasonOf(w, 0, 257, 0));
    }

    @Test
    void oTetoDeBlocosValeNaBusca() {
        // a busca para no teto de blocos (a mesma regra com um teto pequeno, só para o teste)
        World w = simpleShip();
        RocketAssembly.Report report = RocketAssembly.analyze(w, List.of(new int[] {0, 1, 0}), 2, 400_000, 128, 256, ShipAssembly.RULES);
        assertTrue(report.truncated());
        assertFalse(report.assemblable());
        assertEquals(Reason.TOO_LARGE, report.blockers().get(0).reason());
    }

    @Test
    void oTetoPadraoEhOsOitoMilBlocosDaNave() {
        assertEquals(8192, RocketAssembly.MAX_BLOCKS);
    }
}
