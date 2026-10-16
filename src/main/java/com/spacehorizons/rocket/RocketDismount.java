package com.spacehorizons.rocket;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Acha onde por o piloto em pé ao sair do foguete (ou antes de os blocos voltarem): o chão perto da caixa do foguete. */
public final class RocketDismount {
    /** Quantos anéis de blocos em volta da caixa do foguete a busca tenta. */
    private static final int RINGS = 4;

    private RocketDismount() {
    }

    /**
     * O ponto de chão livre mais perto em volta da caixa, na altura da base e um bloco abaixo ou acima dela; null se não
     * houver nenhum (foguete no ar, cercado). O ponto é um lugar onde uma entidade do tipo cabe em pé.
     */
    public static @Nullable Vec3 find(Level level, AABB box, EntityType<?> type) {
        int minX = Mth.floor(box.minX);
        int maxX = Mth.ceil(box.maxX) - 1;
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.ceil(box.maxZ) - 1;
        int baseY = Mth.floor(box.minY + 1.0E-4);
        for (int ring = 1; ring <= RINGS; ring++) {
            for (int dy : new int[] {0, -1, 1}) {
                for (int x = minX - ring; x <= maxX + ring; x++) {
                    for (int z = minZ - ring; z <= maxZ + ring; z++) {
                        boolean onRing = x == minX - ring || x == maxX + ring || z == minZ - ring || z == maxZ + ring;
                        if (!onRing) {
                            continue;
                        }
                        Vec3 spot = DismountHelper.findSafeDismountLocation(type, level, new BlockPos(x, baseY + dy, z), false);
                        if (spot != null) {
                            return spot;
                        }
                    }
                }
            }
        }
        return null;
    }
}
