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
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.PortraitGrid;
import com.spacehorizons.rocket.RocketAttitude;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketLanding;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.ship.block.AntigravityGeneratorBlock;
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
import net.neoforged.neoforge.network.PacketDistributor;

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

    /** O resultado de {@link #evaluate}: o estado, a mensagem (pt-BR) do impedimento, os blocos que impedem e, se pronto, a grade onde ancorar. */
    public record Evaluation(AnchorStatus status, String message, List<BlockPos> blockers, int turns, @Nullable RocketPortrait rotated, @Nullable BlockPos origin) {
        static Evaluation refused(AnchorStatus status, String message) {
            return new Evaluation(status, message, List.of(), 0, null, null);
        }
    }

    /**
     * Confere se a nave pode ancorar agora, sem tocar no mundo: nave (montada pela ponte), em pé, parada, fora do estaleiro e do espaço, com gerador
     * carregado (se {@code needGenerator}) e volume livre no ar. O primeiro impedimento sai em {@link Evaluation#status}; o volume livre só é varrido se
     * o resto passar.
     */
    public static Evaluation evaluate(ServerLevel level, RocketEntity rocket, boolean needGenerator) {
        RocketPortrait portrait = rocket.portrait();
        if (!rocket.isShip()) {
            return Evaluation.refused(AnchorStatus.NOT_SHIP, "Só naves (montadas pela ponte) ancoram no ar; um foguete pousa.");
        }
        if (portrait.isEmpty()) {
            return Evaluation.refused(AnchorStatus.NOT_SHIP, "A nave não tem blocos.");
        }
        if (ShipYard.is(level) || SpaceDimension.is(level) || rocket.isVirtual()) {
            return Evaluation.refused(AnchorStatus.NOT_SHIP, "Não há onde ancorar aqui (estaleiro ou espaço): a âncora é dentro da dimensão de um corpo.");
        }
        boolean busy = SETTLING.containsKey(rocket.getUUID()) || ShipConversions.isFrozen(rocket);
        double tilt = rocket.tiltDegrees();
        // a posição antiga só vale depois do primeiro tick (uma entidade recém-criada tem a antiga na origem)
        double moved = rocket.tickCount > 1 ? rocket.position().distanceTo(rocket.oldPosition()) : 0.0;
        double speed = Math.max(moved, rocket.getDeltaMovement().length());
        int[] generators = needGenerator ? AntigravityGeneratorBlock.count(portrait) : new int[2];
        int turns = PortraitGrid.quarterTurnsFromYaw(rocket.yawDegrees());
        Object[] room = new Object[3];
        AnchorStatus status = AnchorStatus.classify(true, busy, rocket.flight().mode() == FlightMode.PARACHUTE, speed, tilt, needGenerator, generators[0],
                generators[1], () -> {
                    RocketPortrait rotated = portrait.rotated(turns);
                    BlockPos origin = RocketLanding.originFor(rocket.position(), rotated.sizeX, rotated.sizeY, rotated.sizeZ);
                    List<Blocker> found = blockers(level, rotated, origin, AnchorStatusPayload.MAX_BLOCKERS);
                    room[0] = rotated;
                    room[1] = origin;
                    room[2] = found;
                    return found.isEmpty();
                });
        String message = switch (status) {
            case BUSY -> "Esta nave já está ancorando ou numa conversão.";
            case PARACHUTE -> "Não dá para ancorar com o paraquedas aberto.";
            case TOO_TILTED -> String.format(java.util.Locale.ROOT, "A nave está inclinada demais para ancorar (%.0f graus; máximo %.0f).", tilt, MAX_TILT_DEGREES);
            case TOO_FAST -> String.format(java.util.Locale.ROOT, "A nave precisa estar parada para ancorar (%.1f b/t; máximo %.1f).", speed, MAX_SPEED);
            case NO_GENERATOR -> "A nave precisa de um gerador de antigravidade para ancorar.";
            case NO_ENERGY -> "O gerador de antigravidade está sem carga (recarrega com a nave ancorada ou pousada).";
            case NO_ROOM -> {
                @SuppressWarnings("unchecked")
                List<Blocker> found = (List<Blocker>) room[2];
                yield found.get(0).message();
            }
            default -> "";
        };
        if (status == AnchorStatus.READY) {
            return new Evaluation(status, message, List.of(), turns, (RocketPortrait) room[0], (BlockPos) room[1]);
        }
        if (status == AnchorStatus.NO_ROOM) {
            @SuppressWarnings("unchecked")
            List<Blocker> found = (List<Blocker>) room[2];
            return new Evaluation(status, message, found.stream().map(Blocker::pos).toList(), turns, null, null);
        }
        return Evaluation.refused(status, message);
    }

    /**
     * Pede a âncora da nave (comando de dev: não exige gerador; ver {@link #request(ServerLevel, RocketEntity, boolean)}). Lança
     * {@link ShipConversionException} sem tocar no mundo se algo recusar.
     */
    public static void request(ServerLevel level, RocketEntity rocket) throws ShipConversionException {
        request(level, rocket, false);
    }

    /**
     * Pede a âncora da nave: confere ({@link #evaluate}) e começa a acomodação; a conversão segue em lotes nos ticks seguintes. Lança
     * {@link ShipConversionException} sem tocar no mundo se algo recusar. Com {@code needGenerator} (a tecla do piloto) exige um gerador de
     * antigravidade carregado; a conversão gasta a carga de todos os geradores ({@code AnchorJob}).
     */
    public static void request(ServerLevel level, RocketEntity rocket, boolean needGenerator) throws ShipConversionException {
        Evaluation e = evaluate(level, rocket, needGenerator);
        if (e.status() != AnchorStatus.READY) {
            throw new ShipConversionException(e.message());
        }
        int turns = e.turns();
        RocketPortrait rotated = e.rotated();
        BlockPos origin = e.origin();

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

    /** De quanto em quanto tempo (ticks) o piloto recebe o estado da âncora para o painel. */
    public static final int STATUS_INTERVAL_TICKS = 20;

    /**
     * Manda ao piloto de uma nave no planeta o estado da âncora para o painel, a cada {@link #STATUS_INTERVAL_TICKS}. Chamado pelo controlador de voo
     * junto da telemetria; só naves (o foguete comum não ancora) fora do estaleiro e do espaço.
     */
    public static void tickPilot(RocketEntity rocket, @Nullable ServerPlayer pilot, long now) {
        if (pilot == null || !rocket.isShip() || now % STATUS_INTERVAL_TICKS != 0 || !(rocket.level() instanceof ServerLevel level)) {
            return;
        }
        Evaluation e = evaluate(level, rocket, true);
        if (e.status() != AnchorStatus.NOT_SHIP) {
            PacketDistributor.sendToPlayer(pilot, new AnchorStatusPayload(e.status(), e.blockers(), false));
        }
    }

    /**
     * A tecla de ancorar do piloto (pacote do cliente): confere e ancora, ou devolve ao piloto o estado e os blocos que impedem. Só vale de quem
     * pilota (controla) a nave.
     */
    public static void onPilotRequest(ServerPlayer player) {
        if (!(player.getVehicle() instanceof RocketEntity rocket) || rocket.getControllingPassenger() != player || !(player.level() instanceof ServerLevel level)) {
            PacketDistributor.sendToPlayer(player, new AnchorStatusPayload(AnchorStatus.NOT_SHIP, List.of(), true));
            return;
        }
        Evaluation e = evaluate(level, rocket, true);
        if (e.status() != AnchorStatus.READY) {
            SpaceHorizons.LOGGER.info("[anchor] {} pediu a âncora e foi recusado ({}): {}", player.getPlainTextName(), e.status(), e.message());
            PacketDistributor.sendToPlayer(player, new AnchorStatusPayload(e.status(), e.blockers(), true));
            return;
        }
        try {
            request(level, rocket, true);
            PacketDistributor.sendToPlayer(player, new AnchorStatusPayload(AnchorStatus.BUSY, List.of(), false));
        } catch (ShipConversionException ex) {
            SpaceHorizons.LOGGER.info("[anchor] {} pediu a âncora e foi recusado: {}", player.getPlainTextName(), ex.getMessage());
            player.sendSystemMessage(Component.literal(ex.getMessage()));
        }
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
        if (!index.isEmpty() && server.getTickCount() % PRUNE_INTERVAL_TICKS == 0) {
            pruneBroken(server);
        }
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

    /** Um bloco que impede a âncora (na caixa da nave ou na folga em volta dela) e o porquê. */
    public record Blocker(BlockPos pos, String message) {
    }

    /**
     * O volume no ar está livre? Cada bloco da nave cai em ar (carregado, dentro da altura de construção) e cada vizinho por face que não é da nave
     * também é ar: a nave não encosta em nada (a montagem de volta levaria o vizinho junto, e não substitui nada como o pouso faz com grama).
     */
    static void preCheck(ServerLevel level, RocketPortrait rotated, BlockPos origin) throws ShipConversionException {
        List<Blocker> found = blockers(level, rotated, origin, 1);
        if (!found.isEmpty()) {
            throw new ShipConversionException(found.get(0).message());
        }
    }

    /** Os primeiros {@code max} blocos que impedem a âncora (sem repetir posição), na ordem da varredura; vazio se o volume está livre. */
    static List<Blocker> blockers(ServerLevel level, RocketPortrait rotated, BlockPos origin, int max) {
        List<Blocker> out = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = 0; y < rotated.sizeY; y++) {
            for (int z = 0; z < rotated.sizeZ; z++) {
                for (int x = 0; x < rotated.sizeX; x++) {
                    if (rotated.get(x, y, z).isAir()) {
                        continue;
                    }
                    if (!blockerAt(level, cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z), "o volume da nave", seen, out, max)) {
                        return out;
                    }
                    for (Direction d : Direction.values()) {
                        int nx = x + d.getStepX();
                        int ny = y + d.getStepY();
                        int nz = z + d.getStepZ();
                        if (rotated.inside(nx, ny, nz) && !rotated.get(nx, ny, nz).isAir()) {
                            continue;
                        }
                        if (!blockerAt(level, cursor.set(origin.getX() + nx, origin.getY() + ny, origin.getZ() + nz), "a folga de 1 bloco de ar em volta da nave", seen,
                                out, max)) {
                            return out;
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Confere uma posição que tem de ser ar; se impede, anota e devolve false quando já há {@code max} anotados (a varredura para). */
    private static boolean blockerAt(ServerLevel level, BlockPos pos, String what, java.util.Set<Long> seen, List<Blocker> out, int max) {
        String message = null;
        if (!level.isInsideBuildHeight(pos)) {
            message = "Sem volume livre para ancorar: " + what + " sai da altura de construção em " + pos.toShortString() + ".";
        } else if (!level.isLoaded(pos)) {
            message = "Sem volume livre para ancorar: chunk não carregado em " + pos.toShortString() + ".";
        } else {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                message = "Sem volume livre para ancorar: " + what + " encontra " + ShipBlockKeys.blockId(state) + " em " + pos.toShortString() + ".";
            }
        }
        if (message != null && seen.add(pos.asLong())) {
            out.add(new Blocker(pos.immutable(), message));
        }
        return out.size() < max;
    }

    // ---------------------------------------------------------------- limites do S6 (fase 2f)

    /** De quanto em quanto tempo (ticks) confere se as naves ancoradas ainda têm a ponte. */
    private static final int PRUNE_INTERVAL_TICKS = 20;

    /**
     * Esquece a âncora de uma nave que o jogador quebrou: a ponte registrada deixou de ser a ponte (a nave não pode mais ser montada, e a caixa
     * ficaria protegida contra queda de areia e fluido para sempre). Só confere ponte em chunk carregado.
     */
    static void pruneBroken(MinecraftServer server) {
        for (Anchor anchor : List.copyOf(index)) {
            ServerLevel level = server.getLevel(anchor.levelKey());
            if (level == null || !level.isLoaded(anchor.cockpit())) {
                continue;
            }
            if (!level.getBlockState(anchor.cockpit()).is(ModBlocks.SHIP_BRIDGE.get())) {
                get(server).remove(anchor.id());
                SpaceHorizons.LOGGER.info("[anchor] âncora {} esquecida: a ponte em {} foi quebrada", anchor.id(), anchor.cockpit().toShortString());
            }
        }
    }

    /**
     * A nave (já girada ao quarto de volta) está parada no ar, sem chão nenhum sob a base? Nenhuma coluna da base tem bloco na linha logo abaixo. É o
     * caso do pouso antigo (clique agachado na entidade pousada no ar), que precisa registrar a âncora.
     */
    public static boolean isHovering(ServerLevel level, RocketPortrait rotated, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int z = 0; z < rotated.sizeZ; z++) {
            for (int x = 0; x < rotated.sizeX; x++) {
                cursor.set(origin.getX() + x, origin.getY() - 1, origin.getZ() + z);
                if (level.isLoaded(cursor) && !level.getBlockState(cursor).isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Registra a âncora de uma nave que o pouso antigo ({@code RocketLanding.land}) pôs em blocos no ar, para a caixa dela ser protegida e a ponte desancorar. */
    public static void registerAfterAirLanding(ServerLevel level, RocketPortrait rotated, BlockPos origin, int quarterTurns) {
        BlockPos cockpit = origin.offset(rotated.cockpit());
        Anchor anchor = get(level.getServer()).add(level, origin, rotated.sizeX, rotated.sizeY, rotated.sizeZ, cockpit, quarterTurns, level.getGameTime());
        SpaceHorizons.LOGGER.info("[anchor] nave pousada no ar pelo clique: âncora {} registrada em {} ({}x{}x{})", anchor.id(), origin.toShortString(), rotated.sizeX,
                rotated.sizeY, rotated.sizeZ);
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
