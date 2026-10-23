package com.spacehorizons.planet.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.TerrainStyle;
import com.spacehorizons.planet.atmosphere.AtmosphereModel;

import net.minecraft.resources.Identifier;

/** Formato dos parâmetros de planeta: os três planetas do jar carregam, a Terra e a Lua não mudaram, o inválido é recusado. */
class PlanetSpecTest {
    private static final Identifier TEST_ID = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "teste");

    private static JsonObject json(String planet) throws IOException {
        String path = "data/" + SpaceHorizons.MODID + "/planet/" + planet + ".json";
        try (InputStream in = PlanetSpecTest.class.getClassLoader().getResourceAsStream(path)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** Lê o JSON de um planeta do jar, aplica a mudança e tenta carregar. */
    private static PlanetSpec parseModified(String planet, Consumer<JsonObject> change) throws IOException {
        JsonObject root = json(planet);
        change.accept(root);
        return PlanetSpec.parse(TEST_ID, root.toString());
    }

    private static void assertRefused(String planet, Consumer<JsonObject> change) {
        assertThrows(IllegalArgumentException.class, () -> parseModified(planet, change));
    }

    private static JsonObject relief(JsonObject root) {
        return root.getAsJsonObject("terrain").getAsJsonObject("relief");
    }

    @Test
    void theCatalogLoadsEarthMoonAndMars() {
        assertEquals(3, Planets.ALL.size());
        for (PlanetDefinition planet : Planets.ALL) {
            assertEquals(planet, Planets.spec(planet).definition());
            assertEquals(planet, Planets.byId(planet.id()).orElseThrow());
        }
        assertEquals("spacehorizons:mars", Planets.MARS.id().toString());
    }

    @Test
    void earthKeepsItsOriginalParameters() {
        PlanetDefinition earth = Planets.EARTH;
        assertEquals(0x5EAC0FFEEL, earth.seed());
        assertEquals(655_360, earth.circumferenceBlocks());
        assertEquals(63, earth.seaLevel());
        assertEquals(70.0, earth.maxLatitudeDegrees());
        assertEquals(96, earth.terrainAmplitude());
        assertEquals(TerrainStyle.EARTH, earth.style());
        assertEquals(1.0, earth.surfaceGravity());
        assertTrue(earth.hasOcean());
        AtmosphereModel air = AtmosphereModel.of(earth);
        assertEquals(AtmosphereModel.EARTH_THICKNESS, air.thickness());
        assertEquals(AtmosphereModel.EARTH_BASE_ALTITUDE, air.baseAltitude());
        assertTrue(air.present() && air.breathable());
        assertEquals(new AtmosphereModel(AtmosphereModel.EARTH_THICKNESS, AtmosphereModel.EARTH_BASE_ALTITUDE), air);
        assertEquals(SurfaceType.EARTH_TYPES, Planets.spec(earth).surface().types().keySet());
    }

    @Test
    void moonHasNoAirOnlyTheIceVeinAndAFourTypePalette() {
        PlanetSpec moon = Planets.spec(Planets.MOON);
        assertEquals(AtmosphereModel.NONE, moon.atmosphere().model());
        assertEquals(1, moon.ores().size());
        assertEquals("spacehorizons:lunar_ice_ore", moon.ores().get(0).block().get(0).toString());
        assertEquals(4, moon.surface().types().size());
        assertEquals("minecraft:stone", moon.surface().subsurface().get(0).toString());
    }

    @Test
    void aBlockChoiceCanBeASingleIdOrAFallbackList() {
        PlanetSpec mars = Planets.spec(Planets.MARS);
        assertEquals(2, mars.surface().types().get(SurfaceType.REGOLITH).top().size());
        assertEquals("spacehorizons:mars_regolith", mars.surface().types().get(SurfaceType.REGOLITH).top().get(0).toString());
        assertEquals(1, mars.surface().types().get(SurfaceType.MARE).top().size());
    }

    @Test
    void unmodifiedFilesParse() throws IOException {
        for (String planet : new String[] {"earth", "moon", "mars"}) {
            PlanetSpec spec = parseModified(planet, root -> { });
            assertEquals(TEST_ID, spec.id());
        }
    }

    @Test
    void invalidWorldParametersAreRefused() {
        assertRefused("mars", root -> root.addProperty("circumference", 348_161));
        assertRefused("mars", root -> root.addProperty("circumference", -16));
        assertRefused("mars", root -> root.addProperty("gravity", 0.0));
        assertRefused("mars", root -> root.addProperty("max_latitude", 95.0));
        assertRefused("mars", root -> root.addProperty("day_length_ticks", 10));
        assertRefused("mars", root -> root.addProperty("sea_level", 400));
        assertRefused("mars", root -> root.remove("seed"));
        assertRefused("mars", root -> root.addProperty("style", "gasoso"));
        assertRefused("mars", root -> root.getAsJsonObject("terrain").addProperty("amplitude", 0));
    }

    @Test
    void invalidTerrainParametersAreRefused() {
        assertRefused("mars", root -> relief(root).getAsJsonObject("lowlands").addProperty("edge1", -1.0));
        assertRefused("mars", root -> relief(root).getAsJsonObject("highlands").addProperty("octaves", 0));
        assertRefused("mars", root -> relief(root).getAsJsonObject("craters").getAsJsonArray("scales").get(0)
                .getAsJsonObject().addProperty("probability", 1.5));
        // influência maior que uma célula cortaria as crateras na borda da célula
        assertRefused("mars", root -> relief(root).getAsJsonObject("craters").addProperty("radius_span", 0.6));
        assertRefused("mars", root -> relief(root).getAsJsonObject("volcanoes").addProperty("radius_min", 0.45));
        assertRefused("mars", root -> relief(root).getAsJsonObject("polar").addProperty("cap_type", "forest"));
        assertRefused("mars", root -> relief(root).addProperty("max_height", 400));
        assertRefused("mars", root -> relief(root).addProperty("max_height", 50));
        // estilo e relevo precisam combinar
        assertRefused("mars", root -> root.getAsJsonObject("terrain").remove("relief"));
        assertRefused("earth", root -> root.getAsJsonObject("terrain").add("relief", relief(jsonUnchecked("mars"))));
    }

    @Test
    void incoherentPaletteWaterAndAtmosphereAreRefused() {
        // o relevo de Marte produz vulcões: a paleta precisa do papel
        assertRefused("mars", root -> root.getAsJsonObject("surface").getAsJsonObject("types").remove("volcanic"));
        assertRefused("earth", root -> root.getAsJsonObject("surface").getAsJsonObject("types").remove("forest"));
        assertRefused("mars", root -> root.getAsJsonObject("surface").getAsJsonObject("types").getAsJsonObject("mare")
                .addProperty("color", "#zz0000"));
        assertRefused("mars", root -> root.getAsJsonObject("surface").getAsJsonObject("types").getAsJsonObject("mare")
                .add("top", new JsonArray()));
        assertRefused("mars", root -> root.getAsJsonObject("water").addProperty("ocean", true));
        assertRefused("earth", root -> root.getAsJsonObject("water").addProperty("ocean", false));
        assertRefused("mars", root -> root.getAsJsonObject("atmosphere").remove("scatter"));
        assertRefused("mars", root -> root.getAsJsonObject("atmosphere").addProperty("thickness", 50.0));
        assertRefused("moon", root -> root.getAsJsonObject("atmosphere").addProperty("thickness", 1000.0));
        assertRefused("earth", root -> root.add("ores", jsonUnchecked("mars").getAsJsonArray("ores")));
        assertRefused("mars", root -> root.getAsJsonArray("ores").get(0).getAsJsonObject().addProperty("min_y", 200));
    }

    @Test
    void anotherPlanetCanBeDescribedByParametersAlone() throws IOException {
        // um planeta novo é só JSON: Marte com outra semente, outro tamanho, sem vulcões e com calota de rocha
        PlanetSpec spec = parseModified("mars", root -> {
            root.addProperty("seed", 1234);
            root.addProperty("circumference", 98_304);
            relief(root).remove("volcanoes");
            relief(root).getAsJsonObject("polar").addProperty("cap_type", "highland");
            root.getAsJsonObject("surface").getAsJsonObject("types").remove("volcanic");
            root.getAsJsonObject("surface").getAsJsonObject("types").remove("polar_ice");
        });
        assertEquals(98_304, spec.definition().circumferenceBlocks());
        assertFalse(spec.terrain().relief().orElseThrow().producedTypes().contains(SurfaceType.VOLCANIC));
        var terrain = new com.spacehorizons.planet.terrain.ParametricTerrain(spec.definition(), spec.terrain(), spec.surface());
        int h = terrain.surfaceHeight(100, 200);
        assertEquals(h, terrain.surfaceHeight(100 + 98_304, 200));
    }

    private static JsonObject jsonUnchecked(String planet) {
        try {
            return json(planet);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
