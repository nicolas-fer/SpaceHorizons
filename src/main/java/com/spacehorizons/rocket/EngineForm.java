package com.spacehorizons.rocket;

import net.minecraft.util.StringRepresentable;

/**
 * Como um bloco {@code rocket_engine} se apresenta ({@link RocketEngineBlock#FORM}), conforme a estrutura multibloco que ele forma
 * ({@link Multiblocks}). O nome é o do blockstate ({@code blockstates/rocket_engine.json}).
 */
public enum EngineForm implements StringRepresentable {
    /** Motor 1x1 avulso: o modelo normal. */
    SOLO("solo", Multiblocks.EngineRole.SOLO),
    /** Mestre de um motor 2x2x2: o canto mínimo, que desenha o modelo do conjunto ({@code rocket_engine_2x2}). */
    MASTER_2("master_2", Multiblocks.EngineRole.MASTER_2),
    /** Mestre de um motor 3x3x3: o bloco central, que desenha o modelo do conjunto ({@code rocket_engine_3x3}). */
    MASTER_3("master_3", Multiblocks.EngineRole.MASTER_3),
    /** Qualquer outro bloco de um motor formado: invisível ({@code rocket_engine_part}). */
    PART("part", Multiblocks.EngineRole.PART);

    private final String name;
    private final Multiblocks.EngineRole role;

    EngineForm(String name, Multiblocks.EngineRole role) {
        this.name = name;
        this.role = role;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public Multiblocks.EngineRole role() {
        return this.role;
    }

    public static EngineForm of(Multiblocks.EngineRole role) {
        for (EngineForm form : values()) {
            if (form.role == role) {
                return form;
            }
        }
        return SOLO;
    }
}
