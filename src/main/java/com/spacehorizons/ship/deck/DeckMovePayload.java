package com.spacehorizons.ship.deck;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A posição do tripulante em COORDENADAS LOCAIS da nave (cliente para servidor, a cada tick, no lugar do pacote de posição do vanilla, que leva
 * a posição do mundo e que o servidor ignora para quem está no convés). Como a posição é local, a velocidade da nave e o atraso da rede não
 * entram: o servidor aplica o estado recebido à pose da nave naquele momento.
 *
 * @param rocketId id da entidade da nave (descarta pacote de um embarque antigo)
 * @param x posição dos pés (retrato)
 * @param vx velocidade (blocos por tick local)
 * @param yaw olhar local (graus do Minecraft)
 * @param onGround se está no chão
 */
public record DeckMovePayload(int rocketId, double x, double y, double z, double vx, double vy, double vz, float yaw, float pitch,
        boolean onGround) implements CustomPacketPayload {
    public static final Type<DeckMovePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "deck_move"));

    public static final StreamCodec<ByteBuf, DeckMovePayload> CODEC = StreamCodec.of((buf, p) -> p.write(new FriendlyByteBuf(buf)),
            buf -> read(new FriendlyByteBuf(buf)));

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.rocketId);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeDouble(this.vx);
        buf.writeDouble(this.vy);
        buf.writeDouble(this.vz);
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.pitch);
        buf.writeBoolean(this.onGround);
    }

    private static DeckMovePayload read(FriendlyByteBuf buf) {
        return new DeckMovePayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    /** O estado como {@link DeckState}. */
    public DeckState toState() {
        DeckState s = new DeckState();
        s.pos.set(this.x, this.y, this.z);
        s.vel.set(this.vx, this.vy, this.vz);
        s.yaw = this.yaw;
        s.pitch = this.pitch;
        s.onGround = this.onGround;
        return s;
    }

    public static DeckMovePayload of(int rocketId, DeckState s) {
        return new DeckMovePayload(rocketId, s.pos.x, s.pos.y, s.pos.z, s.vel.x, s.vel.y, s.vel.z, s.yaw, s.pitch, s.onGround);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
