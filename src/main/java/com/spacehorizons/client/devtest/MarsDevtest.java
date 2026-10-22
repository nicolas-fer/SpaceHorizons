package com.spacehorizons.client.devtest;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfacePalette;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.terrain.OreVeins;
import com.spacehorizons.worldgen.PlanetChunkGenerator;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Só em dev: as buscas e conferências do autoteste {@code -PshOnly=mars} (o roteiro fica no fim do AutoTest). Os pontos de
 * interesse (uma cratera, um vulcão, um veio de minério) são achados no cliente pela MESMA função de terreno e de veios que
 * o servidor usa para gerar os chunks; depois o autoteste confere que o mundo gerado bate com ela.
 */
final class MarsDevtest {
    private static final String TAG = "[AUTOTEST] MARS ";
    static final PlanetDefinition MARS = Planets.MARS;
    private static int passed;
    private static int failed;

    private MarsDevtest() {
    }

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

    static <T> T onServer(Minecraft mc, Function<MinecraftServer, T> body) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("sem servidor integrado");
        }
        try {
            return server.submit(() -> body.apply(server)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao consultar o servidor", e);
        }
    }

    static PlanetTerrain terrain() {
        return PlanetTerrains.analytic(MARS);
    }

    static boolean onMars(Minecraft mc) {
        return mc.level != null && Planets.forLevel(mc.level).filter(MARS::equals).isPresent();
    }

    /** Tempo de tick do servidor e da dimensão de Marte, chunks carregados e o custo de geração de terreno de Marte. */
    static String stats(Minecraft mc) {
        double[] gen = PlanetChunkGenerator.terrainStats(MARS);
        String server = onServer(mc, srv -> {
            long[] all = srv.getTickTimesNanos().clone();
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "tick(servidor, 100 ticks)=%.2f ms (máx %.2f)",
                    avg(all), max(all)));
            for (ServerLevel level : srv.getAllLevels()) {
                if (Planets.forLevel(level).filter(MARS::equals).isPresent()) {
                    long[] t = srv.getTickTime(level.dimension());
                    if (t != null) {
                        sb.append(String.format(Locale.ROOT, " | tick(marte)=%.2f ms (máx %.2f)", avg(t), max(t)));
                    }
                    sb.append(" | chunks(marte)=").append(level.getChunkSource().getLoadedChunksCount());
                }
            }
            return sb.toString();
        });
        return server + String.format(Locale.ROOT, " | geração de terreno de Marte: %d chunks, %.3f ms/chunk", (long) gen[0], gen[1]);
    }

    static void logStats(Minecraft mc, String label) {
        SpaceHorizons.LOGGER.info(TAG + "STATS {}: {}", label, stats(mc));
    }

    /** Média e máximo (ms) de um vetor de tempos de tick em ns, ignorando zeros (ticks ainda não medidos). */
    static double avg(long[] nanos) {
        long sum = 0;
        int n = 0;
        for (long v : nanos) {
            if (v > 0) {
                sum += v;
                n++;
            }
        }
        return n == 0 ? 0 : sum / 1.0e6 / n;
    }

    static double max(long[] nanos) {
        long m = 0;
        for (long v : nanos) {
            m = Math.max(m, v);
        }
        return m / 1.0e6;
    }

    /** Conferências na chegada: dimensão, gravidade, bloco do chão da paleta e altura igual à do terreno analítico. */
    static void checkArrival(Minecraft mc) {
        check("dimensao", onMars(mc), "dim=" + mc.level.dimension().identifier());
        double gravity = mc.player.getAttributeValue(Attributes.GRAVITY);
        check("gravidade", Math.abs(gravity - 0.08 * MARS.surfaceGravity()) < 1e-6,
                String.format(Locale.ROOT, "atributo=%.5f esperado=%.5f", gravity, 0.08 * MARS.surfaceGravity()));
        int x = mc.player.getBlockX();
        int z = mc.player.getBlockZ();
        int expected = terrain().surfaceHeight(x, z);
        BlockState top = mc.level.getBlockState(new BlockPos(x, expected, z));
        BlockState above = mc.level.getBlockState(new BlockPos(x, expected + 1, z));
        SurfaceType type = terrain().surfaceType(x, z);
        BlockState paletteTop = SurfacePalette.of(MARS).top(type);
        check("chao_da_paleta", top.getBlock() == paletteTop.getBlock() && above.isAir(),
                "coluna (" + x + ", " + z + ") tipo=" + type + " y=" + expected + " bloco=" + id(top) + " esperado=" + id(paletteTop)
                        + " acima=" + id(above));
        check("pe_no_chao", Math.abs(mc.player.getY() - (expected + 1)) < 3.0,
                String.format(Locale.ROOT, "y do jogador=%.2f chão=%d", mc.player.getY(), expected));
    }

    /** Uma coluna larga de fundo de cratera perto do ponto de chegada: {xFundo, zFundo, xBorda, zBorda, yBorda}. */
    static int[] findCrater() {
        PlanetTerrain t = terrain();
        for (int r = 512; r <= 24_000; r += 512) {
            for (int a = 0; a < 64; a++) {
                double ang = a * Math.PI / 32;
                int x = (int) Math.round(r * Math.cos(ang));
                int z = (int) Math.round(r * Math.sin(ang));
                if (t.surfaceType(x, z) != SurfaceType.CRATER_FLOOR) {
                    continue;
                }
                // a cratera precisa ser larga (o fundo continua por 64 blocos para os dois lados)
                if (t.surfaceType(x - 64, z) != SurfaceType.CRATER_FLOOR || t.surfaceType(x + 64, z) != SurfaceType.CRATER_FLOOR) {
                    continue;
                }
                // anda para leste até a borda (o ponto mais alto logo depois do fim do fundo)
                int edge = x;
                while (edge < x + 4_000 && t.surfaceType(edge, z) == SurfaceType.CRATER_FLOOR) {
                    edge += 4;
                }
                int best = edge;
                int bestH = t.surfaceHeight(edge, z);
                for (int s = edge; s < edge + 1_500; s += 4) {
                    int h = t.surfaceHeight(s, z);
                    if (h > bestH) {
                        bestH = h;
                        best = s;
                    }
                }
                int floorH = t.surfaceHeight(x, z);
                SpaceHorizons.LOGGER.info(TAG + "cratera: fundo ({}, {}) y={}, borda ({}, {}) y={}", x, z, floorH, best, z, bestH);
                return new int[] {x, z, best, z, bestH};
            }
        }
        return null;
    }

    /** O topo do vulcão mais perto da chegada: {xTopo, zTopo, yTopo}. */
    static int[] findVolcano() {
        PlanetTerrain t = terrain();
        for (int r = 1_024; r <= 120_000; r += 1_024) {
            int steps = Math.max(16, r / 1_024 * 6);
            for (int a = 0; a < steps; a++) {
                double ang = a * 2 * Math.PI / steps;
                int x = (int) Math.round(r * Math.cos(ang));
                int z = (int) Math.round(r * Math.sin(ang));
                if (Math.abs(z) > MARS.maxLatitudeBlocks() * 0.8 || t.surfaceType(x, z) != SurfaceType.VOLCANIC) {
                    continue;
                }
                // sobe a encosta até o topo (passos cada vez menores)
                int cx = x;
                int cz = z;
                for (int step = 512; step >= 8; step /= 2) {
                    boolean moved = true;
                    while (moved) {
                        moved = false;
                        int h = t.surfaceHeight(cx, cz);
                        int[][] dirs = {{step, 0}, {-step, 0}, {0, step}, {0, -step}};
                        for (int[] d : dirs) {
                            if (t.surfaceHeight(cx + d[0], cz + d[1]) > h) {
                                cx += d[0];
                                cz += d[1];
                                moved = true;
                                break;
                            }
                        }
                    }
                }
                int h = t.surfaceHeight(cx, cz);
                SpaceHorizons.LOGGER.info(TAG + "vulcão: encosta em ({}, {}), topo da borda da caldeira ({}, {}) y={}", x, z, cx, cz, h);
                return new int[] {cx, cz, h};
            }
        }
        return null;
    }

    /** Um trecho de veio de minério perto de (x0, z0), bem abaixo do chão: {x, y, z}. */
    static int[] findVein(int x0, int z0) {
        OreVeins veins = new OreVeins(MARS, Planets.spec(MARS).ores());
        PlanetTerrain t = terrain();
        for (int x = x0; x < x0 + 160; x += 2) {
            for (int z = z0; z < z0 + 160; z += 2) {
                int top = t.surfaceHeight(x, z) - 8;
                for (int y = Math.min(top, 70); y > 10; y -= 2) {
                    if (veins.veinAt(x, y, z) < 0) {
                        continue;
                    }
                    int n = 0;
                    for (int dx = -3; dx <= 3; dx++) {
                        for (int dy = -3; dy <= 3; dy++) {
                            for (int dz = -3; dz <= 3; dz++) {
                                if (veins.veinAt(x + dx, y + dy, z + dz) >= 0) {
                                    n++;
                                }
                            }
                        }
                    }
                    if (n >= 14) {
                        SpaceHorizons.LOGGER.info(TAG + "veio: ({}, {}, {}) com {} blocos de minério no cubo 7x7x7", x, y, z, n);
                        return new int[] {x, y, z};
                    }
                }
            }
        }
        return null;
    }

    /** Id do bloco de subsolo resolvido (o que o autoteste cava em volta do veio). */
    static String subsurfaceId() {
        return id(SurfacePalette.of(MARS).subsurface());
    }

    /**
     * Confere no mundo do cliente que o veio gerado é o da função: no cubo 9x7x9 em volta do ponto, todo bloco que a função
     * diz ser minério é o bloco do veio, e nenhum outro é.
     */
    static void checkVein(Minecraft mc, int[] p) {
        OreVeins veins = new OreVeins(MARS, Planets.spec(MARS).ores());
        BlockState ore = SurfacePalette.resolve(MARS.id(), Planets.spec(MARS).ores().get(0).block());
        int expected = 0;
        int matched = 0;
        int stray = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = new BlockPos(p[0] + dx, p[1] + dy, p[2] + dz);
                    boolean should = veins.veinAt(pos.getX(), pos.getY(), pos.getZ()) >= 0;
                    boolean is = mc.level.getBlockState(pos).getBlock() == ore.getBlock();
                    if (should) {
                        expected++;
                        if (is) {
                            matched++;
                        }
                    } else if (is) {
                        stray++;
                    }
                }
            }
        }
        check("veio_gerado_igual_a_funcao", expected > 0 && matched == expected && stray == 0,
                "bloco=" + id(ore) + " esperados=" + expected + " encontrados=" + matched + " fora da função=" + stray);
    }

    static String id(BlockState state) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key.toString();
    }
}
