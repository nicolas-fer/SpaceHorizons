package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.rocket.FlightMachine.Command;
import com.spacehorizons.rocket.FlightMachine.Event;
import com.spacehorizons.rocket.FlightMachine.Result;
import com.spacehorizons.rocket.FlightMachine.Snapshot;
import com.spacehorizons.rocket.FlightMachine.ZoneAccess;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.AttitudeMath;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.FrameBridge;
import com.spacehorizons.space.flight.FrameBridge.Local;
import com.spacehorizons.space.flight.RocketStats;
import com.spacehorizons.space.flight.V3;

/** A máquina de estados do voo: decolagem, troca de zona, pouso suave, batida, paraquedas, sem dimensão de espaço. */
class FlightMachineTest {
    private static final double CLOCK = 5_000.0;
    private static final int SEA = Planets.EARTH.seaLevel();
    /** 4 motores, 12 tanques, 1 paraquedas, 2 pernas, 1 cockpit e 4 blocos de estrutura: 6,3 t secas, 44,7 t cheio. */
    private static final RocketStats STATS = RocketStats.of(4, 12, 1, 2, 1, 4, 4.0);
    private static final FlightMachine.Box BOX = new FlightMachine.Box(1.0, 3.0, 1.0);
    private static final double FULL = 12 * 4_000.0;
    private static final ZoneAccess ALL = new ZoneAccess() {
        @Override
        public boolean hasSpace() {
            return true;
        }

        @Override
        public boolean hasBody(SolarSystem.Body body) {
            return true;
        }
    };
    private static final ZoneAccess NO_SPACE = new ZoneAccess() {
        @Override
        public boolean hasSpace() {
            return false;
        }

        @Override
        public boolean hasBody(SolarSystem.Body body) {
            return true;
        }
    };

    private static double ground() {
        return SEA;
    }

    private static Result step(Snapshot s, Command c, Local landed, Quaterniond landedAttitude, ZoneAccess zones) {
        return FlightMachine.step(s, c, STATS, BOX, CLOCK, 1.0, (x, z, ex, ez, lo, hi) -> ground(), zones, FULL, landed, landedAttitude);
    }

    private static Command thrust(double throttle) {
        return new Command(0.0, 0.0, 0.0, throttle, false, false);
    }

    /** O foguete pousado de pé sobre o chão do spawn: o centro da caixa fica meia altura acima do chão. */
    private static Local onThePad(SolarSystem.Body body) {
        return new Local(0.0, body.planet().seaLevel() + BOX.hy(), 0.0, 0.0, 0.0, 0.0);
    }

    /** Um foguete de pé em voo na dimensão da Terra, a {@code y} com velocidade vertical {@code vy} (a posição é a do spawn). */
    private static Snapshot flyingUpright(double y, double vy, Quaterniond localAttitude) {
        FlightState state = FrameBridge.fromDimension(SolarSystem.EARTH, CLOCK, new Local(0.0, y, 0.0, 0.0, vy, 0.0), FULL / 2.0);
        Quaterniond inertial = AttitudeMath.localToInertial(SolarSystem.EARTH, CLOCK, 0.0, 0.0, localAttitude);
        return new Snapshot(FlightMode.FLIGHT, state, inertial, V3.ZERO, SasMode.OFF, 0.0, false, SolarSystem.EARTH, state.fuelMb());
    }

    // ------------------------------------------------------------------ pousado e decolagem

    @Test
    void landedWithoutThrottleStaysPutAndKeepsTheLandedPlacement() {
        Snapshot landed = Snapshot.landed(SolarSystem.EARTH, FULL);
        Local at = onThePad(SolarSystem.EARTH);
        Result r = step(landed, Command.IDLE, at, new Quaterniond(), ALL);
        assertEquals(Event.NONE, r.event());
        assertEquals(FlightMode.LANDED, r.next().mode());
        assertFalse(r.next().flying());
        assertEquals(at.y(), r.local().y(), 1e-12);
    }

    @Test
    void lowThrottleThatCannotLiftTheRocketKeepsItOnTheGround() {
        // empuxo 10 · 0,1 / 44,7 t = 0,022 < g = 0,08: não sai do chão
        Result r = step(Snapshot.landed(SolarSystem.EARTH, FULL), thrust(0.1), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(FlightMode.LANDED, r.next().mode());
        assertEquals(Event.NONE, r.event());
    }

    @Test
    void noFuelOrNoEngineNeverLifts() {
        Result dry = FlightMachine.step(Snapshot.landed(SolarSystem.EARTH, 0.0), thrust(1.0), STATS, BOX, CLOCK, 1.0,
                (x, z, ex, ez, lo, hi) -> ground(), ALL, 0.0, onThePad(SolarSystem.EARTH), new Quaterniond());
        assertEquals(FlightMode.LANDED, dry.next().mode());
        RocketStats noEngine = RocketStats.of(0, 1, 0, 0, 1, 2, 1.0);
        Result none = FlightMachine.step(Snapshot.landed(SolarSystem.EARTH, 4_000.0), thrust(1.0), noEngine, BOX, CLOCK, 1.0,
                (x, z, ex, ez, lo, hi) -> ground(), ALL, 4_000.0, onThePad(SolarSystem.EARTH), new Quaterniond());
        assertEquals(FlightMode.LANDED, none.next().mode());
    }

    @Test
    void fullThrottleLiftsOffUpwardAndBurnsFuel() {
        Result r = step(Snapshot.landed(SolarSystem.EARTH, FULL), thrust(1.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(Event.LIFTOFF, r.event());
        assertEquals(FlightMode.FLIGHT, r.next().mode());
        assertNotNull(r.next().state());
        assertTrue(r.next().state().fuelMb() < FULL, "queimou combustível");
        assertTrue(r.local().y() > onThePad(SolarSystem.EARTH).y(), "subiu");
        assertTrue(r.local().vy() > 0.0, "velocidade vertical positiva");
        assertSame(SolarSystem.EARTH, r.next().zoneBody());
    }

    @Test
    void onTheMoonTheLiftoffStateCarriesTheGroundRotationVelocity() {
        Result r = step(Snapshot.landed(SolarSystem.MOON, FULL), thrust(1.0), onThePad(SolarSystem.MOON), new Quaterniond(), ALL);
        assertEquals(Event.LIFTOFF, r.event());
        double speed = r.next().state().v().length();
        assertTrue(speed > 3.0 && speed < 12.0, "ω×r da Lua entra na velocidade inercial: " + speed);
        // e relativa ao chão a velocidade é só a do empuxo
        assertTrue(Math.hypot(r.local().vx(), r.local().vz()) < 0.01);
    }

    // ------------------------------------------------------------------ zonas

    @Test
    void aVerticalFullThrottleAscentLeavesThePlanetDimensionForSpace() {
        Snapshot s = Snapshot.landed(SolarSystem.EARTH, FULL);
        Local landed = onThePad(SolarSystem.EARTH);
        Result r = null;
        int ticks = 0;
        do {
            r = step(s, thrust(1.0), landed, new Quaterniond(), ALL);
            s = r.next();
            ticks++;
        } while (!r.transfer() && ticks < 2_000);
        assertEquals(Event.LEAVE_ZONE, r.event(), "saiu da zona em " + ticks + " ticks");
        assertNull(r.toBody(), "destino: espaço aberto");
        assertNull(r.next().zoneBody());
        // o destino é a posição absoluta: espaço (sx = r) vira y = sx + 20000 da dimensão de espaço
        double radius = Planets.EARTH.radius();
        assertEquals(20_000.0 + radius + 5_000.0, r.local().y(), 400.0);
        // em órbita acima, o passo seguinte continua no espaço, sem nova troca
        Result next = step(r.next(), thrust(0.0), landed, new Quaterniond(), ALL);
        assertEquals(Event.NONE, next.event());
        assertNull(next.next().zoneBody());
    }

    @Test
    void withoutTheSpaceDimensionTheRocketStaysInThePlanetZone() {
        Snapshot above = flyingUpright(SEA + 5_100.0, 3.0, new Quaterniond());
        Result r = step(above, thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), NO_SPACE);
        assertEquals(Event.NONE, r.event());
        assertSame(SolarSystem.EARTH, r.next().zoneBody());
        assertTrue(r.local().y() > SEA + 5_100.0);
        Result with = step(above, thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(Event.LEAVE_ZONE, with.event());
    }

    @Test
    void fallingBelowEightyPercentOfTheExitAltitudeEntersThePlanetDimension() {
        FlightState state = FrameBridge.fromDimension(SolarSystem.EARTH, CLOCK, new Local(0.0, SEA + 3_990.0, 0.0, 0.0, -5.0, 0.0), 1_000.0);
        Quaterniond inertial = AttitudeMath.localToInertial(SolarSystem.EARTH, CLOCK, 0.0, 0.0, new Quaterniond());
        Snapshot inSpace = new Snapshot(FlightMode.FLIGHT, state, inertial, V3.ZERO, SasMode.OFF, 0.0, false, null, 1_000.0);
        Result r = step(inSpace, thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(Event.ENTER_ZONE, r.event());
        assertSame(SolarSystem.EARTH, r.toBody());
        assertSame(SolarSystem.EARTH, r.next().zoneBody());
        assertEquals(SEA + 3_985.0, r.local().y(), 10.0);
        assertTrue(r.local().vy() < 0.0, "ainda descendo, agora com velocidade local ao chão");
        // a atitude local de um foguete de pé no espaço de cabeça para cima volta ao identidade (de pé) aqui no spawn
        assertEquals(0.0, AttitudeMath.tiltDegrees(r.localAttitude(), new V3(0, 1, 0)), 0.5);
    }

    /** Um foguete no espaço aberto (zona nula) sobre o spawn da Terra, a {@code altitude}, com velocidade local ao chão {@code (vx, vy)}. */
    private static Snapshot inSpaceAbove(double altitude, double vx, double vy) {
        FlightState state = FrameBridge.fromDimension(SolarSystem.EARTH, CLOCK, new Local(0.0, SEA + altitude, 0.0, vx, vy, 0.0), 1_000.0);
        Quaterniond inertial = AttitudeMath.localToInertial(SolarSystem.EARTH, CLOCK, 0.0, 0.0, new Quaterniond());
        return new Snapshot(FlightMode.FLIGHT, state, inertial, V3.ZERO, SasMode.OFF, 0.0, false, null, 1_000.0);
    }

    /** Em voo na dimensão da Terra a {@code altitude} com velocidade horizontal local {@code vx}. */
    private static Snapshot flyingFast(double altitude, double vx) {
        FlightState state = FrameBridge.fromDimension(SolarSystem.EARTH, CLOCK, new Local(0.0, SEA + altitude, 0.0, vx, 5.0, 0.0), FULL / 2.0);
        Quaterniond inertial = AttitudeMath.localToInertial(SolarSystem.EARTH, CLOCK, 0.0, 0.0, new Quaterniond());
        return new Snapshot(FlightMode.FLIGHT, state, inertial, V3.ZERO, SasMode.OFF, 0.0, false, SolarSystem.EARTH, state.fuelMb());
    }

    @Test
    void aSlowDescentEntersThePlanetDimensionBelowTheEnterAltitude() {
        Result r = step(inSpaceAbove(3_990.0, 20.0, -10.0), thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(Event.ENTER_ZONE, r.event());
    }

    @Test
    void aFastReentryStaysInTheSpaceDimensionDownToTheForcedEntryHeight() {
        // 60 b/tick na horizontal (reentrada de órbita): a dimensão da Terra não carregaria o terreno nessa velocidade
        for (double altitude : new double[] {3_990.0, 3_000.0, 1_500.0, 1_100.0}) {
            Result r = step(inSpaceAbove(altitude, 60.0, -10.0), thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
            assertEquals(Event.NONE, r.event(), "altitude " + altitude);
            assertNull(r.next().zoneBody(), "altitude " + altitude);
        }
    }

    @Test
    void aFastReentryIsForcedIntoThePlanetDimensionNearTheTerrain() {
        // o terreno do teste é o nível do mar: a 1.000 de altura o foguete entra mesmo a 60 b/tick (a espera por chunks é do voo)
        Result r = step(inSpaceAbove(990.0, 60.0, -10.0), thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(Event.ENTER_ZONE, r.event());
        assertSame(SolarSystem.EARTH, r.next().zoneBody());
    }

    @Test
    void theForcedEntryHeightIsMeasuredFromTheTerrainNotFromSeaLevel() {
        ZoneAccess mountain = new ZoneAccess() {
            @Override
            public boolean hasSpace() {
                return true;
            }

            @Override
            public boolean hasBody(SolarSystem.Body body) {
                return true;
            }

            @Override
            public double terrainHeight(SolarSystem.Body body, double x, double z) {
                return SEA + 500.0;
            }
        };
        // 1.400 sobre o mar = 900 sobre o terreno de 500: entra; 1.600 = 1.100 sobre o terreno: segue no espaço
        assertEquals(Event.ENTER_ZONE, step(inSpaceAbove(1_400.0, 60.0, -10.0), thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), mountain).event());
        assertEquals(Event.NONE, step(inSpaceAbove(1_600.0, 60.0, -10.0), thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), mountain).event());
    }

    @Test
    void aFastAscentLeavesThePlanetDimensionEarlyButASlowOrLowOneDoesNot() {
        Result fast = step(flyingFast(1_500.0, 60.0), thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(Event.LEAVE_ZONE, fast.event());
        assertNull(fast.next().zoneBody());
        assertEquals(Event.NONE, step(flyingFast(1_500.0, 30.0), thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL).event(), "devagar");
        assertEquals(Event.NONE, step(flyingFast(600.0, 60.0), thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL).event(), "baixo");
        assertEquals(Event.NONE, step(flyingFast(1_500.0, 60.0), thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), NO_SPACE).event(),
                "sem dimensão de espaço");
    }

    @Test
    void thereIsHysteresisBetweenLeavingEarlyAndEnteringAgain() {
        // saiu a mais de 40 b/tick; no espaço, a 30 b/tick (entre 25 e 40) não volta, nem a 1.500 nem a 3.000
        for (double altitude : new double[] {1_500.0, 3_000.0}) {
            Result r = step(inSpaceAbove(altitude, 30.0, 5.0), thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
            assertEquals(Event.NONE, r.event(), "altitude " + altitude);
        }
        assertEquals(Event.NONE, step(flyingFast(1_500.0, 30.0), thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL).event());
        assertEquals(Event.LEAVE_ZONE, step(flyingFast(1_500.0, 45.0), thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL).event());
    }

    @Test
    void thereIsNoEntryWhenThePlanetDimensionDoesNotExist() {
        FlightState state = FrameBridge.fromDimension(SolarSystem.EARTH, CLOCK, new Local(0.0, SEA + 3_990.0, 0.0, 0.0, -5.0, 0.0), 1_000.0);
        Quaterniond inertial = AttitudeMath.localToInertial(SolarSystem.EARTH, CLOCK, 0.0, 0.0, new Quaterniond());
        Snapshot inSpace = new Snapshot(FlightMode.FLIGHT, state, inertial, V3.ZERO, SasMode.OFF, 0.0, false, null, 1_000.0);
        ZoneAccess none = new ZoneAccess() {
            @Override
            public boolean hasSpace() {
                return true;
            }

            @Override
            public boolean hasBody(SolarSystem.Body body) {
                return false;
            }
        };
        Result r = step(inSpace, thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), none);
        assertEquals(Event.NONE, r.event());
        assertNull(r.next().zoneBody());
    }

    // ------------------------------------------------------------------ pouso e batida

    @Test
    void touchingDownSlowlyAndUprightIsASoftLandingSettledOnTheGround() {
        Quaterniond yawed = new Quaterniond(RocketAttitude.fromYaw(90.0).x, RocketAttitude.fromYaw(90.0).y, RocketAttitude.fromYaw(90.0).z,
                RocketAttitude.fromYaw(90.0).w);
        Snapshot s = flyingUpright(SEA + BOX.hy() + 0.4, -0.6, yawed);
        Result r = step(s, thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(Event.SOFT_LANDING, r.event());
        assertEquals(FlightMode.LANDED, r.next().mode());
        assertNull(r.next().state());
        assertEquals(SEA + BOX.hy(), r.local().y(), 1e-9, "a base fica sobre o terreno");
        assertEquals(0.0, r.local().vy(), 0.0);
        assertEquals(s.state().fuelMb(), r.next().fuelMb(), 1.0, "o combustível que sobrou fica com a entidade");
        assertEquals(90.0, RocketAttitude.yawDegrees(new Quaternionf((float) r.localAttitude().x, (float) r.localAttitude().y,
                (float) r.localAttitude().z, (float) r.localAttitude().w)), 0.5, "o giro em torno da vertical se mantém");
        assertEquals(0.0, RocketAttitude.tiltDegrees(new Quaternionf((float) r.localAttitude().x, (float) r.localAttitude().y,
                (float) r.localAttitude().z, (float) r.localAttitude().w)), 0.01, "alinhado ao chão");
    }

    @Test
    void touchingDownFastIsACrashWithTheImpactSpeeds() {
        Snapshot s = flyingUpright(SEA + BOX.hy() + 0.4, -5.0, new Quaterniond());
        Result r = step(s, thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(Event.CRASH, r.event());
        assertEquals(5.0, Math.abs(r.impactVertical()), 0.3, "impactVertical = " + r.impactVertical());
        assertTrue(r.local().y() >= SEA + BOX.hy() - 1e-9, "o ponto de impacto não fica dentro do terreno");
    }

    @Test
    void touchingDownTiltedMoreThanFifteenDegreesIsACrashEvenWhenSlow() {
        Quaterniond tilted = new Quaterniond().rotationX(Math.toRadians(30.0));
        Snapshot s = flyingUpright(SEA + 3.0 + 0.4, -0.5, tilted);
        Result r = step(s, thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(Event.CRASH, r.event());
        assertEquals(30.0, r.tiltDegrees(), 1.0);
    }

    @Test
    void touchingDownSidewaysFastOverTheGroundIsACrash() {
        FlightState state = FrameBridge.fromDimension(SolarSystem.EARTH, CLOCK, new Local(0.0, SEA + 3.4, 0.0, 2.0, -0.4, 0.0), 1_000.0);
        Quaterniond inertial = AttitudeMath.localToInertial(SolarSystem.EARTH, CLOCK, 0.0, 0.0, new Quaterniond());
        Snapshot s = new Snapshot(FlightMode.FLIGHT, state, inertial, V3.ZERO, SasMode.OFF, 0.0, false, SolarSystem.EARTH, 1_000.0);
        Result r = step(s, thrust(0.0), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(Event.CRASH, r.event());
        assertEquals(2.0, r.impactHorizontal(), 0.3);
    }

    @Test
    void anUnknownGroundNeverTouches() {
        Snapshot s = flyingUpright(SEA + 3.0, -5.0, new Quaterniond());
        Result r = FlightMachine.step(s, thrust(0.0), STATS, BOX, CLOCK, 1.0, (x, z, ex, ez, lo, hi) -> Double.NaN, ALL, FULL,
                onThePad(SolarSystem.EARTH), new Quaterniond());
        assertEquals(Event.NONE, r.event());
        assertEquals(FlightMode.FLIGHT, r.next().mode());
    }

    @Test
    void theGroundIsOnlyTestedBelowTheContactAltitude() {
        boolean[] called = {false};
        Snapshot s = flyingUpright(SEA + FlightMachine.CONTACT_ALTITUDE + 100.0, -5.0, new Quaterniond());
        FlightMachine.step(s, thrust(0.0), STATS, BOX, CLOCK, 1.0, (x, z, ex, ez, lo, hi) -> {
            called[0] = true;
            return ground();
        }, ALL, FULL, onThePad(SolarSystem.EARTH), new Quaterniond());
        assertFalse(called[0]);
    }

    // ------------------------------------------------------------------ paraquedas

    @Test
    void theParachuteOpensOnlyBelowEightBlocksPerTickInTheAtmosphere() {
        Snapshot slow = flyingUpright(SEA + 1_000.0, -5.0, new Quaterniond());
        Result opening = step(slow, new Command(0, 0, 0, 0.0, true, false), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(FlightMode.PARACHUTE, opening.next().mode());
        assertTrue(opening.next().state().chute() > 0.0);
        assertFalse(opening.next().chuteRequested(), "o pedido some quando o paraquedas abre");

        Snapshot fast = flyingUpright(SEA + 1_000.0, -20.0, new Quaterniond());
        Result refused = step(fast, new Command(0, 0, 0, 0.0, true, false), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(FlightMode.FLIGHT, refused.next().mode());
        assertTrue(refused.next().chuteRequested(), "o pedido fica armado até poder abrir");
        // sem pedir de novo, o pedido armado abre sozinho quando a velocidade cai
        Snapshot nowSlow = new Snapshot(FlightMode.FLIGHT, flyingUpright(SEA + 1_000.0, -5.0, new Quaterniond()).state(),
                refused.next().attitude(), V3.ZERO, SasMode.OFF, 0.0, true, SolarSystem.EARTH, 1_000.0);
        Result armed = step(nowSlow, Command.IDLE, onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(FlightMode.PARACHUTE, armed.next().mode());
    }

    @Test
    void theParachuteDoesNotOpenInVacuum() {
        Snapshot nearEdge = flyingUpright(SEA + 4_500.0, -5.0, new Quaterniond());
        Result r = step(nearEdge, new Command(0, 0, 0, 0.0, true, false), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(FlightMode.FLIGHT, r.next().mode(), "sem atmosfera a 4.500 blocos");
    }

    // ------------------------------------------------------------------ SAS e comandos

    @Test
    void theSasKeyCyclesTheModeEvenOnTheGround() {
        Snapshot s = Snapshot.landed(SolarSystem.EARTH, FULL);
        SasMode expected = SasMode.OFF;
        for (int i = 0; i < 5; i++) {
            Result r = step(s, new Command(0, 0, 0, 0.0, false, true), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
            expected = expected.next();
            assertEquals(expected, r.next().sas());
            s = r.next();
        }
    }

    @Test
    void aPitchCommandTurnsTheNoseAndTheInertialAttitudeFollows() {
        Snapshot s = flyingUpright(SEA + 1_000.0, 0.0, new Quaterniond());
        V3 before = AttitudeMath.noseDirection(s.attitude());
        Result r = step(s, new Command(1.0, 0, 0, 0.0, false, false), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        V3 after = AttitudeMath.noseDirection(r.next().attitude());
        assertTrue(before.angleTo(after) > 1e-4, "o nariz girou");
        assertTrue(r.next().spin().x() > 0.0, "e o giro de arfagem ficou");
    }

    @Test
    void flightStateRoundTripsThroughTheSnapshotRecords() {
        // o instantâneo é imutável por convenção: o passo não altera o anterior
        Snapshot s = flyingUpright(SEA + 1_000.0, 2.0, new Quaterniond());
        V3 rBefore = s.state().r();
        double[] qBefore = {s.attitude().x, s.attitude().y, s.attitude().z, s.attitude().w};
        step(s, new Command(1.0, 1.0, 1.0, 1.0, false, true), onThePad(SolarSystem.EARTH), new Quaterniond(), ALL);
        assertEquals(rBefore, s.state().r());
        assertEquals(qBefore[0], s.attitude().x, 0.0);
        assertEquals(qBefore[3], s.attitude().w, 0.0);
        assertEquals(SasMode.OFF, s.sas());
    }
}
