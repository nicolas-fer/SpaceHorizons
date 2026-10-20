package com.spacehorizons.ship;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * Uma nave registrada no estaleiro (persistida no {@link ShipRegistry}). O {@code id} é um inteiro sequencial e estável (é o que
 * os comandos digitam); a célula guarda onde ela mora ({@link ShipYard#box}).
 *
 * @param id identificador persistente (1, 2, 3, ...)
 * @param cell índice da célula do estaleiro ({@link ShipYardAllocator})
 * @param name nome para listar
 * @param sizeX tamanho da caixa da nave em blocos (X, Y, Z)
 * @param owner quem criou (pode ser vazio: criada pelo console)
 * @param createdTick tempo de jogo da criação
 */
public record ShipRecord(int id, int cell, String name, int sizeX, int sizeY, int sizeZ, Optional<UUID> owner, long createdTick) {
    public static final Codec<ShipRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(ShipRecord::id),
            Codec.INT.fieldOf("cell").forGetter(ShipRecord::cell),
            Codec.STRING.fieldOf("name").forGetter(ShipRecord::name),
            Codec.INT.fieldOf("size_x").forGetter(ShipRecord::sizeX),
            Codec.INT.fieldOf("size_y").forGetter(ShipRecord::sizeY),
            Codec.INT.fieldOf("size_z").forGetter(ShipRecord::sizeZ),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(ShipRecord::owner),
            Codec.LONG.fieldOf("created").forGetter(ShipRecord::createdTick)
    ).apply(i, ShipRecord::new));

    /** A caixa da nave no estaleiro. */
    public ShipYard.Box box() {
        return ShipYard.box(cell, sizeX, sizeY, sizeZ);
    }
}
