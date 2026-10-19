package com.spacehorizons.rocket;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.rocket.FlightMachine.Snapshot;
import com.spacehorizons.rocket.ImpactPredictor.Prediction;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.Zones;
import com.spacehorizons.space.travel.TravelTransfers;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

/**
 * O encaixe da pré-carga do local de pouso ({@link LandingPreloader}) com o mundo: uma vez por segundo, com o foguete descendo,
 * prevê o impacto ({@link ImpactPredictor}) e pede, na dimensão do corpo, os chunks do ponto previsto e do trecho final da
 * descida com tickets {@code PORTAL} de raio 0 (assíncronos: o servidor gera em segundo plano, este código nunca espera um chunk).
 * O ticket {@code PORTAL} expira em 300 ticks; a renovação a cada segundo o mantém, e soltar é parar de renovar mais o
 * {@code remove} explícito. Pousou, bateu ou sem previsão: solta tudo.
 */
final class LandingPreloadDriver {
    private LandingPreloadDriver() {
    }

    /** Um tick: atualiza a previsão (1 por segundo) ou só o progresso (a cada 5 ticks). */
    static void tick(MinecraftServer server, RocketEntity rocket, RocketFlight f, Snapshot snap) {
        FlightState state = snap.state();
        if (!snap.flying() || state == null) {
            release(f);
            return;
        }
        f.landingTicks++;
        ServerLevel level = f.landingLevel;
        if (f.landingTicks % LandingPreloader.UPDATE_INTERVAL != 0) {
            if (level != null && f.landingTicks % 5 == 0 && f.landing.state() != LandingPreloader.State.IDLE) {
                f.landing.refresh(sink(level));
            }
            return;
        }
        PlanetDefinition planet = state.body().planet();
        level = TravelTransfers.findLevel(server, planet);
        if (level == null) {
            return;
        }
        if (f.landingLevel != null && f.landingLevel != level) {
            release(f);
        }
        f.landingLevel = level;
        Prediction prediction = null;
        if (LandingPreloader.worthPredicting(state.altitude(), Zones.exitAltitude(planet), state.verticalSpeed())) {
            var terrain = PlanetTerrains.get(planet);
            prediction = ImpactPredictor.predict(state, rocket.stats(), f.lastClock,
                    (x, z) -> terrain.surfaceHeight(Mth.floor(x), Mth.floor(z)) + 1.0);
            if (prediction != null && prediction.ticks() > LandingPreloader.MAX_TICKS_TO_IMPACT) {
                prediction = null;
            }
        }
        boolean wasIdle = f.landing.state() == LandingPreloader.State.IDLE;
        f.landing.update(prediction, planet.circumferenceBlocks(), sink(level));
        if (prediction != null && wasIdle) {
            SpaceHorizons.LOGGER.info("Foguete: impacto previsto em ({}, {}) em {} ticks; pré-carregando {} chunks", Mth.floor(prediction.x()),
                    Mth.floor(prediction.z()), prediction.ticks(), f.landing.requestedCount());
        }
    }

    /** Solta todos os tickets da pré-carga (pouso, batida, desmontagem). */
    static void release(RocketFlight f) {
        ServerLevel level = f.landingLevel;
        if (level != null && (f.landing.state() != LandingPreloader.State.IDLE || f.landing.requestedCount() > 0)) {
            f.landing.releaseAll(sink(level));
        }
        f.landingTicks = 0;
    }

    /** Estado para a telemetria: 0 nada, 1 carregando, 2 pronto. */
    static byte telemetryState(RocketFlight f) {
        return (byte) f.landing.state().ordinal();
    }

    private static LandingPreloader.Sink sink(ServerLevel level) {
        return new LandingPreloader.Sink() {
            @Override
            public void add(int chunkX, int chunkZ) {
                level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, new ChunkPos(chunkX, chunkZ), 0);
            }

            @Override
            public void remove(int chunkX, int chunkZ) {
                level.getChunkSource().removeTicketWithRadius(TicketType.PORTAL, new ChunkPos(chunkX, chunkZ), 0);
            }

            @Override
            public boolean loaded(int chunkX, int chunkZ) {
                return level.hasChunk(chunkX, chunkZ);
            }
        };
    }
}
