package com.spacehorizons.content;

import java.util.function.UnaryOperator;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registro de todos os blocos do mod. Cada bloco registrado por {@link #registerWithItem} ganha o seu BlockItem. */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SpaceHorizons.MODID);

    /**
     * PROVISÓRIO: bloco de teste da fundação de conteúdo (etapa 0).
     * Será removido/substituído pelos blocos reais na etapa 1.
     */
    public static final DeferredBlock<Block> PLACEHOLDER_BLOCK = registerWithItem(ContentIds.PLACEHOLDER_BLOCK,
            props -> props.strength(1.5F, 6.0F).sound(SoundType.STONE));

    private ModBlocks() {
    }

    /** Registra um bloco simples e o BlockItem de mesmo id (que entra em {@link ModItems}). */
    private static DeferredBlock<Block> registerWithItem(String name, UnaryOperator<BlockBehaviour.Properties> properties) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(name, properties);
        ModItems.ITEMS.registerSimpleBlockItem(block);
        return block;
    }
}
