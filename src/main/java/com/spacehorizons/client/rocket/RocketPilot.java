package com.spacehorizons.client.rocket;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.RocketControlPayload;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.ship.AnchorRequestPayload;

import net.minecraft.client.CameraType;
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
 * O piloto no cliente: lê as teclas, mantém o acelerador e manda o {@link RocketControlPayload} ao servidor.
 *
 * <ul>
 *   <li>{@link MovementInputUpdateEvent}: guarda o {@code Input} do tick (W/S/A/D, Shift, Ctrl) e <b>mascara Shift e sprint</b>
 *       quando se pilota. Shift sobe o acelerador, e o vanilla desmontaria o jogador com ele; sem mascarar, o servidor também
 *       receberia Shift nos pacotes de entrada. A saída do foguete é a tecla G (pousado).</li>
 *   <li>{@link ClientTickEvent.Pre}: descarta os cliques das teclas do vanilla que dividem o código com as do foguete (Q solta
 *       item, E abre o inventário, T abre o chat), que o vanilla trataria logo em seguida.</li>
 *   <li>{@link ClientTickEvent.Post}: monta e envia o pacote (ao mudar e a cada 5 ticks; flags de borda uma vez por aperto).</li>
 * </ul>
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class RocketPilot {
    private static final RocketControlLink LINK = new RocketControlLink();
    /** Compara o acelerador comandado com o que o servidor aplicou (o painel avisa se o comando não foi aceito). */
    private static final ThrottleWatch WATCH = new ThrottleWatch();

    /** O {@code Input} do vanilla neste tick, antes da máscara (Shift/Ctrl viram acelerador, W/S/A/D viram giro). */
    private static Input held = Input.EMPTY;
    private static boolean wasPiloting;
    private static boolean helpShown;
    private static @Nullable RocketControlPayload lastSent;
    /** O último pacote que levava alguma flag de borda (SAS, paraquedas, desmontar, cheio, corta); para o autoteste. */
    private static @Nullable RocketControlPayload lastFlagged;
    private static int sentCount;

    private RocketPilot() {
    }

    /** O foguete que o jogador local pilota (sentado no cockpit), ou null. */
    public static @Nullable RocketEntity rocket() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() instanceof RocketEntity rocket && rocket.getControllingPassenger() == player ? rocket : null;
    }

    public static boolean isPiloting() {
        return rocket() != null;
    }

    /** O acelerador que o piloto comanda (0..1); o painel mostra este, que reage no mesmo tick. */
    public static float throttle() {
        return LINK.throttle();
    }

    /** O acelerador que o servidor aplicou (dado sincronizado do foguete que se pilota), ou o comandado se não se pilota. */
    public static float appliedThrottle() {
        RocketEntity rocket = rocket();
        return rocket != null ? rocket.throttle() : LINK.throttle();
    }

    /** O servidor não está aplicando o acelerador comandado há alguns ticks (o painel mostra os dois). */
    public static boolean throttleNotAccepted() {
        return WATCH.notAccepted();
    }

    /**
     * Só para o autoteste: esquece que estava pilotando e zera o acelerador guardado. Entre dois voos do roteiro o jogador troca de
     * foguete no mesmo tick (sem passar por "não pilotando"), e o acelerador do voo anterior (0,84 no meio de uma frenagem) seguia sendo
     * mandado ao foguete novo, que decolava sozinho antes da partida.
     */
    public static void forgetPilotingForTest() {
        wasPiloting = false;
        LINK.reset(0.0F);
        WATCH.reset();
    }

    /** O último pacote enviado ao servidor (para o autoteste conferir), ou null. */
    public static @Nullable RocketControlPayload lastSent() {
        return lastSent;
    }

    /** O último pacote enviado com flag de borda, ou null (para o autoteste). */
    public static @Nullable RocketControlPayload lastFlagged() {
        return lastFlagged;
    }

    /** Quantos pacotes de controle já saíram nesta sessão (para o autoteste). */
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
        // sem Shift (desmontaria) e sem sprint: o vanilla e o servidor não os veem
        event.getInput().keyPresses = new Input(in.forward(), in.backward(), in.left(), in.right(), in.jump(), false, false);
    }

    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
        if (isPiloting()) {
            discardVanillaClicksOnRocketKeys(Minecraft.getInstance());
        }
    }

    @SubscribeEvent
    static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        RocketEntity rocket = rocket();
        if (rocket == null) {
            wasPiloting = false;
            held = Input.EMPTY;
            WATCH.reset();
            return;
        }
        LocalPlayer player = mc.player;
        if (!wasPiloting) {
            wasPiloting = true;
            onBoard(mc, player, rocket);
        }
        boolean screenOpen = mc.gui.screen() != null;
        if (!screenOpen && consumeClick(RocketKeys.CAMERA)) {
            toggleCamera(mc, player);
        }
        if (!screenOpen && consumeClick(RocketKeys.ANCHOR)) {
            // o servidor confere tudo (gerador, parada, espaço livre) e responde com o estado e os blocos que impedem
            ClientPacketDistributor.sendToServer(new AnchorRequestPayload());
        }
        RocketControlLink.Keys keys = screenOpen ? RocketControlLink.Keys.NONE : sampleKeys(mc, rocket);
        RocketControlPayload payload = LINK.tick(keys);
        if (payload != null) {
            lastSent = payload;
            if (payload.flags() != 0) {
                lastFlagged = payload;
            }
            sentCount++;
            ClientPacketDistributor.sendToServer(payload);
        }
        WATCH.tick(LINK.throttle(), rocket.throttle());
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        wasPiloting = false;
        held = Input.EMPTY;
        lastSent = null;
    }

    // ---------------------------------------------------------------- entrada

    private static RocketControlLink.Keys sampleKeys(Minecraft mc, RocketEntity rocket) {
        boolean disassemble = consumeClick(RocketKeys.DISASSEMBLE);
        if (disassemble && rocket.flightMode() != FlightMode.LANDED) {
            // só pousado: avisa em vez de mandar um pedido que o servidor recusaria
            mc.player.sendOverlayMessage(Component.translatable("message.spacehorizons.rocket.not_landed"));
            disassemble = false;
        }
        return new RocketControlLink.Keys(held.forward(), held.backward(), held.left(), held.right(), RocketKeys.ROLL_LEFT.isDown(),
                RocketKeys.ROLL_RIGHT.isDown(), held.shift(), held.sprint(), consumeClick(RocketKeys.FULL_THROTTLE),
                consumeClick(RocketKeys.CUT_THROTTLE), consumeClick(RocketKeys.NEXT_SAS), consumeClick(RocketKeys.PARACHUTE), disassemble);
    }

    /** Esvazia a fila de cliques da tecla e diz se houve algum. */
    private static boolean consumeClick(KeyMapping key) {
        boolean any = false;
        while (key.consumeClick()) {
            any = true;
        }
        return any;
    }

    /** Joga fora os cliques das teclas do vanilla que usam o mesmo código de alguma tecla do foguete. */
    private static void discardVanillaClicksOnRocketKeys(Minecraft mc) {
        for (KeyMapping vanilla : mc.options.keyMappings) {
            if (RocketKeys.ALL.contains(vanilla)) {
                continue;
            }
            for (KeyMapping rocketKey : RocketKeys.ALL) {
                if (vanilla.getKey().equals(rocketKey.getKey())) {
                    while (vanilla.consumeClick()) {
                        // descartado
                    }
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- embarque e câmera

    /** Primeiro tick pilotando: acelerador no valor do foguete, cockpit em primeira pessoa, olhar livre zerado e a ajuda (uma vez). */
    private static void onBoard(Minecraft mc, LocalPlayer player, RocketEntity rocket) {
        LINK.reset(rocket.throttle());
        WATCH.reset();
        for (KeyMapping key : RocketKeys.ALL) {
            consumeClick(key);
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        resetLook(player);
        // troca o "Aperte Shift para desmontar" do vanilla, que aqui seria falso
        player.sendOverlayMessage(Component.translatable("message.spacehorizons.rocket.board_hint", RocketKeys.DISASSEMBLE.getTranslatedKeyMessage(),
                RocketKeys.CAMERA.getTranslatedKeyMessage()));
        if (!helpShown) {
            helpShown = true;
            player.sendSystemMessage(helpMessage(mc));
        }
    }

    private static void toggleCamera(Minecraft mc, LocalPlayer player) {
        mc.options.setCameraType(mc.options.getCameraType().isFirstPerson() ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        resetLook(player);
    }

    /** O olhar livre é o yaw/pitch do próprio jogador (o mouse já os mexe): zero = olhando para a frente da base da câmera. */
    static void resetLook(LocalPlayer player) {
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.yRotO = 0.0F;
        player.xRotO = 0.0F;
    }

    /** O texto curto de ajuda com as teclas atuais (respeita as trocas feitas na tela de controles). */
    static Component helpMessage(Minecraft mc) {
        return Component.translatable("message.spacehorizons.rocket.help", mc.options.keyUp.getTranslatedKeyMessage(),
                mc.options.keyDown.getTranslatedKeyMessage(), mc.options.keyLeft.getTranslatedKeyMessage(),
                mc.options.keyRight.getTranslatedKeyMessage(), RocketKeys.ROLL_LEFT.getTranslatedKeyMessage(),
                RocketKeys.ROLL_RIGHT.getTranslatedKeyMessage(), mc.options.keyShift.getTranslatedKeyMessage(),
                mc.options.keySprint.getTranslatedKeyMessage(), RocketKeys.FULL_THROTTLE.getTranslatedKeyMessage(),
                RocketKeys.CUT_THROTTLE.getTranslatedKeyMessage(), RocketKeys.NEXT_SAS.getTranslatedKeyMessage(),
                RocketKeys.PARACHUTE.getTranslatedKeyMessage(), RocketKeys.DISASSEMBLE.getTranslatedKeyMessage(),
                RocketKeys.CAMERA.getTranslatedKeyMessage());
    }
}
