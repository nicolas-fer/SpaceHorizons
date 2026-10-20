package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import com.spacehorizons.client.space.SkyAttitude;
import com.spacehorizons.client.space.SkyObserver;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.InertialFrame;
import com.spacehorizons.space.flight.AttitudeMath;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.Orbit;
import com.spacehorizons.space.flight.RocketStats;
import com.spacehorizons.space.flight.V3;

/** A física pura do voo da nave (fase 1c): órbita, atitude, SAS, empuxo, chão, persistência e o mapeamento da atitude para o céu. */
class ShipFlightTest {
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);
    /** Uma nave de teste típica: ponte, 4 motores, 8 tanques, 3 assentos, 4 eclusas, janelas e ~6.500 blocos de estrutura. */
    private static final ShipCensus CENSUS = new ShipCensus(1, 4, 8, 400, 3, 4, 6_500);
    private static final RocketStats STATS = ShipSpecs.statsFor(CENSUS);
    private static final double CLOCK = 5_000.0;

    private static ShipFlight.State coast(ShipFlight.State s, int ticks, double clock) {
        for (int i = 0; i < ticks; i++) {
            s = ShipFlight.step(s, ShipFlight.Command.IDLE, STATS, clock + i, 1.0);
        }
        return s;
    }

    // ------------------------------------------------------------------ órbita

    @Test
    void orbitaCircularNaTerraFicaEstavelPorMaisDeCemTicks() {
        ShipFlight.State s0 = ShipFlight.testState(CLOCK, 0.0);
        double r0 = s0.orbit().r().length();
        assertEquals(EARTH.radiusAt(ShipFlight.TEST_ALTITUDE), r0, 1.0e-6);
        double minR = r0;
        double maxR = r0;
        ShipFlight.State s = s0;
        for (int i = 0; i < 1_000; i++) {
            s = ShipFlight.step(s, ShipFlight.Command.IDLE, STATS, CLOCK + i, 1.0);
            minR = Math.min(minR, s.orbit().r().length());
            maxR = Math.max(maxR, s.orbit().r().length());
        }
        assertTrue(maxR - minR < 0.05, "o raio oscilou " + (maxR - minR));
        Orbit orbit = Orbit.of(s.orbit(), CLOCK + 1_000);
        assertTrue(orbit.bound());
        assertTrue(orbit.e() < 1.0e-6, "excentricidade = " + orbit.e());
        assertEquals(ShipFlight.TEST_ALTITUDE, orbit.apoapsis() - EARTH.radius(), 0.5);
        assertEquals(ShipFlight.TEST_ALTITUDE, orbit.periapsis() - EARTH.radius(), 0.5);
    }

    @Test
    void naveDaUmaVoltaInteiraNaOrbita() {
        ShipFlight.State s0 = ShipFlight.testState(CLOCK, 0.0);
        int ticks = (int) Math.round(EARTH.period(s0.orbit().r().length()));
        ShipFlight.State s = coast(s0, ticks, CLOCK);
        // o período não é inteiro: meio tick de erro, a ~80 blocos por tick, são até 40 blocos
        assertTrue(s.orbit().r().distanceTo(s0.orbit().r()) < 60.0,"depois de uma volta a nave deveria estar no ponto de partida: " + s.orbit().r().distanceTo(s0.orbit().r()));
        assertTrue(ticks > 10_000 && ticks < 11_000, "período em ticks = " + ticks);
    }

    @Test
    void ondeSeEstaNaoDependeDoRelogioDoCorpo() {
        // o estado é inercial: o relógio só entra na rotação do ar (zero no espaço); duas execuções com relógios diferentes dão a mesma órbita
        ShipFlight.State a = coast(ShipFlight.testState(0.0, 0.0), 500, 0.0);
        ShipFlight.State b = coast(ShipFlight.testState(0.0, 0.0), 500, 123_456.0);
        assertTrue(a.orbit().r().distanceTo(b.orbit().r()) < 1.0e-6);
    }

    // ------------------------------------------------------------------ empuxo

    @Test
    void queimaProgradeSobeOApoastroEMantemOPeriastro() {
        ShipFlight.State s = ShipFlight.testState(CLOCK, 32_000.0);
        double vBefore = s.orbit().v().length();
        double massBefore = STATS.wetMass(s.fuelMb());
        s = new ShipFlight.State(s.orbit(), s.attitude(), s.spin(), SasMode.PROGRADE, 0.0);
        for (int i = 0; i < 40; i++) {
            s = ShipFlight.step(s, new ShipFlight.Command(0, 0, 0, 1.0, false), STATS, CLOCK + i, 1.0);
        }
        Orbit orbit = Orbit.of(s.orbit(), CLOCK + 40);
        double apoapsisAltitude = orbit.apoapsis() - EARTH.radius();
        double periapsisAltitude = orbit.periapsis() - EARTH.radius();
        assertTrue(apoapsisAltitude > ShipFlight.TEST_ALTITUDE + 20_000.0, "apoastro = " + apoapsisAltitude);
        assertTrue(periapsisAltitude > ShipFlight.TEST_ALTITUDE - 1_500.0, "periastro = " + periapsisAltitude);
        // Tsiolkovsky: ve·ln(m0/m1), com o combustível gasto lido do próprio estado
        double massAfter = STATS.wetMass(s.fuelMb());
        double expectedDv = STATS.exhaustVelocity() * Math.log(massBefore / massAfter);
        assertEquals(40.0 * STATS.fullThrottleFuelMbPerTick(), 32_000.0 - s.fuelMb(), 1.0e-6, "combustível gasto a 100% = vazão x ticks");
        assertEquals(expectedDv, s.orbit().v().length() - vBefore, expectedDv * 0.03, "Δv medido pela velocidade, 3% de tolerância (a gravidade gira o vetor)");
    }

    @Test
    void semCombustivelNaoHaEmpuxoETanqueQuebradoLevaACarga() {
        ShipFlight.State s = ShipFlight.testState(CLOCK, 0.0);
        ShipFlight.State burned = ShipFlight.step(s, new ShipFlight.Command(0, 0, 0, 1.0, false), STATS, CLOCK, 1.0);
        assertEquals(s.orbit().v().length(), burned.orbit().v().length(), 1.0e-3, "sem combustível a velocidade só muda pela gravidade");
        // capacidade menor que o combustível: o voo corta o excesso
        ShipFlight.State full = ShipFlight.testState(CLOCK, 32_000.0);
        RocketStats fewer = ShipSpecs.statsFor(new ShipCensus(1, 4, 2, 0, 1, 0, 100));
        assertEquals(8_000.0, ShipFlight.step(full, ShipFlight.Command.IDLE, fewer, CLOCK, 1.0).fuelMb(), 1.0e-9);
    }

    // ------------------------------------------------------------------ atitude

    /** A proa, a direita e o cima da nave, nos eixos do estaleiro de ANTES do giro, depois de aplicar o comando por {@code ticks}. */
    private static Vector3d[] rotated(ShipFlight.Command command, int ticks) {
        ShipFlight.State s0 = ShipFlight.testState(CLOCK, 0.0);
        Quaterniond q0 = ShipFlight.bodyAttitude(s0.attitude());
        ShipFlight.State s = s0;
        for (int i = 0; i < ticks; i++) {
            s = ShipFlight.step(s, command, STATS, CLOCK, 0.0);
        }
        Quaterniond delta = new Quaterniond(q0).conjugate().mul(ShipFlight.bodyAttitude(s.attitude()));
        return new Vector3d[] {delta.transform(new Vector3d(0, 0, -1)), delta.transform(new Vector3d(1, 0, 0)), delta.transform(new Vector3d(0, 1, 0))};
    }

    @Test
    void wBaixaAProaDEViraParaADireitaEERolaNoSentidoHorario() {
        Vector3d[] pitch = rotated(new ShipFlight.Command(1, 0, 0, 0, false), 10);
        assertTrue(pitch[0].y < -0.05, "W deve baixar a proa: proa = " + pitch[0]);
        Vector3d[] yaw = rotated(new ShipFlight.Command(0, 1, 0, 0, false), 10);
        assertTrue(yaw[0].x > 0.05, "D deve virar a proa para a direita (leste): proa = " + yaw[0]);
        Vector3d[] roll = rotated(new ShipFlight.Command(0, 0, 1, 0, false), 10);
        assertTrue(roll[2].x > 0.05, "E deve rolar no sentido horário (o cima vai para a direita): cima = " + roll[2]);
        assertEquals(-1.0, roll[0].z, 1.0e-6, "a rolagem não mexe na proa");
        Vector3d[] up = rotated(new ShipFlight.Command(-1, 0, 0, 0, false), 10);
        assertTrue(up[0].y > 0.05, "S deve subir a proa: proa = " + up[0]);
    }

    @Test
    void estadoInicialTemAProaNoSentidoDaOrbitaEATerraADireita() {
        ShipFlight.State s = ShipFlight.testState(CLOCK, 0.0);
        V3 prograde = s.orbit().v().unit();
        assertTrue(ShipFlight.forward(s.attitude()).angleTo(prograde) < 1.0e-6);
        Quaterniond q = ShipFlight.bodyAttitude(s.attitude());
        Vector3d earthInBody = new Quaterniond(q).conjugate().transform(new Vector3d(-s.orbit().r().x(), -s.orbit().r().y(), -s.orbit().r().z()).normalize());
        assertEquals(1.0, earthInBody.x, 1.0e-9, "a Terra fica na direita (+X do estaleiro, leste)");
        assertEquals(0.0, earthInBody.y, 1.0e-9);
        assertEquals(0.0, earthInBody.z, 1.0e-9);
    }

    @Test
    void estiloFogueteEEixosDoEstaleiroSaoInversos() {
        Quaterniond p = new Quaterniond().rotateXYZ(0.3, -1.1, 0.7);
        Quaterniond round = ShipFlight.rocketAttitude(ShipFlight.bodyAttitude(p));
        double dot = round.x * p.x + round.y * p.y + round.z * p.z + round.w * p.w;
        assertTrue(Math.abs(dot) > 1.0 - 1.0e-12, "dot = " + dot);
        // a proa -Z do estaleiro é o nariz +Y do foguete
        Vector3d bow = ShipFlight.BODY_TO_ROCKET.transform(new Vector3d(0, 0, -1));
        assertEquals(0.0, bow.x, 1.0e-12);
        assertEquals(1.0, bow.y, 1.0e-12);
        assertEquals(0.0, bow.z, 1.0e-12);
    }

    @Test
    void sasProgradeAlinhaAProaComAVelocidade() {
        ShipFlight.State s0 = ShipFlight.testState(CLOCK, 0.0);
        // vira a nave 70 graus em torno do cima e liga o SAS prógrado
        Quaterniond turned = new Quaterniond(s0.attitude()).rotateZ(Math.toRadians(70));
        ShipFlight.State s = new ShipFlight.State(s0.orbit(), turned, V3.ZERO, SasMode.PROGRADE, 0.0);
        for (int i = 0; i < 200; i++) {
            s = ShipFlight.step(s, ShipFlight.Command.IDLE, STATS, CLOCK + i, 1.0);
        }
        double angle = Math.toDegrees(ShipFlight.forward(s.attitude()).angleTo(s.orbit().v().unit()));
        assertTrue(angle < 0.5, "a proa ficou a " + angle + " graus do prógrado");
    }

    @Test
    void sasEstabilizarZeraOGiroELiberaAProa() {
        ShipFlight.State s = ShipFlight.testState(CLOCK, 0.0);
        s = new ShipFlight.State(s.orbit(), s.attitude(), s.spin(), SasMode.OFF, 0.0);
        for (int i = 0; i < 20; i++) {
            s = ShipFlight.step(s, new ShipFlight.Command(0, 1, 0, 0, false), STATS, CLOCK + i, 1.0);
        }
        assertTrue(s.spin().length() > 0.01, "a nave está girando");
        // OFF mantém o giro; STABILIZE freia
        ShipFlight.State off = coast(s, 30, CLOCK);
        assertTrue(off.spin().length() > 0.01, "sem SAS o giro persiste");
        ShipFlight.State stab = new ShipFlight.State(s.orbit(), s.attitude(), s.spin(), SasMode.STABILIZE, 0.0);
        stab = coast(stab, 30, CLOCK);
        assertTrue(stab.spin().length() < 1.0e-9, "o SAS estabilizar para o giro: " + stab.spin());
    }

    @Test
    void passoComNextSasCicla() {
        ShipFlight.State s = ShipFlight.testState(CLOCK, 0.0);
        s = ShipFlight.step(s, new ShipFlight.Command(0, 0, 0, 0, true), STATS, CLOCK, 1.0);
        assertEquals(SasMode.STABILIZE, s.sas());
        s = ShipFlight.step(s, new ShipFlight.Command(0, 0, 0, 0, true), STATS, CLOCK, 1.0);
        assertEquals(SasMode.PROGRADE, s.sas());
    }

    // ------------------------------------------------------------------ chão da fase 1

    @Test
    void naveAbaixoDaAltitudeMinimaEHSegurada() {
        double minRadius = EARTH.radiusAt(ShipFlight.floorAltitude(SolarSystem.EARTH));
        V3 up = new V3(1, 0, 0);
        FlightState low = new FlightState(SolarSystem.EARTH, up.times(minRadius - 500.0), new V3(-3.0, 0.0, 70.0), 0.0, 0.0, 0.0);
        FlightState held = ShipFlight.floor(low);
        assertEquals(minRadius, held.r().length(), 1.0e-9);
        assertEquals(0.0, held.v().dot(up), 1.0e-9, "a velocidade para dentro é zerada");
        assertEquals(70.0, held.v().z(), 1.0e-9, "a tangencial fica");
        assertTrue(ShipFlight.onFloor(held));
        FlightState fine = new FlightState(SolarSystem.EARTH, up.times(minRadius + 100.0), new V3(-3.0, 0.0, 70.0), 0.0, 0.0, 0.0);
        assertEquals(fine, ShipFlight.floor(fine));
    }

    // ------------------------------------------------------------------ persistência

    @Test
    void estadoDeVooVoltaIgualDoCodec() {
        ShipFlight.State s = ShipFlight.testState(CLOCK, 12_345.0);
        s = new ShipFlight.State(s.orbit(), new Quaterniond(0.1, 0.2, 0.3, 0.9).normalize(), new V3(0.01, -0.02, 0.03), SasMode.RETROGRADE, 0.37);
        ShipFlightData data = ShipFlightData.of(s, CENSUS);
        ShipFlightData back = ShipFlightData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                ShipFlightData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
        assertEquals(data, back);
        ShipFlight.State r = back.toState();
        assertEquals(s.orbit().r(), r.orbit().r());
        assertEquals(s.orbit().v(), r.orbit().v());
        assertEquals(12_345.0, r.fuelMb(), 0.0);
        assertEquals(SasMode.RETROGRADE, r.sas());
        assertEquals(0.37, r.throttle(), 0.0);
        assertEquals(s.spin(), r.spin());
        assertTrue(r.attitude().difference(s.attitude(), new Quaterniond()).angle() < 1.0e-12);
        assertEquals(CENSUS, back.censusValue());
    }

    @Test
    void naveComVooVoltaIgualNoRegistro() {
        ShipFlightData data = ShipFlightData.of(ShipFlight.testState(CLOCK, 100.0), CENSUS);
        ShipRecord ship = new ShipRecord(3, 1, "Voo", 32, 20, 56, java.util.Optional.empty(), 7L).withFlight(data);
        ShipRecord back = ShipRecord.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                ShipRecord.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, ship).getOrThrow()).getOrThrow();
        assertEquals(ship, back);
        assertTrue(back.flies());
        assertTrue(!new ShipRecord(1, 0, "A", 64, 64, 64, java.util.Optional.empty(), 5L).flies());
    }

    // ------------------------------------------------------------------ mapeamento da atitude para o céu

    @Test
    void atitudeIdentidadeLocalDaOMesmoCeuQueOEspacoSemNave() {
        // atitude local identidade (a nave alinhada com os eixos da dimensão) = SkyObserver.forSpace, para qualquer relógio
        Quaterniond qLocal = new Quaterniond();
        Quaterniond r = SkyAttitude.localToSpaceFrame(qLocal);
        double[] pos = {EARTH.radiusAt(30_000.0), 0.0, 0.0};
        SkyObserver ship = SkyObserver.forShip(CLOCK, pos, r);
        SkyObserver plain = SkyObserver.forSpace(CLOCK, 0.0, 0.0 + 20_000.0, 0.0);
        for (int i = 0; i < 9; i++) {
            assertEquals(plain.localToSpace()[i], ship.localToSpace()[i], 1.0e-12);
        }
    }

    @Test
    void cadaEixoDaNaveApontaParaOndeOSeuSkyObserverDiz() {
        // a cadeia inteira: atitude inercial da nave -> local da dimensão (no relógio) -> R do céu -> matriz do observador. Os eixos do estaleiro (x,y,z)
        // da câmera têm de sair, em E, onde o quaternion inercial aponta cada eixo da nave (girado de I para E).
        ShipFlight.State s = ShipFlight.testState(CLOCK, 0.0);
        s = new ShipFlight.State(s.orbit(), new Quaterniond(s.attitude()).rotateX(0.4).rotateZ(-0.9).rotateY(1.3), s.spin(), s.sas(), 0.0);
        Quaterniond qI = ShipFlight.bodyAttitude(s.attitude());
        Quaterniond local = AttitudeMath.inertialToSpaceLocal(qI, CLOCK);
        SkyObserver observer = SkyObserver.forShip(CLOCK, new double[] {EARTH.radius() + 30_000.0, 0, 0}, SkyAttitude.localToSpaceFrame(local));
        double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        double[] m = observer.localToSpace();
        for (int a = 0; a < 3; a++) {
            Vector3d inertial = qI.transform(new Vector3d(axes[a][0], axes[a][1], axes[a][2]));
            double[] inE = new double[3];
            InertialFrame.toEarthFixed(CLOCK, inertial.x, inertial.y, inertial.z, inE);
            // a matriz do observador leva um vetor dos eixos da câmera (estaleiro) aos de E: precisa da conversão dos eixos do estaleiro
            // para os da dimensão de espaço, que é a mesma do foguete (SpaceDimension.LOCAL_TO_SPACE já está dentro de m)
            double[] viaMatrix = {m[0] * axes[a][0] + m[1] * axes[a][1] + m[2] * axes[a][2], m[3] * axes[a][0] + m[4] * axes[a][1] + m[5] * axes[a][2],
                    m[6] * axes[a][0] + m[7] * axes[a][1] + m[8] * axes[a][2]};
            // m = R·L: aplicar L ao eixo local dá os eixos do espaço; o inercial girado de I para E é R·L·(eixo) quando q local = M⁻¹·qI
            assertEquals(inE[0], viaMatrix[0], 1.0e-9, "eixo " + a + " x");
            assertEquals(inE[1], viaMatrix[1], 1.0e-9, "eixo " + a + " y");
            assertEquals(inE[2], viaMatrix[2], 1.0e-9, "eixo " + a + " z");
        }
    }

    @Test
    void girarANaveNoventaGrausTiraATerraDaJanelaDoLadoDireito() {
        // com a Terra à direita (leste), uma guinada de 90 graus para a esquerda (a proa vira para o oeste) joga a Terra para trás
        ShipFlight.State s0 = ShipFlight.testState(CLOCK, 0.0);
        double[] pos = new double[3];
        double[] posI = {s0.orbit().r().x(), s0.orbit().r().y(), s0.orbit().r().z()};
        InertialFrame.toEarthFixed(CLOCK, posI[0], posI[1], posI[2], pos);
        double before = earthAngleFromBow(s0, pos);
        // gira 90 graus em torno do cima da nave (guinada para a esquerda)
        Quaterniond turned = new Quaterniond(s0.attitude()).rotateZ(Math.toRadians(90));
        double after = earthAngleFromBow(new ShipFlight.State(s0.orbit(), turned, V3.ZERO, SasMode.OFF, 0.0), pos);
        double radius = Math.toDegrees(Math.asin(EARTH.radius() / s0.orbit().r().length()));
        assertTrue(before > 80.0 && before < 100.0, "antes, a Terra está a ~90 graus da proa (à direita): " + before);
        assertTrue(after > 170.0, "depois a Terra está atrás: " + after);
        assertTrue(before - radius < 60.0, "antes, a borda da Terra entra na janela da frente/direita (raio angular " + radius + ")");
        assertTrue(after - radius > 90.0, "depois o disco todo está fora de qualquer janela da proa ou dos lados: " + (after - radius));
    }

    /** O ângulo (graus) entre a proa (-Z do estaleiro) e a direção da Terra, vistos do céu virtual da nave. */
    private static double earthAngleFromBow(ShipFlight.State s, double[] posE) {
        Quaterniond local = AttitudeMath.inertialToSpaceLocal(ShipFlight.bodyAttitude(s.attitude()), CLOCK);
        SkyObserver observer = SkyObserver.forShip(CLOCK, posE, SkyAttitude.localToSpaceFrame(local));
        var apparent = observer.apparent(SolarSystem.EARTH);
        double dot = -apparent.z();       // proa = (0, 0, -1) nos eixos da câmera
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }
}
