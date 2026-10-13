// Projeção esférica exata. Espelho de com.spacehorizons.planet.CurvatureMath: mudar um exige mudar o outro.
// pos: posição relativa à câmera (mundo plano). Retorna a posição relativa à câmera no mundo curvo.
uniform float SpaceHorizons_PlanetRadius;    // <= 0 desliga a curvatura
uniform float SpaceHorizons_CameraAltitude;  // altitude da câmera sobre o nível do mar, em blocos

vec3 spaceHorizonsCurve(vec3 pos) {
    if (SpaceHorizons_PlanetRadius <= 0.0) {
        return pos;
    }
    float dist = length(pos.xz);
    if (dist < 1.0e-4) {
        return pos;
    }
    float R = SpaceHorizons_PlanetRadius;
    float theta = dist / R;
    float h = pos.y + SpaceHorizons_CameraAltitude;
    float halfSin = sin(theta * 0.5);
    vec2 xz = pos.xz * ((R + h) * sin(theta) / dist);
    float y = h * cos(theta) - 2.0 * R * halfSin * halfSin - SpaceHorizons_CameraAltitude;
    return vec3(xz.x, y, xz.y);
}
