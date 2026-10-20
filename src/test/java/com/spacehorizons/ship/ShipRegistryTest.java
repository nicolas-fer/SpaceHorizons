package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.Identifier;

/** O registro das naves: ids, células, salvar e ler de volta, quarentena entre sessões e ponto de retorno. */
class ShipRegistryTest {
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");

    @Test
    void criaNavesComIdsSequenciaisEmCelulasDiferentes() {
        ShipRegistry r = new ShipRegistry();
        ShipRecord a = r.create("A", 64, 64, 64, null, 100L);
        ShipRecord b = r.create("B", 64, 64, 64, UUID.randomUUID(), 200L);
        assertEquals(1, a.id());
        assertEquals(2, b.id());
        assertEquals(0, a.cell());
        assertEquals(1, b.cell());
        assertEquals(2, r.all().size());
        assertTrue(r.isDirty());
        assertNotNull(r.byId(2));
        assertNull(r.byId(3));
    }

    @Test
    void tamanhoInvalidoNaoConsomeCelula() {
        ShipRegistry r = new ShipRegistry();
        assertThrows(IllegalArgumentException.class, () -> r.create("grande", 200, 64, 64, null, 0L));
        assertEquals(0, r.allocator().highWater());
        assertEquals(0, r.create("ok", 64, 64, 64, null, 0L).cell());
    }

    @Test
    void acheANaveDaCelulaPeloPonto() {
        ShipRegistry r = new ShipRegistry();
        ShipRecord a = r.create("A", 64, 64, 64, null, 0L);
        ShipRecord b = r.create("B", 64, 64, 64, null, 0L);
        ShipYard.Box boxB = b.box();
        assertEquals(a.id(), r.atPosition(ShipYard.box(0, 64, 64, 64).minX() + 10, ShipYard.box(0, 64, 64, 64).minZ() + 10).id());
        assertEquals(b.id(), r.atPosition(boxB.minX() + 10, boxB.minZ() + 10).id());
        assertNull(r.atPosition(-5000, -5000));
    }

    @Test
    void salvaELeDeVoltaNavesAlocadorERetorno() {
        ShipRegistry r = new ShipRegistry();
        UUID dono = UUID.randomUUID();
        r.create("A", 64, 64, 64, dono, 100L);
        r.create("B", 96, 96, 96, null, 200L);
        r.create("C", 32, 32, 32, null, 300L);
        r.destroy(2);
        r.setReturn(dono, new ShipRegistry.ReturnPoint(OVERWORLD, 1.5, 70.0, -3.25, 90.0F, 15.0F));

        var json = ShipRegistry.CODEC.encodeStart(JsonOps.INSTANCE, r).getOrThrow();
        ShipRegistry lido = ShipRegistry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(2, lido.all().size());
        ShipRecord a = lido.byId(1);
        assertEquals("A", a.name());
        assertEquals(dono, a.owner().orElseThrow());
        assertEquals(100L, a.createdTick());
        assertEquals(64, a.sizeX());
        assertEquals(0, a.cell());
        assertEquals(2, lido.byId(3).cell());
        assertNull(lido.byId(2));
        ShipRegistry.ReturnPoint p = lido.returnPoint(dono);
        assertEquals(OVERWORLD, p.dimension());
        assertEquals(-3.25, p.z());
        assertEquals(90.0F, p.yRot());
        // o próximo id continua depois do maior (nenhum id é reutilizado, nem o da nave destruída)
        assertEquals(4, lido.create("D", 64, 64, 64, null, 0L).id());
    }

    @Test
    void celulaDaNaveDestruidaSoVoltaNaSessaoSeguinte() {
        ShipRegistry r = new ShipRegistry();
        r.create("A", 64, 64, 64, null, 0L);
        ShipRecord b = r.create("B", 64, 64, 64, null, 0L);
        assertTrue(r.destroy(b.id()));
        assertFalse(r.destroy(b.id()), "já destruída");
        assertEquals(2, r.create("C", 64, 64, 64, null, 0L).cell(), "mesma sessão: a célula 1 está em quarentena");

        // "fecha e abre o mundo": salva, lê numa instância nova e começa a sessão
        var json = ShipRegistry.CODEC.encodeStart(JsonOps.INSTANCE, r).getOrThrow();
        ShipRegistry nova = ShipRegistry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(1, nova.allocator().pendingCount());
        nova.beginSession();
        assertEquals(0, nova.allocator().pendingCount());
        assertEquals(1, nova.create("D", 64, 64, 64, null, 0L).cell(), "sessão nova: a célula 1 foi reaproveitada");
        nova.beginSession();   // chamar de novo na mesma sessão não faz nada
        assertEquals(3, nova.create("E", 64, 64, 64, null, 0L).cell());
    }

    @Test
    void limpaORetorno() {
        ShipRegistry r = new ShipRegistry();
        UUID jogador = UUID.randomUUID();
        assertNull(r.returnPoint(jogador));
        r.setReturn(jogador, new ShipRegistry.ReturnPoint(OVERWORLD, 0, 64, 0, 0F, 0F));
        assertNotNull(r.returnPoint(jogador));
        r.clearReturn(jogador);
        assertNull(r.returnPoint(jogador));
    }
}
