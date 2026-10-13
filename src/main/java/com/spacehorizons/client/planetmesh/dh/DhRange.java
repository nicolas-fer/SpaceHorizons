package com.spacehorizons.client.planetmesh.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig;

/**
 * Alcance de renderização do Distant Horizons. Fica num pacote próprio porque só pode ser tocado quando o DH
 * está carregado (ModList.isLoaded("distanthorizons")): as classes da API não existem sem ele.
 */
public final class DhRange {
    private DhRange() {
    }

    /** Raio dos LODs em blocos, ou -1 se o DH ainda não inicializou ou está com a renderização desligada. */
    public static int renderDistanceBlocks() {
        // configs só existe depois que o cliente do DH inicializou
        if (DhApi.Delayed.configs == null) {
            return -1;
        }
        IDhApiGraphicsConfig graphics = DhApi.Delayed.configs.graphics();
        Boolean enabled = graphics.renderingEnabled().getValue();
        if (enabled != null && !enabled) {
            return -1;
        }
        Integer chunks = graphics.chunkRenderDistance().getValue();
        return chunks == null ? -1 : chunks * 16;
    }
}
