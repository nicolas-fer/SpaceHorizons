package com.spacehorizons.space;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/**
 * A dimensão de espaço aberto ({@code spacehorizons:space}) e como suas coordenadas se relacionam com o referencial do
 * espaço ({@link SpaceFrame}). Os eixos da dimensão coincidem com a base local da Terra no spawn (leste, cima, norte),
 * mais um viés de {@link #Y_BIAS} em Y: a conta cabe nos limites de altura (±2.032) e de posição de entidade, e o sol e
 * as estrelas do vanilla funcionam sem inclinação nem rotação extra.
 */
public final class SpaceDimension {
    public static final Identifier TYPE = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "space");
    public static final double Y_BIAS = 20_000.0;
    /** espaço = M · local. Eixos da dimensão = base local da Terra no spawn: leste=+Z, cima=+X, norte=+Y (SpaceFrame.localBasis(EARTH,0,0)). */
    public static final double[] LOCAL_TO_SPACE = {0,1,0,  0,0,1,  1,0,0};   // det = +1

    public static void toSpace(double x, double y, double z, double[] out)   { out[0] = y - Y_BIAS; out[1] = z; out[2] = x; }
    public static void fromSpace(double sx, double sy, double sz, double[] out) { out[0] = sz; out[1] = sx + Y_BIAS; out[2] = sy; }

    public static boolean is(Level level) {
        return level.dimensionTypeRegistration().unwrapKey().filter(k -> k.identifier().equals(TYPE)).isPresent();
    }
}
