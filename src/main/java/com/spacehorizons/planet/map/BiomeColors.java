package com.spacehorizons.planet.map;

import java.util.Optional;

import com.spacehorizons.planet.SurfaceType;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/**
 * Cor média vista de longe e tipo aproximado de superfície de um bioma vanilla. Roda no servidor (o bake), por
 * isso NÃO usa Biome.getGrassColor/getFoliageColor, que dependem do colormap carregado só no cliente: a vegetação
 * vem de uma aproximação própria do gradiente clima (temperatura x chuva) do vanilla, e os overrides do bioma
 * (cor de grama/folhagem/água definidas no JSON) são respeitados.
 */
public final class BiomeColors {
    private static final int SNOW = 0xF2F6F8;
    private static final int STONE = 0x8A8A8A;
    private static final int SAND = 0xDDD0A0;

    private BiomeColors() {
    }

    /** O bioma é de água (oceano ou rio). */
    public static boolean isWaterBiome(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER);
    }

    /** Cor 0xRRGGBB da água do bioma (para o mapa; a malha orbital usa a própria paleta de água com a profundidade). */
    public static int waterColor(Holder<Biome> biome) {
        return biome.value().getWaterColor() & 0xFFFFFF;
    }

    /** Tipo aproximado da superfície em terra (água é decidida pelas alturas, não pelo bioma). */
    public static SurfaceType surfaceType(Holder<Biome> biome) {
        String path = path(biome);
        if (biome.is(BiomeTags.IS_BEACH) && !isSnowy(path) && !path.contains("stony")) {
            return SurfaceType.BEACH;
        }
        if (isSnowy(path)) {
            return SurfaceType.SNOW;
        }
        if (path.contains("desert") || biome.is(BiomeTags.IS_BADLANDS)) {
            return SurfaceType.DESERT;
        }
        if (isRock(path) || biome.is(BiomeTags.IS_MOUNTAIN)) {
            return SurfaceType.MOUNTAIN;
        }
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)
                || path.contains("swamp") || path.contains("mangrove")) {
            return SurfaceType.FOREST;
        }
        return SurfaceType.PLAINS;
    }

    /** Cor média de uma coluna de terra do bioma, vista de longe (0xRRGGBB). */
    public static int landColor(Holder<Biome> biome) {
        String path = path(biome);
        Biome value = biome.value();
        float temperature = clamp01(value.getModifiedClimateSettings().temperature());
        float downfall = clamp01(value.getModifiedClimateSettings().downfall());
        Optional<Integer> grassOverride = value.getModifiedSpecialEffects().grassColorOverride();
        Optional<Integer> foliageOverride = value.getModifiedSpecialEffects().foliageColorOverride();

        if (path.contains("mushroom")) {
            return 0x6F5F70;
        }
        if (path.contains("cherry")) {
            return 0xD9A3BD;
        }
        if (path.contains("pale_garden")) {
            return 0x80887A;
        }
        if (path.contains("mangrove")) {
            return 0x4F6B3A;
        }
        if (path.contains("swamp")) {
            return 0x4E6B3A;
        }
        if (path.contains("dark_forest")) {
            return 0x2E5A20;
        }
        if (biome.is(BiomeTags.IS_BEACH)) {
            if (path.contains("snowy")) {
                return 0xE8EEF0;
            }
            return path.contains("stony") ? 0x8A8A86 : SAND;
        }
        if (isSnowy(path)) {
            if (path.contains("taiga")) {
                return 0xC8D8D8;
            }
            if (path.contains("grove")) {
                return 0xE0ECEE;
            }
            return path.contains("ice_spikes") ? 0xDCE8F2 : SNOW;
        }
        if (path.contains("desert")) {
            return 0xE0D090;
        }
        if (biome.is(BiomeTags.IS_BADLANDS)) {
            return path.contains("wooded") ? 0x9A7048 : 0xB5683A;
        }
        if (path.contains("stony_peaks") || path.contains("stony_shore")) {
            return 0x8A8782;
        }
        if (path.contains("windswept")) {
            // meio rocha, meio vegetação
            return mix(grassColor(temperature, downfall, grassOverride), STONE, 0.5);
        }
        if (biome.is(BiomeTags.IS_JUNGLE)) {
            return scale(foliageColor(temperature, downfall, foliageOverride), 0.85);
        }
        if (biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_FOREST)) {
            return scale(foliageColor(temperature, downfall, foliageOverride), 0.85);
        }
        if (biome.is(BiomeTags.IS_SAVANNA)) {
            return scale(grassColor(temperature, downfall, grassOverride), 0.95);
        }
        return grassColor(temperature, downfall, grassOverride);
    }

    private static boolean isSnowy(String path) {
        return path.contains("snowy") || path.contains("frozen") || path.contains("ice_spikes")
                || path.contains("grove") || path.contains("jagged");
    }

    private static boolean isRock(String path) {
        return path.contains("stony") || path.contains("windswept") || path.contains("peaks");
    }

    private static String path(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
    }

    /**
     * Gradiente de grama do vanilla aproximado pelos cantos do colormap: frio 0x80B497, quente e seco 0xBFB755,
     * quente e úmido 0x59C93C (a chuva pesa pela temperatura, como no vanilla).
     */
    static int grassColor(float temperature, float downfall, Optional<Integer> override) {
        if (override.isPresent()) {
            return override.get() & 0xFFFFFF;
        }
        return gradient(temperature, downfall, 0x80B497, 0xBFB755, 0x59C93C);
    }

    /** Folhagem: frio 0x60A87A, quente e seco 0xAEA42A, quente e úmido 0x30BB0B. */
    static int foliageColor(float temperature, float downfall, Optional<Integer> override) {
        if (override.isPresent()) {
            return override.get() & 0xFFFFFF;
        }
        return gradient(temperature, downfall, 0x60A87A, 0xAEA42A, 0x30BB0B);
    }

    private static int gradient(float temperature, float downfall, int cold, int hotDry, int hotWet) {
        double rain = downfall * temperature;
        int warm = mix(hotDry, hotWet, rain);
        return mix(cold, warm, temperature);
    }

    private static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int g = (int) Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | g << 8 | bl;
    }

    static int scale(int rgb, double factor) {
        int r = (int) Math.round((rgb >> 16 & 0xFF) * factor);
        int g = (int) Math.round((rgb >> 8 & 0xFF) * factor);
        int b = (int) Math.round((rgb & 0xFF) * factor);
        return r << 16 | g << 8 | b;
    }
}
