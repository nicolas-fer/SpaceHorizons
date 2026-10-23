package com.spacehorizons.story;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Gatilho de conquista {@code spacehorizons:story_entry}: dispara quando o jogador lê um diário ou usa um terminal da trama. O JSON da conquista escolhe o
 * registro: {@code "conditions": {"entry": "voss_01"}} (sem {@code entry}, vale qualquer um). Gatilho próprio porque o progresso da trama vive num data
 * attachment que os predicados do vanilla não enxergam; é um gatilho comum, registrado em {@link StorySetup}.
 */
public class StoryEntryTrigger extends SimpleCriterionTrigger<StoryEntryTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Chamado no servidor quando o jogador lê (ou usa) o registro {@code entryId}. */
    public void trigger(ServerPlayer player, String entryId) {
        this.trigger(player, instance -> instance.matches(entryId));
    }

    public record TriggerInstance(Optional<Holder<LootItemCondition>> player, Optional<String> entry) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.optionalFieldOf("entry").forGetter(TriggerInstance::entry))
                .apply(instance, TriggerInstance::new));

        public boolean matches(String entryId) {
            return this.entry.isEmpty() || this.entry.get().equals(entryId);
        }
    }
}
