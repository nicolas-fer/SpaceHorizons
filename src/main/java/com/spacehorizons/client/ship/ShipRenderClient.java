package com.spacehorizons.client.ship;

import org.jspecify.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.space.SkyAttitude;
import com.spacehorizons.ship.ShipFlight;
import com.spacehorizons.ship.ShipFlightData;
import com.spacehorizons.ship.ShipStatePayload;
import com.spacehorizons.ship.ShipYard;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.AttitudeMath;
import com.spacehorizons.space.flight.FrameBridge;
import com.spacehorizons.space.flight.V3;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * O estado virtual da nave no cliente (docs/PLANO_NAVES.md, 4.1; fase 1d): guarda o último {@link ShipStatePayload} recebido e o extrapola
 * para cada quadro, para o céu dentro da nave sair da posição virtual dela e girar com a atitude (a nave é o referencial).
 *
 * <p>A extrapolação usa um relógio de SERVIDOR estimado a partir dos números de tick dos pacotes: o deslocamento do relógio do cliente em
 * relação ao do servidor é o MÍNIMO de (chegada − tick×50 ms) (o pacote de menor atraso), com folga lenta para o lado oposto. Assim o instante
 * de render não depende do atraso de cada pacote (que, com o giro a 2,3 graus por tick, faria o céu tremer); a atitude e a posição andam
 * continuamente entre dois pacotes e a correção na chegada de um novo é só o erro da extrapolação (nulo com o giro constante).
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class ShipRenderClient {
    private static final double TICK_NANOS = 50_000_000.0;
    /** Sem pacote por mais disto (ms), o estado é considerado velho e o céu volta ao de teste. */
    private static final long STALE_MILLIS = 2_000L;
    /** Quanto o deslocamento do relógio pode subir por pacote (ns): segue mudanças reais do atraso sem acompanhar o ruído. */
    private static final double OFFSET_RELAX_NANOS = 20_000.0;
    /** O máximo de ticks que se extrapola além do último pacote. */
    private static final double MAX_EXTRAPOLATION_TICKS = 12.0;

    private static volatile @Nullable ShipStatePayload last;
    private static volatile long lastArrivalNanos;
    private static volatile double clockOffsetNanos = Double.NaN;
    private static volatile long packets;

    private ShipRenderClient() {
    }

    @SubscribeEvent
    static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ShipStatePayload.TYPE, (payload, context) -> {
            long now = System.nanoTime();
            double raw = now - payload.tick() * TICK_NANOS;
            double offset = clockOffsetNanos;
            clockOffsetNanos = Double.isNaN(offset) || raw < offset ? raw : Math.min(raw, offset + OFFSET_RELAX_NANOS);
            last = payload;
            lastArrivalNanos = now;
            packets++;
            SkyAttitude.setExternal(ShipRenderClient::skyAttitude);
        });
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (last != null && (!inShipyard() || stale())) {
            clear();
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static boolean inShipyard() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && ShipYard.is(mc.level);
    }

    private static boolean stale() {
        return (System.nanoTime() - lastArrivalNanos) / 1_000_000L > STALE_MILLIS;
    }

    private static void clear() {
        last = null;
        clockOffsetNanos = Double.NaN;
        SkyAttitude.setExternal(null);
    }

    /** Há estado de nave recente (o céu vem dele)? */
    public static boolean active() {
        return last != null && !stale();
    }

    /** O último estado recebido (para o autoteste e o painel), ou null. */
    public static @Nullable ShipStatePayload latest() {
        return active() ? last : null;
    }

    public static long packetCount() {
        return packets;
    }

    // ---------------------------------------------------------------- extrapolação

    /** Os ticks de servidor entre o estado do último pacote e o instante {@code nowNanos} (relógio de servidor estimado). */
    private static double ticksSince(ShipStatePayload p, long nowNanos) {
        double offset = clockOffsetNanos;
        if (Double.isNaN(offset)) {
            return 0.0;
        }
        double serverTicks = (nowNanos - offset) / TICK_NANOS;
        return Math.max(-1.0, Math.min(MAX_EXTRAPOLATION_TICKS, serverTicks - p.tick()));
    }

    /** A atitude de estilo foguete {@code p} (inercial) em {@code nowNanos}: a do pacote girada pelo giro vezes o tempo. */
    static Quaterniond rocketAttitudeAt(ShipStatePayload p, long nowNanos) {
        double dt = ticksSince(p, nowNanos);
        Quaterniond q = new Quaterniond(p.attitude()[0], p.attitude()[1], p.attitude()[2], p.attitude()[3]).normalize();
        double[] spin = p.spin();
        return AttitudeMath.rotateBody(q, spin[0] * dt, spin[2] * dt, spin[1] * dt);
    }

    /** A atitude R do céu (eixos do espaço, ver {@link SkyAttitude}) para o relógio de render {@code clock}; null sem estado recente. */
    public static @Nullable Quaterniondc skyAttitude(double clock, long nowNanos) {
        ShipStatePayload p = last;
        if (p == null || stale() || !inShipyard()) {
            return null;
        }
        Quaterniond qBody = ShipFlight.bodyAttitude(rocketAttitudeAt(p, nowNanos));
        Quaterniond local = AttitudeMath.inertialToSpaceLocal(qBody, clock);
        return SkyAttitude.localToSpaceFrame(local);
    }

    /** A posição virtual da nave em E (eixos do espaço) para o relógio {@code clock}, extrapolada pela velocidade; null sem estado recente. */
    public static double @Nullable [] positionE(double clock, long nowNanos) {
        ShipStatePayload p = last;
        if (p == null || stale()) {
            return null;
        }
        SolarSystem.Body body = ShipFlightData.bodyByName(p.body());
        if (body == null) {
            return null;
        }
        double dt = ticksSince(p, nowNanos);
        V3 r = new V3(p.r()[0] + p.v()[0] * dt, p.r()[1] + p.v()[1] * dt, p.r()[2] + p.v()[2] * dt);
        V3 e = FrameBridge.absoluteInEarthFixed(body, clock, r);
        return new double[] {e.x(), e.y(), e.z()};
    }
}
