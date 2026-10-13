package com.spacehorizons.planet.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.network.PlanetMapNetwork;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.TerrainStyle;
import com.spacehorizons.worldgen.PlanetChunkGenerator;
import com.spacehorizons.worldgen.density.SeamMath;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Servidor do mapa do planeta: na primeira vez que o nível da Terra ticka, carrega o mapa do cache do mundo
 * ({@code data/spacehorizons/planetmap_<planeta>.bin}, válido se bater semente, tamanho e gerador) ou o calcula em
 * threads de background a partir do gerador real, grava o cache e publica em {@link PlanetTerrains}. Quando fica
 * pronto envia aos jogadores; quem entra depois recebe no login. Nada bloqueia o servidor: enquanto o mapa não está
 * pronto a vista de longe usa o terreno analítico de reserva.
 *
 * Se o gerador do nível for o analítico (PlanetChunkGenerator), o terreno analítico JÁ É o mundo e o mapa é dispensado.
 * Registrada sozinha pelo {@link EventBusSubscriber}; não precisa de chamada no construtor do mod.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public final class PlanetMapService {
    private static final String CACHE_DIR = "spacehorizons";

    /** Planetas cujo bake já foi iniciado neste servidor. */
    private static final Map<PlanetDefinition, Boolean> STARTED = new ConcurrentHashMap<>();
    /** Bytes serializados do mapa de cada planeta pronto, para enviar a quem entra. */
    private static final Map<PlanetDefinition, byte[]> SERIALIZED = new ConcurrentHashMap<>();
    /** Versão do conteúdo gerado: incremente a cada mudança de worldgen (ruído, latitude, costura) que invalide mapas antigos. */
    private static final int GENERATOR_VERSION = 1;
    /** Cancelamento do bake em andamento; cada start() cria o seu, para um bake antigo não ser religado por um mundo novo. */
    private static volatile AtomicBoolean cancel = new AtomicBoolean();
    private static volatile @Nullable MinecraftServer server;

    private PlanetMapService() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PlanetDefinition planet = Planets.forLevel(level).orElse(null);
        if (planet == null || planet.style() != TerrainStyle.EARTH || STARTED.putIfAbsent(planet, Boolean.TRUE) != null) {
            return;
        }
        start(level, planet);
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (Map.Entry<PlanetDefinition, byte[]> entry : SERIALIZED.entrySet()) {
                PlanetMapNetwork.send(player, entry.getKey().id().toString(), entry.getValue());
            }
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        cancel.set(true);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        cancel.set(true);
        STARTED.clear();
        SERIALIZED.clear();
        server = null;
        PlanetTerrains.clearMaps();
    }

    // ---- bake / cache ----

    private static void start(ServerLevel level, PlanetDefinition planet) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator instanceof PlanetChunkGenerator) {
            SpaceHorizons.LOGGER.info("Mapa de {}: gerador analítico, o terreno analítico já é o mundo (sem mapa)", planet.id());
            return;
        }
        MinecraftServer current = level.getServer();
        server = current;
        AtomicBoolean myCancel = new AtomicBoolean();
        cancel = myCancel;
        long seed = level.getSeed();
        String fingerprint = generator.getClass().getName() + "#v" + GENERATOR_VERSION + "#"
                + Integer.toHexString(java.util.Objects.hash(planet.maxLatitudeBlocks(), planet.seaLevel(),
                        planet.circumferenceBlocks(), SeamMath.DEFAULT_HALF_BAND));
        Path file = current.getWorldPath(LevelResource.DATA).resolve(CACHE_DIR).resolve("planetmap_" + planet.id().getPath() + ".bin");
        PlanetMapBaker baker = new PlanetMapBaker(planet, generator, level.getChunkSource().randomState(),
                level.getMinY(), level.getHeight());

        Thread thread = new Thread(() -> run(current, planet, baker, seed, fingerprint, file, myCancel), "SpaceHorizons-PlanetMap");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY + 1);
        thread.start();
    }

    private static void run(MinecraftServer owner, PlanetDefinition planet, PlanetMapBaker baker, long seed,
            String fingerprint, Path file, AtomicBoolean myCancel) {
        try {
            PlanetMap map = loadCache(planet, seed, fingerprint, file);
            byte[] bytes = null;
            if (map == null) {
                int width = PlanetMap.DEFAULT_WIDTH;
                SpaceHorizons.LOGGER.info("Mapa de {}: calculando {}x{} a partir do gerador do mundo (em background)",
                        planet.id(), width, PlanetMap.rowsFor(planet, width));
                long start = System.nanoTime();
                int[] lastPercent = {-1};
                map = baker.bake(seed, width, fingerprint, fraction -> {
                    int percent = (int) (fraction * 100.0);
                    synchronized (lastPercent) {
                        if (percent / 10 != lastPercent[0] / 10) {
                            lastPercent[0] = percent;
                            SpaceHorizons.LOGGER.info("Mapa de {}: {}%", planet.id(), percent);
                        }
                    }
                }, myCancel);
                if (map == null) {
                    SpaceHorizons.LOGGER.info("Mapa de {}: cálculo cancelado", planet.id());
                    return;
                }
                bytes = map.serialize();
                SpaceHorizons.LOGGER.info("Mapa de {}: pronto em {} s ({} KiB comprimido)", planet.id(),
                        String.format("%.1f", (System.nanoTime() - start) / 1.0e9), bytes.length / 1024);
                saveCache(file, bytes);
            }
            if (bytes == null) {
                bytes = map.serialize();
            }
            if (myCancel.get() || server != owner) {
                return;
            }
            publish(owner, planet, map, bytes);
        } catch (Throwable t) {
            SpaceHorizons.LOGGER.error("Falha ao preparar o mapa de {}; a vista de longe usa o terreno de reserva", planet.id(), t);
        }
    }

    private static void publish(MinecraftServer owner, PlanetDefinition planet, PlanetMap map, byte[] bytes) {
        SERIALIZED.put(planet, bytes);
        PlanetTerrains.setMap(map);
        // o envio roda no servidor (a lista de jogadores é da thread principal)
        owner.execute(() -> {
            for (ServerPlayer player : owner.getPlayerList().getPlayers()) {
                PlanetMapNetwork.send(player, planet.id().toString(), bytes);
            }
        });
    }

    private static @Nullable PlanetMap loadCache(PlanetDefinition planet, long seed, String fingerprint, Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            PlanetMap.Header header = PlanetMap.readHeader(bytes);
            if (header == null || !header.matches(planet, seed, PlanetMap.DEFAULT_WIDTH, fingerprint)) {
                SpaceHorizons.LOGGER.info("Mapa de {}: cache em disco não vale mais (semente, tamanho ou gerador mudou), recalculando", planet.id());
                return null;
            }
            PlanetMap map = PlanetMap.deserialize(planet, bytes);
            SpaceHorizons.LOGGER.info("Mapa de {}: carregado do cache ({} KiB)", planet.id(), bytes.length / 1024);
            return map;
        } catch (IOException | RuntimeException e) {
            SpaceHorizons.LOGGER.warn("Mapa de {}: cache ilegível, recalculando", planet.id(), e);
            return null;
        }
    }

    private static void saveCache(Path file, byte[] bytes) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SpaceHorizons.LOGGER.warn("Mapa: não foi possível gravar o cache em {}", file, e);
        }
    }

    // ---- pouso ----

    /**
     * Y onde colocar alguém que desce na coluna (x, z) de um nível do mod: o primeiro bloco livre acima do chão (e
     * da água) mais um de folga. Prefere o heightmap REAL do chunk quando ele já está carregado; senão pergunta ao
     * gerador do nível, que é exato (um só getBaseHeight), sem depender do mapa aproximado.
     */
    public static int landingY(ServerLevel level, int x, int z) {
        int firstFree;
        if (level.hasChunk(x >> 4, z >> 4)) {
            firstFree = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        } else {
            firstFree = level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
                    level.getChunkSource().randomState());
        }
        return firstFree + 1;
    }
}
