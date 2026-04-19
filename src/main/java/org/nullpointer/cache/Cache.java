package org.nullpointer.cache;

import java.time.Duration;

public interface Cache<K, V> {
    void set(K key, V value);
    void set(K key, V value, Duration ttl);
    V get(K key);
    void remove(K key);
}
