package com.spacehorizons.client.rocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** O vigia do acelerador: só acusa depois de ticks seguidos de divergência. */
class ThrottleWatchTest {
    @Test
    void stayQuietWhileTheServerFollows() {
        ThrottleWatch watch = new ThrottleWatch();
        for (int i = 0; i < 100; i++) {
            watch.tick(0.36F, 0.35F);
        }
        assertFalse(watch.notAccepted());
    }

    @Test
    void accuseAfterTheGraceOfDivergingTicks() {
        ThrottleWatch watch = new ThrottleWatch();
        for (int i = 0; i < ThrottleWatch.GRACE_TICKS - 1; i++) {
            watch.tick(0.36F, 0.0F);
        }
        assertFalse(watch.notAccepted(), "ainda dentro da tolerância do pacote e da resposta");
        watch.tick(0.36F, 0.0F);
        assertTrue(watch.notAccepted());
    }

    @Test
    void aSingleAgreeingTickClearsTheAccusation() {
        ThrottleWatch watch = new ThrottleWatch();
        for (int i = 0; i < 50; i++) {
            watch.tick(0.36F, 0.0F);
        }
        assertTrue(watch.notAccepted());
        watch.tick(0.36F, 0.36F);
        assertFalse(watch.notAccepted());
    }

    @Test
    void resetClears() {
        ThrottleWatch watch = new ThrottleWatch();
        for (int i = 0; i < 50; i++) {
            watch.tick(1.0F, 0.0F);
        }
        watch.reset();
        assertFalse(watch.notAccepted());
    }
}
