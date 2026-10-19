package com.spacehorizons.space.flight;

import com.spacehorizons.space.InertialFrame;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.SpaceFrame;

/**
 * Ponte entre o estado de voo (INERCIAL, referencial I, relativo ao corpo dominante) e as coordenadas das dimensões, que
 * são do referencial preso à Terra (E). Ver {@link InertialFrame}.
 *
 * <ul>
 *   <li><b>Dimensão do corpo</b> ({@link #toDimension}, {@link #fromDimension}): posição no mapeamento esférico do
 *       {@link SpaceFrame} (x e z em blocos de arco, y altura) e velocidade MÉTRICA LOCAL relativa ao chão (eixos
 *       leste, cima, norte), isto é, a inercial menos {@code ω × r}. Um foguete parado na pista tem velocidade local 0 e,
 *       em I, a velocidade da rotação do corpo: ~27 blocos/tick (em média) no equador da Terra, vezes o cosseno da
 *       latitude; na Lua, a do centro mais ~0,4 a 0,7 blocos/tick da rotação sincronizada.</li>
 *   <li><b>Dimensão de espaço</b> ({@link #toSpaceDimension}, {@link #fromSpaceDimension}): posição absoluta em E
 *       pelo mapeamento de {@link SpaceDimension} ({@code (x,y,z)_dim = (sz, sx + 20000, sy)}) e velocidade ABSOLUTA em E
 *       (a do referencial da Terra), com os mesmos eixos da posição. O estado é guardado em I; a posição na dimensão vem
 *       de I→E a cada tick.</li>
 * </ul>
 *
 * Só a conversão usa o relógio e a taxa dele ({@code clockRate}: ticks de relógio por tick de jogo, 1 no normal, 0 com o
 * relógio pausado); a integração é toda em eixos inerciais. As velocidades de corpos e de rotação ({@code inertialVelocityAt},
 * {@code ω}) são em blocos por tick de RELÓGIO, por isso são multiplicadas por {@code clockRate} ao virar velocidade por tick
 * de jogo.
 *
 * <p>A velocidade métrica local não é a taxa de variação das coordenadas: x = R·lon anda 1/cos(lat) mais rápido perto
 * dos polos e R/r mais devagar em altitude (ver {@link Local#coordinateVelocity}). Para a entidade use a posição
 * absoluta a cada tick e deixe o {@code deltaMovement} ser a diferença entre duas posições.
 */
public final class FrameBridge {
    /** Posição e velocidade nas coordenadas de uma dimensão (ver a documentação da classe para o que é a velocidade). */
    public record Local(double x, double y, double z, double vx, double vy, double vz) {
        public V3 position() {
            return new V3(x, y, z);
        }

        public V3 velocity() {
            return new V3(vx, vy, vz);
        }

        /**
         * Taxa de variação das coordenadas (x, y, z) da dimensão do corpo {@code body} por tick, para a velocidade
         * métrica local desta posição: {@code dx = v_leste · R/(r cosφ)}, {@code dy = v_cima}, {@code dz = v_norte · R/r}.
         */
        public V3 coordinateVelocity(SolarSystem.Body body) {
            double radius = body.planet().radius();
            double r = radius + (y - body.planet().seaLevel());
            double cosLat = Math.max(1.0e-6, Math.cos(z / radius));
            return new V3(vx * radius / (r * cosLat), vy, vz * radius / r);
        }
    }

    private FrameBridge() {
    }

    // ------------------------------------------------------------------ dimensão do corpo

    /** {@link #toDimension(SolarSystem.Body, double, double, V3, V3)} com o relógio a taxa 1. */
    public static Local toDimension(SolarSystem.Body body, double clock, V3 r, V3 v) {
        return toDimension(body, clock, 1.0, r, v);
    }

    /**
     * Estado inercial relativo ao corpo → coordenadas da dimensão dele. {@code r} e {@code v} em eixos do espaço;
     * a velocidade devolvida é relativa ao chão, nos eixos locais (leste, cima, norte).
     */
    public static Local toDimension(SolarSystem.Body body, double clock, double clockRate, V3 r, V3 v) {
        SolarSystem.Pose pose = body.inertialPoseAt(clock);
        double[] fixed = new double[3];
        pose.unrotate(r.x(), r.y(), r.z(), fixed);
        double[] p = new double[3];
        SpaceFrame.fromBodyFixed(body, fixed[0], fixed[1], fixed[2], p);
        V3 relative = v.minus(rotationVelocity(body, clock, clockRate, r));
        double[] vb = new double[3];
        pose.unrotate(relative.x(), relative.y(), relative.z(), vb);
        double[] vl = new double[3];
        SpaceFrame.bodyToLocal(body, p[0], p[2], vb[0], vb[1], vb[2], vl);
        return new Local(p[0], p[1], p[2], vl[0], vl[1], vl[2]);
    }

    /** {@link #fromDimension(SolarSystem.Body, double, double, Local, double)} com o relógio a taxa 1. */
    public static FlightState fromDimension(SolarSystem.Body body, double clock, Local l, double fuelMb) {
        return fromDimension(body, clock, 1.0, l, fuelMb);
    }

    /**
     * Coordenadas da dimensão do corpo → estado de voo inercial relativo a ele. O aquecimento e o paraquedas voltam
     * zerados; quem tem o estado anterior copia com {@code withHeat}/{@code withChute}.
     */
    public static FlightState fromDimension(SolarSystem.Body body, double clock, double clockRate, Local l, double fuelMb) {
        double[] fixed = new double[3];
        SpaceFrame.toBodyFixed(body, l.x(), l.y(), l.z(), fixed);
        SolarSystem.Pose pose = body.inertialPoseAt(clock);
        double[] rs = new double[3];
        pose.rotate(fixed[0], fixed[1], fixed[2], rs);
        V3 r = new V3(rs[0], rs[1], rs[2]);
        double[] vb = new double[3];
        SpaceFrame.localToBody(body, l.x(), l.z(), l.vx(), l.vy(), l.vz(), vb);
        double[] vs = new double[3];
        pose.rotate(vb[0], vb[1], vb[2], vs);
        V3 v = new V3(vs[0], vs[1], vs[2]).plus(rotationVelocity(body, clock, clockRate, r));
        return new FlightState(body, r, v, fuelMb, 0.0, 0.0);
    }

    /**
     * Velocidade ({@code ω × r}, eixos inerciais, blocos por tick de JOGO) do ponto do corpo que está em {@code r} em
     * relação ao centro: a que o chão (e a atmosfera, que gira com ele) tem por causa da rotação. Na Terra é a rotação
     * diurna (média 2π·|r|·cos(lat)/24.000, ~27 blocos/tick no equador ao nível do mar); zero com o relógio pausado.
     */
    public static V3 rotationVelocity(SolarSystem.Body body, double clock, double clockRate, V3 r) {
        if (clockRate == 0.0) {
            return V3.ZERO;
        }
        double[] center = new double[3];
        body.inertialPositionAt(clock, center);
        double[] ground = new double[3];
        SpaceFrame.inertialGroundVelocity(body, clock, center[0] + r.x(), center[1] + r.y(), center[2] + r.z(), ground);
        double[] centerVelocity = new double[3];
        body.inertialVelocityAt(clock, centerVelocity);
        return new V3(ground[0] - centerVelocity[0], ground[1] - centerVelocity[1], ground[2] - centerVelocity[2])
                .times(clockRate);
    }

    // ------------------------------------------------------------------ dimensão de espaço

    /** Posição da dimensão de espaço {@code (x, y, z)} → posição absoluta no espaço (eixos do {@link SpaceFrame}). */
    public static V3 spaceDimToSpace(double x, double y, double z) {
        double[] out = new double[3];
        SpaceDimension.toSpace(x, y, z, out);
        return V3.of(out);
    }

    /** Posição absoluta no espaço → {@code {x, y, z}} da dimensão de espaço: {@code (sz, sx + 20000, sy)}. */
    public static double[] spaceToSpaceDim(V3 abs) {
        double[] out = new double[3];
        SpaceDimension.fromSpace(abs.x(), abs.y(), abs.z(), out);
        return out;
    }

    /** Vetor (velocidade, direção) da dimensão de espaço → eixos do espaço: só a rotação do mapeamento, sem o viés de Y. */
    public static V3 spaceDimVectorToSpace(double x, double y, double z) {
        return new V3(y, z, x);
    }

    /** Vetor dos eixos do espaço → eixos da dimensão de espaço. */
    public static V3 spaceVectorToSpaceDim(V3 v) {
        return new V3(v.z(), v.x(), v.y());
    }

    /**
     * Posição ABSOLUTA em E (preso à Terra, eixos do {@link SpaceFrame}, a que a dimensão de espaço usa) de um estado
     * relativo ao corpo {@code body}, cuja posição {@code r} é inercial: {@code Ry(φ)·(r + centro_I)}.
     */
    public static V3 absoluteInEarthFixed(SolarSystem.Body body, double clock, V3 r) {
        double[] center = new double[3];
        body.inertialPositionAt(clock, center);
        V3 absolute = r.plus(V3.of(center));
        double[] out = new double[3];
        InertialFrame.toEarthFixed(clock, absolute.x(), absolute.y(), absolute.z(), out);
        return V3.of(out);
    }

    /**
     * Estado inercial relativo ao corpo → coordenadas da dimensão de espaço: posição absoluta ({@code r} mais o centro do
     * corpo, levada de I a E) e velocidade absoluta em E (a inercial mais a do centro, girada, com o termo do giro da
     * Terra), ambas nos eixos da dimensão.
     */
    public static Local toSpaceDimension(SolarSystem.Body body, double clock, double clockRate, V3 r, V3 v) {
        double[] center = new double[3];
        body.inertialPositionAt(clock, center);
        double[] centerVelocity = new double[3];
        body.inertialVelocityAt(clock, centerVelocity);
        V3 absI = r.plus(V3.of(center));
        V3 vI = v.plus(V3.of(centerVelocity).times(clockRate));
        double[] pe = new double[3];
        InertialFrame.toEarthFixed(clock, absI.x(), absI.y(), absI.z(), pe);
        double[] ve = new double[3];
        InertialFrame.velocityToEarthFixed(clock, clockRate, absI.x(), absI.z(), vI.x(), vI.y(), vI.z(), ve);
        double[] p = spaceToSpaceDim(V3.of(pe));
        V3 vd = spaceVectorToSpaceDim(V3.of(ve));
        return new Local(p[0], p[1], p[2], vd.x(), vd.y(), vd.z());
    }

    /** Coordenadas da dimensão de espaço (E) → estado inercial relativo ao corpo {@code body} (o dominante, escolhido pelo chamador). */
    public static FlightState fromSpaceDimension(SolarSystem.Body body, double clock, double clockRate, Local l,
            double fuelMb) {
        double[] center = new double[3];
        body.inertialPositionAt(clock, center);
        double[] centerVelocity = new double[3];
        body.inertialVelocityAt(clock, centerVelocity);
        V3 abs = spaceDimToSpace(l.x(), l.y(), l.z());
        V3 vAbs = spaceDimVectorToSpace(l.vx(), l.vy(), l.vz());
        double[] absI = new double[3];
        InertialFrame.toInertial(clock, abs.x(), abs.y(), abs.z(), absI);
        double[] vI = new double[3];
        InertialFrame.velocityToInertial(clock, clockRate, absI[0], absI[2], vAbs.x(), vAbs.y(), vAbs.z(), vI);
        return new FlightState(body, V3.of(absI).minus(V3.of(center)), V3.of(vI).minus(V3.of(centerVelocity).times(clockRate)),
                fuelMb, 0.0, 0.0);
    }
}
