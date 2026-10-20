package com.spacehorizons.rocket;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** O relatório do painel de projeto (servidor para cliente), a resposta a {@link RocketDesignRequestPayload}. */
public record RocketDesignPayload(DesignReport report) implements CustomPacketPayload {
    public static final Type<RocketDesignPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "rocket_design"));

    public static final StreamCodec<ByteBuf, RocketDesignPayload> CODEC = StreamCodec.of((buf, p) -> p.report.write(new FriendlyByteBuf(buf)),
            buf -> new RocketDesignPayload(DesignReport.read(new FriendlyByteBuf(buf))));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
