package com.spacehorizons.rocket;

import com.spacehorizons.content.ModBlockEntities;
import com.spacehorizons.survival.fuel.FuelCanisterItem;

import com.spacehorizons.content.connected.ConnectedFaces;
import com.spacehorizons.space.flight.Propellant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
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
    /** Tanques ligados formam uma coluna sem emenda (textura conectada); a estrutura formada é decidida por {@link Multiblocks}. */
    private static final ConnectedFaces.Rule RULE = ConnectedFaces.sameBlock();

    private final Propellant propellant;

    public RocketFuelTankBlock(BlockBehaviour.Properties properties) {
        this(properties, Propellant.CHEMICAL);
    }

    /**
     * Tanque de outro combustível (Tier 2: {@code hydrogen_tank}, {@code xenon_tank}): o mesmo bloco, a mesma regra de coluna e as mesmas texturas
     * conectadas; só aceita o galão do seu combustível e só forma coluna com tanques iguais a ele.
     */
    public RocketFuelTankBlock(BlockBehaviour.Properties properties, Propellant propellant) {
        super(properties);
        this.propellant = propellant;
        registerDefaultState(ConnectedFaces.disconnected(stateDefinition.any()));
    }

    /** O combustível que este tanque guarda. */
    public Propellant propellant() {
        return this.propellant;
    }

    /** As conexões deste tanque, para quem escreve blocos por código ({@link ConnectedFaces#connectAround}). */
    public static ConnectedFaces.Rule rule() {
        return RULE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        ConnectedFaces.addProperties(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return ConnectedFaces.connect(defaultBlockState(), context.getLevel(), context.getClickedPos(), RULE);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return ConnectedFaces.update(state, directionToNeighbour, neighbourState, RULE);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return ConnectedFaces.rotate(state, rotation);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RocketFuelTankBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof FuelCanisterItem canister) || FuelCanisterItem.getFuel(stack) <= 0) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (canister.propellant() != this.propellant) {
            // galão de outro combustível: avisa e não despeja
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.spacehorizons.rocket.wrong_fuel",
                        Component.translatable("propellant.spacehorizons." + canister.propellant().key()),
                        Component.translatable("propellant.spacehorizons." + this.propellant.key())));
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity) {
            // o combustível é da ESTRUTURA: o galão enche qualquer bloco dela e a carga se reparte por igual entre os blocos
            TankStructures.Found structure = TankStructures.at(level, pos);
            int fuel = FuelCanisterItem.getFuel(stack);
            int moved = Math.max(0, Math.min(fuel, structure.capacityMb() - structure.fuelMb()));
            if (moved > 0) {
                TankStructures.setTotal(level, structure, structure.fuelMb() + moved);
                FuelCanisterItem.setFuel(stack, fuel - moved);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 1.2F);
            }
            player.sendOverlayMessage(levelMessage(level, pos));
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity) {
            player.sendOverlayMessage(levelMessage(level, pos));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * O que a tela mostra para este tanque: "Tanque de combustível: 1000 / 4000 mB" se avulso, ou a carga da estrutura formada
     * ("Estrutura de tanque 2x2x8: 12000 / 128000 mB"). Serve ao servidor e ao cliente.
     */
    public static Component levelMessage(BlockGetter level, BlockPos pos) {
        TankStructures.Found structure = TankStructures.at(level, pos);
        Propellant propellant = level.getBlockState(pos).getBlock() instanceof RocketFuelTankBlock tank ? tank.propellant() : Propellant.CHEMICAL;
        if (propellant != Propellant.CHEMICAL) {
            Component name = Component.translatable("propellant.spacehorizons." + propellant.key());
            if (structure.formed()) {
                var box = structure.box();
                return Component.translatable("message.spacehorizons.rocket.typed_tank_structure_level", name, box.w(), box.d(), box.h(),
                        structure.fuelMb(), structure.capacityMb());
            }
            return Component.translatable("message.spacehorizons.rocket.typed_tank_level", name, structure.fuelMb(), RocketSpecs.TANK_CAPACITY_MB);
        }
        if (structure.formed()) {
            var box = structure.box();
            return Component.translatable("message.spacehorizons.rocket.tank_structure_level", box.w(), box.d(), box.h(), structure.fuelMb(),
                    structure.capacityMb());
        }
        return Component.translatable("message.spacehorizons.rocket.tank_level", structure.fuelMb(), RocketSpecs.TANK_CAPACITY_MB);
    }

    /** Tipo do block entity, para o ticker futuro e para o teste de modelo. */
    public static boolean isTank(BlockEntity entity) {
        return entity.getType() == ModBlockEntities.ROCKET_FUEL_TANK.get();
    }
}
