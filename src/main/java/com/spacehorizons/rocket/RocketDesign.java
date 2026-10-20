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
        RocketAssembly.Grid grid = RocketAssembler.worldGrid(level);
        List<int[]> seeds = new ArrayList<>();
        LongOpenHashSet pads = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        pads.add(pad.asLong());
        queue.add(pad.immutable());
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (Direction side : Direction.values()) {
                next.setWithOffset(at, side);
                RocketBlockKind kind = grid.kindAt(next.getX(), next.getY(), next.getZ());
                if (kind == RocketBlockKind.PAD) {
                    if (pads.size() < MAX_PADS && pads.add(next.asLong())) {
                        queue.add(next.immutable());
                    }
                } else if (kind.isPart()) {
                    seeds.add(new int[] {next.getX(), next.getY(), next.getZ()});
                }
            }
        }
        if (seeds.isEmpty()) {
            return DesignReport.nothing(pad.getX(), pad.getY(), pad.getZ());
        }
        RocketAssembly.Report report = RocketAssembly.analyze(grid, seeds);
        if (!report.cockpits().isEmpty()) {
            // o que a montagem de verdade faria ao clicar no cockpit: a busca a partir dele
            RocketAssembly.Cell cockpit = report.cockpits().get(0);
            report = RocketAssembly.analyze(grid, List.of(new int[] {cockpit.x(), cockpit.y(), cockpit.z()}));
        }
        return DesignReport.of(report, pad.getX(), pad.getY(), pad.getZ());
    }
}
