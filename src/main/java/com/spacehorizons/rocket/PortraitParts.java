package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Envio do retrato do foguete em PARTES, para o que não cabe no pacote de spawn (o cliente recusa um pacote de plugin acima de 1 MiB e
 * desconecta; o S3 do docs/PLANO_NAVES.md mediu ~1,65 KB por baú cheio). Lógica pura: o servidor corta os bytes do retrato em partes de
 * {@link #PART_BYTES}, o cliente as junta (em qualquer ordem) e, quando completo, entrega os bytes a quem espera por aquele id de retrato.
 *
 * Quem usa: {@link RocketEntity#writeSpawnData} (grava só o id quando o retrato é grande), {@code RocketEntity.startSeenByPlayer} (manda as
 * partes a cada jogador que passa a ver o foguete) e o cliente (um pacote por parte, {@link RocketPortraitPartPayload}).
 */
public final class PortraitParts {
    /** Tamanho de uma parte: bem abaixo do 1 MiB do pacote, com folga para o cabeçalho. */
    public static final int PART_BYTES = 256 * 1024;
    /** Teto de partes (o retrato de {@link RocketAssembler#MAX_PORTRAIT_BYTES} em partes de {@link #PART_BYTES}, mais folga). */
    public static final int MAX_PARTS = RocketAssembler.MAX_PORTRAIT_BYTES / PART_BYTES + 4;

    /** O tamanho em vigor (só o autoteste o baixa, para provar vários pacotes sem um retrato de megabytes). */
    private static volatile int partBytes = PART_BYTES;

    private PortraitParts() {
    }

    /** O tamanho de parte que o servidor usa (normalmente {@link #PART_BYTES}). */
    public static int partBytes() {
        return partBytes;
    }

    /** Só para teste: muda o tamanho de parte (restaura com {@link #PART_BYTES}). */
    public static void setPartBytesForTest(int bytes) {
        partBytes = Math.max(64, bytes);
    }

    /** Corta {@code data} em partes de no máximo {@code partBytes}; dados vazios dão uma parte vazia. */
    public static List<byte[]> split(byte[] data, int partBytes) {
        if (partBytes <= 0) {
            throw new IllegalArgumentException("tamanho de parte inválido: " + partBytes);
        }
        List<byte[]> parts = new ArrayList<>();
        for (int at = 0; at < data.length || parts.isEmpty(); at += partBytes) {
            parts.add(Arrays.copyOfRange(data, at, Math.min(data.length, at + partBytes)));
        }
        return parts;
    }

    /** Une as partes na ordem. */
    public static byte[] join(List<byte[]> parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] out = new byte[total];
        int at = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }

    /**
     * Recebe as partes de vários retratos (chave: id do retrato) e guarda os que ficaram completos até alguém os pegar. Só o thread do
     * cliente mexe nele. Recusa (IllegalArgumentException) partes incoerentes: quantidade fora do limite, índice fora da quantidade ou
     * quantidade diferente da primeira parte do mesmo retrato.
     */
    public static final class Receiver {
        private static final int MAX_PENDING = 8;

        private final Map<UUID, Partial> partial = new LinkedHashMap<>();
        private final Map<UUID, byte[]> complete = new LinkedHashMap<>();

        private static final class Partial {
            final byte[][] parts;
            int received;

            Partial(int count) {
                this.parts = new byte[count][];
            }
        }

        /** Adiciona uma parte; devolve true se o retrato ficou completo agora. */
        public boolean accept(UUID id, int index, int count, byte[] bytes) {
            if (count < 1 || count > MAX_PARTS || index < 0 || index >= count) {
                throw new IllegalArgumentException("parte de retrato inválida: " + index + " de " + count);
            }
            Partial p = this.partial.get(id);
            if (p == null) {
                if (this.partial.size() >= MAX_PENDING) {
                    this.partial.remove(this.partial.keySet().iterator().next());
                }
                p = new Partial(count);
                this.partial.put(id, p);
            } else if (p.parts.length != count) {
                throw new IllegalArgumentException("quantidade de partes diferente para o retrato " + id + ": " + count + " e " + p.parts.length);
            }
            if (p.parts[index] == null) {
                p.parts[index] = bytes;
                p.received++;
            }
            if (p.received < p.parts.length) {
                return false;
            }
            this.partial.remove(id);
            if (this.complete.size() >= MAX_PENDING) {
                this.complete.remove(this.complete.keySet().iterator().next());
            }
            this.complete.put(id, join(Arrays.asList(p.parts)));
            return true;
        }

        /** Os bytes do retrato {@code id} se já chegaram inteiros (e os tira da guarda), ou null. */
        public @Nullable byte[] take(UUID id) {
            return this.complete.remove(id);
        }

        /** Partes que faltam para o retrato {@code id} (para diagnóstico); -1 se nada chegou dele. */
        public int missing(UUID id) {
            Partial p = this.partial.get(id);
            return p == null ? (this.complete.containsKey(id) ? 0 : -1) : p.parts.length - p.received;
        }

        public void clear() {
            this.partial.clear();
            this.complete.clear();
        }
    }

    /** O receptor do cliente (uma instância por JVM). */
    public static final Receiver CLIENT = new Receiver();
}
