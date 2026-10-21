package com.spacehorizons.rocket;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.jspecify.annotations.Nullable;

import com.spacehorizons.space.flight.AttitudeMath;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.V3;

/**
 * Controle de atitude do foguete (lógica pura): o piloto comanda a TAXA de giro nos eixos do corpo (arfagem em torno de +X,
 * rolagem em torno do nariz +Y, guinada em torno de +Z), limitada a {@link #MAX_SPIN} e com aceleração angular
 * {@link #SPIN_ACCEL}; sem torque aerodinâmico, o giro que sobra persiste (como no vácuo) a menos que o SAS o zere.
 *
 * <ul>
 *   <li>{@code OFF}: o giro persiste; o comando muda a taxa do eixo comandado.</li>
 *   <li>{@code STABILIZE}: os eixos sem comando são freados até parar (segura a atitude).</li>
 *   <li>{@code PROGRADE}/{@code RETROGRADE}/{@code HORIZONTAL}: sem comando algum do piloto, aponta o nariz para o alvo
 *       (velocidade, o oposto, ou a horizontal local) por {@link AttitudeMath#slewNoseToward}; com comando, o piloto manda e
 *       os eixos livres são freados. Sem alvo (horizontal na subida vertical), age como estabilizar.</li>
 * </ul>
 *
 * O giro é um vetor {@link V3} em eixos do corpo: x = arfagem, y = rolagem, z = guinada (radianos por tick).
 */
public final class AttitudeControl {
    /** Taxa máxima de giro por eixo (rad/tick): ~46 graus por segundo. */
    public static final double MAX_SPIN = 0.04;
    /** Variação máxima da taxa por tick e por eixo (rad/tick²). */
    public static final double SPIN_ACCEL = 0.01;
    /** Comandos abaixo disto contam como "sem comando". */
    public static final double DEADZONE = 0.01;

    /** Resultado de um tick: a atitude nova (inercial) e o giro novo. */
    public record Result(Quaterniond attitude, V3 spin) {
    }

    private AttitudeControl() {
    }

    /**
     * Um tick de controle. {@code pitch}, {@code yaw} e {@code roll} vão de -1 a 1. {@code sasTarget} é a direção inercial
     * que o SAS prógrado/retrógrado quer (ver {@link AttitudeMath#sasTarget}), ou null.
     */
    public static Result step(Quaterniondc attitude, V3 spin, SasMode sas, double pitch, double yaw, double roll,
            @Nullable V3 sasTarget) {
        boolean manual = Math.abs(pitch) > DEADZONE || Math.abs(yaw) > DEADZONE || Math.abs(roll) > DEADZONE;
        if (!manual && sasTarget != null && (sas == SasMode.PROGRADE || sas == SasMode.RETROGRADE || sas == SasMode.HORIZONTAL)) {
            return new Result(AttitudeMath.slewNoseToward(attitude, sasTarget, MAX_SPIN), V3.ZERO);
        }
        boolean damp = sas != SasMode.OFF;
        double sx = approach(spin.x(), target(pitch, spin.x(), damp));
        double sy = approach(spin.y(), target(roll, spin.y(), damp));
        double sz = approach(spin.z(), target(yaw, spin.z(), damp));
        Quaterniond next = AttitudeMath.rotateBody(attitude, sx, sz, sy);
        return new Result(next, new V3(sx, sy, sz));
    }

    /** Taxa que o eixo quer: a comandada, ou zero com o SAS ligado, ou a que já tem. */
    private static double target(double input, double current, boolean damp) {
        if (Math.abs(input) > DEADZONE) {
            return Math.max(-1.0, Math.min(1.0, input)) * MAX_SPIN;
        }
        return damp ? 0.0 : current;
    }

    private static double approach(double current, double target) {
        double delta = target - current;
        if (Math.abs(delta) <= SPIN_ACCEL) {
            return target;
        }
        return current + Math.copySign(SPIN_ACCEL, delta);
    }
}
