package com.spacehorizons.client.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Conversão quaternion → yaw/pitch/roll da câmera do vanilla (ida e volta) e as bases do cockpit e da terceira pessoa. */
class CockpitCameraTest {
    private static final float EPS = 1.0E-4F;

    /** Dois quaternions são a mesma rotação se |dot| = 1 (q e -q são a mesma rotação). */
    private static void assertSameRotation(Quaternionf expected, Quaternionf actual, String what) {
        float dot = Math.abs(expected.dot(actual));
        assertEquals(1.0F, dot, 1.0E-4F, what + ": " + expected + " contra " + actual);
    }

    private static Quaternionf randomRotation(Random random) {
        return new Quaternionf(random.nextGaussian(), random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
    }

    @Test
    void idaEVoltaDeAngulosDaCameraDoVanilla() {
        // fora de pitch ±90 (degenerado), os três ângulos voltam iguais
        for (float yaw : new float[] {0F, 37F, -120F, 179F, -179F, 90F}) {
            for (float pitch : new float[] {0F, 25F, -60F, 89F, -89F}) {
                for (float roll : new float[] {0F, 30F, -75F, 170F}) {
                    Quaternionf original = CockpitCamera.fromVanilla(yaw, pitch, roll);
                    float[] back = CockpitCamera.toVanilla(original);
                    assertSameRotation(original, CockpitCamera.fromVanilla(back[0], back[1], back[2]), "yaw=" + yaw + " pitch=" + pitch + " roll=" + roll);
                    if (Math.abs(pitch) < 80F) {
                        assertEquals(wrap(yaw), wrap(back[0]), 0.01F, "yaw");
                        assertEquals(pitch, back[1], 0.01F, "pitch");
                        assertEquals(wrap(roll), wrap(back[2]), 0.01F, "roll");
                    }
                }
            }
        }
    }

    /** Pitch exatamente ±90° (o foguete em pé olhando ao longo do nariz): yaw e roll se confundem, e a rotação ainda precisa voltar exata. */
    @Test
    void gimbalExatoNaoPerdeARotacao() {
        for (float pitchSign : new float[] {1F, -1F}) {
            for (float yaw : new float[] {0F, 33F, -120F, 180F}) {
                for (float roll : new float[] {0F, 45F, -170F}) {
                    Quaternionf original = CockpitCamera.fromVanilla(yaw, 90F * pitchSign, roll);
                    float[] a = CockpitCamera.toVanilla(original);
                    assertSameRotation(original, CockpitCamera.fromVanilla(a[0], a[1], a[2]), "gimbal pitch=" + 90F * pitchSign + " yaw=" + yaw + " roll=" + roll);
                    assertEquals(90F * pitchSign, a[1], 0.01F);
                }
            }
        }
        // o caso real: foguete em pé, câmera ao longo do nariz, qualquer olhar livre só de yaw; e a mesma coisa rolada
        for (float roll : new float[] {0F, 45F, 90F, -135F}) {
            Quaternionf attitude = new Quaternionf().rotationY((float) Math.toRadians(roll));
            Quaternionf cam = CockpitCamera.cameraRotation(attitude, false, 0F, 0F);
            float[] a = CockpitCamera.toVanilla(cam);
            assertSameRotation(cam, CockpitCamera.fromVanilla(a[0], a[1], a[2]), "em pé rolado " + roll);
            Vector3f up = CockpitCamera.fromVanilla(a[0], a[1], a[2]).transform(new Vector3f(0, 1, 0));
            Vector3f expectedUp = attitude.transform(new Vector3f(0, 0, -1));
            assertEquals(1F, up.dot(expectedUp), EPS, "o cima da tela acompanha a rolagem (" + roll + ")");
        }
    }

    @Test
    void qualquerRotacaoVoltaExataPeloVanilla() {
        Random random = new Random(42);
        for (int i = 0; i < 2000; i++) {
            Quaternionf q = randomRotation(random);
            float[] a = CockpitCamera.toVanilla(q);
            assertSameRotation(q, CockpitCamera.fromVanilla(a[0], a[1], a[2]), "aleatório " + i);
        }
    }

    /** O que o vanilla faz na câmera espelhada (F5 de frente): setRotation(yaw + 180, -pitch, -roll) sobre o que o evento deu. */
    private static Quaternionf vanillaMirrored(float yaw, float pitch, float roll) {
        return CockpitCamera.fromVanilla(yaw + 180F, -pitch, -roll);
    }

    @Test
    void cameraEspelhadaVeOFoguetePorTras() {
        Random random = new Random(7);
        for (int i = 0; i < 500; i++) {
            Quaternionf q = randomRotation(random);
            float[] a = CockpitCamera.toVanillaMirrored(q);
            Quaternionf result = vanillaMirrored(a[0], a[1], a[2]);
            assertSameRotation(new Quaternionf(q).rotateY((float) Math.PI), result, "espelhada " + i);
            // a frente espelhada é a oposta da frente normal
            Vector3f f1 = q.transform(new Vector3f(0, 0, -1));
            Vector3f f2 = result.transform(new Vector3f(0, 0, -1));
            assertEquals(-1.0F, f1.dot(f2), EPS);
        }
    }

    @Test
    void primeiraPessoaOlhaAoLongoDoNarizComCimaEmMenosZ() {
        Quaternionf cam = CockpitCamera.cameraRotation(new Quaternionf(), false, 0F, 0F);
        Vector3f forward = cam.transform(new Vector3f(0, 0, -1));
        Vector3f up = cam.transform(new Vector3f(0, 1, 0));
        Vector3f left = cam.transform(new Vector3f(-1, 0, 0));
        assertEquals(0F, forward.x, EPS);
        assertEquals(1F, forward.y, EPS);
        assertEquals(0F, forward.z, EPS);
        assertEquals(-1F, up.z, EPS, "o cima da tela é a frente do cockpit (-Z)");
        assertEquals(1F, left.x, EPS, "a esquerda da tela é +X, então a direita é -X");
    }

    @Test
    void camerasAcompanhamQualquerAtitude() {
        Random random = new Random(3);
        for (int i = 0; i < 300; i++) {
            Quaternionf attitude = randomRotation(random);
            Quaternionf cam = CockpitCamera.cameraRotation(attitude, false, 0F, 0F);
            Vector3f nose = attitude.transform(new Vector3f(0, 1, 0));
            assertEquals(1F, cam.transform(new Vector3f(0, 0, -1)).dot(nose), EPS, "a câmera olha ao longo do nariz");
            // e o que o vanilla reconstrói a partir dos ângulos do evento é a mesma câmera
            float[] a = CockpitCamera.toVanilla(cam);
            assertSameRotation(cam, CockpitCamera.fromVanilla(a[0], a[1], a[2]), "ida e volta pelo evento " + i);
        }
    }

    @Test
    void olharLivreGiraAEmTornoDoCimaEDaEsquerdaDaTela() {
        // yaw do jogador 90 (olhar para a direita, no sentido do Minecraft): a direita da tela é -X do corpo
        Quaternionf cam = CockpitCamera.cameraRotation(new Quaternionf(), false, 90F, 0F);
        Vector3f forward = cam.transform(new Vector3f(0, 0, -1));
        assertEquals(-1F, forward.x, EPS);
        assertEquals(0F, forward.y, EPS);
        // pitch 90 (olhar para baixo): aponta para o oposto do cima da tela (-(-Z) = +Z)
        Quaternionf down = CockpitCamera.cameraRotation(new Quaternionf(), false, 0F, 90F);
        assertEquals(1F, down.transform(new Vector3f(0, 0, -1)).z, EPS);
    }

    @Test
    void sentidosDasTeclasCombinamComAVisaoDoPiloto() {
        Quaternionf toCamera = CockpitCamera.cameraRotation(new Quaternionf(), false, 0F, 0F).invert();
        // W (arfagem +): gira em torno de +X e leva o nariz para +Z, o BAIXO da tela do cockpit
        Vector3f noseW = new Quaternionf().rotateX(0.1F).transform(new Vector3f(0, 1, 0));
        assertTrue(noseW.z > 0F);
        assertTrue(toCamera.transform(new Vector3f(noseW)).y < 0F, "W leva o nariz para baixo da tela");
        // D (guinada +): leva o nariz para -X, a direita da tela
        Vector3f noseD = new Quaternionf().rotateZ(0.1F).transform(new Vector3f(0, 1, 0));
        assertTrue(toCamera.transform(new Vector3f(noseD)).x > 0F, "D leva o nariz para a direita da tela");
        // E (rolagem +): o cima do foguete (-Z do corpo) tomba para a direita da tela, no sentido horário visto pelo piloto
        Vector3f upE = new Quaternionf().rotateY(0.1F).transform(new Vector3f(0, 0, -1));
        assertTrue(toCamera.transform(new Vector3f(upE)).x > 0F, "E gira o foguete no sentido horário");
    }

    @Test
    void terceiraPessoaVeOFoguetePelaFrenteDoCockpit() {
        Quaternionf cam = CockpitCamera.cameraRotation(new Quaternionf(), true, 0F, 0F);
        Vector3f forward = cam.transform(new Vector3f(0, 0, -1));
        Vector3f up = cam.transform(new Vector3f(0, 1, 0));
        assertTrue(forward.z > 0.9F, "olha para +Z do retrato");
        assertTrue(forward.y < 0F, "inclinada para baixo");
        assertTrue(up.y > 0.9F, "o nariz fica para cima da tela");
        // a câmera fica em (assento - frente × distância): na frente do cockpit (-Z) e acima
        assertTrue(-forward.z < 0F && -forward.y > 0F);
        // com a câmera de frente, W (nariz para +Z) afasta o nariz do observador, e D leva o nariz para a direita da tela
        Quaternionf toCamera = new Quaternionf(cam).invert();
        assertTrue(toCamera.transform(new Vector3f(new Quaternionf().rotateZ(0.1F).transform(new Vector3f(0, 1, 0)))).x > 0F);
    }

    @Test
    void distanciaDaTerceiraPessoaCresceComOTamanho() {
        assertEquals(CockpitCamera.MIN_DISTANCE, CockpitCamera.thirdPersonDistance(1, 1, 1));
        assertTrue(CockpitCamera.thirdPersonDistance(3, 20, 3) > CockpitCamera.thirdPersonDistance(3, 8, 3));
        assertEquals(CockpitCamera.MAX_DISTANCE, CockpitCamera.thirdPersonDistance(64, 64, 64));
        assertEquals(2.0F + 1.5F * 10, CockpitCamera.thirdPersonDistance(3, 10, 3), 1.0E-5F);
    }

    @Test
    void olhoDaPrimeiraPessoaFicaNaJanelaForaDoCasco() {
        // janela para o norte (-Z): o olho fica à frente do plano da janela (z < -0.5), na altura do meio dela, no centro em x
        Vector3f north = CockpitCamera.windowEye(0, -1);
        assertEquals(0.0F, north.x, EPS);
        assertEquals(6.5F / 16.0F - 0.5F, north.y, EPS);
        assertTrue(north.z < -0.5F && north.z > -0.8F, "o olho fica logo à frente da janela: " + north.z);
        // virar a janela para o leste leva o recuo para +X
        Vector3f east = CockpitCamera.windowEye(1, 0);
        assertEquals(-north.z, east.x, EPS);
        assertEquals(0.0F, east.z, EPS);
        assertEquals(north.y, east.y, EPS);
    }

    private static float wrap(float degrees) {
        float d = degrees % 360F;
        if (d > 180F) {
            d -= 360F;
        } else if (d <= -180F) {
            d += 360F;
        }
        return d;
    }
}
