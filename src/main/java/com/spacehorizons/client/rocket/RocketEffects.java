package com.spacehorizons.client.rocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketPortrait;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Efeitos dos foguetes visíveis (de qualquer jogador, pelos dados sincronizados: acelerador, combustível, calor):
 * <ul>
 *   <li><b>chama</b> e fumaça em cada motor (bloco {@code rocket_engine} do retrato), na pose girada pela atitude, com
 *       quantidade e velocidade proporcionais ao acelerador;</li>
 *   <li><b>fumaça de decolagem</b>: se o jato bate no chão a menos de {@value #GROUND_SMOKE_REACH} blocos, uma nuvem se
 *       espalha rente ao chão, mais forte quanto mais perto;</li>
 *   <li><b>reentrada</b>: com {@code heat > 0,7} chamas e fumaça na face do foguete voltada para o vento relativo (o oposto
 *       da velocidade; sem velocidade, a base);</li>
 *   <li><b>som de motor</b> em laço ({@link RocketEngineSound}).</li>
 * </ul>
 * Partículas são do cliente e não têm custo no servidor; só rodam a menos de {@value #RANGE} blocos da câmera.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class RocketEffects {
    private static final double RANGE = 128.0;
    private static final double GROUND_SMOKE_REACH = 16.0;
    private static final float REENTRY_HEAT = 0.7F;

    /** Posições locais (bocal, centro da face de baixo) dos motores de cada retrato, calculadas uma vez. */
    private static final Map<UUID, List<Vector3f>> ENGINES = new HashMap<>();
    private static final Map<Integer, RocketEngineSound> SOUNDS = new HashMap<>();

    private RocketEffects() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            return;
        }
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof RocketEntity rocket) || rocket.portrait().isEmpty() || rocket.distanceToSqr(camera) > RANGE * RANGE) {
                continue;
            }
            tickSound(mc, rocket);
            if (RocketEngineSound.running(rocket)) {
                engineExhaust(level, rocket);
            }
            if (rocket.heat() > REENTRY_HEAT) {
                reentry(level, rocket);
            }
        }
        SOUNDS.values().removeIf(RocketEngineSound::isStopped);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SOUNDS.clear();
        ENGINES.clear();
    }

    // ---------------------------------------------------------------- som

    private static void tickSound(Minecraft mc, RocketEntity rocket) {
        RocketEngineSound current = SOUNDS.get(rocket.getId());
        if (current == null || current.isStopped()) {
            if (RocketEngineSound.running(rocket)) {
                RocketEngineSound sound = new RocketEngineSound(rocket);
                SOUNDS.put(rocket.getId(), sound);
                mc.getSoundManager().play(sound);
            }
        }
    }

    // ---------------------------------------------------------------- motores

    /** As posições locais (x+0,5; y; z+0,5) dos motores do retrato: o centro da face de baixo de cada bloco. */
    static List<Vector3f> enginesOf(RocketPortrait portrait) {
        List<Vector3f> cached = ENGINES.get(portrait.id());
        if (cached != null) {
            return cached;
        }
        if (ENGINES.size() > 16) {
            ENGINES.clear();
        }
        List<Vector3f> list = new ArrayList<>();
        // índices da paleta que são motor (a busca na grade compara inteiros, não estados)
        boolean[] isEngine = new boolean[portrait.palette().size()];
        boolean any = false;
        for (int i = 0; i < isEngine.length; i++) {
            BlockState state = portrait.palette().get(i);
            isEngine[i] = state.is(ModBlocks.ROCKET_ENGINE.get());
            any |= isEngine[i];
        }
        if (any) {
            for (int y = 0; y < portrait.sizeY; y++) {
                for (int z = 0; z < portrait.sizeZ; z++) {
                    for (int x = 0; x < portrait.sizeX; x++) {
                        // só o bocal de baixo: num motor formado (2x2x2, 3x3x3) a chama sai da camada de baixo, não de cada bloco
                        if (isEngine[portrait.grid().get(x, y, z)] && !(y > 0 && isEngine[portrait.grid().get(x, y - 1, z)])) {
                            list.add(new Vector3f(x + 0.5F, y, z + 0.5F));
                        }
                    }
                }
            }
        }
        ENGINES.put(portrait.id(), list);
        return list;
    }

    private static void engineExhaust(ClientLevel level, RocketEntity rocket) {
        RandomSource random = level.getRandom();
        float throttle = rocket.throttle();
        Quaternionf attitude = new Quaternionf();
        rocket.orientation(1.0F, attitude);
        Vector3f pivot = rocket.pivotLocal();
        Vector3f exhaust = attitude.transform(new Vector3f(0.0F, -1.0F, 0.0F));
        Vec3 drift = rocket.getDeltaMovement();
        Vec3 center = rocket.position();
        Vec3 firstNozzle = null;

        for (Vector3f local : enginesOf(rocket.portrait())) {
            Vector3f offset = attitude.transform(new Vector3f(local).sub(pivot));
            Vec3 nozzle = center.add(offset.x(), offset.y(), offset.z());
            if (firstNozzle == null) {
                firstNozzle = nozzle;
            }
            int flames = 1 + Math.round(throttle * 5.0F) + (random.nextFloat() < 0.5F ? 1 : 0);
            for (int i = 0; i < flames; i++) {
                double speed = 0.25 + (0.15 + 0.55 * throttle) * random.nextDouble();
                level.addParticle(ParticleTypes.FLAME, nozzle.x + jitter(random, 0.25), nozzle.y + jitter(random, 0.25), nozzle.z + jitter(random, 0.25),
                        exhaust.x() * speed + drift.x + jitter(random, 0.04), exhaust.y() * speed + drift.y + jitter(random, 0.04),
                        exhaust.z() * speed + drift.z + jitter(random, 0.04));
            }
            if (throttle > 0.15F && random.nextFloat() < 0.25F + 0.5F * throttle) {
                double speed = 0.1 + 0.25 * throttle;
                level.addParticle(ParticleTypes.CLOUD, nozzle.x + jitter(random, 0.3), nozzle.y + jitter(random, 0.3), nozzle.z + jitter(random, 0.3),
                        exhaust.x() * speed + drift.x + jitter(random, 0.03), exhaust.y() * speed + drift.y + jitter(random, 0.03),
                        exhaust.z() * speed + drift.z + jitter(random, 0.03));
            }
        }
        if (firstNozzle != null) {
            groundSmoke(level, rocket, firstNozzle, exhaust, throttle, random);
        }
    }

    /** Nuvem rente ao chão onde o jato bate: só perto do chão, e mais forte com o acelerador alto. */
    private static void groundSmoke(ClientLevel level, RocketEntity rocket, Vec3 nozzle, Vector3f exhaust, float throttle, RandomSource random) {
        Vec3 end = nozzle.add(exhaust.x() * GROUND_SMOKE_REACH, exhaust.y() * GROUND_SMOKE_REACH, exhaust.z() * GROUND_SMOKE_REACH);
        var hit = level.clip(new ClipContext(nozzle, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rocket));
        if (hit.getType() == HitResult.Type.MISS) {
            return;
        }
        Vec3 point = hit.getLocation();
        double closeness = 1.0 - point.distanceTo(nozzle) / GROUND_SMOKE_REACH;
        int count = (int) Math.ceil(throttle * 5.0 * closeness);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 0.1 + 0.3 * random.nextDouble() * throttle;
            level.addParticle(random.nextFloat() < 0.3F ? ParticleTypes.WHITE_SMOKE : ParticleTypes.CLOUD, point.x + Math.cos(angle) * 0.5,
                    point.y + 0.15, point.z + Math.sin(angle) * 0.5, Math.cos(angle) * speed, 0.02 + 0.04 * random.nextDouble(), Math.sin(angle) * speed);
        }
    }

    // ---------------------------------------------------------------- reentrada

    /** Chamas e fumaça na face voltada para o vento relativo; a quantidade cresce com o calor. */
    private static void reentry(ClientLevel level, RocketEntity rocket) {
        RandomSource random = level.getRandom();
        float k = Math.min(1.0F, (rocket.heat() - REENTRY_HEAT) / 1.3F);
        RocketPortrait portrait = rocket.portrait();
        Quaternionf attitude = new Quaternionf();
        rocket.orientation(1.0F, attitude);

        // direção do vento relativo (de onde o ar vem): o oposto da velocidade; parado, a base do foguete
        Vec3 velocity = rocket.getDeltaMovement();
        Vector3f wind = velocity.lengthSqr() > 1.0E-6
                ? new Vector3f((float) -velocity.x, (float) -velocity.y, (float) -velocity.z).normalize()
                : attitude.transform(new Vector3f(0.0F, -1.0F, 0.0F));
        Vector3f side = Math.abs(wind.y()) < 0.9F ? new Vector3f(0.0F, 1.0F, 0.0F).cross(wind).normalize() : new Vector3f(1.0F, 0.0F, 0.0F).cross(wind).normalize();
        Vector3f up = new Vector3f(wind).cross(side);
        float radius = 0.5F * Math.max(portrait.sizeX, portrait.sizeZ) + 0.5F;
        float reach = 0.5F * portrait.sizeY * 0.8F + 0.5F;
        Vec3 center = rocket.position();

        int count = 2 + Math.round(10.0F * k);
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = radius * Math.sqrt(random.nextDouble());
            double px = center.x + wind.x() * reach + side.x() * Math.cos(a) * r + up.x() * Math.sin(a) * r;
            double py = center.y + wind.y() * reach + side.y() * Math.cos(a) * r + up.y() * Math.sin(a) * r;
            double pz = center.z + wind.z() * reach + side.z() * Math.cos(a) * r + up.z() * Math.sin(a) * r;
            level.addParticle(random.nextFloat() < 0.75F ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, px, py, pz, velocity.x * 0.5 + jitter(random, 0.05),
                    velocity.y * 0.5 + jitter(random, 0.05), velocity.z * 0.5 + jitter(random, 0.05));
            if (random.nextFloat() < 0.5F * k) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, px, py, pz, jitter(random, 0.05), jitter(random, 0.05), jitter(random, 0.05));
            }
        }
    }

    private static double jitter(RandomSource random, double amount) {
        return (random.nextDouble() - 0.5) * 2.0 * amount;
    }
}
