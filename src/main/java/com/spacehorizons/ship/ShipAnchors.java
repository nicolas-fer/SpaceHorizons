package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.PortraitGrid;
import com.spacehorizons.rocket.RocketAttitude;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketLanding;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.ship.deck.DeckCrew;
import com.spacehorizons.space.SpaceDimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Âncora no ar (spike S6, {@code docs/PLANO_NAVE_ATMOSFERA.md}): a nave parada vira blocos reais no ar, e de volta. Guarda as naves ancoradas
 * (estado persistido no Overworld, como o {@link ShipRegistry}), que o jogo trata de um jeito próprio enquanto estão ali: areia, cascalho e
 * outros blocos que caem não caem e fluidos não escorrem dentro da caixa ({@code AnchorTickMixin}), e mobs não nascem nela nem no teto
 * ({@link #onSpawnCheck}).
 *
 * <p>Ancorar ({@link #request}): só nave ({@link RocketEntity#isShip}), quase em pé e quase parada. O retrato é girado ao quarto de volta mais perto
 * (como o pouso) e o canto mínimo é o do centro arredondado à grade; a pré-verificação ({@link #preCheck}) exige ar em cada bloco da nave e um
 * bloco de ar em volta (senão a montagem de volta levaria o vizinho junto). Antes da conversão a entidade é ACOMODADA na grade em
 * {@link #SETTLE_TICKS} ticks (posição e atitude vão suavemente ao ponto exato onde os blocos vão aparecer): assim os blocos aparecem onde a
 * entidade estava e quem anda no convés fica no mesmo ponto do mundo, sem teleporte. A conversão é o {@link AnchorJob}, em lotes.
 *
 * <p>Desancorar ({@link #unanchor}): a montagem da fase 2a-i pela ponte ({@link ShipAssembler}), sem exigir a plataforma. O jogador que montou senta;
 * quem estava em pé na nave entra no convés na mesma posição. Decisão A3 (recomendada): a âncora exigir um gerador antigravidade; no spike é o
 * comando de dev {@code /spacehorizons ship anchor} e {@code unanchor}.
 */
public final class ShipAnchors extends SavedData {
    /** Velocidade máxima (blocos por tick) para ancorar: a nave tem de estar parada (pairando). */
    public static final double MAX_SPEED = 2.0;
    /** Inclinação máxima (graus), a mesma do pouso. */
    public static final double MAX_TILT_DEGREES = 15.0;
    /** Ticks da acomodação na grade (0,5 s); sem desvio nenhum, a conversão começa no mesmo tick. */
    public static final int SETTLE_TICKS = 10;

    /** Uma nave ancorada: a caixa dos blocos (canto mínimo e tamanho), a ponte, o giro aplicado ao retrato e o tick em que ancorou. */
    public record Anchor(int id, Identifier dimension, BlockPos min, int sizeX, int sizeY, int sizeZ, BlockPos cockpit, int quarterTurns, long since) {
        static final Codec<Anchor> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Anchor::id),
                Identifier.CODEC.fieldOf("dimension").forGetter(Anchor::dimension),
                BlockPos.CODEC.fieldOf("min").forGetter(Anchor::min),
                Codec.INT.fieldOf("sx").forGetter(Anchor::sizeX),
                Codec.INT.fieldOf("sy").forGetter(Anchor::sizeY),
                Codec.INT.fieldOf("sz").forGetter(Anchor::sizeZ),
                BlockPos.CODEC.fieldOf("cockpit").forGetter(Anchor::cockpit),
                Codec.INT.optionalFieldOf("turns", 0).forGetter(Anchor::quarterTurns),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(Anchor::since)
        ).apply(i, Anchor::new));

        /** O bloco (x, y, z) está dentro da caixa, com {@code margin} blocos a mais de cada lado? */
        public boolean contains(int x, int y, int z, int margin) {
            return x >= this.min.getX() - margin && x < this.min.getX() + this.sizeX + margin && y >= this.min.getY() - margin
                    && y < this.min.getY() + this.sizeY + margin && z >= this.min.getZ() - margin && z < this.min.getZ() + this.sizeZ + margin;
        }

        public AABB box() {
            return new AABB(this.min.getX(), this.min.getY(), this.min.getZ(), this.min.getX() + this.sizeX, this.min.getY() + this.sizeY,
                    this.min.getZ() + this.sizeZ);
        }

        public ResourceKey<Level> levelKey() {
            return ResourceKey.create(Registries.DIMENSION, this.dimension);
        }
    }

    public static final Codec<ShipAnchors> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("next_id").forGetter(r -> r.nextId),
            Anchor.CODEC.listOf().fieldOf("anchors").forGetter(r -> List.copyOf(r.anchors))
    ).apply(i, ShipAnchors::new));

    public static final SavedDataType<ShipAnchors> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "ship_anchors"), ShipAnchors::new, CODEC);

    private int nextId = 1;
    private final List<Anchor> anchors = new ArrayList<>();

    public ShipAnchors() {
    }

    private ShipAnchors(int nextId, List<Anchor> anchors) {
        this.nextId = nextId;
        this.anchors.addAll(anchors);
    }

    /** O estado do mundo (carrega e atualiza o índice usado no caminho quente dos ticks de bloco). */
    public static ShipAnchors get(MinecraftServer server) {
        ShipAnchors data = server.overworld().getDataStorage().computeIfAbsent(TYPE);
        if (data.view == null || index != data.view) {
            data.view = List.copyOf(data.anchors);
            index = data.view;
        }
        return data;
    }

    /** A cópia imutável das âncoras desta instância, a mesma do índice estático. */
    private @Nullable List<Anchor> view;

    Anchor add(ServerLevel level, BlockPos min, int sizeX, int sizeY, int sizeZ, BlockPos cockpit, int quarterTurns, long since) {
        Anchor anchor = new Anchor(this.nextId++, level.dimension().identifier(), min.immutable(), sizeX, sizeY, sizeZ, cockpit.immutable(), quarterTurns, since);
        this.anchors.add(anchor);
        changed();
        return anchor;
    }

    boolean remove(int id) {
        boolean removed = this.anchors.removeIf(a -> a.id() == id);
        if (removed) {
            changed();
        }
        return removed;
    }

    private void changed() {
        setDirty();
        this.view = List.copyOf(this.anchors);
        index = this.view;
    }

    // ---------------------------------------------------------------- consultas (caminho quente)

    /** As âncoras do mundo carregado (todas as dimensões); vazio antes de o servidor abrir o estado. */
    private static volatile List<Anchor> index = List.of();

    public static List<Anchor> all() {
        return index;
    }

    /** O bloco está dentro de uma nave ancorada nesta dimensão? Chamado a cada tick agendado de fluido e de bloco que cai. */
    public static boolean holds(Level level, BlockPos pos) {
        return find(level, pos, 0) != null;
    }

    /** A âncora que contém o bloco, ou null. */
    public static @Nullable Anchor at(Level level, BlockPos pos) {
        return find(level, pos, 0);
    }

    private static @Nullable Anchor find(Level level, BlockPos pos, int margin) {
        List<Anchor> list = index;
        if (list.isEmpty()) {
            return null;
        }
        Identifier dim = level.dimension().identifier();
        for (Anchor a : list) {
            if (a.contains(pos.getX(), pos.getY(), pos.getZ(), margin) && a.dimension().equals(dim)) {
                return a;
            }
        }
        return null;
    }

    /**
     * Mobs não nascem numa nave ancorada nem no teto dela: a caixa com um bloco de folga (o pé do mob em cima do teto fica um acima da caixa). Só os
     * nascimentos do mundo (naturais, da geração e patrulhas); ovo, comando e gerador seguem valendo.
     */
    public static void onSpawnCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (index.isEmpty()) {
            return;
        }
        EntitySpawnReason reason = event.getSpawnType();
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION && reason != EntitySpawnReason.PATROL) {
            return;
        }
        if (find(event.getLevel().getLevel(), event.getPos(), 1) != null) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    // ---------------------------------------------------------------- matemática da grade (pura, testada)

    /** A atitude da nave ancorada com {@code quarterTurns} quartos de volta horários: a do retrato girado por {@link RocketPortrait#rotated}. */
    public static Quaternionf snappedAttitude(int quarterTurns) {
        return RocketAttitude.fromYaw(Math.floorMod(quarterTurns, 4) * 90.0);
    }

    /** O centro da entidade em que os blocos girados, com o canto mínimo em {@code origin}, ficam exatamente onde o retrato estava. */
    public static Vec3 snappedCenter(BlockPos origin, int rotatedSizeX, int sizeY, int rotatedSizeZ) {
        return new Vec3(origin.getX() + rotatedSizeX / 2.0, origin.getY() + sizeY / 2.0, origin.getZ() + rotatedSizeZ / 2.0);
    }

    // ---------------------------------------------------------------- ancorar

    /** A nave está acomodando na grade antes da conversão. */
    private record Settle(ServerLevel level, RocketEntity rocket, Vec3 from, Quaternionf fromAttitude, Vec3 to, Quaternionf toAttitude, int quarterTurns,
            BlockPos origin, int[] tick) {
    }

    private static final Map<UUID, Settle> SETTLING = new HashMap<>();
    /** Para o autoteste: o deslocamento (blocos) e o giro (graus) da última acomodação. */
    public static volatile double lastSettleDistance;
    public static volatile double lastSettleDegrees;

    /**
     * Pede a âncora da nave: confere (nave, em pé, parada, fora do estaleiro e do espaço, volume livre no ar) e começa a acomodação; a conversão
     * segue em lotes nos ticks seguintes. Lança {@link ShipConversionException} sem tocar no mundo se algo recusar.
     */
    public static void request(ServerLevel level, RocketEntity rocket) throws ShipConversionException {
        if (!rocket.isShip()) {
            throw new ShipConversionException("Só naves (montadas pela ponte) ancoram no ar; um foguete pousa.");
        }
        RocketPortrait portrait = rocket.portrait();
        if (portrait.isEmpty()) {
            throw new ShipConversionException("A nave não tem blocos.");
        }
        if (ShipYard.is(level) || SpaceDimension.is(level) || rocket.isVirtual()) {
            throw new ShipConversionException("Não há onde ancorar aqui (estaleiro ou espaço): a âncora é dentro da dimensão de um corpo.");
        }
        if (SETTLING.containsKey(rocket.getUUID()) || ShipConversions.isFrozen(rocket)) {
            throw new ShipConversionException("Esta nave já está ancorando ou numa conversão.");
        }
        if (rocket.flight().mode() == FlightMode.PARACHUTE) {
            throw new ShipConversionException("Não dá para ancorar com o paraquedas aberto.");
        }
        double tilt = rocket.tiltDegrees();
        if (tilt > MAX_TILT_DEGREES) {
            throw new ShipConversionException(String.format(java.util.Locale.ROOT, "A nave está inclinada demais para ancorar (%.0f graus; máximo %.0f).", tilt,
                    MAX_TILT_DEGREES));
        }
        double speed = Math.max(rocket.position().distanceTo(rocket.oldPosition()), rocket.getDeltaMovement().length());
        if (speed > MAX_SPEED) {
            throw new ShipConversionException(String.format(java.util.Locale.ROOT, "A nave precisa estar parada para ancorar (%.1f b/t; máximo %.1f).", speed,
                    MAX_SPEED));
        }
        int turns = PortraitGrid.quarterTurnsFromYaw(rocket.yawDegrees());
        RocketPortrait rotated = portrait.rotated(turns);
        BlockPos origin = RocketLanding.originFor(rocket.position(), rotated.sizeX, rotated.sizeY, rotated.sizeZ);
        preCheck(level, rotated, origin);

        Vec3 to = snappedCenter(origin, rotated.sizeX, rotated.sizeY, rotated.sizeZ);
        Quaternionf toAttitude = snappedAttitude(turns);
        Quaternionf fromAttitude = new Quaternionf(rocket.attitude());
        double distance = rocket.position().distanceTo(to);
        double degrees = Math.toDegrees(fromAttitude.difference(toAttitude, new Quaternionf()).angle());
        degrees = degrees > 180.0 ? 360.0 - degrees : degrees;
        lastSettleDistance = distance;
        lastSettleDegrees = degrees;
        SpaceHorizons.LOGGER.info("[anchor] pedido da nave {}: canto {}, {} quarto(s) de volta, acomodação de {} blocos e {} graus", rocket.getUUID(),
                origin.toShortString(), turns, String.format(java.util.Locale.ROOT, "%.3f", distance), String.format(java.util.Locale.ROOT, "%.2f", degrees));
        ShipConversions.freeze(rocket.getUUID());
        if (distance < 1.0E-4 && degrees < 0.01) {
            rocket.setPos(to);
            rocket.setAttitude(toAttitude);
            startJob(level, rocket, turns, origin);
            return;
        }
        SETTLING.put(rocket.getUUID(), new Settle(level, rocket, rocket.position(), fromAttitude, to, toAttitude, turns, origin, new int[1]));
    }

    private static void startJob(ServerLevel level, RocketEntity rocket, int turns, BlockPos origin) {
        try {
            ShipConversions.startAnchor(level, rocket, turns, origin);
        } catch (ShipConversionException e) {
            ShipConversions.unfreeze(rocket.getUUID());
            SpaceHorizons.LOGGER.warn("[anchor] a âncora da nave {} foi recusada depois da acomodação: {}", rocket.getUUID(), e.getMessage());
            tell(level, rocket, Component.literal("Âncora recusada: " + e.getMessage()));
        }
    }

    /** Avisa a tripulação (passageiros e quem anda no convés) da nave. */
    private static void tell(ServerLevel level, RocketEntity rocket, Component message) {
        for (Entity passenger : rocket.getPassengers()) {
            if (passenger instanceof ServerPlayer player) {
                player.sendSystemMessage(message);
            }
        }
        for (ServerPlayer player : level.players()) {
            DeckCrew.Member m = DeckCrew.member(player.getUUID());
            if (m != null && m.rocketUuid().equals(rocket.getUUID())) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** A cada tick do servidor (antes da fila das conversões): move as naves em acomodação e, ao chegar na grade, começa a conversão. */
    public static void tick(MinecraftServer server) {
        if (SETTLING.isEmpty()) {
            return;
        }
        Iterator<Settle> it = SETTLING.values().iterator();
        List<Settle> arrived = new ArrayList<>();
        while (it.hasNext()) {
            Settle s = it.next();
            RocketEntity rocket = s.rocket();
            if (rocket.isRemoved()) {
                ShipConversions.unfreeze(rocket.getUUID());
                it.remove();
                continue;
            }
            int t = ++s.tick()[0];
            double f = Math.min(1.0, t / (double) SETTLE_TICKS);
            double e = f * f * (3.0 - 2.0 * f);
            Vec3 p = s.from().lerp(s.to(), e);
            Vec3 before = rocket.position();
            rocket.setPos(p);
            rocket.setDeltaMovement(p.subtract(before));
            rocket.setAttitude(s.fromAttitude().slerp(s.toAttitude(), (float) e, new Quaternionf()));
            if (f >= 1.0) {
                rocket.setPos(s.to());
                rocket.setAttitude(s.toAttitude());
                rocket.setDeltaMovement(Vec3.ZERO);
                it.remove();
                arrived.add(s);
            }
        }
        for (Settle s : arrived) {
            startJob(s.level(), s.rocket(), s.quarterTurns(), s.origin());
        }
    }

    /**
     * O volume no ar está livre? Cada bloco da nave cai em ar (carregado, dentro da altura de construção) e cada vizinho por face que não é da nave
     * também é ar: a nave não encosta em nada (a montagem de volta levaria o vizinho junto, e não substitui nada como o pouso faz com grama).
     */
    static void preCheck(ServerLevel level, RocketPortrait rotated, BlockPos origin) throws ShipConversionException {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = 0; y < rotated.sizeY; y++) {
            for (int z = 0; z < rotated.sizeZ; z++) {
                for (int x = 0; x < rotated.sizeX; x++) {
                    if (rotated.get(x, y, z).isAir()) {
                        continue;
                    }
                    requireAir(level, cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z), "o volume da nave");
                    for (Direction d : Direction.values()) {
                        int nx = x + d.getStepX();
                        int ny = y + d.getStepY();
                        int nz = z + d.getStepZ();
                        if (rotated.inside(nx, ny, nz) && !rotated.get(nx, ny, nz).isAir()) {
                            continue;
                        }
                        requireAir(level, cursor.set(origin.getX() + nx, origin.getY() + ny, origin.getZ() + nz), "a folga de 1 bloco de ar em volta da nave");
                    }
                }
            }
        }
    }

    private static void requireAir(ServerLevel level, BlockPos pos, String what) throws ShipConversionException {
        if (!level.isInsideBuildHeight(pos)) {
            throw new ShipConversionException("Sem volume livre para ancorar: " + what + " sai da altura de construção em " + pos.toShortString() + ".");
        }
        if (!level.isLoaded(pos)) {
            throw new ShipConversionException("Sem volume livre para ancorar: chunk não carregado em " + pos.toShortString() + ".");
        }
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) {
            throw new ShipConversionException("Sem volume livre para ancorar: " + what + " encontra " + ShipBlockKeys.blockId(state) + " em " + pos.toShortString()
                    + ".");
        }
    }

    // ---------------------------------------------------------------- desancorar

    /**
     * Desancora: monta a nave de novo pela ponte, sem exigir a plataforma (a busca e a remoção da fase 2a-i), tira o registro da âncora, põe quem estava
     * em pé na caixa no convés na mesma posição do mundo e senta {@code pilot} (pode ser null: o comando de dev deixa todos em pé). Lança
     * {@link RocketException} sem tocar no mundo se a montagem recusar.
     */
    public static RocketEntity unanchor(ServerLevel level, Anchor anchor, @Nullable ServerPlayer pilot) throws RocketException {
        AABB box = anchor.box().inflate(1.0);
        List<ServerPlayer> standing = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player != pilot && !player.isPassenger() && !player.isSpectator() && !DeckCrew.isCrew(player) && player.getBoundingBox().intersects(box)) {
                standing.add(player);
            }
        }
        RocketEntity ship = ShipAssembler.assemble(level, anchor.cockpit(), pilot, true);
        get(level.getServer()).remove(anchor.id());
        int boarded = 0;
        for (ServerPlayer player : standing) {
            if (DeckCrew.boardAtWorld(player, ship)) {
                boarded++;
            }
        }
        if (pilot != null) {
            pilot.startRiding(ship, true, true);
        }
        SpaceHorizons.LOGGER.info("[anchor] âncora {} desfeita: nave {} com {} blocos de volta a entidade em {}, {} no convés, piloto {}", anchor.id(),
                ship.getUUID(), ship.portrait().blockCount(), ship.position(), boarded, pilot == null ? "nenhum" : pilot.getPlainTextName());
        return ship;
    }

    // ---------------------------------------------------------------- servidor

    /** O servidor abriu o mundo: carrega as âncoras (o índice do caminho quente precisa delas antes do primeiro tick de bloco). */
    public static void onServerStarted(MinecraftServer server) {
        get(server);
    }

    /** O servidor parou: esquece a acomodação e o índice (o mundo seguinte, no mesmo processo, carrega o dele). */
    public static void reset() {
        SETTLING.clear();
        index = List.of();
    }
}
