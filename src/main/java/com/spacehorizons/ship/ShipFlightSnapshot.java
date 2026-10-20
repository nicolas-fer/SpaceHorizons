package com.spacehorizons.ship;

import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * O que o {@link ShipRecord} guarda do foguete que virou blocos no estaleiro (spike S3): de onde ele veio (dimensão e centro da caixa),
 * o NBT da entidade SEM o retrato (estado de voo, atitude, acelerador, SAS, aquecimento, combustível, posição virtual: tudo o que
 * {@code RocketEntity} salva), o tamanho exato do retrato, onde está o cockpit (coordenadas locais = relativas ao canto da caixa), o
 * romaneio do que entrou ({@link ShipManifest}) e quem estava a bordo. É o que a conversão de volta usa para recriar a entidade.
 *
 * @param dimension dimensão onde o foguete estava
 * @param x,y,z centro da caixa da entidade nessa dimensão
 * @param rocket NBT da entidade (sem "Portrait")
 * @param portraitSize tamanho do retrato (X, Y, Z); a caixa do registro é maior ou igual (mínimo 16)
 * @param cockpit posição local do cockpit (relativa ao canto da caixa, que é onde o retrato foi colocado)
 * @param manifest romaneio de entrada
 * @param passengers quem estava sentado no foguete na conversão
 */
public record ShipFlightSnapshot(Identifier dimension, double x, double y, double z, CompoundTag rocket, BlockPos portraitSize, BlockPos cockpit,
        ShipManifest manifest, List<UUID> passengers) {
    public static final Codec<ShipFlightSnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("dimension").forGetter(ShipFlightSnapshot::dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(ShipFlightSnapshot::x),
            Codec.DOUBLE.fieldOf("y").forGetter(ShipFlightSnapshot::y),
            Codec.DOUBLE.fieldOf("z").forGetter(ShipFlightSnapshot::z),
            CompoundTag.CODEC.fieldOf("rocket").forGetter(ShipFlightSnapshot::rocket),
            BlockPos.CODEC.fieldOf("portrait_size").forGetter(ShipFlightSnapshot::portraitSize),
            BlockPos.CODEC.fieldOf("cockpit").forGetter(ShipFlightSnapshot::cockpit),
            ShipManifest.CODEC.fieldOf("manifest").forGetter(ShipFlightSnapshot::manifest),
            UUIDUtil.CODEC.listOf().fieldOf("passengers").forGetter(ShipFlightSnapshot::passengers)
    ).apply(i, ShipFlightSnapshot::new));

    public Vec3 center() {
        return new Vec3(this.x, this.y, this.z);
    }
}
