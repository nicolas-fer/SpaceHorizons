package com.spacehorizons.ship;

import com.spacehorizons.rocket.RocketCensus;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketSpecs;
import com.spacehorizons.space.flight.RocketStats;

/**
 * Empuxo de decolagem de uma nave montada no chão (docs/PLANO_NAVES.md, fase 2a): a soma dos {@code ship_impulse_engine} com o bocal
 * para BAIXO mais os motores do foguete ({@code rocket_engine}, avulsos e formados em 2x2/3x3, pelo censo do foguete). Só o de baixo
 * empurra a nave para cima; o de cima e o horizontal não contam (o horizontal empurra no plano, o de cima puxa para o chão). Lógica
 * pura, sem o jogo, para ser testada.
 */
public final class ShipLiftoff {
    private ShipLiftoff() {
    }

    /** Quantos motores de impulso a nave tem por sentido do bocal. */
    public record Nozzles(int up, int down, int horizontal) {
    }

    /** Empuxo de decolagem, em t·blocos/tick²: os bocais para baixo mais o empuxo dos motores de foguete do censo. */
    public static double thrust(Nozzles nozzles, RocketCensus rocket) {
        return nozzles.down() * ShipSpecs.ENGINE_THRUST + RocketSpecs.thrustOf(rocket);
    }

    /**
     * Estatísticas da nave para a física do voo: o censo do foguete (massa seca, tanques e combustível, arrasto) com o empuxo dos bocais
     * para baixo somado ao dos motores de foguete. O Isp é o do conjunto: {@code ΣF / Σ(F/Isp)}, com o Isp dos bocais de impulso
     * ({@link ShipSpecs#ENGINE_ISP_TICKS}) e o dos motores de foguete ({@link RocketSpecs#ispOf}).
     */
    public static RocketStats stats(int nozzlesDown, RocketCensus rocket) {
        RocketStats base = RocketSpecs.statsFor(rocket);
        double nozzleThrust = Math.max(0, nozzlesDown) * ShipSpecs.ENGINE_THRUST;
        double rocketThrust = base.thrust();
        double thrust = nozzleThrust + rocketThrust;
        if (nozzleThrust <= 0.0) {
            return base;
        }
        double flow = nozzleThrust / ShipSpecs.ENGINE_ISP_TICKS + (rocketThrust > 0.0 ? rocketThrust / base.ispTicks() : 0.0);
        return new RocketStats(base.dryMassT(), thrust, thrust / flow, base.fuelCapacityMb(), base.dragK0(), base.parachuteK0(),
                base.engines() + nozzlesDown, base.tanks(), base.parachutes(), base.legs());
    }

    /**
     * Recusa a decolagem sem nenhum motor para baixo: nem bocal de impulso para baixo, nem motor de foguete no censo. Lança
     * {@link RocketException} com {@link RocketException.Reason#NO_LIFTOFF_ENGINE}; devolve o empuxo de decolagem quando passa.
     */
    public static double require(Nozzles nozzles, RocketCensus rocket) throws RocketException {
        if (nozzles.down() <= 0 && rocket.engines() <= 0) {
            throw new RocketException(RocketException.Reason.NO_LIFTOFF_ENGINE);
        }
        return thrust(nozzles, rocket);
    }
}
