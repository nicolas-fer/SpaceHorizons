package com.spacehorizons.client.devtest;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.RocketLanding;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.ship.ConversionReport;
import com.spacehorizons.ship.ShipConversions;
import com.spacehorizons.ship.ShipRecord;
import com.spacehorizons.ship.ShipRegistry;
import com.spacehorizons.ship.ShipTestRocket;
import com.spacehorizons.ship.ShipYard;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Só em dev (autoteste, cenário {@code shipconvert}, spike S3): o foguete grande gerado por código, as conferências INDEPENDENTES do
 * romaneio da conversão (contagem de itens por Container, comparação célula a célula com o retrato original, combustível dos tanques)
 * e o pouso no chão. O roteiro fica em {@code AutoTest.shipConvertScenario}; os PASSOU/FALHOU usam {@link ShipDevtest#check}.
 */
final class ShipConvertDevtest {
    private static final String TAG = "[AUTOTEST] ";

    /** O foguete de teste em uso (para as conferências) e onde ele foi posto. */
    static ShipTestRocket.Built current;
    static ShipTestRocket.Spec currentSpec;
    static UUID rocketUuid;
    static Vec3 rocketCenter;
    /** Canto mínimo onde o foguete pousou como blocos (depois de {@link #land}). */
    static BlockPos landedOrigin;

    private ShipConvertDevtest() {
    }

    // ---- foguete gerado ----

    /** Gera o foguete de ~{@code blocks} blocos e o põe na frente do jogador (pousado, o fundo na altura dos pés). */
    static void spawn(Minecraft mc, int blocks) {
        ShipDevtest.onServer(mc, server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) player.level();
            long t0 = System.nanoTime();
            currentSpec = ShipTestRocket.preset(blocks);
            current = ShipTestRocket.build(level.registryAccess(), currentSpec);
            double gen = (System.nanoTime() - t0) / 1.0e6;
            rocketCenter = new Vec3(Math.floor(player.getX()) + 14 + currentSpec.sx() / 2.0, Math.floor(player.getY()) + currentSpec.sy() / 2.0, Math.floor(player.getZ()) + 0.0);
            rocketCenter = new Vec3(Math.floor(rocketCenter.x) + (currentSpec.sx() % 2 == 0 ? 0.0 : 0.5), Math.floor(rocketCenter.y) + (currentSpec.sy() % 2 == 0 ? 0.0 : 0.5),
                    Math.floor(rocketCenter.z) + (currentSpec.sz() % 2 == 0 ? 0.0 : 0.5));
            RocketEntity rocket = ShipTestRocket.spawn(level, rocketCenter, current);
            rocketUuid = rocket.getUUID();
            SpaceHorizons.LOGGER.info(TAG + "SHIPCONV foguete gerado: {} blocos ({}x{}x{}), {} baús, {} itens esperados, {} mB nos tanques (entidade {}), pacote do retrato {} bytes, "
                    + "gerado em {} ms, centro {}", current.portrait().blockCount(), currentSpec.sx(), currentSpec.sy(), currentSpec.sz(), current.chests(),
                    current.expectedTotalItems(), current.tankFuelMb(), rocket.fuelMb(), RocketPortrait.encodedSize(current.portrait()), String.format("%.0f", gen), rocketCenter);
            return null;
        });
    }

    static int packetBytes() {
        return RocketPortrait.encodedSize(current.portrait());
    }

    /** Blocos do retrato do foguete que o CLIENTE recebeu (-1 se a entidade não chegou). */
    static int clientBlocks(Minecraft mc) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof RocketEntity r && r.getUUID().equals(rocketUuid)) {
                return r.portrait().blockCount();
            }
        }
        return -1;
    }

    static Vec3 clientRocketPos(Minecraft mc) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof RocketEntity r && r.getUUID().equals(rocketUuid)) {
                return r.position();
            }
        }
        return null;
    }

    static void seatPlayer(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            Entity rocket = ((ServerLevel) player.level()).getEntity(rocketUuid);
            if (rocket != null) {
                player.startRiding(rocket);
            }
            return null;
        });
    }

    static String playerState(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            return String.format(java.util.Locale.ROOT, "dim=%s pos=(%.1f, %.1f, %.1f) vehicle=%s", p.level().dimension().identifier(), p.getX(), p.getY(), p.getZ(),
                    p.getVehicle() == null ? "nenhum" : p.getVehicle().getType().builtInRegistryHolder().key().identifier().getPath());
        });
    }

    static boolean playerRidesRocket(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            return p.getVehicle() instanceof RocketEntity r && r.getUUID().equals(rocketUuid);
        });
    }

    static void dismountPlayer(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            server.getPlayerList().getPlayers().get(0).stopRiding();
            return null;
        });
    }

    // ---- estado da conversão ----

    static void setMaxPacket(Minecraft mc, int bytes) {
        ShipDevtest.onServer(mc, server -> {
            ShipConversions.setMaxPacketBytes(bytes);
            return null;
        });
    }

    static boolean busy(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> ShipConversions.busy());
    }

    static ConversionReport lastReport(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> {
            var reports = ShipConversions.reports();
            return reports.isEmpty() ? null : reports.get(reports.size() - 1);
        });
    }

    /** O id da primeira (e única) nave do registro, ou 0. */
    static int firstShipId(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> ShipRegistry.get(server).all().stream().mapToInt(ShipRecord::id).min().orElse(0));
    }

    static String shipPhase(Minecraft mc, int id) {
        return ShipDevtest.onServer(mc, server -> {
            ShipRecord s = ShipRegistry.get(server).byId(id);
            return s == null ? "ausente" : s.phase() + (s.snapshot().isPresent() ? "+snapshot" : "");
        });
    }

    static boolean rocketEntityExists(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(rocketUuid) != null) {
                    return true;
                }
            }
            return false;
        });
    }

    /** O foguete (entidade) no servidor: {combustível piso, blocos do retrato, modo}, ou null. */
    static String rocketInfo(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(rocketUuid) instanceof RocketEntity r) {
                    return String.format(java.util.Locale.ROOT, "dim=%s pos=%s blocos=%d combustivel=%.2f modo=%s", level.dimension().identifier(), r.position(),
                            r.portrait().blockCount(), r.fuelMb(), r.flightMode());
                }
            }
            return null;
        });
    }

    static Vec3 serverRocketPos(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(rocketUuid) instanceof RocketEntity r) {
                    return r.position();
                }
            }
            return null;
        });
    }

    static double serverRocketFuel(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(rocketUuid) instanceof RocketEntity r) {
                    return r.fuelMb();
                }
            }
            return -1.0;
        });
    }

    // ---- conferências independentes (no estaleiro) ----

    private static Map<String, Integer> countContainers(ServerLevel level, int x0, int y0, int z0, int sx, int sy, int sz) {
        Map<String, Integer> out = new TreeMap<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = y0; y < y0 + sy; y++) {
            for (int z = z0; z < z0 + sz; z++) {
                for (int x = x0; x < x0 + sx; x++) {
                    BlockState state = level.getBlockState(p.set(x, y, z));
                    if (state.hasBlockEntity()) {
                        BlockEntity be = level.getBlockEntity(p);
                        if (be instanceof Container c && !(be instanceof AbstractFurnaceBlockEntity)) {
                            for (int i = 0; i < c.getContainerSize(); i++) {
                                ItemStack stack = c.getItem(i);
                                if (!stack.isEmpty()) {
                                    out.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
                                }
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    private static int fuelOfTanks(ServerLevel level, int x0, int y0, int z0, int sx, int sy, int sz) {
        int total = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = y0; y < y0 + sy; y++) {
            for (int z = z0; z < z0 + sz; z++) {
                for (int x = x0; x < x0 + sx; x++) {
                    if (level.getBlockState(p.set(x, y, z)).hasBlockEntity() && level.getBlockEntity(p) instanceof RocketFuelTankBlockEntity tank) {
                        total += tank.fuelMb();
                    }
                }
            }
        }
        return total;
    }

    /** Células cujo estado difere do retrato original (ar incluído) numa caixa com o canto em (x0, y0, z0); -1 se não há retrato. */
    private static int stateMismatches(ServerLevel level, int x0, int y0, int z0) {
        if (current == null) {
            return -1;
        }
        RocketPortrait portrait = current.portrait();
        int bad = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = 0; y < portrait.sizeY; y++) {
            for (int z = 0; z < portrait.sizeZ; z++) {
                for (int x = 0; x < portrait.sizeX; x++) {
                    if (!level.getBlockState(p.set(x0 + x, y0 + y, z0 + z)).equals(portrait.get(x, y, z))) {
                        bad++;
                    }
                }
            }
        }
        return bad;
    }

    /** Diferença entre os itens esperados (calculados na geração) e os contados nos inventários da caixa; "" se iguais. */
    private static String itemDiff(Map<String, Integer> actual) {
        StringBuilder sb = new StringBuilder();
        java.util.TreeSet<String> keys = new java.util.TreeSet<>(current.expectedItems().keySet());
        keys.addAll(actual.keySet());
        for (String k : keys) {
            int a = current.expectedItems().getOrDefault(k, 0);
            int b = actual.getOrDefault(k, 0);
            if (a != b) {
                sb.append(k).append(' ').append(a).append("->").append(b).append("; ");
            }
        }
        return sb.toString();
    }

    /** Estado da nave no estaleiro: {diferença de itens ("" = exato), células diferentes do retrato, combustível dos tanques}. */
    static Object[] yardCheck(Minecraft mc, int shipId) {
        return ShipDevtest.onServer(mc, server -> {
            ServerLevel yard = ShipYard.level(server);
            ShipRecord ship = ShipRegistry.get(server).byId(shipId);
            if (ship == null) {
                return new Object[] {"nave ausente", -1, -1};
            }
            ShipYard.Box b = ship.box();
            Map<String, Integer> items = countContainers(yard, b.minX(), b.minY(), b.minZ(), b.sizeX(), b.sizeY(), b.sizeZ());
            return new Object[] {itemDiff(items), stateMismatches(yard, b.minX(), b.minY(), b.minZ()), fuelOfTanks(yard, b.minX(), b.minY(), b.minZ(), b.sizeX(), b.sizeY(), b.sizeZ())};
        });
    }

    /** Blocos que não são ar na caixa de uma nave (célula liberada = 0). Carrega os chunks. */
    static int nonAirInBox(Minecraft mc, ShipYard.Box box) {
        return ShipDevtest.onServer(mc, server -> {
            ServerLevel yard = ShipYard.level(server);
            int n = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        if (!yard.getBlockState(p.set(x, y, z)).isAir()) {
                            n++;
                        }
                    }
                }
            }
            return n;
        });
    }

    static ShipYard.Box boxOf(Minecraft mc, int shipId) {
        return ShipDevtest.onServer(mc, server -> ShipRegistry.get(server).byId(shipId).box());
    }

    /** O baú mais perto de {@code from} dentro da caixa: {x, y, z, total de itens no servidor} ou null. */
    static int[] nearestChest(Minecraft mc, int shipId, Vec3 from) {
        return ShipDevtest.onServer(mc, server -> {
            ServerLevel yard = ShipYard.level(server);
            ShipYard.Box b = ShipRegistry.get(server).byId(shipId).box();
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    for (int x = b.minX(); x <= b.maxX(); x++) {
                        if (yard.getBlockState(p.set(x, y, z)).is(Blocks.CHEST)) {
                            double d = from.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
                            if (d < bestDist) {
                                bestDist = d;
                                best = p.immutable();
                            }
                        }
                    }
                }
            }
            if (best == null || !(yard.getBlockEntity(best) instanceof Container c)) {
                return null;
            }
            int total = 0;
            for (int i = 0; i < c.getContainerSize(); i++) {
                total += c.getItem(i).getCount();
            }
            return new int[] {best.getX(), best.getY(), best.getZ(), total};
        });
    }

    // ---- pouso no chão (depois de voltar a entidade) ----

    /** Desmonta o foguete no chão com {@link RocketLanding#land}; devolve a mensagem de erro ou "" se pousou. */
    static String land(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(rocketUuid) instanceof RocketEntity rocket) {
                    landedOrigin = RocketLanding.originFor(rocket.position(), rocket.portrait().sizeX, rocket.portrait().sizeY, rocket.portrait().sizeZ);
                    try {
                        RocketLanding.land(level, rocket);
                        return "";
                    } catch (com.spacehorizons.rocket.RocketException e) {
                        return "RocketException " + e.getMessage();
                    }
                }
            }
            return "sem entidade";
        });
    }

    /** Depois de pousar: {diferença de itens, células diferentes do retrato, combustível dos tanques}. */
    static Object[] landedCheck(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> {
            ServerLevel level = server.overworld();
            BlockPos o = landedOrigin;
            RocketPortrait p = current.portrait();
            Map<String, Integer> items = countContainers(level, o.getX(), o.getY(), o.getZ(), p.sizeX, p.sizeY, p.sizeZ);
            return new Object[] {itemDiff(items), stateMismatches(level, o.getX(), o.getY(), o.getZ()), fuelOfTanks(level, o.getX(), o.getY(), o.getZ(), p.sizeX, p.sizeY, p.sizeZ)};
        });
    }

    /** Apaga os blocos do foguete pousado (para o próximo teste usar o mesmo lugar). */
    static void clearLanded(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            ServerLevel level = server.overworld();
            RocketPortrait p = current.portrait();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = 0; y < p.sizeY; y++) {
                for (int z = 0; z < p.sizeZ; z++) {
                    for (int x = 0; x < p.sizeX; x++) {
                        pos.set(landedOrigin.getX() + x, landedOrigin.getY() + y, landedOrigin.getZ() + z);
                        if (!level.getBlockState(pos).isAir()) {
                            level.removeBlockEntity(pos);
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
                        }
                    }
                }
            }
            return null;
        });
    }

    /** Remove o foguete (entidade) gerado, se ainda existir (limpeza entre os testes). */
    static void discardRocket(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(rocketUuid);
                if (e != null) {
                    e.discard();
                }
            }
            return null;
        });
    }

    /** Descreve um relatório para o log do teste. */
    static void logReport(String label, ConversionReport r) {
        SpaceHorizons.LOGGER.info(TAG + "SHIPCONV RELATORIO {}: {}", label, r == null ? "(sem relatório)" : r.line());
    }

    static AABB yardAabb(ShipYard.Box b) {
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
    }
}
