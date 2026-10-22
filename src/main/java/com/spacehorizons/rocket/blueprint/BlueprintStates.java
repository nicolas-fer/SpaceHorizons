package com.spacehorizons.rocket.blueprint;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spacehorizons.content.connected.ConnectedFaces;
import com.spacehorizons.rocket.EngineForm;
import com.spacehorizons.rocket.RocketCockpitBlock;
import com.spacehorizons.rocket.RocketEngineBlock;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

/**
 * Estados de bloco da planta: a normalização (o que a planta compara), a string que ela guarda e a volta da string para o estado.
 *
 * <p><b>Normalizado</b>: as propriedades DERIVADAS voltam ao padrão do bloco, porque o jogo as recalcula sozinho e elas não dizem nada do
 * projeto: o papel do motor na estrutura formada ({@code form}, de qualquer bloco com propriedade {@link EngineForm}) e o giro do modelo do
 * mestre ({@code facing} do motor), as seis conexões das texturas conectadas ({@link ConnectedFaces}), a direção do cockpit (o assento pode
 * olhar para qualquer lado sem mudar o foguete) e {@code waterlogged}. Todo o resto (escadas, lajes, blocos comuns) compara exato.
 */
public final class BlueprintStates {
    private static final Map<String, Optional<BlockState>> RESOLVED = new ConcurrentHashMap<>();
    private static final Map<Blueprint, BlockState[]> PALETTES = new WeakHashMap<>();

    private BlueprintStates() {
    }

    /** O estado com as propriedades derivadas no padrão do bloco. */
    public static BlockState normalize(BlockState state) {
        BlockState base = state.getBlock().defaultBlockState();
        BlockState out = state;
        for (Property<?> property : state.getProperties()) {
            if (derived(state, property)) {
                out = copy(out, base, property);
            }
        }
        return out;
    }

    private static boolean derived(BlockState state, Property<?> property) {
        if (property.getValueClass() == EngineForm.class || property == BlockStateProperties.WATERLOGGED) {
            return true;
        }
        if (property == ConnectedFaces.UP || property == ConnectedFaces.DOWN || property == ConnectedFaces.NORTH || property == ConnectedFaces.SOUTH
                || property == ConnectedFaces.EAST || property == ConnectedFaces.WEST) {
            // só nos blocos de textura conectada (o tanque e o vidro da nave); cercas e muros usam as mesmas propriedades por outro motivo
            return state.hasProperty(ConnectedFaces.UP) && state.hasProperty(ConnectedFaces.DOWN);
        }
        return property == HorizontalDirectionalBlock.FACING
                && (state.getBlock() instanceof RocketEngineBlock || state.getBlock() instanceof RocketCockpitBlock);
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState into, BlockState from, Property<T> property) {
        return into.setValue(property, from.getValue(property));
    }

    /** A string que a planta guarda: {@code namespace:bloco[prop=valor,...]} do estado normalizado. */
    public static String serialize(BlockState state) {
        return BlockStateParser.serialize(normalize(state));
    }

    /** O estado normalizado de uma string da planta, ou null se o bloco não existe neste jogo (guardado em cache). */
    public static @Nullable BlockState resolve(String state) {
        return RESOLVED.computeIfAbsent(state, s -> {
            try {
                return Optional.of(normalize(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState()));
            } catch (CommandSyntaxException | RuntimeException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** A paleta da planta já resolvida (um estado normalizado por entrada da paleta; null nos desconhecidos), guardada por planta. */
    public static BlockState[] palette(Blueprint blueprint) {
        synchronized (PALETTES) {
            BlockState[] states = PALETTES.get(blueprint);
            if (states == null) {
                states = new BlockState[blueprint.palette().size()];
                for (int i = 0; i < states.length; i++) {
                    states[i] = resolve(blueprint.palette().get(i));
                }
                PALETTES.put(blueprint, states);
            }
            return states;
        }
    }
}
