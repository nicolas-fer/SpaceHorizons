package com.spacehorizons.content;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.survival.fuel.RefineryBlock;

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

    /** Refinaria: precisa de picareta; acende (luz 13) enquanto queima combustível de fornalha. */
    public static final DeferredBlock<RefineryBlock> REFINERY = registerBlockWithItem(ContentIds.REFINERY, RefineryBlock::new,
            props -> props.strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(RefineryBlock.LIT) ? 13 : 0));

    private ModBlocks() {
    }

    /** Como {@link #registerWithItem}, para blocos com classe própria. */
    private static <B extends Block> DeferredBlock<B> registerBlockWithItem(String name,
            Function<BlockBehaviour.Properties, ? extends B> factory, UnaryOperator<BlockBehaviour.Properties> properties) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, factory, properties);
        ModItems.ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    /** Registra um bloco simples e o BlockItem de mesmo id (que entra em {@link ModItems}). */
    private static DeferredBlock<Block> registerWithItem(String name, UnaryOperator<BlockBehaviour.Properties> properties) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(name, properties);
        ModItems.ITEMS.registerSimpleBlockItem(block);
        return block;
    }
}
