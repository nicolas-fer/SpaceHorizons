package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joml.Quaterniondc;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.ship.ShipRenderClient;
import com.spacehorizons.client.space.SkyObserver;
import com.spacehorizons.client.space.SpaceSun;
import com.spacehorizons.ship.ShipFlight;
import com.spacehorizons.ship.ShipFlightTicker;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.Orbit;
import com.spacehorizons.space.flight.V3;
import com.spacehorizons.space.SpaceClock;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Leituras e medidas do cenário {@code ship} do autoteste (fases 1d e 1e das naves): o estado de voo lido do servidor integrado, onde a Terra está
 * em relação ao olhar (pelo mesmo observador que desenha o céu) e a medida do TREMOR do giro (a velocidade angular do céu quadro a quadro).
 */
final class ShipPilotDevtest {
    private static final String TAG = "[AUTOTEST] SHIP ";

    private ShipPilotDevtest() {
    }

    /** O estado de voo da nave no servidor: órbita (apoastro, periastro, velocidade), combustível, acelerador, SAS e a proa em eixos inerciais. */
    record Live(double apoapsis, double periapsis, double speed, double fuelMb, double throttle, int sas, V3 forward, V3 velocity, double radius) {
    }

    static Live live(Minecraft mc, int shipId) {
        return ShipDevtest.onServer(mc, server -> {
            ShipFlightTicker.Live l = ShipFlightTicker.live(shipId);
            if (l == null) {
                return null;
            }
            var orbit = l.state().orbit();
            double clock = SpaceClock.ticks(server.overworld());
            Orbit o = Orbit.of(orbit, clock);
            double r = BodyPhysics.of(orbit.body()).radius();
            return new Live(o.apoapsis() - r, o.periapsis() - r, orbit.v().length(), orbit.fuelMb(), l.state().throttle(), l.state().sas().ordinal(),
                    ShipFlight.forward(l.state().attitude()), orbit.v(), orbit.r().length());
        });
    }

    static String describe(Live l) {
        return l == null ? "sem estado" : String.format(Locale.ROOT, "apoastro=%.0f periastro=%.0f v=%.3f b/t combustível=%.0f mB acelerador=%.2f sas=%d", l.apoapsis(),
                l.periapsis(), l.speed(), l.fuelMb(), l.throttle(), l.sas());
    }

    static double angleDeg(V3 a, V3 b) {
        return Math.toDegrees(a.angleTo(b));
    }

    /** O vetor de olhar do Minecraft (yaw e pitch em graus) em eixos do mundo. */
    static Vec3 lookVec(float yaw, float pitch) {
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        return new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
    }

    /**
     * Onde a Terra está em relação ao olhar, pelo observador que desenha o céu agora: {distância angular do centro dela ao olhar (graus), raio
     * angular do disco (graus), altura da direção dela (y)}. O disco aparece no quadro se distância - raio é menor que a metade do campo de visão.
     */
    static double[] earthFromView(Minecraft mc, float yaw, float pitch) {
        double clock = SpaceSun.clock(mc.level);
        SkyObserver observer = SkyObserver.of(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ(), clock).orElseThrow();
        var earth = observer.apparent(SolarSystem.EARTH);
        Vec3 look = lookVec(yaw, pitch);
        double dot = earth.x() * look.x + earth.y() * look.y + earth.z() * look.z;
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        return new double[] {angle, Math.toDegrees(earth.angularRadius()), earth.y()};
    }

    // ---------------------------------------------------------------- tremor do giro

    private static final List<double[]> FRAMES = new ArrayList<>();
    private static boolean sampling;
    private static boolean registered;

    /** Começa a gravar a atitude do céu a cada quadro (a mesma função do desenho, no instante do quadro). */
    static void startFrames() {
        FRAMES.clear();
        sampling = true;
        if (!registered) {
            registered = true;
            NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class, e -> {
                Minecraft mc = Minecraft.getInstance();
                if (!sampling || mc.level == null) {
                    return;
                }
                long now = System.nanoTime();
                Quaterniondc q = ShipRenderClient.skyAttitude(SpaceSun.clock(mc.level), now);
                if (q != null) {
                    FRAMES.add(new double[] {now, q.x(), q.y(), q.z(), q.w()});
                }
            });
        }
    }

    /**
     * Para de gravar e mede: a velocidade angular quadro a quadro (graus por segundo) e o quanto ela varia na janela de giro constante (os quadros
     * com velocidade acima de 90% da máxima). Devolve {quadros, máximo, média na janela, desvio relativo máximo na janela, quadros na janela}.
     */
    static double[] stopFrames() {
        sampling = false;
        List<double[]> speeds = new ArrayList<>();
        for (int i = 1; i < FRAMES.size(); i++) {
            double[] a = FRAMES.get(i - 1);
            double[] b = FRAMES.get(i);
            double dt = (b[0] - a[0]) * 1.0e-9;
            if (dt < 1.0e-4) {
                continue;
            }
            double dot = Math.abs(a[1] * b[1] + a[2] * b[2] + a[3] * b[3] + a[4] * b[4]);
            double angle = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, dot)));
            speeds.add(new double[] {angle / dt, dt});
        }
        // quadros atrasados pelo SO (intervalo acima de 3 vezes a mediana) saem da medição; o intervalo do quadro é o tempo real (nanoTime)
        double[] dts = speeds.stream().mapToDouble(s -> s[1]).sorted().toArray();
        final double median = dts.length == 0 ? 0.0 : dts[dts.length / 2];
        int before = speeds.size();
        speeds.removeIf(s -> s[1] > 3.0 * median);
        SpaceHorizons.LOGGER.info(TAG + "TREMOR intervalos entre quadros (ms): minimo {}, mediano {}, maximo {}; descartados {} quadros atrasados (acima de 3x a mediana)",
                String.format(Locale.ROOT, "%.1f", dts.length == 0 ? 0.0 : dts[0] * 1000.0), String.format(Locale.ROOT, "%.1f", median * 1000.0),
                String.format(Locale.ROOT, "%.1f", dts.length == 0 ? 0.0 : dts[dts.length - 1] * 1000.0), before - speeds.size());
        // a janela de giro constante: os quadros do meio do giro (do 25% ao 75% do ângulo total percorrido); a rampa de aceleração e a de freada ficam fora
        double total = 0.0;
        for (double[] s : speeds) {
            total += s[0] * s[1];
        }
        double max = 0.0;
        double acc = 0.0;
        double sum = 0.0;
        int n = 0;
        List<double[]> window = new ArrayList<>();
        for (double[] s : speeds) {
            max = Math.max(max, s[0]);
            double mid = acc + 0.5 * s[0] * s[1];
            acc += s[0] * s[1];
            if (mid > 0.25 * total && mid < 0.75 * total) {
                window.add(s);
                sum += s[0];
                n++;
            }
        }
        double mean = n == 0 ? 0.0 : sum / n;
        double dev = 0.0;
        StringBuilder raw = new StringBuilder();
        for (double[] s : window) {
            dev = Math.max(dev, Math.abs(s[0] - mean) / mean);
            raw.append(String.format(Locale.ROOT, "%.1f@%.0fms ", s[0], s[1] * 1000.0));
        }
        SpaceHorizons.LOGGER.info(TAG + "TREMOR janela (graus/s @ ms do quadro): {}", raw);
        SpaceHorizons.LOGGER.info(TAG + "TREMOR {} quadros, velocidade angular maxima {} graus/s, na janela de giro constante {} quadros a media de {} graus/s com desvio maximo de {}%",
                speeds.size(), String.format(Locale.ROOT, "%.1f", max), n, String.format(Locale.ROOT, "%.1f", mean), String.format(Locale.ROOT, "%.1f", dev * 100.0));
        return new double[] {speeds.size(), max, mean, dev, n};
    }
}
