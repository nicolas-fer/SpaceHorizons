package com.spacehorizons.space.vessel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Escolhe quais veículos o servidor manda a um observador (docs/PLANO_NAVES.md, 4.3; fase 2d, lógica pura): os mais próximos dentro do raio de
 * estado, sem o próprio veículo do observador, no máximo {@link #MAX_VESSELS}. O casco só vale a pena (e o cliente só o pede) dentro do raio do casco; além dele
 * o veículo é um impostor (um ponto ou uma caixa mínima).
 */
public final class VesselSelector {
    /** Raio (blocos) dentro do qual o estado do veículo é mandado: dos 2 a 5 km do plano, com folga para o impostor ser visível. */
    public static final double STATE_RADIUS = 20_000.0;
    /** Raio (blocos) dentro do qual o cliente pede o casco do veículo. */
    public static final double HULL_RADIUS = 5_000.0;
    /** No máximo isto de veículos por observador. */
    public static final int MAX_VESSELS = 8;

    private VesselSelector() {
    }

    /** A distância (blocos) entre o observador e o veículo, em E. */
    public static double distance(double[] observer, double[] vessel) {
        double dx = vessel[0] - observer[0];
        double dy = vessel[1] - observer[1];
        double dz = vessel[2] - observer[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Os veículos de {@code all} dentro de {@code radius} do observador, do mais perto ao mais longe, sem o de id {@code self} (null: nenhum), no máximo
     * {@code max}. A distância é à posição do veículo menos o raio dele (a ponta do casco, não o centro, decide), para um veículo enorme não sumir
     * cedo demais.
     */
    public static List<VesselEntry> select(double[] observer, List<VesselEntry> all, @Nullable UUID self, double radius, int max) {
        List<VesselEntry> within = new ArrayList<>();
        for (VesselEntry e : all) {
            if (e.id().equals(self)) {
                continue;
            }
            if (distance(observer, e.pos()) - e.radius() <= radius) {
                within.add(e);
            }
        }
        within.sort(Comparator.comparingDouble((VesselEntry e) -> distance(observer, e.pos())));
        return within.size() <= max ? within : new ArrayList<>(within.subList(0, max));
    }

    /** O cliente deve ter o casco deste veículo (está dentro do raio do casco)? */
    public static boolean wantsHull(double distance, double radius) {
        return distance - radius <= HULL_RADIUS;
    }
}
