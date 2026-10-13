#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform AtmosphereInfo {
    vec4 PlanetParams;
    vec4 SunParams;
    mat4 InvView;
};

layout(location = 0) in vec3 rayDir;

layout(location = 0) out vec4 fragColor;

// Amostras por metade do trecho (total = 2 * HALF_STEPS)
const int HALF_STEPS = 16;
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

// Ruído "interleaved gradient" (Jimenez): barato e bem distribuído, 0..1 por pixel
float interleavedGradientNoise(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

// Uma amostra do espalhamento: soma o que chega à câmera vindo do ponto t do raio, atenuado pelo ar entre os dois
void accumulate(float t, float dx, vec3 dir, vec3 center, float radius, vec3 sigma, vec3 sunDir,
        inout vec3 opticalDepth, inout vec3 scattered) {
    vec3 fromCenter = dir * t - center;
    float dist = length(fromCenter);
    float rho = atmosphereDensity(dist - radius);

    // luz do sol no ponto, com crepúsculo suave e um piso para o lado noturno não sumir de vez
    float sunCos = dot(fromCenter / max(dist, 1.0), sunDir);
    float lit = mix(0.25, 1.0, smoothstep(-0.2, 0.3, sunCos));

    vec3 stepDepth = sigma * rho * dx;
    opticalDepth += stepDepth * 0.5;
    scattered += exp(-opticalDepth) * stepDepth * lit;
    opticalDepth += stepDepth * 0.5;
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

    // Jitter por pixel: o erro de amostragem vira ruído fino (que o dither final esconde) em vez de faixas
    // concêntricas. As amostras ficam em duas metades do trecho, adensadas perto do ponto de maior
    // aproximação tm (onde o ar é mais denso). Todas as amostras caem DENTRO do trecho [tStart, tEnd], então
    // nenhuma sai de cena de repente quando o raio gira (o que fazia a contribuição dar degraus por elevação).
    float jitter = interleavedGradientNoise(gl_FragCoord.xy);
    float tm = clamp(tca, tStart, tEnd);

    // metade próxima da câmera, do ponto mais distante de tm até tm (t crescente, para a profundidade óptica acumular na ordem do raio)
    float nearLength = tm - tStart;
    if (nearLength > 0.0) {
        for (int i = HALF_STEPS - 1; i >= 0; i--) {
            float w = (float(i) + jitter) / float(HALF_STEPS);
            float t = tm - nearLength * w * w;
            float dx = nearLength * 2.0 * w / float(HALF_STEPS);
            accumulate(t, dx, dir, center, radius, sigma, sunDir, opticalDepth, scattered);
        }
    }
    // metade distante, de tm até a saída
    float farLength = tEnd - tm;
    if (farLength > 0.0) {
        for (int i = 0; i < HALF_STEPS; i++) {
            float w = (float(i) + jitter) / float(HALF_STEPS);
            float t = tm + farLength * w * w;
            float dx = farLength * 2.0 * w / float(HALF_STEPS);
            accumulate(t, dx, dir, center, radius, sigma, sunDir, opticalDepth, scattered);
        }
    }

    // fase de Rayleigh normalizada (média 1 na esfera)
    float mu = dot(dir, sunDir);
    float phase = 0.75 * (1.0 + mu * mu);

    vec3 color = min(scattered * phase * SunParams.w * EXPOSURE, vec3(1.0));
    // Dither de ~1 nível de 8 bits (ruído triangular, sempre >= 0 porque a mistura aditiva descarta o negativo):
    // quebra os degraus do gradiente do céu, que sem isso aparecem como faixas largas em tela de baixa resolução.
    float dither = 0.5 * (interleavedGradientNoise(gl_FragCoord.xy + 5.588238) + interleavedGradientNoise(gl_FragCoord.yx + 11.17));
    color += step(1.0e-5, color) * dither * (1.0 / 255.0);

    // saída aditiva; o alpha não importa (a mistura preserva o alpha do destino)
    fragColor = vec4(color, 1.0);
}
