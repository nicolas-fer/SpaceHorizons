package com.spacehorizons.ship;

import org.joml.Quaterniond;

import com.spacehorizons.content.ModEntities;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.V3;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A subida da nave até a altitude de saída (fase 2a-ii): o gatilho que manda a entidade para o estaleiro, a conversão do estado de voo
 * da entidade no estado virtual da nave e o piloto sentado na ponte. Puro, exceto {@link #sitPilot}.
 */
public final class ShipAscent {
    private ShipAscent() {
    }

    /**
     * O gatilho: a nave voando cruza a altitude de saída do corpo. Vale uma vez só ({@code requested} fica verdadeiro depois do primeiro
     * pedido), para o job não ser enfileirado a cada tick.
     */
    public static boolean shouldConvert(boolean ship, boolean flying, boolean leaves, boolean requested) {
        return ship && flying && leaves && !requested;
    }

    /**
     * O estado de voo da entidade no instante da troca, no estado virtual da nave. Os dois usam eixos inerciais e a atitude de estilo
     * foguete (ver {@link ShipFlight#attitudeFor}), então a conversão é cópia: a posição e a velocidade são as do {@link FlightState}, sem
     * mudar de referencial, e a atitude e o SAS seguem como estão.
     *
     * <p>Decisão de produto: o acelerador NÃO passa para o voo virtual. A entidade para de queimar na troca e a nave começa com o acelerador
     * em 0; o piloto religa se quiser. Assim a órbita que a troca entrega é a que o piloto vê, sem queima que continue em segundo plano.
     */
    public static ShipFlight.State flightState(FlightState orbit, Quaterniond attitude, V3 spin, SasMode sas) {
        return new ShipFlight.State(orbit, attitude, spin, sas, 0.0);
    }

    /**
     * Senta o piloto na ponte da nave no estaleiro: um assento invisível ({@link ShipSeatEntity}) no ponto {@code at}, que é o que
     * {@link ShipPilots#pilotOf} procura. Devolve se sentou.
     */
    public static boolean sitPilot(ServerLevel yard, ServerPlayer pilot, Vec3 at) {
        ShipSeatEntity seat = ShipSeatEntity.free(yard);
        seat.setPos(at.x, at.y, at.z);
        yard.addFreshEntity(seat);
        return pilot.startRiding(seat);
    }
}
