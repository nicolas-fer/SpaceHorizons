package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.RocketException.Reason;

/** Busca em largura e validação da montagem, sobre uma grade de papéis (sem o jogo). */
class RocketAssemblyTest {
    /** Mundo de brinquedo: tudo é ar, menos o que foi posto. */
    private static final class World implements RocketAssembly.Grid {
        private final Map<Long, RocketBlockKind> blocks = new HashMap<>();

        World put(int x, int y, int z, RocketBlockKind kind) {
            this.blocks.put(RocketAssembly.pack(x, y, z), kind);
            return this;
        }

        @Override
        public RocketBlockKind kindAt(int x, int y, int z) {
            return this.blocks.getOrDefault(RocketAssembly.pack(x, y, z), RocketBlockKind.AIR);
        }
    }

    /** Plataforma 3x3 em y=0 (centro em 0,0) e, em cima dela, motor, tanque, tanque e cockpit na coluna central. */
    private static World simpleRocket() {
        World w = new World();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                w.put(x, 0, z, RocketBlockKind.PAD);
            }
        }
        return w.put(0, 1, 0, RocketBlockKind.ENGINE).put(0, 2, 0, RocketBlockKind.FUEL_TANK)
                .put(0, 3, 0, RocketBlockKind.FUEL_TANK).put(0, 4, 0, RocketBlockKind.COCKPIT);
    }

    private static Reason reasonOf(RocketAssembly.Grid grid, int x, int y, int z) {
        return assertThrows(RocketException.class, () -> RocketAssembly.scan(grid, x, y, z)).reason();
    }

    @Test
    void foguetePequenoEhAchadoInteiroComSuaCaixaEContagem() throws Exception {
        RocketAssembly.Result r = RocketAssembly.scan(simpleRocket(), 0, 4, 0);
        assertEquals(4, r.cells().size());
        assertEquals(0, r.cells().get(0).x(), "o cockpit é o primeiro");
        assertEquals(RocketBlockKind.COCKPIT, r.cells().get(0).kind());
        assertEquals(1, r.sizeX());
        assertEquals(4, r.sizeY());
        assertEquals(1, r.sizeZ());
        assertEquals(1, r.minY());
        assertEquals(4, r.maxY());
        assertEquals(new RocketCensus(1, 1, 2, 0, 0, 0, 0), r.census());
        assertEquals(1, r.padContacts(), "só o motor (em y=1) encosta na plataforma, por baixo");
    }

    @Test
    void aBuscaParaNaPlataformaENoArEIgnoraOQueSoToucaNaDiagonal() throws Exception {
        World w = simpleRocket();
        w.put(1, 2, 1, RocketBlockKind.HULL);   // só encosta na diagonal do tanque: não é ligado por face
        w.put(5, 5, 5, RocketBlockKind.HULL);   // solto no ar
        RocketAssembly.Result r = RocketAssembly.scan(w, 0, 4, 0);
        assertEquals(4, r.cells().size());
        assertTrue(r.cells().stream().noneMatch(c -> c.kind() == RocketBlockKind.PAD));
        assertEquals(0, r.census().hulls());
    }

    @Test
    void blocosLigadosPorFaceEntramComoCascoEstruturaEPernas() throws Exception {
        World w = simpleRocket();
        w.put(1, 2, 0, RocketBlockKind.HULL).put(-1, 2, 0, RocketBlockKind.STRUCTURE).put(1, 1, 0, RocketBlockKind.LEG)
                .put(0, 5, 0, RocketBlockKind.PARACHUTE);
        RocketAssembly.Result r = RocketAssembly.scan(w, 0, 4, 0);
        assertEquals(8, r.cells().size());
        assertEquals(new RocketCensus(1, 1, 2, 1, 1, 1, 1), r.census());
        assertEquals(3, r.sizeX());
        assertEquals(5, r.sizeY());
        assertEquals(2, r.padContacts(), "o motor e a perna encostam na plataforma");
    }

    @Test
    void comecarForaDeUmCockpitEhRecusado() {
        assertEquals(Reason.NO_COCKPIT, reasonOf(simpleRocket(), 0, 3, 0));
        assertEquals(Reason.NO_COCKPIT, reasonOf(simpleRocket(), 9, 9, 9));
    }

    @Test
    void semMotorSemTanqueSemPlataformaOuComDoisCockpitsEhRecusado() {
        World semMotor = simpleRocket().put(0, 1, 0, RocketBlockKind.HULL);
        assertEquals(Reason.NO_ENGINE, reasonOf(semMotor, 0, 4, 0));

        World semTanque = simpleRocket().put(0, 2, 0, RocketBlockKind.HULL).put(0, 3, 0, RocketBlockKind.HULL);
        assertEquals(Reason.NO_TANK, reasonOf(semTanque, 0, 4, 0));

        World semPlataforma = simpleRocket();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                semPlataforma.put(x, 0, z, RocketBlockKind.AIR);
            }
        }
        assertEquals(Reason.NO_PAD, reasonOf(semPlataforma, 0, 4, 0));

        World doisCockpits = simpleRocket().put(0, 5, 0, RocketBlockKind.COCKPIT);
        assertEquals(Reason.MULTIPLE_COCKPITS, reasonOf(doisCockpits, 0, 4, 0));
    }

    @Test
    void encostarNoTerrenoEhRecusadoComAPosicaoDoBloco() {
        World w = simpleRocket().put(1, 2, 0, RocketBlockKind.HULL).put(2, 2, 0, RocketBlockKind.TERRAIN);
        RocketException e = assertThrows(RocketException.class, () -> RocketAssembly.scan(w, 0, 4, 0));
        assertEquals(Reason.TOUCHES_TERRAIN, e.reason());
        assertEquals(java.util.List.of(2, 2, 0), java.util.Arrays.asList(e.args()));
    }

    @Test
    void blocoProibidoEChunkNaoCarregadoSaoRecusados() {
        assertEquals(Reason.FORBIDDEN_BLOCK, reasonOf(simpleRocket().put(1, 3, 0, RocketBlockKind.FORBIDDEN), 0, 4, 0));
        assertEquals(Reason.UNLOADED_CHUNK, reasonOf(simpleRocket().put(0, 5, 0, RocketBlockKind.UNLOADED), 0, 4, 0));
    }

    @Test
    void terrenoTocadoPorUmaPernaTambemEhRecusadoMesmoComPlataforma() {
        // a perna encosta na plataforma E num bloco de terra ao lado: a plataforma é permitida, o terreno não
        World w = simpleRocket().put(1, 1, 0, RocketBlockKind.LEG).put(2, 1, 0, RocketBlockKind.TERRAIN);
        assertEquals(Reason.TOUCHES_TERRAIN, reasonOf(w, 0, 4, 0));
    }

    @Test
    void tetoDeBlocosEhRespeitado() {
        World w = simpleRocket();
        for (int y = 5; y < 30; y++) {
            w.put(0, y, 0, RocketBlockKind.HULL);
        }
        RocketException e = assertThrows(RocketException.class, () -> RocketAssembly.scan(w, 0, 4, 0, 10, 100_000, 64));
        assertEquals(Reason.TOO_LARGE, e.reason());
        assertDoesNotThrowWith(w, 0, 4, 0, 100, 100_000, 64);
    }

    private static void assertDoesNotThrowWith(World w, int x, int y, int z, int maxBlocks, int maxIterations, int maxExtent) {
        try {
            RocketAssembly.Result r = RocketAssembly.scan(w, x, y, z, maxBlocks, maxIterations, maxExtent);
            assertEquals(4 + 25, r.cells().size());
        } catch (RocketException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void tetoDeExtensaoEDeIteracoesSaoRespeitados() {
        World alto = simpleRocket();
        for (int y = 5; y < 70; y++) {
            alto.put(0, y, 0, RocketBlockKind.HULL);
        }
        assertEquals(Reason.TOO_LARGE, reasonOf(alto, 0, 4, 0), "mais de 64 blocos de altura");

        assertEquals(Reason.TOO_LARGE, assertThrows(RocketException.class,
                () -> RocketAssembly.scan(simpleRocket(), 0, 4, 0, 1024, 3, 64)).reason(), "poucas iterações");
    }

    @Test
    void foguetePartidoEmDoisPeloArFicaSoComOLadoDoCockpit() throws Exception {
        World w = simpleRocket();
        w.put(0, 2, 0, RocketBlockKind.AIR);   // abre um buraco: o motor e o tanque de baixo ficam de fora
        // sobra o tanque de cima + cockpit, sem motor
        assertEquals(Reason.NO_ENGINE, reasonOf(w, 0, 4, 0));
        assertFalse(w.kindAt(0, 2, 0).isPart());
    }

    @Test
    void empacotamentoDeCoordenadasNaoColideEntreVizinhos() {
        java.util.Set<Long> vistos = new java.util.HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    assertTrue(vistos.add(RocketAssembly.pack(x, y, z)), "colisão em " + x + "," + y + "," + z);
                }
            }
        }
        assertEquals(net.minecraft.core.BlockPos.asLong(5, -7, 9), RocketAssembly.pack(5, -7, 9));
    }
}
