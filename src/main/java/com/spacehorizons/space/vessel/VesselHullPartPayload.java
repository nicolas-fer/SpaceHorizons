package com.spacehorizons.space.vessel;

import java.util.UUID;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.PortraitParts;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Uma parte do casco codificado de um veículo (servidor para cliente): o casco vai em partes de até {@link PortraitParts#PART_BYTES} (as mesmas contas
 * do retrato do foguete), o cliente as junta em {@link PortraitParts.Receiver}.
 *
 * @param id id do veículo
 * @param version versão do casco (o cliente descarta partes de uma versão velha)
 * @param index número da parte
 * @param count quantas partes
 * @param bytes os bytes
 */
public record VesselHullPartPayload(UUID id, int version, int index, int count, byte[] bytes) implements CustomPacketPayload {
    public static final Type<VesselHullPartPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "vessel_hull_part"));

    public static final StreamCodec<ByteBuf, VesselHullPartPayload> CODEC = StreamCodec.of((buf, p) -> {
        FriendlyByteBuf b = new FriendlyByteBuf(buf);
        b.writeUUID(p.id);
        b.writeVarInt(p.version);
        b.writeVarInt(p.index);
        b.writeVarInt(p.count);
        b.writeByteArray(p.bytes);
    }, buf -> {
        FriendlyByteBuf b = new FriendlyByteBuf(buf);
        return new VesselHullPartPayload(b.readUUID(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readByteArray(PortraitParts.PART_BYTES + 16));
    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
