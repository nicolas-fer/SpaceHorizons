#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>

// Mesmo bloco do limb.fsh (o layout precisa ser idêntico nos dois estágios)
layout(std140) uniform AtmosphereInfo {
    vec4 PlanetParams; // x raio, y altitude da câmera, z espessura, w altitude base da atmosfera
    vec4 SunParams;    // xyz direção do sol (mundo, relativa à câmera), w intensidade já multiplicada pela visibilidade
    mat4 InvView;      // rotação da vista invertida (vista -> mundo relativo à câmera)
};

layout(location = 0) out vec3 rayDir;

void main() {
    // triângulo de tela cheia, como o core/screenquad do vanilla
    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    vec2 ndc = uv * 2.0 - 1.0;
    gl_Position = vec4(ndc, 0.0, 1.0);

    // Direção do raio de cada pixel. A direção é linear em ndc (w constante para z fixo), então a
    // interpolação entre os 3 vértices é exata; z = 0.5 está dentro do frustum em qualquer convenção de profundidade.
    vec4 viewPoint = inverse(ProjMat) * vec4(ndc, 0.5, 1.0);
    rayDir = mat3(InvView) * (viewPoint.xyz / viewPoint.w);
}
