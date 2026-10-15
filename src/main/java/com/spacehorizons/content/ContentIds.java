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

    /** Itens que não são BlockItems. */
    public static final List<String> ITEMS = List.of(PLACEHOLDER_ITEM);

    /** Blocos (cada um tem também um BlockItem de mesmo id). */
    public static final List<String> BLOCKS = List.of(PLACEHOLDER_BLOCK);

    private ContentIds() {
    }
}
