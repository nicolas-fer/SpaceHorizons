package com.spacehorizons.rocket;

import com.spacehorizons.content.ModItems;
import com.spacehorizons.space.flight.Propellant;
import com.spacehorizons.survival.fuel.FuelCanisterItem;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Motor do Tier 2 que guarda carga dentro dele ({@link EngineChargeBlockEntity}): o nuclear (o núcleo, carregado com barras
 * {@code nuclear_fuel_rod}: cada barra vai num bloco vazio do motor e aquece {@link EngineType#ROD_CORE_MB} mB de hidrogênio) e o de fusão (o
 * hélio-3, despejado com o galão de He-3: {@link EngineType#FUSION_CHARGE_PER_BLOCK_MB} mB por bloco). Forma 2x2x2 e 3x3x3 como o motor químico
 * ({@link RocketEngineBlock}); a carga é do CONJUNTO de motores iguais ligados: o clique em qualquer bloco carrega o primeiro que tiver espaço.
 */
public class ChargedEngineBlock extends RocketEngineBlock implements EntityBlock {
    private final EngineType type;

    public ChargedEngineBlock(BlockBehaviour.Properties properties, EngineType type) {
        super(properties);
        this.type = type;
    }

    public EngineType type() {
        return this.type;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EngineChargeBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        boolean rod = this.type == EngineType.NUCLEAR && stack.is(ModItems.NUCLEAR_FUEL_ROD.get());
        boolean helium = this.type == EngineType.FUSION && FuelCanisterItem.propellantOf(stack) == Propellant.HELIUM3
                && FuelCanisterItem.getFuel(stack) > 0;
        if (!rod && !helium) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        LongArrayList cluster = EngineForming.cluster(level, pos, this);
        if (rod) {
            // uma barra inteira num bloco sem núcleo (uma barra gasta pela metade não recebe outra por cima)
            for (long cell : cluster) {
                if (level.getBlockEntity(BlockPos.of(cell)) instanceof EngineChargeBlockEntity be && be.chargeMb() == 0) {
                    be.setCharge(EngineType.ROD_CORE_MB);
                    stack.consume(1, player);
                    level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.8F);
                    break;
                }
            }
        } else {
            int fuel = FuelCanisterItem.getFuel(stack);
            for (long cell : cluster) {
                if (fuel <= 0) {
                    break;
                }
                if (level.getBlockEntity(BlockPos.of(cell)) instanceof EngineChargeBlockEntity be) {
                    int moved = Math.min(fuel, be.capacityMb() - be.chargeMb());
                    if (moved > 0) {
                        be.setCharge(be.chargeMb() + moved);
                        fuel -= moved;
                    }
                }
            }
            if (fuel != FuelCanisterItem.getFuel(stack)) {
                FuelCanisterItem.setFuel(stack, fuel);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 1.4F);
            }
        }
        player.sendOverlayMessage(chargeMessage(level, pos));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(chargeMessage(level, pos));
        }
        return InteractionResult.SUCCESS;
    }

    /** "Núcleo do motor nuclear: X / Y mB de hidrogênio" ou "Hélio-3 do motor de fusão: X / Y mB", somando o conjunto de motores ligados. */
    public static Component chargeMessage(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChargedEngineBlock engine)) {
            return Component.empty();
        }
        int charge = 0;
        int capacity = 0;
        for (long cell : EngineForming.cluster(level, pos, engine)) {
            if (level.getBlockEntity(BlockPos.of(cell)) instanceof EngineChargeBlockEntity be) {
                charge += be.chargeMb();
                capacity += be.capacityMb();
            }
        }
        return Component.translatable("message.spacehorizons.rocket.engine_charge." + engine.type().key(), charge, capacity);
    }
}
