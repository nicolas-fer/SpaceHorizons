package com.spacehorizons.space.travel;

import java.util.ArrayList;
import java.util.List;

import com.spacehorizons.space.SolarSystem;

/**
 * Trajetória do piloto automático, toda no referencial do espaço: uma lista de pontos de passagem (normalmente só o
 * destino; se a reta cortaria um planeta, entram pontos intermediários acima da superfície) e a velocidade por tick.
 * Java puro: um tick de voo é {@link #advance()}, que move a posição e atualiza a velocidade.
 *
 * Velocidade: proporcional à altitude sobre o corpo mais próximo (sobe devagar do chão e acelera longe),
 * limitada por aceleração e, no último trecho, proporcional à distância que falta (para suave perto do destino).
 */
public final class FlightPlan {
    /** Blocos/tick de velocidade por bloco de altitude sobre o corpo mais próximo. */
    public static final double ALTITUDE_SPEED_FACTOR = 0.04;
    /** Blocos/tick por bloco de distância ao destino final (frenagem exponencial). */
    public static final double DISTANCE_SPEED_FACTOR = 0.06;
    public static final double MIN_SPEED = 0.5;
    /** 60 blocos/tick = 1.200 blocos/s: os ~47.000 blocos Terra–Lua levam menos de um minuto. */
    public static final double MAX_SPEED = 60.0;
    public static final double ACCELERATION_FACTOR = 1.06;
    public static final double ACCELERATION_ADD = 0.15;
    /** Altitude acima da superfície dos pontos intermediários que desviam de um planeta. */
    public static final double DETOUR_ALTITUDE = 6_000.0;
    /** A reta não pode passar mais perto que isto da superfície (raio + margem) de um corpo no meio do caminho. */
    private static final double SURFACE_MARGIN = 50.0;
    private static final int MAX_DETOUR_DEPTH = 8;
    private static final double ARRIVE_DISTANCE = 1.0;

    private final List<double[]> waypoints;
    private final double[] position;
    private final double[] velocity = new double[3];
    private int index;
    private double speed;
    private boolean finished;

    private FlightPlan(List<double[]> waypoints, double[] start) {
        this.waypoints = waypoints;
        this.position = start.clone();
    }

    /** Plano de {@code start} até {@code target} (ambos no espaço), desviando dos corpos de {@code bodies}. */
    public static FlightPlan create(List<SolarSystem.Body> bodies, double[] start, double[] target) {
        List<double[]> points = new ArrayList<>();
        appendSafe(bodies, start, target, points, 0);
        return new FlightPlan(points, start);
    }

    /** Acrescenta os pontos de a (exclusive) até b (inclusive), com desvios se o segmento corta um corpo. */
    private static void appendSafe(List<SolarSystem.Body> bodies, double[] a, double[] b, List<double[]> out, int depth) {
        SolarSystem.Body hit = depth < MAX_DETOUR_DEPTH ? firstHit(bodies, a, b) : null;
        if (hit == null) {
            out.add(b.clone());
            return;
        }
        double[] center = {hit.x(), hit.y(), hit.z()};
        double[] ab = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        double abLen2 = ab[0] * ab[0] + ab[1] * ab[1] + ab[2] * ab[2];
        double t = Math.max(0.0, Math.min(1.0,
                ((center[0] - a[0]) * ab[0] + (center[1] - a[1]) * ab[1] + (center[2] - a[2]) * ab[2]) / abLen2));
        // o desvio sai pelo lado em que o segmento passa mais perto do centro
        double[] dir = {a[0] + ab[0] * t - center[0], a[1] + ab[1] * t - center[1], a[2] + ab[2] * t - center[2]};
        double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        if (len < 1.0e-3 * hit.planet().radius()) {
            // reta passa pelo centro: sai por uma direção perpendicular ao segmento
            double[] axis = Math.abs(ab[1]) < 0.9 * Math.sqrt(abLen2) ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
            dir = new double[] {ab[1] * axis[2] - ab[2] * axis[1], ab[2] * axis[0] - ab[0] * axis[2],
                    ab[0] * axis[1] - ab[1] * axis[0]};
            len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        }
        double radius = hit.planet().radius() + DETOUR_ALTITUDE;
        double[] detour = {center[0] + dir[0] / len * radius, center[1] + dir[1] / len * radius,
                center[2] + dir[2] / len * radius};
        appendSafe(bodies, a, detour, out, depth + 1);
        appendSafe(bodies, detour, b, out, depth + 1);
    }

    /**
     * Primeiro corpo cuja esfera (raio + margem) o segmento atravessa. Um segmento que começa dentro da margem e se
     * afasta (decolando do chão) não conta: só vale quando o segmento caminha em direção ao centro.
     */
    static SolarSystem.Body firstHit(List<SolarSystem.Body> bodies, double[] a, double[] b) {
        double[] ab = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        double abLen2 = ab[0] * ab[0] + ab[1] * ab[1] + ab[2] * ab[2];
        if (abLen2 < 1.0e-9) {
            return null;
        }
        for (SolarSystem.Body body : bodies) {
            double[] ac = {body.x() - a[0], body.y() - a[1], body.z() - a[2]};
            double tRaw = (ac[0] * ab[0] + ac[1] * ab[1] + ac[2] * ab[2]) / abLen2;
            if (tRaw <= 0) {
                continue;
            }
            double t = Math.min(1.0, tRaw);
            double dx = a[0] + ab[0] * t - body.x();
            double dy = a[1] + ab[1] * t - body.y();
            double dz = a[2] + ab[2] * t - body.z();
            double r = body.planet().radius() + SURFACE_MARGIN;
            if (dx * dx + dy * dy + dz * dz < r * r) {
                return body;
            }
        }
        return null;
    }

    /** Altitude sobre o corpo mais próximo (distância ao centro menos o raio), nunca negativa. */
    static double altitudeOverNearest(List<SolarSystem.Body> bodies, double[] p) {
        double best = Double.MAX_VALUE;
        for (SolarSystem.Body body : bodies) {
            double dx = p[0] - body.x();
            double dy = p[1] - body.y();
            double dz = p[2] - body.z();
            best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz) - body.planet().radius());
        }
        return Math.max(0, best);
    }

    /**
     * Um tick de voo. Atualiza {@link #position()} e {@link #velocity()} (deslocamento deste tick, espaço) e devolve
     * true quando chegou ao destino (a velocidade final é zero).
     */
    public boolean advance() {
        return advance(SolarSystem.BODIES);
    }

    public boolean advance(List<SolarSystem.Body> bodies) {
        if (finished) {
            return true;
        }
        double[] wp = waypoints.get(index);
        double dx = wp[0] - position[0];
        double dy = wp[1] - position[1];
        double dz = wp[2] - position[2];
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean last = index == waypoints.size() - 1;

        double target = ALTITUDE_SPEED_FACTOR * altitudeOverNearest(bodies, position);
        if (last) {
            target = Math.min(target, DISTANCE_SPEED_FACTOR * dist);
        }
        target = Math.max(MIN_SPEED, Math.min(MAX_SPEED, target));
        speed = Math.min(target, speed * ACCELERATION_FACTOR + ACCELERATION_ADD);

        double step = Math.min(speed, dist);
        if (dist > 1.0e-9) {
            velocity[0] = dx / dist * step;
            velocity[1] = dy / dist * step;
            velocity[2] = dz / dist * step;
        } else {
            velocity[0] = 0;
            velocity[1] = 0;
            velocity[2] = 0;
        }
        position[0] += velocity[0];
        position[1] += velocity[1];
        position[2] += velocity[2];

        if (dist - step <= ARRIVE_DISTANCE) {
            if (last) {
                position[0] = wp[0];
                position[1] = wp[1];
                position[2] = wp[2];
                velocity[0] = 0;
                velocity[1] = 0;
                velocity[2] = 0;
                speed = 0;
                finished = true;
                return true;
            }
            index++;
        }
        return false;
    }

    /** Posição atual no espaço (array interno; não modificar). */
    public double[] position() {
        return position;
    }

    /** Deslocamento do último tick no espaço, em blocos/tick (array interno; não modificar). */
    public double[] velocity() {
        return velocity;
    }

    public boolean isFinished() {
        return finished;
    }

    public List<double[]> waypoints() {
        return waypoints;
    }
}
