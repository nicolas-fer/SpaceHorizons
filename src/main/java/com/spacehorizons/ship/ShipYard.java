package com.spacehorizons.ship;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import org.jspecify.annotations.Nullable;

/**
 * O estaleiro ({@code spacehorizons:shipyard}): a dimensão onde as naves vivem como blocos reais parados (caminho C do
 * {@code docs/PLANO_NAVES.md}). As coordenadas dela são só um mapa de células, sem relação com o espaço virtual.
 *
 * <h2>Geometria</h2>
 * Grade de células de {@link #CELL} x {@link #CELL} blocos em XZ (64 x 64 chunks), uma por nave, numeradas em espiral
 * ({@link ShipYardAllocator#cellCoords}). A caixa da nave fica CENTRADA na célula (alinhada a chunks, para a nave não
 * dividir chunks com o vazio e cobrir o mínimo de colunas) e começa em Y = {@link #BASE_Y}. O tamanho máximo (128 x 128 x 256)
 * deixa pelo menos 448 blocos de vazio entre duas naves. A dimensão tem altura {@link #HEIGHT} a partir de Y = 0
 * ({@code dimension_type/shipyard.json}): 32 de vão embaixo, 256 de nave e 96 de vão em cima.
 */
public final class ShipYard {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "shipyard");
    /** Chave da dimensão (a do world preset). */
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, ID);
    /** Tipo de dimensão (para o cliente, que só conhece o tipo: ver {@link #is}). */
    public static final ResourceKey<DimensionType> TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, ID);

    /** Lado da célula, em blocos (múltiplo de 16). */
    public static final int CELL = 1024;
    /** Y do piso da caixa da nave. */
    public static final int BASE_Y = 32;
    /** Altura da dimensão (min_y 0). */
    public static final int HEIGHT = 384;
    /** Tamanho máximo da nave (D2 do plano): X, Z e Y. */
    public static final int MAX_X = 128;
    public static final int MAX_Z = 128;
    public static final int MAX_Y = 256;
    public static final int MIN_SIZE = 16;

    private ShipYard() {
    }

    /** O nível é o estaleiro (pelo tipo de dimensão, como SpaceDimension.is)? Servidor e cliente respondem igual. */
    public static boolean is(Level level) {
        return level.dimensionTypeRegistration().unwrapKey().filter(k -> k.identifier().equals(ID)).isPresent();
    }

    /** O nível do estaleiro, ou null se o mundo não tem a dimensão (criado antes dela existir, sem o world preset novo). */
    public static @Nullable ServerLevel level(MinecraftServer server) {
        return server.getLevel(DIMENSION);
    }

    /** Menor X da célula {@code index}. */
    public static int cellMinX(int index) {
        return ShipYardAllocator.cellCoords(index)[0] * CELL;
    }

    /** Menor Z da célula {@code index}. */
    public static int cellMinZ(int index) {
        return ShipYardAllocator.cellCoords(index)[1] * CELL;
    }

    /** A célula {@code index} contém o ponto (x, z) do estaleiro? */
    public static boolean cellContains(int index, double x, double z) {
        double minX = cellMinX(index);
        double minZ = cellMinZ(index);
        return x >= minX && x < minX + CELL && z >= minZ && z < minZ + CELL;
    }

    /** A caixa (em blocos do estaleiro) de uma nave de tamanho {@code sx, sy, sz} na célula {@code index}. */
    public static Box box(int index, int sx, int sy, int sz) {
        checkSize(sx, sy, sz);
        int x = cellMinX(index) + (((CELL - sx) / 2) & ~15);
        int z = cellMinZ(index) + (((CELL - sz) / 2) & ~15);
        return new Box(x, BASE_Y, z, sx, sy, sz);
    }

    /** Valida o tamanho contra o plano (D2): lados de {@link #MIN_SIZE} até 128 x 256 x 128. */
    public static void checkSize(int sx, int sy, int sz) {
        if (sx < MIN_SIZE || sy < MIN_SIZE || sz < MIN_SIZE || sx > MAX_X || sy > MAX_Y || sz > MAX_Z) {
            throw new IllegalArgumentException("tamanho fora do limite: " + sx + "x" + sy + "x" + sz + " (de " + MIN_SIZE + " até "
                    + MAX_X + "x" + MAX_Y + "x" + MAX_Z + ")");
        }
    }

    /** Caixa da nave no estaleiro: canto mínimo e tamanho. */
    public record Box(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        public int maxX() {
            return minX + sizeX - 1;
        }

        public int maxY() {
            return minY + sizeY - 1;
        }

        public int maxZ() {
            return minZ + sizeZ - 1;
        }

        public boolean contains(double x, double y, double z) {
            return x >= minX && x < minX + sizeX && y >= minY && y < minY + sizeY && z >= minZ && z < minZ + sizeZ;
        }

        /** Menor chunk X da caixa. */
        public int chunkMinX() {
            return minX >> 4;
        }

        public int chunkMaxX() {
            return maxX() >> 4;
        }

        public int chunkMinZ() {
            return minZ >> 4;
        }

        public int chunkMaxZ() {
            return maxZ() >> 4;
        }

        /** Colunas de chunk que a caixa toca. */
        public int chunkColumns() {
            return (chunkMaxX() - chunkMinX() + 1) * (chunkMaxZ() - chunkMinZ() + 1);
        }
    }
}
