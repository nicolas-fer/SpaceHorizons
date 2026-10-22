package com.spacehorizons.space.flight;

import java.util.Arrays;

/**
 * Quanto de cada {@link Propellant} o foguete leva, em mB (valor imutável, com igualdade por conteúdo). O foguete químico do Tier 1 só tem
 * {@link Propellant#CHEMICAL}; os do Tier 2 têm um "tanque" por combustível, e cada motor queima o seu.
 */
public final class FuelLoad {
    public static final FuelLoad EMPTY = new FuelLoad(new double[Propellant.count()]);

    private final double[] mb;

    private FuelLoad(double[] mb) {
        this.mb = mb;
    }

    /** Só combustível químico (o foguete do Tier 1). */
    public static FuelLoad chemical(double mb) {
        return EMPTY.with(Propellant.CHEMICAL, mb);
    }

    public static FuelLoad of(Propellant p, double mb) {
        return EMPTY.with(p, mb);
    }

    public double get(Propellant p) {
        return this.mb[p.ordinal()];
    }

    /** O mesmo com {@code p} trocado por {@code value} (negativo vira zero). */
    public FuelLoad with(Propellant p, double value) {
        double v = Math.max(0.0, value);
        if (this.mb[p.ordinal()] == v) {
            return this;
        }
        double[] copy = this.mb.clone();
        copy[p.ordinal()] = v;
        return new FuelLoad(copy);
    }

    public FuelLoad plus(Propellant p, double add) {
        return with(p, get(p) + add);
    }

    /** Soma, combustível a combustível. */
    public FuelLoad plus(FuelLoad other) {
        double[] copy = this.mb.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] += other.mb[i];
        }
        return new FuelLoad(copy);
    }

    /** Cada combustível limitado ao do {@code cap} (a capacidade). */
    public FuelLoad clampTo(FuelLoad cap) {
        double[] copy = this.mb.clone();
        boolean changed = false;
        for (int i = 0; i < copy.length; i++) {
            double v = Math.max(0.0, Math.min(cap.mb[i], copy[i]));
            changed |= v != copy[i];
            copy[i] = v;
        }
        return changed ? new FuelLoad(copy) : this;
    }

    /** Cada combustível arredondado para baixo (o que volta aos blocos ao pousar). */
    public FuelLoad floor() {
        double[] copy = this.mb.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] = Math.floor(copy[i]);
        }
        return new FuelLoad(copy);
    }

    /** Massa total da carga, em toneladas (o núcleo nuclear não pesa). */
    public double massT() {
        double t = 0.0;
        for (Propellant p : Propellant.values()) {
            t += this.mb[p.ordinal()] * p.tonsPerMb();
        }
        return t;
    }

    /** Soma dos combustíveis com massa, em mB (o que o painel chama de "combustível"; o núcleo nuclear fica de fora). */
    public double propellantMb() {
        double total = 0.0;
        for (Propellant p : Propellant.values()) {
            if (p.hasMass()) {
                total += this.mb[p.ordinal()];
            }
        }
        return total;
    }

    /** Só há combustível químico (ou nada): o foguete do Tier 1. */
    public boolean onlyChemical() {
        for (Propellant p : Propellant.values()) {
            if (p != Propellant.CHEMICAL && this.mb[p.ordinal()] > 0.0) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmpty() {
        for (double v : this.mb) {
            if (v > 0.0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FuelLoad other && Arrays.equals(this.mb, other.mb);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.mb);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FuelLoad[");
        boolean first = true;
        for (Propellant p : Propellant.values()) {
            double v = this.mb[p.ordinal()];
            if (v > 0.0) {
                sb.append(first ? "" : ", ").append(p.key()).append('=').append(String.format(java.util.Locale.ROOT, "%.1f", v));
                first = false;
            }
        }
        return sb.append(']').toString();
    }
}
