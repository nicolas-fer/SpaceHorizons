package com.spacehorizons.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFogConfig;

import com.spacehorizons.SpaceHorizons;

/**
 * Desliga a neblina do DH enquanto há um mundo aberto: a curvatura do planeta já esconde a borda dos LODs, e a neblina
 * grossa do DH só escurecia o horizonte. Só é chamado com o DH carregado (as classes da API ficam isoladas aqui, como em
 * DhCurvatureConfig).
 *
 * Usa a camada de override da API: restaurar é limpar o override, e o valor do usuário volta.
 */
public final class DhFogConfig {
    private static final String REASON = "SpaceHorizons: neblina do DH desligada";

    /** Se o override que desliga a neblina está ativo. */
    private static boolean applied;

    private DhFogConfig() {
    }

    /** Desliga a neblina do DH (idempotente: só toca na config na primeira vez). */
    public static synchronized void disable() {
        if (applied) {
            return;
        }
        IDhApiFogConfig fog = fogConfig();
        if (fog == null) {
            return;
        }
        set(fog.enableDhFog(), false);
        applied = true;
    }

    /** Devolve a neblina do DH ao valor do usuário (ao sair do mundo). */
    public static synchronized void restore() {
        if (!applied) {
            return;
        }
        IDhApiFogConfig fog = fogConfig();
        if (fog != null) {
            fog.enableDhFog().clearValue();
        }
        applied = false;
    }

    private static <T> void set(IDhApiConfigValue<T> value, T newValue) {
        if (!value.getCanBeOverrodeByApi()) {
            SpaceHorizons.LOGGER.warn("DH não permite override da neblina pela API; a neblina do DH continua ligada");
            return;
        }
        value.setValue(newValue, REASON);
    }

    private static IDhApiFogConfig fogConfig() {
        // configs só existe quando o cliente do DH já inicializou (ou não existe em servidor dedicado)
        if (DhApi.Delayed.configs == null) {
            return null;
        }
        return DhApi.Delayed.configs.graphics().fog();
    }
}
