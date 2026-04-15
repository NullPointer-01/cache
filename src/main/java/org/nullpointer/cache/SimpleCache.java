package org.nullpointer.cache;

import org.nullpointer.cache.evictionpolicy.EvictionPolicy;
import org.nullpointer.cache.exceptions.CapacityExceededException;
import org.nullpointer.cache.exceptions.KeyNotFoundException;
import org.nullpointer.cache.storage.Storage;

public class SimpleCache<K, V> implements Cache<K,V> {
    private final Storage<K, V> storage;
    private final EvictionPolicy<K> evictionPolicy;

    public SimpleCache(Storage<K, V> storage, EvictionPolicy<K> evictionPolicy) {
        this.storage = storage;
        this.evictionPolicy = evictionPolicy;
    }

    @Override
    public void set(K key, V value) {
        try {
            this.storage.add(key, value);
            this.evictionPolicy.onKeyAccess(key);
        } catch (CapacityExceededException ignored) {
            K keyToEvict = this.evictionPolicy.evictionCandidate()
                    .orElseThrow(() -> new RuntimeException("Error while inserting key into cache..."));
            this.storage.remove(keyToEvict);
            this.evictionPolicy.onKeyRemove(keyToEvict);
            set(key, value);
        }
    }

    @Override
    public V get(K key) {
        try {
            V value = this.storage.get(key);
            this.evictionPolicy.onKeyAccess(key);
            return value;
        } catch (KeyNotFoundException ignored) {
            return null;
        }
    }
}

