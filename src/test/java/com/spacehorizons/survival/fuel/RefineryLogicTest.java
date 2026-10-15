package com.spacehorizons.survival.fuel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonPrimitive;

/** Lógica pura da refinaria e do galão: tanque, queima, progresso, transferência e limites do componente. */
class RefineryLogicTest {
    /** Carvão: 250 mB em 100 ticks, com combustível de fornalha de 1600 ticks no slot. */
    private static final RefineryLogic.Inputs COAL = new RefineryLogic.Inputs(true, 250, 100, 1600);

    /** Roda {@code n} ticks com a mesma situação de slots; devolve quantas entradas e combustíveis foram consumidos. */
    private static int[] run(RefineryLogic logic, RefineryLogic.Inputs in, int n) {
        int inputs = 0;
        int fuels = 0;
        for (int i = 0; i < n; i++) {
            RefineryLogic.Result r = logic.tick(in);
            if (r.consumeInputItem()) {
                inputs++;
            }
            if (r.consumeFuelItem()) {
                fuels++;
            }
        }
        return new int[] {inputs, fuels};
    }

    @Test
    void enchePeloProcessamento() {
        RefineryLogic logic = new RefineryLogic();
        // o primeiro tick acende (consome o combustível) e já conta progresso: a 100a unidade fecha no tick 100
        int[] used = run(logic, COAL, 100);
        assertEquals(250, logic.tank());
        assertEquals(1, used[0]);
        assertEquals(1, used[1]);
        assertTrue(logic.isLit());
        assertEquals(0, logic.progress());
    }

    @Test
    void naoPassaDaCapacidadeDoTanque() {
        RefineryLogic logic = new RefineryLogic();
        // 16 carvões enchem 4000 mB; a partir daí não processa mais
        int[] used = run(logic, COAL, 100 * 40);
        assertEquals(RefineryLogic.TANK_CAPACITY, logic.tank());
        assertEquals(16, used[0]);
    }

    @Test
    void naoProcessaSeASaidaNaoCabeNoTanque() {
        RefineryLogic logic = new RefineryLogic();
        logic.load(RefineryLogic.TANK_CAPACITY - 100, 0, 0, 0, 0);
        int[] used = run(logic, COAL, 300);
        assertEquals(0, used[0], "250 mB não cabem em 100 mB livres");
        assertEquals(0, used[1], "sem espaço, nem gasta combustível de fornalha");
        assertEquals(RefineryLogic.TANK_CAPACITY - 100, logic.tank());
    }

    @Test
    void naoProcessaSemCombustivelDeFornalha() {
        RefineryLogic logic = new RefineryLogic();
        RefineryLogic.Inputs semFornalha = new RefineryLogic.Inputs(true, 250, 100, 0);
        int[] used = run(logic, semFornalha, 500);
        assertEquals(0, used[0]);
        assertEquals(0, logic.tank());
        assertEquals(0, logic.progress());
        assertFalse(logic.isLit());
    }

    @Test
    void naoProcessaSemReceita() {
        RefineryLogic logic = new RefineryLogic();
        int[] used = run(logic, new RefineryLogic.Inputs(false, 0, 0, 1600), 300);
        assertEquals(0, used[0]);
        assertEquals(0, used[1], "sem entrada válida não acende");
        assertEquals(0, logic.tank());
    }

    @Test
    void queimaCombustivelEReacendeSemPiscar() {
        RefineryLogic logic = new RefineryLogic();
        // fornalha com item de 150 ticks de queima, processos de 100 ticks: 450 ticks => 3 itens de combustível
        RefineryLogic.Inputs in = new RefineryLogic.Inputs(true, 250, 100, 150);
        boolean apagouNoMeio = false;
        int fuels = 0;
        int inputs = 0;
        for (int i = 0; i < 450 && logic.tank() + 250 <= RefineryLogic.TANK_CAPACITY; i++) {
            RefineryLogic.Result r = logic.tick(in);
            fuels += r.consumeFuelItem() ? 1 : 0;
            inputs += r.consumeInputItem() ? 1 : 0;
            apagouNoMeio |= !r.lit();
        }
        assertFalse(apagouNoMeio, "ao fim de um item de combustível o próximo acende no mesmo tick");
        assertEquals(3, fuels);
        assertEquals(4, inputs);
        assertEquals(1000, logic.tank());
    }

    @Test
    void queimaCaiUmPorTickEApagaQuandoNaoHaMais() {
        RefineryLogic logic = new RefineryLogic();
        logic.tick(new RefineryLogic.Inputs(true, 250, 100, 10));
        assertEquals(10, logic.litTime());
        // sem entrada: só queima o que resta
        for (int i = 0; i < 9; i++) {
            logic.tick(RefineryLogic.Inputs.NONE);
        }
        assertEquals(1, logic.litTime());
        RefineryLogic.Result last = logic.tick(RefineryLogic.Inputs.NONE);
        assertFalse(last.lit());
        assertTrue(last.changed(), "mudar de aceso para apagado conta como mudança (atualiza o bloco)");
    }

    @Test
    void progressoRecuaSemCombustivelOuEspaco() {
        RefineryLogic logic = new RefineryLogic();
        run(logic, new RefineryLogic.Inputs(true, 250, 100, 20), 20);
        assertEquals(20, logic.progress(), "20 ticks de queima: o primeiro acende e já conta");
        // acabou a queima e não há mais combustível: recua de 2 em 2
        RefineryLogic.Inputs semFornalha = new RefineryLogic.Inputs(true, 250, 100, 0);
        logic.tick(semFornalha);
        logic.tick(semFornalha);
        assertEquals(16, logic.progress());
    }

    @Test
    void trocarDeReceitaReiniciaOProgresso() {
        RefineryLogic logic = new RefineryLogic();
        run(logic, COAL, 50);
        assertEquals(50, logic.progress());
        logic.tick(new RefineryLogic.Inputs(true, 50, 40, 1600));
        assertEquals(1, logic.progress(), "outra duração: recomeça (e já conta este tick)");
        assertEquals(40, logic.processTotal());
    }

    @Test
    void enchePeloMetodoFillSemPassarDaCapacidade() {
        RefineryLogic logic = new RefineryLogic();
        assertEquals(3000, logic.fill(3000));
        assertEquals(1000, logic.fill(5000), "só cabem 1000 mB");
        assertEquals(RefineryLogic.TANK_CAPACITY, logic.tank());
        assertEquals(0, logic.fill(1));
        assertEquals(0, logic.fill(-5), "negativo não tira nada");
        assertEquals(1500, logic.drain(1500));
        assertEquals(2500, logic.drain(99999));
        assertEquals(0, logic.tank());
    }

    @Test
    void transfereParaOGalaoPorTickAteEncher() {
        RefineryLogic logic = new RefineryLogic();
        logic.fill(1500);
        int canister = 0;
        int ticks = 0;
        while (true) {
            int moved = logic.transferToCanister(canister);
            if (moved == 0) {
                break;
            }
            assertTrue(moved <= RefineryLogic.TRANSFER_PER_TICK);
            canister += moved;
            ticks++;
        }
        assertEquals(CanisterFuel.CAPACITY, canister, "o galão enche até 1000 e para");
        assertEquals(100, ticks, "1000 mB a 10 mB por tick");
        assertEquals(500, logic.tank(), "o resto fica no tanque");
    }

    @Test
    void transfereSoOQueHaNoTanque() {
        RefineryLogic logic = new RefineryLogic();
        logic.fill(4);
        assertEquals(4, logic.transferToCanister(0));
        assertEquals(0, logic.transferToCanister(0));
        // galão quase cheio: só o que falta
        logic.fill(100);
        assertEquals(3, logic.transferToCanister(CanisterFuel.CAPACITY - 3));
    }

    @Test
    void estadoCarregadoFicaDentroDosLimites() {
        RefineryLogic logic = new RefineryLogic();
        logic.load(99999, -5, -1, -3, -9);
        assertEquals(RefineryLogic.TANK_CAPACITY, logic.tank());
        assertEquals(0, logic.litTime());
        assertEquals(0, logic.progress());
    }

    // ---- data component do galão ----

    @Test
    void galaoLimitaAQuantidade() {
        assertEquals(0, CanisterFuel.clamp(-10));
        assertEquals(1000, CanisterFuel.clamp(5000));
        assertEquals(1000, CanisterFuel.add(900, 500));
        assertEquals(0, CanisterFuel.add(100, -500));
        assertEquals(700, CanisterFuel.add(400, 300));
        assertEquals(600, CanisterFuel.space(400));
        assertEquals(0, CanisterFuel.space(1000));
        assertEquals(0.5F, CanisterFuel.fraction(500), 1e-6);
    }

    @Test
    void barraDoGalaoUsaEscalaDe13Pixels() {
        assertEquals(0, CanisterFuel.barWidth(0));
        assertEquals(13, CanisterFuel.barWidth(1000));
        assertEquals(7, CanisterFuel.barWidth(500));
        assertEquals(13, CanisterFuel.barWidth(9999), "acima do máximo é cortado");
    }

    @Test
    void codecDoComponenteRecusaForaDosLimites() {
        assertTrue(CanisterFuel.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(0)).result().isPresent());
        assertTrue(CanisterFuel.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(1000)).result().isPresent());
        assertFalse(CanisterFuel.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(1001)).result().isPresent());
        assertFalse(CanisterFuel.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(-1)).result().isPresent());
    }
}
