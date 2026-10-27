package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

import com.mojang.serialization.Codec;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.space.flight.FuelLoad;
import com.spacehorizons.space.flight.Propellant;
import com.spacehorizons.space.flight.RocketStats;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;
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
    /**
     * Na dimensão de espaço o foguete fica ESTACIONADO num ponto fixo ({@link SpaceParking}) e a posição real do voo vive só no estado de
     * voo; este dado é a posição real (coordenadas da dimensão de espaço, em float: o céu é desenhado dali) e o outro diz se vale.
     */
    private static final EntityDataAccessor<Boolean> VIRTUAL = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3fc> VIRTUAL_POS = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.VECTOR3);
    /**
     * Convés andável (S5): o tick do servidor em que a pose (posição e atitude) deste momento vale, enviado a cada tick enquanto há tripulação a pé
     * a bordo (zero sem tripulação). O cliente de um tripulante usa isto para desenhar a nave numa linha do tempo de ticks do servidor, sem os
     * trancos da chegada dos pacotes ({@link com.spacehorizons.ship.deck.DeckPoseTimeline}). Definido DEPOIS da atitude, para chegar depois dela.
     */
    private static final EntityDataAccessor<Integer> POSE_STAMP = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    /**
     * Os assentos dos passageiros (fase 2e): "uuid=posição local do assento (asLong)" separados por ";". Quem não consta senta na ponte (o cockpit).
     * O cliente precisa disto para posicionar cada passageiro ({@link #positionRider}).
     */
    private static final EntityDataAccessor<String> SEATS = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.STRING);
    /** Salto de posição (blocos) num tick de cliente acima do qual é uma costura do planeta ou um teleporte, não um passo de voo. */
    private static final double JUMP_DISTANCE = 2_000.0;

    /** Pouso com a inclinação máxima (graus) para voltar a blocos; acima disso a desmontagem é recusada. */
    private static final double MAX_TILT_TO_LAND_DEGREES = 15.0;

    private RocketPortrait portrait = RocketPortrait.EMPTY;
    /** Cliente: deltas do retrato que chegaram antes do retrato que eles mudam (o retrato grande chega em partes); aplicados quando ele chega. */
    private final List<com.spacehorizons.ship.deck.PortraitDeltaPayload> deferredDeltas = new ArrayList<>();
    /** Quem está em qual assento (posição local, asLong); servidor é a fonte, o cliente lê do dado sincronizado. */
    private final Map<UUID, Long> seatByRider = new HashMap<>();
    /** Cliente: avisa que um retrato foi trocado por outro (o cache de malha esquece o antigo). Ligado pelo renderer. */
    public static java.util.function.BiConsumer<UUID, UUID> portraitReplaced = (oldId, newId) -> {
    };
    /** Cliente: o retrato é grande e chega em partes ({@link PortraitParts}); este é o id que se espera, ou null. */
    private @Nullable UUID pendingPortraitId;
    private RocketStats stats = RocketSpecs.statsFor(RocketCensus.EMPTY);
    private final RocketFlight flight = new RocketFlight();
    /**
     * Combustível por tipo, em mB com fração (o consumo do voo é fracionário); o dado sincronizado é só o total com massa, arredondado para baixo.
     * O foguete químico só tem {@link Propellant#CHEMICAL}; os do Tier 2 têm hidrogênio, xenônio, hélio-3 e o núcleo nuclear.
     */
    private FuelLoad fuel = FuelLoad.EMPTY;
    /** Atitude no servidor (a fonte da verdade) e a interpolação dela no cliente (tick anterior, atual e alvo sincronizado). */
    private final Quaternionf attitude = new Quaternionf();
    private final Quaternionf attitudePrev = new Quaternionf();
    private final Quaternionf attitudeCur = new Quaternionf();
    private final Quaternionf attitudeTarget = new Quaternionf();
    private boolean attitudeSeeded;
    /** Cliente: posição virtual (espaço) do tick anterior e do atual, para interpolar entre ticks como a atitude; e o alvo sincronizado. */
    private final Vector3d virtualPrev = new Vector3d();
    private final Vector3d virtualCur = new Vector3d();
    private final Vector3d virtualTarget = new Vector3d();
    private boolean virtualSeeded;
    /** Cliente: neste tick o foguete deu um salto (costura do planeta, teleporte): o passageiro também não interpola até a nova posição. */
    private boolean jumped;
    /**
     * É uma nave ({@code ShipAssembler}), não um foguete. Só o servidor usa (salvo no NBT); o cliente não precisa dele nesta parte.
     * Enquanto for nave, o voo não sai para a dimensão de espaço acima da altitude de saída ({@code RocketFlightController}).
     */
    private boolean ship;
    /** Nave: bocais de impulso para baixo da montagem; entram no empuxo ({@code ShipLiftoff.stats}). Zero para foguete. */
    private int nozzlesDown;

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
        builder.define(VIRTUAL, false);
        builder.define(VIRTUAL_POS, new Vector3f());
        builder.define(POSE_STAMP, 0);
        builder.define(SEATS, "");
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
        if (SEATS.equals(accessor) && this.level().isClientSide()) {
            this.seatByRider.clear();
            this.seatByRider.putAll(parseSeats(this.entityData.get(SEATS)));
        }
        if (POSE_STAMP.equals(accessor) && this.level().isClientSide()) {
            recordPoseStamp(this.entityData.get(POSE_STAMP));
        }
        if (VIRTUAL_POS.equals(accessor) || VIRTUAL.equals(accessor)) {
            Vector3fc v = this.entityData.get(VIRTUAL_POS);
            this.virtualTarget.set(v.x(), v.y(), v.z());
            if (!this.virtualSeeded && this.entityData.get(VIRTUAL)) {
                // primeiro valor: sem varredura a partir da origem
                this.virtualSeeded = true;
                this.virtualPrev.set(this.virtualTarget);
                this.virtualCur.set(this.virtualTarget);
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

    /** Autoteste: a nave obedece ao comando do teste mesmo sem ninguém sentado (a tripulação fica em pé no convés). */
    public void setDevtestPilot(boolean on) {
        this.flight.setDevtestPilot(on);
    }

    /** Guarda o último comando do piloto (o voo o lê a cada tick do servidor). */
    public void applyControl(RocketControlPayload payload) {
        this.flight.applyControl(payload, this.level().getGameTime());
    }

    /** Troca o retrato (spawn, carga do NBT ou montagem): recalcula estatísticas e a caixa de colisão. */
    public void setPortrait(RocketPortrait portrait) {
        this.portrait = portrait;
        refreshStats();
        this.refreshDimensions();
        if (!this.deferredDeltas.isEmpty()) {
            List<com.spacehorizons.ship.deck.PortraitDeltaPayload> waiting = new ArrayList<>(this.deferredDeltas);
            this.deferredDeltas.clear();
            for (com.spacehorizons.ship.deck.PortraitDeltaPayload delta : waiting) {
                this.applyDelta(delta);
            }
        }
    }

    /**
     * Servidor: aplica mudanças ao retrato (porta que abre, conteúdo de baú que muda) e manda o DELTA a quem vê a nave, não o retrato inteiro.
     * O retrato novo tem id novo (o desenho refaz a malha); o NBT salvo e a conversão em blocos (estaleiro, âncora, pouso) já leem o retrato novo.
     */
    public void changePortrait(List<PortraitChange> changes) {
        if (this.level().isClientSide() || changes.isEmpty() || this.portrait.isEmpty()) {
            return;
        }
        RocketPortrait old = this.portrait;
        RocketPortrait next = old.withChanges(UUID.randomUUID(), changes);
        this.portrait = next;
        boolean stateChanged = false;
        for (PortraitChange change : changes) {
            stateChanged |= change.stateId() >= 0;
        }
        if (stateChanged) {
            refreshStats();
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(this,
                new com.spacehorizons.ship.deck.PortraitDeltaPayload(this.getId(), old.id(), next.id(), List.copyOf(changes)));
    }

    /** Cliente: aplica um delta do servidor; se o retrato dele ainda não chegou (retrato grande em partes), guarda o delta para depois. */
    public void applyDelta(com.spacehorizons.ship.deck.PortraitDeltaPayload delta) {
        if (this.portrait.id().equals(delta.newId())) {
            return;
        }
        if (!this.portrait.id().equals(delta.baseId())) {
            if (this.deferredDeltas.size() < 64) {
                this.deferredDeltas.add(delta);
            }
            return;
        }
        RocketPortrait old = this.portrait;
        this.portrait = old.withChanges(delta.newId(), delta.changes());
        portraitReplaced.accept(old.id(), delta.newId());
    }

    /** É uma nave montada pela ponte (ver o campo {@code ship}). */
    public boolean isShip() {
        return this.ship;
    }

    public void setShip(boolean ship) {
        this.ship = ship;
        refreshStats();
    }

    /** Nave: quantos bocais de impulso para baixo ela tem (o empuxo de decolagem); recalcula as estatísticas. */
    public void setNozzlesDown(int nozzlesDown) {
        this.nozzlesDown = Math.max(0, nozzlesDown);
        refreshStats();
    }

    /** As estatísticas do voo: as do censo do retrato; na nave, com os bocais de impulso para baixo no empuxo. */
    private void refreshStats() {
        this.stats = this.ship ? com.spacehorizons.ship.ShipLiftoff.stats(this.nozzlesDown, this.portrait.census()) : this.portrait.stats();
    }

    /** O combustível QUÍMICO, em mB (o único do foguete do Tier 1; os do Tier 2 estão em {@link #fuel()}). */
    public double fuelMb() {
        return this.fuel.get(Propellant.CHEMICAL);
    }

    /** Combustível químico em mB, limitado à capacidade dos tanques (os outros combustíveis ficam como estão). */
    public void setFuelMb(double mb) {
        setFuel(this.fuel.with(Propellant.CHEMICAL, mb));
    }

    /** Toda a carga, por combustível. */
    public FuelLoad fuel() {
        return this.fuel;
    }

    /** A carga por combustível, cada um limitado à capacidade (tanques e motores); sincroniza o total com massa, arredondado para baixo. */
    public void setFuel(FuelLoad load) {
        this.fuel = load.clampTo(this.stats.capacity());
        this.entityData.set(FUEL_MB, (float) Math.floor(this.fuel.propellantMb()));
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

    /**
     * Servidor: marca o foguete como estacionado no espaço, com a posição real {@code spaceDimPosition} (coordenadas da dimensão de espaço, em E,
     * calculada para o relógio {@code clock}). O dado sincronizado guarda a posição em I (inercial: não gira com a Terra), porque o cliente a
     * interpola entre ticks e a leva de volta a E com o relógio DELE: em E um ponto perto de Marte anda ~1.400 blocos por tick (a Terra gira), e um
     * pacote atrasado de um tick bastava para o planeta que a câmera orbita parecer a mais de um raio de distância e sumir do céu.
     */
    public void setVirtualSpacePosition(Vec3 spaceDimPosition, double clock) {
        double[] e = new double[3];
        com.spacehorizons.space.SpaceDimension.toSpace(spaceDimPosition.x, spaceDimPosition.y, spaceDimPosition.z, e);
        double[] inertial = new double[3];
        com.spacehorizons.space.InertialFrame.toInertial(clock, e[0], e[1], e[2], inertial);
        setVirtualInertial(new Vec3(inertial[0], inertial[1], inertial[2]));
    }

    /** Marca como estacionado com a posição real já em I (a carga do NBT e o servidor, depois da conversão). */
    private void setVirtualInertial(Vec3 inertial) {
        this.entityData.set(VIRTUAL, true);
        this.entityData.set(VIRTUAL_POS, new Vector3f((float) inertial.x, (float) inertial.y, (float) inertial.z));
    }

    /** Servidor: o foguete anda de verdade (na dimensão de um corpo): sem posição virtual. O dado só é reenviado quando o valor muda. */
    public void clearVirtualSpacePosition() {
        this.entityData.set(VIRTUAL, false);
    }

    /** O foguete está estacionado no espaço (a posição da entidade é o ponto fixo; a real é {@link #virtualSpacePosition}). */
    public boolean isVirtual() {
        return this.entityData.get(VIRTUAL);
    }

    /**
     * Cliente: a posição real do foguete no espaço em I (inercial, centrada na Terra, interpolada entre ticks), de onde o céu é visto (o cliente a
     * leva a E com o relógio do quadro); null se o foguete não está estacionado.
     */
    public @Nullable Vec3 virtualSpacePosition(float partialTick) {
        if (!this.isVirtual()) {
            return null;
        }
        double t = partialTick;
        return new Vec3(Mth.lerp(t, this.virtualPrev.x, this.virtualCur.x), Mth.lerp(t, this.virtualPrev.y, this.virtualCur.y),
                Mth.lerp(t, this.virtualPrev.z, this.virtualCur.z));
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
            com.spacehorizons.ship.deck.DeckFrame smooth = this.smoothFrame(this.clientTickAt(partialTick));
            if (smooth != null) {
                dest.set(smooth.attitude());
                return;
            }
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
            if (this.pendingPortraitId != null) {
                receivePendingPortrait();
            }
            this.attitudePrev.set(this.attitudeCur);
            this.attitudeCur.set(this.attitudeTarget);
            this.virtualPrev.set(this.virtualCur);
            this.virtualCur.set(this.virtualTarget);
            if (this.isHeld()) {
                freezeAtTarget();
            }
            // salto de posição (costura do planeta, teleporte): sem interpolar de uma ponta à outra, nem o foguete nem o passageiro
            this.jumped = this.position().distanceToSqr(this.oldPosition()) > JUMP_DISTANCE * JUMP_DISTANCE;
            if (this.jumped) {
                this.setOldPosAndRot();
            }
        } else if (this.devtestScript != null) {
            // autoteste do convés (S5): movimento roteirizado, no lugar do voo
            this.devtestScript.accept(this);
            com.spacehorizons.ship.deck.DeckCrew.afterRocketTick(this);
        } else if (!com.spacehorizons.ship.ShipConversions.isFrozen(this)) {
            // congelado enquanto a nave vira blocos no estaleiro (S3): o voo não anda até a conversão terminar
            this.flight.tick(this);
            this.entityData.set(FLIGHT_MODE, this.flight.mode().id());
            // a tripulação a pé no convés anda junto: a posição dela vem do referencial local pela pose que a nave acabou de ter
            com.spacehorizons.ship.deck.DeckCrew.afterRocketTick(this);
        } else {
            // congelada (conversão ou âncora, S6): a tripulação a pé continua andando no convés parado; a posição dela vem da pose de agora
            com.spacehorizons.ship.deck.DeckCrew.afterRocketTick(this);
        }
    }

    /** Autoteste do convés (S5): se definido, o servidor roda isto a cada tick no lugar do voo; o roteiro move a entidade e gira a atitude. */
    private @Nullable Consumer<RocketEntity> devtestScript;

    public void setDevtestScript(@Nullable Consumer<RocketEntity> script) {
        this.devtestScript = script;
    }

    /** A tripulação a pé deixa o convés quando a entidade sai do mundo (troca de dimensão, desmontagem, descarga). */
    @Override
    public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);
        if (!this.level().isClientSide()) {
            com.spacehorizons.ship.deck.DeckCrew.onRocketRemoved(this);
        }
    }

    // ---------------------------------------------------------------- pose suavizada para a tripulação a pé (S5)

    /** O relógio do cliente em ticks (contador de ticks do cliente com a parcial do quadro); ligado pelo código do cliente, null no servidor. */
    public static volatile @Nullable DoubleSupplier clientClock;
    /**
     * O contador de ticks do CLIENTE (um por tick, sem saltos). O tempo do jogo ({@code level.getGameTime()}) NÃO serve de relógio da linha do tempo: o
     * servidor o corrige no cliente a cada segundo, e com a rede atrasando e variando os pacotes ele salta de 1 a 2 ticks para trás ou para a frente.
     */
    public static volatile @Nullable LongSupplier clientTicks;

    private com.spacehorizons.ship.deck.@Nullable DeckPoseTimeline deckTimeline;

    /** Servidor: marca a pose atual com o tick do servidor (só enquanto há tripulação a pé a bordo). */
    public void stampPose(int stamp) {
        this.entityData.set(POSE_STAMP, Math.max(1, stamp));
    }

    /** Servidor: a tripulação a pé acabou; o cliente deixa a linha do tempo e volta à interpolação do vanilla (a troca é misturada, fase 2g). */
    public void clearPoseStamp() {
        this.entityData.set(POSE_STAMP, 0);
    }

    /** Quantos ticks do cliente dura a mistura do desenho do casco ao trocar da interpolação do vanilla para a linha do tempo, ou o contrário. */
    public static final double SEAM_BLEND_TICKS = 20.0;
    /** Cliente: a mistura das trocas do desenho do casco entre as duas fontes de pose (fase 2g): a diferença do instante da troca desce a zero. */
    private final com.spacehorizons.ship.deck.PoseBlend hullBlend = new com.spacehorizons.ship.deck.PoseBlend();
    /** Cliente: o desenho está na linha do tempo (a pose vem dela) ou no vanilla. */
    private boolean timelineOn;
    /** Cliente: o servidor avisou que não há mais tripulação (marca 0): a linha do tempo não vale mais, as amostras ficam só para a troca. */
    private boolean timelineStopped;

    /** Cliente: chegou o tick do servidor da pose; guarda a posição e a atitude que o vanilla acabou de receber (a posição vem antes dos dados). */
    private void recordPoseStamp(int stamp) {
        DoubleSupplier clock = clientClock;
        if (clock == null) {
            return;
        }
        if (stamp <= 0) {
            // sem tripulação: a próxima leitura do desenho troca a fonte (misturando) de volta ao vanilla
            this.timelineStopped = true;
            return;
        }
        if (this.deckTimeline == null) {
            this.deckTimeline = new com.spacehorizons.ship.deck.DeckPoseTimeline();
        }
        if (this.timelineStopped) {
            // nova tripulação depois de uma parada: as amostras antigas (de outro embarque) não servem de ponta para interpolar
            if (this.timelineOn) {
                this.switchPoseSource(clock.getAsDouble(), false);
            }
            this.deckTimeline.clear();
            this.timelineStopped = false;
        }
        var handler = this.getInterpolation();
        var target = handler.target();
        Vec3 p = handler.hasActiveInterpolation() && target != null ? target.position() : this.position();
        this.deckTimeline.record(stamp, p.x, p.y, p.z, this.attitudeTarget, clock.getAsDouble());
    }

    /** O tempo do cliente (ticks do jogo com fração) de um quadro com a parcial dada. */
    public double clientTickAt(float partialTick) {
        LongSupplier ticks = clientTicks;
        return (ticks == null ? this.level().getGameTime() : ticks.getAsLong()) + (double) partialTick;
    }

    /** Cliente: a pose suavizada no tempo do cliente {@code clientTick}, ou null se não há (sem tripulação a pé ou sem pacotes recentes). */
    public com.spacehorizons.ship.deck.@Nullable DeckFrame smoothFrame(double clientTick) {
        com.spacehorizons.ship.deck.DeckPoseTimeline timeline = this.deckTimeline;
        if (timeline == null || !this.level().isClientSide()) {
            return null;
        }
        boolean on = !this.timelineStopped && timeline.active(clientTick);
        if (on != this.timelineOn) {
            this.switchPoseSource(clientTick, on);
        }
        com.spacehorizons.ship.deck.DeckFrame base = on ? timeline.sample(clientTick, this.pivotLocal()) : null;
        if (!this.hullBlend.active(clientTick)) {
            return base;
        }
        return this.blended(clientTick, base != null ? base : this.vanillaFrame(clientTick));
    }

    /** A pose do casco na interpolação do vanilla no tempo do cliente {@code clientTick} (a parcial é a fração desde o último tick do cliente). */
    private com.spacehorizons.ship.deck.DeckFrame vanillaFrame(double clientTick) {
        float partial = (float) Math.max(0.0, Math.min(1.0, clientTick - this.clientTickAt(0.0F)));
        Vec3 p = this.getPosition(partial);
        Quaternionf q = new Quaternionf();
        this.attitudePrev.slerp(this.attitudeCur, partial, q);
        return new com.spacehorizons.ship.deck.DeckFrame(p.x, p.y, p.z, q, this.pivotLocal());
    }

    /** A pose {@code base} da fonte atual com o que resta da mistura da última troca (posição do centro e atitude). */
    private com.spacehorizons.ship.deck.DeckFrame blended(double clientTick, com.spacehorizons.ship.deck.DeckFrame base) {
        org.joml.Vector3d center = this.hullBlend.position(clientTick, new org.joml.Vector3d(base.cx(), base.cy(), base.cz()), new org.joml.Vector3d());
        Quaternionfc a = base.attitude();
        org.joml.Quaterniond q = this.hullBlend.rotation(clientTick, new org.joml.Quaterniond(a.x(), a.y(), a.z(), a.w()), new org.joml.Quaterniond());
        return new com.spacehorizons.ship.deck.DeckFrame(center.x, center.y, center.z, new Quaternionf((float) q.x, (float) q.y, (float) q.z, (float) q.w), this.pivotLocal());
    }

    /**
     * A fonte da pose do casco mudou (a linha do tempo da nave ligou ou desligou): a pose que estava sendo desenhada e a da fonte nova diferem de até
     * 2 ticks de movimento da nave; a diferença é guardada e desce a zero em {@link #SEAM_BLEND_TICKS} ticks, e o desenho, a câmera e o tripulante,
     * que leem a mesma pose, não pulam (fase 2g).
     */
    private void switchPoseSource(double clientTick, boolean on) {
        com.spacehorizons.ship.deck.DeckPoseTimeline timeline = this.deckTimeline;
        com.spacehorizons.ship.deck.DeckFrame fromTimeline = timeline == null || timeline.size() == 0 ? null : timeline.sample(clientTick, this.pivotLocal());
        com.spacehorizons.ship.deck.DeckFrame old = this.timelineOn && fromTimeline != null ? fromTimeline : this.vanillaFrame(clientTick);
        if (this.hullBlend.active(clientTick)) {
            old = this.blended(clientTick, old);
        }
        com.spacehorizons.ship.deck.DeckFrame now = on && fromTimeline != null ? fromTimeline : this.vanillaFrame(clientTick);
        Quaternionfc oa = old.attitude();
        Quaternionfc na = now.attitude();
        this.hullBlend.beginPosition(clientTick, SEAM_BLEND_TICKS, new org.joml.Vector3d(old.cx(), old.cy(), old.cz()), new org.joml.Vector3d(now.cx(), now.cy(), now.cz()));
        this.hullBlend.beginRotation(clientTick, SEAM_BLEND_TICKS, new org.joml.Quaterniond(oa.x(), oa.y(), oa.z(), oa.w()),
                new org.joml.Quaterniond(na.x(), na.y(), na.z(), na.w()));
        this.timelineOn = on;
    }

    /**
     * Cliente: o tick do servidor (fracionário) que a linha do tempo mostra no tempo do cliente {@code clientTick}, ou NaN se ela não está ativa. É o
     * tempo em que se lê o estado dos tripulantes vistos por observadores, para ficarem na mesma pose do casco.
     */
    public double smoothServerTimeAt(double clientTick) {
        com.spacehorizons.ship.deck.DeckPoseTimeline timeline = this.deckTimeline;
        return timeline == null || this.timelineStopped || !this.level().isClientSide() || !timeline.active(clientTick) ? Double.NaN : timeline.serverTimeAt(clientTick);
    }

    /** Cliente: a linha do tempo está ativa mas o tempo mostrado passou da pose mais nova (sem dado novo; diagnóstico do autoteste). */
    public boolean smoothStarved(double clientTick) {
        com.spacehorizons.ship.deck.DeckPoseTimeline timeline = this.deckTimeline;
        return timeline != null && !this.timelineStopped && this.level().isClientSide() && timeline.active(clientTick) && timeline.starved(clientTick);
    }

    /** Cliente: a atitude suavizada em dupla precisão (para medir o tremor), ou null. */
    public org.joml.@Nullable Quaterniond smoothAttitude(double clientTick) {
        com.spacehorizons.ship.deck.DeckPoseTimeline timeline = this.deckTimeline;
        return timeline == null || this.timelineStopped || !this.level().isClientSide() || !timeline.active(clientTick) ? null : timeline.sampleAttitude(clientTick);
    }

    // ---------------------------------------------------------------- pose para o convés andável (S5)

    /**
     * A pose da nave neste tick: o centro da caixa, a atitude e o pivô. No servidor é a atual; no cliente, a do fim do último tick (a posição
     * já interpolada pelo passo de rede e a atitude do tick).
     */
    public com.spacehorizons.ship.deck.DeckFrame deckFrame() {
        return new com.spacehorizons.ship.deck.DeckFrame(this.getX(), this.getY(), this.getZ(), this.level().isClientSide() ? this.attitudeCur : this.attitude,
                this.pivotLocal());
    }

    /** Cliente: a pose do tick anterior (posição e atitude), para interpolar o tripulante como o desenho interpola a nave. */
    public com.spacehorizons.ship.deck.DeckFrame deckFramePrevious() {
        Vec3 old = this.oldPosition();
        return new com.spacehorizons.ship.deck.DeckFrame(old.x, old.y, old.z, this.attitudePrev, this.pivotLocal());
    }

    /** Cliente: a pose no quadro, a mesma que o desenho da nave usa (posição linear, atitude por slerp). */
    public com.spacehorizons.ship.deck.DeckFrame deckFrameAt(float partialTick) {
        com.spacehorizons.ship.deck.DeckFrame smooth = this.smoothFrame(this.clientTickAt(partialTick));
        if (smooth != null) {
            return smooth;
        }
        Vec3 p = this.getPosition(partialTick);
        Quaternionf q = new Quaternionf();
        this.orientation(partialTick, q);
        return new com.spacehorizons.ship.deck.DeckFrame(p.x, p.y, p.z, q, this.pivotLocal());
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
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player player && this.seatOf(player).equals(this.portrait.cockpit())) {
                return player;
            }
        }
        return null;
    }

    /** O assento (posição local) do passageiro: o que ele escolheu ao sentar, ou a ponte (o cockpit) para quem sentou clicando na nave. */
    public BlockPos seatOf(Entity passenger) {
        Long seat = this.seatByRider.get(passenger.getUUID());
        return seat == null ? this.portrait.cockpit() : BlockPos.of(seat);
    }

    /** O passageiro sentou clicando num assento do retrato (e não pelo clique de sempre na nave)? */
    public boolean sittingFromDeck(Entity passenger) {
        return this.seatByRider.containsKey(passenger.getUUID());
    }

    /** Alguém está sentado neste assento? */
    public boolean seatTaken(BlockPos seat) {
        for (Entity passenger : this.getPassengers()) {
            if (this.seatOf(passenger).equals(seat)) {
                return true;
            }
        }
        return false;
    }

    /** Cada assento da nave (a ponte e os {@code ship_seat}) tem no máximo um passageiro; sem assento escolhido o passageiro vai à ponte. */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !this.seatTaken(this.seatOf(passenger));
    }

    /**
     * Servidor: senta o jogador no assento {@code seat} do retrato (passageiro da nave, posicionado por {@link #positionRider} na pose dela). Quem
     * estava no convés deixa o convés sem teletransporte; se a nave recusa, volta ao convés onde estava. Assento ocupado: aviso na barra de ação.
     */
    public boolean sitAt(net.minecraft.server.level.ServerPlayer player, BlockPos seat) {
        if (this.level().isClientSide() || player.isPassenger() || player.level() != this.level()) {
            return false;
        }
        if (this.seatTaken(seat)) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.spacehorizons.ship.seat_taken"));
            return false;
        }
        com.spacehorizons.ship.deck.DeckState previous = com.spacehorizons.ship.deck.DeckCrew.release(player);
        this.seatByRider.put(player.getUUID(), seat.asLong());
        this.syncSeats();
        boolean rode = player.startRiding(this);
        if (!rode) {
            this.seatByRider.remove(player.getUUID());
            this.syncSeats();
            if (previous != null) {
                com.spacehorizons.ship.deck.DeckCrew.board(player, this, previous);
            }
        }
        SpaceHorizons.LOGGER.info("[deck] {} senta no assento {} da nave {}: {}", player.getPlainTextName(), seat, this.getId(), rode ? "sentou" : "recusado");
        return rode;
    }

    /**
     * Quem sentou pelo retrato e levanta com a nave em voo volta ao convés, em pé ao lado do assento (a nave em movimento não tem chão para pisar
     * no mundo). Pousada, sai como sempre ({@link #getDismountLocationForPassenger}).
     */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (this.level().isClientSide()) {
            // o cliente refaz a lista de passageiros inteira a cada pacote (ejeta todos e senta de novo): o mapa de assentos vem só do dado sincronizado
            return;
        }
        Long seat = this.seatByRider.remove(passenger.getUUID());
        if (seat == null) {
            // sem assento do retrato (o piloto sentado pelo caminho antigo da ponte): levantar em voo pede o embarque no convés
            if (passenger instanceof net.minecraft.server.level.ServerPlayer player && !this.isRemoved()) {
                com.spacehorizons.ship.deck.DeckCrew.requestBoardAfterSeat(player, this);
            }
            return;
        }
        this.syncSeats();
        if (passenger instanceof net.minecraft.server.level.ServerPlayer player && !this.isRemoved() && !this.portrait.isEmpty()
                && this.flight.mode() != FlightMode.LANDED && !player.hasDisconnected() && player.isAlive()) {
            this.standUp(player, BlockPos.of(seat));
        }
    }

    private void standUp(net.minecraft.server.level.ServerPlayer player, BlockPos seat) {
        Vec3 spot = com.spacehorizons.ship.deck.DeckStand.find(this.portrait.deckColliders(), seat.getX(), seat.getY(), seat.getZ(), player.getBbWidth(),
                player.getBbHeight());
        com.spacehorizons.ship.deck.DeckState state = new com.spacehorizons.ship.deck.DeckState();
        state.pos.set(spot.x, spot.y, spot.z);
        float[] view = this.deckFrame().viewToLocal(player.getYRot(), player.getXRot());
        state.yaw = view[0];
        state.pitch = view[1];
        state.onGround = true;
        com.spacehorizons.ship.deck.DeckCrew.board(player, this, state);
    }

    private void syncSeats() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<UUID, Long> e : this.seatByRider.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        this.entityData.set(SEATS, sb.toString());
    }

    private static Map<UUID, Long> parseSeats(String text) {
        Map<UUID, Long> seats = new HashMap<>();
        for (String entry : text.split(";")) {
            int eq = entry.indexOf('=');
            if (eq > 0) {
                try {
                    seats.put(UUID.fromString(entry.substring(0, eq)), Long.parseLong(entry.substring(eq + 1)));
                } catch (IllegalArgumentException ignored) {
                    // entrada ilegível: o passageiro cai na ponte
                }
            }
        }
        return seats;
    }

    /** O piloto fica no cockpit (girado pela atitude); o ponto de assento é o do bloco do cockpit menos a altura do quadril. */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        Vec3 at = this.passengerPositionAt(this.position(), this.level().isClientSide() ? this.attitudeCur : this.attitude, passenger);
        moveFunction.accept(passenger, at.x, at.y, at.z);
        if (this.jumped && this.level().isClientSide()) {
            passenger.setOldPosAndRot();
        }
    }

    /** Onde o passageiro fica (posição da entidade dele) com o foguete em {@code center} e a atitude local {@code attitude}. */
    public Vec3 passengerPositionAt(Vec3 center, Quaternionfc attitude, Entity passenger) {
        BlockPos seatPos = this.seatOf(passenger);
        Vector3f local = new Vector3f(seatPos.getX() + 0.5F, seatPos.getY() + 0.5F, seatPos.getZ() + 0.5F).sub(this.pivotLocal());
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
        // UM passo: a cada tick o foguete vai ao último ponto recebido (junto com o piloto, que é posicionado a partir dele no mesmo tick), e o
        // desenho interpola entre dois ticks. Mais passos deixavam o foguete 2 passos atrás do piloto a 50 blocos por tick; o
        // handler vanilla ainda daria snapTo acima de 64 blocos (ver RocketPositionMixin, que impede isso).
        return LinearInterpolationHandler.create(this, 1);
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

    /**
     * O retrato vai no pacote de spawn se couber ({@link RocketAssembler#INLINE_PORTRAIT_BYTES}); senão só o id vai, e as partes seguem em
     * pacotes próprios quando o jogador passa a ver o foguete ({@link #startSeenByPlayer}).
     */
    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        boolean deferred = RocketPortrait.encodedSize(this.portrait) > RocketAssembler.inlineLimit();
        buffer.writeBoolean(deferred);
        if (deferred) {
            buffer.writeUUID(this.portrait.id());
        } else {
            RocketPortrait.write(buffer, this.portrait);
        }
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        if (buffer.readBoolean()) {
            this.pendingPortraitId = buffer.readUUID();
            receivePendingPortrait();
        } else {
            this.setPortrait(RocketPortrait.read(buffer));
        }
    }

    /** Cliente: se as partes do retrato esperado já chegaram, monta o retrato. */
    private void receivePendingPortrait() {
        byte[] bytes = PortraitParts.CLIENT.take(this.pendingPortraitId);
        if (bytes == null) {
            return;
        }
        this.pendingPortraitId = null;
        try {
            this.setPortrait(RocketPortrait.read(io.netty.buffer.Unpooled.wrappedBuffer(bytes)));
        } catch (RuntimeException e) {
            SpaceHorizons.LOGGER.error("Retrato do foguete recebido em partes está corrompido", e);
        }
    }

    /** O retrato grande demais para o pacote de spawn segue em partes para quem passa a ver o foguete. */
    @Override
    public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (this.portrait.isEmpty() || RocketPortrait.encodedSize(this.portrait) <= RocketAssembler.inlineLimit()) {
            return;
        }
        List<byte[]> parts = PortraitParts.split(this.portrait.encoded(), PortraitParts.partBytes());
        for (int i = 0; i < parts.size(); i++) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new RocketPortraitPartPayload(this.portrait.id(), i, parts.size(), parts.get(i)));
        }
        SpaceHorizons.LOGGER.info("Retrato grande do foguete ({} bytes) enviado a {} em {} parte(s)", this.portrait.encoded().length,
                player.getGameProfile().name(), parts.size());
    }

    private static final Codec<List<Float>> QUATERNION_CODEC = Codec.FLOAT.listOf(4, 4);

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("Portrait", CompoundTag.CODEC, this.portrait.save());
        output.putDouble("FuelMb", this.fuelMb());
        if (!this.fuel.onlyChemical()) {
            // Tier 2: os outros combustíveis (o químico continua em "FuelMb", então saves antigos e novos se leem dos dois lados)
            for (Propellant p : Propellant.values()) {
                if (p != Propellant.CHEMICAL && this.fuel.get(p) > 0.0) {
                    output.putDouble("Fuel_" + p.key(), this.fuel.get(p));
                }
            }
        }
        output.putBoolean("Ship", this.ship);
        output.putInt("NozzlesDown", this.nozzlesDown);
        output.putFloat("Throttle", this.throttle());
        output.putByte("FlightMode", this.flightMode().id());
        output.putByte("SasMode", this.sasMode());
        output.putFloat("Heat", this.heat());
        output.store("Attitude", QUATERNION_CODEC, List.of(this.attitude.x, this.attitude.y, this.attitude.z, this.attitude.w));
        if (this.isVirtual()) {
            // a posição real (virtual) acompanha a entidade na troca de dimensão, que a recria por NBT, até o primeiro passo do voo
            Vector3fc v = this.entityData.get(VIRTUAL_POS);
            output.putDouble("VirtualX", v.x());
            output.putDouble("VirtualY", v.y());
            output.putDouble("VirtualZ", v.z());
        }
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
        FuelLoad load = FuelLoad.chemical(input.getDoubleOr("FuelMb", 0.0));
        for (Propellant p : Propellant.values()) {
            if (p != Propellant.CHEMICAL) {
                load = load.with(p, input.getDoubleOr("Fuel_" + p.key(), 0.0));
            }
        }
        this.setFuel(load);
        this.ship = input.getBooleanOr("Ship", false);
        this.setNozzlesDown(input.getIntOr("NozzlesDown", 0));
        this.setThrottle(input.getFloatOr("Throttle", 0.0F));
        this.setFlightMode(FlightMode.byId(input.getByteOr("FlightMode", FlightMode.LANDED.id())));
        this.entityData.set(SAS_MODE, input.getByteOr("SasMode", (byte) 0));
        this.entityData.set(HEAT, input.getFloatOr("Heat", 0.0F));
        input.read("Attitude", QUATERNION_CODEC).ifPresent(q -> this.setAttitude(new Quaternionf(q.get(0), q.get(1), q.get(2), q.get(3))));
        this.flight.load(input);
        double virtualY = input.getDoubleOr("VirtualY", Double.NaN);
        if (!Double.isNaN(virtualY)) {
            this.setVirtualInertial(new Vec3(input.getDoubleOr("VirtualX", 0.0), virtualY, input.getDoubleOr("VirtualZ", 0.0)));
        }
    }
}
