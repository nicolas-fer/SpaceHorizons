package com.spacehorizons.network;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Divisão do mapa serializado em pedaços de payload e a montagem do outro lado. Java puro (testável sem rede).
 * O montador aceita um mapa por vez: um pedaço de índice 0 reinicia a montagem, os seguintes devem vir em ordem
 * (a conexão do jogo entrega em ordem); qualquer desencontro descarta a montagem em curso.
 */
public final class PlanetMapChunks {
    private PlanetMapChunks() {
    }

    /** Parte {@code data} em payloads de no máximo {@link PlanetMapPayload#MAX_CHUNK} bytes. */
    public static List<PlanetMapPayload> split(String planet, byte[] data) {
        int count = Math.max(1, (data.length + PlanetMapPayload.MAX_CHUNK - 1) / PlanetMapPayload.MAX_CHUNK);
        List<PlanetMapPayload> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int from = i * PlanetMapPayload.MAX_CHUNK;
            int to = Math.min(data.length, from + PlanetMapPayload.MAX_CHUNK);
            byte[] piece = new byte[to - from];
            System.arraycopy(data, from, piece, 0, piece.length);
            out.add(new PlanetMapPayload(planet, data.length, i, count, piece));
        }
        return out;
    }

    /** Montagem no cliente. */
    public static final class Assembler {
        private @Nullable String planet;
        private byte @Nullable [] buffer;
        private int nextIndex;
        private int count;
        private int written;

        /**
         * Aceita um pedaço. Devolve o mapa completo (bytes de PlanetMap.serialize) quando o último pedaço chega e a
         * montagem fecha no tamanho anunciado; senão null.
         */
        public byte @Nullable [] accept(PlanetMapPayload payload) {
            if (payload.index() == 0) {
                reset();
                if (payload.count() < 1 || payload.totalLength() < 0 || payload.totalLength() > 64 * 1024 * 1024) {
                    return null;
                }
                planet = payload.planet();
                buffer = new byte[payload.totalLength()];
                count = payload.count();
            }
            byte[] target = buffer;
            if (target == null || planet == null || !planet.equals(payload.planet()) || payload.index() != nextIndex
                    || payload.count() != count || payload.totalLength() != target.length
                    || written + payload.data().length > target.length) {
                reset();
                return null;
            }
            System.arraycopy(payload.data(), 0, target, written, payload.data().length);
            written += payload.data().length;
            nextIndex++;
            if (nextIndex < count) {
                return null;
            }
            byte[] complete = written == target.length ? target : null;
            reset();
            return complete;
        }

        public void reset() {
            planet = null;
            buffer = null;
            nextIndex = 0;
            count = 0;
            written = 0;
        }
    }
}
