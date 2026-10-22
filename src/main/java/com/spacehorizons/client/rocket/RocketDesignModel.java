package com.spacehorizons.client.rocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.spacehorizons.client.rocket.RocketPanelModel.Row;
import com.spacehorizons.client.rocket.RocketPanelModel.Tone;
import com.spacehorizons.rocket.DesignReport;
import com.spacehorizons.rocket.MissionBudget;
import com.spacehorizons.rocket.RocketBlockKind;

/**
 * O que o painel de projeto da plataforma mostra, já formatado (lógica pura, testada): as linhas de números, a lista de destinos e os
 * problemas. O desenho está em {@link RocketDesignClient}; os textos traduzíveis são chaves ({@code design.spacehorizons.*}). As velocidades
 * seguem o painel de voo: blocos por segundo (b/s = b/t x 20); o relatório vem em blocos por tick.
 */
public final class RocketDesignModel {
    private RocketDesignModel() {
    }

    /** Um destino da lista: a chave do nome, o Δv exigido (b/s, com a margem) e se o foguete o alcança. */
    public record DestinationRow(String nameKey, String required, State state) {
    }

    public enum State {
        /** Alcança, e há um destino melhor que também alcança. */
        REACHED,
        /** O melhor destino que alcança. */
        BEST,
        /** Não alcança. */
        MISSING
    }

    /** Um problema para a lista: a chave do texto, os argumentos, se impede a montagem e onde está o bloco. */
    public record ProblemLine(String key, Object[] args, boolean blocking, int x, int y, int z) {
    }

    private static String bps(double blocksPerTick) {
        double v = blocksPerTick * RocketPanelModel.TICKS_PER_SECOND;
        return String.format(Locale.ROOT, "%,.0f b/s", v).replace(',', '.');
    }

    private static String tons(double t) {
        return String.format(Locale.ROOT, "%.1f t", t);
    }

    /** As linhas de números: blocos, massas, empuxo, TWR, Δv, motores e tanques. */
    public static List<Row> rows(DesignReport r) {
        List<Row> rows = new ArrayList<>();
        if (!r.found()) {
            return rows;
        }
        rows.add(new Row("design.spacehorizons.row.blocks", r.blocks() + " (" + r.sizeX() + "x" + r.sizeY() + "x" + r.sizeZ() + ")", Tone.NORMAL));
        rows.add(new Row("design.spacehorizons.row.dry_mass", tons(r.dryMassT()), Tone.NORMAL));
        rows.add(new Row("design.spacehorizons.row.wet_mass", tons(r.wetMassT()), Tone.NORMAL));
        rows.add(new Row("design.spacehorizons.row.thrust", String.format(Locale.ROOT, "%.1f", r.thrust()), r.thrust() > 0 ? Tone.NORMAL : Tone.BAD));
        rows.add(new Row("design.spacehorizons.row.isp", String.format(Locale.ROOT, "%.0f", r.ispTicks()), Tone.NORMAL));
        Tone twrTone = r.twr() <= MissionBudget.MIN_TWR_LIFTOFF ? Tone.BAD : r.twr() < MissionBudget.MIN_TWR_ORBIT ? Tone.WARN : Tone.GOOD;
        rows.add(new Row("design.spacehorizons.row.twr", Double.isFinite(r.twr()) ? String.format(Locale.ROOT, "%.2f", r.twr()) : RocketPanelModel.NONE, twrTone));
        rows.add(new Row("design.spacehorizons.row.delta_v", bps(r.deltaV()), r.deltaV() > 0 ? Tone.NORMAL : Tone.BAD));
        boolean tier2 = !r.lines().isEmpty();
        if (!tier2 || r.engineBlocks() > 0) {
            rows.add(new Row("design.spacehorizons.row.engines", enginesText(r), Tone.NORMAL));
        }
        if (!tier2 || r.tankBlocks() > 0) {
            rows.add(new Row("design.spacehorizons.row.tanks", tanksText(r), Tone.NORMAL));
        }
        for (DesignReport.Line line : r.lines()) {
            Tone tone = line.kind() == RocketBlockKind.ION_ENGINE && line.power() < 100 ? Tone.WARN : Tone.NORMAL;
            rows.add(new Row("design.spacehorizons.row." + line.kind().name().toLowerCase(Locale.ROOT), lineText(line), tone));
        }
        return rows;
    }

    /** A linha de um tipo do Tier 2: blocos e formas; motores com empuxo e Isp (e a energia do iônico); tanques com a capacidade. */
    static String lineText(DesignReport.Line l) {
        StringBuilder sb = new StringBuilder().append(l.blocks());
        List<String> parts = new ArrayList<>();
        if (l.kind().isEngine()) {
            if (l.cubes3() > 0) {
                parts.add(l.cubes3() + " x 3x3x3");
            }
            if (l.cubes2() > 0) {
                parts.add(l.cubes2() + " x 2x2x2");
            }
            if (l.solo() > 0) {
                parts.add(l.solo() + " x 1x1");
            }
            sb.append(parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")");
            sb.append(String.format(Locale.ROOT, " F %.2f Isp %.0f", l.thrust(), l.ispTicks()));
            if (l.kind() == RocketBlockKind.ION_ENGINE) {
                sb.append(" ").append(l.power()).append('%');
            }
        } else if (l.kind().isTank()) {
            if (l.cubes2() > 0) {
                parts.add(l.cubes2() + " col");
            }
            if (l.solo() > 0) {
                parts.add(l.solo() + " av");
            }
            sb.append(parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")");
            sb.append(String.format(Locale.ROOT, " %,.0f mB", l.capacityMb()).replace(',', '.'));
        }
        return sb.toString();
    }

    static String enginesText(DesignReport r) {
        StringBuilder sb = new StringBuilder().append(r.engineBlocks());
        List<String> parts = new ArrayList<>();
        if (r.engines3() > 0) {
            parts.add(r.engines3() + " x 3x3x3");
        }
        if (r.engines2() > 0) {
            parts.add(r.engines2() + " x 2x2x2");
        }
        if (r.soloEngines() > 0) {
            parts.add(r.soloEngines() + " x 1x1");
        }
        return sb.append(parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")").toString();
    }

    static String tanksText(DesignReport r) {
        StringBuilder sb = new StringBuilder().append(r.tankBlocks());
        List<String> parts = new ArrayList<>();
        if (r.tankColumns() > 0) {
            parts.add(r.tankColumns() + " col");
        }
        if (r.looseTanks() > 0) {
            parts.add(r.looseTanks() + " av");
        }
        return sb.append(parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")").toString();
    }

    /** Os destinos do salto suborbital em diante, cada um com o Δv exigido e se é alcançado. */
    public static List<DestinationRow> destinations(DesignReport r) {
        List<DestinationRow> rows = new ArrayList<>();
        MissionBudget.Destination best = r.destinationValue();
        for (MissionBudget.Destination d : MissionBudget.Destination.values()) {
            if (d.ordinal() < MissionBudget.Destination.SUBORBITAL.ordinal()) {
                continue;
            }
            State state = !r.found() || d.ordinal() > best.ordinal() ? State.MISSING : d == best ? State.BEST : State.REACHED;
            rows.add(new DestinationRow("design.spacehorizons.destination." + d.name().toLowerCase(Locale.ROOT), bps(MissionBudget.required(d)), state));
        }
        return rows;
    }

    /** A chave do melhor destino (a frase de destaque, inclusive "não decola"). */
    public static String headlineKey(DesignReport r) {
        return "design.spacehorizons.destination." + r.destinationValue().name().toLowerCase(Locale.ROOT);
    }

    /** O que falta para o próximo destino ("faltam 1.234 b/s de Δv"), ou null se já é o melhor ou não há o que dizer. */
    public static String missingForNext(DesignReport r) {
        if (!r.found() || r.nextRequiredDeltaV() <= 0.0 || r.nextRequiredDeltaV() <= r.deltaV()) {
            return null;
        }
        return bps(r.nextRequiredDeltaV() - r.deltaV());
    }

    /** Os problemas, os que impedem a montagem primeiro. */
    public static List<ProblemLine> problems(DesignReport r) {
        List<ProblemLine> lines = new ArrayList<>();
        for (DesignReport.Problem p : r.problems()) {
            Object[] args = switch (p.type()) {
                case MULTIPLE_COCKPITS -> new Object[] {p.arg()};
                case LOW_TWR -> new Object[] {String.format(Locale.ROOT, "%.2f", p.arg() / 100.0)};
                case LOW_POWER -> new Object[] {p.arg()};
                default -> new Object[] {p.x(), p.y(), p.z()};
            };
            String key = "design.spacehorizons.problem." + p.type().name().toLowerCase(Locale.ROOT);
            if (p.type() == DesignReport.Type.NO_ENGINE_FUEL && p.arg() >= 0 && p.arg() < RocketBlockKind.values().length) {
                // qual tanque falta: uma chave por tipo ("falta tanque de hidrogênio")
                key += "." + RocketBlockKind.values()[p.arg()].name().toLowerCase(Locale.ROOT);
            }
            lines.add(new ProblemLine(key, args, p.type().blocking(), p.x(), p.y(), p.z()));
        }
        return lines;
    }

    /** Para o log do autoteste: tudo o que o painel diz, em uma linha. */
    public static String describe(DesignReport r) {
        if (!r.found()) {
            return "nada sobre a plataforma";
        }
        StringBuilder sb = new StringBuilder();
        for (Row row : rows(r)) {
            sb.append(row.labelKey().substring(row.labelKey().lastIndexOf('.') + 1)).append('=').append(row.value()).append("; ");
        }
        sb.append("destino=").append(r.destinationValue()).append("; montavel=").append(r.assemblable()).append("; problemas=");
        for (ProblemLine p : problems(r)) {
            sb.append(p.key().substring(p.key().lastIndexOf('.') + 1)).append('@').append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append(' ');
        }
        return sb.toString().trim();
    }
}
