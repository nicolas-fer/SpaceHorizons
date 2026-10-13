package com.spacehorizons.network;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.map.PlanetMap;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Cliente do mapa do planeta: monta os pedaços, descomprime numa thread de background (o mapa passa de 1 MiB) e
 * publica em {@code PlanetTerrains}; a malha orbital e os corpos reconstroem quando a versão muda. Ao sair do mundo o
 * mapa é descartado (o próximo mundo tem outra semente). Registrada sozinha pelo {@link EventBusSubscriber}.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class PlanetMapClient {
    private static final PlanetMapChunks.Assembler ASSEMBLER = new PlanetMapChunks.Assembler();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SpaceHorizons-PlanetMapReceive");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    /** Incrementa a cada saída do mundo: descarta resultados de recebimentos que começaram no mundo anterior. */
    private static volatile int session;

    private PlanetMapClient() {
    }

    @SubscribeEvent
    static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {
        // roda na thread principal; só copia o pedaço, o trabalho pesado vai para o WORKER
        event.register(PlanetMapPayload.TYPE, (payload, context) -> {
            byte[] complete = ASSEMBLER.accept(payload);
            if (complete == null) {
                return;
            }
            String planetId = payload.planet();
            int startedIn = session;
            WORKER.execute(() -> publish(planetId, complete, startedIn));
        });
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        session++;
        ASSEMBLER.reset();
        PlanetTerrains.clearMaps();
    }

    private static void publish(String planetId, byte[] data, int startedIn) {
        try {
            Identifier id = Identifier.tryParse(planetId);
            PlanetDefinition planet = id == null ? null : Planets.byId(id).orElse(null);
            if (planet == null) {
                SpaceHorizons.LOGGER.warn("Mapa de planeta desconhecido recebido: {}", planetId);
                return;
            }
            PlanetMap map = PlanetMap.deserialize(planet, data);
            if (startedIn != session) {
                return;
            }
            PlanetMap current = PlanetTerrains.map(planet);
            if (map.sameContent(current)) {
                // servidor integrado: o servidor já publicou o mesmo mapa neste processo
                return;
            }
            PlanetTerrains.setMap(map);
            SpaceHorizons.LOGGER.info("Mapa de {} recebido: {}x{}, {} KiB", planetId, map.width(), map.height(), data.length / 1024);
        } catch (IOException | RuntimeException e) {
            SpaceHorizons.LOGGER.error("Mapa de {} recebido está inválido", planetId, e);
        }
    }
}
