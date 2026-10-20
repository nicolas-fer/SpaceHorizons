package com.spacehorizons.ship;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * O estado virtual da nave (servidor para os clientes que estão dentro dela, a cada 2 ticks; plano 5): de onde o céu é visto e com que
 * atitude. O cliente extrapola a partir daqui entre dois pacotes (posição pela velocidade e atitude pelo giro, com o relógio que anda).
 *
 * @param shipId id da nave
 * @param tick tempo de jogo do servidor (ticks) em que o estado vale: o cliente o usa para extrapolar a partir de um relógio de servidor estável
 *             (sem o tremor da chegada dos pacotes)
 * @param clock relógio do Overworld (ticks, com fração) no instante do estado
 * @param clockRate ticks de relógio por tick de jogo
 * @param body id do corpo dominante da física ({@code earth}, {@code moon})
 * @param r posição inercial relativa ao centro do corpo (blocos)
 * @param v velocidade inercial relativa ao corpo (blocos/tick)
 * @param attitude atitude de estilo foguete {@code p} (inercial), x, y, z, w (ver {@link ShipFlight})
 * @param spin giro em eixos do corpo (rad/tick)
 * @param throttle acelerador aplicado (0 a 1)
 * @param sas modo do SAS (ordinal)
 * @param bow lado da proa no estaleiro (0 norte, 1 leste, 2 sul, 3 oeste; {@link ShipArrangement}): a ponte define a frente da nave
 */
public record ShipStatePayload(int shipId, long tick, double clock, double clockRate, String body, double[] r, double[] v, double[] attitude, double[] spin,
        float throttle, byte sas, byte bow) implements CustomPacketPayload {
    public static final Type<ShipStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "ship_state"));

    public static final StreamCodec<ByteBuf, ShipStatePayload> CODEC = StreamCodec.of((buf, p) -> p.write(new FriendlyByteBuf(buf)),
            buf -> read(new FriendlyByteBuf(buf)));

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.shipId);
        buf.writeVarLong(this.tick);
        buf.writeDouble(this.clock);
        buf.writeDouble(this.clockRate);
        buf.writeUtf(this.body, 32);
        writeAll(buf, this.r);
        writeAll(buf, this.v);
        writeAll(buf, this.attitude);
        writeAll(buf, this.spin);
        buf.writeFloat(this.throttle);
        buf.writeByte(this.sas);
        buf.writeByte(this.bow);
    }

    private static ShipStatePayload read(FriendlyByteBuf buf) {
        return new ShipStatePayload(buf.readVarInt(), buf.readVarLong(), buf.readDouble(), buf.readDouble(), buf.readUtf(32), readAll(buf, 3), readAll(buf, 3), readAll(buf, 4),
                readAll(buf, 3), buf.readFloat(), buf.readByte(), buf.readByte());
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
