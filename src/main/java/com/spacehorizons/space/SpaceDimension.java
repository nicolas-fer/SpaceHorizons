package com.spacehorizons.space;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/**
 * A dimensão de espaço aberto ({@code spacehorizons:space}): vazia, sem nuvens nem chuva, onde a nave voa entre os
 * corpos. Aqui moram só as coordenadas: como um ponto do espaço do {@link SolarSystem} (o referencial da Terra) vira
 * um ponto da dimensão e de volta.
 *
 * <h2>Eixos</h2>
 * Os eixos da dimensão são a base local da Terra no spawn ({@code SpaceFrame.localBasis(EARTH, 0, 0)}): leste = +Z do
 * espaço, cima = +X, norte = +Y. Assim o sol, as estrelas e o resto do céu do vanilla funcionam no espaço sem
 * inclinação nem rotação extra, e uma subida vertical sobre o spawn da Terra continua vertical lá em cima. Um viés de
 * {@link #Y_BIAS} em Y mantém as coordenadas perto do centro da faixa que a dimensão aceita.
 * {@code (x, y, z) da dimensão = (sz, sx + Y_BIAS, sy)}.
 */
public final class SpaceDimension {
    public static final Identifier TYPE = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "space");
    public static final double Y_BIAS = 20_000.0;
    /** espaço = M · local. Eixos da dimensão = base local da Terra no spawn: leste=+Z, cima=+X, norte=+Y (SpaceFrame.localBasis(EARTH,0,0)). */
    public static final double[] LOCAL_TO_SPACE = {0, 1, 0, 0, 0, 1, 1, 0, 0};   // det = +1

    private SpaceDimension() {
    }

    /** Ponto (x, y, z) da dimensão de espaço → ponto no espaço do SolarSystem. out = {sx, sy, sz}. */
    public static void toSpace(double x, double y, double z, double[] out) {
        out[0] = y - Y_BIAS;
        out[1] = z;
        out[2] = x;
    }

    /** Ponto do espaço do SolarSystem → ponto (x, y, z) da dimensão de espaço. out = {x, y, z}. */
    public static void fromSpace(double sx, double sy, double sz, double[] out) {
        out[0] = sz;
        out[1] = sx + Y_BIAS;
        out[2] = sy;
    }

    /** O nível é a dimensão de espaço (pelo tipo de dimensão, como Planets.forLevel)? Servidor e cliente respondem igual. */
    public static boolean is(Level level) {
        return level.dimensionTypeRegistration().unwrapKey().filter(k -> k.identifier().equals(TYPE)).isPresent();
    }
}
