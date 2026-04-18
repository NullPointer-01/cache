package org.nullpointer.cache;

import org.nullpointer.cache.evictionpolicy.EvictionPolicy;
import org.nullpointer.cache.model.Event;
import org.nullpointer.cache.model.EventType;
import org.nullpointer.cache.model.StripedBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleCache<K, V> implements Cache<K, V> {
    private final ConcurrentHashMap<K, V> map;
    private final int capacity;
    private final EvictionPolicy<K> evictionPolicy;

    private final List<StripedBuffer<K>> buffers;
    private final ReentrantLock maintenanceLock;
    private final ScheduledExecutorService scheduler;
    private final ScheduledFuture<?> future;

    private static final int DRAIN_LIMIT_PER_STRIPE = 16;
    private static final int BUFFERS_SIZE = 16;

    public SimpleCache(int capacity, EvictionPolicy<K> evictionPolicy) {
        this.capacity = capacity;
        this.evictionPolicy = evictionPolicy;

        this.map = new ConcurrentHashMap<>();
        this.buffers = new ArrayList<>(BUFFERS_SIZE);
        for (int i = 0; i < BUFFERS_SIZE; i++) this.buffers.add(new StripedBuffer<>());

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
        buffers.get(stripeIndex()).offer(new Event<>(EventType.ACCESS, key));
        tryMaintenance();
    }

    @Override
    public V get(K key) {
        V value = map.get(key);
        if (value != null) {
            buffers.get(stripeIndex()).offer(new Event<>(EventType.ACCESS, key));
        }
        return value;
    }

    @Override
    public void remove(K key) {
        if (map.remove(key) != null) {
            buffers.get(stripeIndex()).offer(new Event<>(EventType.REMOVE, key));
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
        for (int i = 0; i < BUFFERS_SIZE; i++) {
            Event<K> event;
            int stripeDrained = 0;

            while (stripeDrained++ < DRAIN_LIMIT_PER_STRIPE && (event = buffers.get(i).poll()) != null) {
                switch (event.getType()) {
                    case ACCESS -> {
                        if (map.containsKey(event.getKey())) {
                            evictionPolicy.onKeyAccess(event.getKey());
                        }
                    }
                    case REMOVE -> evictionPolicy.onKeyRemove(event.getKey());
                }
            }
        }

        while (map.size() > capacity) {
            Optional<K> candidate = evictionPolicy.evictionCandidate();
            if (candidate.isEmpty()) break;

            map.remove(candidate.get());
            evictionPolicy.onKeyRemove(candidate.get());
        }
    }

    private int stripeIndex() {
        return (int) (Thread.currentThread().getId() % BUFFERS_SIZE);
    }
}

