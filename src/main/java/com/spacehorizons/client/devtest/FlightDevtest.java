package com.spacehorizons.client.devtest;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix3d;
import org.joml.Vector3d;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.rocket.AttitudeControl;
import com.spacehorizons.rocket.FlightMachine.Snapshot;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.RocketAssembler;
import com.spacehorizons.rocket.RocketControlPayload;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.RocketTelemetryBuilder;
import com.spacehorizons.rocket.RocketTelemetryPayload;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceClock;
import com.spacehorizons.space.flight.AttitudeMath;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.FrameBridge;
import com.spacehorizons.space.flight.FrameBridge.Local;
import com.spacehorizons.space.flight.V3;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Cenário {@code flight} do autoteste (fase B1: voo no servidor). O cliente de controles é de outro agente, então um
 * "piloto de teste" roda NO SERVIDOR: a cada tick lê o estado do foguete e chama {@code rocket.applyControl} como se o
 * cliente mandasse o pacote. Três voos:
 *
 * <ul>
 *   <li><b>A</b> (salto suborbital): sobe com acelerador cheio e uma curva de gravidade, corta com apoastro de ~6.000 blocos
 *       (dentro da dimensão de espaço), desce de retrógrado, volta à dimensão da Terra, freia com o motor, abre o paraquedas e
 *       pousa suave;</li>
 *   <li><b>B</b> (batida): sobe um pouco, corta e cai de volta sem paraquedas: o foguete se desfaz em itens e o piloto toma dano;</li>
 *   <li><b>C</b> (órbita): curva de gravidade forte, corta com apoastro de ~6.000, espera o apoastro e circulariza com SAS
 *       prógrado; desorbita, freia de retrógrado no espaço e pousa;</li>
 *   <li><b>D</b>: igual a C, mas SEM o freio no espaço (o jogador comum): a descida rápida depende da pré-carga do local de pouso.</li>
 * </ul>
 *
 * Tudo vai para o log com o prefixo {@code [AUTOTEST] FLIGHT}; o cliente espera os marcos ({@link #reached}) e tira os prints.
 */
final class FlightDevtest {
    private static final String TAG = "[AUTOTEST] FLIGHT ";
    /** Plataforma e foguete no ar, bem acima do terreno do spawn (como no cenário rocket). */
    static final BlockPos BASE = RocketDevtest.BASE;

    /** O cenário roda com o relógio andando (gamerule advance_time): a Terra gira em relação a I, taxa 1 tick de relógio por tick de jogo. */
    private static final double CLOCK_RATE = 1.0;

    private static final Set<String> MILESTONES = ConcurrentHashMap.newKeySet();
    private static boolean registered;
    private static boolean armed;
    private static char variant;
    private static int stage;
    private static int stageTicks;
    private static int totalTicks;
    private static int pendingSas;
    private static boolean sasPulse;
    /** O último comando que o piloto de teste mandou (ver {@link #reapplyControl}). */
    private static RocketControlPayload lastApplied;
    private static double padY;
    /** Relógio do Overworld no tick atual (o referencial inercial gira com ele: o leste e a velocidade do ar dependem do relógio). */
    private static double clockNow;
    private static double lastHealth;
    /** Voo R (piloto real): 0 = piloto de teste, 1/3 = janela em que SÓ o cliente real comanda, 2 = entre janelas, 4 = fim. */
    private static int realPhase;
    private static int realTicks;
    private static long mspsStart;
    private static long tickStartNanos;
    private static double msptSum;
    private static double msptMax;
    private static int msptCount;

    private FlightDevtest() {
    }

    static boolean reached(String milestone) {
        return MILESTONES.contains(milestone);
    }

    private static void mark(String milestone) {
        if (MILESTONES.add(milestone)) {
            SpaceHorizons.LOGGER.info("{}MARCO '{}' no tick {} do voo {}", TAG, milestone, totalTicks, variant);
        }
    }

    // ---------------------------------------------------------------- montagem

    private interface ServerWork {
        void run(MinecraftServer server, ServerPlayer player);
    }

    private static void onServer(Minecraft mc, String label, ServerWork work) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            SpaceHorizons.LOGGER.warn("{}{}: sem servidor integrado", TAG, label);
            return;
        }
        server.execute(() -> {
            try {
                work.run(server, server.getPlayerList().getPlayers().get(0));
            } catch (RuntimeException e) {
                SpaceHorizons.LOGGER.error(TAG + label + " falhou", e);
            }
        });
    }

    /** Constrói o foguete do voo {@code which} sobre a plataforma, abastece, monta e senta o jogador. */
    static void build(Minecraft mc, char which) {
        com.spacehorizons.client.rocket.RocketPilot.forgetPilotingForTest();
        onServer(mc, "montagem " + which, (server, player) -> {
            ServerLevel level = player.level();
            variant = which;
            armed = false;
            stage = 0;
            stageTicks = 0;
            totalTicks = 0;
            pendingSas = 0;
            lastApplied = null;
            realPhase = 0;
            realTicks = 0;
            // limpa a plataforma e o que sobrou de um voo anterior
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    for (int y = -1; y <= 16; y++) {
                        level.setBlock(BASE.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            for (RocketEntity old : level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(200.0))) {
                old.discard();
            }
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlock(BASE.offset(x, 0, z), ModBlocks.LAUNCH_PAD.get().defaultBlockState(), 2);
                }
            }
            BlockPos cockpit;
            if (which == 'B') {
                // pequeno: 1 motor, 1 tanque, cockpit; sem paraquedas
                put(level, 0, 1, 0, ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                put(level, 0, 2, 0, ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState());
                put(level, 0, 3, 0, ModBlocks.ROCKET_COCKPIT.get().defaultBlockState());
                cockpit = BASE.offset(0, 3, 0);
            } else {
                // 4 motores (2x2), 12 tanques (2x2x3), cockpit e 3 cascos, 1 paraquedas e 2 pernas
                for (int x = 0; x <= 1; x++) {
                    for (int z = 0; z <= 1; z++) {
                        put(level, x, 1, z, ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                        for (int y = 2; y <= 4; y++) {
                            put(level, x, y, z, ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState());
                        }
                        if (x != 0 || z != 0) {
                            put(level, x, 5, z, ModBlocks.ROCKET_HULL.get().defaultBlockState());
                        }
                    }
                }
                put(level, 0, 5, 0, ModBlocks.ROCKET_COCKPIT.get().defaultBlockState());
                put(level, 0, 6, 0, ModBlocks.ROCKET_PARACHUTE.get().defaultBlockState());
                put(level, -1, 1, 0, ModBlocks.ROCKET_LANDING_LEG.get().defaultBlockState());
                put(level, -1, 1, 1, ModBlocks.ROCKET_LANDING_LEG.get().defaultBlockState());
                cockpit = BASE.offset(0, 5, 0);
            }
            // abastece todos os tanques ("por comando": direto no block entity)
            for (int x = -1; x <= 2; x++) {
                for (int y = 1; y <= 6; y++) {
                    for (int z = -1; z <= 2; z++) {
                        if (level.getBlockEntity(BASE.offset(x, y, z)) instanceof RocketFuelTankBlockEntity tank) {
                            tank.setFuel(4000);
                        }
                    }
                }
            }
            try {
                RocketEntity rocket = RocketAssembler.assemble(level, cockpit, null);
                padY = rocket.getY();
                player.teleportTo(rocket.getX(), rocket.getY() + 4.0, rocket.getZ());
                boolean seated = player.startRiding(rocket, true, true);
                SpaceHorizons.LOGGER.info("{}MONTADO voo {}: blocos={} massa seca={} t, combustível={} mB, empuxo={}, TWR={}, Δv={} b/t, jogador sentado={}, modo={}",
                        TAG, which, rocket.portrait().blockCount(), f(rocket.stats().dryMassT()), f(rocket.fuelMb()), f(rocket.stats().thrust()),
                        f(rocket.stats().twr(rocket.fuelMb(), 0.08)), f(rocket.stats().deltaV(rocket.fuelMb())), seated, rocket.flightMode());
                mark("built_" + Character.toLowerCase(which));
            } catch (RocketException e) {
                SpaceHorizons.LOGGER.error("{}a montagem do voo {} foi recusada: {}", TAG, which, e.getMessage());
                mark("failed");
            }
            registerTick();
        });
    }

    private static void put(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(BASE.offset(x, y, z), state, 2);
    }

    /** Libera o piloto de teste (o cliente já tirou o print da plataforma). */
    static void go(Minecraft mc) {
        onServer(mc, "partida", (server, player) -> {
            armed = true;
            SpaceHorizons.LOGGER.info("{}PARTIDA do voo {}", TAG, variant);
        });
    }

    /** Estado do lado do cliente (a entidade, o veículo do jogador, tela atual): prova de que a travessia não abriu tela de carregamento. */
    static void clientCheck(Minecraft mc, String label) {
        var rockets = mc.level.getEntitiesOfClass(RocketEntity.class, mc.player.getBoundingBox().inflate(64.0));
        SpaceHorizons.LOGGER.info("{}CLIENTE {}: dim={} jogador montado em={} entidades de foguete={} tela={} pos=({}, {}, {})", TAG, label,
                mc.level.dimension().identifier(), mc.player.getVehicle() == null ? "nada" : mc.player.getVehicle().getType(), rockets.size(),
                mc.gui.screen() == null ? "nenhuma" : mc.gui.screen().getClass().getSimpleName(), f(mc.player.getX()), f(mc.player.getY()),
                f(mc.player.getZ()));
        for (RocketEntity r : rockets) {
            SpaceHorizons.LOGGER.info("{}CLIENTE {}: foguete sincronizado: combustível={} modo={} acelerador={} sas={} calor={} blocos no retrato={}", TAG, label,
                    r.syncedFuelMb(), r.flightMode(), r.throttle(), r.sasMode(), r.heat(), r.portrait().blockCount());
        }
    }

    // ---------------------------------------------------------------- o piloto de teste

    private static void registerTick() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, e -> {
            tickStartNanos = System.nanoTime();
            reapplyControl(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            try {
                onTick(e.getServer());
            } catch (RuntimeException ex) {
                SpaceHorizons.LOGGER.error(TAG + "piloto de teste falhou", ex);
            }
            if (armed && stage < 90) {
                double ms = (System.nanoTime() - tickStartNanos) / 1.0e6;
                msptSum += ms;
                msptMax = Math.max(msptMax, ms);
                if (++msptCount >= 200) {
                    SpaceHorizons.LOGGER.info("{}MSPT (200 ticks): média={} ms, máximo={} ms", TAG, f(msptSum / msptCount), f(msptMax));
                    msptSum = 0.0;
                    msptMax = 0.0;
                    msptCount = 0;
                }
            }
        });
    }

    /**
     * O cliente real (o jogador do autoteste) também manda o pacote de controle, com o acelerador que ele guarda (depois de uma
     * travessia de dimensão o cliente o reinicia com o valor sincronizado do foguete: 0,84 no meio de uma frenagem), a cada 5 ticks;
     * esse pacote sobrescrevia o comando do piloto de teste em 1 de cada 5 ticks e o foguete queimava combustível sem o piloto
     * mandar. Antes do passo, o comando do piloto de teste volta a valer (sem as flags de ação: as bordas não se repetem).
     */
    private static void reapplyControl(MinecraftServer server) {
        RocketControlPayload mine = lastApplied;
        if (!armed || mine == null || realWindow() || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        if (server.getPlayerList().getPlayers().get(0).getVehicle() instanceof RocketEntity rocket
                && !rocket.flight().control(rocket.level().getGameTime()).equals(mine)) {
            rocket.applyControl(new RocketControlPayload(mine.pitch(), mine.yaw(), mine.roll(), mine.throttle(), (byte) 0));
        }
    }

    private static void onTick(MinecraftServer server) {
        if (!armed || stage >= 90 || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        clockNow = SpaceClock.ticks(player.level());
        totalTicks++;
        stageTicks++;
        if (!(player.getVehicle() instanceof RocketEntity rocket)) {
            if (variant == 'B' && stage >= 2 && !reached("crashed_b")) {
                crashReport(player);
            } else if (stage < 90 && stage > 0 && !reached("landed_a") && variant == 'A') {
                SpaceHorizons.LOGGER.warn("{}o jogador NÃO está montado no foguete no tick {} (veículo={})", TAG, totalTicks, player.getVehicle());
            }
            return;
        }
        Snapshot snap = rocket.flight().snapshot();
        if (snap == null) {
            return;
        }
        Control c = new Control();
        switch (variant) {
            case 'A', 'R', 'L' -> flightA(rocket, snap, c, player);
            case 'B' -> flightB(rocket, snap, c, player);
            default -> flightC(rocket, snap, c, player); // C e D
        }
        // pulso de SAS: uma borda por pacote (o bit sobe num tick e desce no seguinte)
        int flags = c.flags;
        if (pendingSas > 0 && !sasPulse) {
            flags |= RocketControlPayload.FLAG_NEXT_SAS;
            sasPulse = true;
            pendingSas--;
        } else {
            sasPulse = false;
        }
        if (real()) {
            realTick(rocket, snap, player);
        }
        if (realWindow()) {
            return; // só o cliente real comanda: o piloto de teste não manda nada
        }
        lastApplied = new RocketControlPayload((float) c.pitch, (float) c.yaw, 0.0F, (float) c.throttle, (byte) flags);
        rocket.applyControl(lastApplied);
        if (totalTicks % 20 == 0) {
            log(rocket, snap, c, player);
        }
    }

    /** Voos com janela do piloto real: R (janelas normais) e L (cliente lento: ver {@link #setClientLag}). */
    private static boolean real() {
        return variant == 'R' || variant == 'L';
    }

    private static volatile int clientLagMs;
    private static boolean lagRegistered;

    /** Simula um cliente lento (poucos ticks por segundo), dormindo a cada tick do cliente; 0 desliga. */
    static void setClientLag(int ms) {
        clientLagMs = ms;
        if (ms > 0 && !lagRegistered) {
            lagRegistered = true;
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Pre.class, e -> {
                int lag = clientLagMs;
                if (lag > 0) {
                    try {
                        Thread.sleep(lag);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
        SpaceHorizons.LOGGER.info("{}cliente lento: {} ms a mais por tick do cliente", TAG, ms);
    }

    private static boolean realWindow() {
        return real() && (realPhase == 1 || realPhase == 3);
    }

    /**
     * Voo R: o voo A com duas janelas em que SÓ o piloto real (teclas no cliente, RocketPilot, pacote) comanda o acelerador: a 1ª no
     * espaço logo depois da travessia Terra para espaço, a 2ª na Terra logo depois da reentrada. O servidor registra a cada 5 ticks o
     * comando recebido, o acelerador aplicado, o combustível e a velocidade.
     */
    private static void realTick(RocketEntity rocket, Snapshot snap, ServerPlayer player) {
        boolean inSpace = snap.zoneBody() == null;
        if (realPhase == 0 && stage == 2 && inSpace && snap.mode() == FlightMode.FLIGHT && stageTicks > 40) {
            realPhase = 1;
            realTicks = 0;
            mark("real1_start");
        } else if (variant == 'R' && realPhase == 2 && stage == 4 && !inSpace && stageTicks > 15) {
            realPhase = 3;
            realTicks = 0;
            mark("real2_start");
        }
        if (!realWindow()) {
            return;
        }
        realTicks++;
        if (realTicks % 5 == 1) {
            long now = rocket.level().getGameTime();
            RocketControlPayload ctl = rocket.flight().control(now);
            FlightState st = snap.state();
            SpaceHorizons.LOGGER.info("{}REAL fase {} t={} dim={} comando recebido: acel={} flags={} (idade {} ticks, pacotes {}) | aplicado: snapshot={} sync={} | comb={} mB "
                    + "| v={} vv={} | sentado={} segurado={}", TAG, realPhase, realTicks, rocket.level().dimension().identifier(), f(ctl.throttle()), ctl.flags(),
                    now - rocket.flight().lastControlTick(), rocket.flight().controlPackets(), f(snap.throttle()), f(rocket.throttle()), f(rocket.fuelMb()),
                    st == null ? "-" : f(st.v().length()), st == null ? "-" : f(st.v().dot(st.r().unit())), player.getVehicle() == rocket, rocket.isHeld());
        }
        if (realTicks >= (realPhase == 1 ? (variant == 'L' ? 500 : 170) : 60)) {
            mark(realPhase == 1 ? "real1_end" : "real2_end");
            realPhase = realPhase == 1 ? 2 : 4;
        }
    }

    private static final class Control {
        double pitch;
        double yaw;
        double throttle;
        int flags;
    }

    private static void sas(int pulses) {
        pendingSas += pulses;
    }

    private static void next(int newStage) {
        SpaceHorizons.LOGGER.info("{}ESTÁGIO {} -> {} no tick {} (voo {})", TAG, stage, newStage, totalTicks, variant);
        stage = newStage;
        stageTicks = 0;
    }

    private static void timeout(int limit, String what) {
        if (stageTicks > limit) {
            SpaceHorizons.LOGGER.error("{}TEMPO ESGOTADO no estágio {} ({}) do voo {}; abortando o voo", TAG, stage, what, variant);
            mark("failed");
            mark("end");
            stage = 99;
        }
    }

    // ---------------------------------------------------------------- voo A: salto suborbital com pouso suave

    private static void flightA(RocketEntity rocket, Snapshot snap, Control c, ServerPlayer player) {
        if (snap.mode() == FlightMode.LANDED && stage >= 2 && stageTicks > 3) {
            reportLanding(rocket, snap, player);
            mark("landed_a");
            mark("end_a");
            stage = 90;
            return;
        }
        RocketTelemetryPayload t = telemetry(rocket, snap, player);
        switch (stage) {
            case 0 -> { // no chão: liga o SAS (desligado -> estabilizar) e acelera
                if (stageTicks == 1) {
                    sas(1);
                }
                if (stageTicks > 5) {
                    next(1);
                }
            }
            case 1 -> { // subida com curva de gravidade
                c.throttle = 1.0;
                if (t == null) {
                    return;
                }
                mark("liftoff_a");
                steerGravityTurn(snap, c, t.altitude(), 150.0, 1500.0, 25.0);
                if (t.altitude() > 800.0) {
                    mark("climb_a");
                }
                if (t.apoapsisAltitude() >= 6_000.0) {
                    c.throttle = 0.0;
                    SpaceHorizons.LOGGER.info("{}CORTE do motor: apoastro previsto {} blocos, altitude {} v={} combustível={} mB", TAG,
                            f(t.apoapsisAltitude()), f(t.altitude()), f(t.orbitalSpeed()), f(t.fuelMb()));
                    next(2);
                }
                timeout(900, "subida");
            }
            case 2 -> { // sobe de graça até o apoastro, segurando a atitude
                if (t == null) {
                    return;
                }
                if (snap.zoneBody() == null) {
                    mark("space_a");
                }
                mark("climb_a");
                if (t.verticalSpeed() < -2.0 && snap.zoneBody() == null) {
                    mark("apogee_a");
                    SpaceHorizons.LOGGER.info("{}APOASTRO atingido no espaço: altitude {} vertical {} horizontal {}", TAG, f(t.altitude()),
                            f(t.verticalSpeed()), f(t.horizontalSpeed()));
                    sas(2); // estabilizar -> prógrado -> retrógrado
                    next(3);
                }
                timeout(2_500, "voo balístico até o apoastro");
            }
            case 3 -> { // cai de retrógrado até voltar à dimensão da Terra (freando como no pouso: a troca é a 1.200 sobre o terreno)
                if (t != null) {
                    landingControl(rocket, snap, c, t, player);
                }
                if (snap.zoneBody() != null) {
                    mark("reentry_a");
                    SpaceHorizons.LOGGER.info("{}REENTRADA na dimensão da Terra: altitude {} vertical {}", TAG, f(t.altitude()), f(t.verticalSpeed()));
                    next(4);
                }
                timeout(2_500, "descida no espaço");
            }
            case 4 -> { // freia com o motor, abre o paraquedas e pousa
                if (t == null) {
                    return;
                }
                landingControl(rocket, snap, c, t, player);
                if (snap.mode() == FlightMode.PARACHUTE) {
                    mark("chute_a");
                }
                timeout(3_000, "pouso");
            }
            default -> {
            }
        }
    }

    /** O controle de pouso: perfil de velocidade que chega a 5 b/t a 300 blocos do chão, paraquedas abaixo de 8 b/t. */
    private static void landingControl(RocketEntity rocket, Snapshot snap, Control c, RocketTelemetryPayload t, ServerPlayer player) {
        FlightState st = snap.state();
        if (st == null) {
            return;
        }
        ServerLevel level = player.level();
        // na dimensão de espaço (vazia) não há terreno sob o foguete: a altura é a altitude sobre o nível do mar
        double clearance = snap.zoneBody() == null ? 10_000.0 : clearance(level, rocket);
        if (clearance >= 10_000.0) {
            // chunk sob o foguete ainda não carregado (a deriva horizontal de ~8 b/t leva o foguete a milhares de blocos da plataforma e,
            // com a Terra 10x maior, o chunk só chegava aos ~850 de altitude, tarde demais para frear): usa a altitude do nível do mar
            clearance = Math.max(0.0, t.altitude() - 40.0);
        }
        // a velocidade em relação ao chão (a reentrada vinda de órbita chega quase na horizontal): o freio retrógrado a corta inteira
        double down = Math.hypot(t.verticalSpeed(), t.horizontalSpeed());
        double target = Math.sqrt(25.0 + 2.0 * 0.15 * Math.max(0.0, clearance - 300.0)); // 0,15 b/t²: abaixo da desaceleração líquida do foguete cheio (~0,23)
        if (down > target + 0.2 && clearance < 3_000.0) {
            c.throttle = Math.min(1.0, (down - target) / 2.0);
        }
        if (clearance < 330.0 && Math.hypot(t.verticalSpeed(), t.horizontalSpeed()) < 8.0) { // relativa ao chão (a orbital, inercial, inclui a rotação da Terra)
            c.flags |= RocketControlPayload.FLAG_PARACHUTE;
        }
        if (clearance < 330.0) {
            c.throttle = down > 1.5 && snap.mode() != FlightMode.PARACHUTE ? Math.min(1.0, (down - 5.0) / 2.0) : 0.0;
            c.throttle = Math.max(0.0, c.throttle);
        }
    }

    /** Distância da base do foguete ao terreno sob ele. */
    private static double clearance(ServerLevel level, RocketEntity rocket) {
        int bx = (int) Math.floor(rocket.getX());
        int bz = (int) Math.floor(rocket.getZ());
        if (!level.hasChunk(bx >> 4, bz >> 4)) {
            return 10_000.0;
        }
        double top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return rocket.getY() - rocket.portrait().sizeY / 2.0 - top;
    }

    private static void reportLanding(RocketEntity rocket, Snapshot snap, ServerPlayer player) {
        SpaceHorizons.LOGGER.info("{}POUSO SUAVE confirmado no tick {}: pos=({}, {}, {}) combustível={} mB (início {}), massa atual={} t, inclinação={} graus, "
                + "piloto montado={}, saúde={}", TAG, totalTicks, f(rocket.getX()), f(rocket.getY()), f(rocket.getZ()), f(rocket.fuelMb()),
                "48000", f(rocket.stats().wetMass(rocket.fuelMb())), f(rocket.tiltDegrees()), player.getVehicle() == rocket, f(player.getHealth()));
    }

    // ---------------------------------------------------------------- voo B: batida

    private static void flightB(RocketEntity rocket, Snapshot snap, Control c, ServerPlayer player) {
        RocketTelemetryPayload t = telemetry(rocket, snap, player);
        switch (stage) {
            case 0 -> {
                if (stageTicks == 1) {
                    sas(1);
                    lastHealth = player.getHealth();
                    SpaceHorizons.LOGGER.info("{}B: saúde do piloto antes={} modo de jogo={}", TAG, f(lastHealth), player.gameMode());
                }
                if (stageTicks > 5) {
                    next(1);
                }
            }
            case 1 -> {
                c.throttle = 1.0;
                if (t != null && t.verticalSpeed() >= 1.3) {
                    c.throttle = 0.0;
                    SpaceHorizons.LOGGER.info("{}B: corte com {} b/t a {} blocos da plataforma", TAG, f(t.verticalSpeed()), f(rocket.getY() - padY));
                    next(2);
                }
                timeout(200, "impulso curto");
            }
            case 2 -> {
                if (snap.mode() == FlightMode.FLIGHT && t != null && stageTicks % 5 == 0) {
                    SpaceHorizons.LOGGER.info("{}B: caindo, altura={} vertical={}", TAG, f(rocket.getY() - padY), f(t.verticalSpeed()));
                }
                timeout(400, "queda");
            }
            default -> {
            }
        }
    }

    private static void crashReport(ServerPlayer player) {
        mark("crashed_b");
        int stacks = 0;
        int items = 0;
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(15.0))) {
            stacks++;
            items += item.getItem().getCount();
        }
        int rockets = player.level().getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(200.0)).size();
        SpaceHorizons.LOGGER.info("{}B: BATIDA confirmada no tick {}: entidades de foguete restantes={} (esperado 0), itens no chão={} em {} pilhas, saúde do piloto {} -> {}, "
                + "piloto montado={}, pos do piloto=({}, {}, {})", TAG, totalTicks, rockets, items, stacks, f(lastHealth), f(player.getHealth()),
                player.getVehicle() != null, f(player.getX()), f(player.getY()), f(player.getZ()));
        mark("end_b");
        stage = 90;
    }

    // ---------------------------------------------------------------- voo C: órbita

    private static void flightC(RocketEntity rocket, Snapshot snap, Control c, ServerPlayer player) {
        if (snap.mode() == FlightMode.LANDED && stage >= 8) {
            SpaceHorizons.LOGGER.info("{}C: POUSO depois da órbita no tick {}: pos=({}, {}, {}) combustível={} mB", TAG, totalTicks, f(rocket.getX()),
                    f(rocket.getY()), f(rocket.getZ()), f(rocket.fuelMb()));
            mark("landed_" + suffix());
            mark("end_" + suffix());
            stage = 90;
            return;
        }
        RocketTelemetryPayload t = telemetry(rocket, snap, player);
        switch (stage) {
            case 0 -> {
                if (stageTicks == 1) {
                    sas(1);
                }
                if (stageTicks > 5) {
                    next(1);
                }
            }
            case 1 -> {
                c.throttle = 1.0;
                if (t == null) {
                    return;
                }
                mark("liftoff_" + suffix());
                // com a Terra 10x maior a órbita pede ~91 b/t horizontais (e ~85 de Δv de circularização no apoastro): a curva vai
                // até a horizontal (90 graus, perfil de raiz como no AscentTest) aos 3.000 de altitude, para o apoastro de 6.000
                // chegar já com a maior parte da velocidade lateral e a circularização ser curta
                steerGravityTurn(snap, c, t.altitude(), 100.0, 3_000.0, 90.0, 0.5);
                if (t.apoapsisAltitude() >= 6_000.0) {
                    c.throttle = 0.0;
                    SpaceHorizons.LOGGER.info("{}C: CORTE: apoastro {} blocos, periastro {}, altitude {}, v horizontal {}, combustível={} mB", TAG,
                            f(t.apoapsisAltitude()), f(t.periapsisAltitude()), f(t.altitude()), f(t.horizontalSpeed()), f(t.fuelMb()));
                    sas(1); // estabilizar -> prógrado
                    next(2);
                }
                timeout(900, "subida");
            }
            case 2 -> {
                if (t == null) {
                    return;
                }
                if (snap.zoneBody() == null) {
                    mark("space_" + suffix());
                }
                if (snap.zoneBody() == null && t.timeToApoapsis() < 8.0 && t.verticalSpeed() > -0.5) {
                    SpaceHorizons.LOGGER.info("{}C: no apoastro: altitude {} v horizontal {} tempo até o apoastro {}", TAG, f(t.altitude()),
                            f(t.horizontalSpeed()), f(t.timeToApoapsis()));
                    next(3);
                }
                timeout(3_000, "balístico até o apoastro");
            }
            case 3 -> { // circulariza: queima prógrada até o periastro passar de 5.200
                if (t == null) {
                    return;
                }
                c.throttle = Math.max(0.15, Math.min(1.0, (5_200.0 - t.periapsisAltitude()) / 400.0));
                if (t.periapsisAltitude() >= 5_200.0 || t.fuelMb() < 50.0) {
                    c.throttle = 0.0;
                    SpaceHorizons.LOGGER.info("{}C: ÓRBITA: apoastro {} periastro {} inclinação {} graus, período≈{} ticks, combustível={} mB, Δv restante={}", TAG,
                            f(t.apoapsisAltitude()), f(t.periapsisAltitude()), f(t.inclination()), f(2.0 * Math.PI
                                    * Math.sqrt(Math.pow((t.apoapsisAltitude() + t.periapsisAltitude()) / 2.0 + BodyPhysics.of(SolarSystem.EARTH).radius(), 3)
                                            / BodyPhysics.of(SolarSystem.EARTH).mu())),
                            f(t.fuelMb()), f(t.deltaV()));
                    next(4);
                }
                timeout(600, "circularização");
            }
            case 4 -> { // fica em órbita um tempo, vendo se a órbita se sustenta no espaço
                if (stageTicks == 1) {
                    mark("orbit_" + suffix());
                }
                if (stageTicks == 200 && t != null) {
                    SpaceHorizons.LOGGER.info("{}C: 200 ticks DEPOIS da circularização: altitude {} apoastro {} periastro {} zona={} (espaço = null)", TAG,
                            f(t.altitude()), f(t.apoapsisAltitude()), f(t.periapsisAltitude()), snap.zoneBody());
                    mark("orbit_stable_" + suffix());
                    sas(1); // prógrado -> retrógrado
                    next(5);
                }
            }
            case 5 -> { // alinha o nariz ao retrógrado
                if (stageTicks > 60) {
                    next(6);
                }
            }
            case 6 -> { // freia: queima retrógrada até o periastro entrar no planeta
                if (t == null) {
                    return;
                }
                c.throttle = t.periapsisAltitude() > 500.0 ? 1.0 : 0.3;
                if (t.periapsisAltitude() < -300.0 || t.fuelMb() < 50.0) {
                    c.throttle = 0.0;
                    SpaceHorizons.LOGGER.info("{}C: DESÓRBITA: periastro {} apoastro {} combustível={} mB", TAG, f(t.periapsisAltitude()), f(t.apoapsisAltitude()),
                            f(t.fuelMb()));
                    next(7);
                }
                timeout(600, "queima de desórbita");
            }
            case 7 -> { // desce até a dimensão da Terra
                // a dimensão da Terra só carrega o terreno a velocidade baixa: no espaço, abaixo da altitude de entrada, freia de
                // retrógrado até a horizontal ao chão ficar abaixo do limite de entrada (o piloto faz o mesmo)
                // voo D: o jogador comum, que NÃO freia no espaço: cai com a velocidade horizontal que a órbita deixou e só usa o motor
                // dentro da atmosfera (landingControl); a pré-carga do local de pouso é que tem de dar conta do terreno
                if (variant == 'D' && t != null) {
                    landingControl(rocket, snap, c, t, player);
                }
                if (variant != 'D' && t != null && snap.zoneBody() == null && t.altitude() < 4_000.0 && t.horizontalSpeed() > 15.0 && t.fuelMb() > 50.0) {
                    c.throttle = Math.min(1.0, (t.horizontalSpeed() - 12.0) / 4.0);
                }
                if (snap.zoneBody() == null && t != null && t.altitude() < 3_000.0) {
                    mark("descent_" + suffix());
                }
                if (snap.zoneBody() == null && t != null && t.altitude() < 1_300.0) {
                    mark("descent1200_" + suffix());
                }
                if (snap.zoneBody() != null) {
                    mark("reentry_" + suffix());
                    SpaceHorizons.LOGGER.info("{}C: REENTRADA na dimensão da Terra: altitude {} vertical {} horizontal {}", TAG, f(t.altitude()),
                            f(t.verticalSpeed()), f(t.horizontalSpeed()));
                    next(8);
                }
                timeout(16_000, "descida da órbita (a órbita baixa tem período de ~7.800 ticks)");
            }
            case 8 -> {
                if (t == null) {
                    return;
                }
                landingControl(rocket, snap, c, t, player);
                if (snap.mode() == FlightMode.PARACHUTE) {
                    mark("chute_" + suffix());
                }
                timeout(4_000, "pouso depois da órbita");
            }
            default -> {
            }
        }
    }

    /** Sufixo dos marcos do voo de órbita: {@code c} (com freio no espaço) ou {@code d} (sem freio). */
    private static String suffix() {
        return variant == 'D' ? "d" : "c";
    }

    // ---------------------------------------------------------------- utilidades do piloto

    /** A telemetria do estado de voo (null pousado). */
    private static RocketTelemetryPayload telemetry(RocketEntity rocket, Snapshot snap, ServerPlayer player) {
        FlightState st = snap.state();
        if (st == null) {
            return null;
        }
        return RocketTelemetryBuilder.build(st, snap.mode(), snap.sas(), snap.throttle(), snap.zoneBody() == null, rocket.stats(),
                SpaceClock.ticks(player.level()), CLOCK_RATE, false);
    }

    /**
     * Curva de gravidade: vertical até {@code startAlt}, depois inclina o nariz para o LESTE, de 0 até {@code maxDeg} graus
     * conforme a altitude vai de {@code startAlt} a {@code fullAlt}. O comando é fechado em malha: erro entre o nariz e a
     * direção desejada, no referencial do corpo do foguete (arfagem em +X, guinada em +Z).
     */
    private static void steerGravityTurn(Snapshot snap, Control c, double altitude, double startAlt, double fullAlt, double maxDeg) {
        steerGravityTurn(snap, c, altitude, startAlt, fullAlt, maxDeg, 1.0);
    }

    /** Idem, com a inclinação proporcional à fração de altitude elevada a {@code exponent} (0,5 inclina mais cedo). */
    private static void steerGravityTurn(Snapshot snap, Control c, double altitude, double startAlt, double fullAlt, double maxDeg,
            double exponent) {
        FlightState st = snap.state();
        double frac = Math.pow(Math.max(0.0, Math.min(1.0, (altitude - startAlt) / (fullAlt - startAlt))), exponent);
        double theta = Math.toRadians(maxDeg) * frac;
        V3 up = st.r().unit();
        V3 east = eastAt(snap);
        V3 desired = up.times(Math.cos(theta)).plus(east.times(Math.sin(theta)));
        steer(snap, desired, c);
    }

    private static V3 eastAt(Snapshot snap) {
        FlightState st = snap.state();
        SolarSystem.Body body = st.body();
        Local l = FrameBridge.toDimension(body, clockNow, CLOCK_RATE, st.r(), st.v());
        Matrix3d m = AttitudeMath.localToSpaceMatrix(body, clockNow, l.x(), l.z());
        Vector3d e = m.transform(new Vector3d(1.0, 0.0, 0.0));
        return new V3(e.x, e.y, e.z);
    }

    /** Comando proporcional de arfagem e guinada para apontar o nariz para {@code desired} (inercial). */
    private static void steer(Snapshot snap, V3 desired, Control c) {
        V3 nose = AttitudeMath.noseDirection(snap.attitude());
        double angle = nose.angleTo(desired);
        if (angle < 1.0e-4) {
            return;
        }
        V3 axis = nose.cross(desired);
        if (axis.length() < 1.0e-9) {
            axis = nose.cross(new V3(1.0, 0.0, 0.0));
        }
        axis = axis.unit();
        Vector3d inBody = snap.attitude().transformInverse(axis.x(), axis.y(), axis.z(), new Vector3d());
        double k = 0.35 * angle / AttitudeControl.MAX_SPIN;
        c.pitch = Math.max(-1.0, Math.min(1.0, inBody.x * k));
        c.yaw = Math.max(-1.0, Math.min(1.0, inBody.z * k));
    }

    private static void log(RocketEntity rocket, Snapshot snap, Control c, ServerPlayer player) {
        RocketTelemetryPayload t = telemetry(rocket, snap, player);
        if (t == null) {
            SpaceHorizons.LOGGER.info("{}[{}] t={} estágio={} POUSADO pos=({}, {}, {}) combustível={}", TAG, variant, totalTicks, stage, f(rocket.getX()),
                    f(rocket.getY()), f(rocket.getZ()), f(rocket.fuelMb()));
            return;
        }
        SpaceHorizons.LOGGER.info("{}[{}] t={} est={} pouso={}/{}% dim={} modo={} sas={} alt={} vv={} vh={} v={} apo={} peri={} incl={} tta={} comb={} mB massa={} t "
                + "acel={}→{} TWR={} Δv={} calor={} inclin.nariz={} pos=({}, {}, {}) sentado={}", TAG, variant, totalTicks, stage,
                rocket.flight().landing().state(), rocket.flight().landing().percent(),
                snap.zoneBody() == null ? "espaço" : snap.zoneBody().planet().id().getPath(), snap.mode(), snap.sas(), f(t.altitude()),
                f(t.verticalSpeed()), f(t.horizontalSpeed()), f(t.orbitalSpeed()), f(t.apoapsisAltitude()), f(t.periapsisAltitude()), f(t.inclination()),
                f(t.timeToApoapsis()), f(t.fuelMb()), f(rocket.stats().wetMass(t.fuelMb())), f(c.throttle), f(t.throttle()), f(t.twr()), f(t.deltaV()),
                f(t.heat()), f(AttitudeMath.tiltDegrees(snap.attitude(), snap.state().r().unit())), f(rocket.getX()), f(rocket.getY()), f(rocket.getZ()),
                player.getVehicle() == rocket);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
