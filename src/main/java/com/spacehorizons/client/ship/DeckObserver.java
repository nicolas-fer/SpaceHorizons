package com.spacehorizons.client.ship;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.ship.deck.DeckFrame;
import com.spacehorizons.ship.deck.DeckObservePayload;
import com.spacehorizons.ship.deck.DeckObservedHistory;
import com.spacehorizons.ship.deck.DeckState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Os tripulantes a pé no convés de uma nave, vistos por OUTROS clientes (fase 2e). O servidor deixa de mandar a posição do mundo do tripulante
 * (dezenas de blocos por tick de diferença para o casco, que o cliente interpola por conta própria) e manda o estado LOCAL com o tick do servidor
 * ({@link DeckObservePayload}). Aqui o jogador remoto é posto pela pose da nave na MESMA linha do tempo do casco ({@code RocketEntity.smoothFrame}):
 * <ul>
 *   <li>no fim de cada tick, a posição antiga e a nova do jogador no mundo são a pose da nave em g e em g + 1 aplicada ao estado local lido nos
 *       mesmos tempos do servidor (como {@link DeckClient} faz com o jogador local), com o olhar local como giro do modelo;</li>
 *   <li>no desenho, o modelo vai à posição exata do quadro (pose e estado lidos no tempo do quadro) e é girado pela atitude da nave em torno dos
 *       pés, sem ser descartado pelo campo de visão (a caixa da entidade está até um tick de nave longe de onde ele é desenhado);</li>
 *   <li>a caminhada anima pelo passo local, não pelo deslocamento da nave.</li>
 * </ul>
 * Sem estado novo há mais de {@link #STALE_TICKS} ticks, ou quando o servidor avisa que o tripulante saiu, o jogador volta ao vanilla.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class DeckObserver {
    /** Sem estado por tantos ticks do cliente, o tripulante deixa de ser tratado como a bordo. */
    private static final double STALE_TICKS = 10.0;

    private static final Map<Integer, DeckObservedHistory> HISTORIES = new HashMap<>();
    private static final Map<Integer, Double> STEPS = new HashMap<>();
    private static int drawnFrames;

    /**
     * Só para o autoteste: um olho fixo no referencial de uma nave no lugar do olho do jogador local (o observador "parado no ar" ao lado da nave);
     * devolve a posição do olho no mundo para a parcial do quadro, ou null para a câmera de sempre.
     */
    public static volatile @Nullable Function<Float, Vec3> eyeOverride;

    private DeckObserver() {
    }

    // ---------------------------------------------------------------- consulta

    /** O jogador remoto é um tripulante a bordo de uma nave (com estado recente e a nave no cliente)? */
    public static boolean isObserved(Entity entity) {
        return rocketOf(entity) != null;
    }

    /** Quantos quadros de desenho de jogador observado já aconteceram (o evento só dispara se o modelo passou do descarte). */
    public static int drawnFrames() {
        return drawnFrames;
    }

    /** O estado local mais recente na linha do tempo do quadro, ou null. */
    public static @Nullable DeckState stateAt(Entity entity, float partialTick) {
        RocketEntity rocket = rocketOf(entity);
        if (rocket == null) {
            return null;
        }
        double serverTime = rocket.smoothServerTimeAt(rocket.clientTickAt(partialTick));
        return Double.isNaN(serverTime) ? null : HISTORIES.get(entity.getId()).sample(serverTime);
    }

    /** O passo horizontal local do tripulante no último tick (para a animação de caminhada). */
    public static double localStep(Entity entity) {
        return STEPS.getOrDefault(entity.getId(), 0.0);
    }

    /** Os pés do tripulante no mundo neste quadro (pose e estado lidos no tempo do quadro), ou null. */
    public static @Nullable Vec3 feetWorld(Entity entity, float partialTick) {
        RocketEntity rocket = rocketOf(entity);
        if (rocket == null) {
            return null;
        }
        double clientTick = rocket.clientTickAt(partialTick);
        double serverTime = rocket.smoothServerTimeAt(clientTick);
        DeckFrame frame = rocket.smoothFrame(clientTick);
        DeckState s = Double.isNaN(serverTime) || frame == null ? null : HISTORIES.get(entity.getId()).sample(serverTime);
        if (s == null) {
            return null;
        }
        Vector3d w = DeckObservedHistory.worldFeet(frame, s);
        return new Vec3(w.x, w.y, w.z);
    }

    /** O quanto falta (somado ao desenho do vanilla, que interpola a posição do tick em linha reta) para o modelo ficar na posição exata do quadro. */
    public static Vec3 modelCorrection(Entity entity, float partialTick) {
        Vec3 exact = feetWorld(entity, partialTick);
        return exact == null ? Vec3.ZERO : exact.subtract(entity.getPosition(partialTick));
    }

    /** A nave em que o tripulante está, no cliente, se ele está a bordo e a linha do tempo dela está ativa; senão null. */
    private static @Nullable RocketEntity rocketOf(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(entity instanceof RemotePlayer)) {
            return null;
        }
        DeckObservedHistory h = HISTORIES.get(entity.getId());
        if (h == null || !(mc.level.getEntity(h.rocketId()) instanceof RocketEntity rocket) || rocket.isRemoved()) {
            return null;
        }
        double now = rocket.clientTickAt(0.0F);
        return Double.isNaN(rocket.smoothServerTimeAt(now)) || !h.fresh(now, STALE_TICKS) ? null : rocket;
    }

    /** O olho do observador do autoteste neste quadro, ou null. */
    public static @Nullable Vec3 observerEye(float partialTick) {
        Function<Float, Vec3> f = eyeOverride;
        return f == null ? null : f.apply(partialTick);
    }

    // ---------------------------------------------------------------- pacotes

    @SubscribeEvent
    static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(DeckObservePayload.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && payload.playerId() == mc.player.getId()) {
                return;
            }
            if (!payload.aboard()) {
                HISTORIES.remove(payload.playerId());
                STEPS.remove(payload.playerId());
                return;
            }
            var clock = RocketEntity.clientClock;
            double arrival = clock == null ? 0.0 : clock.getAsDouble();
            HISTORIES.computeIfAbsent(payload.playerId(), id -> new DeckObservedHistory()).record(payload.rocketId(), payload.stamp(), payload.toState(), arrival);
        });
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HISTORIES.clear();
        STEPS.clear();
    }

    // ---------------------------------------------------------------- tick

    /**
     * No fim do tick (depois de todas as entidades): põe cada jogador observado pela pose da nave em g e em g + 1 (a antiga e a nova posição do
     * mundo, entre as quais o desenho interpola) e pelo estado local lido nos mesmos tempos.
     */
    @SubscribeEvent
    static void onTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || HISTORIES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, DeckObservedHistory>> it = HISTORIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, DeckObservedHistory> e = it.next();
            if (!(mc.level.getEntity(e.getKey()) instanceof RemotePlayer player)) {
                continue;
            }
            RocketEntity rocket = rocketOf(player);
            if (rocket == null) {
                continue;
            }
            DeckObservedHistory h = e.getValue();
            double g = rocket.clientTickAt(0.0F);
            DeckFrame before = rocket.smoothFrame(g);
            DeckFrame now = rocket.smoothFrame(g + 1.0);
            DeckState a = h.sample(rocket.smoothServerTimeAt(g));
            DeckState b = h.sample(rocket.smoothServerTimeAt(g + 1.0));
            if (before == null || now == null || a == null || b == null) {
                continue;
            }
            Vector3d wa = DeckObservedHistory.worldFeet(before, a);
            Vector3d wb = DeckObservedHistory.worldFeet(now, b);
            player.getInterpolation().cancel();
            player.setOldPosAndRot(new Vec3(wa.x, wa.y, wa.z), a.yaw, a.pitch);
            player.setPos(wb.x, wb.y, wb.z);
            player.setYRot(b.yaw);
            player.setXRot(b.pitch);
            player.setYHeadRot(b.yaw);
            player.yHeadRotO = a.yaw;
            player.setOnGround(b.onGround);
            STEPS.put(e.getKey(), Math.hypot(b.pos.x - a.pos.x, b.pos.z - a.pos.z));
        }
    }

    // ---------------------------------------------------------------- desenho

    /** O modelo do jogador observado: na posição exata do quadro e girado pela atitude da nave em torno dos pés. */
    @SubscribeEvent
    static void onRenderPlayer(RenderPlayerEvent.Pre<?> event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || HISTORIES.isEmpty()) {
            return;
        }
        Entity entity = mc.level.getEntity(event.getRenderState().id);
        RocketEntity rocket = entity == null ? null : rocketOf(entity);
        if (rocket == null) {
            return;
        }
        Quaternionf attitude = new Quaternionf();
        rocket.orientation(event.getPartialTick(), attitude);
        Vec3 fix = modelCorrection(entity, event.getPartialTick());
        event.getPoseStack().translate(fix.x, fix.y, fix.z);
        event.getPoseStack().rotate(attitude);
        drawnFrames++;
    }
}
