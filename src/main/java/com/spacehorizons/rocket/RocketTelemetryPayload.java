package com.spacehorizons.rocket;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Telemetria de voo (servidor para o piloto), o que o painel mostra. O servidor manda ao piloto a cada 4 ticks enquanto
 * ele está no foguete. Distâncias em blocos, velocidades em blocos/tick, ângulos em graus, combustível em mB.
 *
 * @param body               id do corpo dominante da física (ex.: "earth", "moon")
 * @param inSpace            true se o foguete está na dimensão de espaço
 * @param altitude           altitude sobre o nível do mar do corpo dominante
 * @param verticalSpeed      velocidade vertical (positiva subindo), relativa ao corpo
 * @param horizontalSpeed    velocidade horizontal em relação ao chão do corpo
 * @param orbitalSpeed       módulo da velocidade relativa ao centro do corpo (inercial)
 * @param apoapsisAltitude   altitude do apoastro (Double.POSITIVE_INFINITY se a órbita não é fechada)
 * @param periapsisAltitude  altitude do periastro
 * @param inclination        inclinação (polo P = −norte; órbita para leste = 0)
 * @param timeToApoapsis     ticks até o apoastro (NaN se não há)
 * @param fuelMb             combustível restante
 * @param fuelCapacityMb     capacidade total dos tanques
 * @param deltaV             delta-v restante (b/t)
 * @param twr                razão empuxo/peso local com o acelerador no máximo
 * @param throttle           acelerador aplicado (0..1)
 * @param sasMode            modo do SAS (ordinal de AttitudeMath.SasMode)
 * @param flightMode         ordinal de {@link FlightMode}
 * @param heat               índice de aquecimento (0..2; aviso acima de 0,7)
 * @param chute              abertura do paraquedas (0..1)
 * @param plannerStatus      ordinal do status do TransferPlanner (ou -1 sem plano)
 * @param phaseNow           ângulo de fase atual Terra-Lua (graus) para a transferência
 * @param phaseIdeal         ângulo de fase ideal (graus)
 * @param tliDeltaV          delta-v da injeção translunar (b/t)
 * @param burnNow            true se é hora de queimar para a Lua
 * @param heldForChunks      true se o passo da simulação está segurado esperando chunks
 * @param landingState       pré-carga do local de pouso: 0 sem previsão, 1 carregando, 2 pronta ({@link LandingPreloader.State})
 * @param landingPercent     porcentagem (0..100) dos chunks do local de pouso previsto já carregados
 * @param marsStatus         ordinal do status do MarsPlanner (ou -1 sem plano: fora da órbita da Terra)
 * @param marsPhaseNow       quanto Marte está à frente da Terra na órbita em volta do sol (graus)
 * @param marsPhaseIdeal     a fase ideal para queimar a partir do ponto em que a nave está (NaN fora da faixa de queima)
 * @param marsDeltaV         delta-v da injeção para Marte (b/t)
 * @param marsBurnNow        true se é hora de queimar para Marte
 * @param marsTimeToBurn     ticks até a próxima queima possível para Marte (NaN sem previsão)
 */
public record RocketTelemetryPayload(String body, boolean inSpace, double altitude, double verticalSpeed, double horizontalSpeed,
        double orbitalSpeed, double apoapsisAltitude, double periapsisAltitude, double inclination, double timeToApoapsis,
        double fuelMb, double fuelCapacityMb, double deltaV, double twr, float throttle, byte sasMode, byte flightMode, float heat,
        float chute, byte plannerStatus, float phaseNow, float phaseIdeal, float tliDeltaV, boolean burnNow, boolean heldForChunks,
        byte landingState, byte landingPercent, byte marsStatus, float marsPhaseNow, float marsPhaseIdeal, float marsDeltaV, boolean marsBurnNow,
        float marsTimeToBurn) implements CustomPacketPayload {
    /** Sem o plano de Marte (o que a telemetria do Tier 1 sempre foi). */
    public RocketTelemetryPayload(String body, boolean inSpace, double altitude, double verticalSpeed, double horizontalSpeed, double orbitalSpeed,
            double apoapsisAltitude, double periapsisAltitude, double inclination, double timeToApoapsis, double fuelMb, double fuelCapacityMb,
            double deltaV, double twr, float throttle, byte sasMode, byte flightMode, float heat, float chute, byte plannerStatus, float phaseNow,
            float phaseIdeal, float tliDeltaV, boolean burnNow, boolean heldForChunks, byte landingState, byte landingPercent) {
        this(body, inSpace, altitude, verticalSpeed, horizontalSpeed, orbitalSpeed, apoapsisAltitude, periapsisAltitude, inclination, timeToApoapsis,
                fuelMb, fuelCapacityMb, deltaV, twr, throttle, sasMode, flightMode, heat, chute, plannerStatus, phaseNow, phaseIdeal, tliDeltaV, burnNow,
                heldForChunks, landingState, landingPercent, (byte) -1, 0.0F, Float.NaN, 0.0F, false, Float.NaN);
    }

    public static final Type<RocketTelemetryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "rocket_telemetry"));

    public static final StreamCodec<ByteBuf, RocketTelemetryPayload> CODEC = StreamCodec.of(
            (buf, p) -> p.write(new FriendlyByteBuf(buf)), buf -> read(new FriendlyByteBuf(buf)));

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(body, 32);
        buf.writeBoolean(inSpace);
        buf.writeDouble(altitude);
        buf.writeDouble(verticalSpeed);
        buf.writeDouble(horizontalSpeed);
        buf.writeDouble(orbitalSpeed);
        buf.writeDouble(apoapsisAltitude);
        buf.writeDouble(periapsisAltitude);
        buf.writeDouble(inclination);
        buf.writeDouble(timeToApoapsis);
        buf.writeDouble(fuelMb);
        buf.writeDouble(fuelCapacityMb);
        buf.writeDouble(deltaV);
        buf.writeDouble(twr);
        buf.writeFloat(throttle);
        buf.writeByte(sasMode);
        buf.writeByte(flightMode);
        buf.writeFloat(heat);
        buf.writeFloat(chute);
        buf.writeByte(plannerStatus);
        buf.writeFloat(phaseNow);
        buf.writeFloat(phaseIdeal);
        buf.writeFloat(tliDeltaV);
        buf.writeBoolean(burnNow);
        buf.writeBoolean(heldForChunks);
        buf.writeByte(landingState);
        buf.writeByte(landingPercent);
        buf.writeByte(marsStatus);
        buf.writeFloat(marsPhaseNow);
        buf.writeFloat(marsPhaseIdeal);
        buf.writeFloat(marsDeltaV);
        buf.writeBoolean(marsBurnNow);
        buf.writeFloat(marsTimeToBurn);
    }

    private static RocketTelemetryPayload read(FriendlyByteBuf buf) {
        return new RocketTelemetryPayload(buf.readUtf(32), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readByte(), buf.readByte(), buf.readFloat(),
                buf.readFloat(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readByte(),
                buf.readByte(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
