package com.spacehorizons.planet.spec;

import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.resources.Identifier;

/** Codecs pequenos dos parâmetros de planeta: cores em hexadecimal e escolha de bloco com reserva. */
public final class SpecCodecs {
    private SpecCodecs() {
    }

    /** Cor "#rrggbb" ↔ 0xRRGGBB. */
    public static final Codec<Integer> COLOR = Codec.STRING.comapFlatMap(SpecCodecs::parseColor,
            rgb -> String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF));

    /**
     * Bloco com reserva: um id ou uma lista de ids, e vale o PRIMEIRO que existir no registro quando o mundo carrega. Assim
     * um planeta pede um bloco do mod que ainda não existe (por exemplo {@code spacehorizons:mars_regolith}) e usa um
     * vanilla provisório até ele aparecer, sem mudar o JSON.
     */
    public static final Codec<List<Identifier>> BLOCK_CHOICE = Codec.withAlternative(
            Identifier.CODEC.listOf().validate(list -> list.isEmpty()
                    ? DataResult.error(() -> "lista de blocos vazia") : DataResult.success(list)),
            Identifier.CODEC.xmap(List::of, list -> list.get(0)));

    static DataResult<Integer> parseColor(String text) {
        if (text.length() != 7 || text.charAt(0) != '#') {
            return DataResult.error(() -> "cor deve ser \"#rrggbb\": " + text);
        }
        try {
            return DataResult.success(Integer.parseInt(text.substring(1), 16));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "cor inválida: " + text);
        }
    }

    /** Recusa com IllegalArgumentException (os records validam no construtor; o carregador transforma em erro legível). */
    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    static void requireRange(double value, double min, double max, String name) {
        require(value >= min && value <= max, name + " fora de [" + min + ", " + max + "]: " + value);
    }
}
