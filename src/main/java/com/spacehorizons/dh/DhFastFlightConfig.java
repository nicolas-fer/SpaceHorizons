package com.spacehorizons.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiGeneratorPlan;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;

import com.spacehorizons.SpaceHorizons;

/**
 * Pausa a geração de LOD do Distant Horizons durante o voo rápido do foguete. A API 7.2 não tem "pausar" nem "esvaziar a
 * fila"; o que existe é o plano do gerador ({@link EDhApiGeneratorPlan#DISABLED}: nenhum terreno distante é gerado). Com ele
 * os LODs já gerados continuam sendo desenhados e a fila de geração para de crescer; o que já estava em andamento termina.
 * É um override da API: {@link #resume} limpa o override e o valor do usuário volta. Só é chamado com o DH carregado
 * (as classes da API ficam isoladas neste pacote, como em {@link DhFogConfig}).
 */
public final class DhFastFlightConfig {
    private static final String REASON = "SpaceHorizons: voo rápido do foguete (geração de LOD pausada)";

    private static boolean paused;

    private DhFastFlightConfig() {
    }

    /** Pausa a geração de LOD. Devolve false se o DH não está pronto ou não deixa a API mudar o plano. */
    public static synchronized boolean pause() {
        IDhApiConfigValue<EDhApiGeneratorPlan> plan = plan();
        if (plan == null) {
            return false;
        }
        if (!plan.getCanBeOverrodeByApi()) {
            SpaceHorizons.LOGGER.warn("DH não permite override do plano do gerador pela API; a geração de LOD não será pausada no voo rápido");
            return false;
        }
        if (!paused) {
            plan.setValue(EDhApiGeneratorPlan.DISABLED, REASON);
            paused = true;
        }
        return true;
    }

    /** Devolve a geração de LOD ao valor do usuário. */
    public static synchronized void resume() {
        if (!paused) {
            return;
        }
        IDhApiConfigValue<EDhApiGeneratorPlan> plan = plan();
        if (plan != null) {
            plan.clearValue();
        }
        paused = false;
    }

    public static synchronized boolean isPaused() {
        return paused;
    }

    private static IDhApiConfigValue<EDhApiGeneratorPlan> plan() {
        // configs só existe quando o cliente do DH já inicializou (ou não existe em servidor dedicado)
        if (DhApi.Delayed.configs == null) {
            return null;
        }
        return DhApi.Delayed.configs.worldGenerator().GeneratorPlan();
    }
}
