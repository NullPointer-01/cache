package org.nullpointer.cache.model;


import java.util.concurrent.ConcurrentLinkedQueue;

public class StripedBuffer<K> {
    private final ConcurrentLinkedQueue<Event<K>> eventBuffer;

    public StripedBuffer() {
        this.eventBuffer = new ConcurrentLinkedQueue<>();
    }

    public boolean offer(Event<K> e) {
        return eventBuffer.offer(e);
    }

    public Event<K> poll() {
        return eventBuffer.poll();
    }
}
