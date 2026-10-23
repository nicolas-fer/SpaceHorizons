package com.spacehorizons.client.ship;

import java.util.List;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.ship.AnchorStatus;
import com.spacehorizons.ship.AnchorStatusPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import org.jspecify.annotations.Nullable;

/**
 * O estado da âncora no cliente (fase 2f): guarda o último {@link AnchorStatusPayload} (o painel {@link AnchorHud} lê daqui), mostra a recusa
 * ao piloto (mensagem na barra) e destaca com partículas vermelhas os blocos que impedem a âncora por alguns segundos.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class AnchorClient {
    /** Quanto tempo (ms) um estado recebido vale sem pacote novo (o servidor manda a cada 1 s). */
    private static final long STATUS_TTL_MS = 3000L;
    /** Quanto tempo (ms) o aviso "ancorada" fica no painel. */
    private static final long ANCHORED_TTL_MS = 6000L;
    /** Quanto tempo (ticks) os blocos que impedem ficam destacados depois de uma recusa. */
    public static final int HIGHLIGHT_TICKS = 160;

    private static volatile @Nullable AnchorStatus status;
    private static volatile long receivedAtMillis;
    private static volatile List<BlockPos> blockers = List.of();
    private static volatile long highlightUntilTick;
    /** Só para o autoteste: quantas recusas chegaram e a última mensagem mostrada. */
    private static volatile int refusals;
    private static volatile @Nullable String lastMessage;
    private static volatile @Nullable String lastKey;

    private AnchorClient() {
    }

    @SubscribeEvent
    static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(AnchorStatusPayload.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            AnchorStatus s = payload.anchorStatus();
            status = s;
            receivedAtMillis = System.currentTimeMillis();
            blockers = payload.blockers();
            if (payload.refused() && s.refuses() && mc.player != null) {
                refusals++;
                String key = "message.spacehorizons.anchor." + s.key();
                lastKey = key;
                lastMessage = Component.translatable(key).getString();
                mc.player.sendOverlayMessage(Component.translatable(key));
                if (!payload.blockers().isEmpty() && mc.level != null) {
                    highlightUntilTick = mc.level.getGameTime() + HIGHLIGHT_TICKS;
                }
            }
        });
    }

    /** O estado a mostrar no painel (null se nunca chegou ou está velho). */
    public static @Nullable AnchorStatus current() {
        AnchorStatus s = status;
        if (s == null) {
            return null;
        }
        long age = System.currentTimeMillis() - receivedAtMillis;
        return age < (s == AnchorStatus.ANCHORED ? ANCHORED_TTL_MS : STATUS_TTL_MS) ? s : null;
    }

    public static List<BlockPos> blockers() {
        return blockers;
    }

    public static int refusals() {
        return refusals;
    }

    public static @Nullable String lastMessage() {
        return lastMessage;
    }

    /** A chave de tradução da última mensagem de recusa (o autoteste confere por ela, que não depende do idioma). */
    public static @Nullable String lastKey() {
        return lastKey;
    }

    /** Os blocos que impedem estão destacados agora? (autoteste) */
    public static boolean highlighting() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getGameTime() < highlightUntilTick && !blockers.isEmpty();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !highlighting() || mc.level.getGameTime() % 2 != 0) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(0xFF3A30, 1.6F);
        for (BlockPos pos : blockers) {
            for (int i = 0; i < 3; i++) {
                mc.level.addParticle(dust, pos.getX() + mc.level.getRandom().nextDouble(), pos.getY() + mc.level.getRandom().nextDouble(),
                        pos.getZ() + mc.level.getRandom().nextDouble(), 0.0, 0.0, 0.0);
            }
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        status = null;
        blockers = List.of();
        highlightUntilTick = 0L;
    }
}
