package com.spacehorizons.client.rocket;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.dh.DhFastFlightConfig;
import com.spacehorizons.rocket.FastFlight;
import com.spacehorizons.rocket.RocketTelemetryPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * As configurações do cliente do piloto durante o voo rápido ({@link FastFlight}): distância de visão e de simulação reduzidas
 * (no mundo local o servidor integrado as lê das opções do cliente a cada tick; num servidor dedicado o cliente as reenvia com
 * {@link Options#broadcastOptions}) e a geração de LOD do Distant Horizons pausada ({@link DhFastFlightConfig}). Tudo volta ao
 * que o jogador tinha ao frear, pousar, descer do foguete ou sair do mundo.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class FastFlightClient {
    private static final FastFlight FLIGHT = new FastFlight();
    private static boolean applied;
    private static int savedView;
    private static int savedSimulation;
    private static boolean dhPaused;
    /** Só dev (autoteste "jump"): desliga o voo rápido para comparar a distância de visão reduzida com a do jogador. */
    private static boolean disabledForTest;

    private FastFlightClient() {
    }

    /** Só dev: liga/desliga o voo rápido (desligado, o cliente do piloto nunca reduz a distância de visão nem pausa o DH). */
    public static void setDisabledForTest(boolean disabled) {
        disabledForTest = disabled;
    }

    /** Em voo rápido com as configurações reduzidas aplicadas (para o autoteste). */
    public static boolean isApplied() {
        return applied;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!RocketPilot.isPiloting()) {
            FLIGHT.reset();
            restore(mc);
            return;
        }
        RocketTelemetryPayload t = RocketTelemetryClient.current();
        if (t != null) {
            FLIGHT.update(Math.hypot(t.verticalSpeed(), t.horizontalSpeed()));
        }
        if (FLIGHT.fast() && !disabledForTest) {
            apply(mc);
        } else {
            restore(mc);
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FLIGHT.reset();
        restore(Minecraft.getInstance());
    }

    private static void apply(Minecraft mc) {
        if (!applied) {
            savedView = mc.options.renderDistance().get();
            savedSimulation = mc.options.simulationDistance().get();
            applied = true;
            SpaceHorizons.LOGGER.info("Voo rápido: distância de visão {} -> {} e simulação {} -> {} chunks", savedView,
                    FastFlight.reduced(savedView, FastFlight.FAST_VIEW_DISTANCE), savedSimulation,
                    FastFlight.reduced(savedSimulation, FastFlight.FAST_SIMULATION_DISTANCE));
        }
        setDistances(mc, FastFlight.reduced(savedView, FastFlight.FAST_VIEW_DISTANCE),
                FastFlight.reduced(savedSimulation, FastFlight.FAST_SIMULATION_DISTANCE));
        if (!dhPaused && ModList.get().isLoaded("distanthorizons")) {
            dhPaused = DhFastFlightConfig.pause();
            if (dhPaused) {
                SpaceHorizons.LOGGER.info("Voo rápido: geração de LOD do Distant Horizons pausada");
            }
        }
    }

    private static void restore(Minecraft mc) {
        if (dhPaused) {
            DhFastFlightConfig.resume();
            dhPaused = false;
            SpaceHorizons.LOGGER.info("Voo rápido: geração de LOD do Distant Horizons retomada");
        }
        if (applied) {
            setDistances(mc, savedView, savedSimulation);
            applied = false;
            SpaceHorizons.LOGGER.info("Voo rápido terminou: distância de visão {} e simulação {} restauradas", savedView, savedSimulation);
        }
    }

    private static void setDistances(Minecraft mc, int view, int simulation) {
        boolean changed = false;
        if (mc.options.renderDistance().get() != view) {
            mc.options.renderDistance().set(view);
            changed = true;
        }
        if (mc.options.simulationDistance().get() != simulation) {
            mc.options.simulationDistance().set(simulation);
            changed = true;
        }
        if (changed && mc.player != null) {
            mc.options.broadcastOptions();
        }
    }
}
