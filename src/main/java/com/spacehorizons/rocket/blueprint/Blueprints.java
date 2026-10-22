package com.spacehorizons.rocket.blueprint;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.rocket.RocketBlockKind;

/**
 * As plantas embutidas (docs/PLANO_TIERS.md, seção 4, item 3). São os projetos que o simulador da missão JÁ voou ({@code MissionBudgetTest}):
 * o destino que o painel promete para cada uma o simulador cumpre.
 *
 * <ul>
 *   <li>{@link #SUBORBITAL}: motor 1x1, coluna 1x1x8, cockpit e paraquedas (salto suborbital: TWR 1,1).</li>
 *   <li>{@link #ORBITAL}: motor 2x2x2, coluna 1x1x4, cockpit e paraquedas (chega à órbita).</li>
 *   <li>{@link #LUNAR}: camada 3x3 de motores, coluna 3x3x5, cockpit, paraquedas e 8 cascos (Lua, pouso e volta). <b>Gancho de história</b>
 *   ({@code docs/PLANO_SOBREVIVENCIA.md}, etapa 5): esta planta deve ser ACHADA num terminal de uma base abandonada na Lua. Até a história
 *   existir ela é {@link Entry#locked() trancada} em sobrevivência (o menu a mostra trancada) e livre no criativo (aba e menu); quando a base
 *   existir, o terminal entrega o item com esta planta ({@code RocketBlueprintItem.stackOf(Blueprints.LUNAR)}).</li>
 * </ul>
 *
 * Cada planta inclui a base de plataformas (a pegada do foguete mais uma borda de 1), com a plataforma-âncora em 0, 0, 0.
 */
public final class Blueprints {
    public static final String PAD = "spacehorizons:launch_pad";
    public static final String ENGINE = "spacehorizons:rocket_engine";
    public static final String TANK = "spacehorizons:rocket_fuel_tank";
    public static final String COCKPIT = "spacehorizons:rocket_cockpit";
    public static final String PARACHUTE = "spacehorizons:rocket_parachute";
    public static final String HULL = "spacehorizons:rocket_hull";

    /** Uma planta embutida: o id (também a chave do nome, {@code blueprint.spacehorizons.<id>}) e se está trancada em sobrevivência. */
    public record Entry(String id, Blueprint blueprint, boolean locked) {
    }

    public static final Blueprint SUBORBITAL = design("suborbital", 1, 1, 8, 1, 0);
    public static final Blueprint ORBITAL = design("orbital", 2, 1, 4, 1, 0);
    public static final Blueprint LUNAR = design("lunar", 0, 3, 5, 3, 8);

    public static final List<Entry> ALL = List.of(new Entry("suborbital", SUBORBITAL, false), new Entry("orbital", ORBITAL, false),
            new Entry("lunar", LUNAR, true));

    private Blueprints() {
    }

    public static @Nullable Entry byId(String id) {
        for (Entry e : ALL) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    /**
     * O projeto padrão (o mesmo de {@code TestWorld.design} nos testes): motor de lado {@code engineSide} (1: um bloco; 2 e 3: cubo cheio;
     * 0: camada 3x3 de motores avulsos), coluna de tanques {@code tankW x tankH x tankD} em cima, cockpit no topo da coluna, paraquedas em cima
     * do cockpit e {@code hulls} cascos em placas 3x3 acima dele; a base de plataformas cobre a pegada mais uma borda de 1.
     */
    static Blueprint design(String id, int engineSide, int tankW, int tankH, int tankD, int hulls) {
        Blueprint.Builder b = Blueprint.builder(id, "", "");
        int engineW = engineSide == 0 ? 3 : engineSide;
        int engineH = Math.max(1, engineSide);
        int footprint = Math.max(engineW, Math.max(tankW, tankD));
        b.fill(-1, 0, -1, footprint + 2, 1, footprint + 2, PAD, RocketBlockKind.PAD);
        b.fill(0, 1, 0, engineW, engineH, engineW, ENGINE, RocketBlockKind.ENGINE);
        int tankY = 1 + engineH;
        b.fill(0, tankY, 0, tankW, tankH, tankD, TANK, RocketBlockKind.FUEL_TANK);
        int cockpitY = tankY + tankH;
        b.add(0, cockpitY, 0, COCKPIT, RocketBlockKind.COCKPIT);
        int y = cockpitY + 1;
        b.add(0, y++, 0, PARACHUTE, RocketBlockKind.PARACHUTE);
        int left = hulls;
        for (; left > 0; y++) {
            for (int i = 0; i < 9 && left > 0; i++, left--) {
                b.add(-1 + i % 3, y, -1 + i / 3, HULL, RocketBlockKind.HULL);
            }
        }
        return b.build();
    }
}
