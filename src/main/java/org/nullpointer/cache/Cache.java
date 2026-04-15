package org.nullpointer.cache;

public interface Cache<K, V> {
    void set(K key, V value);
    V get(K key);
}
