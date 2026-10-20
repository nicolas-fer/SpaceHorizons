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
layout(location = 3) in vec3 fixedPosition;

layout(location = 0) out vec4 fragColor;

// ---- Detalhe procedural da superfície (copiado em planetmesh/planet_mesh.fsh e space/body.fsh; manter os dois iguais) ----
// O mapa do planeta tem células de 320 blocos: de perto a cor interpolada some em borrão. O detalhe é ruído de valor 3D nas
// coordenadas FIXAS no corpo (em blocos), por isso não nada com a câmera, é o mesmo na malha orbital e na esfera do corpo (sem
// salto na troca de dimensão) e não tem emenda nem polo. Cada oitava some sozinha quando o pixel fica maior que o seu comprimento de
// onda (sem aliasing, e de longe o planeta volta a ser liso). Faz duas coisas: granulado de cor na terra e na água, e costa
// irregular (o limiar da máscara de água é deslocado pelo ruído, em vez do degradê reto de uma célula).

uint planetPcg(uint v) {
    uint s = v * 747796405u + 2891336453u;
    uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}

float planetHash(ivec3 c) {
    return float(planetPcg(uint(c.x) + planetPcg(uint(c.y) + planetPcg(uint(c.z))))) * (1.0 / 4294967295.0);
}

float planetValueNoise(vec3 p) {
    ivec3 i = ivec3(floor(p));
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = planetHash(i);
    float n100 = planetHash(i + ivec3(1, 0, 0));
    float n010 = planetHash(i + ivec3(0, 1, 0));
    float n110 = planetHash(i + ivec3(1, 1, 0));
    float n001 = planetHash(i + ivec3(0, 0, 1));
    float n101 = planetHash(i + ivec3(1, 0, 1));
    float n011 = planetHash(i + ivec3(0, 1, 1));
    float n111 = planetHash(i + ivec3(1, 1, 1));
    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
               mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

// Desvio (-1..1, média 0) de 5 oitavas de comprimentos de onda 800, 200, 50, 12,5 e 3 blocos. blockPos = posição fixa no corpo em
// blocos; as oitavas finas somem quando um pixel cobre mais que ~metade do comprimento de onda.
float planetDetail(vec3 blockPos) {
    vec3 p = blockPos / 800.0;
    float footprint = length(dFdx(p)) + length(dFdy(p));
    float sum = 0.0;
    float total = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        float fade = 1.0 - smoothstep(0.30, 0.90, footprint);
        sum += amp * fade * (planetValueNoise(p) * 2.0 - 1.0);
        total += amp;
        p = p * 4.0 + vec3(17.31, 9.17, 31.73);
        footprint *= 4.0;
        amp *= 0.55;
    }
    return clamp(sum / total * 1.8, -1.0, 1.0);
}

// Água rasa da malha (PlanetMeshPalette.WATER_SHALLOW): a cor de uma costa; mar aberto usa a cor do próprio vértice.
const vec3 PLANET_SHALLOW = vec3(0.247, 0.463, 0.894);

// Cor da superfície com detalhe. baseColor/waterMask = cor e alfa do vértice (alfa = fração de amostras de água). outWater = a máscara
// já irregular (para o brilho do sol).
vec3 planetSurface(vec3 baseColor, float waterMask, vec3 blockPos, out float outWater) {
    float d = planetDetail(blockPos);
    if (waterMask < 0.004) {
        outWater = 0.0;
        return clamp(baseColor * (1.0 + 0.22 * d), 0.0, 1.0);
    }
    // na costa a cor do vértice é a mistura terra/água: tira a água para achar a terra
    vec3 land = clamp((baseColor - waterMask * PLANET_SHALLOW) / max(1.0 - waterMask, 0.2), 0.0, 1.0);
    vec3 water = mix(PLANET_SHALLOW, baseColor, smoothstep(0.6, 1.0, waterMask));
    float shifted = waterMask + d * 0.16;
    float isWater = smoothstep(0.42, 0.58, shifted);
    outWater = isWater;
    return mix(clamp(land * (1.0 + 0.22 * d), 0.0, 1.0), clamp(water * (1.0 + 0.07 * d), 0.0, 1.0), isWater);
}


// Mesmo sombreamento de planetmesh/planet_mesh.fsh (sem neblina): o planeta visto de outro corpo precisa ter
// exatamente a cor da malha orbital do mesmo planeta visto de longe, senão a troca de dimensão "pisca".
void main() {
    vec3 normal = normalize(surfaceNormal);
    vec3 view = normalize(viewDirection);
    vec3 sun = SunDirection.xyz;

    float sunHeight = dot(normal, sun);
    float day = smoothstep(-0.10, 0.28, sunHeight);
    float light = mix(SunDirection.w, 1.0, day) * (0.82 + 0.18 * clamp(sunHeight, 0.0, 1.0));
    // detalhe procedural sobre a cor do mapa (células de 320 blocos) e costa irregular
    float waterMask;
    vec3 albedo = planetSurface(vertexColor.rgb, vertexColor.a, fixedPosition, waterMask);
    vec3 color = albedo * light;

    // alfa do vértice = máscara de água (ver PlanetMeshGeometry.shadeVertex); brilho do sol no oceano
    float water = smoothstep(0.5, 1.0, waterMask);
    vec3 reflected = reflect(-sun, normal);
    float glint = pow(max(dot(reflected, view), 0.0), 600.0) * day * water;
    color += vec3(1.0, 0.96, 0.85) * glint * 0.30;
    color = min(color, vec3(1.0));

    fragColor = vec4(color, BodyParams.x);
}
