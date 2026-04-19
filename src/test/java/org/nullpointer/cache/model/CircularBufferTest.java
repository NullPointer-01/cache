package org.nullpointer.cache.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class CircularBufferTest {

    @Test
    void offerAndPollSingleElement() {
        CircularBuffer<String> buf = new CircularBuffer<>(4);
        buf.offer("a");
        assertEquals("a", buf.poll());
        assertNull(buf.poll());
    }

    @Test
    void pollFromEmptyReturnsNull() {
        CircularBuffer<Integer> buf = new CircularBuffer<>(4);
        assertNull(buf.poll());
    }

    @Test
    void preservesInsertionOrderWithinCapacity() {
        int cap = 8;
        CircularBuffer<Integer> buf = new CircularBuffer<>(cap);
        for (int i = 0; i < cap; i++) buf.offer(i);

        for (int i = 0; i < cap; i++) {
            assertEquals(i, buf.poll());
        }
        assertNull(buf.poll());
    }

    @Test
    void overwriteOldestOnOverflow() {
        int cap = 4;
        CircularBuffer<Integer> buf = new CircularBuffer<>(cap);

        // Offer 7 items into a capacity-4 buffer: 0,1,2,3,4,5,6
        for (int i = 0; i < 7; i++) buf.offer(i);

        // The oldest 3 (0,1,2) were overwritten; only 3,4,5,6 remain
        List<Integer> polled = new ArrayList<>();
        Integer val;
        while ((val = buf.poll()) != null) polled.add(val);

        assertEquals(List.of(3, 4, 5, 6), polled);
    }

    @Test
    void sizeReflectsOffersAndPolls() {
        CircularBuffer<String> buf = new CircularBuffer<>(8);
        assertEquals(0, buf.size());

        buf.offer("a");
        buf.offer("b");
        buf.offer("c");
        assertEquals(3, buf.size());

        buf.poll();
        assertEquals(2, buf.size());

        buf.poll();
        buf.poll();
        assertEquals(0, buf.size());
    }

    @Test
    void sizeClampedToCapacityOnOverflow() {
        int cap = 4;
        CircularBuffer<Integer> buf = new CircularBuffer<>(cap);
        for (int i = 0; i < 10; i++) buf.offer(i);

        // Even though 10 were offered, size should report at most capacity
        assertEquals(cap, buf.size());
    }

    @Test
    void rejectsNonPowerOfTwoCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new CircularBuffer<>(3));
        assertThrows(IllegalArgumentException.class, () -> new CircularBuffer<>(0));
        assertThrows(IllegalArgumentException.class, () -> new CircularBuffer<>(-1));
    }

    @Test
    void concurrentOffersDoNotLoseSlots() throws Exception {
        int cap = 256;
        int threads = 8;
        int perThread = 500;
        CircularBuffer<Integer> buf = new CircularBuffer<>(cap);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            int threadId = t;
            futures.add(pool.submit(() -> {
                try { barrier.await(); } catch (Exception e) { throw new RuntimeException(e); }
                for (int i = 0; i < perThread; i++) {
                    buf.offer(threadId * perThread + i);
                }
            }));
        }

        for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        // Drain everything
        List<Integer> polled = new ArrayList<>();
        Integer val;
        while ((val = buf.poll()) != null) polled.add(val);

        // Total offered = threads * perThread = 4000, capacity = 256
        // We should see exactly capacity items
        assertEquals(cap, polled.size(), "Polled more than capacity: " + polled.size());
    }

    @Test
    void concurrentOfferWithSingleConsumer() throws Exception {
        int cap = 256;
        int threads = 4;
        int perThread = 2000;
        CircularBuffer<Integer> buf = new CircularBuffer<>(cap);

        ExecutorService producers = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads + 1); // +1 for consumer
        List<Future<?>> futures = new ArrayList<>();

        // Producers
        for (int t = 0; t < threads; t++) {
            int threadId = t;
            futures.add(producers.submit(() -> {
                try { barrier.await(); } catch (Exception e) { throw new RuntimeException(e); }
                for (int i = 0; i < perThread; i++) {
                    buf.offer(threadId * perThread + i);
                }
            }));
        }

        // Single consumer runs concurrently with producers
        List<Integer> consumed = Collections.synchronizedList(new ArrayList<>());
        Thread consumer = new Thread(() -> {
            try { barrier.await(); } catch (Exception e) { throw new RuntimeException(e); }
            // Poll for a bit longer than producers run
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < deadline) {
                Integer val = buf.poll();
                if (val != null) {
                    consumed.add(val);
                } else {
                    Thread.yield();
                }
            }
        });
        consumer.start();

        for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
        producers.shutdown();
        consumer.join(5000);

        // Drain any remaining after producers finish
        Integer val;
        while ((val = buf.poll()) != null) consumed.add(val);

        // All consumed values must be valid
        for (int v : consumed) {
            assertTrue(v >= 0 && v < threads * perThread,
                "Invalid value consumed: " + v);
        }
        // Should have consumed a reasonable number of items
        assertFalse(consumed.isEmpty(), "Consumer should have polled some items");
    }
}
