#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform BodyInfo {
    mat4 MvpMat;
    mat4 ModelMat;
    vec4 SunDirection;
    vec4 VesselParams;
};

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec3 cameraRelative;

layout(location = 0) out vec4 fragColor;

// Casco de outra nave ou foguete visto de longe: cor chapada por bloco, luz simples do sol único (a mesma direção do disco, dos corpos e das
// estrelas). A normal vem da própria face (derivadas da posição), virada para o lado de quem olha, então a malha não precisa de normais.
void main() {
    vec3 normal = normalize(cross(dFdx(cameraRelative), dFdy(cameraRelative)));
    vec3 toCamera = -cameraRelative;
    if (dot(normal, toCamera) < 0.0) {
        normal = -normal;
    }
    float diffuse = max(dot(normal, SunDirection.xyz), 0.0);
    float light = mix(SunDirection.w, 1.0, diffuse);
    fragColor = vec4(vertexColor.rgb * light, 1.0);
}
