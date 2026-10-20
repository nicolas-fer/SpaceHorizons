package com.spacehorizons.client.ship;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.rocket.RocketControlLink;
import com.spacehorizons.client.rocket.RocketKeys;
import com.spacehorizons.client.rocket.ThrottleWatch;
import com.spacehorizons.rocket.RocketControlPayload;
import com.spacehorizons.ship.ShipControlPayload;
import com.spacehorizons.ship.ShipSeatEntity;
import com.spacehorizons.ship.ShipStatePayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import org.jspecify.annotations.Nullable;

/**
 * O piloto da nave no cliente (docs/PLANO_NAVES.md, seção 5; fase 1e): o equivalente do {@code RocketPilot} para quem está sentado no assento da
 * nave. Reaproveita as teclas do foguete ({@link RocketKeys}), o acelerador em alavanca ({@link RocketControlLink}: Shift sobe, Ctrl desce, Z cheio,
 * X corta) e o vigia do acelerador ({@link ThrottleWatch}); manda o comando em {@link ShipControlPayload}. W/S giram a arfagem, A/D a guinada, Q/E a
 * rolagem, T muda o SAS. A tecla de desmontar do foguete (G) levanta do assento. A câmera é a normal do jogador: a nave está parada no
 * estaleiro, quem gira é o céu pela janela.
 *
 * <p>Shift e sprint são mascarados enquanto se pilota (Shift sobe o acelerador, e o vanilla desmontaria o jogador do assento com ele).
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class ShipPilot {
    private static final RocketControlLink LINK = new RocketControlLink();
    private static final ThrottleWatch WATCH = new ThrottleWatch();

    private static Input held = Input.EMPTY;
    private static boolean wasPiloting;
    private static boolean helpShown;
    private static @Nullable RocketControlPayload lastSent;
    private static @Nullable RocketControlPayload lastFlagged;
    private static int sentCount;

    private ShipPilot() {
    }

    /** O jogador local está sentado no assento de uma nave (pilota)? */
    public static boolean isPiloting() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() instanceof ShipSeatEntity;
    }

    /** O acelerador que o piloto comanda (0..1). */
    public static float throttle() {
        return LINK.throttle();
    }

    /** O acelerador que o servidor aplicou (do último estado da nave), ou o comandado se ainda não há estado. */
    public static float appliedThrottle() {
        ShipStatePayload state = ShipRenderClient.latest();
        return state != null ? state.throttle() : LINK.throttle();
    }

    /** O servidor não está aplicando o acelerador comandado há alguns ticks. */
    public static boolean throttleNotAccepted() {
        return WATCH.notAccepted();
    }

    /** Só para o autoteste: esquece que estava pilotando e zera o acelerador guardado. */
    public static void forgetPilotingForTest() {
        wasPiloting = false;
        LINK.reset(0.0F);
        WATCH.reset();
    }

    public static @Nullable RocketControlPayload lastSent() {
        return lastSent;
    }

    public static @Nullable RocketControlPayload lastFlagged() {
        return lastFlagged;
    }

    public static int sentCount() {
        return sentCount;
    }

    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (event.getEntity() != Minecraft.getInstance().player || !isPiloting()) {
            return;
        }
        Input in = event.getInput().keyPresses;
        held = in;
        // sem Shift (desmontaria do assento) e sem sprint: o vanilla e o servidor não os veem
        event.getInput().keyPresses = new Input(in.forward(), in.backward(), in.left(), in.right(), in.jump(), false, false);
    }

    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
        if (isPiloting()) {
            discardVanillaClicksOnPilotKeys(Minecraft.getInstance());
        }
    }

    @SubscribeEvent
    static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!isPiloting()) {
            wasPiloting = false;
            held = Input.EMPTY;
            WATCH.reset();
            return;
        }
        LocalPlayer player = mc.player;
        if (!wasPiloting) {
            wasPiloting = true;
            onBoard(mc, player);
        }
        boolean screenOpen = mc.gui.screen() != null;
        RocketControlLink.Keys keys = screenOpen ? RocketControlLink.Keys.NONE : sampleKeys();
        RocketControlPayload payload = LINK.tick(keys);
        if (payload != null) {
            lastSent = payload;
            if (payload.flags() != 0) {
                lastFlagged = payload;
            }
            sentCount++;
            ClientPacketDistributor.sendToServer(new ShipControlPayload(payload));
        }
        WATCH.tick(LINK.throttle(), appliedThrottle());
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        wasPiloting = false;
        held = Input.EMPTY;
        lastSent = null;
    }

    // ---------------------------------------------------------------- entrada

    /** As teclas deste tick. A tecla "desmontar" do foguete levanta do assento (o servidor trata o bit como "levantar"). */
    private static RocketControlLink.Keys sampleKeys() {
        return new RocketControlLink.Keys(held.forward(), held.backward(), held.left(), held.right(), RocketKeys.ROLL_LEFT.isDown(),
                RocketKeys.ROLL_RIGHT.isDown(), held.shift(), held.sprint(), consumeClick(RocketKeys.FULL_THROTTLE), consumeClick(RocketKeys.CUT_THROTTLE),
                consumeClick(RocketKeys.NEXT_SAS), false, consumeClick(RocketKeys.DISASSEMBLE));
    }

    private static boolean consumeClick(KeyMapping key) {
        boolean any = false;
        while (key.consumeClick()) {
            any = true;
        }
        return any;
    }

    /** Joga fora os cliques das teclas do vanilla que usam o mesmo código de alguma tecla do piloto (Q solta, E abre o inventário, T abre o chat). */
    private static void discardVanillaClicksOnPilotKeys(Minecraft mc) {
        for (KeyMapping vanilla : mc.options.keyMappings) {
            if (RocketKeys.ALL.contains(vanilla)) {
                continue;
            }
            for (KeyMapping pilotKey : RocketKeys.ALL) {
                if (vanilla.getKey().equals(pilotKey.getKey())) {
                    while (vanilla.consumeClick()) {
                        // descartado
                    }
                    break;
                }
            }
        }
    }

    /** Primeiro tick sentado: o acelerador começa no valor da nave e a ajuda aparece (uma vez). */
    private static void onBoard(Minecraft mc, LocalPlayer player) {
        ShipStatePayload state = ShipRenderClient.latest();
        LINK.reset(state != null ? state.throttle() : 0.0F);
        WATCH.reset();
        for (KeyMapping key : RocketKeys.ALL) {
            consumeClick(key);
        }
        player.sendOverlayMessage(Component.translatable("message.spacehorizons.ship.board_hint", RocketKeys.DISASSEMBLE.getTranslatedKeyMessage(),
                RocketKeys.NEXT_SAS.getTranslatedKeyMessage()));
        if (!helpShown) {
            helpShown = true;
            player.sendSystemMessage(Component.translatable("message.spacehorizons.ship.help", mc.options.keyUp.getTranslatedKeyMessage(),
                    mc.options.keyDown.getTranslatedKeyMessage(), mc.options.keyLeft.getTranslatedKeyMessage(), mc.options.keyRight.getTranslatedKeyMessage(),
                    RocketKeys.ROLL_LEFT.getTranslatedKeyMessage(), RocketKeys.ROLL_RIGHT.getTranslatedKeyMessage(), mc.options.keyShift.getTranslatedKeyMessage(),
                    mc.options.keySprint.getTranslatedKeyMessage(), RocketKeys.FULL_THROTTLE.getTranslatedKeyMessage(), RocketKeys.CUT_THROTTLE.getTranslatedKeyMessage(),
                    RocketKeys.NEXT_SAS.getTranslatedKeyMessage(), RocketKeys.DISASSEMBLE.getTranslatedKeyMessage()));
        }
    }
}
