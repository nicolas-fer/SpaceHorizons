package com.spacehorizons.ship;

import java.util.List;

import com.spacehorizons.rocket.RocketAssembly;
import com.spacehorizons.rocket.RocketBlockKind;
import com.spacehorizons.rocket.RocketException;

/**
 * Busca da montagem de uma nave no chão, a partir da ponte ({@code ship_bridge}), com as mesmas regras e a mesma validação do foguete
 * ({@link RocketAssembly}), só com outras exigências: a nave não precisa de motor nem de tanque (docs/PLANO_NAVES.md, 3.1). Lógica pura
 * sobre uma grade de papéis; o adaptador do mundo é {@link ShipKinds#assemblyKind}. A ponte entra como {@link RocketBlockKind#COCKPIT}
 * (é o cockpit da nave), então duas pontes ou um cockpit de foguete dentro da nave recusam a montagem.
 *
 * <p>Teto de blocos: o mesmo do foguete ({@link RocketAssembly#MAX_BLOCKS}, 8.192), caixa de 128 × 256 × 128. Nenhum bloco sai do mundo
 * antes de a busca inteira passar: o resultado só diz o que remover.
 */
public final class ShipAssembly {
    /** As exigências de uma nave: nenhum motor nem tanque obrigatório (a ponte basta como núcleo). */
    public static final RocketAssembly.Rules RULES = new RocketAssembly.Rules(false, false);

    /** A nave ancorada no ar (spike S6) é montada de novo sem chão: as mesmas exigências, sem a plataforma de lançamento. */
    public static final RocketAssembly.Rules AIRBORNE_RULES = new RocketAssembly.Rules(false, false, false);

    private ShipAssembly() {
    }

    /** Procura a nave cuja ponte está em (x, y, z); lança {@link RocketException} se a montagem deve ser recusada. */
    public static RocketAssembly.Result scan(RocketAssembly.Grid grid, int x, int y, int z) throws RocketException {
        return RocketAssembly.scan(grid, x, y, z, RocketAssembly.MAX_BLOCKS, RocketAssembly.MAX_ITERATIONS, RocketAssembly.MAX_EXTENT_XZ,
                RocketAssembly.MAX_EXTENT_Y, RULES);
    }

    /** Como {@link #scan}, para a nave ancorada no ar: sem exigir a plataforma ({@link #AIRBORNE_RULES}). */
    public static RocketAssembly.Result scanAirborne(RocketAssembly.Grid grid, int x, int y, int z) throws RocketException {
        return RocketAssembly.scan(grid, x, y, z, RocketAssembly.MAX_BLOCKS, RocketAssembly.MAX_ITERATIONS, RocketAssembly.MAX_EXTENT_XZ,
                RocketAssembly.MAX_EXTENT_Y, AIRBORNE_RULES);
    }

    /** O relatório inteiro (sem lançar), com todos os problemas: o mesmo que a montagem usaria. */
    public static RocketAssembly.Report analyze(RocketAssembly.Grid grid, int x, int y, int z) {
        return RocketAssembly.analyze(grid, List.<int[]>of(new int[] {x, y, z}), RocketAssembly.MAX_BLOCKS, RocketAssembly.MAX_ITERATIONS,
                RocketAssembly.MAX_EXTENT_XZ, RocketAssembly.MAX_EXTENT_Y, RULES);
    }
}
