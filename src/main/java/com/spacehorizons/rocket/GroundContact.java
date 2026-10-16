package com.spacehorizons.rocket;

import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * Contas do contato com o chão (lógica pura): a caixa do foguete é um paralelepípedo orientado pela atitude LOCAL da
 * dimensão; o que importa para tocar o terreno é a meia-extensão da caixa alinhada aos eixos que o envolve, e o ponto mais
 * alto do terreno sob essa pegada. O terreno em si vem de uma {@link Ground} (o adaptador do mundo está em
 * {@link RocketFlightController}).
 */
public final class GroundContact {
    /** Meias-extensões da caixa alinhada aos eixos que envolve o foguete girado (x, y, z da dimensão). */
    public record Extents(double x, double y, double z) {
    }

    /**
     * O terreno sob o foguete: a altura da superfície sólida mais alta na pegada ({@code x ± halfX, z ± halfZ}) dentro da janela
     * vertical {@code [yMin, yMax]}, ou NaN se não há nenhuma nela (ou o chunk não está carregado). A janela cobre o que a base
     * pode ter atravessado neste tick; blocos colocados por jogadores contam (o mapa de alturas do vanilla não serve: ele não
     * acompanha blocos postos acima do terreno gerado neste mundo).
     */
    @FunctionalInterface
    public interface Ground {
        double top(double x, double z, double halfX, double halfZ, double yMin, double yMax);
    }

    private GroundContact() {
    }

    /** Meias-extensões da caixa de meias-dimensões ({@code hx, hy, hz}, eixos do retrato) depois de girada por {@code localAttitude}. */
    public static Extents extents(Quaterniondc localAttitude, double hx, double hy, double hz) {
        Vector3d cx = localAttitude.transform(1.0, 0.0, 0.0, new Vector3d());
        Vector3d cy = localAttitude.transform(0.0, 1.0, 0.0, new Vector3d());
        Vector3d cz = localAttitude.transform(0.0, 0.0, 1.0, new Vector3d());
        return new Extents(
                Math.abs(cx.x) * hx + Math.abs(cy.x) * hy + Math.abs(cz.x) * hz,
                Math.abs(cx.y) * hx + Math.abs(cy.y) * hy + Math.abs(cz.y) * hz,
                Math.abs(cx.z) * hx + Math.abs(cy.z) * hy + Math.abs(cz.z) * hz);
    }

    /**
     * A base da caixa ({@code centerY - halfY}) tocou o terreno de altura {@code top}? Subindo (velocidade vertical
     * positiva) só conta se a base já está bem abaixo da superfície (bateu de lado numa encosta): a decolagem não pode
     * "bater" no chão de onde acabou de sair. Terreno desconhecido (NaN) nunca toca.
     */
    public static boolean touching(double centerY, double halfY, double top, double verticalSpeed) {
        if (Double.isNaN(top)) {
            return false;
        }
        double bottom = centerY - halfY;
        return bottom <= top + 1.0e-6 && (verticalSpeed <= 0.0 || bottom < top - 1.0);
    }
}
