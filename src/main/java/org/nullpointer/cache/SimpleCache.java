package org.nullpointer.cache;

import org.nullpointer.cache.evictionpolicy.EvictionPolicy;
import org.nullpointer.cache.model.CacheEntry;
import org.nullpointer.cache.model.Event;
import org.nullpointer.cache.model.StripedBuffer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleCache<K, V> implements Cache<K, V> {
    private final ConcurrentHashMap<K, CacheEntry<K, V>> map;
    private final int capacity;
    private final EvictionPolicy<K> evictionPolicy;

    private final List<StripedBuffer<K>> buffers;
    private final ReentrantLock maintenanceLock;
    private final ScheduledExecutorService scheduler;
    private final ScheduledFuture<?> future;

    private final PriorityQueue<CacheEntry<K, V>> expiryQueue;

    private static final int READ_DRAIN_LIMIT_PER_STRIPE = 16;
    private static final int BUFFERS_SIZE = 16;

    public SimpleCache(CacheConfig<K> config) {
        this.capacity = config.getCapacity();
        this.evictionPolicy = config.getEvictionPolicy();

        this.map = new ConcurrentHashMap<>();
        this.buffers = new ArrayList<>(BUFFERS_SIZE);
        for (int i = 0; i < BUFFERS_SIZE; i++) this.buffers.add(new StripedBuffer<>());

        this.expiryQueue = new PriorityQueue<>();
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
        set(key, value, null);
    }

    @Override
    public void set(K key, V value, Duration ttl) {
        long expiresAtNanos = (ttl != null) ? System.nanoTime() + ttl.toNanos() : 0;
        CacheEntry<K, V> entry = new CacheEntry<>(key, value, expiresAtNanos);
        CacheEntry<K, V> old = map.put(key, entry);
        StripedBuffer<K> stripe = buffers.get(stripeIndex());

        if (old == null) {
            stripe.recordAdd(key);
        } else {
            stripe.recordUpdate(key);
        }
        tryMaintenance();
    }

    @Override
    public V get(K key) {
        CacheEntry<K, V> entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            // CAS remove — only if the entry hasn't been replaced by another thread
            if (map.remove(key, entry)) {
                buffers.get(stripeIndex()).recordRemoval(key);
                tryMaintenance();
            }
            return null;
        }
        buffers.get(stripeIndex()).recordAccess(key);
        return entry.getValue();
    }

    @Override
    public void remove(K key) {
        if (map.remove(key) != null) {
            buffers.get(stripeIndex()).recordRemoval(key);
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
        // Step 1: Drain all write events first
        for (int i = 0; i < BUFFERS_SIZE; i++) {
            Event<K> event;
            while ((event = buffers.get(i).pollWrite()) != null) {
                switch (event.getType()) {
                    case ADD, UPDATE -> {
                        evictionPolicy.onKeyAccess(event.getKey());
                        enqueueExpiry(event.getKey());
                    }
                    case REMOVE -> evictionPolicy.onKeyRemove(event.getKey());
                }
            }
        }

        // Step 2: Drain read events — bounded per stripe
        for (int i = 0; i < BUFFERS_SIZE; i++) {
            K key;
            int stripeDrained = 0;
            while (stripeDrained++ < READ_DRAIN_LIMIT_PER_STRIPE && (key = buffers.get(i).pollAccess()) != null) {
                if (map.containsKey(key)) {
                    evictionPolicy.onKeyAccess(key);
                }
            }
        }

        // Step 3: Expire entries from the priority queue
        long now = System.nanoTime();
        while (!expiryQueue.isEmpty() && expiryQueue.peek().getExpiresAtNanos() <= now) {
            CacheEntry<K, V> entry = expiryQueue.poll();
            K key = entry.getKey();
            // If the map still holds this exact object, it hasn't been re-set
            if (map.get(key) == entry) {
                map.remove(key);
                evictionPolicy.onKeyRemove(key);
            }
        }

        // Step 4: Enforce capacity
        while (map.size() > capacity) {
            Optional<K> candidate = evictionPolicy.evictionCandidate();
            if (candidate.isEmpty()) break;

            map.remove(candidate.get());
            evictionPolicy.onKeyRemove(candidate.get());
        }
    }

    private void enqueueExpiry(K key) {
        CacheEntry<K, V> entry = map.get(key);
        if (entry != null && entry.hasTtl()) {
            expiryQueue.add(entry);
        }
    }

    private int stripeIndex() {
        return (int) (Thread.currentThread().getId() % BUFFERS_SIZE);
    }
}

