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
}
