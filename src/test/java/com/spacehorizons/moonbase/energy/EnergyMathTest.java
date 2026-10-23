package com.spacehorizons.moonbase.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.moonbase.energy.EnergyMath.Role;

/** Balanço de energia: geração solar, fluxo entre vizinhos, conservação e cadeias com bateria e cabo. */
class EnergyMathTest {
    /** Nó de teste que usa as regras puras. */
    private static final class Node {
        final Role role;
        final int capacity;
        int energy;

        Node(Role role, int capacity, int energy) {
            this.role = role;
            this.capacity = capacity;
            this.energy = energy;
        }
    }

    /** Um tick da cadeia: cada nó empurra para o próximo e para o anterior (como faz o block entity com os 6 vizinhos). */
    private static void tick(Node[] chain) {
        for (int i = 0; i < chain.length; i++) {
            for (int j : new int[] {i - 1, i + 1}) {
                if (j >= 0 && j < chain.length && chain[i].role != Role.CONSUMER) {
                    int moved = EnergyMath.flow(chain[i].role, chain[i].energy, chain[i].capacity, chain[j].role, chain[j].energy, chain[j].capacity);
                    chain[i].energy -= moved;
                    chain[j].energy += moved;
                }
            }
        }
    }

    private static int total(Node[] chain) {
        int sum = 0;
        for (Node n : chain) {
            sum += n.energy;
        }
        return sum;
    }

    @Test
    void solarOutputFollowsTheSunAndNeedsTheSky() {
        assertEquals(8, EnergyMath.solarOutput(1.0, true));
        assertEquals(4, EnergyMath.solarOutput(0.5, true));
        assertEquals(1, EnergyMath.solarOutput(0.06, true), "sol baixo ainda dá o mínimo de 1");
        assertEquals(0, EnergyMath.solarOutput(0.04, true), "quase no horizonte: nada");
        assertEquals(0, EnergyMath.solarOutput(-0.8, true), "noite");
        assertEquals(0, EnergyMath.solarOutput(1.0, false), "coberto");
        assertEquals(8, EnergyMath.solarOutput(5.0, true), "altura acima de 1 satura");
    }

    @Test
    void producerGivesEverythingToAnyoneButNeverReceives() {
        assertEquals(8, EnergyMath.flow(Role.PRODUCER, 8, 400, Role.STORAGE, 0, 200_000));
        assertEquals(8, EnergyMath.flow(Role.PRODUCER, 8, 400, Role.CABLE, 900, 1_000));
        assertEquals(8, EnergyMath.flow(Role.PRODUCER, 8, 400, Role.CONSUMER, 0, 4_000));
        assertEquals(0, EnergyMath.flow(Role.STORAGE, 100, 200_000, Role.PRODUCER, 0, 400), "painel não recebe");
        assertEquals(EnergyMath.TRANSFER_PER_TICK, EnergyMath.flow(Role.PRODUCER, 400, 400, Role.STORAGE, 0, 200_000), "limite por face");
    }

    @Test
    void consumersNeverGive() {
        assertEquals(0, EnergyMath.flow(Role.CONSUMER, 4_000, 4_000, Role.STORAGE, 0, 200_000));
        assertEquals(0, EnergyMath.flow(Role.CONSUMER, 4_000, 4_000, Role.CABLE, 0, 1_000));
    }

    @Test
    void nothingMovesIntoAFullNodeOrFromAnEmptyOne() {
        assertEquals(0, EnergyMath.flow(Role.PRODUCER, 8, 400, Role.STORAGE, 200_000, 200_000));
        assertEquals(0, EnergyMath.flow(Role.STORAGE, 0, 200_000, Role.CONSUMER, 0, 4_000));
        assertEquals(3, EnergyMath.flow(Role.STORAGE, 100_000, 200_000, Role.CONSUMER, 3_997, 4_000), "só o que cabe");
    }

    @Test
    void batteryFeedsMachinesAtTheFaceRateAndEqualizesWithOtherBatteries() {
        assertEquals(200, EnergyMath.flow(Role.STORAGE, 100_000, 200_000, Role.CONSUMER, 0, 4_000));
        // duas baterias: só passa de quem está mais cheia, e só até igualar
        int moved = EnergyMath.flow(Role.STORAGE, 100_000, 200_000, Role.STORAGE, 0, 200_000);
        assertEquals(200, moved, "limitado pela taxa de 200 por tick (igualar daria 50.000)");
        assertEquals(0, EnergyMath.flow(Role.STORAGE, 0, 200_000, Role.STORAGE, 100_000, 200_000), "nunca no sentido contrário");
        assertEquals(0, EnergyMath.flow(Role.STORAGE, 50_000, 200_000, Role.STORAGE, 50_000, 200_000), "iguais: nada");
        // perto de igualar, passa só metade da diferença (não ultrapassa)
        assertEquals(50, EnergyMath.flow(Role.STORAGE, 100_100, 200_000, Role.STORAGE, 100_000, 200_000));
    }

    @Test
    void chainPanelCableBatteryChargesAndConservesEnergy() {
        Node[] chain = {new Node(Role.PRODUCER, 400, 0), new Node(Role.CABLE, 1_000, 0), new Node(Role.STORAGE, 200_000, 0)};
        int generated = 0;
        for (int t = 0; t < 2_000; t++) {
            chain[0].energy = EnergyMath.add(chain[0].energy, chain[0].capacity, 8);
            generated += 8;
            tick(chain);
        }
        // tudo o que o painel gerou está em algum lugar da cadeia (o painel de 400 de capacidade esvazia nos outros)
        assertEquals(generated, total(chain));
        assertTrue(chain[2].energy > 14_000, "a bateria recebeu a maior parte: " + chain[2].energy);
    }

    @Test
    void chainBatteryPowersAMachineThroughALongCable() {
        Node[] chain = new Node[12];
        chain[0] = new Node(Role.STORAGE, 200_000, 100_000);
        for (int i = 1; i < 11; i++) {
            chain[i] = new Node(Role.CABLE, 1_000, 0);
        }
        chain[11] = new Node(Role.CONSUMER, 4_000, 0);
        int before = total(chain);
        int drawn = 0;
        for (int t = 0; t < 400; t++) {
            tick(chain);
            // a máquina consome 30 UE por tick quando pode
            int use = Math.min(30, chain[11].energy);
            chain[11].energy -= use;
            drawn += use;
        }
        assertEquals(before, total(chain) + drawn, "conservação");
        assertTrue(drawn >= 30 * 300, "a máquina trabalha quase sempre pelo cabo de 10 trechos (consumiu " + drawn + ")");
    }

    @Test
    void sumOfEnergyNeverGoesAboveCapacityOrBelowZero() {
        assertEquals(400, EnergyMath.add(395, 400, 100));
        assertEquals(0, EnergyMath.add(5, 400, -100));
        assertEquals(0, EnergyMath.space(400, 400));
        assertEquals(395, EnergyMath.space(5, 400));
    }
}
