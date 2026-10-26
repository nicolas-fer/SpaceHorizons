package com.spacehorizons.ship.deck;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * O estado LOCAL de um tripulante, para os OUTROS clientes que veem a nave (servidor para cliente, a cada tick da nave, no lugar do pacote de posição
 * do vanilla do tripulante, que o servidor suprime para quem está no convés). Leva o tick do servidor em que o estado valeu (o mesmo carimbo da pose da
 * nave, {@code RocketEntity.stampPose}): o observador desenha o tripulante na pose da nave daquele mesmo tick, na mesma linha do tempo do casco.
 *
 * @param playerId id da entidade do jogador (tripulante)
 * @param rocketId id da entidade da nave
 * @param stamp tick do servidor da pose (e do estado)
 * @param aboard false avisa que o tripulante saiu do convés: o observador volta a obedecer o pacote de posição do vanilla
 * @param x posição dos pés no retrato
 * @param yaw olhar local (graus do Minecraft)
 * @param onGround se está no chão
 */
public record DeckObservePayload(int playerId, int rocketId, int stamp, boolean aboard, double x, double y, double z, float yaw, float pitch,
        boolean onGround) implements CustomPacketPayload {
    public static final Type<DeckObservePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "deck_observe"));

    public static final StreamCodec<ByteBuf, DeckObservePayload> CODEC = StreamCodec.of((buf, p) -> p.write(new FriendlyByteBuf(buf)),
            buf -> read(new FriendlyByteBuf(buf)));

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.playerId);
        buf.writeVarInt(this.rocketId);
        buf.writeVarInt(this.stamp);
        buf.writeBoolean(this.aboard);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.pitch);
        buf.writeBoolean(this.onGround);
    }

    private static DeckObservePayload read(FriendlyByteBuf buf) {
        return new DeckObservePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    /** O estado a bordo de um tripulante. */
    public static DeckObservePayload of(int playerId, int rocketId, int stamp, DeckState s) {
        return new DeckObservePayload(playerId, rocketId, stamp, true, s.pos.x, s.pos.y, s.pos.z, s.yaw, s.pitch, s.onGround);
    }

    /** O aviso de que o tripulante deixou o convés. */
    public static DeckObservePayload left(int playerId, int rocketId, int stamp) {
        return new DeckObservePayload(playerId, rocketId, stamp, false, 0.0, 0.0, 0.0, 0.0F, 0.0F, false);
    }

    /** O estado como {@link DeckState} (sem velocidade: o observador não precisa dela). */
    public DeckState toState() {
        DeckState s = new DeckState();
        s.pos.set(this.x, this.y, this.z);
        s.yaw = this.yaw;
        s.pitch = this.pitch;
        s.onGround = this.onGround;
        return s;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
