package com.spacehorizons.ship.deck;

import java.util.List;
import java.util.function.Consumer;

import com.spacehorizons.rocket.PortraitGrid;
import com.spacehorizons.rocket.RocketPortrait;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Os sólidos do retrato como caixas no referencial local (coordenadas do retrato: o bloco {@code (x, y, z)} ocupa {@code [x, x+1]} etc.). A forma
 * de colisão de cada estado da paleta é lida uma vez ({@code getCollisionShape} sem mundo: para os blocos da nave só o estado importa) e
 * guardada; a consulta de uma região só olha as células dela. Vale para o servidor e para o cliente, cada um com o seu retrato.
 */
public final class DeckColliders implements DeckCollision.Colliders {
    private static final AABB[] NONE = new AABB[0];

    private final PortraitGrid grid;
    private final List<BlockState> palette;
    /** Caixas de colisão por índice da paleta, relativas à célula; {@code null} até a primeira consulta daquele índice. */
    private final AABB[][] shapes;

    public DeckColliders(RocketPortrait portrait) {
        this.grid = portrait.grid();
        this.palette = portrait.palette();
        this.shapes = new AABB[this.palette.size()][];
    }

    @Override
    public void collect(AABB region, Consumer<AABB> out) {
        int minX = Math.max(0, (int) Math.floor(region.minX));
        int minY = Math.max(0, (int) Math.floor(region.minY));
        int minZ = Math.max(0, (int) Math.floor(region.minZ));
        int maxX = Math.min(this.grid.sizeX - 1, (int) Math.floor(region.maxX));
        int maxY = Math.min(this.grid.sizeY - 1, (int) Math.floor(region.maxY));
        int maxZ = Math.min(this.grid.sizeZ - 1, (int) Math.floor(region.maxZ));
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int index = this.grid.get(x, y, z);
                    if (index == 0) {
                        continue;
                    }
                    for (AABB box : this.boxesOf(index)) {
                        out.accept(box.move(x, y, z));
                    }
                }
            }
        }
    }

    /** O estado do bloco na célula, para saber o que está sob os pés (ar fora do retrato). */
    public BlockState stateAt(int x, int y, int z) {
        return this.palette.get(this.grid.get(x, y, z));
    }

    private AABB[] boxesOf(int index) {
        AABB[] boxes = this.shapes[index];
        if (boxes == null) {
            boxes = shapeOf(this.palette.get(index));
            this.shapes[index] = boxes;
        }
        return boxes;
    }

    private static AABB[] shapeOf(BlockState state) {
        try {
            var shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (shape.isEmpty()) {
                return NONE;
            }
            return shape.toAabbs().toArray(new AABB[0]);
        } catch (RuntimeException e) {
            // um bloco cuja forma depende do mundo: sólido por segurança (o convés não pode ter buraco que derrube o tripulante)
            return state.isSolid() ? new AABB[] {Shapes.block().bounds()} : NONE;
        }
    }
}
