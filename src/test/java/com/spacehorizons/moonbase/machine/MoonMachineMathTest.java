package com.spacehorizons.moonbase.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.moonbase.energy.EnergyMath;
import com.spacehorizons.survival.fuel.CanisterFuel;
import com.spacehorizons.survival.oxygen.OxygenAmounts;

/** Conversões das máquinas da base lunar: eletrólise, refino, extração de hélio-3 e o progresso de uma tarefa. */
class MoonMachineMathTest {
    @Test
    void progressAdvancesOneTickAtATimeAndFinishesAtTheTotal() {
        int progress = 0;
        int ticks = 0;
        boolean done = false;
        while (!done) {
            MoonMachineMath.Step s = MoonMachineMath.advance(true, progress, MoonMachineMath.ELECTROLYSIS_TICKS);
            progress = s.progress();
            done = s.done();
            ticks++;
        }
        assertEquals(MoonMachineMath.ELECTROLYSIS_TICKS, ticks);
        assertEquals(0, progress, "zera ao terminar");
    }

    @Test
    void progressRecedesTwoPerTickWhenStalled() {
        assertEquals(8, MoonMachineMath.advance(false, 10, 100).progress());
        assertEquals(0, MoonMachineMath.advance(false, 1, 100).progress());
        assertFalse(MoonMachineMath.advance(false, 50, 100).done());
    }

    @Test
    void transferRespectsRateBufferAndSpace() {
        assertEquals(24, MoonMachineMath.transfer(1_200, 2_400, 24));
        assertEquals(10, MoonMachineMath.transfer(10, 2_400, 24));
        assertEquals(5, MoonMachineMath.transfer(1_200, 5, 24));
        assertEquals(0, MoonMachineMath.transfer(0, 5, 24));
        assertEquals(0, MoonMachineMath.transfer(100, 0, 24));
    }

    @Test
    void electrolysisOfOneIceFillsAHalfCanisterOfHydrogenAndAHalfSmallTankOfOxygen() {
        assertEquals(500, MoonMachineMath.ICE_H2_MB);
        assertEquals(OxygenAmounts.SMALL_TANK_CAPACITY / 2, MoonMachineMath.ICE_O2_UNITS);
        assertEquals(CanisterFuel.CAPACITY / 2, MoonMachineMath.ICE_H2_MB);
        // os reservatórios cabem 8 gelos de hidrogênio (4.000 mB) e 4 de oxigênio (4.800): o oxigênio trava primeiro, no quarto gelo
        int h2 = 0;
        int o2 = 0;
        int ice = 0;
        while (MoonMachineMath.canElectrolyze(h2, o2)) {
            h2 += MoonMachineMath.ICE_H2_MB;
            o2 += MoonMachineMath.ICE_O2_UNITS;
            ice++;
        }
        assertEquals(4, ice);
        assertEquals(4_800, o2);
    }

    @Test
    void refineryFillsARocketTankWithTenIceAndSixMinutesOfPower() {
        assertEquals(10, MoonMachineMath.iceForFuel(4_000));
        assertEquals(1, MoonMachineMath.iceForFuel(1));
        assertEquals(3, MoonMachineMath.iceForFuel(1_000));
        assertEquals(3_600, MoonMachineMath.energyPerRefinedIce());
        assertEquals(2_000, MoonMachineMath.energyPerElectrolyzedIce());
        // o buffer interno da refinaria é de 4.000 mB: 10 gelos exatamente
        int tank = 0;
        int ice = 0;
        while (MoonMachineMath.canRefine(tank)) {
            tank += MoonMachineMath.REFINERY_FUEL_PER_ICE_MB;
            ice++;
        }
        assertEquals(10, ice);
        assertEquals(4_000, tank);
        assertFalse(MoonMachineMath.canRefine(tank));
    }

    @Test
    void extractorBeatsTheClickAndAFourBlocksFillACanister() {
        assertEquals(250, MoonMachineMath.HELIUM3_PER_REGOLITH_MB);
        assertEquals(4, CanisterFuel.CAPACITY / MoonMachineMath.HELIUM3_PER_REGOLITH_MB);
        assertTrue(MoonMachineMath.HELIUM3_PER_REGOLITH_MB > com.spacehorizons.survival.fuel.FuelCanisterItem.HELIUM3_PER_BLOCK_MB,
                "a máquina rende mais que o clique, que continua valendo");
    }

    @Test
    void panelsNeededForTheMachinesAtNoon() {
        // a 8 UE/t por painel
        assertEquals(3, (int) Math.ceil(MoonMachineMath.ELECTROLYSIS_POWER / (double) EnergyMath.PANEL_OUTPUT_PER_TICK));
        assertEquals(4, (int) Math.ceil(MoonMachineMath.REFINERY_POWER / (double) EnergyMath.PANEL_OUTPUT_PER_TICK));
        assertEquals(2, (int) Math.ceil(MoonMachineMath.EXTRACTOR_POWER / (double) EnergyMath.PANEL_OUTPUT_PER_TICK));
        // a bateria cheia mantém a refinaria por 6.666 ticks (5,5 min)
        assertEquals(6_666, EnergyMath.BATTERY_CAPACITY / MoonMachineMath.REFINERY_POWER);
    }

    @Test
    void machineEnergyBufferFitsInAShortForTheScreenSync() {
        assertTrue(EnergyMath.MACHINE_CAPACITY <= Short.MAX_VALUE);
        assertTrue(MoonMachineMath.ELECTROLYZER_O2_CAPACITY <= Short.MAX_VALUE);
        assertTrue(MoonMachineMath.ELECTROLYZER_H2_CAPACITY <= Short.MAX_VALUE);
        assertTrue(MoonMachineMath.REFINERY_TANK_CAPACITY <= Short.MAX_VALUE);
    }

    @Test
    void everyMachineKindDeclaresItsSlotsConsistently() {
        for (MachineKind kind : MachineKind.values()) {
            for (int slot = 0; slot < MachineKind.SLOT_COUNT; slot++) {
                if (kind.slotIsOutput(slot)) {
                    assertTrue(kind.slotUsed(slot), kind + " slot de saída " + slot + " precisa estar em uso");
                }
            }
            if (!kind.hasMenu()) {
                for (int slot = 0; slot < MachineKind.SLOT_COUNT; slot++) {
                    assertFalse(kind.slotUsed(slot), kind + " sem menu não usa slots");
                }
            }
        }
        assertEquals(MachineKind.SOLAR_PANEL, MachineKind.byOrdinal(-1));
        assertEquals(MachineKind.LUNAR_REFINERY, MachineKind.byOrdinal(MachineKind.LUNAR_REFINERY.ordinal()));
        assertEquals(MachineStatus.IDLE, MachineStatus.byId(999));
    }
}
