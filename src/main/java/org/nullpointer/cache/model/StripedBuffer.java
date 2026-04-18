package org.nullpointer.cache.model;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class StripedBuffer<K> {
    private static final int MAX_READ_EVENTS = 256;

    private final ConcurrentLinkedQueue<K> readBuffer; // Buffer size is bounded
    private final ConcurrentLinkedQueue<Event<K>> writeBuffer; // Unbounded buffer
    private final AtomicInteger readSize;

    public StripedBuffer() {
        this.readBuffer = new ConcurrentLinkedQueue<>();
        this.writeBuffer = new ConcurrentLinkedQueue<>();
        this.readSize = new AtomicInteger();
    }

    public void recordAccess(K key) {
        // Drop read events if size exceeds. Leads to slight stale eviction
        if (readSize.get() >= MAX_READ_EVENTS) return;
        readSize.incrementAndGet();
        readBuffer.offer(key);
    }

    public K pollAccess() {
        K key = readBuffer.poll();
        if (key != null) readSize.decrementAndGet();
        return key;
    }

    public void recordAdd(K key) {
        writeBuffer.offer(new Event<>(EventType.ADD, key));
    }

    public void recordUpdate(K key) {
        writeBuffer.offer(new Event<>(EventType.UPDATE, key));
    }

    public void recordRemoval(K key) {
        writeBuffer.offer(new Event<>(EventType.REMOVE, key));
    }

    public Event<K> pollWrite() {
        return writeBuffer.poll();
    }
}
