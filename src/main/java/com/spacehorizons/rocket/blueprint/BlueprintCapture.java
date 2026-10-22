package com.spacehorizons.rocket.blueprint;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.rocket.RocketAssembly;
import com.spacehorizons.rocket.RocketBlockKind;
import com.spacehorizons.rocket.RocketDesign;

/**
 * Salva o foguete que está sobre uma plataforma como planta (lógica pura). Acha o foguete pela MESMA busca do painel e da montagem
 * ({@link RocketDesign#search}): o que a planta guarda é o que o painel mostra e o que o clique no cockpit montaria. Leva também as
 * plataformas ligadas que ficam sob o foguete (a pegada mais uma borda de 1) e sempre a plataforma clicada, que vira a âncora (0, 0, 0).
 */
public final class BlueprintCapture {
    private BlueprintCapture() {
    }

    /** O estado (string normalizada) do bloco do mundo em x, y, z. */
    @FunctionalInterface
    public interface StateNamer {
        String stateAt(int x, int y, int z);
    }

    /**
     * A planta do foguete sobre a plataforma em (padX, padY, padZ), ou null se não há peça nenhuma encostada nas plataformas. Um foguete
     * incompleto (sem cockpit, sem motor) também é salvo: a planta é um projeto, não exige estar montável.
     */
    public static @Nullable Blueprint capture(RocketAssembly.Grid grid, StateNamer namer, int padX, int padY, int padZ, String name, String author) {
        RocketDesign.Search search = RocketDesign.search(grid, padX, padY, padZ);
        RocketAssembly.Report report = search.report();
        if (report == null || report.result().cells().isEmpty()) {
            return null;
        }
        RocketAssembly.Result r = report.result();
        if (r.maxX() - padX >= Blueprint.RANGE || padX - r.minX() > Blueprint.RANGE || r.maxY() - padY >= Blueprint.RANGE
                || padY - r.minY() > Blueprint.RANGE || r.maxZ() - padZ >= Blueprint.RANGE || padZ - r.minZ() > Blueprint.RANGE) {
            return null; // não acontece com a busca limitada a 128 x 256 x 128 a partir de uma peça encostada na plataforma
        }
        Blueprint.Builder builder = Blueprint.builder("", name, author);
        for (int[] pad : search.pads()) {
            boolean anchor = pad[0] == padX && pad[1] == padY && pad[2] == padZ;
            boolean under = pad[0] >= r.minX() - 1 && pad[0] <= r.maxX() + 1 && pad[2] >= r.minZ() - 1 && pad[2] <= r.maxZ() + 1
                    && pad[1] >= r.minY() - 1 && pad[1] <= r.maxY() + 1;
            if (anchor || under) {
                builder.add(pad[0] - padX, pad[1] - padY, pad[2] - padZ, namer.stateAt(pad[0], pad[1], pad[2]), RocketBlockKind.PAD);
            }
        }
        for (RocketAssembly.Cell cell : r.cells()) {
            builder.add(cell.x() - padX, cell.y() - padY, cell.z() - padZ, namer.stateAt(cell.x(), cell.y(), cell.z()), cell.kind());
        }
        if (builder.size() > Blueprint.MAX_ENTRIES) {
            return null;
        }
        return builder.build();
    }
}
