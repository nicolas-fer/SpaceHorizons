package com.spacehorizons.ship.deck;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A correspondência entre o estaleiro e o convés (fase 2e, frente 2): o retrato de uma nave vai ao estaleiro sem rotação, com o canto mínimo da
 * caixa em {@code box.min}, então a posição relativa à nave é a mesma nos dois lugares: a posição local do convés mais o canto da caixa dá a do
 * estaleiro e vice-versa. No pouso, o retrato volta ao chão girado em quartos de volta ({@code PortraitGrid.rotated}), e a posição do tripulante
 * gira junto. Lógica pura, testada sem o jogo.
 */
public final class DeckYardMap {
    private DeckYardMap() {
    }

    /** Posição local (convés) → posição no estaleiro, para a caixa com o canto mínimo em ({@code minX}, {@code minY}, {@code minZ}). */
    public static Vector3d toYard(Vector3dc local, double minX, double minY, double minZ) {
        return new Vector3d(minX + local.x(), minY + local.y(), minZ + local.z());
    }

    /** Posição no estaleiro → posição local (convés). */
    public static Vector3d toLocal(double x, double y, double z, double minX, double minY, double minZ) {
        return new Vector3d(x - minX, y - minY, z - minZ);
    }

    /**
     * A posição local depois de {@code quarterTurns} quartos de volta horários da caixa {@code sizeX x sizeZ} (a mesma regra de
     * {@code PortraitGrid.rotatedX/rotatedZ}, para um ponto contínuo: o bloco {@code [i, i+1]} vai a {@code [n-1-i, n-i]}); a altura não muda.
     */
    public static Vector3d rotate(Vector3dc local, int quarterTurns, int sizeX, int sizeZ) {
        double x = local.x();
        double z = local.z();
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new Vector3d(sizeZ - z, local.y(), x);
            case 2 -> new Vector3d(sizeX - x, local.y(), sizeZ - z);
            case 3 -> new Vector3d(z, local.y(), sizeX - x);
            default -> new Vector3d(x, local.y(), z);
        };
    }

    /** A posição no mundo de quem estava em {@code local} quando o retrato voltou ao chão com o canto mínimo em {@code origin}. */
    public static Vector3d landed(Vector3dc local, int quarterTurns, int sizeX, int sizeZ, double originX, double originY, double originZ) {
        Vector3d r = rotate(local, quarterTurns, sizeX, sizeZ);
        return new Vector3d(originX + r.x, originY + r.y, originZ + r.z);
    }
}
