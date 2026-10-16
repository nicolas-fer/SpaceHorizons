package com.spacehorizons.survival.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.atmosphere.AtmosphereModel;
import com.spacehorizons.survival.compressor.CompressorLogic.Status;
import com.spacehorizons.survival.oxygen.OxygenAmounts;
import com.spacehorizons.survival.oxygen.OxygenMath;

/**
 * Lógica pura do compressor: enche só com ar, não passa da capacidade, queima combustível só ao comprimir e não
 * queima sem tanque, com tanque cheio, no vácuo ou sem o que queimar.
 */
class CompressorLogicTest {
    private static final int SMALL = OxygenAmounts.SMALL_TANK_CAPACITY;
    private static final int LARGE = OxygenAmounts.LARGE_TANK_CAPACITY;
    /** Tempo de queima do carvão no vanilla (80 s). */
    private static final int COAL = 1_600;

    /** Resultado de rodar o compressor por vários ticks sobre um tanque que acumula o que o compressor põe. */
    private record Run(int amount, int fuelItems, int workingTicks, int ticks, Status last) {
    }

    /**
     * Roda até {@code maxTicks}, parando quando o compressor não está mais trabalhando e não há mais o que mudar. O
     * tanque começa com {@code amount}; o combustível é um estoque de {@code coals} itens de {@code burn} ticks de queima
     * (o item só é lido quando a lógica pede para acender, como no block entity).
     */
    private static Run run(CompressorLogic logic, boolean breathable, boolean hasTank, int amount, int capacity, int coals, int burn,
            int maxTicks) {
        int fuelItems = 0;
        int working = 0;
        int ticks = 0;
        Status last = logic.status();
        for (; ticks < maxTicks; ticks++) {
            int available = coals - fuelItems;
            CompressorLogic.Result r = logic.tick(
                    new CompressorLogic.Inputs(breathable, hasTank, amount, capacity, available > 0 ? burn : 0));
            last = r.status();
            if (r.consumeFuelItem()) {
                fuelItems++;
            }
            if (r.status() == Status.WORKING) {
                working++;
            }
            amount += r.fill();
            if (r.status() != Status.WORKING) {
                ticks++;
                break;
            }
        }
        return new Run(amount, fuelItems, working, ticks, last);
    }

    // ---- enche só com ar ----

    @Test
    void enchaOTanquePequenoEm200TicksComUmCarvao() {
        Run run = run(new CompressorLogic(), true, true, 0, SMALL, 5, COAL, 10_000);
        assertEquals(SMALL, run.amount());
        assertEquals(200, run.workingTicks(), "2.400 a 12 por tick = 200 ticks = 10 s");
        assertEquals(CompressorLogic.ticksToFill(SMALL), run.workingTicks());
        assertEquals(1, run.fuelItems());
        assertEquals(Status.FULL, run.last());
    }

    @Test
    void enchaOTanqueGrandeEm600Ticks() {
        Run run = run(new CompressorLogic(), true, true, 0, LARGE, 5, COAL, 10_000);
        assertEquals(LARGE, run.amount());
        assertEquals(600, run.workingTicks(), "7.200 a 12 por tick = 600 ticks = 30 s");
        assertEquals(1, run.fuelItems());
    }

    @Test
    void noVacuoNaoEnche() {
        Run run = run(new CompressorLogic(), false, true, 0, SMALL, 5, COAL, 500);
        assertEquals(0, run.amount());
        assertEquals(0, run.workingTicks());
        assertEquals(Status.NO_AIR, run.last());
    }

    @Test
    void noVacuoNaoQueimaCombustivelNemAcende() {
        CompressorLogic logic = new CompressorLogic();
        for (int i = 0; i < 300; i++) {
            CompressorLogic.Result r = logic.tick(new CompressorLogic.Inputs(false, true, 0, SMALL, COAL));
            assertEquals(0, r.fill());
            assertFalse(r.consumeFuelItem());
            assertFalse(r.lit());
        }
        assertEquals(0, logic.litTime());
    }

    @Test
    void semArAFaltaDeArVenceAFaltaDeTanque() {
        // a mensagem útil no vácuo é "sem ar", mesmo com o slot vazio
        CompressorLogic logic = new CompressorLogic();
        assertEquals(Status.NO_AIR, logic.tick(new CompressorLogic.Inputs(false, false, 0, 0, 0)).status());
    }

    // ---- não passa da capacidade ----

    @Test
    void noUltimoTickSoEntraOQueFalta() {
        CompressorLogic logic = new CompressorLogic();
        // faltam 5 de oxigênio: menos que a taxa de 12 por tick
        CompressorLogic.Result r = logic.tick(new CompressorLogic.Inputs(true, true, SMALL - 5, SMALL, COAL));
        assertEquals(5, r.fill());
        assertEquals(Status.WORKING, r.status());
        CompressorLogic.Result after = logic.tick(new CompressorLogic.Inputs(true, true, SMALL, SMALL, COAL));
        assertEquals(0, after.fill());
        assertEquals(Status.FULL, after.status());
    }

    @Test
    void nuncaPassaDaCapacidadeMesmoComTanqueCarregadoAcimaDela() {
        CompressorLogic logic = new CompressorLogic();
        // dado salvo inconsistente (acima da capacidade): conta como cheio, não como espaço negativo
        CompressorLogic.Result r = logic.tick(new CompressorLogic.Inputs(true, true, SMALL + 500, SMALL, COAL));
        assertEquals(0, r.fill());
        assertEquals(Status.FULL, r.status());
    }

    @Test
    void tanqueMeioCheioSoRecebeAMetade() {
        Run run = run(new CompressorLogic(), true, true, SMALL / 2, SMALL, 5, COAL, 10_000);
        assertEquals(SMALL, run.amount());
        assertEquals(100, run.workingTicks());
    }

    // ---- combustível ----

    @Test
    void oCarvaoRendeOitoTanquesPequenosEmeioSemDesperdicio() {
        CompressorLogic logic = new CompressorLogic();
        int fuelItems = 0;
        int filled = 0;
        // 8 tanques pequenos = 19.200 de oxigênio = 1.600 ticks de funcionamento = exatamente 1 carvão
        for (int tank = 0; tank < 8; tank++) {
            Run run = run(logic, true, true, 0, SMALL, 99, COAL, 10_000);
            fuelItems += run.fuelItems();
            filled += run.amount();
        }
        assertEquals(1, fuelItems, "1.600 ticks de queima cobrem 8 tanques de 200 ticks");
        assertEquals(8 * SMALL, filled);
        assertEquals(0, logic.litTime(), "o carvão acabou exatamente no fim do oitavo tanque");
        // o nono tanque precisa de outro carvão
        Run ninth = run(logic, true, true, 0, SMALL, 99, COAL, 10_000);
        assertEquals(1, ninth.fuelItems());
    }

    @Test
    void comCombustivelCurtoReacendeNoMesmoTickSemParar() {
        CompressorLogic logic = new CompressorLogic();
        // item de 150 ticks de queima, tanque grande de 600 ticks: precisa de 4 itens, sem tick parado no meio
        Run run = run(logic, true, true, 0, LARGE, 99, 150, 10_000);
        assertEquals(LARGE, run.amount());
        assertEquals(4, run.fuelItems());
        assertEquals(600, run.workingTicks());
        assertEquals(600, run.ticks() - 1, "um tick a mais só para perceber que está cheio");
    }

    @Test
    void naoQueimaSemTanque() {
        CompressorLogic logic = new CompressorLogic();
        for (int i = 0; i < 200; i++) {
            CompressorLogic.Result r = logic.tick(new CompressorLogic.Inputs(true, false, 0, 0, COAL));
            assertFalse(r.consumeFuelItem());
            assertEquals(Status.NO_TANK, r.status());
            assertFalse(r.lit());
        }
        assertEquals(0, logic.litTime());
    }

    @Test
    void naoQueimaComTanqueCheio() {
        CompressorLogic logic = new CompressorLogic();
        for (int i = 0; i < 200; i++) {
            CompressorLogic.Result r = logic.tick(new CompressorLogic.Inputs(true, true, SMALL, SMALL, COAL));
            assertFalse(r.consumeFuelItem());
            assertEquals(Status.FULL, r.status());
        }
        assertEquals(0, logic.litTime());
    }

    @Test
    void semCombustivelNaoEnche() {
        CompressorLogic logic = new CompressorLogic();
        CompressorLogic.Result r = logic.tick(new CompressorLogic.Inputs(true, true, 0, SMALL, 0));
        assertEquals(0, r.fill());
        assertEquals(Status.NO_FUEL, r.status());
        assertFalse(r.lit());
    }

    @Test
    void aQueimaParaQuandoOCompressorNaoTrabalha() {
        CompressorLogic logic = new CompressorLogic();
        // acende e comprime 50 ticks
        for (int i = 0; i < 50; i++) {
            logic.tick(new CompressorLogic.Inputs(true, true, i * 12, SMALL, COAL));
        }
        assertEquals(COAL - 50, logic.litTime());
        // 500 ticks parado (sem tanque, no vácuo, tanque cheio): a queima não anda
        for (int i = 0; i < 100; i++) {
            logic.tick(new CompressorLogic.Inputs(true, false, 0, 0, 0));
            logic.tick(new CompressorLogic.Inputs(false, true, 0, SMALL, 0));
            logic.tick(new CompressorLogic.Inputs(true, true, SMALL, SMALL, 0));
            logic.tick(new CompressorLogic.Inputs(true, true, SMALL, SMALL, COAL));
            logic.tick(new CompressorLogic.Inputs(true, true, SMALL, SMALL, 0));
        }
        assertEquals(COAL - 50, logic.litTime(), "não gasta o carvão que já está aceso enquanto não comprime");
        // e continua de onde parou, sem acender outro item, quando há tanque de novo e nem há mais item no slot
        CompressorLogic.Result r = logic.tick(new CompressorLogic.Inputs(true, true, 0, SMALL, 0));
        assertEquals(Status.WORKING, r.status());
        assertFalse(r.consumeFuelItem());
        assertEquals(COAL - 51, logic.litTime());
    }

    // ---- situação e avisos de mudança ----

    @Test
    void changedAvisaQuandoASituacaoMudaOuAcende() {
        CompressorLogic logic = new CompressorLogic();
        CompressorLogic.Inputs working = new CompressorLogic.Inputs(true, true, 0, SMALL, COAL);
        CompressorLogic.Result first = logic.tick(working);
        assertTrue(first.changed(), "NO_TANK inicial para WORKING, e acendeu um item");
        assertTrue(first.consumeFuelItem());
        CompressorLogic.Result second = logic.tick(working);
        assertFalse(second.changed(), "continuar comprimindo não é mudança (o enchimento é salvo à parte)");
        CompressorLogic.Result stop = logic.tick(new CompressorLogic.Inputs(true, true, SMALL, SMALL, 0));
        assertTrue(stop.changed(), "WORKING para FULL apaga o bloco");
        assertFalse(stop.lit());
    }

    @Test
    void idsDeSituacaoParaATelaSaoEstaveis() {
        for (Status s : Status.values()) {
            assertEquals(s, Status.byId(s.ordinal()));
        }
        assertEquals(Status.NO_TANK, Status.byId(-1));
        assertEquals(Status.NO_TANK, Status.byId(99));
    }

    @Test
    void estadoCarregadoFicaDentroDosLimites() {
        CompressorLogic logic = new CompressorLogic();
        logic.load(-5, -9);
        assertEquals(0, logic.litTime());
        assertEquals(0, logic.litTotal());
        logic.load(1_000, 1_600);
        assertEquals(1_000, logic.litTime());
        assertEquals(1_600, logic.litTotal());
    }

    // ---- ar respirável ----

    @Test
    void naTerraOArEhRespiravelAteOTopoDaAtmosfera() {
        Optional<PlanetDefinition> earth = Optional.of(Planets.EARTH);
        int sea = Planets.EARTH.seaLevel();
        assertTrue(CompressorLogic.isBreathable(earth, sea));
        assertTrue(CompressorLogic.isBreathable(earth, 120));
        assertTrue(CompressorLogic.isBreathable(earth, sea + 1_000));
        // blocos na última altura de ar (a conta usa o centro do bloco)
        assertTrue(CompressorLogic.isBreathable(earth, sea + (int) AtmosphereModel.EARTH_THICKNESS - 1));
        assertFalse(CompressorLogic.isBreathable(earth, sea + (int) AtmosphereModel.EARTH_THICKNESS));
        assertFalse(CompressorLogic.isBreathable(earth, 1_900), "acima de 1.863 é vácuo na Terra de teste");
        assertFalse(CompressorLogic.isBreathable(earth, 15_000));
    }

    @Test
    void naLuaNuncaEhRespiravel() {
        Optional<PlanetDefinition> moon = Optional.of(Planets.MOON);
        assertFalse(CompressorLogic.isBreathable(moon, Planets.MOON.seaLevel()));
        assertFalse(CompressorLogic.isBreathable(moon, 0));
        assertFalse(CompressorLogic.isBreathable(moon, 200));
    }

    @Test
    void foraDosPlanetasConhecidosOArEhRespiravel() {
        assertTrue(CompressorLogic.isBreathable(Optional.empty(), 64));
        assertTrue(CompressorLogic.isBreathable(Optional.empty(), 5_000));
    }

    @Test
    void omesmoCriterioDeVacuoDoJogador() {
        // o compressor usa o MESMO OxygenMath.isVacuum do sistema de oxigênio, só contado do centro do bloco
        for (int y = -20; y < 2_500; y += 37) {
            assertEquals(!OxygenMath.isVacuum(Planets.EARTH, y + 0.5), CompressorLogic.isBreathable(Optional.of(Planets.EARTH), y),
                    "y=" + y);
        }
    }
}
