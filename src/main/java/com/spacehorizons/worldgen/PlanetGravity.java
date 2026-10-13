package com.spacehorizons.worldgen;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Gravidade de cada planeta como modificador transitório do atributo GRAVITY (valor base 0,08) nos jogadores
 * e nos mobs do level. O atributo é sincronizável, então o cliente prevê o movimento com a mesma gravidade.
 *
 * O dano de queda NÃO acompanha a gravidade sozinho (ele depende só da distância caída): com gravidade baixa
 * o jogador cai mais devagar mas a distância continua a mesma, então FALL_DAMAGE_MULTIPLIER recebe o mesmo
 * fator. O modificador é transitório (não vai para o disco): é reaplicado a cada tick de jogador e na entrada
 * de mobs, e removido quando o jogador está num level sem planeta ou com gravidade 1.
 */
final class PlanetGravity {
    private static final Identifier GRAVITY_ID = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "planet_gravity");
    private static final Identifier FALL_ID = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "planet_fall_damage");
    private static final double EPSILON = 1.0E-9;

    private PlanetGravity() {
    }

    /** Chamado a cada tick de level (servidor): confere os jogadores, que mudam de dimensão sem evento de entrada. */
    static void onLevelTick(ServerLevel level) {
        double factor = factorOf(level);
        for (ServerPlayer player : level.players()) {
            apply(player, factor);
        }
    }

    /** Mobs que entram no level (spawn, carregamento de chunk, troca de dimensão). */
    static void onEntityJoin(Level level, net.minecraft.world.entity.Entity entity) {
        if (level.isClientSide() || !(entity instanceof LivingEntity living) || entity instanceof Player) {
            return;
        }
        apply(living, factorOf(level));
    }

    private static double factorOf(Level level) {
        return Planets.forLevel(level).map(PlanetDefinition::surfaceGravity).orElse(1.0);
    }

    static void apply(LivingEntity entity, double factor) {
        set(entity.getAttribute(Attributes.GRAVITY), GRAVITY_ID, factor);
        set(entity.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), FALL_ID, factor);
    }

    private static void set(AttributeInstance attribute, Identifier id, double factor) {
        if (attribute == null) {
            return;
        }
        if (Math.abs(factor - 1.0) < EPSILON) {
            if (attribute.hasModifier(id)) {
                attribute.removeModifier(id);
            }
            return;
        }
        double amount = factor - 1.0;
        AttributeModifier current = attribute.getModifier(id);
        if (current == null || Math.abs(current.amount() - amount) > EPSILON) {
            // multiplicador sobre o valor total: 0,08 * fator para a gravidade, 1,0 * fator para o dano de queda
            attribute.addOrUpdateTransientModifier(
                    new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
