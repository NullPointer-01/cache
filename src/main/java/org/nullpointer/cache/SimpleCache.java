package org.nullpointer.cache;

import org.nullpointer.cache.evictionpolicy.EvictionPolicy;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleCache<K, V> implements Cache<K, V> {
    private final ConcurrentHashMap<K, V> map;
    private final int capacity;
    private final EvictionPolicy<K> evictionPolicy;

    private final ConcurrentLinkedQueue<Event<K>> eventBuffer;
    private final ReentrantLock maintenanceLock;
    private final ScheduledExecutorService scheduler;
    private final ScheduledFuture<?> future;

    private static final int DRAIN_LIMIT = 64;

    public SimpleCache(int capacity, EvictionPolicy<K> evictionPolicy) {
        this.capacity = capacity;
        this.evictionPolicy = evictionPolicy;

        this.map = new ConcurrentHashMap<>();
        this.eventBuffer = new ConcurrentLinkedQueue<>();
        this.maintenanceLock = new ReentrantLock();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "maintenance-thread");
            t.setDaemon(true);
            return t;
        });
        this.future = scheduler.scheduleWithFixedDelay(this::maintenance, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void set(K key, V value) {
        map.put(key, value);
        eventBuffer.offer(new Event<>(EventType.ACCESS, key));
        tryMaintenance();
    }

    @Override
    public V get(K key) {
        V value = map.get(key);
        if (value != null) {
            eventBuffer.offer(new Event<>(EventType.ACCESS, key));
        }
        return value;
    }

    @Override
    public void remove(K key) {
        if (map.remove(key) != null) {
            eventBuffer.offer(new Event<>(EventType.REMOVE, key));
            tryMaintenance();
        }
    }

    public void stop() {
        future.cancel(false);
        scheduler.shutdown();
    }

    // For testing — forces a synchronous drain + eviction
    void runMaintenance() {
        maintenance();
    }

    private void tryMaintenance() {
        if (maintenanceLock.tryLock()) {
            try {
                drainAndEvict();
            } finally {
                maintenanceLock.unlock();
            }
        }
    }

    private void maintenance() {
        maintenanceLock.lock();
        try {
            drainAndEvict();
        } finally {
            maintenanceLock.unlock();
        }
    }

    private void drainAndEvict() {
        Event<K> event;
        int drained = 0;

        while (drained++ < DRAIN_LIMIT && (event = eventBuffer.poll()) != null) {
            switch (event.type) {
                case ACCESS -> {
                    if (map.containsKey(event.key)) {
                        evictionPolicy.onKeyAccess(event.key);
                    }
                }
                case REMOVE -> evictionPolicy.onKeyRemove(event.key);
            }
        }

        while (map.size() > capacity) {
            Optional<K> candidate = evictionPolicy.evictionCandidate();
            if (candidate.isEmpty()) break;

            map.remove(candidate.get());
            evictionPolicy.onKeyRemove(candidate.get());
        }
    }

    private enum EventType { ACCESS, REMOVE }

    private record Event<K>(EventType type, K key) {}
}

