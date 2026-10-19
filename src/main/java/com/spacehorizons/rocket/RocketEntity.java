package com.spacehorizons.rocket;

import java.util.List;

import com.mojang.serialization.Codec;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.space.flight.RocketStats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * O foguete montado: os blocos viram esta entidade (o retrato) e o piloto senta nela. Servidor autoritativo: o piloto é o
 * passageiro de controle, mas a posição não vem do cliente ({@link #isClientAuthoritative}); o cliente só interpola.
 *
 * A posição da entidade é o CENTRO da caixa do retrato (o pivô de giro); a caixa de colisão/pick é alinhada aos eixos e
 * não gira. A atitude (quaternion local da dimensão, identidade pousado) é um dado sincronizado, junto com acelerador,
 * combustível, modo de voo, modo do SAS e aquecimento. O retrato vai no pacote de spawn ({@link IEntityWithComplexSpawn})
 * e a entidade é salva inteira, porque a troca de dimensão a recria a partir do NBT.
 *
 * O voo (fase B1) roda no servidor a cada tick, em {@link RocketFlight#tick}: ele move a entidade, sincroniza os dados e
 * troca de dimensão com o piloto.
 */
public class RocketEntity extends Entity implements IEntityWithComplexSpawn {
    private static final EntityDataAccessor<Quaternionfc> ATTITUDE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Float> THROTTLE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FUEL_MB = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> FLIGHT_MODE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> SAS_MODE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> HEAT = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.FLOAT);
    /** O servidor está segurando o passo do voo (espera de chunks): o cliente não interpola nem prevê movimento além da posição real. */
    private static final EntityDataAccessor<Boolean> HELD = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BOOLEAN);

    /** Pouso com a inclinação máxima (graus) para voltar a blocos; acima disso a desmontagem é recusada. */
    private static final double MAX_TILT_TO_LAND_DEGREES = 15.0;

    private RocketPortrait portrait = RocketPortrait.EMPTY;
    private RocketStats stats = RocketSpecs.statsFor(RocketCensus.EMPTY);
    private final RocketFlight flight = new RocketFlight();
    /** Combustível em mB com fração (o consumo do voo é fracionário); o dado sincronizado é só o valor arredondado para baixo. */
    private double fuelMb;
    /** Atitude no servidor (a fonte da verdade) e a interpolação dela no cliente (tick anterior, atual e alvo sincronizado). */
    private final Quaternionf attitude = new Quaternionf();
    private final Quaternionf attitudePrev = new Quaternionf();
    private final Quaternionf attitudeCur = new Quaternionf();
    private final Quaternionf attitudeTarget = new Quaternionf();
    private boolean attitudeSeeded;

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
    }

    // ---------------------------------------------------------------- dados sincronizados

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ATTITUDE, new Quaternionf());
        builder.define(THROTTLE, 0.0F);
        builder.define(FUEL_MB, 0.0F);
        builder.define(FLIGHT_MODE, FlightMode.LANDED.id());
        builder.define(SAS_MODE, (byte) 0);
        builder.define(HEAT, 0.0F);
        builder.define(HELD, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (ATTITUDE.equals(accessor)) {
            this.attitudeTarget.set(this.entityData.get(ATTITUDE));
            if (!this.attitudeSeeded) {
                // primeiro valor (spawn): sem varredura a partir da identidade
                this.attitudeSeeded = true;
                this.attitudePrev.set(this.attitudeTarget);
                this.attitudeCur.set(this.attitudeTarget);
            }
        }
    }

    // ---------------------------------------------------------------- API do contrato

    /** Estado de voo e controle no servidor. */
    public RocketFlight flight() {
        return this.flight;
    }

    /** O retrato (os blocos); {@link RocketPortrait#EMPTY} até o spawn complexo chegar ao cliente. */
    public RocketPortrait portrait() {
        return this.portrait;
    }

    /** Massa, empuxo, capacidade de combustível e contagens, calculadas a partir do retrato. */
    public RocketStats stats() {
        return this.stats;
    }

    /** Guarda o último comando do piloto (o voo o lê a cada tick do servidor). */
    public void applyControl(RocketControlPayload payload) {
        this.flight.applyControl(payload, this.level().getGameTime());
    }

    /** Troca o retrato (spawn, carga do NBT ou montagem): recalcula estatísticas e a caixa de colisão. */
    public void setPortrait(RocketPortrait portrait) {
        this.portrait = portrait;
        this.stats = portrait.stats();
        this.refreshDimensions();
    }

    public double fuelMb() {
        return this.fuelMb;
    }

    /** Combustível em mB, limitado à capacidade dos tanques; sincroniza o valor arredondado para baixo. */
    public void setFuelMb(double mb) {
        this.fuelMb = Math.max(0.0, Math.min(this.stats.fuelCapacityMb(), mb));
        this.entityData.set(FUEL_MB, (float) Math.floor(this.fuelMb));
    }

    /** Combustível como o cliente enxerga (sincronizado). */
    public float syncedFuelMb() {
        return this.entityData.get(FUEL_MB);
    }

    public float throttle() {
        return this.entityData.get(THROTTLE);
    }

    public void setThrottle(float throttle) {
        this.entityData.set(THROTTLE, Math.max(0.0F, Math.min(1.0F, throttle)));
    }

    public FlightMode flightMode() {
        return FlightMode.byId(this.entityData.get(FLIGHT_MODE));
    }

    public void setFlightMode(FlightMode mode) {
        this.flight.setMode(mode);
        this.entityData.set(FLIGHT_MODE, mode.id());
    }

    public byte sasMode() {
        return this.entityData.get(SAS_MODE);
    }

    /** Sincroniza o modo de voo, o modo do SAS e o aquecimento (servidor). */
    void syncFlightData(FlightMode mode, byte sasMode, float heat) {
        this.entityData.set(FLIGHT_MODE, mode.id());
        this.entityData.set(SAS_MODE, sasMode);
        this.entityData.set(HEAT, heat);
    }

    public float heat() {
        return this.entityData.get(HEAT);
    }

    /** Servidor: marca (ou solta) o passo segurado. O dado só é enviado quando o valor muda. */
    void setHeld(boolean held) {
        this.entityData.set(HELD, held);
    }

    /** O passo do voo está segurado esperando chunks (no cliente, o valor sincronizado). */
    public boolean isHeld() {
        return this.entityData.get(HELD);
    }

    /** Define a atitude (servidor): guarda e sincroniza uma cópia (o dado só marca "sujo" se o valor mudar). */
    public void setAttitude(Quaternionfc q) {
        this.attitude.set(q).normalize();
        this.entityData.set(ATTITUDE, new Quaternionf(this.attitude));
    }

    /** A atitude do servidor (no cliente, a sincronizada). */
    public Quaternionfc attitude() {
        return this.level().isClientSide() ? this.attitudeTarget : this.attitude;
    }

    // ---------------------------------------------------------------- render (cliente)

    /** Atitude interpolada para o frame (entre o tick anterior e o atual). */
    public void orientation(float partialTick, Quaternionf dest) {
        if (this.level().isClientSide()) {
            this.attitudePrev.slerp(this.attitudeCur, partialTick, dest);
        } else {
            dest.set(this.attitude);
        }
    }

    /** O ponto do retrato (coordenadas locais) que fica na posição da entidade: o centro da caixa. */
    public Vector3f pivotLocal() {
        return new Vector3f(this.portrait.sizeX / 2.0F, this.portrait.sizeY / 2.0F, this.portrait.sizeZ / 2.0F);
    }

    /** A caixa é o centro de giro, não o "olho" da hitbox: a luz do mundo é amostrada no centro. */
    @Override
    public Vec3 getLightProbePosition(float partialTickTime) {
        return this.getPosition(partialTickTime);
    }

    // ---------------------------------------------------------------- caixa e posição

    /** A caixa do retrato (alinhada aos eixos; a hitbox não gira com o foguete). Antes do retrato: a caixa padrão do tipo. */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        RocketPortrait p = this.portrait;
        if (p == null || p.isEmpty()) {
            return super.getDimensions(pose);
        }
        return EntityDimensions.fixed(Math.max(p.sizeX, p.sizeZ), p.sizeY);
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        RocketPortrait p = this.portrait;
        if (p == null || p.isEmpty()) {
            return super.makeBoundingBox(position);
        }
        double hx = p.sizeX / 2.0;
        double hy = p.sizeY / 2.0;
        double hz = p.sizeZ / 2.0;
        return new AABB(position.x - hx, position.y - hy, position.z - hz, position.x + hx, position.y + hy, position.z + hz);
    }

    /** O centro do bloco do cockpit no mundo, com a atitude atual aplicada em torno do centro da caixa. */
    public Vec3 cockpitWorldPosition() {
        Vector3f local = new Vector3f(this.portrait.cockpit().getX() + 0.5F, this.portrait.cockpit().getY() + 0.5F,
                this.portrait.cockpit().getZ() + 0.5F).sub(this.pivotLocal());
        if (this.level().isClientSide()) {
            this.attitudeCur.transform(local);
        } else {
            this.attitude.transform(local);
        }
        return this.position().add(local.x(), local.y(), local.z());
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.attitudePrev.set(this.attitudeCur);
            this.attitudeCur.set(this.attitudeTarget);
            if (this.isHeld()) {
                freezeAtTarget();
            }
        } else {
            this.flight.tick(this);
            this.entityData.set(FLIGHT_MODE, this.flight.mode().id());
        }
    }

    /**
     * Segurado pelo servidor (cliente): a posição REAL é a última recebida. A interpolação de 2 ticks ainda estaria andando
     * em direção a ela, e a velocidade sincronizada do último passo (90 blocos por tick) a faria seguir adiante; então pula
     * para o alvo, zera a velocidade e para de interpolar. Quando o servidor volta a andar, a interpolação recomeça do ponto real.
     */
    private void freezeAtTarget() {
        var handler = this.getInterpolation();
        var target = handler.target();
        if (target != null) {
            this.setPos(target.position());
            handler.cancel();
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.attitudePrev.set(this.attitudeCur);
    }

    // ---------------------------------------------------------------- passageiro

    /** O piloto é o jogador sentado no cockpit. */
    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    /** O piloto fica no cockpit (girado pela atitude); o ponto de assento é o do bloco do cockpit menos a altura do quadril. */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        Vec3 at = this.passengerPositionAt(this.position(), this.level().isClientSide() ? this.attitudeCur : this.attitude, passenger);
        moveFunction.accept(passenger, at.x, at.y, at.z);
    }

    /** Onde o passageiro fica (posição da entidade dele) com o foguete em {@code center} e a atitude local {@code attitude}. */
    public Vec3 passengerPositionAt(Vec3 center, Quaternionfc attitude, Entity passenger) {
        Vector3f local = new Vector3f(this.portrait.cockpit().getX() + 0.5F, this.portrait.cockpit().getY() + 0.5F,
                this.portrait.cockpit().getZ() + 0.5F).sub(this.pivotLocal());
        attitude.transform(local);
        Vec3 seat = center.add(local.x(), local.y() - 0.2, local.z());
        Vec3 offset = passenger.getVehicleAttachmentPoint(this);
        return new Vec3(seat.x - offset.x, seat.y - offset.y, seat.z - offset.z);
    }

    /** Pousado: em pé no chão ao lado do foguete. Em voo (inclusive na troca de dimensão), onde o passageiro já está. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (this.flight.mode() != FlightMode.LANDED) {
            return passenger.position();
        }
        Vec3 spot = RocketDismount.find(this.level(), this.getBoundingBox(), passenger.getType());
        return spot != null ? spot : passenger.position();
    }

    // ---------------------------------------------------------------- autoridade e física vanilla

    /** Servidor autoritativo: o cliente do piloto não manda a posição do veículo (o servidor validaria e recusaria saltos grandes). */
    @Override
    public boolean isClientAuthoritative() {
        return false;
    }

    @Override
    protected boolean isLocalClientAuthoritative() {
        return false;
    }

    /** O servidor ticka o foguete mesmo longe de jogadores e em chunks que não estão em ticking de entidades do jogador. */
    @Override
    public boolean isAlwaysTicking() {
        return true;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    /** Sem isto o servidor expulsa o piloto por "voar" (a checagem vanilla de veículo flutuante). */
    @Override
    public boolean isFlyingVehicle() {
        return true;
    }

    /** O vanilla devolveria o último movimento que o CLIENTE reportou (velho e zero), não o do servidor. */
    @Override
    public Vec3 getKnownMovement() {
        return this.getDeltaMovement();
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 2);
    }

    /** Clicável (sentar) mas sem colisão de entidade: o jogador vai sentado e nada empurra o foguete. */
    @Override
    public boolean isPickable() {
        return !this.isRemoved();
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

    // ---------------------------------------------------------------- interação

    /**
     * Clicar senta o piloto. Shift + clicar com a mão vazia, com o foguete pousado e vazio, desmonta: os blocos voltam
     * ao lugar (o único jeito de desfazer a montagem em sobrevivência antes do comando de desmontar do piloto, 3b).
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSecondaryUseActive() && !this.isVehicle() && player.getMainHandItem().isEmpty()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                try {
                    RocketLanding.land(serverLevel, this);
                } catch (RocketException e) {
                    player.sendSystemMessage(e.component());
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide() && !player.startRiding(this)) {
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- inclinação (pouso)

    /** Inclinação do foguete em relação à vertical, em graus. */
    public double tiltDegrees() {
        return RocketAttitude.tiltDegrees(this.attitude);
    }

    /** O quanto o foguete girou em torno da vertical, em graus horários vistos de cima (o mesmo sentido do yaw do Minecraft). */
    public double yawDegrees() {
        return RocketAttitude.yawDegrees(this.attitude);
    }

    /** Pode voltar a blocos: pousado e quase em pé. */
    public boolean canLand() {
        return this.flight.mode() == FlightMode.LANDED && this.tiltDegrees() <= MAX_TILT_TO_LAND_DEGREES;
    }

    // ---------------------------------------------------------------- spawn e salvar

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        RocketPortrait.write(buffer, this.portrait);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        this.setPortrait(RocketPortrait.read(buffer));
    }

    private static final Codec<List<Float>> QUATERNION_CODEC = Codec.FLOAT.listOf(4, 4);

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("Portrait", CompoundTag.CODEC, this.portrait.save());
        output.putDouble("FuelMb", this.fuelMb);
        output.putFloat("Throttle", this.throttle());
        output.putByte("FlightMode", this.flightMode().id());
        output.putByte("SasMode", this.sasMode());
        output.putFloat("Heat", this.heat());
        output.store("Attitude", QUATERNION_CODEC, List.of(this.attitude.x, this.attitude.y, this.attitude.z, this.attitude.w));
        this.flight.save(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.read("Portrait", CompoundTag.CODEC).ifPresent(tag -> {
            try {
                this.setPortrait(RocketPortrait.load(input.lookup(), tag));
            } catch (IllegalArgumentException e) {
                SpaceHorizons.LOGGER.error("Retrato do foguete corrompido; a entidade fica sem blocos", e);
            }
        });
        this.setFuelMb(input.getDoubleOr("FuelMb", 0.0));
        this.setThrottle(input.getFloatOr("Throttle", 0.0F));
        this.setFlightMode(FlightMode.byId(input.getByteOr("FlightMode", FlightMode.LANDED.id())));
        this.entityData.set(SAS_MODE, input.getByteOr("SasMode", (byte) 0));
        this.entityData.set(HEAT, input.getFloatOr("Heat", 0.0F));
        input.read("Attitude", QUATERNION_CODEC).ifPresent(q -> this.setAttitude(new Quaternionf(q.get(0), q.get(1), q.get(2), q.get(3))));
        this.flight.load(input);
    }
}
