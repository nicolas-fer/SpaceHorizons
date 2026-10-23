package com.spacehorizons.moonbase.room;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Gatilho de conquista {@code spacehorizons:sealed_room}: dispara para o jogador que está dentro de uma sala selada e pressurizada
 * pelo gerador de oxigênio (respirando o ar da sala). Sem condições além do jogador. Gatilho próprio porque nenhum critério do
 * vanilla enxerga o ar da sala (ele vive no {@link PressurizedRooms}, não no mundo).
 */
public class SealedRoomTrigger extends SimpleCriterionTrigger<SealedRoomTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> true);
    }

    public record TriggerInstance(Optional<Holder<LootItemCondition>> player) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player))
                .apply(instance, TriggerInstance::new));
    }
}
