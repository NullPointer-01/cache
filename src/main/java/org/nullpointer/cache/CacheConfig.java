package org.nullpointer.cache;

import org.nullpointer.cache.evictionpolicy.EvictionPolicy;
import org.nullpointer.cache.evictionpolicy.LRUEvictionPolicy;

import java.time.Duration;

public class CacheConfig<K> {
    private final int capacity;
    private final EvictionPolicy<K> evictionPolicy;
    private final Duration maintenanceInterval;

    private CacheConfig(Builder<K> builder) {
        this.capacity = builder.capacity;
        this.evictionPolicy = builder.evictionPolicy != null
                ? builder.evictionPolicy
                : new LRUEvictionPolicy<>();
        this.maintenanceInterval = builder.maintenanceInterval != null
                ? builder.maintenanceInterval
                : Duration.ofSeconds(1);
    }

    public int getCapacity() {
        return capacity;
    }

    public EvictionPolicy<K> getEvictionPolicy() {
        return evictionPolicy;
    }

    public Duration getMaintenanceInterval() {
        return maintenanceInterval;
    }

    public static <K> Builder<K> builder() {
        return new Builder<>();
    }

    public static class Builder<K> {
        private int capacity;
        private EvictionPolicy<K> evictionPolicy;
        private Duration maintenanceInterval;

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

        public Builder<K> maintenanceInterval(Duration maintenanceInterval) {
            this.maintenanceInterval = maintenanceInterval;
            return this;
        }

        public CacheConfig<K> build() {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Capacity must be positive, got: " + capacity);
            }
            if (maintenanceInterval != null && (maintenanceInterval.isZero() || maintenanceInterval.isNegative())) {
                throw new IllegalArgumentException("Maintenance interval must be positive, got: " + maintenanceInterval);
            }
            return new CacheConfig<>(this);
        }
    }
}
