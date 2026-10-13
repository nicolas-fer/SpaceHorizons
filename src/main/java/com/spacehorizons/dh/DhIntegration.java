package com.spacehorizons.dh;

import java.util.Optional;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

import net.minecraft.world.level.Level;

/**
 * Ponte com o Distant Horizons. Só é chamado quando o DH está carregado: classes da API do DH
 * ficam neste pacote para o mod continuar funcionando sem ele.
 *
 * Ao carregar um level que é planeta, registra o gerador de LOD próprio. A curvatura do DH é ajustada
 * no cliente (DhCurvatureClient), pelo planeta que a câmera está vendo.
 */
public final class DhIntegration {
    private DhIntegration() {
    }

    public static void init() {
        DhApiEventRegister.on(DhApiLevelLoadEvent.class, new LevelLoad());
        DhCloudConfig.init();
    }

    /** Planeta de um level do Minecraft, pelo tipo de dimensão (ver Planets.forLevel). */
    static Optional<PlanetDefinition> planetOf(IDhApiLevelWrapper wrapper) {
        if (!(wrapper.getWrappedMcObject() instanceof Level level)) {
            return Optional.empty();
        }
        return Planets.forLevel(level);
    }

    private static final class LevelLoad extends DhApiLevelLoadEvent {
        @Override
        public void onLevelLoad(DhApiEventParam<EventParam> event) {
            IDhApiLevelWrapper wrapper = event.value.levelWrapper;
            Optional<PlanetDefinition> planet = planetOf(wrapper);
            if (planet.isEmpty()) {
                return;
            }
            Level level = (Level) wrapper.getWrappedMcObject();
            DhApiResult<Void> registered = DhApi.worldGenOverrides.registerWorldGeneratorOverride(
                    wrapper, new PlanetLodGenerator(wrapper, level, planet.get()));
            if (!registered.success) {
                SpaceHorizons.LOGGER.warn("DH recusou o gerador do planeta {}: {}", planet.get().id(), registered.message);
            }
        }
    }
}
