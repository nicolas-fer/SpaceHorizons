package com.spacehorizons.rocket;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.FrameBridge;
import com.spacehorizons.space.flight.MarsPlanner;
import com.spacehorizons.space.flight.Orbit;
import com.spacehorizons.space.flight.RocketStats;
import com.spacehorizons.space.flight.TransferPlanner;
import com.spacehorizons.space.flight.V3;

/**
 * Monta a telemetria do painel a partir do estado de voo (lógica pura): órbita (apoastro, periastro, inclinação, tempo até o
 * apoastro), velocidades, combustível e delta-v, TWR local e o planejador da Lua quando em órbita da Terra.
 */
public final class RocketTelemetryBuilder {
    private RocketTelemetryBuilder() {
    }

    /**
     * A telemetria do estado {@code s} no relógio {@code clock} (taxa {@code clockRate}). {@code mode} é o modo de voo,
     * {@code sas} o do SAS, {@code throttle} o acelerador aplicado, {@code inSpace} se o foguete está na dimensão de espaço e
     * {@code held} se o passo está segurado esperando chunks.
     */
    public static RocketTelemetryPayload build(FlightState s, FlightMode mode, SasMode sas, double throttle, boolean inSpace,
            RocketStats stats, double clock, double clockRate, boolean held) {
        return build(s, mode, sas, throttle, inSpace, stats, clock, clockRate, held, (byte) 0, (byte) 0);
    }

    /** Idem, com o estado da pré-carga do local de pouso ({@code landingState} 0, 1 ou 2 e a porcentagem de chunks carregados). */
    public static RocketTelemetryPayload build(FlightState s, FlightMode mode, SasMode sas, double throttle, boolean inSpace,
            RocketStats stats, double clock, double clockRate, boolean held, byte landingState, byte landingPercent) {
        BodyPhysics bp = BodyPhysics.of(s.body());
        V3 r = s.r();
        V3 up = r.unit();
        V3 groundRelative = s.v().minus(FrameBridge.rotationVelocity(s.body(), clock, clockRate, r));
        double radial = groundRelative.dot(up);
        double horizontal = groundRelative.minus(up.times(radial)).length();

        Orbit orbit = Orbit.of(s, clock);
        double apoapsis = orbit.bound() ? orbit.apoapsis() - bp.radius() : Double.POSITIVE_INFINITY;
        double periapsis = orbit.periapsis() - bp.radius();
        double timeToApoapsis = orbit.bound() ? orbit.timeTo(Math.PI) : Double.NaN;

        byte plannerStatus = -1;
        float phaseNow = 0.0F;
        float phaseIdeal = 0.0F;
        float tliDeltaV = 0.0F;
        boolean burnNow = false;
        if (s.body() == SolarSystem.EARTH) {
            TransferPlanner.Plan plan = TransferPlanner.moon(s, clock, clockRate);
            plannerStatus = (byte) plan.status().ordinal();
            if (plan.usable()) {
                phaseNow = (float) plan.currentPhaseDeg();
                phaseIdeal = (float) plan.idealPhaseDeg();
                tliDeltaV = (float) plan.tliDeltaV();
                burnNow = plan.burnNow();
            }
        }
        byte marsStatus = -1;
        float marsPhaseNow = 0.0F;
        float marsPhaseIdeal = Float.NaN;
        float marsDeltaV = 0.0F;
        boolean marsBurnNow = false;
        float marsTimeToBurn = Float.NaN;
        if (s.body() == SolarSystem.EARTH) {
            MarsPlanner.Plan mars = MarsPlanner.plan(s, clock, clockRate);
            marsStatus = (byte) mars.status().ordinal();
            if (mars.usable()) {
                marsPhaseNow = (float) mars.currentPhaseDeg();
                marsPhaseIdeal = (float) mars.idealPhaseDeg();
                marsDeltaV = (float) mars.injectionDeltaV();
                marsBurnNow = mars.burnNow();
                marsTimeToBurn = (float) mars.timeToWindowTicks();
            }
        }
        // o foguete químico (Tier 1) com a conta de sempre; com motores do Tier 2, a carga por combustível (o painel mostra o total com massa)
        boolean chemical = stats.chemicalOnly() && s.fuel().onlyChemical();
        double fuelMb = chemical ? s.fuelMb() : s.fuel().propellantMb();
        double deltaV = chemical ? stats.deltaV(s.fuelMb()) : stats.deltaV(s.fuel());
        double twr = chemical ? stats.twr(s.fuelMb(), bp.gravityAt(r.length())) : stats.twr(s.fuel(), bp.gravityAt(r.length()));
        return new RocketTelemetryPayload(s.body().planet().id().getPath(), inSpace, bp.altitude(r.length()), radial, horizontal,
                s.v().length(), apoapsis, periapsis, Math.toDegrees(orbit.inclination()), timeToApoapsis, fuelMb,
                stats.fuelCapacityMb(), deltaV, twr, (float) throttle,
                (byte) sas.ordinal(), (byte) mode.ordinal(), (float) s.heat(), (float) s.chute(), plannerStatus, phaseNow, phaseIdeal,
                tliDeltaV, burnNow, held, landingState, landingPercent, marsStatus, marsPhaseNow, marsPhaseIdeal, marsDeltaV, marsBurnNow, marsTimeToBurn);
    }
}
