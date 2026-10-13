package com.spacehorizons.worldgen.density;

import com.mojang.serialization.MapCodec;

import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;

/**
 * Folha periódica em X: avalia a função de entrada (um ruído e tudo que ele lê de X, incluindo os shifts)
 * em coordenadas embrulhadas e, na faixa da costura, mistura as duas avaliações (ver SeamMath).
 * Só nasce do SeamRewrite, depois da leitura dos dados; nunca é serializada.
 *
 * Funciona sobre DensityVolume em qualquer passo (células de interpolação incluídas): os deslocamentos são
 * múltiplos da circunferência (múltipla de 16), então o alinhamento às células se mantém. Os caches abaixo
 * são indexados por volume e coordenada, então alternar entre os volumes A e B na faixa só os recalcula.
 */
public record PeriodicXLeaf(DensityFunction input, int circumference, int halfBand) implements DensityFunction {
    private static final float SQRT_2 = (float) Math.sqrt(2.0);

    @Override
    public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return new PeriodicXSampler(this.input.compileSampler(context), this.circumference, this.halfBand);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newInput = rule.rewrite(this.input);
        return newInput == this.input ? this : new PeriodicXLeaf(newInput, this.circumference, this.halfBand);
    }

    /** cos·a + sin·b com a, b em [lo, hi] fica em [lo*raiz2 se lo<0, hi*raiz2 se hi>0]; senão não sai do intervalo. */
    @Override
    public Interval range() {
        Interval range = this.input.range();
        float lo = range.min();
        float hi = range.max();
        if (Float.isNaN(lo) || Float.isNaN(hi)) {
            return range;
        }
        return Interval.of(lo < 0.0F ? lo * SQRT_2 : lo, hi > 0.0F ? hi * SQRT_2 : hi);
    }

    @Override
    public @DensityFunction.Axes int domainAxes() {
        return this.input.domainAxes();
    }

    @Override
    public MapCodec<PeriodicXLeaf> codec() {
        throw new UnsupportedOperationException("PeriodicXLeaf nunca é serializado");
    }
}
