package org.nullpointer.cache;

import org.nullpointer.cache.evictionpolicy.EvictionPolicy;

import java.util.HashMap;
import java.util.Map;

public class SimpleCache<K, V> implements Cache<K, V> {
    private final Map<K, V> map;
    private final int capacity;
    private final EvictionPolicy<K> evictionPolicy;

    public SimpleCache(int capacity, EvictionPolicy<K> evictionPolicy) {
        this.capacity = capacity;
        this.evictionPolicy = evictionPolicy;
        this.map = new HashMap<>();
    }

    @Override
    public void set(K key, V value) {
        if (!map.containsKey(key) && map.size() >= capacity) {
            K candidate = evictionPolicy.evictionCandidate()
                    .orElseThrow(() -> new RuntimeException("Eviction policy returned no candidate"));
            map.remove(candidate);
            evictionPolicy.onKeyRemove(candidate);
        }
        map.put(key, value);
        evictionPolicy.onKeyAccess(key);
    }

    @Override
    public V get(K key) {
        if (!map.containsKey(key)) {
            return null;
        }
        evictionPolicy.onKeyAccess(key);
        return map.get(key);
    }
}

