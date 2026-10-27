package com.spacehorizons.ship.deck;

import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;

/**
 * O salto de uma troca sem costura (fase 2g): entre dois quadros desenhados seguidos, quanto a posição e a rotação andaram ALÉM do que a nave andaria
 * naquele intervalo. O critério do plano é salto &lt; 0,1 bloco na posição e &lt; 1 grau na câmera. O movimento esperado entra por dois lados: um
 * jogador parado no mundo tem velocidade 0 antes de embarcar e a da nave depois, então o salto é o menor dos dois restos (parado e acompanhando a nave).
 * Lógica pura, testada sem o jogo.
 */
public final class SeamJump {
    private SeamJump() {
    }

    /**
     * O salto de posição entre dois quadros: o resto do deslocamento depois de descontar o movimento esperado da nave no intervalo
     * ({@code expectedVelocity} em blocos por tick, {@code dtTicks} em ticks), ou nada descontado, o que der menos.
     */
    public static double position(Vector3dc before, Vector3dc after, Vector3dc expectedVelocity, double dtTicks) {
        double dx = after.x() - before.x();
        double dy = after.y() - before.y();
        double dz = after.z() - before.z();
        double still = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double ex = dx - expectedVelocity.x() * dtTicks;
        double ey = dy - expectedVelocity.y() * dtTicks;
        double ez = dz - expectedVelocity.z() * dtTicks;
        return Math.min(still, Math.sqrt(ex * ex + ey * ey + ez * ez));
    }

    /**
     * Como {@link #position(Vector3dc, Vector3dc, Vector3dc, double)}, com um terceiro movimento esperado: seguir a velocidade que o próprio ponto
     * trazia do par de quadros anterior ({@code ownVelocity}, blocos por tick). Um deslize suave (a mistura da câmera indo do olhar em pé ao do
     * assento) anda vários blocos, mas sem salto entre quadros; num quadro um pouco mais longo o deslocamento passaria de 0,1 bloco sem ser costura.
     * Um teleporte continua salto, porque a velocidade de antes não o explica.
     */
    public static double position(Vector3dc before, Vector3dc after, Vector3dc expectedVelocity, Vector3dc ownVelocity, double dtTicks) {
        double ox = after.x() - before.x() - ownVelocity.x() * dtTicks;
        double oy = after.y() - before.y() - ownVelocity.y() * dtTicks;
        double oz = after.z() - before.z() - ownVelocity.z() * dtTicks;
        return Math.min(position(before, after, expectedVelocity, dtTicks), Math.sqrt(ox * ox + oy * oy + oz * oz));
    }

    /** O ângulo, em graus, entre duas rotações (0 a 180). */
    public static double angleDegrees(Quaterniondc a, Quaterniondc b) {
        double dot = Math.abs(a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w());
        double norm = Math.sqrt((a.x() * a.x() + a.y() * a.y() + a.z() * a.z() + a.w() * a.w()) * (b.x() * b.x() + b.y() * b.y() + b.z() * b.z() + b.w() * b.w()));
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, dot / norm)));
    }

    /** O salto de rotação, em graus: o ângulo entre os quadros menos o giro esperado da nave no intervalo ({@code degreesPerTick}), nunca negativo. */
    public static double angle(Quaterniondc before, Quaterniondc after, double degreesPerTick, double dtTicks) {
        return Math.max(0.0, angleDegrees(before, after) - Math.abs(degreesPerTick) * dtTicks);
    }

    /**
     * Como {@link #angle(Quaterniondc, Quaterniondc, double, double)}, com o giro que a própria câmera trazia do par anterior ({@code earlier} →
     * {@code before}, em {@code dtEarlierTicks}) continuado por {@code dtTicks}: a mistura girando a velocidade constante não é salto, só uma
     * mudança brusca de giro é.
     */
    public static double angle(Quaterniondc earlier, Quaterniondc before, Quaterniondc after, double degreesPerTick, double dtEarlierTicks, double dtTicks) {
        Quaterniond step = new Quaterniond(before).mul(new Quaterniond(earlier).invert());
        AxisAngle4d turn = new AxisAngle4d(step);
        if (turn.angle > Math.PI) {
            turn.angle -= 2.0 * Math.PI;
        }
        turn.angle *= dtTicks / dtEarlierTicks;
        Quaterniond predicted = new Quaterniond(turn).mul(before);
        return Math.min(angle(before, after, degreesPerTick, dtTicks), angleDegrees(predicted, after));
    }
}
