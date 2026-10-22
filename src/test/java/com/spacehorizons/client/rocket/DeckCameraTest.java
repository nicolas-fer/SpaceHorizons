package com.spacehorizons.client.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** A câmera do tripulante a pé (S5): atitude da nave composta com o olhar local. */
class DeckCameraTest {
    private static final float EPS = 1.0E-4F;

    /** O vetor de olhar do Minecraft (yaw e pitch em graus). */
    private static Vector3f look(float yaw, float pitch) {
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        return new Vector3f((float) (-Math.sin(yr) * Math.cos(pr)), (float) -Math.sin(pr), (float) (Math.cos(yr) * Math.cos(pr)));
    }

    @Test
    void naveParadaAMesmaCameraDoVanilla() {
        for (float yaw : new float[] {0F, 33F, -120F, 179F}) {
            for (float pitch : new float[] {0F, 25F, -60F}) {
                float[] angles = CockpitCamera.toVanilla(CockpitCamera.deckRotation(new Quaternionf(), yaw, pitch));
                assertEquals(Math.floorMod(Math.round(yaw), 360), Math.floorMod(Math.round(angles[0]), 360), 0.02F, "yaw");
                assertEquals(pitch, angles[1], 0.01F, "pitch");
                assertEquals(0F, angles[2], 0.01F, "roll");
            }
        }
    }

    @Test
    void aFrenteDaCameraEOOlharLocalGiradoPelaAtitude() {
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            Quaternionf attitude = new Quaternionf(random.nextGaussian(), random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            float yaw = random.nextFloat() * 360F - 180F;
            float pitch = random.nextFloat() * 160F - 80F;
            Quaternionf camera = CockpitCamera.deckRotation(attitude, yaw, pitch);
            // a câmera do vanilla olha para -Z dela; o olhar local, girado pela atitude, é a direção que ela tem de ter no mundo
            Vector3f forward = camera.transform(new Vector3f(0, 0, -1));
            Vector3f expected = attitude.transform(look(yaw, pitch));
            assertEquals(expected.x, forward.x, EPS);
            assertEquals(expected.y, forward.y, EPS);
            assertEquals(expected.z, forward.z, EPS);
            // e o "cima" da câmera é o "cima" local girado: o horizonte acompanha a inclinação da nave
            Vector3f up = camera.transform(new Vector3f(0, 1, 0));
            Vector3f localUp = CockpitCamera.fromVanilla(yaw, pitch, 0F).transform(new Vector3f(0, 1, 0));
            Vector3f expectedUp = attitude.transform(localUp);
            assertEquals(expectedUp.x, up.x, EPS);
            assertEquals(expectedUp.y, up.y, EPS);
            assertEquals(expectedUp.z, up.z, EPS);
        }
    }

    @Test
    void ostresAngulosRecompoemACameraComposta() {
        // o evento só aceita yaw, pitch e roll: voltar dos três à rotação composta tem de ser exato (nave inclinada 30 graus e girando)
        for (float spin : new float[] {0F, 40F, 135F, -77F}) {
            Quaternionf attitude = new Quaternionf().rotateX((float) Math.toRadians(30.0)).rotateY((float) Math.toRadians(spin));
            Quaternionf camera = CockpitCamera.deckRotation(attitude, 25F, 10F);
            float[] a = CockpitCamera.toVanilla(camera);
            Quaternionf back = CockpitCamera.fromVanilla(a[0], a[1], a[2]);
            assertEquals(1.0F, Math.abs(camera.dot(back)), 1.0E-4F);
        }
    }
}
