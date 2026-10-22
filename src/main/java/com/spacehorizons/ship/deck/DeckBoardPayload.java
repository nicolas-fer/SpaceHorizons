package com.spacehorizons.ship.deck;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * O servidor põe o jogador no referencial da nave (servidor para cliente): a partir daqui o cliente simula o jogador em coordenadas locais,
 * começando em {@code state}, e deixa de mandar o pacote de posição do vanilla.
 *
 * @param rocketId id da entidade da nave
 * @param state posição, velocidade e olhar locais de partida
 */
public record DeckBoardPayload(int rocketId, DeckMovePayload state) implements CustomPacketPayload {
    public static final Type<DeckBoardPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "deck_board"));

    public static final StreamCodec<ByteBuf, DeckBoardPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DeckBoardPayload::rocketId,
            DeckMovePayload.CODEC, DeckBoardPayload::state,
            DeckBoardPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
