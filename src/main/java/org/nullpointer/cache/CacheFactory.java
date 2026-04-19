package org.nullpointer.cache;

public class CacheFactory<K, V> {
    public Cache<K, V> defaultCache(final int capacity) {
        CacheConfig<K> config = CacheConfig.<K>builder().capacity(capacity).build();
        return new SimpleCache<>(config);
    }

    public Cache<K, V> createCache(final CacheConfig<K> config) {
        return new SimpleCache<>(config);
    }
}
