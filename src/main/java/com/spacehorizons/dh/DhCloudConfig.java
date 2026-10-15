package com.spacehorizons.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelUnloadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Num planeta as nuvens são as do vanilla (curvadas pelo shader, com alcance ampliado); as do DH são camadas
 * planas, desenhadas pelo próprio DH, e não acompanham a curvatura. Decisão: desligá-las pela API enquanto o level
 * é um planeta. O override da API não toca na config do usuário: clearValue devolve o valor dele.
 *
 * Efeito colateral do DH: no primeiro frame de cada renderer ele grava "nuvens vanilla = desligadas" nas opções
 * do jogador (para as vanilla não cobrirem os LODs). Sem tratar isso, desligar as nuvens do DH deixaria o planeta
 * sem nenhuma nuvem. Depois que o DH renderizou o primeiro frame, o valor é restaurado uma vez: o que o
 * usuário tinha ao entrar no planeta, ou Fancy se já estava desligado (o DH grava isso e ele persiste entre
 * sessões, então "desligado" não é uma escolha reconhecível). Ao sair do planeta volta o valor de antes.
 * Depois da restauração a opção é do usuário: mudar no menu de vídeo vale.
 *
 * Só é chamado com o DH carregado (as classes da API ficam isoladas no pacote dh).
 */
final class DhCloudConfig {
    private static final String REASON = "SpaceHorizons: nuvens do planeta são as vanilla curvadas";
    /** Quando o usuário já estava com as nuvens desligadas ao entrar, é isto que o planeta passa a ter. */
    private static final CloudStatus FALLBACK_STATUS = CloudStatus.FANCY;
    /** Limite de ticks esperando o DH renderizar (ele pode estar desligado); passado disso restaura mesmo assim. */
    private static final int MAX_WAIT_TICKS = 20 * 60;

    /** true entre o load e o unload de um level que é planeta. */
    private static volatile boolean active;
    /**
     * Level (planeta) carregado mais recentemente. Numa viagem o DH carrega o planeta novo e só depois descarrega o antigo:
     * sem isto, o unload do antigo desligava o override do novo e as nuvens planas do DH voltavam (visto ao voltar da Lua).
     */
    private static volatile Object currentLevel;
    /** O DH já executou pelo menos um frame neste planeta (e, portanto, já fez o override das opções). */
    private static volatile boolean dhRendered;
    /** Valor das nuvens vanilla ao entrar no planeta (null = ainda não lido; lido no primeiro tick). */
    private static CloudStatus statusOnEntry;
    /** Valor que a restauração gravou (null = não gravou nada). */
    private static CloudStatus statusWritten;
    private static boolean statusFixed;
    private static int waitedTicks;
    private static boolean overrideRefused;

    private DhCloudConfig() {
    }

    /** Registra os handlers do DH e o tick do cliente. Chamar uma vez, com o DH carregado. */
    static void init() {
        DhApiEventRegister.on(DhApiLevelLoadEvent.class, new LevelLoad());
        DhApiEventRegister.on(DhApiLevelUnloadEvent.class, new LevelUnload());
        DhApiEventRegister.on(DhApiBeforeRenderEvent.class, new BeforeRender());
        NeoForge.EVENT_BUS.addListener(DhCloudConfig::onClientTick);
    }

    private static synchronized void enter() {
        active = true;
        dhRendered = false;
        statusOnEntry = null;
        statusWritten = null;
        statusFixed = false;
        waitedTicks = 0;
        overrideRefused = false;
        applyOverride();
    }

    private static synchronized void leave() {
        if (!active) {
            return;
        }
        active = false;
        IDhApiConfigValue<Boolean> clouds = cloudsValue();
        if (clouds != null) {
            clouds.clearValue();
        }
        // as opções só são seguras no thread principal; os handlers do DH podem vir de outro
        Minecraft.getInstance().execute(DhCloudConfig::restoreVanillaStatus);
    }

    /** Desliga as nuvens do DH; religa o override se alguém o limpou (p.ex. a neblina por altitude do DhFogConfig). */
    private static void applyOverride() {
        IDhApiConfigValue<Boolean> clouds = cloudsValue();
        if (clouds == null || overrideRefused || !clouds.getValue()) {
            return;
        }
        if (!clouds.getCanBeOverrodeByApi()) {
            overrideRefused = true;
            SpaceHorizons.LOGGER.warn("DH não permite override das nuvens pela API; as nuvens planas do DH continuam no planeta");
            return;
        }
        clouds.setValue(false, REASON);
    }

    private static synchronized void onClientTick(ClientTickEvent.Post event) {
        if (!active) {
            return;
        }
        applyOverride();
        OptionInstance<CloudStatus> option = Minecraft.getInstance().options.cloudStatus();
        if (statusOnEntry == null) {
            statusOnEntry = option.get();
        }
        if (statusFixed) {
            return;
        }
        if (!dhRendered && ++waitedTicks < MAX_WAIT_TICKS) {
            return;
        }
        statusFixed = true;
        if (option.get() == CloudStatus.OFF) {
            CloudStatus target = statusOnEntry != CloudStatus.OFF ? statusOnEntry : FALLBACK_STATUS;
            option.set(target);
            statusWritten = target;
            SpaceHorizons.LOGGER.info("Nuvens vanilla do planeta restauradas para {} (o DH as desliga ao renderizar)", target);
        }
    }

    /** Volta as nuvens vanilla ao valor de antes do planeta, se a restauração as mudou e o usuário não mexeu depois. */
    private static synchronized void restoreVanillaStatus() {
        if (statusWritten == null || statusOnEntry == null) {
            return;
        }
        OptionInstance<CloudStatus> option = Minecraft.getInstance().options.cloudStatus();
        if (option.get() == statusWritten) {
            option.set(statusOnEntry);
        }
        statusWritten = null;
    }

    private static IDhApiConfigValue<Boolean> cloudsValue() {
        // configs só existe quando o cliente do DH já inicializou (ou não existe em servidor dedicado)
        if (DhApi.Delayed.configs == null) {
            return null;
        }
        return DhApi.Delayed.configs.graphics().genericRendering().cloudRenderingEnabled();
    }

    private static final class LevelLoad extends DhApiLevelLoadEvent {
        @Override
        public void onLevelLoad(DhApiEventParam<EventParam> event) {
            if (DhIntegration.planetOf(event.value.levelWrapper).isPresent()) {
                currentLevel = event.value.levelWrapper;
                enter();
            }
        }
    }

    private static final class LevelUnload extends DhApiLevelUnloadEvent {
        @Override
        public void onLevelUnload(DhApiEventParam<EventParam> event) {
            if (DhIntegration.planetOf(event.value.levelWrapper).isPresent()
                    && java.util.Objects.equals(currentLevel, event.value.levelWrapper)) {
                // só sai do planeta se o descarregado é o atual; o descarregamento do anterior (depois do load do novo) é ignorado
                currentLevel = null;
                leave();
            }
        }
    }

    private static final class BeforeRender extends DhApiBeforeRenderEvent {
        @Override
        public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
            if (active) {
                dhRendered = true;
            }
        }
    }
}
