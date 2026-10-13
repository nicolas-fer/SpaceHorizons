#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform BodyInfo {
    mat4 MvpMat;
    mat4 ModelMat;
    vec4 SunDirection;
    vec4 BodyParams;
};

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec3 surfaceNormal;
layout(location = 2) in vec3 viewDirection;

layout(location = 0) out vec4 fragColor;

// Mesmo sombreamento de planetmesh/planet_mesh.fsh (sem neblina): o planeta visto de outro corpo precisa ter
// exatamente a cor da malha orbital do mesmo planeta visto de longe, senão a troca de dimensão "pisca".
void main() {
    vec3 normal = normalize(surfaceNormal);
    vec3 view = normalize(viewDirection);
    vec3 sun = SunDirection.xyz;

    float sunHeight = dot(normal, sun);
    float day = smoothstep(-0.10, 0.28, sunHeight);
    float light = mix(SunDirection.w, 1.0, day) * (0.82 + 0.18 * clamp(sunHeight, 0.0, 1.0));
    vec3 color = vertexColor.rgb * light;

    // alfa do vértice = máscara de água (ver PlanetMeshGeometry.shadeVertex); brilho do sol no oceano
    float water = smoothstep(0.5, 1.0, vertexColor.a);
    vec3 reflected = reflect(-sun, normal);
    float glint = pow(max(dot(reflected, view), 0.0), 600.0) * day * water;
    color += vec3(1.0, 0.96, 0.85) * glint * 0.30;
    color = min(color, vec3(1.0));

    fragColor = vec4(color, BodyParams.x);
}
