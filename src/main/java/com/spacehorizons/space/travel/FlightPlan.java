package com.spacehorizons.space.travel;

import java.util.ArrayList;
import java.util.List;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

/**
 * Trajetória do piloto automático. Java puro: um tick de voo é {@link #advance(double)}, que move a posição e atualiza a
 * velocidade.
 *
 * <h2>Referencial</h2>
 * O voo é feito no referencial FIXO no corpo {@code frame} (cartesiano com origem no centro e eixos presos ao corpo, ver
 * SpaceFrame): é o referencial em que o jogador do Minecraft está parado quando não se mexe, então a velocidade do plano
 * é a velocidade relativa ao chão do corpo e vale como está para o jogador. Na Terra ele coincide com o espaço; na Lua,
 * que anda e gira com a órbita, o destino parado no corpo é um ponto parado. Se o destino é OUTRO corpo (que se move
 * neste referencial), o plano faz perseguição com antecipação: mira onde o destino estará quando chegar, e corrige a cada
 * tick. Ao entrar na esfera de influência do destino, quem chama refaz o plano no referencial dele (ver FlightGuide).
 *
 * <h2>Trajetória</h2>
 * Uma lista de pontos de passagem (a subida radial ao sair de um corpo; desvios acima da superfície se a reta cortaria
 * um corpo) e depois o destino. A velocidade é proporcional à altitude sobre o corpo mais próximo (sobe devagar do chão e
 * acelera longe), limitada por aceleração e, no último trecho, proporcional à distância que falta (para suave perto do
 * destino).
 */
public final class FlightPlan {
    /** Blocos/tick de velocidade por bloco de altitude sobre o corpo mais próximo (150 b/tick na altitude de saída da Terra, 5.000, já limitado pelo teto). */
    public static final double ALTITUDE_SPEED_FACTOR = 0.03;
    /** Blocos/tick por bloco de distância ao destino final (frenagem exponencial). */
    public static final double DISTANCE_SPEED_FACTOR = 0.06;
    public static final double MIN_SPEED = 0.5;
    /** 200 blocos/tick = 4.000 blocos/s: tem de passar da velocidade da Lua (85 a 167 b/tick) para alcançá-la; a 300 b/tick o servidor engasgou e a 200 já sofre (chunks do voo, ver README). */
    public static final double MAX_SPEED = 200.0;
    public static final double ACCELERATION_FACTOR = 1.06;
    public static final double ACCELERATION_ADD = 0.15;
    /** Altitude acima da superfície dos pontos intermediários que desviam de um planeta. */
    public static final double DETOUR_ALTITUDE = 6_000.0;
    /**
     * Ao sair de um corpo rumo a outro, o plano sobe na vertical até esta altitude antes de virar para o destino (que pode
     * estar perto do horizonte): sem isso a trajetória rasparia o chão em baixa velocidade.
     */
    public static final double ASCENT_ALTITUDE = 3_000.0;
    /** A reta não pode passar mais perto que isto da superfície (raio + margem) de um corpo no meio do caminho. */
    private static final double SURFACE_MARGIN = 1_000.0;
    private static final int MAX_DETOUR_DEPTH = 8;
    private static final double ARRIVE_DISTANCE = 1.0;
    /** Teto do tempo de antecipação da perseguição (ticks). */
    private static final double MAX_LEAD_TICKS = 20_000.0;
    /** Velocidade média presumida para o instante do cálculo dos desvios (para onde estará o destino que se move). */
    private static final double PLANNING_SPEED = 0.9 * MAX_SPEED;

    /** Esfera (centro e raio) no referencial do plano. */
    private record Sphere(double x, double y, double z, double radius) {
    }

    private final SolarSystem.Body frame;
    private final List<SolarSystem.Body> bodies;
    private final SolarSystem.Body targetBody;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    private final List<double[]> waypoints;
    private final double[] position;
    private final double[] velocity = new double[3];
    private int index;
    private double speed;
    private boolean finished;

    private FlightPlan(SolarSystem.Body frame, List<SolarSystem.Body> bodies, SolarSystem.Body targetBody, double tx, double ty,
            double tz, List<double[]> waypoints, double[] start, double initialSpeed) {
        this.frame = frame;
        this.bodies = bodies;
        this.targetBody = targetBody;
        this.targetX = tx;
        this.targetY = ty;
        this.targetZ = tz;
        this.waypoints = waypoints;
        this.position = start.clone();
        this.speed = Math.max(0.0, Math.min(MAX_SPEED, initialSpeed));
    }

    /**
     * Plano a partir de {@code start} (cartesiano fixo no corpo {@code frame}) até o ponto (tx, ty, tz) da dimensão de
     * {@code targetBody}, desviando dos corpos de {@code bodies}. {@code clock} é o relógio do Overworld (ticks) do início
     * e {@code initialSpeed} a velocidade relativa ao chão com que o voo começa (blocos/tick; 0 saindo parado).
     */
    public static FlightPlan create(SolarSystem.Body frame, List<SolarSystem.Body> bodies, double clock, double[] start,
            double initialSpeed, SolarSystem.Body targetBody, double tx, double ty, double tz) {
        List<double[]> points = new ArrayList<>();
        FlightPlan probe = new FlightPlan(frame, bodies, targetBody, tx, ty, tz, points, start, initialSpeed);

        double[] first = new double[3];
        probe.targetInFrame(clock, first);
        double tau = Math.min(MAX_LEAD_TICKS, distance(start, first) / PLANNING_SPEED);
        double[] aim = new double[3];
        probe.targetInFrame(clock + tau, aim);
        List<Sphere> spheres = probe.spheres(clock + tau);

        double[] from = start;
        double startRadius = Math.sqrt(start[0] * start[0] + start[1] * start[1] + start[2] * start[2]);
        double ascentRadius = frame.planet().radius() + ASCENT_ALTITUDE;
        if (targetBody != frame && startRadius < ascentRadius && startRadius > 1.0e-6) {
            double k = ascentRadius / startRadius;
            from = new double[] {start[0] * k, start[1] * k, start[2] * k};
            points.add(from.clone());
        }
        appendSafe(spheres, from, aim, points, 0);
        // o último ponto é o próprio destino: ele é seguido em tempo real (pode se mover), não é um ponto fixo
        points.remove(points.size() - 1);
        return probe;
    }

    /** Acrescenta os pontos de a (exclusive) até b (inclusive), com desvios se o segmento corta um corpo. */
    private static void appendSafe(List<Sphere> spheres, double[] a, double[] b, List<double[]> out, int depth) {
        Sphere hit = depth < MAX_DETOUR_DEPTH ? firstHit(spheres, a, b) : null;
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
        if (len < 1.0e-3 * hit.radius()) {
            // reta passa pelo centro: sai por uma direção perpendicular ao segmento
            double[] axis = Math.abs(ab[1]) < 0.9 * Math.sqrt(abLen2) ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
            dir = new double[] {ab[1] * axis[2] - ab[2] * axis[1], ab[2] * axis[0] - ab[0] * axis[2],
                    ab[0] * axis[1] - ab[1] * axis[0]};
            len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        }
        double radius = hit.radius() + DETOUR_ALTITUDE;
        double[] detour = {center[0] + dir[0] / len * radius, center[1] + dir[1] / len * radius,
                center[2] + dir[2] / len * radius};
        appendSafe(spheres, a, detour, out, depth + 1);
        appendSafe(spheres, detour, b, out, depth + 1);
    }

    /**
     * Primeira esfera (raio + margem) que o segmento atravessa. Um segmento que começa dentro da margem e se afasta
     * (decolando do chão) não conta: só vale quando o segmento caminha em direção ao centro. {@code radius} das esferas é
     * o raio do planeta; a margem é somada aqui.
     */
    private static Sphere firstHit(List<Sphere> spheres, double[] a, double[] b) {
        double[] ab = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        double abLen2 = ab[0] * ab[0] + ab[1] * ab[1] + ab[2] * ab[2];
        if (abLen2 < 1.0e-9) {
            return null;
        }
        for (Sphere sphere : spheres) {
            double[] ac = {sphere.x() - a[0], sphere.y() - a[1], sphere.z() - a[2]};
            double tRaw = (ac[0] * ab[0] + ac[1] * ab[1] + ac[2] * ab[2]) / abLen2;
            if (tRaw <= 0) {
                continue;
            }
            double t = Math.min(1.0, tRaw);
            double dx = a[0] + ab[0] * t - sphere.x();
            double dy = a[1] + ab[1] * t - sphere.y();
            double dz = a[2] + ab[2] * t - sphere.z();
            double r = sphere.radius() + SURFACE_MARGIN;
            if (dx * dx + dy * dy + dz * dz < r * r) {
                return sphere;
            }
        }
        return null;
    }

    /** Centro de cada corpo e raio, no referencial do plano no instante {@code clock}. */
    private List<Sphere> spheres(double clock) {
        List<Sphere> out = new ArrayList<>(bodies.size());
        double[] s = new double[3];
        double[] b = new double[3];
        for (SolarSystem.Body body : bodies) {
            if (body == frame) {
                out.add(new Sphere(0, 0, 0, body.planet().radius()));
            } else {
                body.positionAt(clock, s);
                SpaceFrame.spaceToBodyFixed(frame, clock, s[0], s[1], s[2], b);
                out.add(new Sphere(b[0], b[1], b[2], body.planet().radius()));
            }
        }
        return out;
    }

    /** Altitude sobre o corpo mais próximo (distância ao centro menos o raio), nunca negativa. */
    private static double altitudeOverNearest(List<Sphere> spheres, double[] p) {
        double best = Double.MAX_VALUE;
        for (Sphere sphere : spheres) {
            double dx = p[0] - sphere.x();
            double dy = p[1] - sphere.y();
            double dz = p[2] - sphere.z();
            best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz) - sphere.radius());
        }
        return Math.max(0, best);
    }

    /** Ponto de destino no referencial do plano no instante {@code clock}. */
    void targetInFrame(double clock, double[] out) {
        if (targetBody == frame) {
            SpaceFrame.toBodyFixed(frame, targetX, targetY, targetZ, out);
            return;
        }
        double[] s = new double[3];
        SpaceFrame.toSpace(targetBody, clock, targetX, targetY, targetZ, s);
        SpaceFrame.spaceToBodyFixed(frame, clock, s[0], s[1], s[2], out);
    }

    /** O destino fica parado neste referencial (é um ponto do próprio corpo do referencial). */
    public boolean targetIsStatic() {
        return targetBody == frame;
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Para onde mirar no último trecho: o próprio destino se ele está parado; senão, onde estará quando o plano chegar lá. */
    private double[] aimPoint(double clock, double[] now) {
        if (targetBody == frame) {
            return now;
        }
        double[] goal = now.clone();
        for (int i = 0; i < 2; i++) {
            double tau = Math.min(MAX_LEAD_TICKS, distance(position, goal) / MAX_SPEED);
            targetInFrame(clock + tau, goal);
        }
        return goal;
    }

    /**
     * Um tick de voo no instante {@code clock}. Atualiza {@link #position()} e {@link #velocity()} (deslocamento deste
     * tick, no referencial do corpo) e devolve true quando chegou ao destino (a velocidade final é zero).
     */
    public boolean advance(double clock) {
        if (finished) {
            return true;
        }
        boolean last = index >= waypoints.size();
        double[] now = new double[3];
        targetInFrame(clock, now);
        double[] wp = last ? aimPoint(clock, now) : waypoints.get(index);
        double dx = wp[0] - position[0];
        double dy = wp[1] - position[1];
        double dz = wp[2] - position[2];
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double target = ALTITUDE_SPEED_FACTOR * altitudeOverNearest(spheres(clock), position);
        if (last) {
            target = Math.min(target, DISTANCE_SPEED_FACTOR * distance(position, now));
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
            if (!last) {
                index++;
            } else if (targetBody == frame) {
                position[0] = wp[0];
                position[1] = wp[1];
                position[2] = wp[2];
                velocity[0] = 0;
                velocity[1] = 0;
                velocity[2] = 0;
                speed = 0;
                finished = true;
                return true;
            } else {
                // destino que se move: só vale como chegada se o plano está de fato em cima dele
                double[] next = new double[3];
                targetInFrame(clock + 1.0, next);
                if (distance(position, next) <= 2.0 * ARRIVE_DISTANCE) {
                    velocity[0] = 0;
                    velocity[1] = 0;
                    velocity[2] = 0;
                    speed = 0;
                    finished = true;
                    return true;
                }
            }
        }
        return false;
    }

    /** Posição atual (cartesiano fixo no corpo do referencial; array interno, não modificar). */
    public double[] position() {
        return position;
    }

    /** Deslocamento do último tick no referencial do corpo, em blocos/tick (array interno; não modificar). */
    public double[] velocity() {
        return velocity;
    }

    /** Velocidade escalar atual (blocos/tick, relativa ao chão do corpo do referencial). */
    public double speed() {
        return speed;
    }

    public boolean isFinished() {
        return finished;
    }

    /** Pontos de passagem fixos antes do destino (subida e desvios), no referencial do corpo. */
    public List<double[]> waypoints() {
        return waypoints;
    }

    public SolarSystem.Body frame() {
        return frame;
    }

    public SolarSystem.Body targetBody() {
        return targetBody;
    }
}
