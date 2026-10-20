package com.spacehorizons.client.ship;

import com.spacehorizons.client.space.SkyAttitude;
import com.spacehorizons.client.space.SkyObserver;
import com.spacehorizons.planet.Planets;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;

/**
 * O céu visto de dentro de uma nave no estaleiro (spike S2): as coordenadas do estaleiro não têm relação com o espaço, então o
 * observador é VIRTUAL, não sai da posição da câmera. Por enquanto (sem voo) é uma posição fixa de teste perto da Terra, sobre o
 * spawn dela, com a atitude identidade; o modo de teste {@code /spacehorizons skytest} do spike S1 também vale aqui (atitude
 * falsa). Quando a nave voar, o estado virtual dela (posição e atitude sincronizadas) entra no lugar da posição fixa.
 */
public final class ShipSkyClient {
    /** Altitude da posição virtual de teste sobre o spawn da Terra, em blocos. */
    public static final double TEST_ALTITUDE = 30_000.0;

    private ShipSkyClient() {
    }

    /** A posição virtual de teste, no espaço do SolarSystem em E: sobre o spawn da Terra (eixo +X = "cima" do spawn). */
    public static double[] testPosition() {
        return new double[] {Planets.EARTH.radius() + TEST_ALTITUDE, 0.0, 0.0};
    }

    /** O observador do céu dentro do estaleiro, no relógio {@code clock}: posição virtual de teste e a atitude do modo skytest (ou identidade). */
    public static SkyObserver observer(double clock) {
        Quaterniondc attitude = SkyAttitude.current();
        return SkyObserver.forShip(clock, testPosition(), attitude != null ? attitude : new Quaterniond());
    }
}
