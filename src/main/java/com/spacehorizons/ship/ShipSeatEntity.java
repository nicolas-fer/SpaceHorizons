package com.spacehorizons.ship;

import java.util.List;

import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModEntities;
import com.spacehorizons.ship.block.ShipSeatBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * O assento do piloto como entidade (docs/PLANO_NAVES.md, 3.2: "uma entidade invisível por assento, igual aos seats do Create"): uma entidade
 * minúscula, parada sobre o bloco {@link ShipSeatBlock}, na altura do assento, que carrega um passageiro. Nada nela se move: a nave inteira está
 * parada no estaleiro e o jogador sentado fica onde sentou, sem teletransporte. Não é salva (o jogador sai do assento ao salvar e fechar o mundo) e some
 * sozinha quando o bloco some ou o assento fica vazio.
 */
public class ShipSeatEntity extends Entity {
    public ShipSeatEntity(EntityType<? extends ShipSeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Senta o jogador no assento do bloco {@code pos}; avisa se está ocupado. Devolve se sentou. */
    public static boolean sit(ServerLevel level, BlockPos pos, Player player) {
        if (!level.getBlockState(pos).is(ModBlocks.SHIP_SEAT.get())) {
            return false;
        }
        List<ShipSeatEntity> here = level.getEntitiesOfClass(ShipSeatEntity.class, new AABB(pos));
        for (ShipSeatEntity seat : here) {
            if (seat.isVehicle() && !seat.hasPassenger(player)) {
                player.sendOverlayMessage(Component.translatable("message.spacehorizons.ship.seat_taken"));
                return false;
            }
        }
        ShipSeatEntity seat = here.isEmpty() ? null : here.get(0);
        if (seat == null) {
            seat = new ShipSeatEntity(ModEntities.SHIP_SEAT.get(), level);
            seat.setPos(pos.getX() + 0.5, pos.getY() + ShipSeatBlock.SEAT_HEIGHT, pos.getZ() + 0.5);
            level.addFreshEntity(seat);
        }
        return player.startRiding(seat);
    }

    // ---------------------------------------------------------------- dados e salvar

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        // o bloco sumiu (quebrado) ou ninguém está sentado: a entidade some e solta quem estiver nela
        if (this.tickCount % 5 == 0) {
            boolean blockGone = !this.level().getBlockState(this.blockPosition()).is(ModBlocks.SHIP_SEAT.get())
                    && !this.level().getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.2, this.getZ())).is(ModBlocks.SHIP_SEAT.get());
            boolean empty = !this.isVehicle() && this.tickCount > 20;
            if (blockGone || empty) {
                this.ejectPassengers();
                this.discard();
            }
        }
    }

    // ---------------------------------------------------------------- passageiro

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    /** Ao levantar: em pé no chão livre mais perto do assento (a nave é um mundo normal, o chão está ali). */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (this.level() instanceof ServerLevel level) {
            return ShipSpots.standNear(level, this.blockPosition(), 2);
        }
        return passenger.position();
    }

    // ---------------------------------------------------------------- física vanilla

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }
}
