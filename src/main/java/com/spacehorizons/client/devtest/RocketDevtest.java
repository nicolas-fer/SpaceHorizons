package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.rocket.RocketClient;
import com.spacehorizons.client.rocket.RocketTankHud;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModDataComponents;
import com.spacehorizons.rocket.PortraitGrid;
import com.spacehorizons.rocket.RocketAssembler;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.RocketFuelTankItem;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Verificações do cenário {@code rocket} do autoteste (protótipo 3a-1: montar, virar entidade, ser visto, sentar e pousar
 * de volta a blocos). Tudo o que precisa do servidor roda no servidor integrado e escreve no log com o prefixo
 * {@code [AUTOTEST] ROCKET}, para eu conferir os números depois. O foguete de teste fica sobre uma plataforma 3x3 no ar.
 */
final class RocketDevtest {
    private static final String TAG = "[AUTOTEST] ROCKET ";
    /** Centro da plataforma 3x3 (no ar, bem acima do terreno do spawn: só encosta nela). */
    static final BlockPos BASE = new BlockPos(0, 150, 0);

    /** Os blocos do foguete de teste: deslocamento a partir de BASE e o bloco (como no comando setblock). */
    static final Map<BlockPos, String> ROCKET_BLOCKS = new LinkedHashMap<>();

    static {
        ROCKET_BLOCKS.put(new BlockPos(0, 1, 0), "spacehorizons:rocket_engine");
        ROCKET_BLOCKS.put(new BlockPos(-1, 1, 0), "spacehorizons:rocket_landing_leg");
        ROCKET_BLOCKS.put(new BlockPos(1, 1, 0), "spacehorizons:rocket_landing_leg");
        ROCKET_BLOCKS.put(new BlockPos(0, 2, 0), "spacehorizons:rocket_fuel_tank");
        ROCKET_BLOCKS.put(new BlockPos(-1, 2, 0), "minecraft:furnace[facing=north]");
        ROCKET_BLOCKS.put(new BlockPos(0, 3, 0), "spacehorizons:rocket_hull");
        ROCKET_BLOCKS.put(new BlockPos(1, 3, 0), "spacehorizons:rocket_hull");
        ROCKET_BLOCKS.put(new BlockPos(-1, 3, 0), "minecraft:chest[facing=north]");
        ROCKET_BLOCKS.put(new BlockPos(0, 4, 0), "spacehorizons:rocket_cockpit[facing=north]");
        ROCKET_BLOCKS.put(new BlockPos(0, 5, 0), "spacehorizons:rocket_parachute");
    }

    /** Estados e NBT de block entity de cada bloco do foguete antes de montar (para comparar depois do pouso). */
    private static final Map<BlockPos, BlockState> BEFORE_STATES = new LinkedHashMap<>();
    private static final Map<BlockPos, CompoundTag> BEFORE_NBT = new LinkedHashMap<>();

    private RocketDevtest() {
    }

    static BlockPos at(BlockPos offset) {
        return BASE.offset(offset);
    }

    private interface ServerWork {
        void run(MinecraftServer server, ServerLevel level, ServerPlayer player);
    }

    /** Roda no servidor integrado (thread do servidor) e loga qualquer falha. */
    private static void onServer(Minecraft mc, String label, ServerWork work) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            SpaceHorizons.LOGGER.warn("{}{}: sem servidor integrado", TAG, label);
            return;
        }
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                work.run(server, player.level(), player);
            } catch (RuntimeException e) {
                SpaceHorizons.LOGGER.error(TAG + label + " falhou", e);
            }
        });
    }

    private static CompoundTag blockEntityNbt(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
        tag.remove("x");
        tag.remove("y");
        tag.remove("z");
        return tag;
    }

    // ---------------------------------------------------------------- antes de montar

    /** Guarda o estado de cada bloco e o NBT dos block entities, e loga o foguete montado em blocos. */
    static void snapshot(Minecraft mc) {
        onServer(mc, "snapshot", (server, level, player) -> {
            BEFORE_STATES.clear();
            BEFORE_NBT.clear();
            int solid = 0;
            for (BlockPos offset : ROCKET_BLOCKS.keySet()) {
                BlockPos pos = at(offset);
                BlockState state = level.getBlockState(pos);
                BEFORE_STATES.put(offset, state);
                CompoundTag nbt = blockEntityNbt(level, pos);
                if (nbt != null) {
                    BEFORE_NBT.put(offset, nbt);
                }
                if (!state.isAir()) {
                    solid++;
                }
            }
            int pad = 0;
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (level.getBlockState(BASE.offset(x, 0, z)).is(ModBlocks.LAUNCH_PAD.get())) {
                        pad++;
                    }
                }
            }
            SpaceHorizons.LOGGER.info("{}ANTES: {} de {} blocos do foguete no mundo, {} de 9 blocos da plataforma, block entities guardados={}",
                    TAG, solid, ROCKET_BLOCKS.size(), pad, BEFORE_NBT.keySet().stream().map(BlockPos::toShortString).toList());
            for (Map.Entry<BlockPos, BlockState> e : BEFORE_STATES.entrySet()) {
                SpaceHorizons.LOGGER.info("{}  antes {} = {}", TAG, at(e.getKey()).toShortString(), e.getValue());
            }
        });
    }

    /** Tooltip do tanque cheio e da carga lida pelo HUD (o que o jogador vê ao olhar o bloco). */
    static void tankDisplay(Minecraft mc, String label) {
        Component hud = RocketTankHud.lookedAtText(mc);
        SpaceHorizons.LOGGER.info("{}tanque {}: texto ao olhar='{}' (mira em {})", TAG, label, hud == null ? "nenhum" : hud.getString(),
                mc.hitResult == null ? "nada" : mc.hitResult.getType());
        ItemStack full = RocketFuelTankItem.fullStack();
        SpaceHorizons.LOGGER.info("{}tanque {}: tooltip do item cheio={}", TAG, label, full.getTooltipLines(Item.TooltipContext.of(mc.level),
                mc.player, TooltipFlag.NORMAL).stream().map(Component::getString).toList());
    }

    /** Quebra o tanque com combustível, vê o item que cai (componente) e recoloca com ele (como a refinaria). */
    static void tankBreakAndReplace(Minecraft mc) {
        onServer(mc, "quebra do tanque", (server, level, player) -> {
            BlockPos pos = at(new BlockPos(0, 2, 0));
            BlockState state = level.getBlockState(pos);
            int before = level.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity t ? t.fuelMb() : -1;
            level.destroyBlock(pos, true);
            ItemStack dropped = ItemStack.EMPTY;
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0))) {
                if (e.getItem().is(ModBlocks.ROCKET_FUEL_TANK.get().asItem())) {
                    dropped = e.getItem().copy();
                    e.discard();
                }
            }
            Integer stored = dropped.get(ModDataComponents.ROCKET_TANK_MB.get());
            level.setBlock(pos, state, 3);
            int after = -1;
            if (level.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity placed) {
                placed.applyComponentsFromItemStack(dropped);
                after = placed.fuelMb();
            }
            SpaceHorizons.LOGGER.info("{}QUEBRA do tanque: combustível antes={} mB, item caiu={} componente={} mB, tanque ao recolocar={} mB "
                    + "(esperado igual nos três), tooltip do item caído={}", TAG, before, !dropped.isEmpty(), stored, after,
                    dropped.getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.NORMAL).stream().map(Component::getString).toList());
        });
    }

    // ---------------------------------------------------------------- depois de montar

    /** Os blocos sumiram, a plataforma ficou, e a entidade existe com N blocos (servidor e cliente). */
    static void afterAssemble(Minecraft mc, String label) {
        onServer(mc, "depois de montar", (server, level, player) -> {
            int remaining = 0;
            for (BlockPos offset : ROCKET_BLOCKS.keySet()) {
                if (!level.getBlockState(at(offset)).isAir()) {
                    remaining++;
                }
            }
            int pad = 0;
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (level.getBlockState(BASE.offset(x, 0, z)).is(ModBlocks.LAUNCH_PAD.get())) {
                        pad++;
                    }
                }
            }
            List<RocketEntity> rockets = level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(40.0));
            SpaceHorizons.LOGGER.info("{}{}: blocos que sobraram no mundo={} (esperado 0), plataforma={} de 9 (esperado 9), entidades de foguete={}",
                    TAG, label, remaining, pad, rockets.size());
            for (RocketEntity r : rockets) {
                var p = r.portrait();
                SpaceHorizons.LOGGER.info("{}{}: entidade pos=({}, {}, {}) blocos={} caixa={}x{}x{} cockpit local={} combustível={} mB (sync {}), modo={}, "
                        + "massa seca={} t, empuxo={}, capacidade={} mB, motores={} tanques={} pernas={} paraquedas={}, yaw={}, inclinação={}, "
                        + "hitbox={}, passageiros={}", TAG, label, f(r.getX()), f(r.getY()), f(r.getZ()), p.blockCount(), p.sizeX, p.sizeY,
                        p.sizeZ, p.cockpit().toShortString(), f(r.fuelMb()), r.syncedFuelMb(), r.flightMode(), f(r.stats().dryMassT()),
                        f(r.stats().thrust()), f(r.stats().fuelCapacityMb()), r.stats().engines(), r.stats().tanks(), r.stats().legs(),
                        r.stats().parachutes(), f(r.yawDegrees()), f(r.tiltDegrees()), r.getBoundingBox(), r.getPassengers().size());
            }
        });
    }

    /** O que o CLIENTE tem da entidade: retrato recebido pelo spawn complexo, malhas assadas e o veículo do jogador. */
    static void clientView(Minecraft mc, String label) {
        List<RocketEntity> rockets = mc.level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(60.0));
        SpaceHorizons.LOGGER.info("{}{} (cliente): entidades de foguete={}, malhas no cache={}, jogador montado em={}", TAG, label, rockets.size(),
                RocketClient.meshCount(), mc.player.getVehicle() == null ? "nada" : mc.player.getVehicle().getType());
        for (RocketEntity r : rockets) {
            SpaceHorizons.LOGGER.info("{}{} (cliente): pos=({}, {}, {}) blocos no retrato={} caixa={}x{}x{} combustível sincronizado={} modo={} "
                    + "hitbox={} id do retrato={}", TAG, label, f(r.getX()), f(r.getY()), f(r.getZ()), r.portrait().blockCount(),
                    r.portrait().sizeX, r.portrait().sizeY, r.portrait().sizeZ, r.syncedFuelMb(), r.flightMode(), r.getBoundingBox(),
                    r.portrait().id());
        }
    }

    /** O jogador sentou: onde ele está em relação ao cockpit (servidor) e quem controla. */
    static void seatCheck(Minecraft mc, String label) {
        onServer(mc, "assento", (server, level, player) -> {
            if (player.getVehicle() instanceof RocketEntity r) {
                var seat = r.cockpitWorldPosition();
                SpaceHorizons.LOGGER.info("{}{}: jogador montado no foguete; controla={}, jogador=({}, {}, {}) cockpit=({}, {}, {}) distância={}, "
                        + "foguete=({}, {}, {})", TAG, label, r.getControllingPassenger() == player, f(player.getX()), f(player.getY()),
                        f(player.getZ()), f(seat.x), f(seat.y), f(seat.z), f(player.position().distanceTo(seat)), f(r.getX()), f(r.getY()), f(r.getZ()));
            } else {
                SpaceHorizons.LOGGER.warn("{}{}: o jogador NÃO está montado no foguete (veículo={})", TAG, label, player.getVehicle());
            }
        });
    }

    /** Clique direito de verdade na entidade (senta o piloto). */
    static void clickRocket(Minecraft mc) {
        List<RocketEntity> rockets = mc.level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(60.0));
        if (rockets.isEmpty()) {
            SpaceHorizons.LOGGER.warn("{}clique na entidade: nenhuma entidade de foguete no cliente", TAG);
            return;
        }
        RocketEntity rocket = rockets.get(0);
        var result = mc.gameMode.interact(mc.player, rocket, new EntityHitResult(rocket), InteractionHand.MAIN_HAND);
        SpaceHorizons.LOGGER.info("{}clique na entidade (cliente): resultado={}", TAG, result);
    }

    /** O piloto desembarca pelo caminho vanilla (o mesmo que Shift usa: stopRiding no servidor). */
    static void dismount(Minecraft mc) {
        onServer(mc, "desembarque", (server, level, player) -> {
            var antes = player.position();
            player.stopRiding();
            SpaceHorizons.LOGGER.info("{}DESEMBARQUE: antes=({}, {}, {}) depois=({}, {}, {}) veículo={}", TAG, f(antes.x), f(antes.y), f(antes.z),
                    f(player.getX()), f(player.getY()), f(player.getZ()), player.getVehicle());
        });
    }

    // ---------------------------------------------------------------- pouso

    /** Compara o mundo depois do pouso com o instantâneo de antes de montar (girando a expectativa se houve giro). */
    static void afterLand(Minecraft mc, String label, int quarterTurns) {
        onServer(mc, "depois do pouso", (server, level, player) -> {
            List<RocketEntity> rockets = level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(60.0));
            int sx = 3;
            int sz = 1;
            int turns = Math.floorMod(quarterTurns, 4);
            Rotation rotation = Rotation.values()[turns];
            int sizeX = PortraitGrid.rotatedSizeX(turns, sx, sz);
            int sizeZ = PortraitGrid.rotatedSizeZ(turns, sx, sz);
            // origem esperada: o centro da caixa original (BASE.x + 0,5 ; BASE.z + 0,5) fica o centro da girada
            int originX = (int) Math.round(BASE.getX() + 0.5 - sizeX / 2.0);
            int originZ = (int) Math.round(BASE.getZ() + 0.5 - sizeZ / 2.0);
            int minY = BASE.getY() + 1;
            int equal = 0;
            List<String> diffs = new ArrayList<>();
            for (Map.Entry<BlockPos, BlockState> e : BEFORE_STATES.entrySet()) {
                BlockPos offset = e.getKey();
                int lx = offset.getX() + 1; // a caixa original começa em x = -1
                int lz = offset.getZ();
                int ly = offset.getY() - 1; // e em y = 1
                BlockPos expectedPos = new BlockPos(originX + PortraitGrid.rotatedX(turns, lx, lz, sx, sz), minY + ly,
                        originZ + PortraitGrid.rotatedZ(turns, lx, lz, sx, sz));
                BlockState expected = e.getValue().rotate(rotation);
                BlockState actual = level.getBlockState(expectedPos);
                boolean sameNbt = true;
                CompoundTag before = BEFORE_NBT.get(offset);
                if (before != null) {
                    CompoundTag after = blockEntityNbt(level, expectedPos);
                    sameNbt = before.equals(after);
                    if (!sameNbt) {
                        diffs.add("NBT diferente em " + expectedPos.toShortString() + ": antes=" + before + " depois=" + after);
                    }
                }
                if (expected.equals(actual) && sameNbt) {
                    equal++;
                } else if (!expected.equals(actual)) {
                    diffs.add("estado em " + expectedPos.toShortString() + ": esperado=" + expected + " achei=" + actual);
                }
            }
            int pad = 0;
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (level.getBlockState(BASE.offset(x, 0, z)).is(ModBlocks.LAUNCH_PAD.get())) {
                        pad++;
                    }
                }
            }
            int fuel = -1;
            for (BlockPos offset : ROCKET_BLOCKS.keySet()) {
                if (BEFORE_STATES.get(offset).is(ModBlocks.ROCKET_FUEL_TANK.get())) {
                    int lx = offset.getX() + 1;
                    BlockPos pos = new BlockPos(originX + PortraitGrid.rotatedX(turns, lx, offset.getZ(), sx, sz),
                            minY + offset.getY() - 1, originZ + PortraitGrid.rotatedZ(turns, lx, offset.getZ(), sx, sz));
                    if (level.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity tank) {
                        fuel = tank.fuelMb();
                    }
                }
            }
            SpaceHorizons.LOGGER.info("{}{}: {} de {} blocos IGUAIS ao de antes (giro de {} quarto(s), esperado girado), plataforma={} de 9, "
                    + "entidades de foguete restantes={} (esperado 0), combustível no tanque={} mB", TAG, label, equal, BEFORE_STATES.size(),
                    turns, pad, rockets.size(), fuel);
            for (String d : diffs) {
                SpaceHorizons.LOGGER.warn("{}{}: DIFERENÇA {}", TAG, label, d);
            }
            for (BlockPos offset : ROCKET_BLOCKS.keySet()) {
                int lx = offset.getX() + 1;
                BlockPos pos = new BlockPos(originX + PortraitGrid.rotatedX(turns, lx, offset.getZ(), sx, sz), minY + offset.getY() - 1,
                        originZ + PortraitGrid.rotatedZ(turns, lx, offset.getZ(), sx, sz));
                SpaceHorizons.LOGGER.info("{}  depois {} = {}", TAG, pos.toShortString(), level.getBlockState(pos));
            }
        });
    }

    // ---------------------------------------------------------------- recusa por terreno

    /** Chama a montagem direto (sem jogador) e loga o motivo da recusa e se o mundo continua igual. */
    static void assembleExpectingRefusal(Minecraft mc, String label) {
        onServer(mc, "montagem recusada", (server, level, player) -> {
            int before = countBlocks(level);
            try {
                RocketAssembler.assemble(level, at(new BlockPos(0, 4, 0)), null);
                SpaceHorizons.LOGGER.error("{}{}: a montagem FOI ACEITA (esperada a recusa)", TAG, label);
            } catch (RocketException e) {
                SpaceHorizons.LOGGER.info("{}{}: RECUSADA com motivo={} args={} mensagem='{}'", TAG, label, e.reason(),
                        java.util.Arrays.toString(e.args()), e.component().getString());
            }
            SpaceHorizons.LOGGER.info("{}{}: blocos do foguete no mundo antes={} depois={} (esperado igual), entidades={}", TAG, label, before,
                    countBlocks(level), level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(40.0)).size());
        });
    }

    private static int countBlocks(ServerLevel level) {
        int n = 0;
        for (BlockPos offset : ROCKET_BLOCKS.keySet()) {
            if (!level.getBlockState(at(offset)).isAir()) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- salvar e carregar a entidade

    /** Salva a entidade em NBT e a recarrega num foguete novo: o retrato, o combustível e a atitude têm de voltar iguais. */
    static void saveLoadRoundTrip(Minecraft mc) {
        onServer(mc, "salvar e carregar", (server, level, player) -> {
            List<RocketEntity> rockets = level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(60.0));
            if (rockets.isEmpty()) {
                SpaceHorizons.LOGGER.warn("{}salvar/carregar: nenhum foguete", TAG);
                return;
            }
            RocketEntity original = rockets.get(0);
            net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput
                    .createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
            original.saveWithoutId(output);
            CompoundTag saved = output.buildResult();
            RocketEntity copy = com.spacehorizons.content.ModEntities.ROCKET.get().create(level,
                    net.minecraft.world.entity.EntitySpawnReason.LOAD);
            copy.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                    level.registryAccess(), saved));
            boolean sameBlocks = copy.portrait().blockCount() == original.portrait().blockCount()
                    && copy.portrait().palette().equals(original.portrait().palette())
                    && copy.portrait().grid().equals(original.portrait().grid())
                    && copy.portrait().blockEntities().equals(original.portrait().blockEntities());
            SpaceHorizons.LOGGER.info("{}SALVAR/CARREGAR: bytes de NBT aproximados={}, retrato igual={} (blocos {} -> {}), combustível {} -> {}, "
                    + "atitude yaw {} -> {}, modo {} -> {}, caixa {} -> {}", TAG, saved.toString().length(), sameBlocks,
                    original.portrait().blockCount(), copy.portrait().blockCount(), f(original.fuelMb()), f(copy.fuelMb()),
                    f(original.yawDegrees()), f(copy.yawDegrees()), original.flightMode(), copy.flightMode(),
                    original.getBoundingBox().getSize(), copy.getBoundingBox().getSize());
            // e o pacote de spawn: escreve e lê o retrato
            io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
            com.spacehorizons.rocket.RocketPortrait.write(buf, original.portrait());
            int bytes = buf.readableBytes();
            var lido = com.spacehorizons.rocket.RocketPortrait.read(buf);
            SpaceHorizons.LOGGER.info("{}PACOTE DE SPAWN: {} bytes para {} blocos; retrato lido igual={}", TAG, bytes, original.portrait().blockCount(),
                    lido.grid().equals(original.portrait().grid()) && lido.palette().equals(original.portrait().palette())
                            && lido.blockEntities().equals(original.portrait().blockEntities()));
        });
    }

    private static String f(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /** Pedra encostada no casco do lado de fora (terreno) e remoção dela. */
    static String terrainBlockCommand(boolean place) {
        BlockPos stone = at(new BlockPos(2, 3, 0));
        return "setblock " + stone.getX() + " " + stone.getY() + " " + stone.getZ() + (place ? " minecraft:stone" : " minecraft:air");
    }

    static List<String> buildCommands() {
        List<String> cmds = new ArrayList<>();
        cmds.add("fill " + (BASE.getX() - 1) + " " + BASE.getY() + " " + (BASE.getZ() - 1) + " " + (BASE.getX() + 1) + " " + BASE.getY() + " "
                + (BASE.getZ() + 1) + " spacehorizons:launch_pad");
        for (Map.Entry<BlockPos, String> e : ROCKET_BLOCKS.entrySet()) {
            BlockPos pos = at(e.getKey());
            cmds.add("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + e.getValue());
        }
        BlockPos furnace = at(new BlockPos(-1, 2, 0));
        BlockPos chest = at(new BlockPos(-1, 3, 0));
        cmds.add("item replace block " + furnace.getX() + " " + furnace.getY() + " " + furnace.getZ() + " container.0 with minecraft:coal 5");
        cmds.add("item replace block " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " container.0 with minecraft:diamond 3");
        cmds.add("item replace block " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " container.13 with minecraft:iron_ingot 7");
        return cmds;
    }

    /** Limpa o que sobrar de uma execução anterior (o mundo do autoteste pode ser reaproveitado). */
    static String clearCommand() {
        return "fill " + (BASE.getX() - 4) + " " + (BASE.getY() - 1) + " " + (BASE.getZ() - 4) + " " + (BASE.getX() + 4) + " " + (BASE.getY() + 14) + " "
                + (BASE.getZ() + 4) + " minecraft:air";
    }
}
