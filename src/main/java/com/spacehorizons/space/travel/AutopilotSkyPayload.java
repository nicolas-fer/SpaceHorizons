package com.spacehorizons.space.travel;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Posição REAL do piloto automático no espaço, enviada a cada tick enquanto o jogador está estacionado na dimensão de espaço
 * ({@link com.spacehorizons.rocket.SpaceParking}): o cliente desenha o céu (Terra, Lua, sol, estrelas) a partir dela e não da posição do
 * jogador, que não se mexe. Coordenadas da dimensão de espaço, as mesmas de {@code RocketEntity.virtualSpacePosition}.
 * {@code active} falso avisa que o céu volta a ser o da posição do jogador (o voo saiu do espaço ou terminou).
 */
public record AutopilotSkyPayload(boolean active, double x, double y, double z) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AutopilotSkyPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "autopilot_sky"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, AutopilotSkyPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AutopilotSkyPayload::active,
            ByteBufCodecs.DOUBLE, AutopilotSkyPayload::x,
            ByteBufCodecs.DOUBLE, AutopilotSkyPayload::y,
            ByteBufCodecs.DOUBLE, AutopilotSkyPayload::z,
            AutopilotSkyPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
