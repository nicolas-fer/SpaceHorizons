package com.spacehorizons.ship.deck;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A mistura de uma troca sem costura (fase 2g): quando a fonte de uma pose muda de uma hora para outra (o casco passa da interpolação do vanilla para a
 * linha do tempo da nave, a câmera passa do olho do jogador a pé para o do assento), a pose nova nasce com uma DIFERENÇA em relação à que estava
 * sendo desenhada. Em vez de deixar essa diferença aparecer de um quadro para o outro (um pulo), ela é guardada no instante da troca e DESCE a zero
 * ao longo de {@code ticks} ticks do relógio do cliente, somada à pose nova: a posição ganha um deslocamento e a atitude uma rotação (a do mundo,
 * aplicada à esquerda) que vão a zero com a curva suave de 3x² − 2x³ (velocidade zero nas duas pontas, sem tranco no começo nem no fim).
 *
 * <p>A posição e a rotação têm cada uma o seu começo, porque a câmera as calcula em passos diferentes do mesmo quadro. Lógica pura, testada sem o jogo.
 */
public final class PoseBlend {
    private final Vector3d positionOffset = new Vector3d();
    private final Quaterniond rotationOffset = new Quaterniond();
    private double positionStart = Double.NaN;
    private double rotationStart = Double.NaN;
    private double positionTicks;
    private double rotationTicks;

    /**
     * Começa a misturar a posição no relógio {@code clock}: {@code old} é o que estava sendo desenhado (já com qualquer mistura em curso) e {@code now}
     * o que a fonte nova dá agora.
     */
    public void beginPosition(double clock, double ticks, Vector3dc old, Vector3dc now) {
        old.sub(now, this.positionOffset);
        this.positionStart = clock;
        this.positionTicks = ticks;
    }

    /** Começa a misturar a atitude: guarda a rotação de mundo {@code d} com {@code d * now = old}. */
    public void beginRotation(double clock, double ticks, Quaterniondc old, Quaterniondc now) {
        old.mul(now.invert(new Quaterniond()), this.rotationOffset).normalize();
        this.rotationStart = clock;
        this.rotationTicks = ticks;
    }

    public boolean positionActive(double clock) {
        return !Double.isNaN(this.positionStart) && clock - this.positionStart < this.positionTicks;
    }

    public boolean rotationActive(double clock) {
        return !Double.isNaN(this.rotationStart) && clock - this.rotationStart < this.rotationTicks;
    }

    public boolean active(double clock) {
        return this.positionActive(clock) || this.rotationActive(clock);
    }

    /** O quanto da diferença ainda resta em {@code clock} (1 no instante da troca, 0 ao fim): a curva suave 3x² − 2x³ invertida. */
    static double remaining(double clock, double start, double ticks) {
        if (Double.isNaN(start) || ticks <= 0.0) {
            return 0.0;
        }
        double x = Math.max(0.0, Math.min(1.0, (clock - start) / ticks));
        return 1.0 - x * x * (3.0 - 2.0 * x);
    }

    /** A posição da fonte nova ({@code natural}) com o resto da diferença somado; {@code dest} recebe o resultado. */
    public Vector3d position(double clock, Vector3dc natural, Vector3d dest) {
        double f = remaining(clock, this.positionStart, this.positionTicks);
        return dest.set(natural.x() + this.positionOffset.x * f, natural.y() + this.positionOffset.y * f, natural.z() + this.positionOffset.z * f);
    }

    /** A atitude da fonte nova com o resto da rotação de diferença aplicado à esquerda. */
    public Quaterniond rotation(double clock, Quaterniondc natural, Quaterniond dest) {
        double f = remaining(clock, this.rotationStart, this.rotationTicks);
        Quaterniond part = new Quaterniond().slerp(this.rotationOffset, f);
        return part.mul(natural, dest).normalize();
    }

    public void clear() {
        this.positionStart = Double.NaN;
        this.rotationStart = Double.NaN;
        this.positionOffset.zero();
        this.rotationOffset.identity();
    }
}
