package com.spacehorizons.space.vessel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A seleção dos veículos que o servidor manda a um observador, por distância (fase 2d das naves). */
class VesselSelectorTest {
    private static VesselEntry at(int n, double x, double y, double z, double radius) {
        return new VesselEntry(new UUID(0, n), VesselEntry.ROCKET, 1, (float) radius, new double[] {x, y, z}, VesselPose.identityAttitude(), new double[3], new double[3]);
    }

    @Test
    void escolheOsMaisPertoDentroDoRaioEmOrdemDeDistancia() {
        double[] observer = {100.0, 0.0, 0.0};
        List<VesselEntry> all = List.of(at(1, 100.0, 0.0, 9_000.0, 5.0), at(2, 100.0, 0.0, 300.0, 5.0), at(3, 100.0, 0.0, 30_000.0, 5.0), at(4, 100.0, 50.0, 0.0, 5.0));
        List<VesselEntry> chosen = VesselSelector.select(observer, all, null, VesselSelector.STATE_RADIUS, 8);
        assertEquals(List.of(new UUID(0, 4), new UUID(0, 2), new UUID(0, 1)), chosen.stream().map(VesselEntry::id).toList(), "o de 30 km fica de fora; a ordem é por distância");
    }

    @Test
    void oProprioVeiculoDoObservadorNuncaVai() {
        List<VesselEntry> all = List.of(at(1, 0, 0, 0, 5.0F), at(2, 10, 0, 0, 5.0F));
        List<VesselEntry> chosen = VesselSelector.select(new double[] {0, 0, 0}, all, new UUID(0, 1), VesselSelector.STATE_RADIUS, 8);
        assertEquals(1, chosen.size());
        assertEquals(new UUID(0, 2), chosen.get(0).id());
    }

    @Test
    void respeitaOMaximoFicandoComOsMaisPerto() {
        List<VesselEntry> all = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            all.add(at(i, 0, 0, i * 100.0, 5.0F));
        }
        List<VesselEntry> chosen = VesselSelector.select(new double[3], all, null, VesselSelector.STATE_RADIUS, 3);
        assertEquals(3, chosen.size());
        assertEquals(new UUID(0, 1), chosen.get(0).id());
        assertEquals(new UUID(0, 3), chosen.get(2).id());
    }

    @Test
    void oRaioDoVeiculoContaNoCorteEUmCascoGrandeNaoSomeCedo() {
        // o centro a 20.050 (fora) mas o raio de 100 deixa a ponta dentro dos 20.000
        List<VesselEntry> all = List.of(at(1, 0, 0, VesselSelector.STATE_RADIUS + 50.0, 100.0F), at(2, 0, 0, VesselSelector.STATE_RADIUS + 50.0, 10.0F));
        List<VesselEntry> chosen = VesselSelector.select(new double[3], all, null, VesselSelector.STATE_RADIUS, 8);
        assertEquals(List.of(new UUID(0, 1)), chosen.stream().map(VesselEntry::id).toList());
    }

    @Test
    void ocascoSoEPedidoDentroDoRaioDoCasco() {
        assertTrue(VesselSelector.wantsHull(300.0, 20.0));
        assertTrue(VesselSelector.wantsHull(VesselSelector.HULL_RADIUS, 0.0));
        assertFalse(VesselSelector.wantsHull(VesselSelector.HULL_RADIUS + 1.0, 0.0));
        assertTrue(VesselSelector.wantsHull(VesselSelector.HULL_RADIUS + 50.0, 100.0), "a ponta do casco decide");
    }

    @Test
    void distanciaEEuclidiana() {
        assertEquals(5.0, VesselSelector.distance(new double[] {0, 0, 0}, new double[] {3, 4, 0}), 1.0e-12);
    }
}
