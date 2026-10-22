package com.spacehorizons.ship.deck;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Colisão do tripulante contra os blocos sólidos do retrato, no REFERENCIAL LOCAL da nave (spike S5 do convés andável). Como o jogador a
 * bordo é simulado nas coordenadas do retrato, a caixa dele é alinhada aos eixos do retrato e os blocos também: a nave pode girar em
 * qualquer eixo e a colisão continua sendo caixa contra caixa (não existe "caixa girada" aqui; a rotação da nave só entra na hora de
 * desenhar e de sincronizar). Matemática pura sobre {@link AABB}, sem o jogo, testada à parte.
 *
 * <p>Segue o algoritmo do vanilla ({@code Entity.collide}): primeiro o eixo Y, depois o eixo horizontal de maior movimento; com o jogador no
 * chão e uma colisão horizontal, tenta subir degraus de até {@code stepHeight}.
 */
public final class DeckCollision {
    /** Tolerância de contato, a mesma do {@code Shapes.collide} do vanilla. */
    private static final double EPS = 1.0E-7;

    private DeckCollision() {
    }

    /** Fornece as caixas sólidas (no referencial local) que tocam uma região. */
    public interface Colliders {
        /** Entrega a {@code out} cada caixa sólida que cruza {@code region} (pode entregar algumas a mais). */
        void collect(AABB region, Consumer<AABB> out);
    }

    /** Resultado de um passo: o movimento que coube e as colisões por eixo. */
    public record Result(Vec3 moved, boolean collidedX, boolean collidedY, boolean collidedZ, boolean onGround) {
        public boolean horizontalCollision() {
            return this.collidedX || this.collidedZ;
        }
    }

    /**
     * Move a caixa {@code box} de {@code movement} (referencial local) sem entrar nos sólidos. {@code stepHeight} é a altura de degrau (0,6 do
     * jogador) e {@code wasOnGround} diz se ela estava no chão antes (só então tenta subir degraus).
     */
    public static Result move(AABB box, Vec3 movement, Colliders colliders, double stepHeight, boolean wasOnGround) {
        List<AABB> near = new ArrayList<>();
        colliders.collect(box.expandTowards(movement).expandTowards(0.0, Math.max(0.0, stepHeight), 0.0).inflate(1.0E-3), near::add);
        Vec3 step = movement.lengthSqr() == 0.0 ? movement : collideWithBoxes(movement, box, near);
        boolean xCollision = movement.x != step.x;
        boolean yCollision = movement.y != step.y;
        boolean zCollision = movement.z != step.z;
        boolean onGroundAfterCollision = yCollision && movement.y < 0.0;
        Vec3 result = step;
        if (stepHeight > 0.0 && (onGroundAfterCollision || wasOnGround) && (xCollision || zCollision)) {
            Vec3 stepped = stepUp(box, movement, near, step, onGroundAfterCollision, stepHeight);
            if (stepped != null) {
                result = stepped;
            }
        }
        // as colisões valem para o movimento FINAL (com o degrau), como no vanilla: subir o degrau sem bater não zera a velocidade
        boolean x = !equal(movement.x, result.x);
        boolean y = movement.y != result.y;
        boolean z = !equal(movement.z, result.z);
        return new Result(result, x, y, z, y && movement.y < 0.0);
    }

    private static boolean equal(double a, double b) {
        return Math.abs(b - a) < 1.0E-5;
    }

    /** O degrau do vanilla: tenta cada altura candidata (o topo dos sólidos próximos) até conseguir andar mais na horizontal. */
    private static Vec3 stepUp(AABB box, Vec3 movement, List<AABB> near, Vec3 step, boolean onGroundAfterCollision, double stepHeight) {
        AABB grounded = onGroundAfterCollision ? box.move(0.0, step.y, 0.0) : box;
        AABB stepUpBox = grounded.expandTowards(movement.x, stepHeight, movement.z);
        if (!onGroundAfterCollision) {
            stepUpBox = stepUpBox.expandTowards(0.0, -1.0E-5, 0.0);
        }
        List<AABB> colliders = new ArrayList<>();
        for (AABB c : near) {
            if (c.intersects(stepUpBox)) {
                colliders.add(c);
            }
        }
        double[] candidates = candidateHeights(grounded, colliders, stepHeight, step.y);
        double stepHorizontal = step.x * step.x + step.z * step.z;
        for (double h : candidates) {
            Vec3 fromGround = collideWithBoxes(new Vec3(movement.x, h, movement.z), grounded, colliders);
            if (fromGround.x * fromGround.x + fromGround.z * fromGround.z > stepHorizontal) {
                double distanceToGround = box.minY - grounded.minY;
                return fromGround.subtract(0.0, distanceToGround, 0.0);
            }
        }
        return null;
    }

    private static double[] candidateHeights(AABB grounded, List<AABB> colliders, double maxStep, double skip) {
        java.util.TreeSet<Double> heights = new java.util.TreeSet<>();
        for (AABB c : colliders) {
            for (double coord : new double[] {c.minY, c.maxY}) {
                double rel = coord - grounded.minY;
                if (rel >= 0.0 && rel != skip && rel <= maxStep) {
                    heights.add(rel);
                }
            }
        }
        double[] out = new double[heights.size()];
        int i = 0;
        for (double h : heights) {
            out[i++] = h;
        }
        return out;
    }

    /** Movimento que cabe contra as caixas, eixo por eixo na ordem do vanilla (Y, depois o horizontal maior). */
    public static Vec3 collideWithBoxes(Vec3 movement, AABB box, List<AABB> colliders) {
        if (colliders.isEmpty()) {
            return movement;
        }
        double rx = 0.0;
        double ry = 0.0;
        double rz = 0.0;
        for (Direction.Axis axis : Direction.axisStepOrder(movement)) {
            double d = movement.get(axis);
            if (d == 0.0) {
                continue;
            }
            AABB moved = box.move(rx, ry, rz);
            double allowed = collideAxis(axis, moved, colliders, d);
            switch (axis) {
                case X -> rx = allowed;
                case Y -> ry = allowed;
                case Z -> rz = allowed;
            }
        }
        return new Vec3(rx, ry, rz);
    }

    /** O quanto a caixa anda ao longo de {@code axis} (no máximo {@code d}, com o sinal) antes de bater numa das caixas. */
    static double collideAxis(Direction.Axis axis, AABB box, List<AABB> colliders, double d) {
        double allowed = d;
        for (AABB c : colliders) {
            if (Math.abs(allowed) < EPS) {
                return 0.0;
            }
            allowed = clip(axis, box, c, allowed);
        }
        return allowed;
    }

    private static double clip(Direction.Axis axis, AABB box, AABB c, double d) {
        // só importa quem se sobrepõe nos outros dois eixos
        switch (axis) {
            case X -> {
                if (!(box.maxY > c.minY + EPS && box.minY < c.maxY - EPS && box.maxZ > c.minZ + EPS && box.minZ < c.maxZ - EPS)) {
                    return d;
                }
                if (d > 0.0 && box.maxX <= c.minX + EPS) {
                    double gap = c.minX - box.maxX;
                    if (gap >= -EPS) {
                        return Math.min(d, gap);
                    }
                } else if (d < 0.0 && box.minX >= c.maxX - EPS) {
                    double gap = c.maxX - box.minX;
                    if (gap <= EPS) {
                        return Math.max(d, gap);
                    }
                }
            }
            case Y -> {
                if (!(box.maxX > c.minX + EPS && box.minX < c.maxX - EPS && box.maxZ > c.minZ + EPS && box.minZ < c.maxZ - EPS)) {
                    return d;
                }
                if (d > 0.0 && box.maxY <= c.minY + EPS) {
                    double gap = c.minY - box.maxY;
                    if (gap >= -EPS) {
                        return Math.min(d, gap);
                    }
                } else if (d < 0.0 && box.minY >= c.maxY - EPS) {
                    double gap = c.maxY - box.minY;
                    if (gap <= EPS) {
                        return Math.max(d, gap);
                    }
                }
            }
            case Z -> {
                if (!(box.maxX > c.minX + EPS && box.minX < c.maxX - EPS && box.maxY > c.minY + EPS && box.minY < c.maxY - EPS)) {
                    return d;
                }
                if (d > 0.0 && box.maxZ <= c.minZ + EPS) {
                    double gap = c.minZ - box.maxZ;
                    if (gap >= -EPS) {
                        return Math.min(d, gap);
                    }
                } else if (d < 0.0 && box.minZ >= c.maxZ - EPS) {
                    double gap = c.maxZ - box.minZ;
                    if (gap <= EPS) {
                        return Math.max(d, gap);
                    }
                }
            }
        }
        return d;
    }

    /**
     * O quanto a caixa penetra no sólido mais fundo (blocos): zero se só encosta ou está livre. É a conferência do servidor ("moved wrongly" no
     * referencial local): a posição que o cliente manda não pode estar dentro de um bloco.
     */
    public static double penetration(AABB box, Colliders colliders) {
        double[] worst = {0.0};
        colliders.collect(box.inflate(1.0E-3), c -> {
            double ox = Math.min(box.maxX, c.maxX) - Math.max(box.minX, c.minX);
            double oy = Math.min(box.maxY, c.maxY) - Math.max(box.minY, c.minY);
            double oz = Math.min(box.maxZ, c.maxZ) - Math.max(box.minZ, c.minZ);
            if (ox > EPS && oy > EPS && oz > EPS) {
                worst[0] = Math.max(worst[0], Math.min(ox, Math.min(oy, oz)));
            }
        });
        return worst[0];
    }
}
