#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>

layout(std140) uniform PlanetMeshInfo {
    mat4 MvpMat;
    vec4 MeshOffset;
    vec4 SunDirection;
    vec4 PlanetCenter;
};

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec3 surfaceNormal;
layout(location = 4) in vec3 viewDirection;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 normal = normalize(surfaceNormal);
    vec3 view = normalize(viewDirection);
    vec3 sun = SunDirection.xyz;

    float sunHeight = dot(normal, sun);
    // curva de dia parecida com a do vanilla: some um pouco antes do pôr do sol, noite = só ambiente
    float day = smoothstep(-0.10, 0.28, sunHeight);
    float light = mix(SunDirection.w, 1.0, day) * (0.82 + 0.18 * clamp(sunHeight, 0.0, 1.0));
    vec3 color = vertexColor.rgb * light;

    // O alfa do vértice é a máscara de água (0..1, fracionária na costa por causa do supersampling).
    // Brilho do sol no oceano: numa esfera lisa o reflexo é um ponto, não um halo; o expoente alto o mantém pequeno
    // e a intensidade baixa evita o "clarão" branco que estourava o centro do disco visto da órbita.
    float water = smoothstep(0.5, 1.0, vertexColor.a);
    vec3 reflected = reflect(-sun, normal);
    float glint = pow(max(dot(reflected, view), 0.0), 600.0) * day * water;
    color += vec3(1.0, 0.96, 0.85) * glint * 0.30;
    color = min(color, vec3(1.0));

    fragColor = apply_fog(vec4(color, 1.0), sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
