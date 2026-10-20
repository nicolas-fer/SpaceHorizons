package com.spacehorizons.ship;

import java.util.List;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import com.spacehorizons.space.flight.V3;

/**
 * A disposição da nave que decide para onde ela aponta e para onde os motores empurram (docs/PLANO_NAVES.md, "Motores e proa"): a PROA e
 * quantos motores de impulso há com o bocal para cada lado. Lógica pura (sem Minecraft); quem lê os blocos é {@code ShipCensusScanner}.
 *
 * <h2>Convenções</h2>
 * Os lados horizontais do estaleiro são numerados no sentido horário visto de cima: {@code 0 = norte (-Z)}, {@code 1 = leste (+X)},
 * {@code 2 = sul (+Z)}, {@code 3 = oeste (-X)}. A PROA é o lado oposto ao {@code facing} da ponte (a ponte olha para quem pilota, que olha
 * para a proa). Um motor com o bocal para o lado {@code n} empurra a nave para o lado oposto, {@code -n}.
 *
 * <p>A física ({@link ShipFlight}) roda numa nave CANÔNICA, com a proa em -Z e a direita em +X, como sempre; a disposição só diz como os
 * eixos do estaleiro se ligam aos canônicos: uma rotação em torno de Y ({@link #yaw}) leva a proa canônica à proa real. O empuxo
 * líquido ({@link #netThrust}) sai nos eixos canônicos: soma vetorial dos motores, cada um com o empuxo de {@link ShipSpecs#ENGINE_THRUST}.
 * Não há torque: o empuxo é aplicado no centro de massa (os motores de uma nave não giram a nave); ver "Limitações" do plano.
 *
 * @param bow lado da proa (0 a 3, ver acima)
 * @param nozzleNorth motores com o bocal para o norte (empurram para o sul)
 * @param nozzleEast  motores com o bocal para o leste (empurram para o oeste)
 * @param nozzleSouth motores com o bocal para o sul (empurram para o norte)
 * @param nozzleWest  motores com o bocal para o oeste (empurram para o leste)
 */
public record ShipArrangement(int bow, int nozzleNorth, int nozzleEast, int nozzleSouth, int nozzleWest) {
    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;

    /**
     * Sem informação (naves de antes desta regra, testes): proa no norte e nenhum motor contado por lado. Os motores do censo que não
     * aparecem na contagem por lado empurram pela proa (o que valia antes).
     */
    public static final ShipArrangement DEFAULT = new ShipArrangement(NORTH, 0, 0, 0, 0);

    private static final double[][] SIDE_VECTORS = {{0.0, -1.0}, {1.0, 0.0}, {0.0, 1.0}, {-1.0, 0.0}};

    public ShipArrangement {
        if (bow < 0 || bow > 3) {
            throw new IllegalArgumentException("proa fora de 0..3: " + bow);
        }
        if (nozzleNorth < 0 || nozzleEast < 0 || nozzleSouth < 0 || nozzleWest < 0) {
            throw new IllegalArgumentException("contagem de motores negativa");
        }
    }

    /** O lado (0 a 3) oposto ao dado. */
    public static int opposite(int side) {
        return (side + 2) & 3;
    }

    /** Os motores com o bocal para o lado dado. */
    public int nozzles(int side) {
        return switch (side) {
            case NORTH -> this.nozzleNorth;
            case EAST -> this.nozzleEast;
            case SOUTH -> this.nozzleSouth;
            default -> this.nozzleWest;
        };
    }

    /** Quantos motores a contagem por lado cobre. */
    public int countedEngines() {
        return this.nozzleNorth + this.nozzleEast + this.nozzleSouth + this.nozzleWest;
    }

    /** Uma disposição igual a esta, com a contagem de motores do lado dado trocada por {@code count}. */
    public ShipArrangement withNozzles(int side, int count) {
        return switch (side) {
            case NORTH -> new ShipArrangement(this.bow, count, this.nozzleEast, this.nozzleSouth, this.nozzleWest);
            case EAST -> new ShipArrangement(this.bow, this.nozzleNorth, count, this.nozzleSouth, this.nozzleWest);
            case SOUTH -> new ShipArrangement(this.bow, this.nozzleNorth, this.nozzleEast, count, this.nozzleWest);
            default -> new ShipArrangement(this.bow, this.nozzleNorth, this.nozzleEast, this.nozzleSouth, count);
        };
    }

    public ShipArrangement withBow(int newBow) {
        return new ShipArrangement(newBow, this.nozzleNorth, this.nozzleEast, this.nozzleSouth, this.nozzleWest);
    }

    /** A proa nos eixos do estaleiro (unitário horizontal: x leste, y cima, z sul). */
    public V3 bowVector() {
        return sideVector(this.bow);
    }

    /** O vetor unitário (eixos do estaleiro) do lado horizontal dado. */
    public static V3 sideVector(int side) {
        double[] v = SIDE_VECTORS[side & 3];
        return new V3(v[0], 0.0, v[1]);
    }

    /**
     * A rotação (radianos, em torno de +Y, regra da mão direita) que leva a proa da nave canônica ({@code -Z}) à proa real: 0 para o norte,
     * -90° para o leste, 180° para o sul e +90° para o oeste. Os eixos do estaleiro em canônicos: {@code Ry(-yaw)}.
     */
    public double yaw() {
        return yawOf(this.bow);
    }

    public static double yawOf(int bow) {
        return switch (bow & 3) {
            case EAST -> -Math.PI / 2.0;
            case SOUTH -> Math.PI;
            case WEST -> Math.PI / 2.0;
            default -> 0.0;
        };
    }

    /** A rotação dos eixos do estaleiro para os canônicos ({@code Ry(-yaw)}): a que o céu compõe com a atitude canônica. */
    public static Quaterniond yardToCanonical(int bow) {
        return new Quaterniond().rotationY(-yawOf(bow));
    }

    /** Um vetor dos eixos do estaleiro nos eixos canônicos da nave. */
    public V3 toCanonical(V3 yard) {
        Vector3d v = yardToCanonical(this.bow).transform(new Vector3d(yard.x(), yard.y(), yard.z()));
        return new V3(v.x, v.y, v.z);
    }

    /**
     * A soma dos empuxos unitários dos {@code engines} motores, nos eixos CANÔNICOS (módulo de 0 a {@code engines}): cada motor com o
     * bocal para {@code n} empurra para {@code -n}; os que a contagem por lado não cobre (naves sem informação) empurram pela proa
     * canônica. Mais motores contados por lado do que {@code engines} (censo antigo): vale a contagem por lado.
     */
    public V3 netThrust(int engines) {
        V3 sum = V3.ZERO;
        for (int side = 0; side < 4; side++) {
            int n = nozzles(side);
            if (n > 0) {
                sum = sum.plus(toCanonical(sideVector(opposite(side))).times(n));
            }
        }
        int uncovered = Math.max(0, engines - countedEngines());
        if (uncovered > 0) {
            sum = sum.plus(new V3(0.0, 0.0, -1.0).times(uncovered));
        }
        return sum;
    }

    /** A disposição como lista de 5 inteiros (gravação): proa e os quatro bocais N, L, S, O. */
    public List<Integer> toList() {
        return List.of(this.bow, this.nozzleNorth, this.nozzleEast, this.nozzleSouth, this.nozzleWest);
    }

    /** O inverso de {@link #toList}; lista inválida volta a {@link #DEFAULT}. */
    public static ShipArrangement fromList(List<Integer> list) {
        if (list == null || list.size() != 5) {
            return DEFAULT;
        }
        int bow = list.get(0);
        if (bow < 0 || bow > 3) {
            return DEFAULT;
        }
        return new ShipArrangement(bow, Math.max(0, list.get(1)), Math.max(0, list.get(2)), Math.max(0, list.get(3)), Math.max(0, list.get(4)));
    }
}
