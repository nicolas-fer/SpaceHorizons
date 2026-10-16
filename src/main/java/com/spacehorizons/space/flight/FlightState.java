package com.spacehorizons.space.flight;

import com.spacehorizons.space.SolarSystem;

/**
 * Estado de voo: SEMPRE relativo ao corpo dominante, em eixos inerciais paralelos aos do espaço (patched conics). A
 * posição {@code r} e a velocidade {@code v} (blocos e blocos/tick de jogo) são relativas ao CENTRO do corpo; a Lua é
 * cinemática e nunca integrada. A troca de corpo é do {@link SoiTracker}; a conversão para as coordenadas da dimensão é
 * do {@link FrameBridge}.
 *
 * <p>{@code heat} é o índice visual de aquecimento (0..{@link FlightConstants#HEAT_MAX}) e {@code chute} a abertura do
 * paraquedas (0 fechado, 1 aberto; é estado físico porque a rampa dura 80 ticks).
 */
public record FlightState(SolarSystem.Body body, V3 r, V3 v, double fuelMb, double heat, double chute) {

    /** Estado com o paraquedas fechado. */
    public FlightState(SolarSystem.Body body, V3 r, V3 v, double fuelMb, double heat) {
        this(body, r, v, fuelMb, heat, 0.0);
    }

    public FlightState withBody(SolarSystem.Body newBody, V3 newR, V3 newV) {
        return new FlightState(newBody, newR, newV, fuelMb, heat, chute);
    }

    public FlightState withRV(V3 newR, V3 newV) {
        return new FlightState(body, newR, newV, fuelMb, heat, chute);
    }

    public FlightState withFuel(double newFuelMb) {
        return new FlightState(body, r, v, newFuelMb, heat, chute);
    }

    public FlightState withHeat(double newHeat) {
        return new FlightState(body, r, v, fuelMb, newHeat, chute);
    }

    public FlightState withChute(double newChute) {
        return new FlightState(body, r, v, fuelMb, heat, newChute);
    }

    /** Distância ao centro do corpo. */
    public double distance() {
        return r.length();
    }

    /** Altitude sobre o nível do mar do corpo dominante. */
    public double altitude() {
        return BodyPhysics.of(body).altitude(r.length());
    }

    public double speed() {
        return v.length();
    }

    /** Velocidade vertical (radial): positiva subindo. */
    public double verticalSpeed() {
        return v.dot(r.unit());
    }
}
