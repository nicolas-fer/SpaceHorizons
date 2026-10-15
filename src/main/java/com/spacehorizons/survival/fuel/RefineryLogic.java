package com.spacehorizons.survival.fuel;

/**
 * Lógica pura da refinaria (sem itens nem mundo): tanque interno em mB, queima do combustível de fornalha e progresso
 * do processamento. O block entity monta as {@link Inputs} a partir dos slots e das receitas, chama {@link #tick} e
 * aplica o {@link Result} (consumir combustível e entrada, acender ou apagar o bloco).
 *
 * O comportamento segue o da fornalha: queima enquanto há entrada processável e espaço no tanque; se faltar
 * combustível ou espaço, o progresso recua 2 por tick; ao acabar a queima acende o próximo item na hora (sem piscar).
 */
public final class RefineryLogic {
    /** Capacidade do tanque interno, em mB (quatro galões). */
    public static final int TANK_CAPACITY = 4000;
    /** Quanto sai do tanque para o galão por tick (um galão vazio enche em 5 s). */
    public static final int TRANSFER_PER_TICK = 10;

    private int tank;
    private int litTime;
    private int litTotal;
    private int progress;
    private int processTotal;

    /**
     * Situação dos slots neste tick.
     *
     * @param hasRecipe     há item de entrada com receita de refino
     * @param recipeMb      combustível que a receita produz, em mB
     * @param recipeTime    duração do processamento, em ticks
     * @param fuelBurnTime  tempo de queima do item do slot de combustível de fornalha (0 se vazio ou se não queima);
     *                      só é lido quando a refinaria precisa acender
     */
    public record Inputs(boolean hasRecipe, int recipeMb, int recipeTime, int fuelBurnTime) {
        public static final Inputs NONE = new Inputs(false, 0, 0, 0);
    }

    /** O que o block entity precisa aplicar depois do tick. */
    public record Result(boolean consumeFuelItem, boolean consumeInputItem, boolean changed, boolean lit) {
    }

    public int tank() {
        return tank;
    }

    public int litTime() {
        return litTime;
    }

    public int litTotal() {
        return litTotal;
    }

    public int progress() {
        return progress;
    }

    public int processTotal() {
        return processTotal;
    }

    public boolean isLit() {
        return litTime > 0;
    }

    /** Restaura o estado salvo (valores inválidos são ajustados aos limites). */
    public void load(int tank, int litTime, int litTotal, int progress, int processTotal) {
        this.tank = Math.max(0, Math.min(TANK_CAPACITY, tank));
        this.litTime = Math.max(0, litTime);
        this.litTotal = Math.max(0, litTotal);
        this.progress = Math.max(0, progress);
        this.processTotal = Math.max(0, processTotal);
    }

    /** Coloca combustível no tanque; devolve quanto foi aceito (o resto não cabe). */
    public int fill(int mb) {
        int accepted = Math.max(0, Math.min(mb, TANK_CAPACITY - tank));
        tank += accepted;
        return accepted;
    }

    /** Tira combustível do tanque; devolve quanto saiu. */
    public int drain(int mb) {
        int taken = Math.max(0, Math.min(mb, tank));
        tank -= taken;
        return taken;
    }

    /**
     * Passa combustível do tanque para um galão com {@code canisterMb} dentro; devolve quanto foi transferido neste
     * tick (o chamador soma ao galão). Respeita a taxa, o que há no tanque e o espaço do galão.
     */
    public int transferToCanister(int canisterMb) {
        int moved = Math.min(TRANSFER_PER_TICK, Math.min(tank, CanisterFuel.space(canisterMb)));
        if (moved <= 0) {
            return 0;
        }
        tank -= moved;
        return moved;
    }

    /** Avança um tick. */
    public Result tick(Inputs in) {
        boolean wasLit = litTime > 0;
        if (litTime > 0) {
            litTime--;
        }
        boolean consumeFuel = false;
        boolean consumeInput = false;
        boolean changed = false;

        boolean canWork = in.hasRecipe() && in.recipeMb() > 0 && tank + in.recipeMb() <= TANK_CAPACITY;
        if (canWork) {
            if (processTotal != in.recipeTime()) {
                // entrada trocada por uma receita de outra duração: recomeça
                processTotal = in.recipeTime();
                progress = 0;
            }
            if (litTime <= 0 && in.fuelBurnTime() > 0) {
                litTime = in.fuelBurnTime();
                litTotal = in.fuelBurnTime();
                consumeFuel = true;
                changed = true;
            }
            if (litTime > 0) {
                progress++;
                if (progress >= Math.max(1, processTotal)) {
                    progress = 0;
                    tank += in.recipeMb();
                    consumeInput = true;
                    changed = true;
                }
            } else if (progress > 0) {
                progress = Math.max(0, progress - 2);
            }
        } else if (progress > 0) {
            progress = Math.max(0, progress - 2);
        }

        boolean lit = litTime > 0;
        if (lit != wasLit) {
            changed = true;
        }
        return new Result(consumeFuel, consumeInput, changed, lit);
    }
}
