package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.Control;
import com.spacehorizons.space.flight.FlightIntegrator;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.FrameBridge;
import com.spacehorizons.space.flight.FrameBridge.Local;
import com.spacehorizons.space.flight.RocketStats;

/**
 * Previsão do ponto de impacto (lógica pura): integra o estado de voo para frente, um tick de cada vez, com o MESMO
 * integrador do voo ({@link FlightIntegrator}: gravidade e arrasto da atmosfera), sem empuxo e sem paraquedas, até a
 * posição (nas coordenadas da dimensão do corpo) tocar o terreno. É uma biblioteca em cima da física de {@code space/flight};
 * não a altera nem a duplica.
 *
 * <p>Sem empuxo e sem paraquedas é a previsão "se eu largar tudo agora": conservadora para o paraquedas (que encurta a
 * descida e a deriva) e errada durante uma queima de freio (que o piloto está fazendo). Por isso quem usa atualiza a
 * previsão uma vez por segundo e pede chunks para uma área, não para um ponto.
 */
public final class ImpactPredictor {
    /** Máximo de ticks integrados (6,7 minutos): mais longe que isso o ponto não é confiável nem interessa. */
    public static final int MAX_STEPS = 8_000;
    /** Abaixo desta coordenada y da dimensão (acima do teto do mundo) o terreno é consultado. */
    public static final double TERRAIN_CHECK_Y = 400.0;
    /** Só os pontos abaixo desta coordenada y da dimensão são guardados para o trecho final da trajetória. */
    public static final double PATH_RECORD_Y = 2_600.0;

    /** Altura do terreno (coordenada y do topo do bloco mais alto) na coluna {@code (x, z)} da dimensão do corpo. */
    @FunctionalInterface
    public interface Terrain {
        double height(double x, double z);
    }

    /** Um ponto da trajetória nas coordenadas da dimensão. */
    public record Point(double x, double y, double z) {
    }

    /**
     * O impacto previsto: onde ({@code x}, {@code z}, a altura do terreno {@code y}), quando ({@code ticks} a partir de agora)
     * e o trecho final da trajetória (do mais alto ao ponto de impacto), nas coordenadas da dimensão do corpo.
     */
    public record Prediction(double x, double y, double z, int ticks, List<Point> path) {
    }

    private ImpactPredictor() {
    }

    /**
     * Prevê o impacto do estado {@code s} no relógio {@code clock} (início do próximo tick). Devolve null se a trajetória
     * não toca o terreno em {@link #MAX_STEPS} ticks (órbita estável, escape, ou ainda muito longe).
     */
    public static @Nullable Prediction predict(FlightState s, RocketStats stats, double clock, Terrain terrain) {
        BodyPhysics bp = BodyPhysics.of(s.body());
        double seaLevel = s.body().planet().seaLevel();
        FlightState state = s.withChute(0.0);
        List<Point> path = new ArrayList<>();
        // distância ao centro abaixo da qual a posição é convertida para a dimensão (o resto do voo está acima do teto do mundo)
        double recordRadius = bp.radius() + (PATH_RECORD_Y - seaLevel);
        for (int k = 1; k <= MAX_STEPS; k++) {
            state = FlightIntegrator.step(state, Control.IDLE, stats, clock + k - 1);
            double r = state.r().length();
            if (r > recordRadius) {
                if (state.verticalSpeed() > 0.0 && r > bp.radius() * 4.0) {
                    return null; // subindo para longe: não volta
                }
                continue;
            }
            Local l = FrameBridge.toDimension(s.body(), clock + k, 1.0, state.r(), state.v());
            path.add(new Point(l.x(), l.y(), l.z()));
            if (l.y() < TERRAIN_CHECK_Y) {
                double ground = terrain.height(l.x(), l.z());
                if (l.y() <= ground) {
                    return new Prediction(l.x(), ground, l.z(), k, trimmed(path, ground, LandingPreloader.PATH_HEIGHT));
                }
            }
        }
        return null;
    }

    /** O trecho da trajetória até {@code height} blocos acima de {@code ground}, e o último ponto fica no chão. */
    private static List<Point> trimmed(List<Point> path, double ground, double height) {
        int first = path.size();
        while (first > 0 && path.get(first - 1).y() - ground <= height) {
            first--;
        }
        return new ArrayList<>(path.subList(first, path.size()));
    }
}
