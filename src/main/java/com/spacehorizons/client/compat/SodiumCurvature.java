package com.spacehorizons.client.compat;

import java.nio.ByteBuffer;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.curvature.CurvatureState;

import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;

/**
 * Curvatura do terreno desenhado pelo Sodium. Só reescreve o fonte do shader de chunks dele
 * (sodium:blocks/block_layer_opaque, usado também pelas camadas cutout e translucent e pelo Iris sem shaderpack)
 * e escreve dois floats a mais no UBO u_Globals; nada de classes internas do Sodium é referenciado em compile time.
 *
 * O bloco u_Globals é uma cópia do globals.glsl do Sodium 0.9.x com os dois membros no fim. Por isso a
 * reescrita só roda em versões 0.9.x; em outras o terreno do Sodium volta a ser plano (e o log avisa), em vez de
 * arriscar um UBO com layout diferente do que o Sodium escreve.
 */
public final class SodiumCurvature {
    private static final Identifier CHUNK_SHADER = Identifier.fromNamespaceAndPath("sodium", "blocks/block_layer_opaque");

    private static final String GLOBALS_INCLUDE = "#include <sodium:globals.glsl>";
    /** Mesmos membros, na mesma ordem, que o globals.glsl do Sodium 0.9.2, mais os dois floats nossos no fim. */
    private static final String GLOBALS_BLOCK = """
            layout(std140) uniform u_Globals {
                mat4 u_ProjectionMatrix;
                mat4 u_ModelViewMatrix;

                vec4 u_FogColor;
                vec2 u_EnvironmentFog;
                vec2 u_RenderFog;

                vec2 u_TexelSize;
                vec2 u_TexCoordShrink;

                float u_FadePeriodInv;
                bool u_UseRGSS;

                float SpaceHorizons_PlanetRadius;
                float SpaceHorizons_CameraAltitude;
            };
            """;
    private static final String CURVE_INCLUDE = """
            #define SPACEHORIZONS_CURVE_UNIFORMS_PROVIDED
            #include <spacehorizons:curvature.glsl>
            """;

    /** position já está relativo à câmera (u_RegionOffset + deslocamento da seção + vértice local). */
    private static final String PROJECTION =
            "gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);";
    private static final String PROJECTION_CURVED =
            "gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(spaceHorizonsCurve(position), 1.0);";

    private static Boolean supported;

    private SodiumCurvature() {
    }

    public static boolean isChunkShader(Identifier id) {
        return CHUNK_SHADER.equals(id);
    }

    /** O Sodium está carregado em uma versão cujo u_Globals conhecemos (0.9.x). */
    public static boolean isSupported() {
        Boolean cached = supported;
        if (cached == null) {
            cached = computeSupported();
            supported = cached;
        }
        return cached;
    }

    private static boolean computeSupported() {
        String version = ModList.get().getModContainerById("sodium")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
        if (version == null) {
            return false;
        }
        boolean ok = version.startsWith("0.9.");
        if (ok) {
            SpaceHorizons.LOGGER.info("Sodium {} detectado: curvatura aplicada ao terreno do Sodium", version);
        } else {
            SpaceHorizons.LOGGER.warn("Sodium {} não é 0.9.x; o terreno do Sodium não será curvado", version);
        }
        return ok;
    }

    /**
     * Devolve o vsh com a curvatura aplicada. O bloco u_Globals é trocado sempre que o include existir, para ficar
     * idêntico ao do fsh; se só a linha de projeção não bater, o terreno fica plano mas o programa ainda linka.
     */
    public static String patchVertex(String source) {
        if (!isSupported()) {
            return source;
        }
        if (!source.contains(GLOBALS_INCLUDE)) {
            SpaceHorizons.LOGGER.warn("block_layer_opaque.vsh do Sodium mudou; a curvatura do terreno do Sodium não foi aplicada");
            return source;
        }
        String out = source.replace(GLOBALS_INCLUDE, GLOBALS_BLOCK + CURVE_INCLUDE);
        if (!out.contains(PROJECTION)) {
            SpaceHorizons.LOGGER.warn("block_layer_opaque.vsh do Sodium mudou; a projeção do terreno do Sodium não foi curvada");
            return out;
        }
        return out.replace(PROJECTION, PROJECTION_CURVED);
    }

    /**
     * Só troca o bloco de uniform, para ele bater com o do vsh (o programa não linka com blocos diferentes).
     * Sem o include esperado o vsh também fica intacto (patchVertex testa o mesmo trecho): ambos precisam concordar.
     */
    public static String patchFragment(String source) {
        if (!isSupported() || !source.contains(GLOBALS_INCLUDE)) {
            return source;
        }
        return source.replace(GLOBALS_INCLUDE, GLOBALS_BLOCK);
    }

    /**
     * Escreve os dois floats logo depois do último membro do u_Globals do Sodium (offset 184 em std140:
     * 2 mat4 + vec4 + 4 vec2 + float + bool = 184 bytes). Chamado antes de o Sodium virar o buffer (flip).
     */
    public static void writeUniforms(ByteBuffer buffer) {
        CurvatureState.Frame frame = CurvatureState.current();
        Std140Builder.intoBuffer(buffer)
                .putFloat((float) frame.radius())
                .putFloat((float) frame.cameraAltitude());
    }
}
