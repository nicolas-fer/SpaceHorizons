package com.spacehorizons.ship;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.joml.Quaterniond;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.V3;

/**
 * O estado de voo da nave como o {@link ShipRecord} o guarda (persistido junto com a nave, plano 2.5): o estado orbital inercial
 * (corpo, posição, velocidade, combustível), a atitude de estilo foguete, o giro, o SAS, o acelerador e o último censo (para a nave ter
 * massa e empuxo logo ao carregar, antes de o primeiro censo em lotes terminar). A conversão de e para {@link ShipFlight.State} é aqui.
 */
public record ShipFlightData(String body, List<Double> r, List<Double> v, double fuelMb, List<Double> attitude, List<Double> spin, int sas,
        double throttle, List<Integer> census) {
    private static final Codec<List<Double>> VEC3 = Codec.DOUBLE.listOf(3, 3);
    private static final Codec<List<Double>> QUAT = Codec.DOUBLE.listOf(4, 4);
    private static final Codec<List<Integer>> CENSUS = Codec.INT.listOf(7, 7);

    public static final Codec<ShipFlightData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("body").forGetter(ShipFlightData::body),
            VEC3.fieldOf("r").forGetter(ShipFlightData::r),
            VEC3.fieldOf("v").forGetter(ShipFlightData::v),
            Codec.DOUBLE.fieldOf("fuel").forGetter(ShipFlightData::fuelMb),
            QUAT.fieldOf("attitude").forGetter(ShipFlightData::attitude),
            VEC3.fieldOf("spin").forGetter(ShipFlightData::spin),
            Codec.INT.fieldOf("sas").forGetter(ShipFlightData::sas),
            Codec.DOUBLE.fieldOf("throttle").forGetter(ShipFlightData::throttle),
            CENSUS.fieldOf("census").forGetter(ShipFlightData::census)
    ).apply(i, ShipFlightData::new));

    /** Grava o estado de voo e o censo. */
    public static ShipFlightData of(ShipFlight.State s, ShipCensus census) {
        FlightState o = s.orbit();
        Quaterniond q = s.attitude();
        return new ShipFlightData(o.body().planet().id().getPath(), List.of(o.r().x(), o.r().y(), o.r().z()), List.of(o.v().x(), o.v().y(), o.v().z()),
                o.fuelMb(), List.of(q.x, q.y, q.z, q.w), List.of(s.spin().x(), s.spin().y(), s.spin().z()), s.sas().ordinal(), s.throttle(),
                List.of(census.bridges(), census.engines(), census.tanks(), census.windows(), census.seats(), census.airlocks(), census.structures()));
    }

    /** O estado de voo (null se o corpo gravado não existe mais). */
    public ShipFlight.@Nullable State toState() {
        SolarSystem.Body b = bodyByName(this.body);
        if (b == null) {
            return null;
        }
        FlightState orbit = new FlightState(b, new V3(this.r.get(0), this.r.get(1), this.r.get(2)), new V3(this.v.get(0), this.v.get(1), this.v.get(2)),
                this.fuelMb, 0.0, 0.0);
        Quaterniond q = new Quaterniond(this.attitude.get(0), this.attitude.get(1), this.attitude.get(2), this.attitude.get(3));
        if (q.lengthSquared() < 1.0e-9) {
            q.identity();
        }
        return new ShipFlight.State(orbit, q, new V3(this.spin.get(0), this.spin.get(1), this.spin.get(2)), SasMode.byId(this.sas),
                Math.max(0.0, Math.min(1.0, this.throttle)));
    }

    /** O corpo pelo nome do caminho do id do planeta ({@code earth}, {@code moon}); null se desconhecido. */
    public static SolarSystem.@Nullable Body bodyByName(String name) {
        for (SolarSystem.Body b : SolarSystem.BODIES) {
            if (b.planet().id().getPath().equals(name)) {
                return b;
            }
        }
        return null;
    }

    /** O último censo gravado. */
    public ShipCensus censusValue() {
        List<Integer> c = this.census;
        return new ShipCensus(c.get(0), c.get(1), c.get(2), c.get(3), c.get(4), c.get(5), c.get(6));
    }
}
