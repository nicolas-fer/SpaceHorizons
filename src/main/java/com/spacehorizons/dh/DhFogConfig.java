package com.spacehorizons.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFarFogConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFogConfig;

import com.spacehorizons.SpaceHorizons;

/**
 * Reduz a neblina do DH conforme a altitude e a desliga no espaço. Só é chamado com o DH carregado
 * (as classes da API ficam isoladas aqui, como em DhCurvatureConfig).
 *
 * Usa a camada de override da API: restaurar é limpar o override, e o valor do usuário volta.
 * A densidade é quantizada em passos de 1/16 para não disparar listeners de config a cada tick.
 */
public final class DhFogConfig {
    private static final String REASON = "SpaceHorizons: neblina por altitude";
    private static final int STEPS = 16;
    /** Abaixo disso a neblina do DH é desligada de vez (o fade já está imperceptível). */
    private static final double OFF_BELOW = 0.02;

    /** Passo atual aplicado (STEPS = sem alteração, 0 = desligada); -1 = nenhum override ativo. */
    private static int appliedStep = -1;

    private DhFogConfig() {
    }

    /** @param density densidade da atmosfera na câmera, 1 (chão) a 0 (espaço) */
    public static synchronized void update(double density) {
        IDhApiFogConfig fog = fogConfig();
        if (fog == null) {
            return;
        }
        int step;
        if (density >= 0.999) {
            step = STEPS;
        } else if (density <= OFF_BELOW) {
            step = 0;
        } else {
            step = (int) Math.round(density * STEPS);
        }
        if (step == STEPS) {
            if (appliedStep >= 0) {
                clear(fog);
            }
            return;
        }
        if (step == appliedStep) {
            return;
        }
        IDhApiFarFogConfig far = fog.farFog();
        if (step == 0) {
            // sem neblina: desliga e deixa a espessura limpa (valor do usuário) para quando religar
            set(fog.enableDhFog(), false);
            far.farFogMinThickness().clearValue();
            far.farFogMaxThickness().clearValue();
        } else {
            double scale = step / (double) STEPS;
            fog.enableDhFog().clearValue();
            scaleThickness(far.farFogMinThickness(), scale);
            scaleThickness(far.farFogMaxThickness(), scale);
        }
        appliedStep = step;
    }

    /** Devolve a neblina do DH ao valor do usuário (ao sair do planeta ou do mundo). */
    public static synchronized void restore() {
        if (appliedStep < 0) {
            return;
        }
        IDhApiFogConfig fog = fogConfig();
        if (fog != null) {
            clear(fog);
        } else {
            appliedStep = -1;
        }
    }

    private static void clear(IDhApiFogConfig fog) {
        fog.enableDhFog().clearValue();
        fog.farFog().farFogMinThickness().clearValue();
        fog.farFog().farFogMaxThickness().clearValue();
        appliedStep = -1;
    }

    private static void scaleThickness(IDhApiConfigValue<Float> value, double scale) {
        // getTrueValue é o valor do usuário, sem o nosso override anterior
        set(value, (float) (value.getTrueValue() * scale));
    }

    private static <T> void set(IDhApiConfigValue<T> value, T newValue) {
        if (!value.getCanBeOverrodeByApi()) {
            SpaceHorizons.LOGGER.warn("DH não permite override da neblina pela API; a neblina do DH não acompanhará a altitude");
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
