package com.spacehorizons.client.rocket;

import com.spacehorizons.rocket.RocketEntity;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Som de motor em laço, ligado ao foguete: volume e tom crescem com o acelerador e o som segue a posição do foguete.
 * Usa o som vanilla do voo de elytra (um ruído de vento contínuo, feito para tocar em laço), grave: com o tom lá embaixo
 * vira um ronco. Para sozinho quando o foguete some, o acelerador chega a zero ou o combustível acaba.
 */
final class RocketEngineSound extends AbstractTickableSoundInstance {
    private final RocketEntity rocket;

    RocketEngineSound(RocketEntity rocket) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.NEUTRAL, RandomSource.create());
        this.rocket = rocket;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.1F;
        this.pitch = 0.5F;
        this.x = rocket.getX();
        this.y = rocket.getY();
        this.z = rocket.getZ();
    }

    /** O motor está empurrando: acelerador acima do mínimo e combustível. */
    static boolean running(RocketEntity rocket) {
        return !rocket.isRemoved() && rocket.throttle() > 0.01F && rocket.syncedFuelMb() > 0.0F;
    }

    @Override
    public void tick() {
        if (!running(this.rocket)) {
            this.stop();
            return;
        }
        float t = this.rocket.throttle();
        this.volume = 0.25F + 1.35F * t;
        this.pitch = 0.5F + 0.4F * t;
        this.x = this.rocket.getX();
        this.y = this.rocket.getY();
        this.z = this.rocket.getZ();
    }
}
