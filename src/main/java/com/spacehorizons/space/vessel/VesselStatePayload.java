package com.spacehorizons.space.vessel;

import java.util.ArrayList;
import java.util.List;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Os outros veículos próximos (servidor para o cliente que está no espaço ou numa nave do estaleiro; fase 2d das naves): o estado virtual de cada um
 * ({@link VesselEntry}), a poucos Hz. O cliente extrapola entre pacotes. Uma lista vazia limpa o que o cliente tinha.
 *
 * @param tick tempo de jogo do servidor (ticks) em que o estado vale: o cliente o usa para extrapolar num relógio de servidor estável
 * @param entries os veículos, do mais perto ao mais longe
 */
public record VesselStatePayload(long tick, List<VesselEntry> entries) implements CustomPacketPayload {
    public static final Type<VesselStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "vessel_state"));

    public static final StreamCodec<ByteBuf, VesselStatePayload> CODEC = StreamCodec.of((buf, p) -> p.write(new FriendlyByteBuf(buf)),
            buf -> read(new FriendlyByteBuf(buf)));

    private void write(FriendlyByteBuf buf) {
        buf.writeVarLong(this.tick);
        buf.writeVarInt(this.entries.size());
        for (VesselEntry e : this.entries) {
            buf.writeUUID(e.id());
            buf.writeByte(e.kind());
            buf.writeVarInt(e.hullVersion());
            buf.writeFloat(e.radius());
            writeAll(buf, e.pos());
            writeAll(buf, e.attitude());
            writeFloats(buf, e.velocity());
            writeFloats(buf, e.omega());
        }
    }

    private static VesselStatePayload read(FriendlyByteBuf buf) {
        long tick = buf.readVarLong();
        int count = buf.readVarInt();
        if (count < 0 || count > VesselSelector.MAX_VESSELS) {
            throw new IllegalArgumentException("veículos demais no pacote: " + count);
        }
        List<VesselEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new VesselEntry(buf.readUUID(), buf.readByte(), buf.readVarInt(), buf.readFloat(), readAll(buf, 3), readAll(buf, 4), readFloats(buf, 3),
                    readFloats(buf, 3)));
        }
        return new VesselStatePayload(tick, entries);
    }

    private static void writeAll(FriendlyByteBuf buf, double[] a) {
        for (double d : a) {
            buf.writeDouble(d);
        }
    }

    private static double[] readAll(FriendlyByteBuf buf, int n) {
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            a[i] = buf.readDouble();
        }
        return a;
    }

    /** A velocidade e o giro vão em float (blocos por tick e rad por tick são pequenos): o erro de extrapolação em 1 s fica abaixo de um milímetro. */
    private static void writeFloats(FriendlyByteBuf buf, double[] a) {
        for (double d : a) {
            buf.writeFloat((float) d);
        }
    }

    private static double[] readFloats(FriendlyByteBuf buf, int n) {
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            a[i] = buf.readFloat();
        }
        return a;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
