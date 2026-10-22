package com.spacehorizons.ship.deck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A colisão do tripulante no referencial local: caixa contra caixa, sem rotação, e a prova de que a pose da nave (posição e atitude, a qualquer
 * velocidade) não muda o resultado local, só a posição derivada no mundo.
 */
class DeckCollisionTest {
    private static final double EPS = 1.0E-6;
    private static final double W = 0.6;
    private static final double H = 1.8;

    private static DeckCollision.Colliders boxes(AABB... all) {
        List<AABB> list = List.of(all);
        return (region, out) -> {
            for (AABB b : list) {
                if (b.intersects(region)) {
                    out.accept(b);
                }
            }
        };
    }

    private static AABB player(double x, double y, double z) {
        return new AABB(x - W / 2, y, z - W / 2, x + W / 2, y + H, z + W / 2);
    }

    /** Piso de 20 x 20 com o topo em y = 1. */
    private static final AABB FLOOR = new AABB(0, 0, 0, 20, 1, 20);

    @Test
    void caiESentaNoPiso() {
        var result = DeckCollision.move(player(10, 1.5, 10), new Vec3(0, -1, 0), boxes(FLOOR), 0.6, false);
        assertEquals(-0.5, result.moved().y, EPS);
        assertTrue(result.collidedY());
        assertTrue(result.onGround());
    }

    @Test
    void paradoNoPisoNaoAfundaNemFlutuaEm10MilTicks() {
        // a gravidade do vanilla (0,0784 por tick) empurra para baixo todo tick; o pé tem de ficar exatamente no topo do piso
        double y = 1.0;
        boolean onGround = true;
        for (int i = 0; i < 10_000; i++) {
            var r = DeckCollision.move(player(10, y, 10), new Vec3(0, -0.0784, 0), boxes(FLOOR), 0.6, onGround);
            y += r.moved().y;
            onGround = r.onGround();
            assertTrue(onGround, "tick " + i);
            assertEquals(1.0, y, EPS, "tick " + i);
        }
    }

    @Test
    void paredeParaOAndarEDeslizaNaOutraDirecao() {
        AABB wall = new AABB(5, 1, 0, 6, 4, 20);
        double x = 4.0;
        double z = 10.0;
        for (int i = 0; i < 50; i++) {
            var r = DeckCollision.move(player(x, 1.0, z), new Vec3(0.2, 0, 0.1), boxes(FLOOR, wall), 0.0, true);
            x += r.moved().x;
            z += r.moved().z;
        }
        assertEquals(5.0 - W / 2, x, EPS, "encostou na parede");
        assertEquals(10.0 + 50 * 0.1, z, 1.0E-9, "e deslizou ao longo dela sem perder o movimento paralelo");
    }

    @Test
    void degrauDeMeioBlocoSobeEDeUmBlocoNao() {
        AABB half = new AABB(6, 1, 0, 7, 1.5, 20);
        var up = DeckCollision.move(player(5.5, 1.0, 10), new Vec3(1.0, -0.0784, 0), boxes(FLOOR, half), 0.6, true);
        assertEquals(1.0, up.moved().x, EPS, "subiu o degrau de 0,5 andando");
        assertEquals(0.5, up.moved().y, EPS, "subiu até o topo do degrau");
        assertTrue(up.onGround());

        AABB full = new AABB(6, 1, 0, 7, 2, 20);
        var blocked = DeckCollision.move(player(5.5, 1.0, 10), new Vec3(1.0, -0.0784, 0), boxes(FLOOR, full), 0.6, true);
        assertTrue(blocked.moved().x < 0.21, "o bloco inteiro barra: andou só " + blocked.moved().x);
        assertTrue(blocked.collidedX());
    }

    @Test
    void tetoParaOPulo() {
        AABB ceiling = new AABB(0, 3, 0, 20, 4, 20);
        var r = DeckCollision.move(player(10, 1.0, 10), new Vec3(0, 0.6, 0), boxes(FLOOR, ceiling), 0.6, true);
        assertEquals(3.0 - H - 1.0, r.moved().y, EPS);
        assertTrue(r.collidedY());
        assertFalse(r.onGround());
    }

    @Test
    void penetracaoMedeOMaisFundo() {
        assertEquals(0.0, DeckCollision.penetration(player(10, 1.0, 10), boxes(FLOOR)), EPS, "encostar não é penetrar");
        assertEquals(0.3, DeckCollision.penetration(player(10, 0.7, 10), boxes(FLOOR)), EPS);
    }

    @Test
    void conferenciaDoServidor() {
        var colliders = boxes(FLOOR);
        DeckState ok = new DeckState();
        ok.pos.set(10.2, 1.0, 10);
        assertEquals(DeckMoveCheck.Verdict.OK, DeckMoveCheck.check(new Vector3d(10, 1, 10), ok, 1, W, H, colliders));

        DeckState fast = new DeckState();
        fast.pos.set(18, 1.0, 10);
        assertEquals(DeckMoveCheck.Verdict.TOO_FAST, DeckMoveCheck.check(new Vector3d(10, 1, 10), fast, 1, W, H, colliders));
        assertEquals(DeckMoveCheck.Verdict.OK, DeckMoveCheck.check(new Vector3d(10, 1, 10), fast, 3, W, H, colliders), "três ticks de atraso da rede dão folga");

        DeckState inside = new DeckState();
        inside.pos.set(10, 0.5, 10);
        assertEquals(DeckMoveCheck.Verdict.IN_SOLID, DeckMoveCheck.check(new Vector3d(10, 1, 10), inside, 1, W, H, colliders));

        DeckState nan = new DeckState();
        nan.pos.set(Double.NaN, 1.0, 10);
        assertEquals(DeckMoveCheck.Verdict.NOT_FINITE, DeckMoveCheck.check(new Vector3d(10, 1, 10), nan, 1, W, H, colliders));
    }

    // ------------------------------------------------------------------------------------------------------------------ colisão girada

    @Test
    void naveGiradaOMundoVeAParedeGiradaMasOResultadoLocalEOMesmo() {
        // a nave: atitude qualquer, centro a 3.000 blocos de altura, pivô no meio de um retrato 20 x 30 x 20; a parede é o plano local x = 5
        Quaternionf attitude = new Quaternionf().rotateY(0.9F).rotateX(-0.5F).rotateZ(0.35F);
        Vector3f pivot = new Vector3f(10, 15, 10);
        DeckFrame frame = new DeckFrame(120.0, 3000.0, -40.0, attitude, pivot);
        AABB wall = new AABB(5, 1, 0, 6, 4, 20);

        // o jogador quer andar para a parede; no mundo o movimento desejado é a direção local +x girada pela atitude
        Vector3f dirWorld = new Vector3f(1, 0, 0);
        attitude.transform(dirWorld);
        Vector3f dirLocal = new Vector3f(dirWorld);
        attitude.invert(new Quaternionf()).transform(dirLocal);
        assertEquals(1.0, dirLocal.x, 1.0E-6);

        double x = 2.0;
        for (int i = 0; i < 30; i++) {
            var r = DeckCollision.move(player(x, 1.0, 10), new Vec3(dirLocal.x * 0.2, 0, dirLocal.z * 0.2), boxes(FLOOR, wall), 0.0, true);
            x += r.moved().x;
        }
        assertEquals(5.0 - W / 2, x, EPS);

        // no mundo, o pé está a W/2 do plano da parede medido ao longo da normal girada
        Vector3d feet = frame.toWorld(x, 1.0, 10);
        Vector3d wallPoint = frame.toWorld(5.0, 1.0, 10);
        double along = (feet.x - wallPoint.x) * dirWorld.x + (feet.y - wallPoint.y) * dirWorld.y + (feet.z - wallPoint.z) * dirWorld.z;
        assertEquals(-W / 2, along, 2.0E-5);
    }

    @Test
    void aVelocidadeDaNaveNaoMudaOQueOTripulanteAnda() {
        // o mesmo passeio local com a nave parada e com a nave subindo a 100 b/t girando 20 graus/s: as posições LOCAIS são idênticas, e
        // vistas pela pose do instante voltam exatamente ao local (a velocidade da nave só entra na transformação)
        List<Vector3d> still = walk(0.0);
        List<Vector3d> fast = walk(100.0);
        assertEquals(still.size(), fast.size());
        for (int i = 0; i < still.size(); i++) {
            assertEquals(still.get(i).x, fast.get(i).x, 1.0E-9);
            assertEquals(still.get(i).y, fast.get(i).y, 1.0E-9);
            assertEquals(still.get(i).z, fast.get(i).z, 1.0E-9);
        }
    }

    /** Anda em quadrado por 800 ticks; a nave sobe a {@code shipSpeed} b/t e gira 1 grau por tick; devolve as posições LOCAIS recuperadas do mundo. */
    private static List<Vector3d> walk(double shipSpeed) {
        List<Vector3d> out = new ArrayList<>();
        Vector3f pivot = new Vector3f(10, 15, 10);
        double x = 3.0;
        double z = 3.0;
        double y = 1.0;
        boolean onGround = true;
        double heading = 0.0;
        for (int t = 0; t < 800; t++) {
            heading = (t / 100) * (Math.PI / 2.0);
            var r = DeckCollision.move(player(x, y, z), new Vec3(Math.cos(heading) * 0.2, -0.0784, Math.sin(heading) * 0.2), boxes(FLOOR), 0.6, onGround);
            x += r.moved().x;
            y += r.moved().y;
            z += r.moved().z;
            onGround = r.onGround();
            Quaternionf attitude = new Quaternionf().rotateX((float) Math.toRadians(30.0)).rotateY((float) Math.toRadians(t));
            DeckFrame frame = new DeckFrame(0.0, 100.0 + shipSpeed * t, 0.0, attitude, pivot);
            Vector3d world = frame.toWorld(x, y, z);
            Vector3d back = frame.toLocal(world.x, world.y, world.z);
            out.add(new Vector3d(Math.round(back.x * 1.0E5) / 1.0E5, Math.round(back.y * 1.0E5) / 1.0E5, Math.round(back.z * 1.0E5) / 1.0E5));
            assertEquals(x, back.x, 1.0E-3);
            assertEquals(y, back.y, 1.0E-3);
            assertEquals(z, back.z, 1.0E-3);
        }
        return out;
    }

    @Test
    void interpolarOMundoEquivaleAInterpolarAPoseDentroDoTick() {
        // o jogador é desenhado por interpolação linear das posições do mundo entre dois ticks; a nave é desenhada por interpolação da pose
        // (posição linear, atitude por slerp). A diferença é a flecha do arco: para 1 grau por tick e 15 blocos de braço, abaixo de 2 mm
        Vector3f pivot = new Vector3f(13, 14, 13);
        DeckFrame a = new DeckFrame(0, 1000, 0, new Quaternionf().rotateX(0.5F).rotateY(0.2F), pivot);
        DeckFrame b = new DeckFrame(0, 1100, 0, new Quaternionf().rotateX(0.5F).rotateY(0.2F + (float) Math.toRadians(1.0)), pivot);
        Vector3d la = new Vector3d(25, 3, 25);
        Vector3d lb = new Vector3d(25.2, 3, 25);
        Vector3d wa = a.toWorld(la.x, la.y, la.z);
        Vector3d wb = b.toWorld(lb.x, lb.y, lb.z);
        for (double t = 0.0; t <= 1.0; t += 0.125) {
            Vector3d lerpedWorld = new Vector3d(wa).lerp(wb, t);
            DeckFrame f = DeckFrame.lerp(a, b, t);
            Vector3d exact = f.toWorld(la.x + (lb.x - la.x) * t, la.y, la.z);
            assertEquals(exact.x, lerpedWorld.x, 2.0E-3);
            assertEquals(exact.y, lerpedWorld.y, 2.0E-3);
            assertEquals(exact.z, lerpedWorld.z, 2.0E-3);
        }
    }
}
