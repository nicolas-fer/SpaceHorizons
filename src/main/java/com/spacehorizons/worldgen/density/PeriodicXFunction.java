package com.spacehorizons.worldgen.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;

/**
 * Função de densidade "spacehorizons:periodic_x": torna a função de entrada periódica em X com o período do
 * planeta (a circunferência) e contínua na costura, sem mudar nada longe dela.
 *
 * Não avalia nada sozinha. Quando o compilador de densidades otimiza a árvore (DfRewriteRule), este nó se
 * substitui pela entrada reescrita por SeamRewrite: cada ruído que lê X vira uma PeriodicXLeaf. A reescrita
 * por folha (e não da raiz inteira) é o que mantém coerentes as partes derivadas (splines de continentalidade,
 * interpolação por células, caches), porque tudo acima das folhas só enxerga valores já periódicos.
 */
public record PeriodicXFunction(Identifier planet, DensityFunction input) implements DensityFunction {
    public static final MapCodec<PeriodicXFunction> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Identifier.CODEC.fieldOf("planet").forGetter(PeriodicXFunction::planet),
                    DensityFunction.CODEC.fieldOf("input").forGetter(PeriodicXFunction::input))
                    .apply(instance, PeriodicXFunction::new));

    @Override
    public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        throw new IllegalStateException("periodic_x precisa passar pelo otimizador antes de compilar");
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        PlanetDefinition definition = Planets.byId(this.planet)
                .orElseThrow(() -> new IllegalStateException("planeta desconhecido em periodic_x: " + this.planet));
        DensityFunction periodic = new SeamRewrite(definition.circumferenceBlocks(), SeamMath.DEFAULT_HALF_BAND).rewrite(this.input);
        // o nó some: quem o chamou recebe a árvore periódica, já otimizada pela regra do compilador
        return rule.rewrite(periodic);
    }

    @Override
    public Interval range() {
        return this.input.range();
    }

    @Override
    public @DensityFunction.Axes int domainAxes() {
        return this.input.domainAxes();
    }

    @Override
    public MapCodec<PeriodicXFunction> codec() {
        return CODEC;
    }
}
