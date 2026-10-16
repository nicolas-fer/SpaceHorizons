package com.spacehorizons.space.flight;

/**
 * Números do foguete que a física precisa (contrato de docs/DESIGN_FOGUETE.md, seção 2.1). Unidades do jogo: massa em
 * toneladas, empuxo em t·blocos/tick², velocidade de exaustão em blocos/tick, Isp em ticks.
 *
 * VERSÃO MÍNIMA da frente A2 (foguete de blocos): só o necessário para o foguete de blocos calcular as estatísticas a
 * partir do retrato. A frente A1 (física pura) cria a versão completa deste arquivo; no merge fica a dela, desde que
 * mantenha o construtor e os quatro métodos abaixo (é só o que o código de A2 usa).
 *
 * @param dryMassT        massa seca (blocos), em toneladas
 * @param thrust          empuxo total dos motores, em t·blocos/tick²
 * @param ispTicks        impulso específico, em ticks (a velocidade de exaustão é Isp · G0)
 * @param fuelCapacityMb  capacidade total dos tanques, em mB
 * @param dragK0          coeficiente de arrasto sem paraquedas (G0 / vt²)
 * @param parachuteK0     coeficiente de arrasto com o paraquedas aberto (zero se não houver paraquedas)
 */
public record RocketStats(double dryMassT, double thrust, double ispTicks, double fuelCapacityMb,
        double dragK0, double parachuteK0, int engines, int tanks, int parachutes, int legs) {
    /** Gravidade de referência do jogo em blocos/tick² (igual a FlightConstants.G0 da frente A1). */
    private static final double G0 = 0.08;
    /** Massa do combustível, em toneladas por mB (um galão de 1.000 mB pesa 0,8 t). */
    private static final double FUEL_T_PER_MB = 0.0008;

    /** Velocidade de exaustão em blocos/tick: Isp · G0. */
    public double exhaustVelocity() {
        return this.ispTicks * G0;
    }

    /** Massa total com {@code fuelMb} de combustível, em toneladas. */
    public double wetMass(double fuelMb) {
        return this.dryMassT + Math.max(0.0, fuelMb) * FUEL_T_PER_MB;
    }

    /** Δv de Tsiolkovsky com {@code fuelMb} de combustível, em blocos/tick (zero sem combustível ou sem motor). */
    public double deltaV(double fuelMb) {
        double wet = this.wetMass(fuelMb);
        if (this.engines <= 0 || fuelMb <= 0.0 || this.dryMassT <= 0.0 || wet <= this.dryMassT) {
            return 0.0;
        }
        return this.exhaustVelocity() * Math.log(wet / this.dryMassT);
    }

    /** Relação empuxo-peso com {@code fuelMb} de combustível numa gravidade {@code g} (blocos/tick²). */
    public double twr(double fuelMb, double g) {
        double weight = this.wetMass(fuelMb) * g;
        return weight <= 0.0 ? 0.0 : this.thrust / weight;
    }
}
