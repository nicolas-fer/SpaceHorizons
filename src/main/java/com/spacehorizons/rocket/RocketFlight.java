package com.spacehorizons.rocket;

import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import com.spacehorizons.rocket.FlightMachine.Snapshot;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.V3;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Estado de voo e controle do foguete no servidor: o último comando do piloto (com detecção de borda dos bits de ação), o
 * instantâneo da {@link FlightMachine} (a verdade do voo), o portão de chunks e os contadores da travessia de dimensão. A
 * física em si é da {@link FlightMachine}; o encaixe com o mundo (tickets, troca de dimensão, pouso, telemetria) é do
 * {@link RocketFlightController}. É salvo junto com a entidade, porque a troca de dimensão a recria a partir do NBT.
 */
public final class RocketFlight {
    /** Ticks sem pacote depois dos quais o comando do piloto é tratado como neutro (rede ruim, alt-tab). */
    public static final int CONTROL_TIMEOUT_TICKS = 10;
    /** Depois de tantos ticks esperando o piloto rastrear o foguete (travessia), a simulação segue mesmo assim. */
    static final int MAX_ARRIVAL_TICKS = 100;

    private FlightMode mode = FlightMode.LANDED;
    private RocketControlPayload control = RocketControlPayload.NEUTRAL;
    private long controlTick = Long.MIN_VALUE / 2;
    /** Bits de ação que subiram (estavam apagados no pacote anterior) desde o último tick. */
    private int edges;

    @Nullable Snapshot snapshot;
    final ChunkGate gate = new ChunkGate();
    /** Pré-carga do local de pouso previsto (tickets do trecho final da descida, ver {@link LandingPreloadDriver}). */
    final LandingPreloader landing = new LandingPreloader();
    /** Level em que a pré-carga pediu tickets (para soltá-los no mesmo level) e contador de ticks dela. */
    net.minecraft.server.level.@Nullable ServerLevel landingLevel;
    int landingTicks;
    /** Recém-chegado de outra dimensão (ou carregado do disco em voo): espera o piloto estar pronto antes de simular. */
    boolean arrivalPending;
    int arrivalTicks;
    /** Velocidade das coordenadas (blocos/tick) do último passo: para pedir os chunks à frente mesmo com o passo segurado. */
    Vec3 lastVelocity = Vec3.ZERO;
    double lastClock = Double.NaN;
    /** Ticks segurados seguidos no tick anterior (só para registrar o fim de uma espera). */
    int lastHeld;
    boolean warnedNoSpace;
    boolean warnedChunks;

    /** A pré-carga do local de pouso (estado e progresso para a telemetria e o autoteste). */
    public LandingPreloader landing() {
        return this.landing;
    }

    public FlightMode mode() {
        return this.snapshot != null ? this.snapshot.mode() : this.mode;
    }

    /** O instantâneo do voo (null antes do primeiro tick do servidor). Só leitura: o estado é da máquina. */
    public @Nullable Snapshot snapshot() {
        return this.snapshot;
    }

    /** Guarda o comando (já saneado) recebido no tick {@code tick}; os bits de ação que subiram viram bordas. */
    public void applyControl(RocketControlPayload payload, long tick) {
        RocketControlPayload clean = payload.sanitized();
        this.edges |= clean.flags() & ~this.control.flags();
        this.control = clean;
        this.controlTick = tick;
    }

    /** O comando em vigor no tick {@code tick}: o último recebido, ou neutro se faz mais de {@link #CONTROL_TIMEOUT_TICKS} ticks. */
    public RocketControlPayload control(long tick) {
        return tick - this.controlTick > CONTROL_TIMEOUT_TICKS ? RocketControlPayload.NEUTRAL : this.control;
    }

    /** Os bits de ação que subiram desde a última chamada (e os zera). */
    int takeEdges() {
        int taken = this.edges;
        this.edges = 0;
        return taken;
    }

    /**
     * Devolve bordas de ação que um tick segurado (espera de chunks ou do piloto na travessia) não aplicou: sem isto o pulso
     * do SAS, que dura um pacote só, se perdia quando o passo era segurado.
     */
    void restoreEdges(int taken) {
        this.edges |= taken;
    }

    /** Um tick de simulação do servidor. */
    public void tick(RocketEntity rocket) {
        RocketFlightController.tick(rocket, this);
    }

    /** Só vale antes de haver instantâneo (carga do NBT); depois o modo é o da máquina. */
    public void setMode(FlightMode mode) {
        this.mode = mode;
    }

    // ---------------------------------------------------------------- NBT

    /** Grava o voo (só em voo: pousado, o estado é o da entidade). */
    void save(ValueOutput output) {
        Snapshot s = this.snapshot;
        if (s == null || !s.flying() || s.state() == null) {
            return;
        }
        ValueOutput out = output.child("Flight");
        out.putString("Zone", s.zoneBody() == null ? "space" : s.zoneBody().planet().id().getPath());
        out.putByte("Mode", s.mode().id());
        FlightState st = s.state();
        out.putString("Body", st.body().planet().id().getPath());
        putVec(out, "R", st.r());
        putVec(out, "V", st.v());
        out.putDouble("Fuel", st.fuelMb());
        out.putDouble("Heat", st.heat());
        out.putDouble("Chute", st.chute());
        Quaterniond q = s.attitude();
        out.putDouble("Qx", q.x);
        out.putDouble("Qy", q.y);
        out.putDouble("Qz", q.z);
        out.putDouble("Qw", q.w);
        putVec(out, "Spin", s.spin());
        out.putByte("Sas", (byte) s.sas().ordinal());
        out.putDouble("Throttle", s.throttle());
        out.putBoolean("ChuteWanted", s.chuteRequested());
    }

    /** Lê o voo salvo por {@link #save}; sem o grupo (ou com corpo desconhecido) o foguete volta pousado. */
    void load(ValueInput input) {
        this.snapshot = null;
        input.child("Flight").ifPresent(in -> {
            SolarSystem.Body body = bodyByName(in.getStringOr("Body", ""));
            String zoneName = in.getStringOr("Zone", "space");
            SolarSystem.Body zone = "space".equals(zoneName) ? null : bodyByName(zoneName);
            if (body == null || (zone == null && !"space".equals(zoneName))) {
                return;
            }
            FlightState state = new FlightState(body, getVec(in, "R"), getVec(in, "V"), in.getDoubleOr("Fuel", 0.0),
                    in.getDoubleOr("Heat", 0.0), in.getDoubleOr("Chute", 0.0));
            Quaterniond q = new Quaterniond(in.getDoubleOr("Qx", 0.0), in.getDoubleOr("Qy", 0.0), in.getDoubleOr("Qz", 0.0),
                    in.getDoubleOr("Qw", 1.0)).normalize();
            FlightMode loaded = FlightMode.byId(in.getByteOr("Mode", FlightMode.FLIGHT.id()));
            if (loaded == FlightMode.LANDED) {
                loaded = FlightMode.FLIGHT;
            }
            this.snapshot = new Snapshot(loaded, state, q, getVec(in, "Spin"), SasMode.byId(in.getByteOr("Sas", (byte) 0)),
                    in.getDoubleOr("Throttle", 0.0), in.getBooleanOr("ChuteWanted", false), zone, state.fuelMb());
            this.arrivalPending = true;
            this.arrivalTicks = 0;
        });
    }

    private static void putVec(ValueOutput out, String key, V3 v) {
        out.putDouble(key + "x", v.x());
        out.putDouble(key + "y", v.y());
        out.putDouble(key + "z", v.z());
    }

    private static V3 getVec(ValueInput in, String key) {
        return new V3(in.getDoubleOr(key + "x", 0.0), in.getDoubleOr(key + "y", 0.0), in.getDoubleOr(key + "z", 0.0));
    }

    /** O corpo pelo nome do caminho do id do planeta ({@code earth}, {@code moon}); null se desconhecido. */
    static SolarSystem.@Nullable Body bodyByName(String name) {
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            if (body.planet().id().getPath().equals(name)) {
                return body;
            }
        }
        return null;
    }
}
