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
 * O que um bloco de motor do Tier 2 guarda dentro dele ({@link ChargedEngineBlock}): no motor nuclear, a vida útil do núcleo (mB de hidrogênio que a
 * barra carregada ainda aquece, até {@link EngineType#ROD_CORE_MB}); no de fusão, o hélio-3 (até {@link EngineType#FUSION_CHARGE_PER_BLOCK_MB}).
 * Vai junto no item ao quebrar (componente {@code engine_charge_mb}), no retrato do foguete montado (o NBT do block entity) e volta ao pousar,
 * como o combustível dos tanques.
 */
public class EngineChargeBlockEntity extends BlockEntity {
    /** Chave do NBT; também é por ela que o retrato lê a carga de cada motor. */
    public static final String CHARGE_KEY = "charge_mb";

    private int chargeMb;

    public EngineChargeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENGINE_CHARGE.get(), pos, state);
    }

    /** O máximo deste bloco (pelo tipo do motor). */
    public int capacityMb() {
        return capacityOf(this.getBlockState());
    }

    public static int capacityOf(BlockState state) {
        return state.getBlock() instanceof ChargedEngineBlock engine ? engine.type().chargePerBlockMb() : 0;
    }

    public int chargeMb() {
        return this.chargeMb;
    }

    public void setCharge(int mb) {
        int value = Math.max(0, Math.min(capacityMb(), mb));
        if (value != this.chargeMb) {
            this.chargeMb = value;
            this.setChanged();
            if (this.level != null && !this.level.isClientSide()) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.chargeMb = Math.max(0, input.getIntOr(CHARGE_KEY, 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(CHARGE_KEY, this.chargeMb);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (this.chargeMb > 0) {
            components.set(ModDataComponents.ENGINE_CHARGE_MB.get(), this.chargeMb);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Integer stored = components.get(ModDataComponents.ENGINE_CHARGE_MB.get());
        if (stored != null) {
            this.chargeMb = Math.max(0, stored);
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
