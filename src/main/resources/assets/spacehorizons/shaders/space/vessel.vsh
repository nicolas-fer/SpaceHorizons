#version 330
#extension GL_ARB_separate_shader_objects : require

// Mesmo bloco de uniforms do body.vsh (Std140Builder em VesselRenderer: mat4, mat4, vec4, vec4).
layout(std140) uniform BodyInfo {
    mat4 MvpMat;       // projeção própria (near pequeno, far grande) * rotação da câmera * modelo
    mat4 ModelMat;     // casco (blocos, relativos ao pivô) -> eixos locais da câmera, com rotação, escala e translação
    vec4 SunDirection; // xyz: direção do sol nos eixos locais da câmera, w: luz ambiente do lado sem sol
    vec4 VesselParams; // reservado
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec3 cameraRelative;

void main() {
    gl_Position = MvpMat * vec4(Position, 1.0);
    vertexColor = Color;
    // posição relativa à câmera nos eixos locais (a rotação da câmera já está no MvpMat): a normal da face sai disto
    cameraRelative = (ModelMat * vec4(Position, 1.0)).xyz;
}
