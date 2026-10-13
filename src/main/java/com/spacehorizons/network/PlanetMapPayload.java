package com.spacehorizons.network;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Um pedaço do mapa do planeta (PlanetMap.serialize(), já comprimido) enviado do servidor ao cliente. O mapa inteiro
 * passa de 1 MiB (limite de um payload do jogo para o cliente), então vai em {@code count} pedaços numerados, na
 * ordem, pela mesma conexão. {@code planet} é o id do planeta e {@code totalLength} o tamanho do mapa completo,
 * para o cliente validar a montagem.
 */
public record PlanetMapPayload(String planet, int totalLength, int index, int count, byte[] data) implements CustomPacketPayload {
    /** Tamanho máximo de um pedaço (bem abaixo do limite de 1 MiB do pacote). */
    public static final int MAX_CHUNK = 60_000;

    public static final CustomPacketPayload.Type<PlanetMapPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "planet_map"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, PlanetMapPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlanetMapPayload::planet,
            ByteBufCodecs.VAR_INT, PlanetMapPayload::totalLength,
            ByteBufCodecs.VAR_INT, PlanetMapPayload::index,
            ByteBufCodecs.VAR_INT, PlanetMapPayload::count,
            ByteBufCodecs.byteArray(MAX_CHUNK + 16), PlanetMapPayload::data,
            PlanetMapPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
