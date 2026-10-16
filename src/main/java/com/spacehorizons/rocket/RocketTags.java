package com.spacehorizons.rocket;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Tags de bloco do foguete (data/spacehorizons/tags/block). */
public final class RocketTags {
    /** As peças do foguete (inclui a plataforma de lançamento). */
    public static final TagKey<Block> ROCKET_PARTS = tag("rocket_parts");
    /** Blocos que nunca viram parte de foguete: a montagem é recusada se a busca tocar um deles (bedrock, dureza -1, spawners...). */
    public static final TagKey<Block> ROCKET_FORBIDDEN = tag("rocket_forbidden");
    /** Terreno natural: a montagem é recusada se um bloco do foguete encostar nele (o foguete não carrega o chão). */
    public static final TagKey<Block> ROCKET_TERRAIN = tag("rocket_terrain");

    private RocketTags() {
    }

    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, name));
    }
}
