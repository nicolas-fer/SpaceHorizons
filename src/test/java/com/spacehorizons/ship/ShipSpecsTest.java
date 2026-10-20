package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.flight.RocketStats;

/** RocketStats de uma nave a partir da contagem de blocos do censo (o espelho de RocketSpecsTest). */
class ShipSpecsTest {
    private static final double EPS = 1.0E-9;

    @Test
    void naveMinimaTemAsMassasDoDesenho() {
        // ponte 2,0 t + motor 1,5 t + tanque 0,3 t
        RocketStats s = ShipSpecs.statsFor(new ShipCensus(1, 1, 1, 0, 0, 0, 0));
        assertEquals(3.8, s.dryMassT(), EPS);
        assertEquals(8.0, s.thrust(), EPS);
        assertEquals(1000.0, s.ispTicks(), EPS);
        assertEquals(4000.0, s.fuelCapacityMb(), EPS);
        assertEquals(80.0, s.exhaustVelocity(), EPS, "ve = Isp * G0 = 1000 * 0,08");
        assertEquals(1, s.engines());
        assertEquals(1, s.tanks());
        assertEquals(0, s.parachutes());
        assertEquals(0, s.legs());
    }

    @Test
    void demaisBlocosPesamSuaMassaCadaUm() {
        // + 4 janelas 0,08 t, 2 assentos 0,1 t, 2 eclusas 0,2 t e 100 blocos de estrutura 2,0 t
        RocketStats s = ShipSpecs.statsFor(new ShipCensus(1, 1, 1, 4, 2, 2, 100));
        assertEquals(3.8 + 4 * 0.02 + 2 * 0.05 + 2 * 0.1 + 100 * 0.02, s.dryMassT(), EPS);
    }

    @Test
    void doisMotoresEDoisTanquesSomamEmpuxoECapacidade() {
        RocketStats s = ShipSpecs.statsFor(new ShipCensus(1, 2, 2, 0, 0, 0, 0));
        assertEquals(16.0, s.thrust(), EPS);
        assertEquals(8000.0, s.fuelCapacityMb(), EPS);
        assertEquals(2.0 + 3.0 + 0.6, s.dryMassT(), EPS);
    }

    @Test
    void motorDeImpulsoEmpurraMaisEGastaMaisQueODoFoguete() {
        // o plano: "empuxo maior e Isp menor" que o motor básico do foguete (2,5 e 1.600 ticks)
        assertTrue(ShipSpecs.ENGINE_THRUST > com.spacehorizons.rocket.RocketSpecs.ENGINE_THRUST);
        assertTrue(ShipSpecs.ENGINE_ISP_TICKS < com.spacehorizons.rocket.RocketSpecs.ENGINE_ISP_TICKS);
        assertEquals(com.spacehorizons.rocket.RocketSpecs.TANK_CAPACITY_MB, ShipSpecs.TANK_CAPACITY_MB, "o tanque é o mesmo do foguete");
    }

    @Test
    void arrastoVemDaVelocidadeTerminalENaoHaParaquedas() {
        RocketStats s = ShipSpecs.statsFor(new ShipCensus(1, 1, 1, 0, 0, 0, 0));
        assertEquals(0.08 / (24.0 * 24.0), s.dragK0(), EPS, "k0 = G0 / vt^2 com vt = 24");
        assertEquals(0.0, s.parachuteK0(), EPS);
    }

    @Test
    void massaMolhadaDeltaVETwrSeguemOContrato() {
        RocketStats s = ShipSpecs.statsFor(new ShipCensus(1, 1, 1, 0, 0, 0, 0));
        // 4.000 mB de combustível = 3,2 t
        assertEquals(3.8 + 3.2, s.wetMass(4000.0), EPS);
        assertEquals(3.8, s.wetMass(0.0), EPS);
        assertEquals(80.0 * Math.log(7.0 / 3.8), s.deltaV(4000.0), 1.0E-9);
        assertEquals(0.0, s.deltaV(0.0), EPS, "sem combustível não há Δv");
        double g = 0.08;
        assertEquals(8.0 / (7.0 * g), s.twr(4000.0, g), 1.0E-9);
        assertTrue(s.twr(0.0, g) > s.twr(4000.0, g), "vazia acelera com mais folga");
    }

    @Test
    void semMotorNaoHaDeltaVNemEmpuxo() {
        RocketStats s = ShipSpecs.statsFor(new ShipCensus(1, 0, 1, 0, 0, 0, 0));
        assertEquals(0.0, s.thrust(), EPS);
        assertEquals(0.0, s.deltaV(4000.0), EPS);
    }

    @Test
    void naveSemBlocosTemAMassaMinimaDeUmBlocoDeEstrutura() {
        RocketStats s = ShipSpecs.statsFor(ShipCensus.EMPTY);
        assertEquals(ShipSpecs.STRUCTURE_MASS_T, s.dryMassT(), EPS);
        assertEquals(0.0, s.thrust(), EPS);
    }

    @Test
    void censoContaPorPapelEIgnoraOAr() {
        ShipCensus c = ShipCensus.of(List.of(ShipBlockKind.BRIDGE, ShipBlockKind.ENGINE, ShipBlockKind.FUEL_TANK, ShipBlockKind.FUEL_TANK,
                ShipBlockKind.WINDOW, ShipBlockKind.SEAT, ShipBlockKind.AIRLOCK, ShipBlockKind.STRUCTURE, ShipBlockKind.STRUCTURE,
                ShipBlockKind.AIR, ShipBlockKind.AIR));
        assertEquals(new ShipCensus(1, 1, 2, 1, 1, 1, 2), c);
        assertEquals(9, c.total());
        assertEquals(c, ShipCensus.EMPTY.plus(ShipBlockKind.BRIDGE, 1).plus(ShipBlockKind.ENGINE, 1).plus(ShipBlockKind.FUEL_TANK, 2)
                .plus(ShipBlockKind.WINDOW, 1).plus(ShipBlockKind.SEAT, 1).plus(ShipBlockKind.AIRLOCK, 1).plus(ShipBlockKind.STRUCTURE, 2)
                .plus(ShipBlockKind.AIR, 5));
    }

    @Test
    void censosDeSecoesSomam() {
        ShipCensus a = new ShipCensus(1, 2, 3, 4, 5, 6, 7);
        ShipCensus b = new ShipCensus(10, 20, 30, 40, 50, 60, 70);
        assertEquals(new ShipCensus(11, 22, 33, 44, 55, 66, 77), a.plus(b));
        assertEquals(ShipCensus.EMPTY.total(), 0);
    }

    @Test
    void massaPorPapelBateComOCalculoDasEstatisticas() {
        ShipCensus c = new ShipCensus(1, 2, 3, 4, 5, 6, 7);
        double soma = 0;
        for (ShipBlockKind kind : ShipBlockKind.values()) {
            soma += ShipSpecs.massOf(kind) * switch (kind) {
                case BRIDGE -> c.bridges();
                case ENGINE -> c.engines();
                case FUEL_TANK -> c.tanks();
                case WINDOW -> c.windows();
                case SEAT -> c.seats();
                case AIRLOCK -> c.airlocks();
                case STRUCTURE -> c.structures();
                case AIR -> 0;
            };
        }
        assertEquals(soma, ShipSpecs.statsFor(c).dryMassT(), EPS);
        assertEquals(soma, ShipSpecs.dryMassT(c), EPS);
        assertEquals(0.0, ShipSpecs.massOf(ShipBlockKind.AIR), EPS);
    }

    @Test
    void colunaDeTanquesFormadaNaNaveTemACapacidadePeloVolumeECascoMaisLeve() {
        com.spacehorizons.rocket.Multiblocks.Box column = new com.spacehorizons.rocket.Multiblocks.Box(0, 0, 0, 2, 8, 2);
        ShipCensus loose = new ShipCensus(1, 1, 32, 0, 1, 0, 0);
        ShipCensus formed = loose.withTankStructures(java.util.List.of(column));
        assertEquals(32, formed.formedTankBlocks());
        assertEquals(0, formed.looseTanks());
        assertTrue(formed.sameCounts(loose), "as contagens por papel são as mesmas");
        assertEquals(128_000.0, ShipSpecs.statsFor(formed).fuelCapacityMb(), EPS, "a capacidade sai do volume, igual ao avulso");
        // 0,3 t * 32 * (0,4 + 0,6 * 72 / 192) = 6,0 t contra 9,6 t avulsos
        assertEquals(6.0, ShipSpecs.tankStructureMassT(column), EPS);
        assertEquals(ShipSpecs.dryMassT(loose) - 9.6 + 6.0, ShipSpecs.dryMassT(formed), EPS);
        assertTrue(ShipSpecs.statsFor(formed).deltaV(128_000.0) > ShipSpecs.statsFor(loose).deltaV(128_000.0));
    }

    @Test
    void censoDeNaveCarregaAsColunasNoRegistroDeVoo() {
        com.spacehorizons.rocket.Multiblocks.Box column = new com.spacehorizons.rocket.Multiblocks.Box(3, 4, 5, 3, 10, 3);
        ShipCensus census = new ShipCensus(1, 2, 90, 0, 1, 0, 10).withTankStructures(java.util.List.of(column));
        ShipFlightData data = ShipFlightData.of(ShipFlight.testState(0.0, 100.0), census);
        ShipFlightData back = ShipFlightData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                ShipFlightData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
        assertEquals(census, back.censusValue());
        assertEquals(java.util.List.of(column), back.censusValue().tankStructures());
    }
}
