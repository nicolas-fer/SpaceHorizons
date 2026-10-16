package com.spacehorizons.survival.suit;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Gatilho de conquista {@code spacehorizons:suit_breathing}: dispara a cada segundo em que o jogador respira só com
 * o traje (oxigênio dos recipientes) no vácuo, com quantos segundos seguidos ele já respira assim. O JSON da conquista
 * escolhe o mínimo: {@code "conditions": {"seconds": {"min": 30}}}.
 *
 * Gatilho próprio porque nenhum critério do vanilla enxerga "respirando do traje": o oxigênio vive num data
 * attachment e nos componentes dos recipientes, que os predicados de entidade e de item não conseguem testar com
 * duração. É um gatilho comum ({@link SimpleCriterionTrigger}), registrado em {@link SuitTriggers}.
 */
public class SuitBreathingTrigger extends SimpleCriterionTrigger<SuitBreathingTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Chamado no servidor, uma vez por segundo de respiração seguida; {@code seconds} é o tempo seguido até agora. */
    public void trigger(ServerPlayer player, int seconds) {
        this.trigger(player, instance -> instance.matches(seconds));
    }

    public record TriggerInstance(Optional<Holder<LootItemCondition>> player, MinMaxBounds.Ints seconds)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("seconds", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::seconds))
                .apply(instance, TriggerInstance::new));

        public boolean matches(int secondsBreathed) {
            return seconds.matches(secondsBreathed);
        }
    }
}
