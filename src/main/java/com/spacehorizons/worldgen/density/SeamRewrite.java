package com.spacehorizons.worldgen.density;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.generator.GradientFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.ShiftNoiseFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;

/**
 * Regra de reescrita que embrulha em PeriodicXLeaf toda função que lê X diretamente: NoiseFunction (com seus
 * shifts como filhos), ShiftA/B/Shift, BlendedNoise (o ruído 3D do relevo) e gradientes no eixo X.
 *
 * As folhas embrulhadas não são percorridas por dentro: os shifts e os filhos delas são avaliados em conjunto
 * nas coordenadas deslocadas, sem mistura em camadas. Referências (HolderHolder) são expandidas aqui mesmo,
 * como o otimizador faria, e o resultado é memorizado por identidade para não explodir o DAG compartilhado.
 * Uma instância por raiz: o memo não deve atravessar árvores.
 *
 * Limitação conhecida: só as funções de densidade do router, aquíferos e veias de minério são periódicos em X. O
 * ruído das regras de superfície (SurfaceSystem: bandas de badlands, cascalho, profundidade da superfície) e o
 * sorteio posicional das células dos aquíferos continuam indexados pelo X não embrulhado e podem destoar na costura.
 */
final class SeamRewrite implements DfRewriteRule {
    private final int circumference;
    private final int halfBand;
    private final Map<DensityFunction, DensityFunction> memo = new IdentityHashMap<>();

    SeamRewrite(int circumference, int halfBand) {
        if (halfBand <= 0 || halfBand * 4L > circumference) {
            throw new IllegalArgumentException("faixa da costura inválida: " + halfBand + " para circunferência " + circumference);
        }
        this.circumference = circumference;
        this.halfBand = halfBand;
    }

    @Override
    public DensityFunction rewrite(DensityFunction function) {
        DensityFunction source = function;
        while (source instanceof DensityFunctions.HolderHolder holder) {
            source = holder.function().value();
        }

        DensityFunction cached = this.memo.get(source);
        if (cached != null) {
            return cached;
        }

        DensityFunction result;
        if (source instanceof PeriodicXLeaf) {
            result = source;
        } else if (source instanceof PeriodicXFunction nested) {
            // periodic_x dentro de periodic_x: o período é o mesmo, basta um nível
            result = this.rewrite(nested.input());
        } else if (readsX(source)) {
            result = new PeriodicXLeaf(source, this.circumference, this.halfBand);
        } else {
            result = source.rewriteChildren(this);
        }
        this.memo.put(source, result);
        return result;
    }

    /** Funções que obtêm X do contexto e o transformam em ruído ou valor: as que precisam avaliar em X embrulhado. */
    static boolean readsX(DensityFunction function) {
        if (function instanceof NoiseFunction || function instanceof ShiftNoiseFunction || function instanceof BlendedNoise) {
            return true;
        }
        // uma fatia em X fixa a coordenada, então a saída dela não depende de X e não precisa de nada
        return function instanceof GradientFunction gradient && gradient.axis() == Direction.Axis.X;
    }
}
