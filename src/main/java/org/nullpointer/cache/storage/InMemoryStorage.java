package org.nullpointer.cache.storage;

import org.nullpointer.cache.exceptions.CapacityExceededException;
import org.nullpointer.cache.exceptions.KeyNotFoundException;

import java.util.HashMap;
import java.util.Map;

public class InMemoryStorage<K, V> implements Storage<K, V> {
    private final Map<K, V> map;
    private final int capacity;

    public InMemoryStorage(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();
    }

    // Instead of throwing StorageFullException, we can provide a state-testing method 'isFull()'
    // to let clients first validate, then add.
    @Override
    public void add(K key, V value) throws CapacityExceededException {
        if (map.containsKey(key)) {
            map.put(key, value);
            return;
        }

        if (map.size() >= capacity) {
            throw new CapacityExceededException("Maximum capacity of " + capacity + " reached");
        }

        map.put(key, value);
    }

    // Could return null instead of exception
    @Override
    public V get(K key) throws KeyNotFoundException {
        if (!map.containsKey(key)) {
            throw new KeyNotFoundException("Key: " + key + " not found");
        }
        return map.get(key);
    }

    // Could return false instead of exception
    @Override
    public void remove(K key) throws KeyNotFoundException {
        if (!map.containsKey(key)) {
            throw new KeyNotFoundException("Key: " + key + " not found");
        }
        map.remove(key);
    }
}
