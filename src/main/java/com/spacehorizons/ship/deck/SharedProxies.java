package com.spacehorizons.ship.deck;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Os contêineres proxy abertos, um por chave (nave + posição do bloco): quem abre o mesmo baú recebe a MESMA instância (dois jogadores veem o
 * mesmo conteúdo, nada se duplica) e a instância some quando o último fecha. Lógica pura e sem thread: só o thread do servidor mexe.
 */
public final class SharedProxies<K, V> {
    private final Map<K, Entry<V>> open = new HashMap<>();

    private static final class Entry<V> {
        final V value;
        int viewers;

        Entry(V value) {
            this.value = value;
        }
    }

    /** O proxy da chave (criado por {@code factory} se não há) com mais um espectador. */
    public V acquire(K key, Supplier<V> factory) {
        Entry<V> entry = this.open.computeIfAbsent(key, k -> new Entry<>(factory.get()));
        entry.viewers++;
        return entry.value;
    }

    /** Um espectador fechou; devolve true se foi o último (o proxy foi esquecido). */
    public boolean release(K key) {
        Entry<V> entry = this.open.get(key);
        if (entry == null) {
            return false;
        }
        entry.viewers--;
        if (entry.viewers <= 0) {
            this.open.remove(key);
            return true;
        }
        return false;
    }

    public int viewers(K key) {
        Entry<V> entry = this.open.get(key);
        return entry == null ? 0 : entry.viewers;
    }

    public int size() {
        return this.open.size();
    }

    public void clear() {
        this.open.clear();
    }
}
