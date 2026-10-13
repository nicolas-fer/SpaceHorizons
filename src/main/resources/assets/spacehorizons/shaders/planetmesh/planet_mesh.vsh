#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>

// Layout idêntico ao escrito em PlanetMeshRenderer (Std140Builder: mat4, vec4, vec4, vec4).
layout(std140) uniform PlanetMeshInfo {
    mat4 MvpMat;          // projeção própria (far plane grande) * rotação da câmera
    vec4 MeshOffset;      // xyz: câmera de construção - câmera atual (a malha é rígida entre reconstruções)
    vec4 SunDirection;    // xyz: direção do sol, w: luz ambiente do lado noturno
    vec4 PlanetCenter;    // xyz: centro do planeta relativo à câmera atual, w: raio
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec3 surfaceNormal;
layout(location = 4) out vec3 viewDirection;

void main() {
    // Position já vem curvada (CurvatureMath) e relativa à câmera de construção
    vec3 pos = Position + MeshOffset.xyz;
    gl_Position = MvpMat * vec4(pos, 1.0);

    // mesma bruma do terreno, calculada com a distância real até o vértice
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);

    vertexColor = Color;
    // numa esfera a normal é a direção radial; o relevo é pequeno demais para importar daqui
    surfaceNormal = pos - PlanetCenter.xyz;
    viewDirection = -pos;
}
