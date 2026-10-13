package com.spacehorizons.space.travel;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Aviso do servidor ao cliente, enviado imediatamente antes do teleporte entre corpos: o respawn que vem logo depois
 * é a nossa transferência contínua e o cliente não deve mostrar a tela de carregamento. Os pacotes seguem a mesma
 * conexão, então o aviso sempre chega antes do pacote de respawn. Leva também a posição e o olhar de destino: o
 * cliente já os aplica ao novo jogador, para o primeiro quadro depois do respawn não sair da origem do mundo novo.
 */
public record TravelTransferPayload(double x, double y, double z, float yRot, float xRot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TravelTransferPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "travel_transfer"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, TravelTransferPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, TravelTransferPayload::x,
            ByteBufCodecs.DOUBLE, TravelTransferPayload::y,
            ByteBufCodecs.DOUBLE, TravelTransferPayload::z,
            ByteBufCodecs.FLOAT, TravelTransferPayload::yRot,
            ByteBufCodecs.FLOAT, TravelTransferPayload::xRot,
            TravelTransferPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
