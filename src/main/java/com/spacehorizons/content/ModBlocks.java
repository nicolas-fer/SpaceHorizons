package com.spacehorizons.content;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.survival.compressor.OxygenCompressorBlock;
import com.spacehorizons.survival.fuel.RefineryBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registro de todos os blocos do mod. Cada bloco registrado por {@link #registerWithItem} ganha o seu BlockItem. */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SpaceHorizons.MODID);

    /** Bloco de aço: dureza de bloco de ferro; só dropa com picareta de ferro ou melhor (ver tags em data/minecraft/tags/block). */
    public static final DeferredBlock<Block> STEEL_BLOCK = registerWithItem(ContentIds.STEEL_BLOCK,
            props -> props.mapColor(MapColor.METAL).strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());

    /** Refinaria: precisa de picareta; acende (luz 13) enquanto queima combustível de fornalha. */
    public static final DeferredBlock<RefineryBlock> REFINERY = registerBlockWithItem(ContentIds.REFINERY, RefineryBlock::new,
            props -> props.strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(RefineryBlock.LIT) ? 13 : 0));

    /** Compressor de oxigênio: precisa de picareta; mesma dureza da refinaria. */
    public static final DeferredBlock<OxygenCompressorBlock> OXYGEN_COMPRESSOR = registerBlockWithItem(ContentIds.OXYGEN_COMPRESSOR,
            OxygenCompressorBlock::new, props -> props.strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops());

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
