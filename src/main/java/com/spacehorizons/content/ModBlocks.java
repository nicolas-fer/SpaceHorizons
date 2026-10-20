package com.spacehorizons.content;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketCockpitBlock;
import com.spacehorizons.rocket.RocketFuelTankBlock;
import com.spacehorizons.survival.compressor.OxygenCompressorBlock;
import com.spacehorizons.survival.fuel.RefineryBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registro de todos os blocos do mod. Cada bloco registrado por {@link #registerWithItem} ganha o seu BlockItem.
 * As máquinas e peças do foguete com modelo próprio (elements, não cubo cheio) usam noOcclusion(): só tira o
 * culling das faces vizinhas (senão apareceriam buracos atrás das partes recuadas). A forma de colisão continua
 * o cubo cheio padrão.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SpaceHorizons.MODID);

    /** Bloco de aço: dureza de bloco de ferro; só dropa com picareta de ferro ou melhor (ver tags em data/minecraft/tags/block). */
    public static final DeferredBlock<Block> STEEL_BLOCK = registerWithItem(ContentIds.STEEL_BLOCK,
            props -> props.mapColor(MapColor.METAL).strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());

    /** Refinaria: precisa de picareta; acende (luz 13) enquanto queima combustível de fornalha. */
    public static final DeferredBlock<RefineryBlock> REFINERY = registerBlockWithItem(ContentIds.REFINERY, RefineryBlock::new,
            props -> props.strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(RefineryBlock.LIT) ? 13 : 0));

    /** Compressor de oxigênio: precisa de picareta; mesma dureza da refinaria. */
    public static final DeferredBlock<OxygenCompressorBlock> OXYGEN_COMPRESSOR = registerBlockWithItem(ContentIds.OXYGEN_COMPRESSOR,
            OxygenCompressorBlock::new, props -> props.strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());

    // --- Foguete de blocos (etapa 3a): todas as peças precisam de picareta; ver tags em data/spacehorizons/tags/block ---

    /** Plataforma de lançamento: o foguete se monta sobre ela e a deixa para trás. */
    public static final DeferredBlock<Block> LAUNCH_PAD = registerWithItem(ContentIds.LAUNCH_PAD,
            props -> props.mapColor(MapColor.COLOR_GRAY).strength(4.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .noOcclusion());

    /** Cockpit: assento do piloto e ponto de partida da montagem. */
    public static final DeferredBlock<RocketCockpitBlock> ROCKET_COCKPIT = registerBlockWithItem(ContentIds.ROCKET_COCKPIT,
            RocketCockpitBlock::new, props -> props.mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredBlock<Block> ROCKET_ENGINE = registerWithItem(ContentIds.ROCKET_ENGINE,
            props -> props.mapColor(MapColor.METAL).strength(4.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .noOcclusion());

    /** Tanque de combustível: block entity com os mB; o BlockItem próprio mostra a carga no tooltip. */
    public static final DeferredBlock<RocketFuelTankBlock> ROCKET_FUEL_TANK = BLOCKS.registerBlock(ContentIds.ROCKET_FUEL_TANK,
            RocketFuelTankBlock::new, props -> props.mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredBlock<Block> ROCKET_PARACHUTE = registerWithItem(ContentIds.ROCKET_PARACHUTE,
            props -> props.mapColor(MapColor.COLOR_RED).strength(2.0F, 3.0F).sound(SoundType.WOOL).requiresCorrectToolForDrops()
                    .noOcclusion());

    public static final DeferredBlock<Block> ROCKET_LANDING_LEG = registerWithItem(ContentIds.ROCKET_LANDING_LEG,
            props -> props.mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .noOcclusion());

    /** Casco de aço: a estrutura do foguete. */
    public static final DeferredBlock<Block> ROCKET_HULL = registerWithItem(ContentIds.ROCKET_HULL,
            props -> props.mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());

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
