package org.nullpointer.cache;

import org.nullpointer.cache.evictionpolicy.EvictionPolicy;
import org.nullpointer.cache.evictionpolicy.LRUEvictionPolicy;

public class CacheConfig<K> {
    private final int capacity;
    private final EvictionPolicy<K> evictionPolicy;

    private CacheConfig(Builder<K> builder) {
        this.capacity = builder.capacity;
        this.evictionPolicy = builder.evictionPolicy != null
                ? builder.evictionPolicy
                : new LRUEvictionPolicy<>();
    }

    public int getCapacity() {
        return capacity;
    }

    public EvictionPolicy<K> getEvictionPolicy() {
        return evictionPolicy;
    }

    public static <K> Builder<K> builder() {
        return new Builder<>();
    }

    public static class Builder<K> {
        private int capacity;
        private EvictionPolicy<K> evictionPolicy;

        private Builder() {
        }

        public Builder<K> capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder<K> evictionPolicy(EvictionPolicy<K> evictionPolicy) {
            this.evictionPolicy = evictionPolicy;
            return this;
        }

        public CacheConfig<K> build() {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Capacity must be positive, got: " + capacity);
            }
            return new CacheConfig<>(this);
        }
    }
}
