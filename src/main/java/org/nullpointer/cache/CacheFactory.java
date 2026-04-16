package org.nullpointer.cache;

import org.nullpointer.cache.evictionpolicy.LRUEvictionPolicy;

public class CacheFactory<K, V> {
    public Cache<K, V> defaultCache(final int capacity) {
        return new SimpleCache<>(capacity, new LRUEvictionPolicy<>());
    }
}
