package com.spacehorizons.client.curvature;

import java.util.Set;
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
    private static final String CLOUD_CURVE_INCLUDE = """
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

    // ---- Entidades, block entities, partículas, itens no mundo, texto no mundo, contornos, rachaduras ----

    static final Identifier DYNAMIC_TRANSFORMS_INCLUDE = Identifier.fromNamespaceAndPath("minecraft", "dynamictransforms.glsl");
    /** Último membro do bloco DynamicTransforms do vanilla. */
    private static final String DYNAMIC_TRANSFORMS_LAST_MEMBER = "vec3 ModelOffset;";
    /** Mesmos membros, na mesma ordem, que o writer (DynamicTransformMixin) escreve no bloco. */
    private static final String DYNAMIC_TRANSFORMS_LAST_MEMBER_CURVED = """
            vec3 ModelOffset;
                float SpaceHorizons_PlanetRadius;
                float SpaceHorizons_CameraAltitude;""";

    /**
     * Passagens de mundo cuja posição de vértice já é relativa à câmera (sem rotação), como a do terreno.
     * Ficam de fora de propósito: terrain (tem o próprio patch), clouds (idem), sky/stars/position* (céu e GUI),
     * gui*, panorama, world_border (quads enormes: curvar só os vértices não vale), debug_point.
     */
    private static final Set<Identifier> WORLD_VERTEX_SHADERS = Set.of(
            core("entity"),
            core("item"),
            core("particle"),
            core("block"),
            core("text"),
            core("glint"),
            core("rendertype_entity_shadow"),
            core("rendertype_leash"),
            core("rendertype_crumbling"),
            core("rendertype_lightning"),
            core("rendertype_beacon_beam"),
            core("rendertype_lines"),
            core("rendertype_outline"),
            core("rendertype_end_portal"),
            core("rendertype_water_mask"));

    private static final Pattern MAIN_FUNCTION = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)");
    /** Projeção do vértice: Position (entity, item, ...), Position + Normal (linhas) ou pos (block). */
    private static final Pattern WORLD_PROJECTION = Pattern.compile(
            "ModelViewMat\\s*\\*\\s*vec4\\(\\s*(Position(?:\\s*\\+\\s*Normal)?|pos)\\s*,\\s*1\\.0\\s*\\)");

    /**
     * Funções injetadas antes do main. Ortográfica (GUI, itens de inventário, entidades em PIP) nunca curva.
     * Perto da câmera a curvatura some (0 a 8 blocos, cheia a partir de 48): a mão em primeira pessoa e o modelo
     * do jogador em terceira pessoa ficam intactos a qualquer altitude. Sem isso, a fórmula (que mapeia altitude
     * absoluta sobre a esfera) esticaria em xz tudo que está na altura da câmera por (1 + altitude/R). A 48 blocos
     * o deslocamento do chão é d²/2R = 0,1 bloco, então a costura com o terreno curvado é invisível.
     */
    private static final String WORLD_CURVE_FUNCTIONS = """
            #define SPACEHORIZONS_CURVE_UNIFORMS_PROVIDED
            #include <spacehorizons:curvature.glsl>
            vec3 spaceHorizonsWorldCurve(vec3 pos) {
                if (ProjMat[3][3] != 0.0) {
                    return pos;
                }
                return mix(pos, spaceHorizonsCurve(pos), smoothstep(8.0, 48.0, length(pos)));
            }

            """;

    /** Resultado da última tentativa de ajustar o dynamictransforms.glsl; os vsh só são curvados se deu certo. */
    private static volatile boolean dynamicTransformsPatched = true; // otimista: o include pode carregar depois do vsh

    private CurvatureShaders() {
    }

    private static Identifier core(String name) {
        return Identifier.fromNamespaceAndPath("minecraft", "core/" + name);
    }

    /** Ajusta o include dynamictransforms.glsl (os demais passam intactos). */
    public static String patchInclude(Identifier id, String source) {
        if (!DYNAMIC_TRANSFORMS_INCLUDE.equals(id)) {
            return source;
        }
        if (!source.contains(DYNAMIC_TRANSFORMS_LAST_MEMBER)) {
            dynamicTransformsPatched = false;
            SpaceHorizons.LOGGER.warn("dynamictransforms.glsl do vanilla mudou; entidades e partículas não serão curvadas");
            return source;
        }
        dynamicTransformsPatched = true;
        return source.replace(DYNAMIC_TRANSFORMS_LAST_MEMBER, DYNAMIC_TRANSFORMS_LAST_MEMBER_CURVED);
    }

    public static boolean isWorldVertex(Identifier id) {
        return WORLD_VERTEX_SHADERS.contains(id);
    }

    /**
     * Curva a projeção do vsh de uma passagem de mundo. Se o include não foi ajustado ou o trecho esperado não
     * bate, devolve o fonte intacto (a passagem fica plana, mas nada quebra).
     */
    public static String patchWorldVertex(String source) {
        if (!dynamicTransformsPatched || !source.contains("#include <minecraft:dynamictransforms.glsl>")
                || !source.contains("#include <minecraft:projection.glsl>")) {
            return source;
        }
        Matcher main = MAIN_FUNCTION.matcher(source);
        if (!main.find() || !WORLD_PROJECTION.matcher(source).find()) {
            SpaceHorizons.LOGGER.warn("um vsh de mundo do vanilla mudou; a curvatura dele não foi aplicada");
            return source;
        }
        String out = source.substring(0, main.start()) + WORLD_CURVE_FUNCTIONS + source.substring(main.start());
        return WORLD_PROJECTION.matcher(out).replaceAll("ModelViewMat * vec4(spaceHorizonsWorldCurve($1), 1.0)");
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
        // O clouds.vsh já inclui dynamictransforms.glsl, que agora carrega os dois floats: o CloudInfo fica como
        // está (declarar de novo daria "member already has a name at global scope"). Só sem o include ajustado
        // (dynamictransforms do vanilla mudou) o bloco próprio, escrito pelo CloudRendererMixin, entra em ação.
        String replacement = dynamicTransformsPatched && source.contains("#include <minecraft:dynamictransforms.glsl>")
                ? block.group() + System.lineSeparator() + CLOUD_CURVE_INCLUDE
                : CLOUD_INFO_BLOCK_CURVED;
        String out = block.replaceFirst(Matcher.quoteReplacement(replacement));
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
