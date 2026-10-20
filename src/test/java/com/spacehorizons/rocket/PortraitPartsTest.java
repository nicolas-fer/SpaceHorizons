package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Retrato grande: a grade da nave da D2 e o envio em partes (cortar, juntar, fora de ordem, recusar o incoerente). */
class PortraitPartsTest {
    private static byte[] randomBytes(int n, long seed) {
        byte[] data = new byte[n];
        new Random(seed).nextBytes(data);
        return data;
    }

    @Test
    void cortaEJuntaSemPerderNada() {
        byte[] data = randomBytes(1_000_003, 1);
        List<byte[]> parts = PortraitParts.split(data, PortraitParts.PART_BYTES);
        assertEquals(4, parts.size(), "1.000.003 bytes em partes de 256 KiB");
        assertTrue(parts.stream().allMatch(p -> p.length <= PortraitParts.PART_BYTES));
        assertArrayEquals(data, PortraitParts.join(parts));
    }

    @Test
    void dadosVaziosEExatosDaoAQuantidadeCerta() {
        assertEquals(1, PortraitParts.split(new byte[0], 10).size());
        assertEquals(1, PortraitParts.split(new byte[10], 10).size());
        assertEquals(2, PortraitParts.split(new byte[11], 10).size());
        assertEquals(3, PortraitParts.split(new byte[30], 10).size());
    }

    @Test
    void receptorJuntaPartesForaDeOrdemERepetidas() {
        byte[] data = randomBytes(700_000, 2);
        List<byte[]> parts = PortraitParts.split(data, PortraitParts.PART_BYTES);
        UUID id = UUID.randomUUID();
        PortraitParts.Receiver receiver = new PortraitParts.Receiver();
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2));
        Collections.reverse(order);
        assertFalse(receiver.accept(id, order.get(0), parts.size(), parts.get(order.get(0))));
        assertEquals(2, receiver.missing(id));
        assertFalse(receiver.accept(id, order.get(0), parts.size(), parts.get(order.get(0))), "parte repetida não conta");
        assertFalse(receiver.accept(id, order.get(1), parts.size(), parts.get(order.get(1))));
        assertTrue(receiver.accept(id, order.get(2), parts.size(), parts.get(order.get(2))));
        assertEquals(0, receiver.missing(id));
        assertArrayEquals(data, receiver.take(id));
        assertNull(receiver.take(id), "só se pega uma vez");
        assertEquals(-1, receiver.missing(id));
    }

    @Test
    void receptorSeparaRetratosDiferentes() {
        PortraitParts.Receiver receiver = new PortraitParts.Receiver();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        byte[] da = randomBytes(300_000, 3);
        byte[] db = randomBytes(10, 4);
        List<byte[]> pa = PortraitParts.split(da, PortraitParts.PART_BYTES);
        assertFalse(receiver.accept(a, 0, pa.size(), pa.get(0)));
        assertTrue(receiver.accept(b, 0, 1, db));
        assertArrayEquals(db, receiver.take(b));
        assertTrue(receiver.accept(a, 1, pa.size(), pa.get(1)));
        assertArrayEquals(da, receiver.take(a));
    }

    @Test
    void receptorRecusaPartesIncoerentes() {
        PortraitParts.Receiver receiver = new PortraitParts.Receiver();
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(id, 3, 3, new byte[1]), "índice fora da quantidade");
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(id, -1, 3, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(id, 0, 0, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(id, 0, PortraitParts.MAX_PARTS + 1, new byte[1]));
        receiver.accept(id, 0, 4, new byte[1]);
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(id, 1, 5, new byte[1]), "quantidade diferente da primeira parte");
    }

    @Test
    void receptorNaoGuardaPartesDeMaisRetratosDoQueOLimite() {
        PortraitParts.Receiver receiver = new PortraitParts.Receiver();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            receiver.accept(id, 0, 2, new byte[1]);
        }
        assertEquals(-1, receiver.missing(ids.get(0)), "o mais antigo foi esquecido");
        assertEquals(1, receiver.missing(ids.get(19)));
    }

    // ---------------------------------------------------------------- a grade da nave da D2

    @Test
    void gradeDe128x128x256ComOito192BlocosSerializaEmRepeticoesPequenas() {
        PortraitGrid grid = PortraitGrid.empty(128, 256, 128);
        assertEquals(128 * 256 * 128, grid.volume());
        // um casco esparso: um pilar vertical no meio e um anel no chão (~8.000 blocos)
        int placed = 0;
        for (int y = 0; y < 256 && placed < 8192; y++) {
            grid.set(64, y, 64, 1 + y % 5);
            placed++;
        }
        for (int x = 0; x < 128 && placed < 8192; x++) {
            if (x == 64) {
                continue; // o pilar já ocupa a coluna x = 64
            }
            for (int z = 0; z < 128 && placed < 8192; z += 16) {
                grid.set(x, 0, z, 6);
                placed++;
            }
        }
        assertEquals(placed, grid.nonEmptyCount());
        ByteBuf buf = Unpooled.buffer();
        grid.writeRuns(buf);
        assertTrue(buf.readableBytes() < 200_000, "4 milhões de células compactadas: " + buf.readableBytes() + " bytes");
        PortraitGrid read = PortraitGrid.readRuns(buf, 128, 256, 128, 6);
        assertEquals(grid, read);
    }

    @Test
    void gradeCheiaDeBlocosDiferentesPassaDoPacoteDe1MibEPrecisaDePartes() {
        // 8.192 blocos com estados variados, sem repetição: o pior caso de runs (2 VarInt por célula)
        PortraitGrid grid = PortraitGrid.empty(32, 32, 8);
        Random random = new Random(7);
        for (int y = 0; y < 32; y++) {
            for (int z = 0; z < 8; z++) {
                for (int x = 0; x < 32; x++) {
                    grid.set(x, y, z, 1 + random.nextInt(8000));
                }
            }
        }
        ByteBuf buf = Unpooled.buffer();
        grid.writeRuns(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        List<byte[]> parts = PortraitParts.split(bytes, 4096);
        assertTrue(parts.size() > 1, "vários pedaços de 4 KiB");
        PortraitParts.Receiver receiver = new PortraitParts.Receiver();
        UUID id = UUID.randomUUID();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, new Random(9));
        boolean done = false;
        for (int i : order) {
            done = receiver.accept(id, i, parts.size(), parts.get(i));
        }
        assertTrue(done);
        PortraitGrid read = PortraitGrid.readRuns(Unpooled.wrappedBuffer(receiver.take(id)), 32, 32, 8, 8000);
        assertEquals(grid, read, "a grade chega igual depois das partes");
    }

    @Test
    void gradeGrandeDemaisEhRecusada() {
        assertThrows(IllegalArgumentException.class, () -> PortraitGrid.empty(129, 256, 128 * 256 / 256 + 1000));
        assertThrows(IllegalArgumentException.class, () -> PortraitGrid.empty(128, 257, 128).set(0, 0, 0, 70_000));
    }

    @Test
    void indiceDePaletaAcimaDe16BitsEhRecusado() {
        PortraitGrid grid = PortraitGrid.empty(2, 2, 2);
        grid.set(0, 0, 0, 65_535);
        assertEquals(65_535, grid.get(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> grid.set(0, 0, 0, 65_536));
    }
}
