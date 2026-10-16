package com.spacehorizons.space.flight;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.SpaceFrame;

/**
 * Ponte entre o estado de voo (inercial, relativo ao corpo dominante, eixos do espaço) e as coordenadas das dimensões.
 *
 * <ul>
 *   <li><b>Dimensão do corpo</b> ({@link #toDimension}, {@link #fromDimension}): posição no mapeamento esférico do
 *       {@link SpaceFrame} (x e z em blocos de arco, y altura) e velocidade MÉTRICA LOCAL relativa ao chão (eixos
 *       leste, cima, norte), isto é, a inercial menos {@code ω × r}. Um foguete parado na pista tem velocidade local 0;
 *       na Lua isso é a velocidade inercial relativa ao centro de ~0,4 a 0,7 blocos/tick, por causa da rotação
 *       sincronizada.</li>
 *   <li><b>Dimensão de espaço</b> ({@link #toSpaceDimension}, {@link #fromSpaceDimension}): posição absoluta no espaço
 *       pelo mapeamento de {@link SpaceDimension} ({@code (x,y,z)_dim = (sz, sx + 20000, sy)}) e velocidade ABSOLUTA
 *       (do referencial da Terra), com os mesmos eixos da posição.</li>
 * </ul>
 *
 * Só a conversão usa o relógio e a taxa dele ({@code clockRate}: ticks de relógio por tick de jogo, 1 no normal, 0 com o
 * relógio pausado); a integração é toda em eixos inerciais. A velocidade do centro de um corpo ({@code velocityAt}) é em
 * blocos por tick de RELÓGIO, por isso é multiplicada por {@code clockRate} ao virar velocidade por tick de jogo.
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
        SolarSystem.Pose pose = body.poseAt(clock);
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
        SolarSystem.Pose pose = body.poseAt(clock);
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
     * Velocidade ({@code ω × r}, eixos do espaço, blocos por tick de JOGO) do ponto do corpo que está em {@code r} em
     * relação ao centro: a que o chão tem por causa da rotação. Zero para a Terra (parada no referencial do espaço).
     */
    public static V3 rotationVelocity(SolarSystem.Body body, double clock, double clockRate, V3 r) {
        if (body == SolarSystem.EARTH || clockRate == 0.0) {
            return V3.ZERO;
        }
        double[] center = new double[3];
        body.positionAt(clock, center);
        double[] ground = new double[3];
        SpaceFrame.groundVelocity(body, clock, center[0] + r.x(), center[1] + r.y(), center[2] + r.z(), ground);
        double[] centerVelocity = new double[3];
        body.velocityAt(clock, centerVelocity);
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
     * Estado inercial relativo ao corpo → coordenadas da dimensão de espaço: posição absoluta ({@code r} mais o centro do
     * corpo) e velocidade absoluta ({@code v} mais a do centro), ambas nos eixos da dimensão.
     */
    public static Local toSpaceDimension(SolarSystem.Body body, double clock, double clockRate, V3 r, V3 v) {
        double[] center = new double[3];
        body.positionAt(clock, center);
        double[] centerVelocity = new double[3];
        body.velocityAt(clock, centerVelocity);
        double[] p = spaceToSpaceDim(r.plus(V3.of(center)));
        V3 vAbs = v.plus(V3.of(centerVelocity).times(clockRate));
        V3 vd = spaceVectorToSpaceDim(vAbs);
        return new Local(p[0], p[1], p[2], vd.x(), vd.y(), vd.z());
    }

    /** Coordenadas da dimensão de espaço → estado relativo ao corpo {@code body} (o dominante, escolhido pelo chamador). */
    public static FlightState fromSpaceDimension(SolarSystem.Body body, double clock, double clockRate, Local l,
            double fuelMb) {
        double[] center = new double[3];
        body.positionAt(clock, center);
        double[] centerVelocity = new double[3];
        body.velocityAt(clock, centerVelocity);
        V3 abs = spaceDimToSpace(l.x(), l.y(), l.z());
        V3 vAbs = spaceDimVectorToSpace(l.vx(), l.vy(), l.vz());
        return new FlightState(body, abs.minus(V3.of(center)), vAbs.minus(V3.of(centerVelocity).times(clockRate)), fuelMb,
                0.0, 0.0);
    }
}
