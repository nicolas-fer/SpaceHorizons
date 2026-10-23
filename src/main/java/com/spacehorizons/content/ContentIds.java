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

    // --- Foguete de blocos (etapa 3a): peças montadas pelo clique no cockpit (ver com.spacehorizons.rocket) ---
    /** Plataforma de lançamento: o foguete se monta sobre ela e a deixa para trás. */
    public static final String LAUNCH_PAD = "launch_pad";
    /** Cockpit: o assento do piloto; clicar nele com a mão vazia monta o foguete. */
    public static final String ROCKET_COCKPIT = "rocket_cockpit";
    public static final String ROCKET_ENGINE = "rocket_engine";
    /** Tanque de combustível: guarda mB (4.000 no máximo); clicar com um galão cheio transfere para ele. */
    public static final String ROCKET_FUEL_TANK = "rocket_fuel_tank";
    public static final String ROCKET_PARACHUTE = "rocket_parachute";
    public static final String ROCKET_LANDING_LEG = "rocket_landing_leg";
    public static final String ROCKET_HULL = "rocket_hull";

    /** Planta de montagem: guarda um projeto de foguete e o mostra em holograma sobre a plataforma (docs/PLANO_TIERS.md, seção 4). */
    public static final String ROCKET_BLUEPRINT = "rocket_blueprint";

    // --- Naves (fase 1b, docs/PLANO_NAVES.md): blocos especiais; a nave é construída livremente com blocos no estaleiro ---
    /** Ponte de comando: o núcleo que define a nave; clicar mostra a especificação (massa, empuxo, Δv, combustível). */
    public static final String SHIP_BRIDGE = "ship_bridge";
    /** Motor de impulso: empuxo para a proa da nave (a popa é o lado onde ele aponta). */
    public static final String SHIP_IMPULSE_ENGINE = "ship_impulse_engine";
    /** Janela de vidro por onde se vê o universo. */
    public static final String SHIP_WINDOW = "ship_window";
    /** Assento do piloto: sentar nele liga o modo piloto. */
    public static final String SHIP_SEAT = "ship_seat";
    /** Eclusa: porta de uma célula que abre e fecha com o botão direito. */
    public static final String SHIP_AIRLOCK = "ship_airlock";
    /** Gerador de antigravidade: a nave precisa de um carregado para ancorar no ar (a carga se gasta ao ancorar e se recupera com o bloco no mundo). */
    public static final String ANTIGRAVITY_GENERATOR = "antigravity_generator";

    // --- Tier 2 (docs/TIER2_IDS.md, o contrato com a arte; números e receitas em docs/PLANO_TIERS.md, "Tier 2: conteúdo") ---
    /** Regolito marciano: solo de Marte; sem toque de seda dá às vezes pó de terras raras (como o cascalho dá sílex). */
    public static final String MARS_REGOLITH = "mars_regolith";
    /** Minério de titânio no regolito marciano: dá titânio bruto. */
    public static final String MARS_TITANIUM_ORE = "mars_titanium_ore";
    public static final String RAW_TITANIUM = "raw_titanium";
    public static final String TITANIUM_INGOT = "titanium_ingot";
    /** Placa de titânio: martelo de forja + 2 lingotes (como a placa de aço); a base das peças do Tier 2. */
    public static final String TITANIUM_PLATE = "titanium_plate";
    public static final String TITANIUM_BLOCK = "titanium_block";
    /** Pó de terras raras (Marte): barras nucleares, bobinas e motores do Tier 2. */
    public static final String RARE_EARTH_DUST = "rare_earth_dust";
    /** Galões dos combustíveis do Tier 2 (1.000 mB cada, como o galão de combustível). */
    public static final String HELIUM3_CANISTER = "helium3_canister";
    public static final String HYDROGEN_CANISTER = "hydrogen_canister";
    public static final String XENON_CANISTER = "xenon_canister";
    /** Barra de combustível nuclear: carregada no motor nuclear, aquece 32.000 mB de hidrogênio. */
    public static final String NUCLEAR_FUEL_ROD = "nuclear_fuel_rod";
    /** Bobina supercondutora: peça do motor de fusão. */
    public static final String SUPERCONDUCTOR_COIL = "superconductor_coil";
    /** Tanques multibloco do Tier 2 (mesma regra de coluna do tanque químico). */
    public static final String HYDROGEN_TANK = "hydrogen_tank";
    public static final String XENON_TANK = "xenon_tank";
    /** Gerador radioisotópico: a energia do motor iônico. */
    public static final String RTG_GENERATOR = "rtg_generator";
    /** Motores do Tier 2 (1x1, 2x2x2 e 3x3x3 como o motor químico). */
    public static final String NUCLEAR_ENGINE = "nuclear_engine";
    public static final String ION_ENGINE = "ion_engine";
    public static final String FUSION_ENGINE = "fusion_engine";

    // --- Base lunar (etapa 4 da sobrevivência; docs/PLANO_SOBREVIVENCIA.md, "Etapa 4: o que foi feito/decidido") ---
    /** Gelo de água dos polos da Lua (item): vira oxigênio, hidrogênio e combustível. Vem do minério {@link #LUNAR_ICE_ORE}. */
    public static final String LUNAR_ICE = "lunar_ice";
    /** Minério de gelo lunar: veios sob o fundo das crateras de latitude alta; sem toque de seda dá 1 a 3 gelos lunares. */
    public static final String LUNAR_ICE_ORE = "lunar_ice_ore";
    /** Painel solar: gera UE só com o sol alto e o céu livre. */
    public static final String SOLAR_PANEL = "solar_panel";
    /** Bateria: guarda 200.000 UE. */
    public static final String BATTERY = "battery";
    /** Cabo de energia: estende a adjacência entre painéis, baterias e máquinas. */
    public static final String POWER_CABLE = "power_cable";
    /** Gerador de oxigênio: pressuriza a sala selada em volta dele (gasta tanques ou gelo e energia). */
    public static final String OXYGEN_GENERATOR = "oxygen_generator";
    /** Extrator de hélio-3: regolito lunar vira hélio-3 no galão. */
    public static final String HELIUM3_EXTRACTOR = "helium3_extractor";
    /** Eletrolisador: gelo vira hidrogênio (galão) e oxigênio (tanque). */
    public static final String ELECTROLYZER = "electrolyzer";
    /** Refinaria lunar: gelo vira combustível químico do Tier 1 (galão). */
    public static final String LUNAR_REFINERY = "lunar_refinery";

    // --- História (etapa 5 da sobrevivência, docs/PLANO_SOBREVIVENCIA.md, "Etapa 5: a trama") ---
    /** Diário da trama: um livro por registro, com o id do registro no componente {@code journal_entry}; clicar abre a leitura. */
    public static final String JOURNAL_ENTRY = "journal_entry";
    /** Terminal de dados: clicar abre o registro do terminal e entrega a recompensa uma vez por jogador (o registro é o estado {@code record}). */
    public static final String DATA_TERMINAL = "data_terminal";

    /** Itens que não são BlockItems. */
    public static final List<String> ITEMS = List.of(
            STEEL_BLEND, STEEL_INGOT, STEEL_NUGGET, FORGE_HAMMER, STEEL_PLATE, BASIC_CIRCUIT, FUEL_CANISTER,
            OXYGEN_TANK_SMALL, OXYGEN_TANK_LARGE,
            SPACE_HELMET, SPACE_CHESTPLATE, SPACE_LEGGINGS, SPACE_BOOTS, ROCKET_BLUEPRINT,
            RAW_TITANIUM, TITANIUM_INGOT, TITANIUM_PLATE, RARE_EARTH_DUST, HELIUM3_CANISTER, HYDROGEN_CANISTER, XENON_CANISTER,
            NUCLEAR_FUEL_ROD, SUPERCONDUCTOR_COIL, LUNAR_ICE, JOURNAL_ENTRY);

    /** Blocos (cada um tem também um BlockItem de mesmo id). */
    public static final List<String> BLOCKS = List.of(STEEL_BLOCK, REFINERY, OXYGEN_COMPRESSOR,
            LAUNCH_PAD, ROCKET_COCKPIT, ROCKET_ENGINE, ROCKET_FUEL_TANK, ROCKET_PARACHUTE, ROCKET_LANDING_LEG, ROCKET_HULL,
            SHIP_BRIDGE, SHIP_IMPULSE_ENGINE, SHIP_WINDOW, SHIP_SEAT, SHIP_AIRLOCK, ANTIGRAVITY_GENERATOR,
            MARS_REGOLITH, MARS_TITANIUM_ORE, TITANIUM_BLOCK, HYDROGEN_TANK, XENON_TANK, RTG_GENERATOR, NUCLEAR_ENGINE, ION_ENGINE,
            FUSION_ENGINE, LUNAR_ICE_ORE, SOLAR_PANEL, BATTERY, POWER_CABLE, OXYGEN_GENERATOR, HELIUM3_EXTRACTOR, ELECTROLYZER,
            LUNAR_REFINERY, DATA_TERMINAL);

    private ContentIds() {
    }
}
