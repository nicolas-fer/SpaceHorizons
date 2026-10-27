package com.spacehorizons.ship.deck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/** A mistura das trocas sem costura ({@link PoseBlend}) e a medida do salto ({@link SeamJump}). */
class SeamTest {
    @Test
    void misturaComecaNaPoseAntigaEAcabaNaNova() {
        PoseBlend b = new PoseBlend();
        Vector3d old = new Vector3d(10, 5, 0);
        Vector3d now = new Vector3d(12, 5, 1);
        b.beginPosition(100.0, 20.0, old, now);
        Vector3d out = new Vector3d();
        assertEquals(0.0, b.position(100.0, now, out).distance(old), 1e-12, "no instante da troca o desenho continua onde estava");
        assertEquals(0.0, b.position(120.0, now, out).distance(now), 1e-12, "ao fim vale só a fonte nova");
        assertEquals(0.0, b.position(500.0, now, out).distance(now), 1e-12);
        assertTrue(b.positionActive(110.0));
        assertFalse(b.positionActive(120.0));
    }

    @Test
    void misturaAntesDaTrocaMantemADiferencaInteira() {
        PoseBlend b = new PoseBlend();
        b.beginPosition(100.0, 20.0, new Vector3d(0, 0, 0), new Vector3d(3, 0, 0));
        Vector3d out = new Vector3d();
        assertEquals(0.0, b.position(99.0, new Vector3d(3, 0, 0), out).x, 1e-12);
    }

    @Test
    void misturaTemVelocidadeZeroNasPontasESemPassosGrandes() {
        PoseBlend b = new PoseBlend();
        b.beginPosition(0.0, 20.0, new Vector3d(8, 0, 0), new Vector3d(0, 0, 0));
        Vector3d out = new Vector3d();
        double previous = b.position(0.0, new Vector3d(), out).x;
        double maxStep = 0.0;
        double firstStep = Double.NaN;
        for (int i = 1; i <= 160; i++) {
            double x = b.position(i * 20.0 / 160.0, new Vector3d(), out).x;
            maxStep = Math.max(maxStep, previous - x);
            if (i == 1) {
                firstStep = previous - x;
            }
            previous = x;
        }
        assertTrue(firstStep < 0.001, "arranca sem tranco: " + firstStep);
        // 8 blocos em 160 quadros: no máximo 1,5x a média (0,05) no meio da curva
        assertTrue(maxStep < 0.08, "passo máximo " + maxStep);
        assertEquals(0.0, out.x, 1e-9);
    }

    @Test
    void misturaDaRotacaoVoltaPelaFonteNova() {
        PoseBlend b = new PoseBlend();
        Quaterniond old = new Quaterniond().rotateY(Math.toRadians(40.0)).rotateX(Math.toRadians(10.0));
        Quaterniond now = new Quaterniond().rotateY(Math.toRadians(35.0));
        b.beginRotation(10.0, 12.0, old, now);
        Quaterniond out = new Quaterniond();
        assertEquals(0.0, SeamJump.angleDegrees(b.rotation(10.0, now, out), old), 1e-6);
        assertEquals(0.0, SeamJump.angleDegrees(b.rotation(22.0, now, out), now), 1e-6);
        double mid = SeamJump.angleDegrees(b.rotation(16.0, now, out), now);
        assertTrue(mid > 0.5 && mid < SeamJump.angleDegrees(old, now), "no meio, entre as duas: " + mid);
    }

    @Test
    void saltoDescontaOMovimentoDaNave() {
        Vector3d a = new Vector3d(0, 0, 0);
        // a nave anda 4 blocos por tick; o quadro seguinte é 0,2 tick depois
        assertEquals(0.0, SeamJump.position(a, new Vector3d(0.8, 0, 0), new Vector3d(4, 0, 0), 0.2), 1e-12);
        // o jogador parado no mundo também não tem salto
        assertEquals(0.0, SeamJump.position(a, new Vector3d(0, 0, 0), new Vector3d(4, 0, 0), 0.2), 1e-12);
        // 8 blocos de pulo (2 ticks de nave a 4 b/t) a mais
        assertEquals(8.0, SeamJump.position(a, new Vector3d(8.8, 0, 0), new Vector3d(4, 0, 0), 0.2), 1e-9);
    }

    @Test
    void deslizeSuaveNaoESaltoMasTeleporteE() {
        Vector3d a = new Vector3d(0, 0, 0);
        Vector3d zero = new Vector3d();
        // a câmera desliza a 0,34 b/t e o quadro dura 0,4 tick: 0,136 bloco, mas na velocidade que já trazia
        assertEquals(0.0, SeamJump.position(a, new Vector3d(0.136, 0, 0), zero, new Vector3d(0.34, 0, 0), 0.4), 1e-12);
        // a mesma distância vindo do repouso é salto
        assertEquals(0.136, SeamJump.position(a, new Vector3d(0.136, 0, 0), zero, zero, 0.4), 1e-12);
        // um teleporte de 0,8 bloco não se explica pela velocidade de antes
        assertEquals(0.8 - 0.068, SeamJump.position(a, new Vector3d(0.8, 0, 0), zero, new Vector3d(0.34, 0, 0), 0.2), 1e-9);
    }

    @Test
    void giroSuaveNaoESaltoMasViradaE() {
        // a câmera gira 2,8 graus por tick em torno de X: 0,56 grau em 0,2 tick, depois 1,008 grau num quadro de 0,36 tick
        Quaterniond z = new Quaterniond().rotateX(Math.toRadians(33.0));
        Quaterniond a = new Quaterniond().rotateX(Math.toRadians(33.56));
        Quaterniond b = new Quaterniond().rotateX(Math.toRadians(34.568));
        assertEquals(0.0, SeamJump.angle(z, a, b, 0.0, 0.2, 0.36), 1e-6);
        // a mesma rotação vinda do repouso é salto
        assertEquals(1.008, SeamJump.angle(a, a, b, 0.0, 0.2, 0.36), 1e-6);
        // virar 30 graus de uma vez com o giro de antes ainda conta quase tudo
        Quaterniond flip = new Quaterniond().rotateX(Math.toRadians(33.56 + 30.0));
        assertTrue(SeamJump.angle(z, a, flip, 0.0, 0.2, 0.2) > 29.0);
    }

    @Test
    void saltoDeRotacaoEmGraus() {
        Quaterniond a = new Quaterniond().rotateY(Math.toRadians(10.0));
        Quaterniond b = new Quaterniond().rotateY(Math.toRadians(12.0));
        assertEquals(2.0, SeamJump.angleDegrees(a, b), 1e-9);
        assertEquals(0.0, SeamJump.angle(a, b, 1.0, 2.0), 1e-9);
        assertEquals(1.5, SeamJump.angle(a, b, 0.5, 1.0), 1e-9);
    }
}
