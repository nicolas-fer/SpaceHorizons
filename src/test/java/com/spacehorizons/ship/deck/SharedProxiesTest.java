package com.spacehorizons.ship.deck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** O proxy do inventário (um por baú, compartilhado) e o ponto de pé ao levantar do assento. */
class SharedProxiesTest {
    @Test
    void doisJogadoresNoMesmoBauRecebemAMesmaInstancia() {
        SharedProxies<String, List<String>> proxies = new SharedProxies<>();
        AtomicInteger created = new AtomicInteger();
        List<String> a = proxies.acquire("nave:5,1,5", () -> {
            created.incrementAndGet();
            return new ArrayList<>();
        });
        List<String> b = proxies.acquire("nave:5,1,5", () -> {
            created.incrementAndGet();
            return new ArrayList<>();
        });
        assertSame(a, b);
        assertEquals(1, created.get(), "o conteúdo existe num lugar só");
        assertEquals(2, proxies.viewers("nave:5,1,5"));
        List<String> other = proxies.acquire("nave:6,1,5", ArrayList::new);
        assertNotSame(a, other);
    }

    @Test
    void oProxySoSomeQuandoOUltimoFecha() {
        SharedProxies<String, Object> proxies = new SharedProxies<>();
        Object first = proxies.acquire("k", Object::new);
        proxies.acquire("k", Object::new);
        assertFalse(proxies.release("k"));
        assertEquals(1, proxies.size());
        assertTrue(proxies.release("k"));
        assertEquals(0, proxies.size());
        assertFalse(proxies.release("k"), "fechar de novo não faz nada");
        assertNotSame(first, proxies.acquire("k", Object::new), "um novo uso começa de uma instância nova (relida do retrato)");
    }

    // ---- DeckStand ----

    private static DeckCollision.Colliders floorAndWalls() {
        // piso de 10x10 com topo em y=1; uma parede no x=6 e o próprio assento (meio bloco) em (5,1,5)
        return (region, out) -> {
            out.accept(new AABB(0, 0, 0, 10, 1, 10));
            out.accept(new AABB(6, 1, 0, 7, 4, 10));
            out.accept(new AABB(5, 1, 5, 6, 1.5, 6));
        };
    }

    @Test
    void emPeAoLadoDoAssentoComChaoELivre() {
        Vec3 spot = DeckStand.find(floorAndWalls(), 5, 1, 5, 0.6, 1.8);
        assertEquals(1.0, spot.y, 1e-9, "no piso");
        assertTrue(DeckStand.fits(floorAndWalls(), spot, 0.6, 1.8));
        assertTrue(Math.abs(spot.x - 5.5) <= 1.0 && Math.abs(spot.z - 5.5) <= 1.0, "no anel de raio 1 em volta do assento");
        assertFalse(spot.x > 6.0 && spot.x < 7.0, "não dentro da parede");
    }

    @Test
    void naoCabeNoSolidoNemNoAr() {
        assertFalse(DeckStand.fits(floorAndWalls(), new Vec3(6.5, 1.0, 5.5), 0.6, 1.8), "dentro da parede");
        assertFalse(DeckStand.fits(floorAndWalls(), new Vec3(3.5, 3.0, 3.5), 0.6, 1.8), "sem chão embaixo");
        assertTrue(DeckStand.fits(floorAndWalls(), new Vec3(3.5, 1.0, 3.5), 0.6, 1.8));
    }
}
