package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;

/** O estado da âncora (fase 2f): a ordem dos impedimentos, o id pela rede e o pacote. */
class AnchorStatusTest {
    private static AnchorStatus classify(boolean busy, boolean parachute, double speed, double tilt, boolean needGenerator, int generators, int charged,
            boolean room) {
        return AnchorStatus.classify(true, busy, parachute, speed, tilt, needGenerator, generators, charged, () -> room);
    }

    @Test
    void naveParadaComGeradorCarregadoEEspacoPodeAncorar() {
        assertEquals(AnchorStatus.READY, classify(false, false, 0.0, 3.0, true, 2, 1, true));
    }

    @Test
    void semGeradorOuSemCarga() {
        assertEquals(AnchorStatus.NO_GENERATOR, classify(false, false, 0.0, 0.0, true, 0, 0, true));
        assertEquals(AnchorStatus.NO_ENERGY, classify(false, false, 0.0, 0.0, true, 3, 0, true));
        // o comando de dev do spike não exige gerador
        assertEquals(AnchorStatus.READY, classify(false, false, 0.0, 0.0, false, 0, 0, true));
    }

    @Test
    void semEspacoLivre() {
        assertEquals(AnchorStatus.NO_ROOM, classify(false, false, 0.0, 0.0, true, 1, 1, false));
    }

    @Test
    void velocidadeEInclinacaoNosLimites() {
        assertEquals(AnchorStatus.READY, classify(false, false, ShipAnchors.MAX_SPEED, ShipAnchors.MAX_TILT_DEGREES, true, 1, 1, true));
        assertEquals(AnchorStatus.TOO_FAST, classify(false, false, ShipAnchors.MAX_SPEED + 0.01, 0.0, true, 1, 1, true));
        assertEquals(AnchorStatus.TOO_TILTED, classify(false, false, 0.0, ShipAnchors.MAX_TILT_DEGREES + 0.01, true, 1, 1, true));
    }

    @Test
    void aOrdemDosImpedimentos() {
        // ocupada vence tudo; depois paraquedas, velocidade, inclinação, gerador, carga e, por último, o espaço
        assertEquals(AnchorStatus.BUSY, classify(true, true, 9.0, 90.0, true, 0, 0, false));
        assertEquals(AnchorStatus.PARACHUTE, classify(false, true, 9.0, 90.0, true, 0, 0, false));
        assertEquals(AnchorStatus.TOO_FAST, classify(false, false, 9.0, 90.0, true, 0, 0, false));
        assertEquals(AnchorStatus.TOO_TILTED, classify(false, false, 0.0, 90.0, true, 0, 0, false));
        assertEquals(AnchorStatus.NO_GENERATOR, classify(false, false, 0.0, 0.0, true, 0, 0, false));
        assertEquals(AnchorStatus.NO_ENERGY, classify(false, false, 0.0, 0.0, true, 1, 0, false));
        assertEquals(AnchorStatus.NO_ROOM, classify(false, false, 0.0, 0.0, true, 1, 1, false));
        assertEquals(AnchorStatus.NOT_SHIP, AnchorStatus.classify(false, true, true, 9.0, 90.0, true, 0, 0, () -> false));
    }

    @Test
    void oVolumeSoEVarridoSeORestoPassa() {
        AtomicInteger scans = new AtomicInteger();
        AnchorStatus.classify(true, false, false, 5.0, 0.0, true, 1, 1, () -> {
            scans.incrementAndGet();
            return true;
        });
        AnchorStatus.classify(true, false, false, 0.0, 0.0, true, 0, 0, () -> {
            scans.incrementAndGet();
            return true;
        });
        assertEquals(0, scans.get(), "velocidade e gerador recusam antes de varrer o mundo");
        AnchorStatus.classify(true, false, false, 0.0, 0.0, true, 1, 1, () -> {
            scans.incrementAndGet();
            return true;
        });
        assertEquals(1, scans.get());
    }

    @Test
    void oIdPelaRedeVoltaAoMesmoEstadoEDesconhecidoViraSemNave() {
        for (AnchorStatus s : AnchorStatus.values()) {
            assertEquals(s, AnchorStatus.byId(s.ordinal()));
            assertEquals(s.name().toLowerCase(java.util.Locale.ROOT), s.key());
        }
        assertEquals(AnchorStatus.NOT_SHIP, AnchorStatus.byId(-1));
        assertEquals(AnchorStatus.NOT_SHIP, AnchorStatus.byId(99));
        assertFalse(AnchorStatus.READY.refuses());
        assertFalse(AnchorStatus.ANCHORED.refuses());
        assertTrue(AnchorStatus.NO_ROOM.refuses());
        assertTrue(AnchorStatus.NOT_SHIP.refuses());
    }

    @Test
    void oPacoteVaiEVoltaComOsBlocosQueImpedem() {
        List<BlockPos> blockers = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            blockers.add(new BlockPos(100 + i, 64, -7));
        }
        AnchorStatusPayload original = new AnchorStatusPayload(AnchorStatus.NO_ROOM, blockers, true);
        assertEquals(AnchorStatusPayload.MAX_BLOCKERS, original.blockers().size(), "o pacote corta a lista no máximo");
        ByteBuf buf = Unpooled.buffer();
        AnchorStatusPayload.CODEC.encode(buf, original);
        AnchorStatusPayload back = AnchorStatusPayload.CODEC.decode(buf);
        assertEquals(original, back);
        assertEquals(AnchorStatus.NO_ROOM, back.anchorStatus());
        assertTrue(back.refused());
        assertEquals(new BlockPos(100, 64, -7), back.blockers().get(0));

        ByteBuf empty = Unpooled.buffer();
        AnchorRequestPayload.CODEC.encode(empty, new AnchorRequestPayload());
        assertEquals(0, empty.readableBytes());
        assertEquals(new AnchorRequestPayload(), AnchorRequestPayload.CODEC.decode(empty));
    }
}
