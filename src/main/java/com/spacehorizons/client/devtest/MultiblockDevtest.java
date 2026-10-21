package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.rocket.RocketDesignClient;
import com.spacehorizons.client.rocket.RocketDesignModel;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.connected.ConnectedFaces;
import com.spacehorizons.rocket.DesignReport;
import com.spacehorizons.rocket.EngineForm;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.MissionBudget;
import com.spacehorizons.rocket.Multiblocks;
import com.spacehorizons.rocket.RocketAssembler;
import com.spacehorizons.rocket.RocketAssembly;
import com.spacehorizons.rocket.RocketControlPayload;
import com.spacehorizons.rocket.RocketDesign;
import com.spacehorizons.rocket.RocketEngineBlock;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketFuelTankBlock;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.RocketLanding;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.rocket.RocketSpecs;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Cenário {@code multiblock} do autoteste (etapa 1 do docs/PLANO_TIERS.md): tanques e motores multibloco, painel de projeto e foguetes grandes.
 * Para cada projeto (motor 1x1, 2x2x2 ou 3x3x3; coluna de tanques; cascos): constrói sobre a plataforma, abre o painel (o cliente pede ao
 * servidor, que usa o MESMO cálculo da montagem), monta, voa uma subida curta com o modelo grande em voo (acelerador, paraquedas), pousa e
 * confere que as estruturas voltam formadas e o combustível bate. Tudo vai para o log com o prefixo {@code [AUTOTEST] MULTIBLOCK}; as
 * verificações saem como {@code CHECK PASSOU} ou {@code CHECK FALHOU} e o resumo no fim.
 */
final class MultiblockDevtest {
    private static final String TAG = "[AUTOTEST] MULTIBLOCK ";
    static final BlockPos BASE = RocketDevtest.BASE;

    private static int passed;
    private static int failed;

    /** Um projeto: motor (lado 1, 2 ou 3), coluna de tanques, cascos em placas, paraquedas, e opcionalmente uma casca grande. */
    record Plan(String key, int engineSide, int tankW, int tankH, int tankD, int hulls, boolean chute, int shell, MissionBudget.Destination expected) {
    }

    static final Map<String, Plan> PLANS = new HashMap<>();

    static {
        // não decola: um motor 1x1 para uma coluna 3x3x10 (TWR 0,1)
        PLANS.put("none", new Plan("none", 1, 3, 10, 3, 0, true, 0, MissionBudget.Destination.NO_LIFTOFF));
        // salto: motor 1x1 e coluna 1x1x8 (TWR 1,1: sai do chão, mas abaixo do 1,2 que a órbita pede)
        PLANS.put("sub", new Plan("sub", 1, 1, 8, 1, 0, true, 0, MissionBudget.Destination.SUBORBITAL));
        // órbita: motor 2x2x2 e coluna 1x1x4
        PLANS.put("orbit", new Plan("orbit", 2, 1, 4, 1, 0, true, 0, MissionBudget.Destination.ORBIT));
        // Lua e volta: motor 3x3x3, coluna 2x2x8 e 20 cascos
        PLANS.put("moonret", new Plan("moonret", 3, 2, 8, 2, 20, true, 0, MissionBudget.Destination.MOON_RETURN));
        // Lua, pouso e volta: motor 3x3x3 e coluna 3x3x10
        PLANS.put("full", new Plan("full", 3, 3, 10, 3, 0, true, 0, MissionBudget.Destination.MOON_LANDING_RETURN));
        // grande: casca de 16x20x16 (mais de 1.024 blocos), motor 3x3x3, coluna 2x2x8
        PLANS.put("big", new Plan("big", 3, 2, 8, 2, 0, true, 16, null));
        // gigante: casca de 34x34x34 (mais de 6.000 blocos)
        PLANS.put("giant", new Plan("giant", 3, 2, 8, 2, 0, true, 34, null));
    }

    private MultiblockDevtest() {
    }

    // ---------------------------------------------------------------- verificações

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        SpaceHorizons.LOGGER.info(TAG + "CHECK {} {}: {}", ok ? "PASSOU" : "FALHOU", name, detail);
    }

    static void summary() {
        SpaceHorizons.LOGGER.info(TAG + "RESUMO: {} verificações passaram, {} falharam", passed, failed);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private interface ServerWork {
        void run(MinecraftServer server, ServerLevel level, ServerPlayer player);
    }

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
                check(label + " sem exceção", false, e.toString());
            }
        });
    }

    // ---------------------------------------------------------------- construção

    /** O que a última construção deixou: a busca (a mesma do painel e da montagem), o cockpit e quantos blocos. */
    private static RocketAssembly.Report lastReport;
    private static BlockPos lastCockpit = BASE;
    private static Plan lastPlan;

    private static void put(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(BASE.offset(x, y, z), state, 2);
    }

    private static void fillBox(ServerLevel level, int x0, int y0, int z0, int w, int h, int d, BlockState state) {
        for (int y = y0; y < y0 + h; y++) {
            for (int z = z0; z < z0 + d; z++) {
                for (int x = x0; x < x0 + w; x++) {
                    put(level, x, y, z, state);
                }
            }
        }
    }

    /** Limpa a área e constrói o projeto {@code key} sobre uma plataforma 7x7, com todos os tanques cheios. */
    static void build(Minecraft mc, String key) {
        com.spacehorizons.client.rocket.RocketPilot.forgetPilotingForTest();
        onServer(mc, "construção " + key, (server, level, player) -> {
            Plan plan = PLANS.get(key);
            lastPlan = plan;
            for (RocketEntity old : level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(200.0))) {
                old.discard();
            }
            int half = plan.shell() > 0 ? plan.shell() / 2 + 4 : 8;
            for (int y = -1; y <= (plan.shell() > 0 ? plan.shell() + 12 : 40); y++) {
                for (int x = -half; x <= half; x++) {
                    for (int z = -half; z <= half; z++) {
                        level.setBlock(BASE.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            fillBox(level, -3, 0, -3, 7, 1, 7, ModBlocks.LAUNCH_PAD.get().defaultBlockState());
            fillBox(level, 0, 1, 0, plan.engineSide(), plan.engineSide(), plan.engineSide(), ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
            int tankY = 1 + plan.engineSide();
            if (plan.shell() > 0) {
                // casca oca de lado = shell: o piso (y = tankY - 0) encosta no topo do motor, a coluna de tanques fica dentro, sobre o piso
                int s = plan.shell();
                int x0 = -s / 2 + 1;
                int floorY = tankY;
                BlockState hull = ModBlocks.ROCKET_HULL.get().defaultBlockState();
                for (int y = floorY; y < floorY + s; y++) {
                    for (int x = x0; x < x0 + s; x++) {
                        for (int z = x0; z < x0 + s; z++) {
                            boolean wall = x == x0 || x == x0 + s - 1 || z == x0 || z == x0 + s - 1 || y == floorY || y == floorY + s - 1;
                            if (wall) {
                                put(level, x, y, z, hull);
                            }
                        }
                    }
                }
                tankY = floorY + 1;
            }
            fillBox(level, 0, tankY, 0, plan.tankW(), plan.tankH(), plan.tankD(), ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState());
            int cockpitY = tankY + plan.tankH();
            put(level, 0, cockpitY, 0, ModBlocks.ROCKET_COCKPIT.get().defaultBlockState());
            int y = cockpitY + 1;
            if (plan.chute()) {
                put(level, 0, y++, 0, ModBlocks.ROCKET_PARACHUTE.get().defaultBlockState());
            }
            int left = plan.hulls();
            for (; left > 0; y++) {
                for (int i = 0; i < 9 && left > 0; i++, left--) {
                    put(level, -1 + i % 3, y, -1 + i / 3, ModBlocks.ROCKET_HULL.get().defaultBlockState());
                }
            }
            // as conexões dos tanques (setBlock não calcula o estado do próprio bloco) e todos cheios
            int tanks = 0;
            for (int yy = tankY; yy < tankY + plan.tankH(); yy++) {
                for (int z = 0; z < plan.tankD(); z++) {
                    for (int x = 0; x < plan.tankW(); x++) {
                        BlockPos pos = BASE.offset(x, yy, z);
                        ConnectedFaces.connectAround(level, pos, ModBlocks.ROCKET_FUEL_TANK.get(), RocketFuelTankBlock.rule());
                        if (level.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity tank) {
                            tank.setFuel(4000);
                            tanks++;
                        }
                    }
                }
            }
            lastCockpit = BASE.offset(0, cockpitY, 0);
            lastReport = RocketAssembly.analyze(RocketAssembler.worldGrid(level), List.of(new int[] {lastCockpit.getX(), lastCockpit.getY(), lastCockpit.getZ()}));
            var census = lastReport.result().census();
            SpaceHorizons.LOGGER.info("{}CONSTRUÍDO {}: {} blocos (caixa {}x{}x{}), motores {} blocos ({} avulsos, {} x 2x2x2, {} x 3x3x3), tanques {} blocos ({} colunas, {} avulsos), "
                    + "paraquedas {}, montável={}, problemas={}", TAG, key, lastReport.result().cells().size(), lastReport.result().sizeX(), lastReport.result().sizeY(),
                    lastReport.result().sizeZ(), census.engines(), census.soloEngines(), census.engineStructuresOfSide(2), census.engineStructuresOfSide(3),
                    census.tanks(), census.tankStructures().size(), census.looseTanks(), census.parachutes(), lastReport.assemblable(),
                    lastReport.blockers().stream().map(b -> b.reason().name()).toList());
            check(key + ": construído montável", lastReport.assemblable(), "recusas " + lastReport.blockers().stream().map(b -> b.reason().name()).toList());
            check(key + ": todos os tanques cheios", tanks == census.tanks(), tanks + " de " + census.tanks());
            // as estruturas formadas no mundo (estado dos blocos) são as que o censo conta
            verifyWorldStates(level, key + " (no mundo, antes de montar)", lastReport);
        });
    }

    /** Os estados dos motores e dos tanques no mundo batem com as estruturas que a partição acha. */
    private static void verifyWorldStates(ServerLevel level, String label, RocketAssembly.Report report) {
        Map<Long, Multiblocks.EngineRole> roles = Multiblocks.engineRoles(report.structures().engines());
        int wrong = 0;
        int masters2 = 0;
        int masters3 = 0;
        int parts = 0;
        int solos = 0;
        for (Map.Entry<Long, Multiblocks.EngineRole> e : roles.entrySet()) {
            BlockState state = level.getBlockState(BlockPos.of(e.getKey()));
            if (!state.is(ModBlocks.ROCKET_ENGINE.get()) || state.getValue(RocketEngineBlock.FORM) != EngineForm.of(e.getValue())) {
                wrong++;
                continue;
            }
            switch (e.getValue()) {
                case MASTER_2 -> masters2++;
                case MASTER_3 -> masters3++;
                case PART -> parts++;
                case SOLO -> solos++;
            }
        }
        check(label + ": estado de cada bloco de motor = papel da partição", wrong == 0,
                "errados=" + wrong + " de " + roles.size() + " (mestres 2x2x2=" + masters2 + ", 3x3x3=" + masters3 + ", partes=" + parts + ", avulsos=" + solos + ")");
        int badTanks = 0;
        int tanks = 0;
        for (RocketAssembly.Cell cell : report.result().cells()) {
            if (cell.kind() != com.spacehorizons.rocket.RocketBlockKind.FUEL_TANK) {
                continue;
            }
            tanks++;
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            BlockState state = level.getBlockState(pos);
            if (!state.equals(ConnectedFaces.connect(state, level, pos, RocketFuelTankBlock.rule()))) {
                badTanks++;
            }
        }
        check(label + ": conexões dos tanques (texturas) corretas", badTanks == 0, "errados=" + badTanks + " de " + tanks);
    }

    // ---------------------------------------------------------------- painel

    /** A plataforma (no centro de BASE) sobre a qual o painel é pedido. */
    static BlockPos padPos() {
        return BASE;
    }

    static void pinPanel(boolean on) {
        RocketDesignClient.pinForTest(on ? BASE : null);
    }

    /** O painel (o relatório que o CLIENTE recebeu) é o que o servidor calcula, e os números são os da montagem. */
    static void panel(Minecraft mc, String key) {
        DesignReport client = RocketDesignClient.lastReport();
        onServer(mc, "painel " + key, (server, level, player) -> {
            Plan plan = PLANS.get(key);
            DesignReport server1 = RocketDesign.analyze(level, BASE);
            SpaceHorizons.LOGGER.info("{}PAINEL {} (cliente): {}", TAG, key, client == null ? "sem relatório" : RocketDesignModel.describe(client));
            SpaceHorizons.LOGGER.info("{}PAINEL {} (servidor): {}", TAG, key, RocketDesignModel.describe(server1));
            check(key + ": o cliente recebeu o relatório do painel", client != null, "relatório=" + (client != null));
            check(key + ": relatório do cliente = cálculo do servidor", client != null && client.equals(server1), "iguais=" + (client != null && client.equals(server1)));
            var stats = RocketSpecs.statsFor(lastReport.result().census());
            check(key + ": números do painel = estatísticas da montagem",
                    Math.abs(server1.thrust() - stats.thrust()) < 1.0e-9 && Math.abs(server1.dryMassT() - stats.dryMassT()) < 1.0e-9
                            && Math.abs(server1.deltaV() - stats.deltaV(stats.fuelCapacityMb())) < 1.0e-9,
                    "empuxo " + f(server1.thrust()) + ", massa seca " + f(server1.dryMassT()) + ", Δv " + f(server1.deltaV()) + " b/t, TWR " + f(server1.twr()));
            if (plan.expected() != null) {
                check(key + ": destino do painel = " + plan.expected(), server1.destinationValue() == plan.expected(),
                        "painel diz " + server1.destinationValue() + " com Δv " + f(server1.deltaV()) + " b/t e TWR " + f(server1.twr()));
            }
            if (plan.shell() > 0) {
                check(key + ": o painel enxerga o foguete grande (mais de 1.024 blocos)", server1.blocks() > 1024, server1.blocks() + " blocos");
            }
            check(key + ": problemas do painel são só avisos", server1.assemblable(), "problemas=" + RocketDesignModel.problems(server1).size());
        });
    }

    // ---------------------------------------------------------------- montar

    static void assemble(Minecraft mc, String key) {
        onServer(mc, "montagem " + key, (server, level, player) -> {
            try {
                RocketEntity rocket = RocketAssembler.assemble(level, lastCockpit, null);
                var scanCensus = lastReport.result().census();
                RocketPortrait portrait = rocket.portrait();
                check(key + ": censo do retrato = censo da busca (a montagem)", portrait.census().sameShape(scanCensus),
                        "retrato " + portrait.census().engineStructures().size() + " motores formados/" + portrait.census().tankStructures().size() + " colunas contra "
                                + scanCensus.engineStructures().size() + "/" + scanCensus.tankStructures().size());
                check(key + ": estatísticas da entidade = painel", Math.abs(rocket.stats().thrust() - RocketSpecs.statsFor(scanCensus).thrust()) < 1.0e-9
                        && Math.abs(rocket.stats().dryMassT() - RocketSpecs.statsFor(scanCensus).dryMassT()) < 1.0e-9,
                        "empuxo " + f(rocket.stats().thrust()) + " massa seca " + f(rocket.stats().dryMassT()) + " capacidade " + f(rocket.stats().fuelCapacityMb()));
                check(key + ": combustível da entidade = tanques cheios", Math.abs(rocket.fuelMb() - rocket.stats().fuelCapacityMb()) < 1.0,
                        f(rocket.fuelMb()) + " de " + f(rocket.stats().fuelCapacityMb()) + " mB");
                // as estruturas viajam no retrato: o mestre e as partes com os mesmos estados
                int masters = 0;
                int parts = 0;
                for (int y = 0; y < portrait.sizeY; y++) {
                    for (int z = 0; z < portrait.sizeZ; z++) {
                        for (int x = 0; x < portrait.sizeX; x++) {
                            BlockState s = portrait.get(x, y, z);
                            if (s.is(ModBlocks.ROCKET_ENGINE.get())) {
                                EngineForm form = s.getValue(RocketEngineBlock.FORM);
                                masters += form == EngineForm.MASTER_2 || form == EngineForm.MASTER_3 ? 1 : 0;
                                parts += form == EngineForm.PART ? 1 : 0;
                            }
                        }
                    }
                }
                check(key + ": retrato leva os mestres e as partes dos motores formados", masters == scanCensus.engineStructures().size()
                        && parts == scanCensus.formedEngineBlocks() - scanCensus.engineStructures().size(), "mestres " + masters + ", partes " + parts);
                boolean seated = false;
                SpaceHorizons.LOGGER.info("{}MONTADO {}: {} blocos, caixa {}x{}x{}, massa seca {} t, empuxo {}, capacidade {} mB, Δv {} b/t, TWR {}, sentado={}, "
                        + "bytes do retrato={}", TAG, key, portrait.blockCount(), portrait.sizeX, portrait.sizeY, portrait.sizeZ, f(rocket.stats().dryMassT()),
                        f(rocket.stats().thrust()), f(rocket.stats().fuelCapacityMb()), f(rocket.stats().deltaV(rocket.fuelMb())),
                        f(rocket.stats().twr(rocket.fuelMb(), DesignReport.EARTH_GRAVITY)), seated, RocketPortrait.encodedSize(portrait));
                FlightDevtest.mark("assembled_" + key);
            } catch (RocketException e) {
                check(key + ": montagem aceita", false, e.getMessage());
                FlightDevtest.mark("failed");
            }
        });
    }

    /** O que o CLIENTE tem do foguete (inclusive o retrato recebido em partes, se o pacote de spawn não o levou). */
    static void clientView(Minecraft mc, String key, boolean expectParts) {
        List<RocketEntity> rockets = mc.level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(120.0));
        check(key + ": o cliente vê o foguete", rockets.size() == 1, "entidades=" + rockets.size());
        if (rockets.isEmpty()) {
            return;
        }
        RocketPortrait portrait = rockets.get(0).portrait();
        int expected = lastReport.result().cells().size();
        check(key + ": retrato completo no cliente" + (expectParts ? " (chegou em partes)" : ""), !portrait.isEmpty() && portrait.blockCount() == expected,
                portrait.blockCount() + " de " + expected + " blocos, caixa " + portrait.sizeX + "x" + portrait.sizeY + "x" + portrait.sizeZ);
        check(key + ": o cliente conta as mesmas estruturas", portrait.census().sameShape(lastReport.result().census()), portrait.census().engineStructures() + " " + portrait.census().tankStructures());
    }

    // ---------------------------------------------------------------- voo curto

    private static boolean registered;
    private static boolean armed;
    private static String flyKey = "";
    private static int flyTicks;
    private static int burnTicks;
    private static double startY;
    private static double maxAltitude;
    private static double maxSpeed;
    private static boolean chuteSent;
    private static boolean everFlew;
    private static double fuelAtStart;
    private static double fuelAtLanding;

    /** Inicia a subida curta: SAS de estabilizar, acelerador cheio por alguns ticks (a velocidade de ~5 b/t), corta e pede o paraquedas. */
    static void fly(Minecraft mc, String key) {
        onServer(mc, "partida " + key, (server, level, player) -> {
            List<RocketEntity> found = level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(60.0));
            if (found.isEmpty()) {
                check(key + ": foguete para voar", false, "nenhum");
                FlightDevtest.mark("landed_" + key);
                return;
            }
            RocketEntity rocket = found.get(0);
            player.teleportTo(rocket.getX(), rocket.getY() + 2.0, rocket.getZ());
            check(key + ": piloto sentado no foguete", player.startRiding(rocket, true, true), "veículo=" + player.getVehicle());
            double wet = rocket.stats().wetMass(rocket.fuelMb());
            double accelNet = rocket.stats().thrust() / wet - DesignReport.EARTH_GRAVITY;
            burnTicks = accelNet <= 0.0 ? 100 : (int) Math.max(3, Math.min(120, Math.round(5.0 / accelNet)));
            flyKey = key;
            flyTicks = 0;
            startY = rocket.getY();
            maxAltitude = 0.0;
            maxSpeed = 0.0;
            chuteSent = false;
            everFlew = false;
            fuelAtStart = rocket.fuelMb();
            armed = true;
            if (!registered) {
                registered = true;
                NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, e -> flyTick(e.getServer()));
            }
            SpaceHorizons.LOGGER.info("{}VOO {}: aceleração líquida {} b/t², queima de {} ticks, combustível {} mB, empuxo {}, massa cheia {} t", TAG, key, f(accelNet),
                    burnTicks, f(rocket.fuelMb()), f(rocket.stats().thrust()), f(wet));
        });
    }

    private static void flyTick(MinecraftServer server) {
        if (!armed || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        if (!(player.getVehicle() instanceof RocketEntity rocket)) {
            return;
        }
        flyTicks++;
        double altitude = rocket.getY() - startY;
        maxAltitude = Math.max(maxAltitude, altitude);
        maxSpeed = Math.max(maxSpeed, rocket.getDeltaMovement().length());
        FlightMode mode = rocket.flight().mode();
        if (mode != FlightMode.LANDED) {
            everFlew = true;
        }
        float throttle = flyTicks >= 2 && flyTicks < 2 + burnTicks ? 1.0F : 0.0F;
        int flags = 0;
        if (flyTicks == 1) {
            flags |= RocketControlPayload.FLAG_NEXT_SAS;
        }
        if (flyTicks == 2 + burnTicks + 2 && !chuteSent) {
            flags |= RocketControlPayload.FLAG_PARACHUTE;
            chuteSent = true;
        }
        rocket.applyControl(new RocketControlPayload(0.0F, 0.0F, 0.0F, throttle, (byte) flags));
        if (flyTicks == 2 + burnTicks / 2) {
            FlightDevtest.mark("burning_" + flyKey);
        }
        if (flyTicks % 40 == 0) {
            SpaceHorizons.LOGGER.info("{}VOO {} tick {}: modo {} altitude {} velocidade {} combustível {} mB acelerador {}", TAG, flyKey, flyTicks, mode, f(altitude),
                    f(rocket.getDeltaMovement().length()), f(rocket.fuelMb()), f(rocket.throttle()));
        }
        boolean none = "none".equals(flyKey);
        if (none && flyTicks > 100) {
            armed = false;
            check("none: com TWR " + f(rocket.stats().twr(rocket.fuelMb(), DesignReport.EARTH_GRAVITY)) + " não decola", !everFlew && mode == FlightMode.LANDED,
                    "modo " + mode + " altitude máxima " + f(maxAltitude) + " combustível " + f(rocket.fuelMb()) + " mB");
            FlightDevtest.mark("landed_" + flyKey);
        } else if (!none && everFlew && mode == FlightMode.LANDED && flyTicks > burnTicks + 10) {
            armed = false;
            fuelAtLanding = rocket.fuelMb();
            check(flyKey + ": decolou e pousou suave", true, "altitude máxima " + f(maxAltitude) + " blocos, velocidade máxima " + f(maxSpeed) + " b/t, "
                    + flyTicks + " ticks, combustível " + f(fuelAtStart) + " -> " + f(fuelAtLanding) + " mB, queimou " + f(fuelAtStart - fuelAtLanding) + " mB");
            FlightDevtest.mark("landed_" + flyKey);
        } else if (flyTicks > 4_000) {
            armed = false;
            check(flyKey + ": voo terminou no tempo", false, "modo " + mode + " altitude " + f(altitude) + " após " + flyTicks + " ticks");
            FlightDevtest.mark("landed_" + flyKey);
        }
    }

    // ---------------------------------------------------------------- pouso

    /** Desmonta o foguete pousado e confere: blocos iguais ao retrato, estruturas formadas de novo, combustível. */
    static void afterLand(Minecraft mc, String key) {
        onServer(mc, "pouso " + key, (server, level, player) -> {
            List<RocketEntity> rockets = level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(400.0));
            if (rockets.isEmpty()) {
                check(key + ": foguete para desmontar", false, "nenhum");
                return;
            }
            RocketEntity rocket = rockets.get(0);
            RocketPortrait portrait = rocket.portrait();
            int turns = com.spacehorizons.rocket.PortraitGrid.quarterTurnsFromYaw(rocket.yawDegrees());
            RocketPortrait rotated = portrait.rotated(turns);
            BlockPos origin = RocketLanding.originFor(rocket.position(), rotated.sizeX, rotated.sizeY, rotated.sizeZ);
            int fuel = (int) Math.floor(rocket.fuelMb());
            try {
                RocketLanding.land(level, rocket);
            } catch (RocketException e) {
                // um foguete de 34 blocos de largura pousa em terreno irregular: a recusa por bloco no caminho (SPACE_OCCUPIED) é o comportamento certo
                boolean expectedRefusal = "giant".equals(key) && e.reason() == RocketException.Reason.SPACE_OCCUPIED;
                check(key + ": desmontagem aceita" + (expectedRefusal ? " ou recusada com motivo (terreno irregular sob 34 blocos de pegada)" : ""), expectedRefusal, e.getMessage());
                rocket.discard();
                FlightDevtest.mark("land_checked_" + key);
                return;
            }
            int blocks = 0;
            int different = 0;
            int fuelSum = 0;
            int masters = 0;
            int parts = 0;
            int solos = 0;
            int badTanks = 0;
            for (int y = 0; y < rotated.sizeY; y++) {
                for (int z = 0; z < rotated.sizeZ; z++) {
                    for (int x = 0; x < rotated.sizeX; x++) {
                        BlockState expected = rotated.get(x, y, z);
                        if (expected.isAir()) {
                            continue;
                        }
                        blocks++;
                        BlockPos pos = origin.offset(x, y, z);
                        BlockState actual = level.getBlockState(pos);
                        if (!expected.equals(actual)) {
                            different++;
                        }
                        if (actual.is(ModBlocks.ROCKET_ENGINE.get())) {
                            switch (actual.getValue(RocketEngineBlock.FORM)) {
                                case MASTER_2, MASTER_3 -> masters++;
                                case PART -> parts++;
                                case SOLO -> solos++;
                            }
                        }
                        if (actual.is(ModBlocks.ROCKET_FUEL_TANK.get())) {
                            if (level.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity tank) {
                                fuelSum += tank.fuelMb();
                            }
                            if (!actual.equals(ConnectedFaces.connect(actual, level, pos, RocketFuelTankBlock.rule()))) {
                                badTanks++;
                            }
                        }
                    }
                }
            }
            var census = portrait.census();
            check(key + ": pouso devolveu todos os blocos iguais ao retrato (girado " + turns + " quarto(s))", blocks == portrait.blockCount() && different == 0,
                    "blocos " + blocks + " de " + portrait.blockCount() + ", diferentes " + different);
            check(key + ": motores voltam formados (mestres " + masters + ", partes " + parts + ", avulsos " + solos + ")",
                    masters == census.engineStructures().size() && parts == census.formedEngineBlocks() - census.engineStructures().size()
                            && solos == census.soloEngines(), "esperado mestres " + census.engineStructures().size() + ", partes "
                            + (census.formedEngineBlocks() - census.engineStructures().size()) + ", avulsos " + census.soloEngines());
            check(key + ": tanques voltam com as texturas conectadas certas", badTanks == 0, "errados " + badTanks);
            check(key + ": combustível devolvido bate (" + fuelSum + " mB nos tanques, " + fuel + " na entidade)", fuelSum == fuel, "soma " + fuelSum + " contra " + fuel);
            // o censo do mundo depois do pouso (a busca a partir do cockpit) = o censo do retrato
            BlockPos cockpit = origin.offset(rotated.cockpit());
            RocketAssembly.Report report = RocketAssembly.analyze(RocketAssembler.worldGrid(level), List.of(new int[] {cockpit.getX(), cockpit.getY(), cockpit.getZ()}));
            check(key + ": censo do mundo depois do pouso = censo do retrato", report.result().census().sameShape(rotated.census()),
                    report.result().census().engineStructures() + " " + report.result().census().tankStructures());
            FlightDevtest.mark("land_checked_" + key);
        });
    }

    // ---------------------------------------------------------------- projeto com defeitos (o painel aponta cada um no bloco certo)

    /**
     * Um projeto com quatro defeitos: sem paraquedas, um motor sem tanque acima (x = 3), uma coluna de tanques 2x3x2 curta demais para formar
     * (incompleta) e uma pedra (terreno) colada na coluna. Posições relativas a BASE: motor A (0,1,0), motor B (3,1,0) ligado por cascos em
     * (1,1,0) e (2,1,0), tanques x 0..1, y 2..4, z 0..1, cockpit (0,5,0), pedra (2,3,0).
     */
    static void buildFlawed(Minecraft mc) {
        com.spacehorizons.client.rocket.RocketPilot.forgetPilotingForTest();
        onServer(mc, "projeto com defeitos", (server, level, player) -> {
            for (RocketEntity old : level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(200.0))) {
                old.discard();
            }
            for (int y = -1; y <= 40; y++) {
                for (int x = -12; x <= 12; x++) {
                    for (int z = -12; z <= 12; z++) {
                        level.setBlock(BASE.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            fillBox(level, -3, 0, -3, 7, 1, 7, ModBlocks.LAUNCH_PAD.get().defaultBlockState());
            put(level, 0, 1, 0, ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
            put(level, 3, 1, 0, ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
            put(level, 1, 1, 0, ModBlocks.ROCKET_HULL.get().defaultBlockState());
            put(level, 2, 1, 0, ModBlocks.ROCKET_HULL.get().defaultBlockState());
            fillBox(level, 0, 2, 0, 2, 3, 2, ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState());
            for (int y = 2; y <= 4; y++) {
                for (int z = 0; z < 2; z++) {
                    for (int x = 0; x < 2; x++) {
                        ConnectedFaces.connectAround(level, BASE.offset(x, y, z), ModBlocks.ROCKET_FUEL_TANK.get(), RocketFuelTankBlock.rule());
                    }
                }
            }
            put(level, 0, 5, 0, ModBlocks.ROCKET_COCKPIT.get().defaultBlockState());
            put(level, 2, 3, 0, Blocks.STONE.defaultBlockState());
            lastCockpit = BASE.offset(0, 5, 0);
            FlightDevtest.mark("built_flawed");
        });
    }

    /** O painel (relatório do cliente) aponta cada defeito no bloco certo; a montagem recusa pelo terreno. */
    static void flawedPanel(Minecraft mc) {
        DesignReport client = RocketDesignClient.lastReport();
        onServer(mc, "painel do projeto com defeitos", (server, level, player) -> {
            DesignReport r = RocketDesign.analyze(level, BASE);
            SpaceHorizons.LOGGER.info("{}PAINEL defeitos (servidor): {}", TAG, RocketDesignModel.describe(r));
            check("defeitos: o cliente recebeu o mesmo relatório", client != null && client.equals(r), "cliente " + (client == null ? "nenhum" : "recebeu"));
            BlockPos chute = BASE.offset(0, 5, 0);
            check("defeitos: 'falta paraquedas' no bloco mais alto (o cockpit)", hasProblem(r, DesignReport.Type.NO_PARACHUTE, chute), r.problems().toString());
            check("defeitos: 'motor sem tanque' no motor B", hasProblem(r, DesignReport.Type.ENGINE_WITHOUT_TANK, BASE.offset(3, 1, 0)), r.problems().toString());
            check("defeitos: o motor A (com tanque acima) não é apontado", !hasProblem(r, DesignReport.Type.ENGINE_WITHOUT_TANK, BASE.offset(0, 1, 0)), r.problems().toString());
            check("defeitos: 'encostado no terreno' na pedra", hasProblem(r, DesignReport.Type.TOUCHES_TERRAIN, BASE.offset(2, 3, 0)), r.problems().toString());
            check("defeitos: 'estrutura de tanque incompleta' nos tanques avulsos da coluna curta", r.problems().stream().anyMatch(p -> p.type() == DesignReport.Type.INCOMPLETE_TANK
                    && p.x() >= BASE.getX() && p.x() <= BASE.getX() + 1 && p.y() >= BASE.getY() + 2 && p.y() <= BASE.getY() + 4), r.problems().toString());
            check("defeitos: o terreno impede montar e o painel diz", !r.assemblable(), "montável=" + r.assemblable());
            try {
                RocketAssembler.assemble(level, lastCockpit, null);
                check("defeitos: a montagem recusa (terreno)", false, "foi aceita");
            } catch (RocketException e) {
                check("defeitos: a montagem recusa pelo mesmo motivo do painel (" + e.reason() + ")", e.reason() == RocketException.Reason.TOUCHES_TERRAIN, e.getMessage());
            }
        });
    }

    private static boolean hasProblem(DesignReport r, DesignReport.Type type, BlockPos pos) {
        return r.problems().stream().anyMatch(p -> p.type() == type && p.x() == pos.getX() && p.y() == pos.getY() && p.z() == pos.getZ());
    }

    // ---------------------------------------------------------------- formação no mundo e combustível da estrutura

    /** Posição (relativa a BASE) das estruturas soltas do teste de formação e do tanque do galão. */
    static final BlockPos FORMING = BASE.offset(14, 1, 14);
    static final BlockPos TANK_COLUMN = BASE.offset(-14, 1, 0);

    /** Põe e quebra blocos de motor e confere que a estrutura forma e desfaz sozinha; deixa uma coluna de tanques 2x2x8 vazia para o galão. */
    static void formingInWorld(Minecraft mc) {
        onServer(mc, "formação no mundo", (server, level, player) -> {
            for (int x = -2; x <= 8; x++) {
                for (int y = 0; y <= 10; y++) {
                    for (int z = -2; z <= 8; z++) {
                        level.setBlock(FORMING.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            BlockState engine = ModBlocks.ROCKET_ENGINE.get().defaultBlockState();
            // 2x2x2: o mestre é o canto mínimo
            for (int i = 0; i < 8; i++) {
                level.setBlock(FORMING.offset(i & 1, (i >> 1) & 1, (i >> 2) & 1), engine, 3);
            }
            BlockState corner = level.getBlockState(FORMING);
            check("formação 2x2x2 no mundo: o canto mínimo é o mestre", corner.getValue(RocketEngineBlock.FORM) == EngineForm.MASTER_2, corner.toString());
            int parts = 0;
            for (int i = 1; i < 8; i++) {
                parts += level.getBlockState(FORMING.offset(i & 1, (i >> 1) & 1, (i >> 2) & 1)).getValue(RocketEngineBlock.FORM) == EngineForm.PART ? 1 : 0;
            }
            check("formação 2x2x2 no mundo: os outros 7 blocos viram partes invisíveis", parts == 7, "partes " + parts);
            // quebra um canto: o resto volta a ser 7 motores avulsos
            level.destroyBlock(FORMING.offset(1, 1, 1), false);
            int solos = 0;
            for (int i = 0; i < 7; i++) {
                solos += level.getBlockState(FORMING.offset(i & 1, (i >> 1) & 1, (i >> 2) & 1)).getValue(RocketEngineBlock.FORM) == EngineForm.SOLO ? 1 : 0;
            }
            check("quebrar um bloco desfaz o motor 2x2x2 (7 avulsos)", solos == 7, "avulsos " + solos);
            level.setBlock(FORMING.offset(1, 1, 1), engine, 3);
            check("recolocar o bloco forma o motor de novo", level.getBlockState(FORMING).getValue(RocketEngineBlock.FORM) == EngineForm.MASTER_2
                    && level.getBlockState(FORMING.offset(1, 1, 1)).getValue(RocketEngineBlock.FORM) == EngineForm.PART, level.getBlockState(FORMING).toString());
            // 3x3x3 ao lado: o mestre é o bloco CENTRAL
            BlockPos three = FORMING.offset(4, 0, 0);
            for (int i = 0; i < 27; i++) {
                level.setBlock(three.offset(i % 3, (i / 3) % 3, i / 9), engine, 3);
            }
            check("formação 3x3x3 no mundo: o bloco central é o mestre", level.getBlockState(three.offset(1, 1, 1)).getValue(RocketEngineBlock.FORM) == EngineForm.MASTER_3
                    && level.getBlockState(three).getValue(RocketEngineBlock.FORM) == EngineForm.PART, level.getBlockState(three.offset(1, 1, 1)).toString());
            check("o 2x2x2 vizinho não se mistura com o 3x3x3", level.getBlockState(FORMING).getValue(RocketEngineBlock.FORM) == EngineForm.MASTER_2,
                    level.getBlockState(FORMING).toString());

            // a coluna de tanques 2x2x8, vazia, para o galão
            for (int y = 0; y < 8; y++) {
                for (int z = 0; z < 2; z++) {
                    for (int x = 0; x < 2; x++) {
                        level.setBlock(TANK_COLUMN.offset(x, y, z), ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState(), 2);
                    }
                }
            }
            for (int y = 0; y < 8; y++) {
                for (int z = 0; z < 2; z++) {
                    for (int x = 0; x < 2; x++) {
                        ConnectedFaces.connectAround(level, TANK_COLUMN.offset(x, y, z), ModBlocks.ROCKET_FUEL_TANK.get(), RocketFuelTankBlock.rule());
                    }
                }
            }
            SpaceHorizons.LOGGER.info("{}formação no mundo pronta; coluna de tanques 2x2x8 em {}", TAG, TANK_COLUMN.toShortString());
        });
    }

    /** O combustível da estrutura depois dos galões: a soma e a repartição por igual entre os 32 blocos. */
    static void structureFuel(Minecraft mc, String label, int expectedMb) {
        onServer(mc, "combustível da estrutura", (server, level, player) -> {
            int sum = 0;
            int min = Integer.MAX_VALUE;
            int max = 0;
            for (int y = 0; y < 8; y++) {
                for (int z = 0; z < 2; z++) {
                    for (int x = 0; x < 2; x++) {
                        if (level.getBlockEntity(TANK_COLUMN.offset(x, y, z)) instanceof RocketFuelTankBlockEntity tank) {
                            sum += tank.fuelMb();
                            min = Math.min(min, tank.fuelMb());
                            max = Math.max(max, tank.fuelMb());
                        }
                    }
                }
            }
            check("galão na estrutura " + label + ": soma da estrutura = " + expectedMb + " mB", sum == expectedMb, "soma " + sum);
            check("galão na estrutura " + label + ": repartido por igual entre os blocos", max - min <= 1, "mínimo " + min + " máximo " + max);
            SpaceHorizons.LOGGER.info("{}texto da estrutura (servidor): {}", TAG, RocketFuelTankBlock.levelMessage(level, TANK_COLUMN.offset(1, 7, 1)).getString());
        });
    }

    static void structureText(Minecraft mc, BlockPos pos) {
        SpaceHorizons.LOGGER.info("{}texto da estrutura (cliente, bloco {}): {}", TAG, pos.toShortString(), RocketFuelTankBlock.levelMessage(mc.level, pos).getString());
        check("o cliente mostra a estrutura formada", RocketFuelTankBlock.levelMessage(mc.level, pos).getString().contains("2x2x8")
                || RocketFuelTankBlock.levelMessage(mc.level, pos).getString().toLowerCase(Locale.ROOT).contains("2x2x8"), RocketFuelTankBlock.levelMessage(mc.level, pos).getString());
    }

    // ---------------------------------------------------------------- partes do retrato

    /** Baixa o limite do pacote de spawn para forçar o envio em partes (restaura com {@code false}). */
    static void forceParts(boolean on) {
        RocketAssembler.setInlineLimitForTest(on ? 2_000 : RocketAssembler.INLINE_PORTRAIT_BYTES);
        com.spacehorizons.rocket.PortraitParts.setPartBytesForTest(on ? 4_096 : com.spacehorizons.rocket.PortraitParts.PART_BYTES);
        SpaceHorizons.LOGGER.info("{}limite do pacote de spawn do retrato: {} bytes, partes de {} bytes", TAG, RocketAssembler.inlineLimit(),
                com.spacehorizons.rocket.PortraitParts.partBytes());
    }

    /** Lista as chaves dos projetos na ordem em que o roteiro os usa. */
    static List<String> keys() {
        return new ArrayList<>(List.of("none", "sub", "orbit", "moonret", "full", "big", "giant"));
    }
}
