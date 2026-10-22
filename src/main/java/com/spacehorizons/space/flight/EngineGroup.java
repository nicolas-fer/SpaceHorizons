package com.spacehorizons.space.flight;

/**
 * Os motores de um mesmo tipo do foguete, somados: queimam o mesmo {@link Propellant} juntos. {@code thrust} é o empuxo somado a 100% e
 * {@code ispTicks} o Isp do grupo (a média harmônica pelo empuxo, {@code ΣF / Σ(F/Isp)}, quando há tamanhos diferentes). Com
 * {@code needsCore} (o motor nuclear), o grupo também gasta {@link Propellant#NUCLEAR_CORE} na mesma quantidade em mB que gasta de
 * hidrogênio: sem núcleo ele para, mesmo com hidrogênio.
 *
 * <p>Vários grupos queimam ao mesmo tempo (cada um o seu combustível): a vazão de massa soma, e o Isp do conjunto é a média harmônica
 * pelo empuxo dos grupos que ainda têm combustível ({@link RocketStats#deltaV(FuelLoad)} faz as fases: quando um combustível acaba, o
 * grupo dele para e os outros seguem).
 */
public record EngineGroup(String type, Propellant propellant, boolean needsCore, double thrust, double ispTicks, int blocks) {
    public EngineGroup {
        if (thrust < 0.0) {
            throw new IllegalArgumentException("empuxo negativo: " + thrust);
        }
        if (thrust > 0.0 && !(ispTicks > 0.0)) {
            throw new IllegalArgumentException("Isp deve ser positivo quando há empuxo: " + ispTicks);
        }
    }

    /** Velocidade de exaustão (blocos/tick). */
    public double exhaustVelocity() {
        return this.ispTicks * FlightConstants.G0;
    }

    /** Vazão de massa a 100% (t/tick). */
    public double fullFlowT() {
        return this.thrust > 0.0 ? this.thrust / exhaustVelocity() : 0.0;
    }

    /** O combustível que este grupo pode queimar com a carga dada, em mB (limitado pelo núcleo, se o precisa). */
    public double usableMb(FuelLoad fuel) {
        double mb = fuel.get(this.propellant);
        return this.needsCore ? Math.min(mb, fuel.get(Propellant.NUCLEAR_CORE)) : mb;
    }

    /** O grupo empurra com esta carga (tem empuxo e combustível utilizável). */
    public boolean active(FuelLoad fuel) {
        return this.thrust > 0.0 && usableMb(fuel) > 0.0;
    }
}
