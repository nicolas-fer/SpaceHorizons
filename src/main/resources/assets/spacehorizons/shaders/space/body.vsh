#version 330
#extension GL_ARB_separate_shader_objects : require

// Layout idêntico ao escrito em BodyRenderer (Std140Builder: mat4, mat4, vec4, vec4) e ao body.fsh.
layout(std140) uniform BodyInfo {
    mat4 MvpMat;       // projeção própria (far plane grande) * rotação da câmera * modelo
    mat4 ModelMat;     // espaço do corpo (eixos do espaço, centro na origem) -> eixos locais da câmera, com escala e translação
    vec4 SunDirection; // xyz: direção do sol nos eixos locais da câmera, w: luz ambiente do lado noturno
    vec4 BodyParams;   // x: opacidade (o céu claro de dia apaga o corpo)
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec3 surfaceNormal;
layout(location = 2) out vec3 viewDirection;
layout(location = 3) out vec3 fixedPosition;

void main() {
    gl_Position = MvpMat * vec4(Position, 1.0);

    // posição relativa à câmera, nos eixos locais (a rotação da câmera já está no MvpMat)
    vec3 pos = (ModelMat * vec4(Position, 1.0)).xyz;
    vertexColor = Color;
    // numa esfera a normal é a direção radial; o relevo é pequeno demais para importar daqui
    surfaceNormal = mat3(ModelMat) * Position;
    viewDirection = -pos;
    // posição FIXA no corpo, em blocos, para o detalhe procedural (planet_detail). (z, y, x) é a ordem do planeta atual
    // (planetmesh/planet_mesh.vsh): igual nas duas, o detalhe é o mesmo vista de dentro ou de fora, sem salto na troca de dimensão
    fixedPosition = vec3(Position.z, Position.y, Position.x);
}
