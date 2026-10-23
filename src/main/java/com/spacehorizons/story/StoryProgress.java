package com.spacehorizons.story;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.story.StoryEntry.Reward;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * O progresso de um jogador na trama (valor do Data Attachment, salvo com o jogador, copiado na morte e sincronizado com o cliente dele): os
 * registros que ele já leu e os terminais cuja recompensa ele já levou. Imutável.
 *
 * @param read    ids dos registros lidos (diários abertos e terminais usados)
 * @param claimed ids dos terminais cuja recompensa já foi entregue (uma vez por jogador)
 */
public record StoryProgress(Set<String> read, Set<String> claimed) {
    public static final StoryProgress EMPTY = new StoryProgress(Set.of(), Set.of());

    public static final MapCodec<StoryProgress> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("read", List.of()).forGetter(p -> List.copyOf(p.read)),
            Codec.STRING.listOf().optionalFieldOf("claimed", List.of()).forGetter(p -> List.copyOf(p.claimed)))
            .apply(instance, (read, claimed) -> new StoryProgress(new TreeSet<>(read), new TreeSet<>(claimed))));

    public static final StreamCodec<RegistryFriendlyByteBuf, StoryProgress> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), p -> List.copyOf(p.read),
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), p -> List.copyOf(p.claimed),
            (read, claimed) -> new StoryProgress(new TreeSet<>(read), new TreeSet<>(claimed)));

    /** O que um terminal entrega ao ser usado: o novo progresso, o que foi entregue agora e se foi a primeira vez deste jogador. */
    public record Claim(StoryProgress progress, List<Reward> granted, boolean first) {
    }

    public StoryProgress {
        read = Set.copyOf(new TreeSet<>(read));
        claimed = Set.copyOf(new TreeSet<>(claimed));
    }

    public boolean hasRead(String entryId) {
        return this.read.contains(entryId);
    }

    public boolean hasClaimed(String entryId) {
        return this.claimed.contains(entryId);
    }

    public StoryProgress withRead(String entryId) {
        if (this.read.contains(entryId)) {
            return this;
        }
        return new StoryProgress(with(this.read, entryId), this.claimed);
    }

    /**
     * Usa o terminal {@code entry}: na primeira vez do jogador entrega as recompensas e marca o terminal como lido e levado; nas seguintes não
     * entrega nada (a recompensa é uma vez por jogador, não por terminal).
     */
    public Claim claim(StoryEntry entry) {
        if (!entry.isTerminal()) {
            throw new IllegalArgumentException("só terminal entrega recompensa: " + entry.id());
        }
        StoryProgress read = this.withRead(entry.id());
        if (this.claimed.contains(entry.id())) {
            return new Claim(read, List.of(), false);
        }
        return new Claim(new StoryProgress(read.read, with(this.claimed, entry.id())), entry.rewards(), true);
    }

    /** O jogador pode escolher a planta embutida {@code blueprintId} no menu em sobrevivência? A lunar abre quando ele levou a do terminal da base. */
    public boolean unlocksBlueprint(String blueprintId) {
        return "lunar".equals(blueprintId) && this.claimed.contains(StoryEntries.SELENE_TERMINAL);
    }

    private static Set<String> with(Collection<String> set, String id) {
        List<String> all = new ArrayList<>(set);
        all.add(id);
        return new TreeSet<>(all);
    }
}
