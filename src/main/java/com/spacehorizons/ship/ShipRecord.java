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
 * @param phase em que ponto de uma conversão entidade/blocos a nave está (spike S3); {@link Phase#READY} é a nave parada e inteira
 * @param snapshot o foguete que virou esta nave (spike S3): vazio nas naves de teste criadas por {@code ship create}
 * @param flight o estado de voo virtual da nave no espaço (fase 1c): vazio nas naves que ainda não voam
 */
public record ShipRecord(int id, int cell, String name, int sizeX, int sizeY, int sizeZ, Optional<UUID> owner, long createdTick, Phase phase,
        Optional<ShipFlightSnapshot> snapshot, Optional<ShipFlightData> flight) {
    /**
     * Estado da conversão. Só {@code READY} é estável; os outros existem em memória durante um job e são gravados para a recuperação
     * depois de um fechamento no meio: {@code FILLING} (entidade → blocos, a entidade ainda existe) e {@code REMOVING} (blocos →
     * entidade, a entidade já foi criada) viram "limpar a caixa e esquecer a nave"; {@code EMPTYING} (lendo os blocos, nada mudou) volta a {@code READY}.
     */
    public enum Phase {
        READY, FILLING, EMPTYING, REMOVING
    }

    public static final Codec<Phase> PHASE_CODEC = Codec.STRING.xmap(s -> {
        try {
            return Phase.valueOf(s);
        } catch (IllegalArgumentException e) {
            return Phase.READY;
        }
    }, Phase::name);

    public static final Codec<ShipRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(ShipRecord::id),
            Codec.INT.fieldOf("cell").forGetter(ShipRecord::cell),
            Codec.STRING.fieldOf("name").forGetter(ShipRecord::name),
            Codec.INT.fieldOf("size_x").forGetter(ShipRecord::sizeX),
            Codec.INT.fieldOf("size_y").forGetter(ShipRecord::sizeY),
            Codec.INT.fieldOf("size_z").forGetter(ShipRecord::sizeZ),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(ShipRecord::owner),
            Codec.LONG.fieldOf("created").forGetter(ShipRecord::createdTick),
            PHASE_CODEC.optionalFieldOf("phase", Phase.READY).forGetter(ShipRecord::phase),
            ShipFlightSnapshot.CODEC.optionalFieldOf("snapshot").forGetter(ShipRecord::snapshot),
            ShipFlightData.CODEC.optionalFieldOf("flight").forGetter(ShipRecord::flight)
    ).apply(i, ShipRecord::new));

    /** Nave sem foguete de origem (a de teste do S2) e sem voo. */
    public ShipRecord(int id, int cell, String name, int sizeX, int sizeY, int sizeZ, Optional<UUID> owner, long createdTick) {
        this(id, cell, name, sizeX, sizeY, sizeZ, owner, createdTick, Phase.READY, Optional.empty(), Optional.empty());
    }

    /** A caixa da nave no estaleiro. */
    public ShipYard.Box box() {
        return ShipYard.box(cell, sizeX, sizeY, sizeZ);
    }

    public ShipRecord withPhase(Phase newPhase) {
        return new ShipRecord(id, cell, name, sizeX, sizeY, sizeZ, owner, createdTick, newPhase, snapshot, flight);
    }

    public ShipRecord withSnapshot(ShipFlightSnapshot newSnapshot) {
        return new ShipRecord(id, cell, name, sizeX, sizeY, sizeZ, owner, createdTick, phase, Optional.of(newSnapshot), flight);
    }

    /** A nave com o estado de voo dado (fase 1c). */
    public ShipRecord withFlight(ShipFlightData newFlight) {
        return new ShipRecord(id, cell, name, sizeX, sizeY, sizeZ, owner, createdTick, phase, snapshot, Optional.of(newFlight));
    }

    /** A nave tem estado de voo virtual (voa no espaço)? */
    public boolean flies() {
        return flight.isPresent();
    }
}
