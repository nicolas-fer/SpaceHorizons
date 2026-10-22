package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.PortraitGrid;
import com.spacehorizons.rocket.RocketAssembly;
import com.spacehorizons.rocket.RocketBlockKind;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.ship.deck.DeckFrame;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** A matemática da âncora no ar (spike S6): a pose da grade bate com o retrato girado, a caixa e a montagem sem plataforma. */
class ShipAnchorsTest {
    private static final double EPS = 1.0E-4;

    /**
     * Com a nave acomodada na grade (centro e atitude de {@link ShipAnchors}), o centro de cada célula do retrato SEM giro, levado ao mundo pela pose,
     * cai exatamente no centro da célula do retrato GIRADO colocado com o canto em {@code origin}: é o que garante que quem anda no convés fica no mesmo
     * ponto em cima dos blocos.
     */
    @Test
    void aPoseDaGradeLevaCadaCelulaAoBlocoGirado() {
        int sx = 5;
        int sy = 3;
        int sz = 8;
        BlockPos origin = new BlockPos(100, 64, -40);
        for (int turns = 0; turns < 4; turns++) {
            int rsx = PortraitGrid.rotatedSizeX(turns, sx, sz);
            int rsz = PortraitGrid.rotatedSizeZ(turns, sx, sz);
            Vec3 c = ShipAnchors.snappedCenter(origin, rsx, sy, rsz);
            DeckFrame frame = new DeckFrame(c.x, c.y, c.z, ShipAnchors.snappedAttitude(turns), new Vector3f(sx / 2.0F, sy / 2.0F, sz / 2.0F));
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sz; z++) {
                    int y = 1;
                    Vector3d w = frame.toWorld(x + 0.5, y + 0.5, z + 0.5);
                    assertEquals(origin.getX() + PortraitGrid.rotatedX(turns, x, z, sx, sz) + 0.5, w.x, EPS, "x, " + turns + " quarto(s)");
                    assertEquals(origin.getY() + y + 0.5, w.y, EPS);
                    assertEquals(origin.getZ() + PortraitGrid.rotatedZ(turns, x, z, sx, sz) + 0.5, w.z, EPS, "z, " + turns + " quarto(s)");
                }
            }
        }
    }

    @Test
    void aCaixaDaAncoraComFolga() {
        ShipAnchors.Anchor a = new ShipAnchors.Anchor(1, Identifier.withDefaultNamespace("overworld"), new BlockPos(10, 100, 20), 4, 5, 6, new BlockPos(12, 101, 22),
                0, 0L);
        assertTrue(a.contains(10, 100, 20, 0));
        assertTrue(a.contains(13, 104, 25, 0));
        assertFalse(a.contains(14, 104, 25, 0));
        assertFalse(a.contains(13, 105, 25, 0), "em cima do teto fica fora da caixa");
        assertTrue(a.contains(13, 105, 25, 1), "mas dentro da folga de 1 (o pé de um mob no teto)");
        assertFalse(a.contains(9, 99, 19, 0));
    }

    /** A nave no ar não toca plataforma: a montagem normal recusa (NO_PAD), a de uma nave ancorada aceita. */
    @Test
    void naveNoArMontaSemPlataformaSoComAsRegrasDaAncora() throws Exception {
        Map<String, RocketBlockKind> blocks = new HashMap<>();
        blocks.put("0,100,0", RocketBlockKind.COCKPIT);
        blocks.put("1,100,0", RocketBlockKind.STRUCTURE);
        blocks.put("1,99,0", RocketBlockKind.ENGINE);
        RocketAssembly.Grid grid = (x, y, z) -> blocks.getOrDefault(x + "," + y + "," + z, RocketBlockKind.AIR);
        assertEquals(RocketException.Reason.NO_PAD, assertThrows(RocketException.class, () -> ShipAssembly.scan(grid, 0, 100, 0)).reason());
        RocketAssembly.Result r = ShipAssembly.scanAirborne(grid, 0, 100, 0);
        assertEquals(3, r.cells().size());
        assertEquals(0, r.padContacts());
        assertTrue(new RocketAssembly.Rules(true, true).needPad(), "as regras antigas continuam exigindo a plataforma");
    }
}
