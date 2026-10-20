package com.spacehorizons.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Onde por o jogador em pé dentro de uma nave no estaleiro (perto do cockpit, que é um bloco cheio onde o piloto "senta"). */
final class ShipSpots {
    private static final int RADIUS = 8;

    private ShipSpots() {
    }

    /** O ponto de entrada de uma nave vinda de um foguete: em pé no chão livre mais perto do cockpit. */
    static Vec3 nearCockpit(ServerLevel yard, ShipRecord ship) {
        ShipFlightSnapshot snap = ship.snapshot().orElseThrow();
        ShipYard.Box box = ship.box();
        BlockPos cockpit = new BlockPos(box.minX() + snap.cockpit().getX(), box.minY() + snap.cockpit().getY(), box.minZ() + snap.cockpit().getZ());
        return standNear(yard, cockpit, RADIUS);
    }

    /**
     * O ponto de chão mais perto de {@code around} onde um jogador cabe em pé (dois blocos sem colisão em cima de um com colisão),
     * dentro do raio; sem nenhum, dois blocos acima de {@code around}. Os chunks do entorno precisam estar carregados.
     */
    static Vec3 standNear(ServerLevel yard, BlockPos around, int radius) {
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    double dist = dx * dx + dz * dz + dy * dy;
                    if (dist >= bestDist) {
                        continue;
                    }
                    int x = around.getX() + dx;
                    int y = around.getY() + dy;
                    int z = around.getZ() + dz;
                    if (free(yard, p.set(x, y, z)) && free(yard, p.set(x, y + 1, z)) && solid(yard, p.set(x, y - 1, z))) {
                        bestDist = dist;
                        best = new Vec3(x + 0.5, y, z + 0.5);
                    }
                }
            }
        }
        return best != null ? best : new Vec3(around.getX() + 0.5, around.getY() + 2.0, around.getZ() + 0.5);
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
