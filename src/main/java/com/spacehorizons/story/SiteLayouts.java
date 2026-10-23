package com.spacehorizons.story;

import com.spacehorizons.story.StoryEntry.Site;

/**
 * Os desenhos dos três lugares da trama (ver {@link SiteLayout} para as coordenadas). São blocos do vanilla e do mod: nenhum arquivo NBT.
 *
 * <ul>
 *   <li><b>Observatório Aurora</b> (Terra, 17 x 15): salão de tijolos de pedra rachados, com buracos no teto e vidros quebrados, o terminal no fundo,
 *   um baú com os dois diários de Helena Voss, estantes e teias; ao lado, a antena parabólica enferrujada (cobre oxidado) e um mastro.</li>
 *   <li><b>Base Selene-1</b> (Lua, 23 x 17): módulo de controle de casco de foguete com o terminal e o baú dos diários de Reyes, corredor, módulo de
 *   laboratório quase todo rompido e a plataforma de lançamento onde o foguete da expedição pousou, com restos dele.</li>
 *   <li><b>Destroços da sonda Vigia-3</b> (Lua, 17 x 17): pátio chamuscado, um fragmento do casco com o terminal, o baú dos diários, o RTG e o prato
 *   da antena caído.</li>
 * </ul>
 */
public final class SiteLayouts {
    private static final String HULL = "spacehorizons:rocket_hull";
    private static final String STEEL = "spacehorizons:steel_block";
    private static final String AIR = "minecraft:air";

    public static final SiteLayout EARTH_STATION = earthStation();
    public static final SiteLayout MOON_BASE = moonBase();
    public static final SiteLayout MOON_PROBE = moonProbe();

    private SiteLayouts() {
    }

    public static SiteLayout of(Site site) {
        return switch (site) {
            case EARTH_STATION -> EARTH_STATION;
            case MOON_BASE -> MOON_BASE;
            case MOON_PROBE -> MOON_PROBE;
        };
    }

    private static SiteLayout earthStation() {
        String bricks = "minecraft:stone_bricks";
        String cracked = "minecraft:cracked_stone_bricks";
        String copper = "minecraft:oxidized_copper";
        SiteLayout l = new SiteLayout("earth_station", 17, 15, 10, "minecraft:cobblestone");
        // piso
        l.mix(0, 0, 0, 16, 0, 14, bricks, cracked, 30, 1);
        // salão (x 1..11, z 2..12): paredes, interior limpo, teto com buracos e uma abertura grande
        l.mix(1, 1, 2, 11, 5, 12, bricks, cracked, 25, 2);
        l.air(2, 1, 3, 10, 5, 11);
        l.mix(1, 6, 2, 11, 6, 12, bricks, AIR, 15, 3);
        l.air(4, 6, 5, 6, 6, 7);
        // porta ao sul e janelas quebradas
        l.air(5, 1, 12, 7, 3, 12);
        l.mix(1, 3, 4, 1, 4, 10, "minecraft:glass_pane", AIR, 55, 4);
        l.mix(11, 3, 4, 11, 4, 10, "minecraft:glass_pane", AIR, 55, 5);
        l.mix(3, 3, 2, 9, 4, 2, "minecraft:glass_pane", AIR, 55, 6);
        l.mix(2, 3, 12, 3, 4, 12, "minecraft:glass_pane", AIR, 55, 7);
        l.mix(9, 3, 12, 10, 4, 12, "minecraft:glass_pane", AIR, 55, 8);
        // interior: o terminal no fundo, o baú dos diários, a estante e a mesa
        l.terminal(6, 1, 3, "south", "aurora");
        l.set(5, 1, 3, "minecraft:lectern[facing=south]");
        l.set(7, 1, 3, "minecraft:crafting_table");
        l.chest(9, 1, 3, "south", "story/earth_station");
        l.set(3, 1, 3, "minecraft:barrel[facing=up]");
        l.set(3, 2, 3, "minecraft:barrel[facing=up]");
        l.mix(10, 1, 5, 10, 3, 10, "minecraft:bookshelf", AIR, 35, 9);
        l.mix(3, 1, 6, 8, 1, 9, "minecraft:gray_carpet", AIR, 25, 10);
        l.set(2, 5, 3, "minecraft:cobweb");
        l.set(10, 5, 3, "minecraft:cobweb");
        l.set(2, 5, 11, "minecraft:cobweb");
        l.set(10, 5, 11, "minecraft:cobweb");
        // antena parabólica de cobre oxidado ao lado e um mastro
        l.box(14, 1, 3, 14, 6, 3, copper);
        l.box(12, 7, 1, 16, 7, 5, copper);
        l.air(12, 7, 1, 12, 7, 1);
        l.air(16, 7, 1, 16, 7, 1);
        l.air(12, 7, 5, 12, 7, 5);
        l.air(16, 7, 5, 16, 7, 5);
        l.set(14, 8, 3, "minecraft:lightning_rod[facing=up]");
        l.box(15, 1, 10, 15, 5, 10, "minecraft:iron_bars");
        l.set(15, 6, 10, "minecraft:lightning_rod[facing=up]");
        return l;
    }

    private static SiteLayout moonBase() {
        SiteLayout l = new SiteLayout("moon_base", 23, 17, 8, "minecraft:stone");
        // apron de aço
        l.mix(0, 0, 0, 22, 0, 16, STEEL, "minecraft:polished_andesite", 25, 11);
        // módulo A, o controle (x 1..9, z 2..10): casco, interior, teto com rombo, entrada ao sul, janelas
        l.mix(1, 1, 2, 9, 4, 10, HULL, STEEL, 20, 12);
        l.air(2, 1, 3, 8, 4, 9);
        l.mix(1, 5, 2, 9, 5, 10, HULL, AIR, 12, 13);
        l.air(6, 5, 6, 7, 5, 7);
        l.air(4, 1, 10, 5, 2, 10);
        l.mix(3, 2, 2, 7, 3, 2, "spacehorizons:ship_window", AIR, 45, 14);
        l.mix(1, 2, 4, 1, 3, 8, "spacehorizons:ship_window", AIR, 45, 15);
        // interior do controle: o terminal, o baú dos diários, estante e fornalha
        l.terminal(5, 1, 3, "south", "selene");
        l.chest(2, 1, 3, "south", "story/moon_base");
        l.set(7, 1, 3, "minecraft:lectern[facing=south]");
        l.set(8, 1, 3, "minecraft:barrel[facing=up]");
        l.set(2, 1, 8, "minecraft:blast_furnace[facing=east]");
        l.mix(3, 1, 5, 7, 1, 8, "minecraft:light_gray_carpet", AIR, 25, 16);
        // corredor para o módulo B
        l.box(10, 1, 4, 11, 3, 7, HULL);
        l.air(10, 1, 5, 11, 2, 6);
        l.air(9, 1, 5, 9, 2, 6);
        // módulo B, o laboratório, quase todo rompido (x 12..20, z 2..8)
        l.mix(12, 1, 2, 20, 4, 8, HULL, AIR, 40, 17);
        l.air(13, 1, 3, 19, 4, 7);
        l.air(12, 1, 5, 12, 2, 6);
        l.mix(12, 5, 2, 20, 5, 8, HULL, AIR, 60, 18);
        l.set(14, 1, 4, "minecraft:crafting_table");
        l.set(18, 1, 6, "minecraft:iron_block");
        l.set(16, 1, 3, "minecraft:barrel[facing=up]");
        l.set(18, 1, 3, "minecraft:barrel[facing=up]");
        // a plataforma de lançamento onde o foguete da expedição pousou, com restos dele
        l.box(17, 0, 11, 19, 0, 13, "spacehorizons:launch_pad");
        l.set(21, 1, 12, "spacehorizons:rocket_engine");
        l.set(21, 1, 13, HULL);
        l.set(15, 1, 14, "spacehorizons:rocket_landing_leg");
        return l;
    }

    private static SiteLayout moonProbe() {
        SiteLayout l = new SiteLayout("moon_probe", 17, 17, 8, "minecraft:stone");
        // pátio chamuscado
        l.mix(0, 0, 0, 16, 0, 16, "minecraft:smooth_basalt", "minecraft:blackstone", 40, 21);
        // fragmento do casco (x 6..10, z 6..10): paredes, interior, teto quebrado, abertura a leste, uma janela
        l.mix(6, 1, 6, 10, 4, 10, HULL, "minecraft:iron_block", 20, 22);
        l.air(7, 1, 7, 9, 3, 9);
        l.mix(6, 5, 6, 10, 5, 10, HULL, AIR, 30, 23);
        l.air(10, 1, 8, 10, 2, 8);
        l.set(8, 3, 6, "spacehorizons:ship_window");
        // dentro: o terminal, o baú dos diários e o RTG que ainda alimenta a antena
        l.terminal(8, 1, 7, "south", "vigia");
        l.chest(9, 1, 7, "south", "story/moon_probe");
        l.set(7, 1, 9, "spacehorizons:rtg_generator");
        // o prato da antena caído e destroços
        l.mix(1, 1, 2, 4, 1, 5, "minecraft:oxidized_copper", AIR, 35, 24);
        l.set(5, 1, 4, "minecraft:lightning_rod[facing=east]");
        l.set(12, 1, 3, "minecraft:iron_block");
        l.set(13, 1, 12, "minecraft:raw_iron_block");
        l.set(3, 1, 12, "minecraft:black_concrete");
        l.set(4, 1, 13, "minecraft:iron_bars");
        return l;
    }
}
