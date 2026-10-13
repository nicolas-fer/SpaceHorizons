package com.spacehorizons.space.travel;

import java.util.function.Predicate;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

/**
 * O piloto automático sem Minecraft: a cada tick avança o {@link FlightPlan}, devolve a posição e a velocidade já nas
 * coordenadas da dimensão em que o jogador está e, quando o voo entra na esfera de influência de outro corpo, faz a
 * transferência (BodyTransfer, com a velocidade convertida para o chão do corpo novo) e refaz o plano no referencial
 * dele. Java puro e testável; {@link Autopilot} só traduz o resultado em teleportes.
 *
 * Destino: um ponto (x, y, z) da dimensão de {@code target}. O plano de cada trecho vive no referencial do corpo atual
 * (ver FlightPlan), e o relógio vem de quem chama (relógio do Overworld em ticks).
 */
public final class FlightGuide {
    /** Resultado de um tick: onde o jogador deve estar e se trocou de corpo. */
    public record Step(BodyTransfer state, boolean transferred, boolean arrived) {
    }

    private final SolarSystem.Body target;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    private final Predicate<SolarSystem.Body> available;
    private SolarSystem.Body body;
    private FlightPlan plan;

    /**
     * @param start corpo em que o jogador está
     * @param x posição x do jogador na dimensão de {@code start}
     * @param y posição y
     * @param z posição z
     * @param target corpo de destino
     * @param available quais corpos têm dimensão no mundo (não se transfere para um que não tem)
     */
    public FlightGuide(SolarSystem.Body start, double x, double y, double z, double clock, SolarSystem.Body target, double tx,
            double ty, double tz, Predicate<SolarSystem.Body> available) {
        this.body = start;
        this.target = target;
        this.targetX = tx;
        this.targetY = ty;
        this.targetZ = tz;
        this.available = available;
        this.plan = newPlan(start, clock, x, y, z, 0.0);
    }

    private FlightPlan newPlan(SolarSystem.Body frame, double clock, double x, double y, double z, double initialSpeed) {
        double[] fixed = new double[3];
        SpaceFrame.toBodyFixed(frame, x, y, z, fixed);
        return FlightPlan.create(frame, SolarSystem.BODIES, clock, fixed, initialSpeed, target, targetX, targetY, targetZ);
    }

    /** Corpo cuja dimensão o jogador deve estar agora. */
    public SolarSystem.Body body() {
        return body;
    }

    public FlightPlan plan() {
        return plan;
    }

    /**
     * Um tick no instante {@code clock}. {@code yRot}/{@code xRot} são o olhar atual do jogador (só usados para o olhar
     * novo quando há transferência; num tick comum voltam inalterados no estado).
     */
    public Step tick(double clock, float yRot, float xRot) {
        boolean arrived = plan.advance(clock);
        double[] fixed = plan.position();
        double[] local = new double[3];
        SpaceFrame.fromBodyFixed(body, fixed[0], fixed[1], fixed[2], local);
        double x = TravelMath.normalizeX(body, local[0]);
        double y = local[1];
        double z = local[2];
        double[] step = plan.velocity();
        double[] v = new double[3];
        SpaceFrame.bodyToLocal(body, x, z, step[0], step[1], step[2], v);

        if (!arrived) {
            double[] space = new double[3];
            SpaceFrame.bodyFixedToSpace(body, clock, fixed[0], fixed[1], fixed[2], space);
            SolarSystem.Body dominant = SphereOfInfluence.dominant(SolarSystem.BODIES, body, clock, space[0], space[1], space[2]);
            if (dominant != body && available.test(dominant)) {
                BodyTransfer transfer = BodyTransfer.compute(body, dominant, clock, x, y, z, v[0], v[1], v[2], yRot, xRot);
                double speed = Math.sqrt(transfer.vx() * transfer.vx() + transfer.vy() * transfer.vy() + transfer.vz() * transfer.vz());
                body = dominant;
                plan = newPlan(dominant, clock, transfer.x(), transfer.y(), transfer.z(), speed);
                return new Step(transfer, true, false);
            }
        }
        return new Step(new BodyTransfer(body, x, y, z, v[0], v[1], v[2], yRot, xRot), false, arrived);
    }
}
