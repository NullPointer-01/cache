package org.nullpointer.cache.storage;


import org.nullpointer.cache.exceptions.CapacityExceededException;
import org.nullpointer.cache.exceptions.KeyNotFoundException;

public interface Storage<K, V> {
    void add(K key, V value) throws CapacityExceededException;
    V get(K key) throws KeyNotFoundException;
    void remove(K key) throws KeyNotFoundException;
}
