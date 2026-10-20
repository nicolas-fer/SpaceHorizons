#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>

// Layout idêntico ao escrito em PlanetMeshRenderer (Std140Builder: mat4, vec4, vec4, vec4).
layout(std140) uniform PlanetMeshInfo {
    mat4 MvpMat;          // projeção própria (far plane grande) * rotação da câmera
    vec4 MeshOffset;      // xyz: câmera de construção - câmera atual (a malha é rígida entre reconstruções)
    vec4 SunDirection;    // xyz: direção do sol, w: luz ambiente do lado noturno
    vec4 PlanetCenter;    // xyz: centro do planeta relativo à câmera atual, w: raio
    vec4 MeshFrame;       // sen(lat), cos(lat), sen(lon), cos(lon) do ponto sob a câmera de construção (a malha é uma grade em torno dele)
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec3 surfaceNormal;
layout(location = 4) out vec3 viewDirection;
layout(location = 5) out vec3 fixedPosition;

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

    // posição FIXA no corpo (em blocos) para o detalhe procedural: a direção do vértice no referencial da câmera de construção
    // (x leste, y cima, z = +Z do mundo) girada para o referencial do mundo, como PlanetMeshProjection.terrainPoint, e depois pela
    // longitude do ponto de construção. A ordem (z, y, x) do corpo vira (sen lon·cos lat, sen lat, cos lon·cos lat).
    vec3 dir = normalize(surfaceNormal);
    float sinLat0 = MeshFrame.x;
    float cosLat0 = MeshFrame.y;
    float ux = dir.x;
    float uy = dir.y * sinLat0 + dir.z * cosLat0;
    float uz = dir.y * cosLat0 - dir.z * sinLat0;
    float sinLon0 = MeshFrame.z;
    float cosLon0 = MeshFrame.w;
    fixedPosition = PlanetCenter.w * vec3(ux * cosLon0 + uz * sinLon0, uy, uz * cosLon0 - ux * sinLon0);
}
