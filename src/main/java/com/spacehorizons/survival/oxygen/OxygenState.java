package com.spacehorizons.survival.oxygen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Estado de oxigênio de um jogador (valor do Data Attachment, salvo com o jogador e sincronizado com o cliente dele).
 *
 * @param reserve  reserva atual, de 0 a {@link OxygenMath#MAX_RESERVE} (em "ticks de respiração")
 * @param enabled  false quando a regra de jogo desliga o sistema; o HUD usa isso para não mostrar nada
 * @param supplied true quando alguma {@link OxygenSource} está abastecendo o jogador no vácuo (o HUD muda de cor)
 */
public record OxygenState(int reserve, boolean enabled, boolean supplied) {
    /** Estado inicial (e o de quem renasce): reserva cheia, sistema ligado, sem fonte. */
    public static final OxygenState FULL = new OxygenState(OxygenMath.MAX_RESERVE, true, false);

    public static final MapCodec<OxygenState> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(0, OxygenMath.MAX_RESERVE).fieldOf("reserve").forGetter(OxygenState::reserve),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(OxygenState::enabled),
            Codec.BOOL.optionalFieldOf("supplied", false).forGetter(OxygenState::supplied))
            .apply(instance, OxygenState::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, OxygenState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OxygenState::reserve,
            ByteBufCodecs.BOOL, OxygenState::enabled,
            ByteBufCodecs.BOOL, OxygenState::supplied,
            OxygenState::new);

    public OxygenState {
        reserve = Math.max(0, Math.min(OxygenMath.MAX_RESERVE, reserve));
    }

    public boolean isFull() {
        return reserve >= OxygenMath.MAX_RESERVE;
    }

    /** Segundos de respiração que restam no vácuo sem fonte (para o texto do HUD). */
    public double secondsLeft() {
        return reserve / (double) (OxygenMath.CONSUMPTION_PER_TICK * 20);
    }
}
