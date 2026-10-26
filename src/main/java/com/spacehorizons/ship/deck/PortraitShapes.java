package com.spacehorizons.ship.deck;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.rocket.PortraitGrid;
import com.spacehorizons.rocket.RocketPortrait;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * As formas de SELEÇÃO (a caixa que a mira enxerga e o contorno desenha) dos blocos do retrato, por estado da paleta, lidas uma vez. Diferem da
 * colisão ({@link DeckColliders}): a porta aberta é uma placa fina na seleção e some na colisão, a fornalha e o baú são cheios em ambas, e um
 * bloco sem colisão (tocha, placa) ainda é selecionável. Serve ao servidor (conferir o clique) e ao cliente (a mira e o contorno).
 */
public final class PortraitShapes implements PortraitRay.Cells {
    private static final AABB[] NONE = new AABB[0];

    private final PortraitGrid grid;
    private final List<BlockState> palette;
    private final AABB[][] shapes;

    public PortraitShapes(RocketPortrait portrait) {
        this.grid = portrait.grid();
        this.palette = portrait.palette();
        this.shapes = new AABB[this.palette.size()][];
    }

    @Override
    public void boxes(int x, int y, int z, Consumer<AABB> out) {
        int index = this.grid.get(x, y, z);
        if (index == 0) {
            return;
        }
        for (AABB box : this.of(index)) {
            out.accept(box);
        }
    }

    /** As caixas de seleção da célula, relativas a ela (vazio se não há bloco). */
    public AABB[] at(int x, int y, int z) {
        int index = this.grid.get(x, y, z);
        return index == 0 ? NONE : this.of(index);
    }

    /** Lança a mira no retrato, em coordenadas locais; null se não acerta. */
    public PortraitRay.@Nullable Hit cast(double ox, double oy, double oz, double dx, double dy, double dz, double reach) {
        return PortraitRay.cast(ox, oy, oz, dx, dy, dz, reach, this.grid.sizeX, this.grid.sizeY, this.grid.sizeZ, this);
    }

    private AABB[] of(int index) {
        AABB[] boxes = this.shapes[index];
        if (boxes == null) {
            boxes = shapeOf(this.palette.get(index));
            this.shapes[index] = boxes;
        }
        return boxes;
    }

    private static AABB[] shapeOf(BlockState state) {
        try {
            var shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return shape.isEmpty() ? NONE : shape.toAabbs().toArray(new AABB[0]);
        } catch (RuntimeException e) {
            return new AABB[] {Shapes.block().bounds()};
        }
    }
}
