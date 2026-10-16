package com.spacehorizons.rocket;

import com.spacehorizons.content.ModBlockEntities;
import com.spacehorizons.survival.fuel.FuelCanisterItem;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Tanque de combustível do foguete: guarda até {@link RocketSpecs#TANK_CAPACITY_MB} mB no block entity. Clicar com um
 * galão que tenha combustível despeja o que couber no tanque; com a mão vazia (ou um galão vazio) mostra a carga na barra
 * de ação. O tooltip do item e a tela ao olhar para o bloco ({@code RocketTankHud}) também mostram a carga.
 */
public class RocketFuelTankBlock extends BaseEntityBlock {
    public RocketFuelTankBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RocketFuelTankBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof FuelCanisterItem) || FuelCanisterItem.getFuel(stack) <= 0) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity tank) {
            int fuel = FuelCanisterItem.getFuel(stack);
            int moved = tank.add(fuel);
            if (moved > 0) {
                FuelCanisterItem.setFuel(stack, fuel - moved);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 1.2F);
            }
            player.sendOverlayMessage(levelMessage(tank));
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity tank) {
            player.sendOverlayMessage(levelMessage(tank));
        }
        return InteractionResult.SUCCESS;
    }

    /** "Tanque de combustível: 1000 / 4000 mB". */
    public static Component levelMessage(RocketFuelTankBlockEntity tank) {
        return Component.translatable("message.spacehorizons.rocket.tank_level", tank.fuelMb(), RocketSpecs.TANK_CAPACITY_MB);
    }

    /** Tipo do block entity, para o ticker futuro e para o teste de modelo. */
    public static boolean isTank(BlockEntity entity) {
        return entity.getType() == ModBlockEntities.ROCKET_FUEL_TANK.get();
    }
}
