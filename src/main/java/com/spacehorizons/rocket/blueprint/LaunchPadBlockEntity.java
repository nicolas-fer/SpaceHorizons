package com.spacehorizons.rocket.blueprint;

import com.spacehorizons.content.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
 * A planta ativa numa plataforma de lançamento (docs/PLANO_TIERS.md, seção 4). A plataforma em que a planta foi posta é a âncora (0, 0, 0)
 * dela. Só o servidor muda a planta ({@link #setBlueprint}); o cliente a recebe pelo pacote de block entity (quem vê o chunk recebe; quem
 * chega depois recebe no carregamento do chunk), então todos os jogadores veem o mesmo holograma.
 */
public class LaunchPadBlockEntity extends BlockEntity {
    public static final String KEY = "blueprint";

    private @Nullable Blueprint blueprint;

    public LaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAUNCH_PAD.get(), pos, state);
    }

    public @Nullable Blueprint blueprint() {
        return this.blueprint;
    }

    /** Põe (ou tira, com null) a planta e avisa os clientes. Só no servidor. */
    public void setBlueprint(@Nullable Blueprint blueprint) {
        this.blueprint = blueprint;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.blueprint = input.read(KEY, Blueprint.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable(KEY, Blueprint.CODEC, this.blueprint);
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
