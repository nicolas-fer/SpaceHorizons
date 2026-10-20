package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.flight.RocketStats;

/** RocketStats calculado a partir da contagem de blocos do retrato. */
class RocketSpecsTest {
    private static final double EPS = 1.0E-9;

    @Test
    void foguetePequenoTemAsMassasDoDesenho() {
        // cockpit 0,8 t + motor 0,6 t + tanque 0,2 t
        RocketStats s = RocketSpecs.statsFor(new RocketCensus(1, 1, 1, 0, 0, 0, 0));
        assertEquals(1.6, s.dryMassT(), EPS);
        assertEquals(2.5, s.thrust(), EPS);
        assertEquals(1600.0, s.ispTicks(), EPS);
        assertEquals(4000.0, s.fuelCapacityMb(), EPS);
        assertEquals(128.0, s.exhaustVelocity(), EPS, "ve = Isp * G0 = 1600 * 0,08");
        assertEquals(1, s.engines());
        assertEquals(1, s.tanks());
        assertEquals(0, s.parachutes());
        assertEquals(0, s.legs());
    }

    @Test
    void demaisBlocosPesamUmDecimoDeToneladaCadaUm() {
        // + paraquedas 0,1 t, 2 pernas 0,2 t, 3 cascos 0,3 t e 2 outros blocos 0,2 t
        RocketStats s = RocketSpecs.statsFor(new RocketCensus(1, 1, 1, 1, 2, 3, 2));
        assertEquals(1.6 + 0.1 + 0.2 + 0.3 + 0.2, s.dryMassT(), EPS);
        assertEquals(1, s.parachutes());
        assertEquals(2, s.legs());
    }

    @Test
    void doisMotoresEDoisTanquesSomamEmpuxoECapacidade() {
        RocketStats s = RocketSpecs.statsFor(new RocketCensus(1, 2, 2, 0, 0, 0, 0));
        assertEquals(5.0, s.thrust(), EPS);
        assertEquals(8000.0, s.fuelCapacityMb(), EPS);
        assertEquals(0.8 + 1.2 + 0.4, s.dryMassT(), EPS);
    }

    @Test
    void arrastoVemDaVelocidadeTerminalESoHaParaquedasSeHouverOBloco() {
        RocketStats sem = RocketSpecs.statsFor(new RocketCensus(1, 1, 1, 0, 0, 0, 0));
        RocketStats com = RocketSpecs.statsFor(new RocketCensus(1, 1, 1, 1, 0, 0, 0));
        assertEquals(0.08 / (16.0 * 16.0), sem.dragK0(), EPS, "k0 = G0 / vt^2 com vt = 16");
        assertEquals(0.0, sem.parachuteK0(), EPS);
        assertEquals(0.08 / 0.25, com.parachuteK0(), EPS, "vt = 0,5 com o paraquedas aberto");
    }

    @Test
    void massaMolhadaDeltaVETwrSeguemOContrato() {
        RocketStats s = RocketSpecs.statsFor(new RocketCensus(1, 1, 1, 0, 0, 0, 0));
        // 4.000 mB de combustível = 3,2 t
        assertEquals(1.6 + 3.2, s.wetMass(4000.0), EPS);
        assertEquals(1.6, s.wetMass(0.0), EPS);
        assertEquals(128.0 * Math.log(4.8 / 1.6), s.deltaV(4000.0), 1.0E-9);
        assertEquals(0.0, s.deltaV(0.0), EPS, "sem combustível não há Δv");
        double g = 0.08;
        assertEquals(2.5 / (4.8 * g), s.twr(4000.0, g), 1.0E-9);
        assertTrue(s.twr(0.0, g) > s.twr(4000.0, g), "vazio sobe com mais folga");
    }

    @Test
    void semMotorNaoHaDeltaVNemEmpuxo() {
        RocketStats s = RocketSpecs.statsFor(new RocketCensus(1, 0, 1, 0, 0, 0, 0));
        assertEquals(0.0, s.thrust(), EPS);
        assertEquals(0.0, s.deltaV(4000.0), EPS);
    }

    @Test
    void censoContaPorPapelEIgnoraOQueNaoEParte() {
        RocketCensus c = RocketCensus.of(List.of(RocketBlockKind.COCKPIT, RocketBlockKind.ENGINE, RocketBlockKind.FUEL_TANK,
                RocketBlockKind.FUEL_TANK, RocketBlockKind.HULL, RocketBlockKind.LEG, RocketBlockKind.PARACHUTE,
                RocketBlockKind.STRUCTURE, RocketBlockKind.PAD, RocketBlockKind.AIR, RocketBlockKind.TERRAIN));
        assertEquals(new RocketCensus(1, 1, 2, 1, 1, 1, 1), c);
        assertEquals(8, c.total());
        assertEquals(c, RocketCensus.EMPTY.plus(RocketBlockKind.COCKPIT, 1).plus(RocketBlockKind.ENGINE, 1)
                .plus(RocketBlockKind.FUEL_TANK, 2).plus(RocketBlockKind.HULL, 1).plus(RocketBlockKind.LEG, 1)
                .plus(RocketBlockKind.PARACHUTE, 1).plus(RocketBlockKind.STRUCTURE, 1).plus(RocketBlockKind.PAD, 5));
    }

    @Test
    void massaPorPapelBateComOCalculoDasEstatisticas() {
        RocketCensus c = new RocketCensus(1, 2, 3, 1, 4, 5, 6);
        double soma = 0;
        for (RocketBlockKind kind : RocketBlockKind.values()) {
            soma += RocketSpecs.massOf(kind) * switch (kind) {
                case COCKPIT -> c.cockpits();
                case ENGINE -> c.engines();
                case FUEL_TANK -> c.tanks();
                case PARACHUTE -> c.parachutes();
                case LEG -> c.legs();
                case HULL -> c.hulls();
                case STRUCTURE -> c.structures();
                default -> 0;
            };
        }
        assertEquals(soma, RocketSpecs.statsFor(c).dryMassT(), EPS);
        assertEquals(0.0, RocketSpecs.massOf(RocketBlockKind.PAD), EPS, "a plataforma fica no chão");
    }

    // ---------------------------------------------------------------- estruturas multibloco

    private static RocketCensus withStructures(RocketCensus base, java.util.List<Multiblocks.Box> tanks, java.util.List<Multiblocks.Box> engines) {
        return new RocketCensus(base.cockpits(), base.engines(), base.tanks(), base.parachutes(), base.legs(), base.hulls(), base.structures(), tanks,
                engines);
    }

    @Test
    void semEstruturasOsNumerosSaoOsDeSempre() {
        // 8 motores e 32 tanques avulsos = os mesmos blocos de antes das estruturas
        RocketStats s = RocketSpecs.statsFor(new RocketCensus(1, 8, 32, 0, 0, 0, 0));
        assertEquals(0.8 + 8 * 0.6 + 32 * 0.2, s.dryMassT(), EPS);
        assertEquals(8 * 2.5, s.thrust(), EPS);
        assertEquals(1600.0, s.ispTicks(), EPS);
    }

    @Test
    void colunaDeTanquesTemACapacidadePeloVolumeECascoMaisLeveQuantoMaior() {
        Multiblocks.Box small = new Multiblocks.Box(0, 0, 0, 1, 4, 1);
        Multiblocks.Box mid = new Multiblocks.Box(0, 0, 0, 2, 8, 2);
        Multiblocks.Box big = new Multiblocks.Box(0, 0, 0, 3, 10, 3);
        assertEquals(16_000, RocketSpecs.tankStructureCapacityMb(small));
        assertEquals(128_000, RocketSpecs.tankStructureCapacityMb(mid));
        assertEquals(360_000, RocketSpecs.tankStructureCapacityMb(big));
        // massa seca: 0,2 t * V * (0,4 + 0,6 * A / (6 V))
        assertEquals(0.2 * 4 * (0.4 + 0.6 * 18.0 / 24.0), RocketSpecs.tankStructureMassT(small), EPS);
        assertEquals(0.68, RocketSpecs.tankStructureMassT(small), EPS);
        assertEquals(4.0, RocketSpecs.tankStructureMassT(mid), EPS);
        assertEquals(9.96, RocketSpecs.tankStructureMassT(big), 0.01);
        double perBlockSmall = RocketSpecs.tankStructureMassT(small) / small.volume();
        double perBlockMid = RocketSpecs.tankStructureMassT(mid) / mid.volume();
        double perBlockBig = RocketSpecs.tankStructureMassT(big) / big.volume();
        assertTrue(perBlockSmall < RocketSpecs.TANK_DRY_MASS_T, "formado é mais leve que avulso");
        assertTrue(perBlockMid < perBlockSmall && perBlockBig < perBlockMid, "quanto maior, mais leve por bloco");
        assertTrue(perBlockBig > RocketSpecs.TANK_DRY_MASS_T * RocketSpecs.TANK_HULL_FLOOR, "nunca abaixo do piso do casco");
    }

    @Test
    void censoComColunaFormadaUsaACapacidadeEAMassaDaEstrutura() {
        Multiblocks.Box column = new Multiblocks.Box(0, 0, 0, 2, 8, 2);
        RocketCensus c = withStructures(new RocketCensus(1, 1, 32, 0, 0, 0, 0), java.util.List.of(column), java.util.List.of());
        RocketStats s = RocketSpecs.statsFor(c);
        assertEquals(128_000.0, s.fuelCapacityMb(), EPS, "a capacidade sai do volume");
        assertEquals(0.8 + 0.6 + 4.0, s.dryMassT(), EPS);
        assertEquals(32, c.formedTankBlocks());
        assertEquals(0, c.looseTanks());
        // com tanques de fora da coluna, estes valem um a um
        RocketCensus mixed = withStructures(new RocketCensus(1, 1, 35, 0, 0, 0, 0), java.util.List.of(column), java.util.List.of());
        assertEquals(3, mixed.looseTanks());
        assertEquals(0.8 + 0.6 + 4.0 + 3 * 0.2, RocketSpecs.statsFor(mixed).dryMassT(), EPS);
    }

    @Test
    void motoresFormadosEmpurramMaisPorMassaEQueimamMelhor() {
        double single = RocketSpecs.ENGINE_THRUST / RocketSpecs.ENGINE_MASS_T;
        double two = RocketSpecs.ENGINE_2_THRUST / RocketSpecs.ENGINE_2_MASS_T;
        double three = RocketSpecs.ENGINE_3_THRUST / RocketSpecs.ENGINE_3_MASS_T;
        assertEquals(24.0, RocketSpecs.ENGINE_2_THRUST, EPS);
        assertEquals(3.84, RocketSpecs.ENGINE_2_MASS_T, EPS);
        assertEquals(101.25, RocketSpecs.ENGINE_3_THRUST, EPS);
        assertEquals(11.34, RocketSpecs.ENGINE_3_MASS_T, EPS);
        assertTrue(single < two && two < three, "empuxo por tonelada: " + single + " < " + two + " < " + three);
        assertTrue(RocketSpecs.ENGINE_ISP_TICKS < RocketSpecs.ENGINE_2_ISP_TICKS && RocketSpecs.ENGINE_2_ISP_TICKS < RocketSpecs.ENGINE_3_ISP_TICKS);

        RocketCensus c2 = withStructures(new RocketCensus(1, 8, 4, 0, 0, 0, 0), java.util.List.of(), java.util.List.of(new Multiblocks.Box(0, 0, 0, 2, 2, 2)));
        RocketStats s2 = RocketSpecs.statsFor(c2);
        assertEquals(24.0, s2.thrust(), EPS);
        assertEquals(1700.0, s2.ispTicks(), EPS);
        assertEquals(0.8 + 3.84 + 4 * 0.2, s2.dryMassT(), EPS);
        assertEquals(8, s2.engines(), "as contagens continuam sendo de blocos");

        RocketCensus c3 = withStructures(new RocketCensus(1, 27, 4, 0, 0, 0, 0), java.util.List.of(), java.util.List.of(new Multiblocks.Box(0, 0, 0, 3, 3, 3)));
        RocketStats s3 = RocketSpecs.statsFor(c3);
        assertEquals(101.25, s3.thrust(), EPS);
        assertEquals(1800.0, s3.ispTicks(), EPS);
    }

    @Test
    void ispDeMotoresMistosEMediaHarmonicaPonderadaPeloEmpuxo() {
        // um 2x2x2 (24 de empuxo, Isp 1700) e 2 motores avulsos (5 de empuxo, Isp 1600)
        RocketCensus c = withStructures(new RocketCensus(1, 10, 4, 0, 0, 0, 0), java.util.List.of(), java.util.List.of(new Multiblocks.Box(0, 0, 0, 2, 2, 2)));
        RocketStats s = RocketSpecs.statsFor(c);
        assertEquals(29.0, s.thrust(), EPS);
        double expected = 29.0 / (24.0 / 1700.0 + 5.0 / 1600.0);
        assertEquals(expected, s.ispTicks(), 1.0E-9);
        // a vazão de massa do conjunto é a soma das vazões dos motores
        assertEquals(24.0 / (1700.0 * 0.08) + 5.0 / (1600.0 * 0.08), s.fullThrottleFlow(), 1.0E-9);
    }

    @Test
    void foguetePesadoComMotorGrandeChegaMaisLongeQueOsMesmosBlocosAvulsos() {
        // 27 motores e 90 tanques, formados ou não: o formado tem mais Δv e mais empuxo por massa
        Multiblocks.Box engine = new Multiblocks.Box(0, 0, 0, 3, 3, 3);
        Multiblocks.Box tank = new Multiblocks.Box(0, 3, 0, 3, 10, 3);
        RocketStats loose = RocketSpecs.statsFor(new RocketCensus(1, 27, 90, 1, 2, 0, 0));
        RocketStats formed = RocketSpecs.statsFor(withStructures(new RocketCensus(1, 27, 90, 1, 2, 0, 0), java.util.List.of(tank), java.util.List.of(engine)));
        assertTrue(formed.dryMassT() < loose.dryMassT());
        assertTrue(formed.deltaV(formed.fuelCapacityMb()) > loose.deltaV(loose.fuelCapacityMb()));
        assertTrue(formed.thrust() > loose.thrust());
        assertEquals(loose.fuelCapacityMb(), formed.fuelCapacityMb(), EPS, "a capacidade é a mesma: 4.000 mB por bloco");
    }
}
