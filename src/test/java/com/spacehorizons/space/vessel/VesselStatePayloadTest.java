package com.spacehorizons.space.vessel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Os pacotes dos outros veículos (estado, pedido e partes do casco) e a chave estável da nave (fase 2d das naves). */
class VesselStatePayloadTest {
    private static VesselEntry entry(int n) {
        return new VesselEntry(new UUID(7, n), n % 2 == 0 ? VesselEntry.SHIP : VesselEntry.ROCKET, 100 + n, 12.5F + n, new double[] {104_303.25 + n, -2_000.125, 77.5},
                new double[] {0.1, 0.2, 0.3, 0.9}, new double[] {0.5F, -0.25F, 0.0}, new double[] {1.0e-3F, 0.0, -2.0e-3F});
    }

    @Test
    void estadoIdaEVoltaComPosicaoEmDoubleEVelocidadeEmFloat() {
        List<VesselEntry> entries = List.of(entry(1), entry(2));
        VesselStatePayload original = new VesselStatePayload(123_456L, entries);
        ByteBuf buf = Unpooled.buffer();
        VesselStatePayload.CODEC.encode(buf, original);
        VesselStatePayload back = VesselStatePayload.CODEC.decode(buf);
        assertEquals(123_456L, back.tick());
        assertEquals(2, back.entries().size());
        for (int i = 0; i < 2; i++) {
            VesselEntry a = entries.get(i);
            VesselEntry b = back.entries().get(i);
            assertEquals(a.id(), b.id());
            assertEquals(a.kind(), b.kind());
            assertEquals(a.hullVersion(), b.hullVersion());
            assertEquals(a.radius(), b.radius());
            assertArrayEquals(a.pos(), b.pos(), 0.0, "a posição vai em double: sem perda a 100 mil blocos");
            assertArrayEquals(a.attitude(), b.attitude(), 0.0);
            assertArrayEquals(a.velocity(), b.velocity(), 1.0e-9);
            assertArrayEquals(a.omega(), b.omega(), 1.0e-9);
        }
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void estadoVazioLimpaOCliente() {
        ByteBuf buf = Unpooled.buffer();
        VesselStatePayload.CODEC.encode(buf, new VesselStatePayload(5L, List.of()));
        assertEquals(0, VesselStatePayload.CODEC.decode(buf).entries().size());
    }

    @Test
    void cadaVeiculoCabeEmPoucosBytes() {
        ByteBuf buf = Unpooled.buffer();
        VesselStatePayload.CODEC.encode(buf, new VesselStatePayload(1L, List.of(entry(1))));
        assertEquals(1 + 1 + 16 + 1 + 1 + 4 + 24 + 32 + 12 + 12, buf.readableBytes(), "tick, contagem, uuid, tipo, versão, raio, pos, atitude, velocidade, giro");
    }

    @Test
    void recusaVeiculosDemaisNoPacote() {
        ByteBuf buf = Unpooled.buffer();
        net.minecraft.network.FriendlyByteBuf f = new net.minecraft.network.FriendlyByteBuf(buf);
        f.writeVarLong(1L);
        f.writeVarInt(VesselSelector.MAX_VESSELS + 1);
        assertThrows(IllegalArgumentException.class, () -> VesselStatePayload.CODEC.decode(buf));
    }

    @Test
    void pedidoEParteDeCascoIdaEVolta() {
        ByteBuf buf = Unpooled.buffer();
        VesselHullRequestPayload.CODEC.encode(buf, new VesselHullRequestPayload(new UUID(1, 2), 9));
        assertEquals(new VesselHullRequestPayload(new UUID(1, 2), 9), VesselHullRequestPayload.CODEC.decode(buf));
        ByteBuf partBuf = Unpooled.buffer();
        VesselHullPartPayload.CODEC.encode(partBuf, new VesselHullPartPayload(new UUID(3, 4), 5, 1, 3, new byte[] {1, 2, 3}));
        VesselHullPartPayload part = VesselHullPartPayload.CODEC.decode(partBuf);
        assertEquals(new UUID(3, 4), part.id());
        assertEquals(5, part.version());
        assertEquals(1, part.index());
        assertEquals(3, part.count());
        assertArrayEquals(new byte[] {1, 2, 3}, part.bytes());
    }

    @Test
    void naveTemUmUuidEstavelPorId() {
        assertEquals(VesselEntry.shipUuid(3), VesselEntry.shipUuid(3));
        List<UUID> ids = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            ids.add(VesselEntry.shipUuid(i));
        }
        assertEquals(50, ids.stream().distinct().count());
        assertEquals(VesselEntry.shipUuid(0).getMostSignificantBits(), VesselEntry.shipUuid(42).getMostSignificantBits(), "a nave se reconhece pelo prefixo");
        assertEquals(42, (int) VesselEntry.shipUuid(42).getLeastSignificantBits());
    }
}
