package com.spacehorizons.story;

import java.util.function.Supplier;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registros da trama do sinal lunar (etapa 5 da sobrevivência, docs/PLANO_SOBREVIVENCIA.md): o progresso do jogador (Data Attachment), o gatilho de
 * conquista, a estrutura da Terra e os comandos de desenvolvimento. Os blocos, itens e o componente ficam nos registros gerais do mod
 * ({@code ModBlocks}, {@code ModItems}, {@code ModDataComponents}).
 */
public final class StorySetup {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SpaceHorizons.MODID);
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, SpaceHorizons.MODID);

    /** O progresso do jogador na trama: salvo, copiado na morte (a história não se perde) e sincronizado com o cliente dele. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<StoryProgress>> PROGRESS = ATTACHMENTS.register("story_progress",
            () -> AttachmentType.builder((Supplier<StoryProgress>) () -> StoryProgress.EMPTY)
                    .serialize(StoryProgress.CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, StoryProgress.STREAM_CODEC)
                    .build());

    /** {@code spacehorizons:story_entry}: leu um diário ou usou um terminal da trama. */
    public static final DeferredHolder<CriterionTrigger<?>, StoryEntryTrigger> ENTRY_TRIGGER = TRIGGERS.register("story_entry", StoryEntryTrigger::new);

    private StorySetup() {
    }

    public static void init(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        TRIGGERS.register(modEventBus);
        StoryWorldgen.init(modEventBus);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> StoryCommands.register(event.getDispatcher()));
    }
}
