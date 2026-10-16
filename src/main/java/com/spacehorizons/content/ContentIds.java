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

    /** Galão de combustível de foguete (guarda mB num data component); receita com placas de aço. */
    public static final String FUEL_CANISTER = "fuel_canister";
    /** Refinaria: transforma carvão, biomassa etc. em combustível de foguete; receita com placas de aço e circuito. */
    public static final String REFINERY = "refinery";

    // --- Tanques e compressor de oxigênio (etapa 2) ---
    /** Tanque de oxigênio pequeno (2.400 ticks de respiração = 2 min); receita com placas de aço, cobre e vidro. */
    public static final String OXYGEN_TANK_SMALL = "oxygen_tank_small";
    /** Tanque de oxigênio grande (7.200 ticks = 6 min). */
    public static final String OXYGEN_TANK_LARGE = "oxygen_tank_large";
    /** Compressor de oxigênio: enche tanques com o ar do ambiente (só onde há ar respirável), queimando combustível de fornalha. */
    public static final String OXYGEN_COMPRESSOR = "oxygen_compressor";

    // --- Traje espacial (etapa 2A): armadura que respira dos recipientes de oxigênio e isola do frio do vácuo ---
    /** Capacete: 4 placas de aço, 1 circuito, 1 vidro (a viseira). */
    public static final String SPACE_HELMET = "space_helmet";
    /** Peitoral: 7 placas de aço e 1 circuito. */
    public static final String SPACE_CHESTPLATE = "space_chestplate";
    /** Calças: 7 placas de aço. */
    public static final String SPACE_LEGGINGS = "space_leggings";
    /** Botas: 4 placas de aço. */
    public static final String SPACE_BOOTS = "space_boots";

    /** Itens que não são BlockItems. */
    public static final List<String> ITEMS = List.of(
            STEEL_BLEND, STEEL_INGOT, STEEL_NUGGET, FORGE_HAMMER, STEEL_PLATE, BASIC_CIRCUIT, FUEL_CANISTER,
            OXYGEN_TANK_SMALL, OXYGEN_TANK_LARGE,
            SPACE_HELMET, SPACE_CHESTPLATE, SPACE_LEGGINGS, SPACE_BOOTS);

    /** Blocos (cada um tem também um BlockItem de mesmo id). */
    public static final List<String> BLOCKS = List.of(STEEL_BLOCK, REFINERY, OXYGEN_COMPRESSOR);

    private ContentIds() {
    }
}
