package org.nullpointer.cache;

import org.nullpointer.cache.evictionpolicy.LRUEvictionPolicy;
import org.nullpointer.cache.storage.InMemoryStorage;

public class CacheFactory<K, V> {
    public Cache<K, V> defaultCache(final int capacity) {
        return new SimpleCache<>(
                new InMemoryStorage<>(capacity),
                new LRUEvictionPolicy<>()
        );
    }
}
