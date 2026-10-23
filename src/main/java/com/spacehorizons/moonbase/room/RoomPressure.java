package com.spacehorizons.moonbase.room;

/**
 * Regras puras da pressão de uma sala selada e do consumo do gerador de oxigênio (sem Minecraft). O gerador varre a sala a
 * cada {@link #SCAN_INTERVAL_TICKS} ticks e chama {@link #step} uma vez por varredura.
 *
 * Unidades: pressão de 0 a {@link #MAX_PRESSURE} (por mil); oxigênio em "ticks de respiração" (a unidade dos tanques, ver
 * {@code OxygenMath}); energia em UE.
 * <ul>
 *   <li>Sala SELADA, com energia e oxigênio no gerador: a pressão sobe {@link #FILL_PER_SCAN} por varredura (5 s até encher).
 *       O ar respirável vale a partir de {@link #BREATHABLE_MIN}.</li>
 *   <li>Manter a sala gasta oxigênio ({@link #maintenance}: 1 por 20 células por varredura, no mínimo 1) e energia
 *       ({@link #POWER_PER_SCAN}). Uma sala de 75 células gasta 3 por varredura = 6 por segundo: um tanque pequeno (2.400)
 *       dura 6,7 min e um gelo (1.200) 3,3 min.</li>
 *   <li>VAZAMENTO (porta aberta, parede quebrada) ou sala grande demais: a pressão cai {@link #LEAK_DECAY_PER_SCAN} por
 *       varredura (2,5 s até zerar) e o gerador não gasta oxigênio (o ar vai embora, não é produzido).</li>
 *   <li>Selada mas sem energia ou sem oxigênio: cai devagar ({@link #STARVE_DECAY_PER_SCAN}: 50 s), como um tanque furando.</li>
 * </ul>
 */
public final class RoomPressure {
    public static final int SCAN_INTERVAL_TICKS = 10;
    public static final int MAX_PRESSURE = 1_000;
    /** Pressão mínima para a sala contar como respirável (50%). */
    public static final int BREATHABLE_MIN = 500;
    public static final int FILL_PER_SCAN = 100;
    public static final int LEAK_DECAY_PER_SCAN = 200;
    public static final int STARVE_DECAY_PER_SCAN = 10;
    /** Células de ar por unidade de oxigênio gasta por varredura. */
    public static final int CELLS_PER_OXYGEN = 20;
    /** Energia por varredura enquanto a sala está sendo mantida (4 UE/t). */
    public static final int POWER_PER_SCAN = 40;
    /** Capacidade do reservatório interno de oxigênio do gerador (dois tanques pequenos). */
    public static final int BUFFER_CAPACITY = 4_800;

    private RoomPressure() {
    }

    /** Estado do gerador depois de uma varredura. */
    public record Result(int pressure, int oxygen, int energy) {
        public boolean breathable() {
            return pressure >= BREATHABLE_MIN;
        }
    }

    /** Oxigênio gasto por varredura para manter uma sala de {@code volume} células. */
    public static int maintenance(int volume) {
        return Math.max(1, volume / CELLS_PER_OXYGEN);
    }

    /**
     * Uma varredura.
     *
     * @param status   resultado do {@link RoomScan}
     * @param volume   células de ar da sala (só vale em SELADA)
     * @param pressure pressão atual
     * @param oxygen   oxigênio no reservatório do gerador
     * @param energy   energia no gerador
     */
    public static Result step(RoomScan.Status status, int volume, int pressure, int oxygen, int energy) {
        int p = Math.max(0, Math.min(MAX_PRESSURE, pressure));
        if (status != RoomScan.Status.SEALED) {
            return new Result(Math.max(0, p - LEAK_DECAY_PER_SCAN), oxygen, energy);
        }
        int need = maintenance(volume);
        if (energy < POWER_PER_SCAN || oxygen < need) {
            return new Result(Math.max(0, p - STARVE_DECAY_PER_SCAN), oxygen, energy);
        }
        return new Result(Math.min(MAX_PRESSURE, p + FILL_PER_SCAN), oxygen - need, energy - POWER_PER_SCAN);
    }
}
