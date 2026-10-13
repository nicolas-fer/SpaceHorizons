package com.spacehorizons.client.curvature;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    static final Identifier CLOUDS_VERTEX = Identifier.fromNamespaceAndPath("minecraft", "core/clouds");

    /** Bloco CloudInfo do vanilla; casado por regex para tolerar espaços e quebras de linha diferentes. */
    private static final Pattern CLOUD_INFO_BLOCK =
            Pattern.compile("layout\\(std140\\)\\s*uniform\\s+CloudInfo\\s*\\{[^}]*\\}\\s*;");
    /** Mesmos membros, na mesma ordem, que o writer (CloudRendererMixin) escreve no bloco. */
    private static final String CLOUD_INFO_BLOCK_CURVED = """
            layout(std140) uniform CloudInfo {
                vec4 CloudColor;
                vec3 CloudOffset;
                vec3 CellSize;
                float SpaceHorizons_PlanetRadius;
                float SpaceHorizons_CameraAltitude;
            };
            #define SPACEHORIZONS_CURVE_UNIFORMS_PROVIDED
            #include <spacehorizons:curvature.glsl>
            """;
    /** pos já está relativo à câmera (célula * CellSize + CloudOffset). */
    private static final String CLOUD_PROJECTION = "gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);";
    private static final String CLOUD_PROJECTION_CURVED = """
            vec3 curvedPos = spaceHorizonsCurve(pos);
                gl_Position = ProjMat * ModelViewMat * vec4(curvedPos, 1.0);""";
    private static final String CLOUD_FOG_DISTANCE = "vertexDistance = fog_spherical_distance(pos);";
    private static final String CLOUD_FOG_DISTANCE_CURVED = "vertexDistance = fog_spherical_distance(curvedPos);";

    private CurvatureShaders() {
    }

    public static boolean isCloudsVertex(Identifier id) {
        return CLOUDS_VERTEX.equals(id);
    }

    /**
     * Curva as nuvens com a mesma fórmula do terreno. Se qualquer trecho esperado não bater,
     * devolve o fonte intacto (as nuvens ficam planas, mas nada quebra).
     */
    public static String patchCloudsVertex(String source) {
        Matcher block = CLOUD_INFO_BLOCK.matcher(source);
        if (!block.find() || !source.contains(CLOUD_PROJECTION) || !source.contains(CLOUD_FOG_DISTANCE)) {
            SpaceHorizons.LOGGER.warn("clouds.vsh do vanilla mudou; a curvatura das nuvens não foi aplicada");
            return source;
        }
        String out = block.replaceFirst(Matcher.quoteReplacement(CLOUD_INFO_BLOCK_CURVED));
        out = out.replace(CLOUD_PROJECTION, CLOUD_PROJECTION_CURVED);
        return out.replace(CLOUD_FOG_DISTANCE, CLOUD_FOG_DISTANCE_CURVED);
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
