package com.spacehorizons.client.rocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.spacehorizons.rocket.RocketTelemetryPayload;
import com.spacehorizons.space.flight.MarsPlanner;
import com.spacehorizons.space.flight.TransferPlanner;

import org.jspecify.annotations.Nullable;

/**
 * O que o painel do piloto mostra, já formatado (lógica pura, testada): as linhas de dados de voo, os avisos e as frases do
 * planejador. O desenho (cores, posições) fica em {@link RocketPanel}; os textos traduzíveis são chaves de idioma.
 *
 * <h2>Unidades (iguais em todo o painel)</h2>
 * A telemetria vem em blocos por tick; o jogador vê <b>blocos por segundo</b> (b/s = b/t × 20) em todas as velocidades e no
 * Δv. Distâncias em blocos (a partir de 10.000 vira "12.3k b"), ângulos em graus, tempos em minutos:segundos,
 * combustível em mB, acelerador em porcentagem.
 */
public final class RocketPanelModel {
    /** Ticks por segundo: converte a telemetria (b/t) para o que o jogador lê (b/s). */
    public static final double TICKS_PER_SECOND = 20.0;
    /** Aquecimento acima do qual o painel avisa. */
    public static final float HEAT_WARNING = 0.7F;
    /** Texto de "sem valor" (órbita aberta, sem telemetria). */
    public static final String NONE = "—";

    /** Quão grave é uma linha ou um aviso (o painel escolhe a cor). */
    public enum Tone {
        NORMAL, GOOD, WARN, BAD
    }

    /** Uma linha de dados: chave do rótulo, valor já formatado (com unidade) e tom. */
    public record Row(String labelKey, String value, Tone tone) {
    }

    /** Um aviso: chave do texto e argumentos. */
    public record Warning(String key, Tone tone, Object... args) {
    }

    private RocketPanelModel() {
    }

    // ---------------------------------------------------------------- formatação

    /** Velocidade em b/s (a entrada é b/t). */
    public static String speed(double blocksPerTick) {
        if (!Double.isFinite(blocksPerTick)) {
            return NONE;
        }
        double bps = blocksPerTick * TICKS_PER_SECOND;
        return Math.abs(bps) < 100.0 ? String.format(Locale.ROOT, "%.1f b/s", bps) : String.format(Locale.ROOT, "%.0f b/s", bps);
    }

    /** Distância em blocos; infinita ou NaN vira "—". */
    public static String distance(double blocks) {
        if (!Double.isFinite(blocks)) {
            return NONE;
        }
        double abs = Math.abs(blocks);
        if (abs >= 100_000.0) {
            return String.format(Locale.ROOT, "%.0fk b", blocks / 1000.0);
        }
        return abs >= 10_000.0 ? String.format(Locale.ROOT, "%.1fk b", blocks / 1000.0) : String.format(Locale.ROOT, "%.0f b", blocks);
    }

    /** Tempo em ticks como m:ss (ou h:mm:ss); NaN, infinito ou negativo vira "—". */
    public static String time(double ticks) {
        if (!Double.isFinite(ticks) || ticks < 0.0) {
            return NONE;
        }
        long seconds = Math.round(ticks / TICKS_PER_SECOND);
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    /** Ângulo em graus com uma casa. */
    public static String degrees(double deg) {
        return Double.isFinite(deg) ? String.format(Locale.ROOT, "%.1f°", deg) : NONE;
    }

    /** Combustível "1.234 / 4.000" sem separador de milhar: "1234 / 4000 mB". */
    public static String fuel(double mb, double capacityMb) {
        return String.format(Locale.ROOT, "%.0f / %.0f mB", Math.max(0.0, mb), Math.max(0.0, capacityMb));
    }

    /** Acelerador de 0 a 1 como porcentagem. */
    public static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.0f%%", Math.max(0.0, Math.min(1.0, fraction)) * 100.0);
    }

    /**
     * O acelerador do painel: o comandado; se o servidor não o aplica ({@code notAccepted}), o comandado e o que ele aplicou de fato
     * ("36% (real 0%)"), para o piloto saber que o comando não está valendo.
     */
    public static String throttle(double commanded, double applied, boolean notAccepted) {
        return notAccepted ? percent(commanded) + " (real " + percent(applied) + ")" : percent(commanded);
    }

    /** Fração de combustível de 0 a 1 (0 se a capacidade é zero). */
    public static float fuelFraction(double mb, double capacityMb) {
        return capacityMb <= 0.0 ? 0.0F : (float) Math.max(0.0, Math.min(1.0, mb / capacityMb));
    }

    // ---------------------------------------------------------------- linhas

    /** Dados de voo (coluna da esquerda). Sem telemetria todas as linhas mostram "—". */
    public static List<Row> flightRows(@Nullable RocketTelemetryPayload t) {
        List<Row> rows = new ArrayList<>();
        if (t == null) {
            for (String key : new String[] {"altitude", "vertical", "horizontal", "orbital", "apoapsis", "periapsis", "inclination", "time_to_apoapsis"}) {
                rows.add(new Row("hud.spacehorizons.rocket." + key, NONE, Tone.NORMAL));
            }
            return rows;
        }
        // descendo depressa perto do chão: velocidade vertical em amarelo/vermelho (o limite de pouso suave é 1,0 b/t = 20 b/s)
        Tone vertical = t.verticalSpeed() < -1.0 && t.altitude() < 400.0 ? (t.verticalSpeed() < -2.0 ? Tone.BAD : Tone.WARN) : Tone.NORMAL;
        rows.add(new Row("hud.spacehorizons.rocket.altitude", distance(t.altitude()), Tone.NORMAL));
        rows.add(new Row("hud.spacehorizons.rocket.vertical", speed(t.verticalSpeed()), vertical));
        rows.add(new Row("hud.spacehorizons.rocket.horizontal", speed(t.horizontalSpeed()), Tone.NORMAL));
        rows.add(new Row("hud.spacehorizons.rocket.orbital", speed(t.orbitalSpeed()), Tone.NORMAL));
        rows.add(new Row("hud.spacehorizons.rocket.apoapsis", distance(t.apoapsisAltitude()), Tone.NORMAL));
        // periastro abaixo da superfície com órbita fechada: a trajetória bate no chão
        Tone peri = Double.isFinite(t.periapsisAltitude()) && t.periapsisAltitude() < 0.0 && Double.isFinite(t.apoapsisAltitude()) ? Tone.WARN
                : Tone.NORMAL;
        rows.add(new Row("hud.spacehorizons.rocket.periapsis", distance(t.periapsisAltitude()), peri));
        rows.add(new Row("hud.spacehorizons.rocket.inclination", degrees(t.inclination()), Tone.NORMAL));
        rows.add(new Row("hud.spacehorizons.rocket.time_to_apoapsis", time(t.timeToApoapsis()), Tone.NORMAL));
        Row landing = landingRow(t);
        if (landing != null) {
            rows.add(landing);
        }
        return rows;
    }

    /**
     * "Pouso: carregando N%" / "Pouso: pronto": o estado da pré-carga do local de pouso previsto; null se não há previsão de
     * impacto (nada a mostrar).
     */
    public static @Nullable Row landingRow(RocketTelemetryPayload t) {
        int percent = Math.max(0, Math.min(100, t.landingPercent()));
        return switch (t.landingState()) {
            case 1 -> new Row("hud.spacehorizons.rocket.landing_loading", percent + "%", Tone.WARN);
            case 2 -> new Row("hud.spacehorizons.rocket.landing_ready", "100%", Tone.GOOD);
            default -> null;
        };
    }

    /** Δv restante em b/s (a entrada é b/t); sem telemetria "—". */
    public static String deltaV(@Nullable RocketTelemetryPayload t) {
        return t == null ? NONE : speed(t.deltaV());
    }

    /** Razão empuxo/peso com duas casas; abaixo de 1 não decola. */
    public static String twr(@Nullable RocketTelemetryPayload t) {
        return t == null || !Double.isFinite(t.twr()) ? NONE : String.format(Locale.ROOT, "%.2f", t.twr());
    }

    // ---------------------------------------------------------------- avisos

    /** Avisos em ordem de importância (o primeiro é o mais grave). */
    public static List<Warning> warnings(@Nullable RocketTelemetryPayload t, float heat) {
        List<Warning> list = new ArrayList<>();
        if (t != null && (t.burnNow() || t.marsBurnNow())) {
            list.add(new Warning("hud.spacehorizons.rocket.burn_now", Tone.GOOD));
        }
        if (heat > HEAT_WARNING) {
            list.add(new Warning("hud.spacehorizons.rocket.heat", heat > 1.2F ? Tone.BAD : Tone.WARN));
        }
        if (t != null && t.heldForChunks()) {
            list.add(new Warning("hud.spacehorizons.rocket.waiting_terrain", Tone.WARN));
        }
        if (t != null && t.chute() > 0.0F) {
            list.add(new Warning("hud.spacehorizons.rocket.parachute", Tone.GOOD, percent(t.chute())));
        }
        return list;
    }

    // ---------------------------------------------------------------- planejador da Lua

    /**
     * As linhas do planejador (Terra → Lua), vazio se não há plano a mostrar. Com o plano válido: fase atual contra a ideal
     * e Δv da injeção translunar; com a órbita errada, a dica do que falta.
     *
     * @return duas ou três linhas de {@link Row} (chave, valor, tom); a primeira é o título do estado
     */
    public static List<Row> plannerRows(@Nullable RocketTelemetryPayload t) {
        List<Row> rows = new ArrayList<>();
        if (t == null || t.plannerStatus() < 0 || t.plannerStatus() >= TransferPlanner.Status.values().length) {
            return rows;
        }
        switch (TransferPlanner.Status.values()[t.plannerStatus()]) {
            case OK -> {
                rows.add(new Row("hud.spacehorizons.rocket.planner.phase", degrees(t.phaseNow()) + " / " + degrees(t.phaseIdeal()),
                        t.burnNow() ? Tone.GOOD : Tone.NORMAL));
                rows.add(new Row("hud.spacehorizons.rocket.planner.tli", speed(t.tliDeltaV()), Tone.NORMAL));
            }
            case WRONG_DIRECTION -> rows.add(new Row("hud.spacehorizons.rocket.planner.wrong_direction", "", Tone.WARN));
            case NOT_IN_ORBIT -> {
                if (t.inSpace()) {
                    rows.add(new Row("hud.spacehorizons.rocket.planner.not_in_orbit", "", Tone.NORMAL));
                }
            }
            case NOT_AROUND_EARTH -> {
            }
        }
        return rows;
    }

    /**
     * As linhas do planejador de Marte (Terra → Marte), vazio se não há plano a mostrar: fase atual contra a ideal do ponto da órbita, Δv da
     * injeção interplanetária e o tempo até a próxima queima possível; com a órbita errada, a dica do que falta.
     */
    public static List<Row> marsPlannerRows(@Nullable RocketTelemetryPayload t) {
        List<Row> rows = new ArrayList<>();
        if (t == null || t.marsStatus() < 0 || t.marsStatus() >= MarsPlanner.Status.values().length) {
            return rows;
        }
        switch (MarsPlanner.Status.values()[t.marsStatus()]) {
            case OK -> {
                rows.add(new Row("hud.spacehorizons.rocket.mars.phase", degrees(t.marsPhaseNow()) + " / "
                        + (Float.isNaN(t.marsPhaseIdeal()) ? NONE : degrees(t.marsPhaseIdeal())), t.marsBurnNow() ? Tone.GOOD : Tone.NORMAL));
                rows.add(new Row("hud.spacehorizons.rocket.mars.dv", speed(t.marsDeltaV()), Tone.NORMAL));
                rows.add(new Row("hud.spacehorizons.rocket.mars.next", t.marsBurnNow() ? "0" : Float.isNaN(t.marsTimeToBurn()) ? NONE : time(t.marsTimeToBurn()),
                        t.marsBurnNow() ? Tone.GOOD : Tone.NORMAL));
            }
            case WRONG_DIRECTION -> rows.add(new Row("hud.spacehorizons.rocket.planner.wrong_direction", "", Tone.WARN));
            case NOT_IN_ORBIT -> {
                if (t.inSpace()) {
                    rows.add(new Row("hud.spacehorizons.rocket.planner.not_in_orbit", "", Tone.NORMAL));
                }
            }
            case NOT_AROUND_EARTH -> {
            }
        }
        return rows;
    }

    /** O planejador tem algo a mostrar (para o painel decidir se desenha a caixa). */
    public static boolean hasPlanner(@Nullable RocketTelemetryPayload t) {
        return !plannerRows(t).isEmpty();
    }
}
