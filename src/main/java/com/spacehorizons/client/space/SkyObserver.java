package com.spacehorizons.client.space;

import java.util.Optional;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.Heliocentric;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.SpaceFrame;
import com.spacehorizons.space.SunModel;
import com.spacehorizons.space.flight.BodyPhysics;

import net.minecraft.world.level.Level;

import org.joml.Matrix3d;
import org.joml.Quaterniondc;

import org.jspecify.annotations.Nullable;

/**
 * De onde se olha o céu: o chão (ou a altitude) de um planeta, com a câmera em (x, y, z) da dimensão dele, ou o espaço
 * aberto, com a câmera em (x, y, z) da dimensão {@link SpaceDimension}. É a única coisa que o {@link BodyRenderer}
 * precisa saber do lugar: todo o resto (outros corpos, sol, orientação das esferas) sai de uma posição no espaço do
 * SolarSystem e de uma matriz que leva os eixos locais da câmera (x leste, y cima, z norte) aos do espaço.
 *
 * <ul>
 *   <li>planeta: {@code espaço = R_pose · baseᵀ · local}, a base local do ponto (leste, cima, norte, nos eixos fixos no
 *       corpo) e a rotação do corpo no instante (a Lua gira com a órbita);</li>
 *   <li>espaço: a matriz constante {@link SpaceDimension#LOCAL_TO_SPACE} (os eixos da dimensão são os do spawn da Terra).</li>
 * </ul>
 *
 * Java puro (só usa {@link Level} em {@link #of}), para os testes conferirem que o modo planeta reproduz as contas de
 * {@link BodySky} e {@link SpaceFrame} e que o espaço coincide com a Terra vista do spawn (sem salto na troca).
 *
 * @param home corpo cuja dimensão é a do observador, ou null no espaço aberto
 * @param clock relógio do Overworld (ticks, com a parte fracionária do frame): tudo é do mesmo instante
 * @param pos posição do observador no espaço do SolarSystem
 * @param localToSpace matriz 3x3 (ordem de linhas): vetor nos eixos locais da câmera → eixos do espaço
 */
public record SkyObserver(SolarSystem.@Nullable Body home, double clock, double[] pos, double[] localToSpace) {
    /** Atitude identidade: a da orientação de referência da dimensão. */
    private static final Quaterniondc IDENTITY = new org.joml.Quaterniond();

    /** Observador no planeta {@code home}, com a câmera no ponto (x, y, z) da dimensão dele. */
    public static SkyObserver forPlanet(SolarSystem.Body home, double clock, double x, double y, double z) {
        double[] s = new double[3];
        SpaceFrame.toSpace(home, clock, x, y, z, s);
        double[] basis = new double[9];                         // linhas: leste, cima, norte (fixos no corpo)
        SpaceFrame.localBasis(home, x, z, basis);
        double[] r = home.poseAt(clock).rotation();
        double[] m = new double[9];                             // espaço = R_pose · baseᵀ · local
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double v = 0.0;
                for (int k = 0; k < 3; k++) {
                    v += r[i * 3 + k] * basis[j * 3 + k];
                }
                m[i * 3 + j] = v;
            }
        }
        return new SkyObserver(home, clock, s, m);
    }

    /** Observador no espaço aberto, com a câmera no ponto (x, y, z) da dimensão {@link SpaceDimension}. */
    public static SkyObserver forSpace(double clock, double x, double y, double z) {
        double[] s = new double[3];
        SpaceDimension.toSpace(x, y, z, s);
        return new SkyObserver(null, clock, s, SpaceDimension.LOCAL_TO_SPACE);
    }

    /**
     * Observador numa nave no espaço: posição absoluta {@code posE} (espaço do SolarSystem, em E) e atitude {@code attitude}, o
     * quaternion (nos eixos do espaço) que gira a nave em relação à orientação de referência da dimensão:
     * {@code localToSpace = R_atitude · SpaceDimension.LOCAL_TO_SPACE}. Com a atitude identidade é {@link #forSpace}; com outra,
     * a nave virou e todo o céu (Terra, Lua, sol, estrelas) gira ao contrário na tela. A matriz sai ortonormal (det +1).
     */
    public static SkyObserver forShip(double clock, double[] posE, Quaterniondc attitude) {
        return new SkyObserver(null, clock, posE.clone(), shipLocalToSpace(attitude));
    }

    /** {@code R_atitude · SpaceDimension.LOCAL_TO_SPACE}, matriz 3x3 em ordem de linhas. */
    public static double[] shipLocalToSpace(Quaterniondc attitude) {
        Matrix3d r = new Matrix3d().set(new org.joml.Quaterniond(attitude).normalize());
        double[] l = SpaceDimension.LOCAL_TO_SPACE;
        double[] m = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double v = 0.0;
                for (int k = 0; k < 3; k++) {
                    v += r.get(k, i) * l[k * 3 + j];     // JOML: get(coluna, linha)
                }
                m[i * 3 + j] = v;
            }
        }
        return m;
    }

    /**
     * Observador do nível: o planeta (pelo tipo de dimensão) ou o espaço aberto; vazio nos outros mundos (Nether, End) e
     * para planetas fora do SolarSystem.
     */
    public static Optional<SkyObserver> of(Level level, double x, double y, double z, double clock) {
        Optional<PlanetDefinition> planet = Planets.forLevel(level);
        if (planet.isPresent()) {
            return SolarSystem.bodyOf(planet.get()).map(body -> forPlanet(body, clock, x, y, z));
        }
        if (SpaceDimension.is(level)) {
            // câmera num foguete estacionado: o observador é a posição REAL do voo, com a atitude identidade (o céu não gira com o foguete)
            double[] ship = ShipView.positionInSpace(clock);
            // modo de teste do céu (comando skytest): a nave virtual gira; sem ele, a orientação fixa da dimensão
            Quaterniondc attitude = SkyAttitude.current();
            if (ship != null || attitude != null) {
                double[] s = ship;
                if (s == null) {
                    s = new double[3];
                    SpaceDimension.toSpace(x, y, z, s);
                }
                return Optional.of(forShip(clock, s, attitude != null ? attitude : IDENTITY));
            }
            return Optional.of(forSpace(clock, x, y, z));
        }
        if (com.spacehorizons.ship.ShipYard.is(level)) {
            // dentro de uma nave no estaleiro: observador virtual (as coordenadas do estaleiro não são as do espaço)
            return Optional.of(com.spacehorizons.client.ship.ShipSkyClient.observer(clock));
        }
        return Optional.empty();
    }

    /** Vetor do espaço → eixos locais da câmera (Mᵀ·v). {@code out} não pode ser a entrada. */
    public void spaceToLocal(double vx, double vy, double vz, double[] out) {
        double[] m = localToSpace;
        out[0] = m[0] * vx + m[3] * vy + m[6] * vz;
        out[1] = m[1] * vx + m[4] * vy + m[7] * vz;
        out[2] = m[2] * vx + m[5] * vy + m[8] * vz;
    }

    /**
     * {@code target} visto daqui: direção (nos eixos locais da câmera), distância ao CENTRO e raio angular do disco.
     * Se o alvo é o planeta do próprio observador, a direção aponta para o nadir.
     */
    public BodySky.Apparent apparent(SolarSystem.Body target) {
        BodySky.Apparent a = BodySky.fromSpacePoint(target, clock, pos[0], pos[1], pos[2]);
        double[] d = new double[3];
        spaceToLocal(a.x(), a.y(), a.z(), d);
        return new BodySky.Apparent(d[0], d[1], d[2], a.distance(), a.angularRadius());
    }

    /**
     * Eixos fixos em {@code target} (a esfera do corpo, desenhada com o mapeamento esférico) → eixos locais da câmera, matriz
     * 3x3 em ordem de linhas. É {@link SpaceFrame#meshToLocal} quando o observador está num planeta.
     */
    public void meshToLocal(SolarSystem.Body target, double[] out) {
        double[] r = target.poseAt(clock).rotation();
        double[] column = new double[3];
        for (int j = 0; j < 3; j++) {
            spaceToLocal(r[j], r[3 + j], r[6 + j], column);
            out[j] = column[0];
            out[3 + j] = column[1];
            out[6 + j] = column[2];
        }
    }

    /**
     * Direção do sol (unitária) nos eixos locais da câmera, vista DAQUI: a do SunModel, a mesma do disco, das estrelas e da luz do
     * chão. No chão de um planeta é a do corpo dele ({@link SolarSystem#sunDirectionFrom}: o sol no infinito na Terra e na Lua, a
     * direção verdadeira em Marte). No espaço aberto, perto da Terra (até a esfera de influência dela) é a mesma de sempre, para o
     * céu não saltar na troca de dimensão; mais longe a direção vai passando à verdadeira, da posição do observador ao sol (que está
     * a {@link Heliocentric#EARTH_ORBIT_RADIUS} da Terra), até valer só ela a duas esferas da Terra.
     */
    public void sun(double[] out) {
        double[] s = new double[3];
        sunInSpace(s);
        spaceToLocal(s[0], s[1], s[2], out);
    }

    /** A direção do sol vista do observador, nos eixos do espaço (ver {@link #sun}). */
    private void sunInSpace(double[] out) {
        if (home != null) {
            SolarSystem.sunDirectionFrom(home, clock, out);
            return;
        }
        SunModel.directionInSpace(clock, out);
        double distance = Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]);
        double soi = BodyPhysics.of(SolarSystem.EARTH).soiRadius();
        if (distance <= soi) {
            return;
        }
        double t = Math.min(1.0, (distance - soi) / soi);
        double w = t * t * (3.0 - 2.0 * t);
        double x = out[0] * Heliocentric.EARTH_ORBIT_RADIUS - pos[0];
        double y = out[1] * Heliocentric.EARTH_ORBIT_RADIUS - pos[1];
        double z = out[2] * Heliocentric.EARTH_ORBIT_RADIUS - pos[2];
        double n = Math.sqrt(x * x + y * y + z * z);
        double bx = (1.0 - w) * out[0] + w * x / n;
        double by = (1.0 - w) * out[1] + w * y / n;
        double bz = (1.0 - w) * out[2] + w * z / n;
        double bn = Math.sqrt(bx * bx + by * by + bz * bz);
        out[0] = bx / bn;
        out[1] = by / bn;
        out[2] = bz / bn;
    }

    /**
     * Direção do sol (unitária) a partir do centro de {@code target}, nos eixos locais da câmera: a que ilumina a esfera dele. Terra e Lua
     * usam a de sempre; Marte, a verdadeira (as fases de Marte vistas da Terra e as da Terra vistas de Marte dependem da geometria).
     */
    public void sunFor(SolarSystem.Body target, double[] out) {
        double[] s = new double[3];
        SolarSystem.sunDirectionFrom(target, clock, s);
        spaceToLocal(s[0], s[1], s[2], out);
    }

    /**
     * Rotação do campo de estrelas do vanilla (eixos do Minecraft do spawn da Terra, que é onde o vanilla o desenha com
     * {@code starAngle = SunModel.angle(clock)}) para os eixos locais da câmera: {@code Mᵀ · LOCAL_TO_SPACE}. Identidade com a
     * atitude identidade; com outra, as estrelas giram com o sol e a Terra por R⁻¹. Matriz 3x3 em ordem de linhas.
     */
    public void vanillaStarsToLocal(double[] out) {
        double[] m = localToSpace;
        double[] l = SpaceDimension.LOCAL_TO_SPACE;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double v = 0.0;
                for (int k = 0; k < 3; k++) {
                    v += m[k * 3 + i] * l[k * 3 + j];
                }
                out[i * 3 + j] = v;
            }
        }
    }
}
