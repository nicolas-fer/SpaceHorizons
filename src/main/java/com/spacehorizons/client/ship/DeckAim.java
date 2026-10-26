package com.spacehorizons.client.ship;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.ship.deck.DeckFrame;
import com.spacehorizons.ship.deck.DeckState;
import com.spacehorizons.ship.deck.DeckUsePayload;
import com.spacehorizons.ship.deck.PortraitDeltaPayload;
import com.spacehorizons.ship.deck.PortraitRay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * A mira nos blocos do retrato de uma nave em movimento (fase 2e, frente 3). O vanilla só mira blocos do mundo; para os blocos do retrato o raio
 * do olhar é refeito em coordenadas LOCAIS da nave (pela pose dela) contra as formas de seleção do retrato ({@link PortraitRay}), para quem está
 * no convés (o olhar local do jogador) e para quem olha uma nave-entidade de fora (o olhar do mundo levado ao referencial dela). O que estiver mais
 * perto, mundo ou retrato, ganha; o retrato mirado ganha o contorno de seleção (desenhado pelo {@code RocketRenderer}) e o clique direito dele vai ao
 * servidor em {@link DeckUsePayload}, no lugar do clique do vanilla.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class DeckAim {
    /** O bloco do retrato mirado: a nave, a posição local, a face, a distância (blocos) e o ponto atingido (local). */
    public record Target(RocketEntity rocket, BlockPos pos, int face, double t, Vec3 local) {
    }

    private static @Nullable Target target;
    private static int uses;
    private static int deltas;

    private DeckAim() {
    }

    /** O bloco do retrato mirado agora (pode ser de qualquer nave à vista), ou null. */
    public static @Nullable Target target() {
        return target;
    }

    /** O bloco mirado, se for da nave {@code rocket} (para o contorno no desenho dela). */
    public static @Nullable Target targetOf(RocketEntity rocket) {
        Target t = target;
        return t != null && t.rocket() == rocket ? t : null;
    }

    /** Cliques enviados ao servidor e deltas do retrato recebidos (diagnóstico do autoteste). */
    public static int useCount() {
        return uses;
    }

    public static int deltaCount() {
        return deltas;
    }

    // ---------------------------------------------------------------- eventos

    @SubscribeEvent
    static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(PortraitDeltaPayload.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getEntity(payload.rocketId()) instanceof RocketEntity rocket) {
                rocket.applyDelta(payload);
                deltas++;
            }
        });
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        target = compute(Minecraft.getInstance());
    }

    /** O clique direito num bloco do retrato mirado vai ao servidor; o do vanilla (que não o enxerga) é cancelado. */
    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSecondaryUseActive()) {
            return;
        }
        if (useNow(mc)) {
            event.setCanceled(true);
            event.setSwingHand(true);
        }
    }

    /** O mesmo caminho do clique do jogador: mira de novo e, se acerta o retrato, manda o clique ao servidor. Devolve se mandou. */
    public static boolean useNow(Minecraft mc) {
        Target t = compute(mc);
        target = t;
        if (t == null) {
            return false;
        }
        ClientPacketDistributor.sendToServer(new DeckUsePayload(t.rocket().getId(), t.pos().asLong(), t.face(), (float) t.local().x, (float) t.local().y, (float) t.local().z));
        uses++;
        return true;
    }

    // ---------------------------------------------------------------- a mira

    /** Lança a mira do jogador local contra os retratos das naves ao alcance; o retrato só vale se estiver mais perto que o bloco do mundo mirado. */
    public static @Nullable Target compute(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || p.isSpectator()) {
            return null;
        }
        double reach = p.blockInteractionRange();
        RocketEntity aboard = DeckClient.rocket();
        Vec3 eyeWorld = p.getEyePosition(1.0F);
        double worldDistance = Double.POSITIVE_INFINITY;
        if (aboard == null) {
            HitResult hit = mc.hitResult;
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                worldDistance = eyeWorld.distanceTo(block.getLocation());
            }
        }
        List<RocketEntity> rockets = aboard != null ? List.of(aboard)
                : mc.level.getEntitiesOfClass(RocketEntity.class, new AABB(eyeWorld, eyeWorld).inflate(reach + 140.0));
        Target best = null;
        for (RocketEntity rocket : rockets) {
            RocketPortrait portrait = rocket.portrait();
            if (rocket.isRemoved() || portrait.isEmpty()) {
                continue;
            }
            Vector3d eye;
            Vector3d dir;
            if (rocket == aboard) {
                DeckState s = DeckClient.state();
                eye = new Vector3d(s.pos.x, s.pos.y + p.getEyeHeight(), s.pos.z);
                Vec3 look = Vec3.directionFromRotation(p.getXRot(), p.getYRot());
                dir = new Vector3d(look.x, look.y, look.z);
            } else {
                DeckFrame frame = rocket.deckFrame();
                Vec3 look = p.getViewVector(1.0F);
                eye = frame.toLocal(eyeWorld.x, eyeWorld.y, eyeWorld.z);
                Vector3d ahead = frame.toLocal(eyeWorld.x + look.x, eyeWorld.y + look.y, eyeWorld.z + look.z);
                dir = ahead.sub(eye);
            }
            PortraitRay.Hit hit = portrait.shapes().cast(eye.x, eye.y, eye.z, dir.x, dir.y, dir.z, reach);
            if (hit != null && hit.t() < worldDistance && (best == null || hit.t() < best.t())) {
                best = new Target(rocket, new BlockPos(hit.x(), hit.y(), hit.z()), hit.face(), hit.t(), new Vec3(hit.hx(), hit.hy(), hit.hz()));
            }
        }
        return best;
    }
}
