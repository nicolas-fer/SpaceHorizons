package com.spacehorizons.ship.deck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.AABB;

/** O embarque automático (decisão de apoio no piso) e a correspondência estaleiro / convés / pouso, sem o jogo. */
class DeckBoardingTest {
    private static final double W = 0.6;
    private static final double H = 1.8;
    private static final double EPS = 1.0E-9;
    /** Piso 10 x 10 com o topo em y = 2 e uma parede de 3 blocos de altura em x de 8 a 10. */
    private static final AABB FLOOR = new AABB(0, 1, 0, 10, 2, 10);
    private static final AABB WALL = new AABB(8, 2, 0, 10, 5, 10);

    private static DeckCollision.Colliders boxes(AABB... all) {
        List<AABB> list = List.of(all);
        return (region, out) -> {
            for (AABB b : list) {
                if (b.intersects(region)) {
                    out.accept(b);
                }
            }
        };
    }

    @Test
    void pesNoTopoEmbarcam() {
        Vector3d at = DeckBoarding.settle(new Vector3d(5, 2.0, 5), W, H, boxes(FLOOR, WALL), DeckBoarding.REACH, DeckBoarding.ABOVE);
        assertNotNull(at);
        assertEquals(2.0, at.y, EPS);
        assertEquals(5.0, at.x, EPS);
    }

    @Test
    void quemJaAtravessouUmPoucoDoPisoSobeAoTopo() {
        Vector3d at = DeckBoarding.settle(new Vector3d(5, 1.6, 5), W, H, boxes(FLOOR), DeckBoarding.REACH, DeckBoarding.ABOVE);
        assertNotNull(at);
        assertEquals(2.0, at.y, EPS);
    }

    @Test
    void logoAcimaDoPisoAindaEmbarca() {
        assertNotNull(DeckBoarding.settle(new Vector3d(5, 2.2, 5), W, H, boxes(FLOOR), DeckBoarding.REACH, DeckBoarding.ABOVE));
    }

    @Test
    void altoDemaisOuFundoDemaisNaoEmbarca() {
        assertNull(DeckBoarding.settle(new Vector3d(5, 3.0, 5), W, H, boxes(FLOOR), DeckBoarding.REACH, DeckBoarding.ABOVE));
        assertNull(DeckBoarding.settle(new Vector3d(5, 1.0, 5), W, H, boxes(FLOOR), DeckBoarding.REACH, DeckBoarding.ABOVE));
    }

    @Test
    void foraDoPisoNaoEmbarca() {
        assertNull(DeckBoarding.settle(new Vector3d(15, 2.0, 5), W, H, boxes(FLOOR), DeckBoarding.REACH, DeckBoarding.ABOVE));
    }

    @Test
    void semAlturaLivreNaoEmbarca() {
        // teto a 1,5 bloco do piso: a caixa de 1,8 não cabe em pé
        AABB ceiling = new AABB(0, 3.5, 0, 10, 4, 10);
        assertNull(DeckBoarding.settle(new Vector3d(5, 2.0, 5), W, H, boxes(FLOOR, ceiling), DeckBoarding.REACH, DeckBoarding.ABOVE));
    }

    @Test
    void escolheOTopoMaisAltoQueCabe() {
        // dentro do cockpit (bloco cheio de 2 a 3) com a folga de assento: sobe ao topo dele, não ao piso de baixo
        AABB cockpit = new AABB(4, 2, 4, 5, 3, 5);
        Vector3d at = DeckBoarding.settle(new Vector3d(4.5, 1.8, 4.5), W, H, boxes(FLOOR, cockpit), DeckBoarding.SEAT_REACH, DeckBoarding.SEAT_ABOVE);
        assertNotNull(at);
        assertEquals(3.0, at.y, EPS);
    }

    @Test
    void levantarDoAssentoSemEspacoEmCimaFicaAoLado() {
        // cockpit sob um teto baixo: em cima não cabe, o tripulante fica no piso ao lado
        AABB cockpit = new AABB(4, 2, 4, 5, 3, 5);
        AABB ceiling = new AABB(3.5, 4.2, 3.5, 5.5, 5, 5.5);
        Vector3d at = DeckBoarding.settleNear(new Vector3d(4.5, 1.8, 4.5), W, H, boxes(FLOOR, cockpit, ceiling), DeckBoarding.SEAT_REACH,
                DeckBoarding.SEAT_ABOVE, 2);
        assertNotNull(at);
        assertEquals(2.0, at.y, EPS);
        assertTrue(Math.abs(at.x - 4.5) >= 1.0 - EPS || Math.abs(at.z - 4.5) >= 1.0 - EPS);
    }

    @Test
    void naveRapidaNaoEmbarcaSozinha() {
        assertTrue(DeckBoarding.slowEnough(0.5));
        assertTrue(DeckBoarding.slowEnough(DeckBoarding.MAX_AUTO_SPEED));
        assertFalse(DeckBoarding.slowEnough(10.0));
    }

    @Test
    void posicaoRelativaEstaleiroEConvesSaoInversas() {
        Vector3d local = new Vector3d(3.25, 1.0, 7.75);
        Vector3d yard = DeckYardMap.toYard(local, 1000, 64, -2000);
        assertEquals(1003.25, yard.x, EPS);
        assertEquals(65.0, yard.y, EPS);
        assertEquals(-1992.25, yard.z, EPS);
        Vector3d back = DeckYardMap.toLocal(yard.x, yard.y, yard.z, 1000, 64, -2000);
        assertEquals(local.x, back.x, EPS);
        assertEquals(local.y, back.y, EPS);
        assertEquals(local.z, back.z, EPS);
    }

    @Test
    void giroDoPousoSegueARegraDosBlocos() {
        // caixa 5 x 3 (x por z): o bloco (1, 2) com giro de 1 quarto vai a (sizeZ-1-2, 1) = (0, 1); o ponto no centro do bloco, idem
        Vector3d p = DeckYardMap.rotate(new Vector3d(1.5, 4.0, 2.5), 1, 5, 3);
        assertEquals(0.5, p.x, EPS);
        assertEquals(4.0, p.y, EPS);
        assertEquals(1.5, p.z, EPS);
        Vector3d two = DeckYardMap.rotate(new Vector3d(1.5, 4.0, 2.5), 2, 5, 3);
        assertEquals(3.5, two.x, EPS);
        assertEquals(0.5, two.z, EPS);
        Vector3d three = DeckYardMap.rotate(new Vector3d(1.5, 4.0, 2.5), 3, 5, 3);
        assertEquals(2.5, three.x, EPS);
        assertEquals(3.5, three.z, EPS);
        Vector3d none = DeckYardMap.rotate(new Vector3d(1.5, 4.0, 2.5), 0, 5, 3);
        assertEquals(1.5, none.x, EPS);
        assertEquals(2.5, none.z, EPS);
    }

    @Test
    void quatroGirosDeUmQuartoVoltamAoComeco() {
        Vector3d p = new Vector3d(1.3, 2.0, 2.2);
        Vector3d q = new Vector3d(p);
        int sx = 5;
        int sz = 3;
        for (int i = 0; i < 4; i++) {
            q = DeckYardMap.rotate(q, 1, i % 2 == 0 ? sx : sz, i % 2 == 0 ? sz : sx);
        }
        assertEquals(p.x, q.x, EPS);
        assertEquals(p.z, q.z, EPS);
    }

    @Test
    void posicaoNoMundoDoPouso() {
        Vector3d w = DeckYardMap.landed(new Vector3d(1.5, 1.0, 0.5), 0, 5, 5, 100, 64, 200);
        assertEquals(101.5, w.x, EPS);
        assertEquals(65.0, w.y, EPS);
        assertEquals(200.5, w.z, EPS);
    }

    @Test
    void quedaAcumulaEPagaAoTocar() {
        DeckFall.Step s = DeckFall.next(0.0, -1.5, false);
        assertEquals(1.5, s.fall(), EPS);
        assertEquals(0.0, s.landed(), EPS);
        s = DeckFall.next(s.fall(), -2.0, false);
        assertEquals(3.5, s.fall(), EPS);
        s = DeckFall.next(s.fall(), -0.5, true);
        assertEquals(0.0, s.fall(), EPS);
        assertEquals(4.0, s.landed(), EPS);
        assertEquals(0.0, DeckFall.next(0.0, 0.0, true).landed(), EPS);
    }

    @Test
    void subirNaoContaComoQueda() {
        assertEquals(2.0, DeckFall.next(2.0, 0.4, false).fall(), EPS);
    }

    @Test
    void multiplicadorDoBlocoSobOsPes() {
        assertEquals(1.0F, DeckFall.multiplier("iron_block"));
        assertEquals(0.0F, DeckFall.multiplier("slime_block"));
        assertEquals(0.2F, DeckFall.multiplier("hay_block"));
        assertEquals(0.5F, DeckFall.multiplier("red_bed"));
    }
}
