package com.spacehorizons.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;

import net.neoforged.fml.ModList;

/**
 * Liga a curvatura do DH ao raio do planeta enquanto ele está carregado.
 *
 * Usa a camada de override da API (setValue com motivo / clearValue), que não mexe na config
 * escrita pelo usuário: ao restaurar, basta limpar o override e o valor do usuário volta.
 */
final class DhCurvatureConfig {
    private static final String REASON = "SpaceHorizons: curvatura do planeta";

    private static boolean applied;

    private DhCurvatureConfig() {
    }

    static synchronized void apply(PlanetDefinition planet) {
        IDhApiConfigValue<Integer> ratio = curveRatioValue();
        if (ratio == null) {
            return;
        }
        int value = DhCurvatureModel.ratioFor(planet.radius(), planet.seaLevel());
        if (ModList.get().isLoaded("sodium")) {
            // Sodium desenha os chunks planos; LODs curvos aparecem como degrau na borda do alcance vanilla
            SpaceHorizons.LOGGER.warn("Sodium carregado: os chunks não são curvados, e a borda entre chunks e LODs do DH fica visível");
        }
        if (!ratio.getCanBeOverrodeByApi()) {
            SpaceHorizons.LOGGER.warn("DH não permite override da curvatura pela API; o planeta ficará sem curvatura certa");
            return;
        }
        applied = ratio.setValue(value, REASON);
        SpaceHorizons.LOGGER.info("Curvatura do DH para {} blocos de raio: earthCurveRatio={} ({})",
                planet.radius(), value, applied ? "aplicada" : "recusada");
    }

    static synchronized void restore() {
        if (!applied) {
            return;
        }
        IDhApiConfigValue<Integer> ratio = curveRatioValue();
        if (ratio != null) {
            ratio.clearValue();
        }
        applied = false;
    }

    private static IDhApiConfigValue<Integer> curveRatioValue() {
        // configs só existe quando o cliente do DH já inicializou (ou não existe em servidor dedicado)
        if (DhApi.Delayed.configs == null) {
            return null;
        }
        return DhApi.Delayed.configs.graphics().earthCurvatureRatio();
    }
}
