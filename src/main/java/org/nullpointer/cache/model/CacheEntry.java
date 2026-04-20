package org.nullpointer.cache.model;

public class CacheEntry<K, V> implements Comparable<CacheEntry<K, V>> {
    private final K key;
    private final V value;
    private final long expiresAtNanos;

    public CacheEntry(K key, V value, long expiresAtNanos) {
        this.key = key;
        this.value = value;
        this.expiresAtNanos = expiresAtNanos;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public long getExpiresAtNanos() {
        return expiresAtNanos;
    }

    public boolean isExpired() {
        return expiresAtNanos != 0 && System.nanoTime() >= expiresAtNanos;
    }

    public boolean hasTtl() {
        return expiresAtNanos != 0;
    }

    @Override
    public int compareTo(CacheEntry<K, V> other) {
        // 0 means no TTL / never expires
        if (this.expiresAtNanos == 0 && other.expiresAtNanos == 0) return 0;
        if (this.expiresAtNanos == 0) return 1;

        if (other.expiresAtNanos == 0) return -1;
        return Long.compare(this.expiresAtNanos, other.expiresAtNanos);
    }
}
