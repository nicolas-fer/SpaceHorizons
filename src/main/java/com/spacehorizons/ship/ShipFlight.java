package com.spacehorizons.ship;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.space.InertialFrame;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.AttitudeMath;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.Control;
import com.spacehorizons.space.flight.FlightIntegrator;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.RocketStats;
import com.spacehorizons.space.flight.SoiTracker;
import com.spacehorizons.space.flight.V3;
import com.spacehorizons.space.flight.Zones;
import com.spacehorizons.rocket.AttitudeControl;

/**
 * O voo virtual de uma nave no espaço (docs/PLANO_NAVES.md, 2.5 e 6; fase 1c): física PURA, sem Minecraft. Reaproveita o integrador, o
 * SAS e a esfera de influência do foguete: a nave não é uma entidade, só um {@link State} (estado orbital inercial relativo ao corpo
 * dominante, atitude, giro, SAS e acelerador) que avança um passo por tick de servidor. A gravidade NÃO é aplicada a quem está dentro
 * (a nave é o referencial; ver {@code ShipTransit} e o estaleiro): aqui só a órbita da nave muda.
 *
 * <h2>Eixos da nave</h2>
 * (Esta classe trabalha na nave CANÔNICA, abaixo; a proa real vem da ponte e os motores empurram conforme o bocal: ver {@link ShipArrangement}.)
 * Os blocos da nave ficam parados no estaleiro, com os eixos do mundo: x leste (a direita de quem olha para a proa), y cima, z sul.
 * A PROA é o norte ({@code -Z}) e o empuxo sai por ela. O controle de atitude do foguete ({@link AttitudeControl}) tem o nariz em +Y do
 * corpo; para reaproveitá-lo sem mexer, a atitude guardada é a de ESTILO FOGUETE {@code p} (nariz = proa) e a atitude dos eixos do
 * estaleiro é {@code q = p · C}, com {@code C} a rotação de +90° em torno de X ({@link #BODY_TO_ROCKET}, que leva a proa -Z ao nariz
 * +Y). A arfagem (W/S) gira em torno de X (a direita da nave), a guinada (A/D) em torno de +Y do estaleiro (cima) e a rolagem (Q/E) em
 * torno da proa; como os sinais do foguete têm outro sentido, a arfagem e a guinada entram com o sinal trocado
 * ({@link #step}): W baixa a proa, D vira a proa para a direita (leste) e E rola no sentido horário visto de dentro.
 *
 * <h2>Chão</h2>
 * Abaixo da altitude de entrada na dimensão do corpo ({@link Zones#enterAltitude}) a nave só é segurada ({@link #floor}), com a
 * velocidade para dentro zerada, quando não pode descer (tripulante em pé, ver {@link ShipDescent}). {@link #step} aplica o chão;
 * {@link #advance} não aplica, e é o que o voo do servidor usa.
 */
public final class ShipFlight {
    /** Rotação de +90° em torno de X: leva os eixos do estaleiro (proa = -Z) aos de estilo foguete (nariz = +Y). */
    public static final Quaterniond BODY_TO_ROCKET = new Quaterniond().rotationX(Math.PI / 2.0);
    private static final Quaterniond ROCKET_TO_BODY = new Quaterniond(BODY_TO_ROCKET).conjugate();

    /** Altitude da órbita inicial da nave de teste, em blocos sobre a superfície da Terra. */
    public static final double TEST_ALTITUDE = 30_000.0;

    private ShipFlight() {
    }

    /**
     * O estado de voo da nave: a órbita (com o combustível), a atitude de estilo foguete {@code p} (inercial: nariz = proa, ver a
     * documentação da classe), o giro em eixos do corpo (rad/tick), o modo do SAS e o acelerador aplicado.
     */
    public record State(FlightState orbit, Quaterniond attitude, V3 spin, SasMode sas, double throttle) {
        public State {
            attitude = new Quaterniond(attitude).normalize();
        }

        public double fuelMb() {
            return this.orbit.fuelMb();
        }

        public State withOrbit(FlightState newOrbit) {
            return new State(newOrbit, this.attitude, this.spin, this.sas, this.throttle);
        }
    }

    /** O comando de um tick: giros de -1 a 1 (os eixos do foguete: arfagem W/S, guinada A/D, rolagem Q/E), acelerador de 0 a 1 e "próximo SAS". */
    public record Command(double pitch, double yaw, double roll, double throttle, boolean nextSas) {
        public static final Command IDLE = new Command(0.0, 0.0, 0.0, 0.0, false);
    }

    // ---------------------------------------------------------------- atitude

    /** A atitude dos eixos do estaleiro (corpo → inercial): é a que o céu usa. {@code p} é a de estilo foguete. */
    public static Quaterniond bodyAttitude(Quaterniondc p) {
        return new Quaterniond(p).mul(BODY_TO_ROCKET).normalize();
    }

    /** O inverso: a atitude de estilo foguete a partir da dos eixos do estaleiro. */
    public static Quaterniond rocketAttitude(Quaterniondc q) {
        return new Quaterniond(q).mul(ROCKET_TO_BODY).normalize();
    }

    /** A proa (e o empuxo) em eixos inerciais, com a atitude {@code p} de estilo foguete. */
    public static V3 forward(Quaterniondc p) {
        return AttitudeMath.noseDirection(p);
    }

    /**
     * A atitude de estilo foguete com a proa em {@code forward} e a DIREITA da nave em {@code right} (eixos inerciais, os dois
     * unitários e perpendiculares): as colunas da atitude dos eixos do estaleiro são {@code (direita, cima, trás)} com
     * {@code cima = direita × proa} e {@code trás = −proa}.
     */
    public static Quaterniond attitudeFor(V3 forward, V3 right) {
        V3 f = forward.unit();
        V3 r = right.unit();
        V3 up = r.cross(f).unit();
        V3 back = f.negate();
        Matrix3d m = new Matrix3d(r.x(), r.y(), r.z(), up.x(), up.y(), up.z(), back.x(), back.y(), back.z());
        return rocketAttitude(new Quaterniond().setFromNormalized(m).normalize());
    }

    /** O eixo {@code axis} da nave canônica (x direita, y cima, z trás) em eixos inerciais, com a atitude {@code p} de estilo foguete. */
    public static V3 thrustDirection(Quaterniondc p, V3 axis) {
        Vector3d rocketFrame = BODY_TO_ROCKET.transform(new Vector3d(axis.x(), axis.y(), axis.z()));
        Vector3d inertial = new Quaterniond(p).transform(rocketFrame);
        return new V3(inertial.x, inertial.y, inertial.z).unit();
    }

    /**
     * A atitude dos eixos do ESTALEIRO (estaleiro para inercial) de uma nave com a proa no lado {@code bow} ({@link ShipArrangement}): é a
     * atitude canônica {@link #bodyAttitude} composta com a rotação do estaleiro para a nave canônica. É a que o céu usa; com a proa no norte
     * ({@code bow = 0}) é a de sempre.
     */
    public static Quaterniond yardAttitude(Quaterniondc p, int bow) {
        return bodyAttitude(p).mul(ShipArrangement.yardToCanonical(bow)).normalize();
    }

    // ---------------------------------------------------------------- estado inicial

    /**
     * Órbita circular em volta de {@code body}, a {@code altitude} blocos da superfície, no ponto cuja direção (unitária, eixos inerciais)
     * é {@code up}: a velocidade é a circular para o lado do polo de referência do corpo (ver {@code Orbit}: leste = inclinação 0).
     */
    public static FlightState circularOrbit(SolarSystem.Body body, double clock, V3 up, double altitude, double fuelMb) {
        BodyPhysics bp = BodyPhysics.of(body);
        V3 r = up.unit().times(bp.radiusAt(altitude));
        V3 direction = bp.pole(clock).cross(r).unit();
        return new FlightState(body, r, direction.times(bp.circularSpeed(r.length())), fuelMb, 0.0, 0.0);
    }

    /**
     * O estado inicial da nave de teste no relógio {@code clock}: órbita circular da Terra a {@link #TEST_ALTITUDE}, sobre o spawn dela (o
     * ponto do eixo +X em E, o mesmo "em cima" de {@code ShipSkyClient.testPosition}), a proa no sentido da órbita e a Terra à DIREITA
     * (a leste, pelas janelas do lado direito), SAS desligado, motor parado.
     */
    public static State testState(double clock, double fuelMb) {
        double[] up = new double[3];
        InertialFrame.toInertial(clock, 1.0, 0.0, 0.0, up);
        FlightState orbit = circularOrbit(SolarSystem.EARTH, clock, V3.of(up), TEST_ALTITUDE, fuelMb);
        V3 forward = orbit.v().unit();
        V3 right = orbit.r().unit().negate();
        return new State(orbit, attitudeFor(forward, right), V3.ZERO, SasMode.OFF, 0.0);
    }

    // ---------------------------------------------------------------- um passo

    /**
     * Um tick de voo: controle de atitude (com o SAS), empuxo pelo integrador do foguete (Tsiolkovsky exato no tick, gravidade do corpo
     * dominante em kick-drift-kick), troca de corpo pela esfera de influência e o chão da fase 1. {@code clock} é o relógio no início do tick e
     * {@code clockRate} os ticks de relógio por tick de jogo.
     */
    public static State step(State s, Command c, RocketStats stats, double clock, double clockRate) {
        return step(s, c, stats, ShipSpecs.BOW_AXIS, clock, clockRate);
    }

    /**
     * O mesmo passo com o empuxo num eixo qualquer da nave: {@code thrustAxis} (unitário) nos eixos canônicos da nave (x direita, y cima,
     * z trás; a proa é {@code -Z}), por exemplo o empuxo líquido de motores virados para lados diferentes ({@link ShipSpecs#thrustAxis}).
     * O SAS continua apontando a PROA: o que a ponte define como frente é o que ele alinha.
     */
    public static State step(State s, Command c, RocketStats stats, V3 thrustAxis, double clock, double clockRate) {
        State moved = advance(s, c, stats, thrustAxis, clock, clockRate);
        return moved.withOrbit(floor(moved.orbit()));
    }

    /**
     * O mesmo passo SEM o chão da fase 1: a órbita pode ir abaixo da altitude de entrada. Quem usa é o voo do servidor, que decide
     * ele mesmo quando a nave desce ({@link ShipDescent}) e só segura o que não pode descer.
     */
    public static State advance(State s, Command c, RocketStats stats, V3 thrustAxis, double clock, double clockRate) {
        SasMode sas = c.nextSas() ? s.sas().next() : s.sas();
        double throttle = Math.max(0.0, Math.min(1.0, Double.isNaN(c.throttle()) ? 0.0 : c.throttle()));
        FlightState orbit = s.orbit();
        // arfagem e guinada com o sinal trocado (ver a documentação da classe)
        AttitudeControl.Result att = AttitudeControl.step(s.attitude(), s.spin(), sas, -c.pitch(), -c.yaw(), c.roll(),
                AttitudeMath.sasTarget(sas, orbit.v(), orbit.r()));
        Control control = new Control(throttle, thrustDirection(att.attitude(), thrustAxis), false);
        FlightState moved = FlightIntegrator.step(orbit, control, stats, clock);
        moved = SoiTracker.update(moved, clock + clockRate, clockRate);
        // o combustível nunca passa da capacidade dos tanques que restam (um tanque quebrado leva a carga dele)
        if (moved.fuelMb() > stats.fuelCapacityMb()) {
            moved = moved.withFuel(stats.fuelCapacityMb());
        }
        return new State(moved, att.attitude(), att.spin(), sas, throttle);
    }

    /** A altitude abaixo da qual a nave é segurada na fase 1 (a de entrada na dimensão do corpo: depois daqui seria o voo de descida). */
    public static double floorAltitude(SolarSystem.Body body) {
        PlanetDefinition planet = body.planet();
        return Zones.enterAltitude(planet);
    }

    /** Segura a nave na altitude mínima (fase 1): a posição vai para o raio mínimo e a velocidade para dentro é zerada. */
    public static FlightState floor(FlightState s) {
        BodyPhysics bp = BodyPhysics.of(s.body());
        double minRadius = bp.radiusAt(floorAltitude(s.body()));
        double radius = s.r().length();
        if (radius >= minRadius) {
            return s;
        }
        V3 up = s.r().unit();
        double radial = s.v().dot(up);
        V3 v = radial < 0.0 ? s.v().minus(up.times(radial)) : s.v();
        return s.withRV(up.times(minRadius), v);
    }

    /** A nave está segura no chão da fase 1 (na altitude mínima, sem descer)? */
    public static boolean onFloor(FlightState s) {
        return s.r().length() <= BodyPhysics.of(s.body()).radiusAt(floorAltitude(s.body())) + 1.0e-6;
    }
}
