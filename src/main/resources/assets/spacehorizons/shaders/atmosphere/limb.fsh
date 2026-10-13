#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform AtmosphereInfo {
    vec4 PlanetParams;
    vec4 SunParams;
    mat4 InvView;
};

layout(location = 0) in vec3 rayDir;

layout(location = 0) out vec4 fragColor;

const int STEPS = 24;
// Espalhamento relativo por canal (Rayleigh aproximado: o azul espalha mais que o vermelho)
const vec3 RAYLEIGH = vec3(0.26, 0.50, 1.0);
// Profundidade óptica no azul ao atravessar a atmosfera na vertical, no centro da curva de densidade
const float VERTICAL_OPTICAL_DEPTH = 1.0;
// Brilho geral do halo (o limbo não deve estourar para branco)
const float EXPOSURE = 0.85;

// Mesma curva de AtmosphereModel.density: 1 até a altitude base, cai a 0 na espessura
float atmosphereDensity(float altitude) {
    float k = 1.0 - clamp((altitude - PlanetParams.w) / (PlanetParams.z - PlanetParams.w), 0.0, 1.0);
    return k * k;
}

void main() {
    float radius = PlanetParams.x;
    float thickness = PlanetParams.z;
    vec3 sunDir = SunParams.xyz;

    vec3 dir = normalize(rayDir);
    // centro do planeta relativo à câmera: mesmo mapeamento da curvatura (esfera tangente abaixo da câmera)
    vec3 center = vec3(0.0, -(radius + PlanetParams.y), 0.0);
    float shellRadius = radius + thickness;

    // distância mínima do centro ao raio; o produto vetorial evita o cancelamento de dot(L,L) - tca^2 em floats
    float tca = dot(center, dir);
    float perp = length(cross(center, dir));
    if (perp >= shellRadius) {
        discard;
    }
    float halfChord = sqrt((shellRadius - perp) * (shellRadius + perp));
    float tStart = max(tca - halfChord, 0.0);
    float tEnd = tca + halfChord;
    if (tEnd <= 0.0) {
        discard;
    }
    // o planeta bloqueia o raio: o trecho termina na superfície
    if (perp < radius) {
        float tPlanet = tca - sqrt((radius - perp) * (radius + perp));
        if (tPlanet > 0.0) {
            tEnd = min(tEnd, tPlanet);
        }
    }
    if (tEnd <= tStart) {
        discard;
    }

    vec3 sigma = RAYLEIGH * (VERTICAL_OPTICAL_DEPTH / thickness);
    vec3 opticalDepth = vec3(0.0);
    vec3 scattered = vec3(0.0);

    // Amostras concentradas perto do ponto de maior aproximação (onde o ar é mais denso): x = sign(u) u^2 metade
    for (int i = 0; i < STEPS; i++) {
        float u = (float(i) + 0.5) / float(STEPS) * 2.0 - 1.0;
        float t = tca + sign(u) * u * u * halfChord;
        if (t < tStart || t > tEnd) {
            continue;
        }
        float dx = 4.0 * abs(u) * halfChord / float(STEPS);

        vec3 fromCenter = dir * t - center;
        float altitude = length(fromCenter) - radius;
        float rho = atmosphereDensity(altitude);

        // luz do sol no ponto, com crepúsculo suave e um piso para o lado noturno não sumir de vez
        float sunCos = dot(fromCenter / max(length(fromCenter), 1.0), sunDir);
        float lit = mix(0.25, 1.0, smoothstep(-0.2, 0.3, sunCos));

        vec3 stepDepth = sigma * rho * dx;
        opticalDepth += stepDepth * 0.5;
        scattered += exp(-opticalDepth) * stepDepth * lit;
        opticalDepth += stepDepth * 0.5;
    }

    // fase de Rayleigh normalizada (média 1 na esfera)
    float mu = dot(dir, sunDir);
    float phase = 0.75 * (1.0 + mu * mu);

    // saída aditiva; o alpha não importa (a mistura preserva o alpha do destino)
    fragColor = vec4(min(scattered * phase * SunParams.w * EXPOSURE, vec3(1.0)), 1.0);
}
