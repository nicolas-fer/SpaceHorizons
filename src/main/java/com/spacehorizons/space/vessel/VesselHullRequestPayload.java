package com.spacehorizons.space.vessel;

import java.util.UUID;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * O cliente pede o casco de um veículo que está dentro do raio do casco ({@link VesselSelector#HULL_RADIUS}) e que ele ainda não tem (cliente para
 * servidor). O servidor só responde sobre veículos que ele mesmo mandou a este jogador.
 *
 * @param id id do veículo ({@link VesselEntry#id})
 * @param version versão que o cliente viu no estado
 */
public record VesselHullRequestPayload(UUID id, int version) implements CustomPacketPayload {
    public static final Type<VesselHullRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "vessel_hull_request"));

    public static final StreamCodec<ByteBuf, VesselHullRequestPayload> CODEC = StreamCodec.of((buf, p) -> {
        FriendlyByteBuf b = new FriendlyByteBuf(buf);
        b.writeUUID(p.id);
        b.writeVarInt(p.version);
    }, buf -> {
        FriendlyByteBuf b = new FriendlyByteBuf(buf);
        return new VesselHullRequestPayload(b.readUUID(), b.readVarInt());
    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
