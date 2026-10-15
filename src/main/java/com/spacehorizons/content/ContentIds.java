package com.spacehorizons.content;

import java.util.List;

/**
 * Ids (sem namespace) de todo o conteúdo registrado. Fonte única usada pelos registros
 * ({@link ModItems}, {@link ModBlocks}) e pelo teste de recursos, que não consegue carregar os
 * registros do Minecraft. Ao registrar algo novo, adicione o id aqui.
 */
public final class ContentIds {
    // --- Materiais (etapa 1A) ---
    /** Mistura de aço: 1 lingote de ferro + 2 carvões; só funde no alto-forno. */
    public static final String STEEL_BLEND = "steel_blend";
    public static final String STEEL_INGOT = "steel_ingot";
    public static final String STEEL_NUGGET = "steel_nugget";
    public static final String STEEL_BLOCK = "steel_block";
    /** Ferramenta com durabilidade, usada em receitas e devolvida com 1 de dano. */
    public static final String FORGE_HAMMER = "forge_hammer";
    public static final String STEEL_PLATE = "steel_plate";
    public static final String BASIC_CIRCUIT = "basic_circuit";

    /** Itens que não são BlockItems. */
    public static final List<String> ITEMS = List.of(
            STEEL_BLEND, STEEL_INGOT, STEEL_NUGGET, FORGE_HAMMER, STEEL_PLATE, BASIC_CIRCUIT);

    /** Blocos (cada um tem também um BlockItem de mesmo id). */
    public static final List<String> BLOCKS = List.of(STEEL_BLOCK);

    private ContentIds() {
    }
}
