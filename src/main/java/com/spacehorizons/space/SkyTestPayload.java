package com.spacehorizons.space;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Modo de teste do céu virtual (spike S1), do servidor ao cliente: {@code mode} 0 = desliga, 1 = atitude fixa (yaw, pitch, roll
 * em graus, eixos locais), 2 = giro contínuo de {@code a} graus por segundo em torno do eixo local {@code axis} (0 x, 1 y, 2 z).
 * Em mode 1, {@code a}, {@code b} e {@code c} são yaw, pitch e roll; em mode 2, {@code a} é a taxa e {@code axis} o eixo.
 */
public record SkyTestPayload(int mode, double a, double b, double c, int axis) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SkyTestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "sky_test"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, SkyTestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SkyTestPayload::mode,
            ByteBufCodecs.DOUBLE, SkyTestPayload::a,
            ByteBufCodecs.DOUBLE, SkyTestPayload::b,
            ByteBufCodecs.DOUBLE, SkyTestPayload::c,
            ByteBufCodecs.VAR_INT, SkyTestPayload::axis,
            SkyTestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
