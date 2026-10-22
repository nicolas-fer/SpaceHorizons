package com.spacehorizons.rocket;

import com.spacehorizons.content.ModBlockEntities;
import com.spacehorizons.content.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Combustível guardado num tanque de foguete, em mB (0 a {@link RocketSpecs#TANK_CAPACITY_MB}). Sincronizado com o
 * cliente para a tela mostrar a carga ao olhar para o bloco. O combustível vai junto no item ao quebrar
 * (componente {@code rocket_tank_mb}) e acompanha o foguete montado (a entidade guarda o total e o devolve aos tanques).
 */
public class RocketFuelTankBlockEntity extends BlockEntity {
    /** Chave do NBT; também é por ela que o retrato do foguete lê o combustível de cada tanque. */
    public static final String FUEL_KEY = "fuel_mb";

    private int fuelMb;

    public RocketFuelTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROCKET_FUEL_TANK.get(), pos, state);
    }

    public int fuelMb() {
        return this.fuelMb;
    }

    /** O combustível deste tanque (o do bloco: químico, hidrogênio ou xenônio; os três usam este block entity). */
    public com.spacehorizons.space.flight.Propellant propellant() {
        return this.getBlockState().getBlock() instanceof RocketFuelTankBlock tank ? tank.propellant() : com.spacehorizons.space.flight.Propellant.CHEMICAL;
    }

    /** Põe até {@code mb} no tanque e devolve quanto coube. */
    public int add(int mb) {
        int accepted = Math.max(0, Math.min(mb, RocketFuel.space(this.fuelMb)));
        if (accepted > 0) {
            this.setFuel(this.fuelMb + accepted);
        }
        return accepted;
    }

    public void setFuel(int mb) {
        int value = RocketFuel.clamp(mb);
        if (value != this.fuelMb) {
            this.fuelMb = value;
            this.setChanged();
            if (this.level != null && !this.level.isClientSide()) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.fuelMb = RocketFuel.clamp(input.getIntOr(FUEL_KEY, 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(FUEL_KEY, this.fuelMb);
    }

    /** O combustível vai para o item do bloco ao quebrar (loot com copy_components), para não se perder. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (this.fuelMb > 0) {
            components.set(ModDataComponents.ROCKET_TANK_MB.get(), this.fuelMb);
        }
    }

    /** Ao colocar um tanque que guardava combustível, ele volta ao tanque. */
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Integer stored = components.get(ModDataComponents.ROCKET_TANK_MB.get());
        if (stored != null) {
            this.fuelMb = RocketFuel.clamp(stored);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
