package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

/** O ponto fixo onde o foguete fica estacionado na dimensão de espaço. */
class SpaceParkingTest {
    @Test
    void theSameRocketAlwaysParksAtTheSamePoint() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertEquals(SpaceParking.pointFor(id), SpaceParking.pointFor(id));
    }

    @Test
    void thePointIsAtTheCentreOfAChunkOfTheGridInsideTheDimensionHeight() {
        Random random = new Random(7);
        for (int i = 0; i < 500; i++) {
            Vec3 p = SpaceParking.pointFor(new UUID(random.nextLong(), random.nextLong()));
            assertEquals(SpaceParking.Y, p.y);
            assertTrue(p.y >= 0.0 && p.y < 16.0, "dentro da altura da dimensão (16 blocos)");
            double gx = (p.x - SpaceParking.BASE - 8.0) / SpaceParking.SPACING;
            double gz = (p.z - SpaceParking.BASE - 8.0) / SpaceParking.SPACING;
            assertEquals(Math.rint(gx), gx, 1e-9);
            assertEquals(Math.rint(gz), gz, 1e-9);
            assertTrue(gx >= 0 && gx < SpaceParking.GRID && gz >= 0 && gz < SpaceParking.GRID);
        }
    }

    @Test
    void parkingIsFarFromEveryRealSpacePosition() {
        // as posições reais do espaço ficam a ~1 milhão de blocos da origem da dimensão; o estacionamento começa em 8 milhões
        Vec3 p = SpaceParking.pointFor(UUID.randomUUID());
        assertTrue(Math.min(p.x, p.z) >= 8_000_000.0 - 1.0);
        assertTrue(Math.max(p.x, p.z) < 29_999_984.0, "dentro da borda do mundo");
    }

    @Test
    void differentRocketsGetDifferentPoints() {
        Random random = new Random(11);
        Set<Vec3> points = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            points.add(SpaceParking.pointFor(new UUID(random.nextLong(), random.nextLong())));
        }
        assertTrue(points.size() >= 195, "quase todos distintos (200 foguetes em 65.536 células): " + points.size());
        assertNotEquals(SpaceParking.pointFor(new UUID(0, 1)), SpaceParking.pointFor(new UUID(0, 2)));
    }
}
