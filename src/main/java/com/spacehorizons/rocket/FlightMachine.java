package com.spacehorizons.rocket;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import com.spacehorizons.rocket.GroundContact.Extents;
import com.spacehorizons.rocket.GroundContact.Ground;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.AttitudeMath;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.Control;
import com.spacehorizons.space.flight.FlightIntegrator;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.FrameBridge;
import com.spacehorizons.space.flight.FrameBridge.Local;
import com.spacehorizons.space.flight.LandingRules;
import com.spacehorizons.space.flight.RocketStats;
import com.spacehorizons.space.flight.SoiTracker;
import com.spacehorizons.space.flight.V3;
import com.spacehorizons.space.flight.Zones;

/**
 * A máquina de estados do voo do foguete, SEPARADA da entidade (lógica pura, testável): {@code LANDED} (preso ao chão da
 * dimensão; a física não roda) → {@code FLIGHT} (estado inercial, em I, relativo ao corpo dominante) → {@code PARACHUTE}
 * (subestado: o paraquedas começou a abrir) → pouso suave ({@code LANDED} de novo) ou batida.
 *
 * <p>O passo {@link #step} é uma FUNÇÃO: recebe o instantâneo anterior e devolve o seguinte (mais o que a entidade precisa
 * aplicar), sem mudar nada. Quem chama decide se confirma (guarda o instantâneo novo) ou descarta (por exemplo, segura o
 * passo porque falta um chunk do destino de uma troca de dimensão).
 *
 * <p>Zona: o foguete vive na dimensão de um corpo ({@code zoneBody}) até a altitude de saída ({@link Zones}) e no espaço
 * aberto ({@code zoneBody == null}) acima dela. A troca devolve {@link Event#LEAVE_ZONE} ou {@link Event#ENTER_ZONE} com as
 * coordenadas do DESTINO; o instantâneo novo já está na zona nova. Se a dimensão de destino não existe no mundo
 * ({@link ZoneAccess}), o foguete segue na zona atual.
 */
public final class FlightMachine {
    /** Abaixo desta altitude (sobre o nível do mar do corpo) o contato com o chão é testado. */
    public static final double CONTACT_ALTITUDE = 400.0;
    private static final V3 UP = new V3(0.0, 1.0, 0.0);

    private FlightMachine() {
    }

    /**
     * O estado do voo entre dois ticks. Em {@code LANDED} só valem o modo de SAS, o acelerador e a zona (o combustível, a
     * posição e a atitude pousados são da entidade); em voo, {@code state} é a verdade (inclusive o combustível) e
     * {@code attitude} é o quaternion INERCIAL (eixos de I). Os objetos mutáveis (quaternion) não são alterados depois
     * de criados.
     */
    public record Snapshot(FlightMode mode, @Nullable FlightState state, Quaterniond attitude, V3 spin, SasMode sas,
            double throttle, boolean chuteRequested, SolarSystem.@Nullable Body zoneBody, double fuelMb) {

        /** Pousado na dimensão de {@code zoneBody}. */
        public static Snapshot landed(SolarSystem.@Nullable Body zoneBody, double fuelMb) {
            return new Snapshot(FlightMode.LANDED, null, new Quaterniond(), V3.ZERO, SasMode.OFF, 0.0, false, zoneBody, fuelMb);
        }

        public boolean flying() {
            return this.mode != FlightMode.LANDED;
        }

        Snapshot withSasAndThrottle(SasMode newSas, double newThrottle) {
            return new Snapshot(this.mode, this.state, this.attitude, this.spin, newSas, newThrottle, this.chuteRequested, this.zoneBody,
                    this.fuelMb);
        }
    }

    /** O comando de um tick: giros de -1 a 1, acelerador de 0 a 1, pedido do paraquedas e "próximo modo do SAS". */
    public record Command(double pitch, double yaw, double roll, double throttle, boolean parachute, boolean nextSas) {
        public static final Command IDLE = new Command(0.0, 0.0, 0.0, 0.0, false, false);
    }

    /** Meias-dimensões da caixa do foguete nos eixos do retrato (blocos). */
    public record Box(double hx, double hy, double hz) {
    }

    /** Quais dimensões existem no mundo (um mundo criado antes da dimensão de espaço não a tem). */
    public interface ZoneAccess {
        boolean hasSpace();

        boolean hasBody(SolarSystem.Body body);

        /**
         * Altura do terreno (coordenada y da dimensão do corpo) na coluna {@code (x, z)}; só decide a entrada forçada na
         * dimensão do corpo ({@link ZoneSwitch}). O padrão é o nível do mar.
         */
        default double terrainHeight(SolarSystem.Body body, double x, double z) {
            return body.planet().seaLevel();
        }
    }

    /** O que aconteceu no tick. */
    public enum Event {
        NONE,
        /** Saiu do chão neste tick. */
        LIFTOFF,
        /** Tocou o chão devagar, de pé: está pousado. */
        SOFT_LANDING,
        /** Tocou o chão rápido, inclinado: o foguete se desfaz. */
        CRASH,
        /** Passou da dimensão do corpo para o espaço aberto. */
        LEAVE_ZONE,
        /** Passou do espaço aberto para a dimensão de um corpo. */
        ENTER_ZONE
    }

    /**
     * O resultado de um passo. {@code local} e {@code localAttitude} estão nas coordenadas da dimensão em que o foguete
     * fica DEPOIS do passo (a de destino se for uma troca; {@code toBody} é o corpo dela, null = espaço aberto). Em
     * {@link Event#SOFT_LANDING}, a posição já está assentada no chão e a velocidade é zero; em {@link Event#CRASH}, os
     * campos de impacto têm a velocidade e a inclinação do contato.
     */
    public record Result(Snapshot next, Event event, SolarSystem.@Nullable Body toBody, Local local, Quaterniond localAttitude,
            double impactVertical, double impactHorizontal, double tiltDegrees) {

        public boolean transfer() {
            return this.event == Event.LEAVE_ZONE || this.event == Event.ENTER_ZONE;
        }
    }

    /**
     * Um tick. {@code clock} é o relógio no início do tick e {@code clockRate} os ticks de relógio por tick de jogo.
     * {@code landedFuelMb}, {@code landedLocal} e {@code landedLocalAttitude} são o combustível, a posição (centro da
     * caixa, em coordenadas da dimensão) e a atitude local do foguete pousado, só usados quando {@code s} é {@code LANDED}.
     */
    public static Result step(Snapshot s, Command c, RocketStats stats, Box box, double clock, double clockRate, Ground ground,
            ZoneAccess zones, double landedFuelMb, Local landedLocal, Quaterniondc landedLocalAttitude) {
        SasMode sas = c.nextSas() ? s.sas().next() : s.sas();
        double throttle = Math.max(0.0, Math.min(1.0, c.throttle()));
        Snapshot cur = s.withSasAndThrottle(sas, throttle);
        Event event = Event.NONE;

        if (cur.mode() == FlightMode.LANDED) {
            SolarSystem.Body zone = cur.zoneBody();
            if (zone == null) {
                return stay(cur, landedLocal, landedLocalAttitude, landedFuelMb);
            }
            double g = BodyPhysics.of(zone).surfaceGravity();
            boolean lifts = stats.engines() > 0 && landedFuelMb > 0.0 && throttle > 0.0
                    && stats.acceleration(landedFuelMb, throttle) > g;
            if (!lifts) {
                return stay(cur, landedLocal, landedLocalAttitude, landedFuelMb);
            }
            // decolagem: o estado inercial nasce da posição na dimensão (com ω×r) e a atitude local vira inercial
            Local atRest = new Local(landedLocal.x(), landedLocal.y(), landedLocal.z(), 0.0, 0.0, 0.0);
            FlightState state = FrameBridge.fromDimension(zone, clock, clockRate, atRest, landedFuelMb);
            Quaterniond inertial = AttitudeMath.localToInertial(zone, clock, landedLocal.x(), landedLocal.z(), landedLocalAttitude);
            cur = new Snapshot(FlightMode.FLIGHT, state, inertial, V3.ZERO, sas, throttle, false, zone, landedFuelMb);
            event = Event.LIFTOFF;
        }

        // ---- em voo: atitude, empuxo, integração, troca de corpo
        FlightState state = cur.state();
        SolarSystem.Body zone = cur.zoneBody();
        V3 reference = zone != null ? state.v().minus(FrameBridge.rotationVelocity(state.body(), clock, clockRate, state.r())) : state.v();
        AttitudeControl.Result att = AttitudeControl.step(cur.attitude(), cur.spin(), sas, c.pitch(), c.yaw(), c.roll(),
                AttitudeMath.sasTarget(sas, reference, state.r()));
        boolean wantsChute = cur.chuteRequested() || c.parachute();
        Control control = new Control(throttle, AttitudeMath.noseDirection(att.attitude()), wantsChute);
        FlightState moved = FlightIntegrator.step(state, control, stats, clock);
        double clockEnd = clock + clockRate;
        moved = SoiTracker.update(moved, clockEnd, clockRate);
        boolean chuteStillWanted = wantsChute && moved.chute() <= 0.0;
        FlightMode mode = moved.chute() > 0.0 ? FlightMode.PARACHUTE : FlightMode.FLIGHT;

        if (zone != null) {
            FlightState inZone = moved.body() == zone ? moved : rebase(moved, zone, clockEnd, clockRate);
            Local loc = FrameBridge.toDimension(zone, clockEnd, clockRate, inZone.r(), inZone.v());
            var planet = zone.planet();
            double altitude = loc.y() - planet.seaLevel();
            double horizontal = ZoneSwitch.horizontalSpeed(loc);
            // o terreno só é consultado quando o resto da saída antecipada (alto e rápido) já vale
            boolean leaves = Zones.shouldLeave(planet, loc.y()) || (ZoneSwitch.mayLeaveEarly(planet, altitude, horizontal)
                    && ZoneSwitch.shouldLeaveEarly(planet, altitude, loc.y() - zones.terrainHeight(zone, loc.x(), loc.z()), horizontal));
            if (leaves && zones.hasSpace()) {
                Local dest = FrameBridge.toSpaceDimension(moved.body(), clockEnd, clockRate, moved.r(), moved.v());
                Quaterniond localAtt = AttitudeMath.inertialToSpaceLocal(att.attitude(), clockEnd);
                Snapshot next = new Snapshot(mode, moved, att.attitude(), att.spin(), sas, throttle, chuteStillWanted, null, moved.fuelMb());
                return new Result(next, Event.LEAVE_ZONE, null, dest, localAtt, 0.0, 0.0, 0.0);
            }
            Quaterniond la = AttitudeMath.inertialToLocal(zone, clockEnd, loc.x(), loc.z(), att.attitude());
            if (loc.y() - planet.seaLevel() < CONTACT_ALTITUDE) {
                Extents e = GroundContact.extents(la, box.hx(), box.hy(), box.hz());
                double vVertical = loc.vy();
                double bottom = loc.y() - e.y();
                double top = ground.top(loc.x(), loc.z(), e.x(), e.z(), bottom - 1.0, bottom + Math.max(2.0, 1.0 - vVertical));
                if (GroundContact.touching(loc.y(), e.y(), top, vVertical)) {
                    return contact(cur, moved, loc, la, top, e, box, vVertical, Math.hypot(loc.vx(), loc.vz()), sas, throttle);
                }
            }
            Snapshot next = new Snapshot(mode, inZone, att.attitude(), att.spin(), sas, throttle, chuteStillWanted, zone, inZone.fuelMb());
            return new Result(next, event, zone, loc, la, 0.0, 0.0, 0.0);
        }

        // ---- no espaço aberto
        // as zonas são do espaço E (preso à Terra): a posição absoluta inercial vira E antes de comparar
        V3 absolute = FrameBridge.absoluteInEarthFixed(moved.body(), clockEnd, moved.r());
        SolarSystem.Body enter = Zones.shouldEnter(clockEnd, absolute);
        if (enter != null && zones.hasBody(enter)) {
            FlightState rebased = enter == moved.body() ? moved : rebase(moved, enter, clockEnd, clockRate);
            Local dest = FrameBridge.toDimension(enter, clockEnd, clockRate, rebased.r(), rebased.v());
            double height = dest.y() - zones.terrainHeight(enter, dest.x(), dest.z());
            // rápido demais para a dimensão do corpo carregar o terreno: segue na dimensão de espaço (vazia), freando no ar
            if (ZoneSwitch.canEnter(enter.planet(), height, ZoneSwitch.horizontalSpeed(dest))) {
                Quaterniond localAtt = AttitudeMath.inertialToLocal(enter, clockEnd, dest.x(), dest.z(), att.attitude());
                Snapshot next = new Snapshot(mode, rebased, att.attitude(), att.spin(), sas, throttle, chuteStillWanted, enter, rebased.fuelMb());
                return new Result(next, Event.ENTER_ZONE, enter, dest, localAtt, 0.0, 0.0, 0.0);
            }
        }
        Local loc = FrameBridge.toSpaceDimension(moved.body(), clockEnd, clockRate, moved.r(), moved.v());
        Quaterniond la = AttitudeMath.inertialToSpaceLocal(att.attitude(), clockEnd);
        Snapshot next = new Snapshot(mode, moved, att.attitude(), att.spin(), sas, throttle, chuteStillWanted, null, moved.fuelMb());
        return new Result(next, event, null, loc, la, 0.0, 0.0, 0.0);
    }

    /** Pousado e sem empuxo suficiente: nada muda além do SAS e do acelerador. */
    private static Result stay(Snapshot cur, Local landedLocal, Quaterniondc landedLocalAttitude, double fuelMb) {
        Snapshot next = new Snapshot(FlightMode.LANDED, null, cur.attitude(), V3.ZERO, cur.sas(), cur.throttle(), false, cur.zoneBody(), fuelMb);
        return new Result(next, Event.NONE, cur.zoneBody(), landedLocal, new Quaterniond(landedLocalAttitude), 0.0, 0.0, 0.0);
    }

    /** O contato com o chão: pouso suave (de pé, parado, sobre o terreno) ou batida. */
    private static Result contact(Snapshot cur, FlightState moved, Local loc, Quaterniond la, double top, Extents e, Box box,
            double vVertical, double vHorizontal, SasMode sas, double throttle) {
        double tilt = AttitudeMath.tiltDegrees(la, UP);
        SolarSystem.Body zone = cur.zoneBody();
        if (LandingRules.contact(vVertical, vHorizontal, tilt) == LandingRules.Outcome.SOFT_LANDING) {
            Quaterniond upright = uprightYaw(la);
            Local rest = new Local(loc.x(), top + box.hy(), loc.z(), 0.0, 0.0, 0.0);
            Snapshot landed = new Snapshot(FlightMode.LANDED, null, new Quaterniond(), V3.ZERO, sas, throttle, false, zone, moved.fuelMb());
            return new Result(landed, Event.SOFT_LANDING, zone, rest, upright, vVertical, vHorizontal, tilt);
        }
        Local at = new Local(loc.x(), Math.max(loc.y(), top + e.y()), loc.z(), loc.vx(), loc.vy(), loc.vz());
        Snapshot gone = new Snapshot(FlightMode.LANDED, null, new Quaterniond(), V3.ZERO, sas, 0.0, false, zone, 0.0);
        return new Result(gone, Event.CRASH, zone, at, la, vVertical, vHorizontal, tilt);
    }

    /** O foguete de pé com o giro em torno da vertical que tem (o nariz de um foguete a menos de 15° da vertical). */
    static Quaterniond uprightYaw(Quaterniondc local) {
        Vector3d north = local.transform(0.0, 0.0, -1.0, new Vector3d());
        double yaw = Math.atan2(north.x, -north.z);
        return new Quaterniond().rotationY(-yaw);
    }

    /** Muda a origem e o referencial da velocidade do estado para outro corpo (a posição e a velocidade absolutas se conservam). */
    public static FlightState rebase(FlightState s, SolarSystem.Body to, double clock, double clockRate) {
        if (s.body() == to) {
            return s;
        }
        double[] from = new double[3];
        double[] fromV = new double[3];
        s.body().inertialPositionAt(clock, from);
        s.body().inertialVelocityAt(clock, fromV);
        double[] dest = new double[3];
        double[] destV = new double[3];
        to.inertialPositionAt(clock, dest);
        to.inertialVelocityAt(clock, destV);
        V3 absolute = s.r().plus(V3.of(from));
        V3 absoluteV = s.v().plus(V3.of(fromV).times(clockRate));
        return s.withBody(to, absolute.minus(V3.of(dest)), absoluteV.minus(V3.of(destV).times(clockRate)));
    }
}
