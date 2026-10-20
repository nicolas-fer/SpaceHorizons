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
    void tetoDeExtensaoEDeIteracoesSaoRespeitados() throws Exception {
        World alto = simpleRocket();
        for (int y = 5; y < 70; y++) {
            alto.put(0, y, 0, RocketBlockKind.HULL);
        }
        assertEquals(Reason.TOO_LARGE, assertThrows(RocketException.class, () -> RocketAssembly.scan(alto, 0, 4, 0, 1024, 100_000, 64)).reason(),
                "mais de 64 blocos de altura num teto de 64");
        // o teto padrão é de 256 em altura e 128 de lado: 69 blocos de altura (y de 1 a 69) passam
        assertEquals(69, RocketAssembly.scan(alto, 0, 4, 0).sizeY(), "altura em blocos");

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

    /** Plataforma 4x4, motor 2x2x2 (y 1 a 2), coluna de tanques 2x2x4 (y 3 a 6) e cockpit em cima (y 7). */
    private static World structuredRocket() {
        World w = new World();
        for (int x = -1; x <= 2; x++) {
            for (int z = -1; z <= 2; z++) {
                w.put(x, 0, z, RocketBlockKind.PAD);
            }
        }
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                for (int y = 1; y <= 2; y++) {
                    w.put(x, y, z, RocketBlockKind.ENGINE);
                }
                for (int y = 3; y <= 6; y++) {
                    w.put(x, y, z, RocketBlockKind.FUEL_TANK);
                }
            }
        }
        return w.put(0, 7, 0, RocketBlockKind.COCKPIT);
    }

    @Test
    void censoDaMontagemContaAsEstruturasMultibloco() throws Exception {
        RocketAssembly.Result r = RocketAssembly.scan(structuredRocket(), 0, 7, 0);
        assertEquals(8, r.census().engines(), "blocos de motor");
        assertEquals(16, r.census().tanks(), "blocos de tanque");
        assertEquals(java.util.List.of(new Multiblocks.Box(0, 1, 0, 2, 2, 2)), r.census().engineStructures());
        assertEquals(java.util.List.of(new Multiblocks.Box(0, 3, 0, 2, 4, 2)), r.census().tankStructures());
        assertEquals(0, r.census().soloEngines());
        assertEquals(0, r.census().looseTanks());
        assertEquals(r.census(), RocketAssembly.analyze(structuredRocket(), java.util.List.of(new int[] {0, 7, 0})).result().census(),
                "o painel (analyze) e a montagem (scan) contam igual");
        // os números saem do mesmo censo
        var stats = RocketSpecs.statsFor(r.census());
        assertEquals(24.0, stats.thrust(), 1.0E-9);
        assertEquals(16 * 4000.0, stats.fuelCapacityMb(), 1.0E-9);
    }

    @Test
    void analyzeJuntaTodosOsProblemasEScanSoLancaOPrimeiro() {
        // sem paraquedas não impede; mas terreno, bloco proibido e dois cockpits somam problemas
        World w = structuredRocket().put(0, 8, 0, RocketBlockKind.COCKPIT).put(2, 3, 0, RocketBlockKind.TERRAIN)
                .put(-1, 4, 0, RocketBlockKind.FORBIDDEN);
        RocketAssembly.Report report = RocketAssembly.analyze(w, java.util.List.of(new int[] {0, 7, 0}));
        assertFalse(report.assemblable());
        java.util.List<Reason> reasons = report.blockers().stream().map(RocketAssembly.Blocker::reason).toList();
        assertTrue(reasons.contains(Reason.TOUCHES_TERRAIN) && reasons.contains(Reason.FORBIDDEN_BLOCK) && reasons.contains(Reason.MULTIPLE_COCKPITS),
                reasons.toString());
        RocketAssembly.Blocker terrain = report.blockers().stream().filter(b -> b.reason() == Reason.TOUCHES_TERRAIN).findFirst().orElseThrow();
        assertEquals(java.util.List.of(2, 3, 0), java.util.List.of(terrain.x(), terrain.y(), terrain.z()), "a posição do bloco de terreno");
        assertEquals(report.blockers().get(0).reason(), reasonOf(w, 0, 7, 0), "scan recusa pelo primeiro problema que o analyze achou");
    }

    @Test
    void analyzeSemCockpitAindaContaOQueAchou() {
        World w = structuredRocket().put(0, 7, 0, RocketBlockKind.HULL);
        RocketAssembly.Report report = RocketAssembly.analyze(w, java.util.List.of(new int[] {0, 6, 0}));
        assertEquals(0, report.result().census().cockpits());
        assertTrue(report.blockers().stream().anyMatch(b -> b.reason() == Reason.NO_COCKPIT));
        assertEquals(8, report.result().census().engines());
        assertEquals(1, report.result().census().hulls());
    }

    @Test
    void foguetesGrandesPassamDe1024BlocosAte8192() throws Exception {
        // casco 20x20x24 oco (parede de 1 bloco) ~ 3.100 blocos: antes o teto era 1.024
        World w = new World();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                w.put(x, 0, z, RocketBlockKind.PAD);
            }
        }
        w.put(0, 1, 0, RocketBlockKind.ENGINE);
        int blocks = 1;
        for (int y = 2; y <= 25; y++) {
            for (int x = -10; x < 10; x++) {
                for (int z = -10; z < 10; z++) {
                    boolean shell = x == -10 || x == 9 || z == -10 || z == 9 || y == 2 || y == 25;
                    if (shell) {
                        w.put(x, y, z, RocketBlockKind.HULL);
                        blocks++;
                    }
                }
            }
        }
        w.put(0, 2, 0, RocketBlockKind.FUEL_TANK).put(0, 3, 0, RocketBlockKind.COCKPIT);
        w.put(0, 4, 0, RocketBlockKind.AIR);
        // o motor e o tanque ligam ao casco pelo piso (y=2): tudo é um corpo só
        RocketAssembly.Result r = RocketAssembly.scan(w, 0, 3, 0);
        assertTrue(r.cells().size() > 1024, "mais de 1.024 blocos: " + r.cells().size());
        assertTrue(r.cells().size() <= RocketAssembly.MAX_BLOCKS);
        assertEquals(20, r.sizeX());
        assertEquals(25, r.sizeY());
        assertTrue(blocks > 1024);
    }

    @Test
    void tetosDaMontagemSaoOsDaNaveDaD2() {
        assertEquals(8192, RocketAssembly.MAX_BLOCKS);
        assertEquals(128, RocketAssembly.MAX_EXTENT_XZ);
        assertEquals(256, RocketAssembly.MAX_EXTENT_Y);
        assertEquals(128 * 128 * 256, PortraitGrid.MAX_VOLUME);
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
