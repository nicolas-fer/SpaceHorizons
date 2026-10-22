package com.spacehorizons.rocket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlocks;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

/**
 * O painel de projeto da plataforma de lançamento, no servidor: acha o foguete montado sobre a plataforma e devolve o {@link DesignReport}
 * (massa, empuxo, TWR, Δv, destino, problemas) a quem pediu ({@link RocketDesignRequestPayload}).
 *
 * <p>Acha o foguete como a montagem acha: as peças encostadas na plataforma (e nas outras plataformas ligadas a ela) são as sementes; se uma
 * delas leva a um cockpit, a busca roda DE NOVO a partir desse cockpit, exatamente o que {@link RocketAssembler#assemble} faria ao clicar nele
 * ({@link RocketAssembly#analyze}); sem cockpit, o relatório é da peça achada (e diz "sem cockpit"). Por construção, o painel e a montagem
 * não podem discordar.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public final class RocketDesign {
    /** Teto de plataformas ligadas examinadas. */
    private static final int MAX_PADS = 512;
    /** Alcance do painel: o servidor ignora pedidos de quem está mais longe que isto da plataforma. */
    public static final double MAX_DISTANCE = 96.0;
    /** Intervalo mínimo, em ticks de jogo, entre dois pedidos do mesmo jogador (o cliente pede a cada 10). */
    private static final long MIN_INTERVAL_TICKS = 4;

    private static final Map<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();

    private RocketDesign() {
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(RocketDesignRequestPayload.TYPE, RocketDesignRequestPayload.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockPos pad = payload.pad();
            long now = player.level().getGameTime();
            Long last = LAST_REQUEST.get(player.getUUID());
            if (last != null && now - last < MIN_INTERVAL_TICKS) {
                return;
            }
            LAST_REQUEST.put(player.getUUID(), now);
            if (!(player.level() instanceof ServerLevel level) || player.distanceToSqr(pad.getX() + 0.5, pad.getY() + 0.5, pad.getZ() + 0.5) > MAX_DISTANCE * MAX_DISTANCE
                    || !level.isLoaded(pad) || !level.getBlockState(pad).is(ModBlocks.LAUNCH_PAD.get())) {
                return;
            }
            PacketDistributor.sendToPlayer(player, new RocketDesignPayload(analyze(level, pad)));
        });
        event.registrar("1").playToClient(RocketDesignPayload.TYPE, RocketDesignPayload.CODEC);
    }

    /** O relatório do foguete sobre a plataforma em {@code pad} (a plataforma que o jogador olhou ou clicou). */
    public static DesignReport analyze(Level level, BlockPos pad) {
        return analyze(RocketAssembler.worldGrid(level), pad.getX(), pad.getY(), pad.getZ());
    }

    /**
     * O relatório do foguete sobre a plataforma em (padX, padY, padZ) de um mundo qualquer visto pela busca: o mundo de verdade
     * ({@link #analyze(Level, BlockPos)}) ou uma planta de montagem ({@code Blueprint.design}), pelo MESMO caminho.
     */
    public static DesignReport analyze(RocketAssembly.Grid grid, int padX, int padY, int padZ) {
        Search search = search(grid, padX, padY, padZ);
        return search.report() == null ? DesignReport.nothing(padX, padY, padZ) : DesignReport.of(search.report(), padX, padY, padZ);
    }

    /**
     * O que a busca do painel achou: o relatório da montagem (null se nada encosta nas plataformas) e as plataformas ligadas à de partida
     * (cada uma como {x, y, z}; a de partida primeiro). A planta salva de um foguete ({@code BlueprintCapture}) usa esta mesma busca.
     */
    public record Search(RocketAssembly.@Nullable Report report, List<int[]> pads) {
    }

    /** A busca do painel a partir da plataforma em (padX, padY, padZ). */
    public static Search search(RocketAssembly.Grid grid, int padX, int padY, int padZ) {
        List<int[]> seeds = new ArrayList<>();
        List<int[]> padList = new ArrayList<>();
        LongOpenHashSet pads = new LongOpenHashSet();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        pads.add(RocketAssembly.pack(padX, padY, padZ));
        int[] start = {padX, padY, padZ};
        queue.add(start);
        padList.add(start);
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (Direction side : Direction.values()) {
                int nx = at[0] + side.getStepX();
                int ny = at[1] + side.getStepY();
                int nz = at[2] + side.getStepZ();
                RocketBlockKind kind = grid.kindAt(nx, ny, nz);
                if (kind == RocketBlockKind.PAD) {
                    if (pads.size() < MAX_PADS && pads.add(RocketAssembly.pack(nx, ny, nz))) {
                        int[] next = {nx, ny, nz};
                        queue.add(next);
                        padList.add(next);
                    }
                } else if (kind.isPart()) {
                    seeds.add(new int[] {nx, ny, nz});
                }
            }
        }
        if (seeds.isEmpty()) {
            return new Search(null, List.copyOf(padList));
        }
        RocketAssembly.Report report = RocketAssembly.analyze(grid, seeds);
        if (!report.cockpits().isEmpty()) {
            // o que a montagem de verdade faria ao clicar no cockpit: a busca a partir dele
            RocketAssembly.Cell cockpit = report.cockpits().get(0);
            report = RocketAssembly.analyze(grid, List.of(new int[] {cockpit.x(), cockpit.y(), cockpit.z()}));
        }
        return new Search(report, List.copyOf(padList));
    }
}
