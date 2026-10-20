package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import com.mojang.serialization.JsonOps;
import com.spacehorizons.space.flight.RocketStats;
import com.spacehorizons.space.flight.V3;

/** A proa pela ponte e o empuxo dos motores conforme a direção do bocal (lógica pura). */
class ShipArrangementTest {
    private static final double EPS = 1.0e-12;
    private static final double SQRT2 = Math.sqrt(2.0);

    private static void assertVec(V3 expected, V3 actual) {
        assertEquals(expected.x(), actual.x(), EPS, "x de " + actual);
        assertEquals(expected.y(), actual.y(), EPS, "y de " + actual);
        assertEquals(expected.z(), actual.z(), EPS, "z de " + actual);
    }

    @Test
    void aRotacaoDaProaLevaOMenosZParaALadoDaProa() {
        for (int bow = 0; bow < 4; bow++) {
            ShipArrangement a = new ShipArrangement(bow, 0, 0, 0, 0);
            Vector3d v = new Quaterniond().rotationY(a.yaw()).transform(new Vector3d(0, 0, -1));
            assertVec(a.bowVector(), new V3(v.x, v.y, v.z));
            // e os eixos do estaleiro, em canônicos, levam a proa real ao -Z canônico
            assertVec(new V3(0, 0, -1), a.toCanonical(a.bowVector()));
        }
    }

    @Test
    void semInformacaoTodosOsMotoresEmpurramPelaProa() {
        // naves antigas e testes: nenhum bocal contado, os motores do censo vão para a frente
        assertVec(new V3(0, 0, -4), ShipArrangement.DEFAULT.netThrust(4));
        ShipCensus census = new ShipCensus(1, 4, 8, 0, 3, 0, 100);
        RocketStats legacy = ShipSpecs.statsFor(census);
        RocketStats same = ShipSpecs.statsFor(census, ShipArrangement.DEFAULT);
        assertEquals(legacy, same);
        assertEquals(4 * ShipSpecs.ENGINE_THRUST, legacy.thrust(), EPS);
        assertEquals(ShipSpecs.ENGINE_ISP_TICKS, legacy.ispTicks(), EPS);
        assertVec(ShipSpecs.BOW_AXIS, ShipSpecs.thrustAxis(census, ShipArrangement.DEFAULT));
    }

    @Test
    void motoresComOBocalParaATrasEmpurramParaAFrenteQualquerQueSejaAProa() {
        // proa a leste: a ponte olha para oeste; motores com o bocal para oeste empurram para leste = a proa
        ShipArrangement east = new ShipArrangement(ShipArrangement.EAST, 0, 0, 0, 4);
        assertVec(new V3(0, 0, -4), east.netThrust(4));
        ShipArrangement south = new ShipArrangement(ShipArrangement.SOUTH, 4, 0, 0, 0);
        assertVec(new V3(0, 0, -4), south.netThrust(4));
        ShipCensus census = new ShipCensus(1, 4, 0, 0, 0, 0, 0);
        assertEquals(1.0, ShipSpecs.engineEfficiency(census, east), EPS);
    }

    @Test
    void motoresEmDirecoesDiferentesSomamVetorialmente() {
        // proa norte, 2 motores com o bocal para o sul (empurram para a frente) e 2 com o bocal para o leste (empurram para o oeste = esquerda)
        ShipArrangement a = new ShipArrangement(ShipArrangement.NORTH, 0, 2, 2, 0);
        V3 net = a.netThrust(4);
        assertVec(new V3(-2, 0, -2), net);
        ShipCensus census = new ShipCensus(1, 4, 8, 0, 3, 0, 100);
        RocketStats s = ShipSpecs.statsFor(census, a);
        assertEquals(2.0 * SQRT2 * ShipSpecs.ENGINE_THRUST, s.thrust(), 1.0e-9);
        assertEquals(1.0 / SQRT2, ShipSpecs.engineEfficiency(census, a), 1.0e-12);
        V3 axis = ShipSpecs.thrustAxis(census, a);
        assertVec(new V3(-1.0 / SQRT2, 0, -1.0 / SQRT2), axis);
        // a vazão de massa é a de 4 motores (n·T/ve), mesmo com o empuxo menor: o Isp efetivo cai na mesma razão
        double flow = 4 * ShipSpecs.ENGINE_THRUST / (ShipSpecs.ENGINE_ISP_TICKS * 0.08);
        assertEquals(flow, s.fullThrottleFlow(), 1.0e-9);
    }

    @Test
    void motoresOpostosSeAnulamSemQueimar() {
        ShipArrangement a = new ShipArrangement(ShipArrangement.NORTH, 0, 1, 0, 1);
        ShipCensus census = new ShipCensus(1, 2, 8, 0, 3, 0, 100);
        RocketStats s = ShipSpecs.statsFor(census, a);
        assertEquals(0.0, s.thrust(), 0.0);
        assertEquals(0.0, ShipSpecs.engineEfficiency(census, a), EPS);
        assertVec(ShipSpecs.BOW_AXIS, ShipSpecs.thrustAxis(census, a));
        assertEquals(0.0, s.deltaV(32_000.0), 0.0);
    }

    @Test
    void empurrarDeLadoMudaAOrbitaNaDirecaoDoLado() {
        // um motor com o bocal para o oeste empurra para leste; proa norte, direita = leste. A nave canônica está com a Terra à direita
        ShipCensus census = new ShipCensus(1, 1, 8, 0, 1, 0, 100);
        ShipArrangement a = new ShipArrangement(ShipArrangement.NORTH, 0, 0, 0, 1);
        RocketStats stats = ShipSpecs.statsFor(census, a);
        V3 axis = ShipSpecs.thrustAxis(census, a);
        assertVec(new V3(1, 0, 0), axis);
        ShipFlight.State s0 = ShipFlight.testState(5_000.0, 32_000.0);
        V3 right = s0.orbit().r().unit().negate();
        V3 forward = s0.orbit().v().unit();
        assertEquals(1.0, ShipFlight.thrustDirection(s0.attitude(), axis).dot(right), 1.0e-9);
        ShipFlight.State s1 = ShipFlight.step(s0, new ShipFlight.Command(0, 0, 0, 1.0, false), stats, axis, 5_000.0, 1.0);
        ShipFlight.State coast = ShipFlight.step(s0, ShipFlight.Command.IDLE, stats, axis, 5_000.0, 1.0);
        V3 dv = s1.orbit().v().minus(coast.orbit().v());
        assertTrue(dv.length() > 1.0e-4, "queimou: " + dv.length());
        assertTrue(dv.unit().dot(right) > 0.999, "o empuxo sai para a direita: " + dv.unit().dot(right));
        assertEquals(0.0, dv.unit().dot(forward), 1.0e-6);
        assertTrue(s1.fuelMb() < s0.fuelMb());
        // o mesmo motor empurrando pela proa (eixo padrão) acelera a nave pela proa, não pelo lado
        ShipFlight.State f = ShipFlight.step(s0, new ShipFlight.Command(0, 0, 0, 1.0, false), stats, ShipSpecs.BOW_AXIS, 5_000.0, 1.0);
        V3 dvForward = f.orbit().v().minus(coast.orbit().v());
        assertTrue(dvForward.unit().dot(forward) > 0.999);
    }

    @Test
    void aAtitudeDoEstaleiroGiraComAProaDaPonte() {
        ShipFlight.State s = ShipFlight.testState(5_000.0, 0.0);
        // proa no norte: igual à atitude canônica de sempre
        Quaterniond north = ShipFlight.yardAttitude(s.attitude(), ShipArrangement.NORTH);
        assertTrue(north.difference(ShipFlight.bodyAttitude(s.attitude()), new Quaterniond()).angle() < 1.0e-12);
        // a proa real (no eixo do estaleiro) vira a proa canônica no espaço: com a proa a leste, o eixo +X do estaleiro aponta para onde a nave aponta
        V3 forward = ShipFlight.forward(s.attitude());
        for (int bow = 0; bow < 4; bow++) {
            Quaterniond q = ShipFlight.yardAttitude(s.attitude(), bow);
            V3 bowYard = new ShipArrangement(bow, 0, 0, 0, 0).bowVector();
            Vector3d inertial = q.transform(new Vector3d(bowYard.x(), bowYard.y(), bowYard.z()));
            assertEquals(1.0, new V3(inertial.x, inertial.y, inertial.z).dot(forward), 1.0e-12, "proa " + bow);
        }
    }

    @Test
    void aDisposicaoVoltaIgualDoCodecEDeRegistrosAntigos() {
        ShipArrangement a = new ShipArrangement(ShipArrangement.WEST, 1, 2, 3, 4);
        ShipFlightData data = ShipFlightData.of(ShipFlight.testState(5_000.0, 100.0), new ShipCensus(1, 10, 8, 0, 3, 0, 100), a);
        var json = ShipFlightData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        ShipFlightData back = ShipFlightData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(a, back.arrangementValue());
        // registro de antes da proa e dos bocais: sem o campo, valem o padrão
        var legacy = json.getAsJsonObject().deepCopy();
        legacy.remove("arrangement");
        ShipFlightData old = ShipFlightData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(ShipArrangement.DEFAULT, old.arrangementValue());
        assertEquals(a, ShipArrangement.fromList(a.toList()));
        assertEquals(ShipArrangement.DEFAULT, ShipArrangement.fromList(java.util.List.of(9, 0, 0, 0, 0)));
    }
}
