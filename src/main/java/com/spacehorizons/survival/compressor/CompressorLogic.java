package com.spacehorizons.survival.compressor;

import java.util.Optional;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.survival.oxygen.OxygenAmounts;
import com.spacehorizons.survival.oxygen.OxygenMath;

/**
 * Lógica pura do compressor de oxigênio (sem itens nem mundo): decide, a cada tick, se o compressor trabalha, quanto
 * ar entra no tanque e quando acende um item de combustível. O block entity monta as {@link Inputs} a partir do tanque,
 * do slot de combustível e da posição do bloco, chama {@link #tick} e aplica o {@link Result}.
 *
 * Balanceamento (unidade: ticks de respiração, o consumo do jogador é 1 por tick):
 * <ul>
 *   <li>{@link #FILL_PER_TICK} = 12 por tick de funcionamento: o tanque pequeno (2.400) enche em 200 ticks = 10 s e o
 *       grande (7.200) em 600 ticks = 30 s. Rápido o bastante para não virar espera, devagar o bastante para encher
 *       um tanque ser uma ação com início e fim (e o tanque ficar um tempo na máquina).</li>
 *   <li>O combustível só queima enquanto o compressor está de fato comprimindo (diferente da fornalha, que queima até o
 *       fim mesmo parada): um carvão (1.600 ticks de queima) rende 1.600 x 12 = 19.200 de oxigênio, 8 tanques
 *       pequenos ou 16 minutos de respiração. O combustível é barato de propósito: o gargalo da progressão é o aço
 *       dos tanques e o combustível de foguete, não o ar.</li>
 *   <li>Só funciona em ar respirável ({@link #isBreathable}): no vácuo (Lua, ou acima da atmosfera da Terra) não há
 *       o que comprimir.</li>
 * </ul>
 */
public final class CompressorLogic {
    /** Oxigênio que entra no tanque por tick de funcionamento (240 por segundo). */
    public static final int FILL_PER_TICK = 12;

    /** Situação do compressor, na ordem de prioridade com que é decidida (e na ordem dos ids enviados à tela). */
    public enum Status {
        /** Ambiente sem ar respirável: nada a comprimir. */
        NO_AIR,
        /** Sem tanque (ou item que não guarda oxigênio) no slot. */
        NO_TANK,
        /** Tanque cheio: terminou. */
        FULL,
        /** Há tanque com espaço e ar, mas falta combustível. */
        NO_FUEL,
        /** Comprimindo neste tick. */
        WORKING;

        /** Id para o {@code ContainerData}; ids fora da faixa viram {@link #NO_TANK}. */
        public static Status byId(int id) {
            Status[] all = values();
            return id >= 0 && id < all.length ? all[id] : NO_TANK;
        }
    }

    private int litTime;
    private int litTotal;
    private Status status = Status.NO_TANK;

    /**
     * Situação dos slots e do ambiente neste tick.
     *
     * @param breathable   o ar em volta do bloco é respirável
     * @param hasTank      o slot tem um item que guarda oxigênio
     * @param amount       carga atual do tanque, em ticks de respiração
     * @param capacity     capacidade do tanque
     * @param fuelBurnTime tempo de queima do item do slot de combustível (0 se vazio ou se não queima); só é lido
     *                     quando o compressor precisa acender
     */
    public record Inputs(boolean breathable, boolean hasTank, int amount, int capacity, int fuelBurnTime) {
    }

    /**
     * O que o block entity precisa aplicar depois do tick.
     *
     * @param fill            oxigênio a pôr no tanque neste tick
     * @param consumeFuelItem gastar um item do slot de combustível (acendeu)
     * @param changed         o estado de queima ou a situação mudou (salvar e atualizar o bloco)
     * @param status          situação neste tick
     */
    public record Result(int fill, boolean consumeFuelItem, boolean changed, Status status) {
        /** O bloco aparece aceso só enquanto comprime. */
        public boolean lit() {
            return status == Status.WORKING;
        }
    }

    public int litTime() {
        return litTime;
    }

    public int litTotal() {
        return litTotal;
    }

    public Status status() {
        return status;
    }

    /** Restaura o estado salvo (valores inválidos são ajustados aos limites). */
    public void load(int litTime, int litTotal) {
        this.litTime = Math.max(0, litTime);
        this.litTotal = Math.max(0, litTotal);
    }

    /** Avança um tick. */
    public Result tick(Inputs in) {
        Status previous = status;
        int space = in.hasTank() ? Math.max(0, in.capacity() - OxygenAmounts.clamp(in.amount(), in.capacity())) : 0;

        Status now;
        if (!in.breathable()) {
            now = Status.NO_AIR;
        } else if (!in.hasTank()) {
            now = Status.NO_TANK;
        } else if (space <= 0) {
            now = Status.FULL;
        } else if (litTime <= 0 && in.fuelBurnTime() <= 0) {
            now = Status.NO_FUEL;
        } else {
            now = Status.WORKING;
        }

        int fill = 0;
        boolean consumeFuel = false;
        if (now == Status.WORKING) {
            if (litTime <= 0) {
                litTime = in.fuelBurnTime();
                litTotal = in.fuelBurnTime();
                consumeFuel = true;
            }
            litTime--;
            // no último tick só entra o que falta (a taxa não faz o tanque passar da capacidade)
            fill = Math.min(FILL_PER_TICK, space);
        }
        status = now;
        return new Result(fill, consumeFuel, consumeFuel || now != previous, now);
    }

    /** Ticks de funcionamento para encher um tanque vazio de {@code capacity}. */
    public static int ticksToFill(int capacity) {
        return Math.max(0, (capacity + FILL_PER_TICK - 1) / FILL_PER_TICK);
    }

    /**
     * O ar no ponto do bloco é respirável? É o mesmo critério de vácuo do jogador ({@link OxygenMath#isVacuum}), contado
     * a partir do centro do bloco. Fora dos planetas conhecidos (Nether, End) o ar é sempre respirável, como no sistema
     * de oxigênio do jogador.
     */
    public static boolean isBreathable(Optional<PlanetDefinition> planet, int blockY) {
        return planet.map(p -> !OxygenMath.isVacuum(p, blockY + 0.5)).orElse(true);
    }
}
