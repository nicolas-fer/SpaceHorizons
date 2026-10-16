package com.spacehorizons.client.rocket;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.spacehorizons.SpaceHorizons;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;

/**
 * As teclas do foguete (categoria "Space Horizons — Foguete" na tela de controles). W/S (arfagem), A/D (guinada) e
 * Shift/Ctrl (acelerador) vêm do {@code Input} do vanilla e das teclas do próprio jogo, e não aparecem aqui.
 *
 * <p>Todas usam o contexto {@link Context#ROCKET}: só valem com o jogador pilotando, e não acusam conflito com as teclas do
 * vanilla que têm o mesmo código (Q solta item, E abre o inventário, T abre o chat). Como o vanilla ainda receberia o aperto,
 * {@link RocketPilot} descarta os cliques dessas teclas do vanilla enquanto se pilota.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class RocketKeys {
    /** Contexto das teclas do foguete: ativo só quando o jogador pilota um foguete e não há tela aberta. */
    public enum Context implements IKeyConflictContext {
        ROCKET;

        @Override
        public boolean isActive() {
            return RocketPilot.isPiloting() && net.minecraft.client.Minecraft.getInstance().gui.screen() == null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    }

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "rocket"));

    public static final KeyMapping ROLL_LEFT = key("roll_left", InputConstants.KEY_Q);
    public static final KeyMapping ROLL_RIGHT = key("roll_right", InputConstants.KEY_E);
    public static final KeyMapping FULL_THROTTLE = key("full_throttle", InputConstants.KEY_Z);
    public static final KeyMapping CUT_THROTTLE = key("cut_throttle", InputConstants.KEY_X);
    public static final KeyMapping NEXT_SAS = key("next_sas", InputConstants.KEY_T);
    public static final KeyMapping PARACHUTE = key("parachute", InputConstants.KEY_P);
    public static final KeyMapping DISASSEMBLE = key("disassemble", InputConstants.KEY_G);
    public static final KeyMapping CAMERA = key("camera", InputConstants.KEY_V);

    /** Todas, na ordem em que aparecem na tela de controles. */
    public static final List<KeyMapping> ALL =
            List.of(ROLL_LEFT, ROLL_RIGHT, FULL_THROTTLE, CUT_THROTTLE, NEXT_SAS, PARACHUTE, DISASSEMBLE, CAMERA);

    private RocketKeys() {
    }

    private static KeyMapping key(String name, int keysym) {
        return new KeyMapping("key." + SpaceHorizons.MODID + ".rocket." + name, Context.ROCKET, InputConstants.Type.KEYBOARD, keysym, CATEGORY);
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        ALL.forEach(event::register);
    }
}
