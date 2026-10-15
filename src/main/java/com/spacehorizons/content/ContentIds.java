package com.spacehorizons.content;

import java.util.List;

/**
 * Ids (sem namespace) de todo o conteúdo registrado. Fonte única usada pelos registros
 * ({@link ModItems}, {@link ModBlocks}) e pelo teste de recursos, que não consegue carregar os
 * registros do Minecraft. Ao registrar algo novo, adicione o id aqui.
 */
public final class ContentIds {
    /** PROVISÓRIO (etapa 0): sai na etapa 1 junto com o conteúdo de teste. */
    public static final String PLACEHOLDER_ITEM = "placeholder_item";
    /** PROVISÓRIO (etapa 0): sai na etapa 1 junto com o conteúdo de teste. */
    public static final String PLACEHOLDER_BLOCK = "placeholder_block";

    /**
     * Galão de combustível de foguete (guarda mB num data component).
     * TODO (materiais da etapa 1): a receita (data/spacehorizons/recipe/fuel_canister.json) usa ferro e pepita; trocar
     * por placas de aço.
     */
    public static final String FUEL_CANISTER = "fuel_canister";
    /**
     * Refinaria: transforma carvão, biomassa etc. em combustível de foguete.
     * TODO (materiais da etapa 1): a receita (data/spacehorizons/recipe/refinery.json) usa ferro, vidro e redstone
     * provisoriamente; trocar ferro por aço (carcaça) e redstone por circuito.
     */
    public static final String REFINERY = "refinery";

    /** Itens que não são BlockItems. */
    public static final List<String> ITEMS = List.of(PLACEHOLDER_ITEM, FUEL_CANISTER);

    /** Blocos (cada um tem também um BlockItem de mesmo id). */
    public static final List<String> BLOCKS = List.of(PLACEHOLDER_BLOCK, REFINERY);

    private ContentIds() {
    }
}
