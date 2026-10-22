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
 *
 * <p>{@code fuel} é a carga por combustível ({@link FuelLoad}, Tier 2); {@link #fuelMb()} é a parte química (o foguete do Tier 1 só
 * tem essa, e os construtores com {@code double fuelMb} são os dele).
 */
public record FlightState(SolarSystem.Body body, V3 r, V3 v, FuelLoad fuel, double heat, double chute) {

    public FlightState {
        fuel = fuel == null ? FuelLoad.EMPTY : fuel;
    }

    /** Estado de um foguete químico (só {@link Propellant#CHEMICAL}). */
    public FlightState(SolarSystem.Body body, V3 r, V3 v, double fuelMb, double heat, double chute) {
        this(body, r, v, FuelLoad.chemical(fuelMb), heat, chute);
    }

    /** Estado com o paraquedas fechado. */
    public FlightState(SolarSystem.Body body, V3 r, V3 v, double fuelMb, double heat) {
        this(body, r, v, fuelMb, heat, 0.0);
    }

    /** O combustível químico, em mB (o único do foguete do Tier 1). */
    public double fuelMb() {
        return fuel.get(Propellant.CHEMICAL);
    }

    public FlightState withBody(SolarSystem.Body newBody, V3 newR, V3 newV) {
        return new FlightState(newBody, newR, newV, fuel, heat, chute);
    }

    public FlightState withRV(V3 newR, V3 newV) {
        return new FlightState(body, newR, newV, fuel, heat, chute);
    }

    /** O mesmo com o combustível QUÍMICO trocado (os outros combustíveis ficam). */
    public FlightState withFuel(double newFuelMb) {
        return new FlightState(body, r, v, fuel.with(Propellant.CHEMICAL, newFuelMb), heat, chute);
    }

    public FlightState withFuelLoad(FuelLoad newFuel) {
        return new FlightState(body, r, v, newFuel, heat, chute);
    }

    public FlightState withHeat(double newHeat) {
        return new FlightState(body, r, v, fuel, newHeat, chute);
    }

    public FlightState withChute(double newChute) {
        return new FlightState(body, r, v, fuel, heat, newChute);
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
