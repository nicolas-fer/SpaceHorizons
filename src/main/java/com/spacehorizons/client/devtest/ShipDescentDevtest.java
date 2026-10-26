package com.spacehorizons.client.devtest;

import java.io.IOException;
import java.nio.file.Path;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.PortraitGrid;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.RocketLanding;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.ship.ShipDescent;
import com.spacehorizons.ship.ShipFlight;
import com.spacehorizons.ship.ShipFlightTicker;
import com.spacehorizons.ship.ShipRecord;
import com.spacehorizons.ship.ShipRegistry;
import com.spacehorizons.ship.ShipYard;
import com.spacehorizons.ship.deck.DeckCrew;
import com.spacehorizons.ship.deck.DeckState;
import com.spacehorizons.ship.deck.DeckYardMap;
import com.spacehorizons.space.SpaceClock;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.FrameBridge;
import com.spacehorizons.space.flight.V3;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Autoteste da descida (fase 2b, parte 1): -PshOnly=shipdescent. Depois da circularização do shipascent, a nave virtual é posta a
 * {@link #START_ALTITUDE} blocos sobre o ponto da órbita atual, descendo a {@link #DESCENT_SPEED} b/t na vertical e no máximo
 * {@link #HORIZONTAL_SPEED} b/t na horizontal em relação ao chão (que gira com a Terra; o estado é inercial, como o do voo virtual). Quando a
 * altitude de entrada e o critério do foguete deixam, a nave vira entidade na Terra. A desorbitação real (queima para descer de uma
 * órbita fechada) fica para depois: aqui a descida é forçada pelo estado de teste.
 *
 * <p>A foto da nave no chão ({@link #photo}) é guardada em memória e em {@link #PHOTO_FILE} (NBT do retrato: blocos, entidades de bloco
 * como o baú, mB dos tanques) para a próxima execução comparar com a nave que volta da descida.
 */
final class ShipDescentDevtest {
    static final double START_ALTITUDE = 1_000.0;
    static final double DESCENT_SPEED = 5.0;
    static final double HORIZONTAL_SPEED = 0.0;
    /** Distância máxima, em blocos (horizontal), entre o ponto esperado da entidade e o ponto onde ela apareceu. */
    static final double POSITION_TOLERANCE = 300.0;
    /** Ticks do servidor até desistir da descida. */
    static final int WAIT_TICKS = 4_000;
    static final Path PHOTO_FILE = Path.of("shipdescent-foto.nbt");

    private static RocketPortrait photo;
    private static int shipId = -1;
    private static ShipYard.Box yardBox;
    /** Itens que a nave de teste leva no baú (os mesmos do shipascent). */
    static final int PHOTO_CHEST_ITEMS = ShipAscentDevtest.GROUND_DIAMONDS + ShipAscentDevtest.GROUND_IRON;
    /** Combustível da nave virtual no começo da descida (a subida gastou parte do que a foto do chão tinha). */
    private static double descentFuelMb;
    private static double expectedX;
    private static double expectedY;
    private static double expectedZ;
    /** A posição relativa à nave do tripulante em pé no estaleiro, no começo da descida (a mesma deve valer no convés da entidade). */
    private static org.joml.Vector3d yardLocal;
    private static double minFootRel;
    private static double maxDriftXz;
    private static boolean watching;
    private static boolean armed;
    private static boolean registered;
    private static boolean started;
    private static boolean done;
    private static int ticks;

    private ShipDescentDevtest() {
    }

    /** Depois da montagem: guarda a foto da nave no chão (em memória e no arquivo para a próxima execução). */
    static void photo(Minecraft mc) {
        ShipGroundDevtest.onServer(mc, "foto da nave no chão", (server, level, player) -> {
            RocketEntity s = ShipAscentDevtest.shipEntity();
            if (s == null) {
                ShipAscentDevtest.check("foto: nave montada no chão", false, "sem nave");
                return;
            }
            photo = s.portrait();
            try {
                NbtIo.writeCompressed(photo.save(), PHOTO_FILE);
            } catch (IOException e) {
                SpaceHorizons.LOGGER.warn("[AUTOTEST] SHIPDESCENT foto não gravada em {}: {}", PHOTO_FILE, e.toString());
            }
            ShipAscentDevtest.check("foto: retrato com blocos", photo.blockCount() > 0, "blocos " + photo.blockCount() + ", mB " + photo.fuelInTanksMb());
        });
    }

    /** Depois do sa_done: põe a nave virtual a START_ALTITUDE, descendo, e arma a conferência que roda em todo tick do servidor. */
    static void start(Minecraft mc) {
        ShipGroundDevtest.onServer(mc, "descida", (server, level, player) -> {
            shipId = ShipAscentDevtest.tradeShipId();
            ShipFlightTicker.Live live = shipId < 0 ? null : ShipFlightTicker.live(shipId);
            ShipRecord rec = shipId < 0 ? null : ShipRegistry.get(server).byId(shipId);
            if (live == null || rec == null) {
                ShipAscentDevtest.check("descida: nave virtual da troca existe", false, "id " + shipId);
                done = true;
                FlightDevtest.mark("sd_done");
                return;
            }
            yardBox = rec.box();
            if (ShipAscentDevtest.STANDING) {
                yardLocal = DeckYardMap.toLocal(player.getX(), player.getY(), player.getZ(), yardBox.minX(), yardBox.minY(), yardBox.minZ());
                SpaceHorizons.LOGGER.info("[AUTOTEST] SHIPDESCENT tripulante em pé no estaleiro em ({}, {}, {}) relativos à nave; vida {}", yardLocal.x, yardLocal.y, yardLocal.z,
                        player.getHealth());
            }
            FlightState o = live.state().orbit();
            descentFuelMb = o.fuelMb();
            V3 up = o.r().unit();
            // horizontal: a do chão girando naquele ponto (zero em relação à superfície) mais HORIZONTAL_SPEED no sentido do movimento; o pouso
            // suave pede no máximo 0,5 b/t de horizontal em relação ao chão, e a nave gira devagar para tirar muita horizontal na descida
            V3 r = up.times(o.body().planet().radius() + START_ALTITUDE);
            // com a taxa do relógio do voo: o autoteste pausa o relógio (advance_time false) e aí o chão não gira na física
            V3 groundV = FrameBridge.rotationVelocity(o.body(), SpaceClock.ticks(server.overworld()), live.rate(), r);
            V3 horizontal = o.v().minus(up.times(o.v().dot(up)));
            V3 tangent = horizontal.lengthSquared() > 0.0 && HORIZONTAL_SPEED > 0.0 ? horizontal.unit().times(HORIZONTAL_SPEED) : V3.ZERO;
            FlightState descent = o.withRV(r, groundV.plus(up.times(-DESCENT_SPEED)).plus(tangent));
            // nariz para cima (o eixo do empuxo dos bocais) e sem giro: o teste é da descida e do pouso, não de virar a nave que saiu da
            // circularização com o nariz na horizontal (ela gira devagar e cairia antes de apontar)
            var upright = new org.joml.Quaterniond().rotationTo(0.0, 1.0, 0.0, up.x(), up.y(), up.z());
            ShipFlightTicker.devtestSetState(shipId, new ShipFlight.State(descent, upright, V3.ZERO, live.state().sas(), 0.0));
            var expected = ShipDescent.local(descent, SpaceClock.ticks(server.overworld()), live.rate());
            expectedX = expected.x();
            expectedY = expected.y();
            expectedZ = expected.z();
            armed = true;
            if (!registered) {
                registered = true;
                NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, e -> tick(e.getServer()));
            }
            SpaceHorizons.LOGGER.info("[AUTOTEST] SHIPDESCENT nave {} posta a {} blocos, descendo {} b/t, horizontal {} b/t; ponto esperado ({}, {}, {})",
                    shipId, ShipAscentDevtest.altitudeOf(descent), DESCENT_SPEED, HORIZONTAL_SPEED, expectedX, expectedY, expectedZ);
        });
    }

    /** Todo tick do servidor: espera a nave sair do ShipRegistry e aparecer como entidade na overworld, ou desiste no prazo. */
    private static void tick(MinecraftServer server) {
        if (!armed || done || shipId < 0) {
            return;
        }
        ticks++;
        if (ShipFlightTicker.devtestDescentStarted(shipId)) {
            started = true;
        }
        boolean gone = ShipRegistry.get(server).byId(shipId) == null;
        RocketEntity entity = gone ? findShip(server) : null;
        if (ticks % 20 == 0) {
            int rockets = 0;
            int ships = 0;
            for (var e : server.overworld().getAllEntities()) {
                if (e instanceof RocketEntity r) {
                    rockets++;
                    ships += r.isShip() ? 1 : 0;
                }
            }
            SpaceHorizons.LOGGER.info("[AUTOTEST] SHIPDESCENT espera tick {}: fora do registro {}, foguetes na overworld {}, naves {}", ticks, gone, rockets, ships);
        }
        if (entity != null) {
            finish(server, entity);
        } else if (ticks > WAIT_TICKS) {
            finish(server, null);
        }
    }

    /** Depois da descida: o tripulante entrou no convés da entidade (servidor e cliente). */
    static boolean crewOnEntity(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        boolean serverSide = server != null && DeckCrew.isCrew(server.getPlayerList().getPlayers().get(0));
        return serverSide && com.spacehorizons.client.ship.DeckClient.isAboard() && com.spacehorizons.client.ship.DeckClient.rocket() != null;
    }

    /** O tripulante está na MESMA posição relativa à nave no convés da entidade que tinha no estaleiro; liga o vigia do piso e do desvio até o pouso. */
    static void checkCrewOnEntity(Minecraft mc) {
        ShipGroundDevtest.onServer(mc, "tripulante na entidade", (server, level, player) -> {
            DeckCrew.Member m = DeckCrew.member(player.getUUID());
            ShipAscentDevtest.check("descida: tripulante em pé no convés da entidade", m != null && yardLocal != null, "tripulante " + (m != null));
            if (m == null || yardLocal == null) {
                return;
            }
            DeckState s = m.state();
            double err = s.pos.distance(yardLocal);
            ShipAscentDevtest.check("descida: mesma posição relativa à nave que no estaleiro (< " + ShipAscentDevtest.RELATIVE_TOLERANCE + " bloco)",
                    err < ShipAscentDevtest.RELATIVE_TOLERANCE, "estaleiro (" + f(yardLocal.x) + ", " + f(yardLocal.y) + ", " + f(yardLocal.z) + "), convés (" + f(s.pos.x)
                            + ", " + f(s.pos.y) + ", " + f(s.pos.z) + "), diferença " + f(err));
            ShipAscentDevtest.check("descida: nenhum dano de queda", !ShipAscentDevtest.fallHurt, "vida " + f(player.getHealth()) + ", último dano " + ShipAscentDevtest.lastDamage);
            minFootRel = 0.0;
            maxDriftXz = 0.0;
            watching = true;
            NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, e -> {
                if (!watching) {
                    return;
                }
                ServerPlayer p = e.getServer().getPlayerList().getPlayers().get(0);
                ShipAscentDevtest.watchDamage(p);
                DeckCrew.Member mm = DeckCrew.member(p.getUUID());
                if (mm != null) {
                    DeckState st = mm.state();
                    minFootRel = Math.min(minFootRel, st.pos.y - yardLocal.y);
                    maxDriftXz = Math.max(maxDriftXz, Math.hypot(st.pos.x - yardLocal.x, st.pos.z - yardLocal.z));
                }
            });
        });
    }

    private static String f(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    /** O foguete do jogador: sentado nele ou, em pé, de que é tripulante. */
    private static RocketEntity shipOf(ServerPlayer player) {
        return MissionDevtest.rocketOf(player);
    }

    /** A entidade de nave na overworld mais perto do ponto esperado. */
    private static RocketEntity findShip(MinecraftServer server) {
        // todas as entidades carregadas: a caixa gigante do getEntitiesOfClass não achava a nave (a 43 mil blocos do spawn)
        RocketEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var any : server.overworld().getAllEntities()) {
            if (!(any instanceof RocketEntity e) || !e.isShip()) {
                continue;
            }
            double d = Math.hypot(e.getX() - expectedX, e.getZ() - expectedZ);
            if (d < bestDistance) {
                bestDistance = d;
                best = e;
            }
        }
        return best;
    }

    /** Confere a descida: uma vez, fora do registro, entidade na overworld, piloto sentado, caixa vazia e retrato igual à foto. */
    private static void finish(MinecraftServer server, RocketEntity entity) {
        done = true;
        ShipAscentDevtest.check("descida disparou (uma vez)", started, "começou " + started);
        ShipAscentDevtest.check("nave saiu do ShipRegistry", ShipRegistry.get(server).byId(shipId) == null, "registro " + ShipRegistry.get(server).byId(shipId));
        if (entity == null) {
            ShipAscentDevtest.check("RocketEntity com isShip() na overworld", false, "nenhuma após " + ticks + " ticks");
            FlightDevtest.mark("sd_done");
            return;
        }
        double horizontal = Math.hypot(entity.getX() - expectedX, entity.getZ() - expectedZ);
        ShipAscentDevtest.check("RocketEntity com isShip() na overworld perto do ponto esperado", horizontal <= POSITION_TOLERANCE,
                String.format("desvio horizontal %.1f, altura %.1f (esperada %.1f)", horizontal, entity.getY(), expectedY));
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        if (ShipAscentDevtest.STANDING) {
            // o tripulante entra no convés da entidade um ou dois ticks depois (espera o jogador chegar à dimensão): a conferência é em crewOnEntity
            ShipAscentDevtest.check("descida sem recusa com o tripulante em pé (a nave virou entidade)", player.getVehicle() == null, "veículo=" + player.getVehicle());
        } else {
            ShipAscentDevtest.check("piloto sentado na nave na Terra", player.getVehicle() == entity, "veículo=" + player.getVehicle());
        }
        ShipAscentDevtest.check("caixa do estaleiro sem blocos", yardEmpty(server), "caixa " + yardBox);
        if (photo != null) {
            RocketPortrait after = entity.portrait();
            ShipAscentDevtest.check("retrato: mesma contagem de blocos da foto", after.blockCount() == photo.blockCount(),
                    "foto " + photo.blockCount() + ", depois " + after.blockCount());
            ShipAscentDevtest.check("retrato: mesma grade de blocos", sameGrid(photo, after), "tamanho " + after.sizeX + "x" + after.sizeY + "x" + after.sizeZ);
            ShipAscentDevtest.check("retrato: mesmas entidades de bloco (NBT do baú)", sameBlockEntities(photo, after),
                    "foto " + photo.blockEntities().size() + ", depois " + after.blockEntities().size());
            // a carga é a do começo da descida (a subida gastou parte da foto do chão); o acelerador está em 0, então nada queima no caminho
            ShipAscentDevtest.check("retrato: mB dos tanques (soma) iguais ao combustível da nave no começo da descida",
                    Math.abs(after.fuelInTanksMb() - descentFuelMb) < 1.0, "começo da descida " + descentFuelMb + " mB, depois " + after.fuelInTanksMb()
                            + " mB (no chão eram " + photo.fuelInTanksMb() + ")");
        }
        SpaceHorizons.LOGGER.info("[AUTOTEST] SHIPDESCENT entidade na Terra: {}, altura {}, {} ticks", entity.position(), entity.getY(), ticks);
        FlightDevtest.mark("sd_done");
    }

    /**
     * Depois do pouso pelo piloto da missão (fase EARTH_LANDING): confere a nave pousada inteira, desmonta com {@link RocketLanding#land} e
     * confere os blocos no chão contra a foto do chão (girada como a desmontagem gira), o baú, os mB, nenhum item solto e nenhuma nave fantasma.
     */
    static void landAndCheck(Minecraft mc) {
        ShipGroundDevtest.onServer(mc, "pouso", (server, level, player) -> {
            RocketEntity ship = shipOf(player);
            if (ship == null || !ship.isShip()) {
                ShipAscentDevtest.check("nave pousada com o piloto sentado (ou o tripulante em pé)", false, "veículo " + player.getVehicle());
                FlightDevtest.mark("sd_landed");
                return;
            }
            ServerLevel ground = (ServerLevel) ship.level();
            ShipAscentDevtest.check("pouso suave: nave em LANDED, inteira", ship.flight().mode() == FlightMode.LANDED && ship.portrait().blockCount() == photo.blockCount(),
                    "modo " + ship.flight().mode() + ", blocos " + ship.portrait().blockCount() + ", em " + ship.position());
            int turns = PortraitGrid.quarterTurnsFromYaw(ship.yawDegrees());
            RocketPortrait rotated = ship.portrait().rotated(turns);
            RocketPortrait photoRotated = photo.rotated(turns);
            BlockPos origin = RocketLanding.originFor(ship.position(), rotated.sizeX, rotated.sizeY, rotated.sizeZ);
            int fuel = (int) Math.floor(ship.fuelMb());
            DeckCrew.Member crewBefore = DeckCrew.member(player.getUUID());
            DeckState before = crewBefore == null ? null : crewBefore.state();
            watching = false;
            ShipAscentDevtest.watchDamage(player);
            if (before != null) {
                ShipAscentDevtest.check("pouso: o tripulante não atravessou o piso na descida (pé sobre o piso local, mínimo " + f(minFootRel) + ")", minFootRel >= -0.001,
                        "mínimo " + f(minFootRel));
                ShipAscentDevtest.check("pouso: o tripulante ficou parado na nave (desvio máximo " + f(maxDriftXz) + " bloco)", maxDriftXz < 0.1, "desvio " + f(maxDriftXz));
            }
            try {
                RocketLanding.land(ground, ship);
            } catch (RocketException e) {
                ShipAscentDevtest.check("desmontagem no chão", false, e.getMessage());
                FlightDevtest.mark("sd_landed");
                return;
            }
            int blocks = 0;
            int mismatches = 0;
            int tankMb = 0;
            int chestItems = 0;
            for (int x = 0; x < rotated.sizeX; x++) {
                for (int y = 0; y < rotated.sizeY; y++) {
                    for (int z = 0; z < rotated.sizeZ; z++) {
                        BlockState expected = photoRotated.get(x, y, z);
                        if (expected.isAir()) {
                            continue;
                        }
                        BlockPos pos = origin.offset(x, y, z);
                        BlockState actual = ground.getBlockState(pos);
                        blocks += actual.isAir() ? 0 : 1;
                        mismatches += actual.equals(expected) ? 0 : 1;
                        BlockEntity be = ground.getBlockEntity(pos);
                        if (be instanceof RocketFuelTankBlockEntity tank) {
                            tankMb += tank.fuelMb();
                        } else if (be instanceof Container container) {
                            for (int i = 0; i < container.getContainerSize(); i++) {
                                chestItems += container.getItem(i).getCount();
                            }
                        }
                    }
                }
            }
            ShipAscentDevtest.check("chão: os " + photo.blockCount() + " blocos da foto de volta", blocks == photo.blockCount(), "blocos " + blocks);
            ShipAscentDevtest.check("chão: cada bloco igual à foto (girada " + turns + " quarto(s))", mismatches == 0, "diferentes " + mismatches);
            ShipAscentDevtest.check("chão: itens do baú iguais à foto (" + PHOTO_CHEST_ITEMS + ")", chestItems == PHOTO_CHEST_ITEMS, "itens " + chestItems);
            ShipAscentDevtest.check("chão: mB dos tanques iguais ao combustível da nave no pouso", tankMb == fuel, "tanques " + tankMb + " mB, nave " + fuel + " mB");
            AABB around = new AABB(origin).expandTowards(rotated.sizeX, rotated.sizeY, rotated.sizeZ).inflate(8.0);
            int drops = ground.getEntitiesOfClass(ItemEntity.class, around).size();
            ShipAscentDevtest.check("chão: nenhum item solto em volta", drops == 0, "itens soltos " + drops);
            int ghosts = ShipRegistry.get(server).all().size();
            ShipAscentDevtest.check("nenhuma nave fantasma no registro", ghosts == 0, "naves " + ghosts);
            if (before != null) {
                org.joml.Vector3d want = DeckYardMap.landed(before.pos, turns, photo.sizeX, photo.sizeZ, origin.getX(), origin.getY(), origin.getZ());
                double err = Math.sqrt(player.distanceToSqr(want.x, want.y, want.z));
                boolean solidBelow = !ground.getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ())).getCollisionShape(ground, BlockPos.containing(player.getX(),
                        player.getY() - 0.2, player.getZ())).isEmpty();
                boolean inside = ground.getBlockState(BlockPos.containing(player.getX(), player.getY() + 0.1, player.getZ())).getCollisionShape(ground, BlockPos.containing(player.getX(),
                        player.getY() + 0.1, player.getZ())).isEmpty();
                ShipAscentDevtest.check("pouso e desmontagem: o jogador fica em pé no chão no lugar relativo certo (< 0,1 bloco)", err < 0.1,
                        "esperado (" + f(want.x) + ", " + f(want.y) + ", " + f(want.z) + "), está em (" + f(player.getX()) + ", " + f(player.getY()) + ", " + f(player.getZ()) + "), diferença " + f(err));
                ShipAscentDevtest.check("pouso e desmontagem: sobre um bloco sólido e sem estar dentro de um", solidBelow && inside, "sólido embaixo " + solidBelow + ", livre nos pés " + inside);
                ShipAscentDevtest.check("pouso e desmontagem: fora do convés, sem dano de queda e sem sentar", !DeckCrew.isCrew(player) && player.getVehicle() == null && !ShipAscentDevtest.fallHurt
                        && player.fallDistance == 0.0, "tripulante " + DeckCrew.isCrew(player) + ", vida " + f(player.getHealth()) + ", último dano " + ShipAscentDevtest.lastDamage
                        + ", queda " + player.fallDistance);
            }
            SpaceHorizons.LOGGER.info("[AUTOTEST] SHIPDESCENT desmontada em {}: {} blocos, giro {}, {} mB", origin.toShortString(), blocks, turns, tankMb);
            FlightDevtest.mark("sd_landed");
        });
    }

    private static boolean yardEmpty(MinecraftServer server) {
        ServerLevel yard = ShipYard.level(server);
        if (yard == null || yardBox == null) {
            return false;
        }
        int blocks = 0;
        for (int x = 0; x < yardBox.sizeX(); x++) {
            for (int y = 0; y < yardBox.sizeY(); y++) {
                for (int z = 0; z < yardBox.sizeZ(); z++) {
                    if (!yard.getBlockState(new BlockPos(yardBox.minX() + x, yardBox.minY() + y, yardBox.minZ() + z)).isAir()) {
                        blocks++;
                    }
                }
            }
        }
        return blocks == 0;
    }

    private static boolean sameGrid(RocketPortrait a, RocketPortrait b) {
        if (a.sizeX != b.sizeX || a.sizeY != b.sizeY || a.sizeZ != b.sizeZ) {
            return false;
        }
        for (int x = 0; x < a.sizeX; x++) {
            for (int y = 0; y < a.sizeY; y++) {
                for (int z = 0; z < a.sizeZ; z++) {
                    if (!a.get(x, y, z).equals(b.get(x, y, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Mesmas entidades de bloco nas mesmas posições, com o mesmo NBT tirando o combustível dos tanques: a subida e a circularização gastam
     * combustível, e a carga dos tanques é conferida à parte (pela soma, contra o combustível da nave no começo da descida).
     */
    private static boolean sameBlockEntities(RocketPortrait a, RocketPortrait b) {
        if (a.blockEntities().size() != b.blockEntities().size()) {
            return false;
        }
        for (var e : a.blockEntities().long2ObjectEntrySet()) {
            var other = b.blockEntities().get(e.getLongKey());
            if (other == null || !withoutFuel(e.getValue()).equals(withoutFuel(other))) {
                return false;
            }
        }
        return true;
    }

    private static net.minecraft.nbt.CompoundTag withoutFuel(net.minecraft.nbt.CompoundTag tag) {
        var copy = tag.copy();
        copy.remove(RocketFuelTankBlockEntity.FUEL_KEY);
        return copy;
    }
}
