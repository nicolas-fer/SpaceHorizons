package com.spacehorizons.ship;

import java.util.List;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * O estado da âncora para o painel do piloto (servidor para cliente): uma vez por segundo enquanto se pilota uma nave no planeta, e na hora de uma
 * recusa ({@code refused}, depois de o jogador apertar a tecla) ou de a nave ancorar. {@code blockers} são os blocos que impedem (até
 * {@link #MAX_BLOCKERS}), que o cliente destaca com partículas depois de uma recusa.
 */
public record AnchorStatusPayload(byte status, List<BlockPos> blockers, boolean refused) implements CustomPacketPayload {
    public static final int MAX_BLOCKERS = 16;

    public static final Type<AnchorStatusPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "anchor_status"));

    public static final StreamCodec<ByteBuf, AnchorStatusPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, AnchorStatusPayload::status,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_BLOCKERS)), AnchorStatusPayload::blockers,
            ByteBufCodecs.BOOL, AnchorStatusPayload::refused,
            AnchorStatusPayload::new);

    public AnchorStatusPayload(AnchorStatus status, List<BlockPos> blockers, boolean refused) {
        this((byte) status.ordinal(), blockers.size() > MAX_BLOCKERS ? List.copyOf(blockers.subList(0, MAX_BLOCKERS)) : List.copyOf(blockers), refused);
    }

    public AnchorStatus anchorStatus() {
        return AnchorStatus.byId(this.status);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
