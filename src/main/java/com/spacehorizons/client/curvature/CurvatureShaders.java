package com.spacehorizons.client.curvature;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.resources.Identifier;

/**
 * Reescreve o código-fonte do terrain.vsh e do terrain.fsh do vanilla na hora em que o ShaderManager os entrega.
 * Não há override de arquivo: outros mods e resource packs continuam funcionando, e se o vanilla
 * mudar o trecho esperado a reescrita vira no-op (a curvatura some, mas nada quebra).
 *
 * O bloco TerrainUniform precisa ser idêntico nos dois estágios; senão o programa não linka.
 * Por isso o bloco é trocado nos dois, mesmo quando a projeção do vsh não bate.
 */
public final class CurvatureShaders {
    static final Identifier TERRAIN_VERTEX = Identifier.fromNamespaceAndPath("minecraft", "core/terrain");

    private static final String TERRAIN_GLOBALS_INCLUDE = "#include <minecraft:terrainglobals.glsl>";
    /** Mesmos membros, na mesma ordem, que o writer (TerrainTransform) escreve no bloco. */
    private static final String TERRAIN_UNIFORM_BLOCK = """
            layout(std140) uniform TerrainUniform {
                mat4 ModelViewMat;
                ivec2 TextureSize;
                float SpaceHorizons_PlanetRadius;
                float SpaceHorizons_CameraAltitude;
            };
            """;
    private static final String TERRAIN_CURVE_INCLUDE = """
            #define SPACEHORIZONS_CURVE_UNIFORMS_PROVIDED
            #include <spacehorizons:curvature.glsl>
            """;

    /** pos já está relativo à câmera (Position + ChunkPosition - CameraBlockPos + CameraOffset). */
    private static final String TERRAIN_PROJECTION = "gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);";
    private static final String TERRAIN_PROJECTION_CURVED =
            "gl_Position = ProjMat * ModelViewMat * vec4(spaceHorizonsCurve(pos), 1.0);";

    private CurvatureShaders() {
    }

    public static boolean isTerrainVertex(Identifier id) {
        return TERRAIN_VERTEX.equals(id);
    }

    /** Devolve o vsh com a curvatura aplicada, ou o próprio fonte se o trecho não bater. */
    public static String patchTerrainVertex(String source) {
        if (!source.contains(TERRAIN_GLOBALS_INCLUDE)) {
            SpaceHorizons.LOGGER.warn("terrain.vsh do vanilla mudou; a curvatura dos chunks não foi aplicada");
            return source;
        }
        String out = source.replace(TERRAIN_GLOBALS_INCLUDE, TERRAIN_UNIFORM_BLOCK + TERRAIN_CURVE_INCLUDE);
        if (!out.contains(TERRAIN_PROJECTION)) {
            SpaceHorizons.LOGGER.warn("terrain.vsh do vanilla mudou; a projeção dos chunks não foi curvada");
            return out;
        }
        return out.replace(TERRAIN_PROJECTION, TERRAIN_PROJECTION_CURVED);
    }

    /**
     * Só troca o bloco de uniform, para ele bater com o do vsh. Sem isso o vsh tem 4 membros e o fsh tem 2,
     * e o programa de terreno não linka. Não recebe a curvatura: o fsh não projeta nada.
     */
    public static String patchTerrainFragment(String source) {
        if (!source.contains(TERRAIN_GLOBALS_INCLUDE)) {
            SpaceHorizons.LOGGER.warn("terrain.fsh do vanilla mudou; o bloco TerrainUniform não foi igualado ao do vsh");
            return source;
        }
        return source.replace(TERRAIN_GLOBALS_INCLUDE, TERRAIN_UNIFORM_BLOCK);
    }
}
