package org.nullpointer.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCacheTTLTest {

    private SimpleCache<String, Integer> cache;

    @BeforeEach
    void setUp() {
        cache = new SimpleCache<>(CacheConfig.<String>builder().capacity(10).build());
    }

    @AfterEach
    void tearDown() {
        cache.stop();
    }

    @Test
    void perKeyTtlExpiresEntryAfterDuration() throws InterruptedException {
        cache.set("k", 42, Duration.ofMillis(100));
        assertEquals(42, cache.get("k"));

        Thread.sleep(150);
        assertNull(cache.get("k"), "entry should be expired after TTL elapsed");
    }

    @Test
    void setWithoutTtlNeverExpires() throws InterruptedException {
        cache.set("k", 42);
        Thread.sleep(150);
        assertEquals(42, cache.get("k"), "entry without TTL should never expire");
    }

    @Test
    void setWithNullTtlNeverExpires() throws InterruptedException {
        cache.set("k", 42, null);
        Thread.sleep(150);
        assertEquals(42, cache.get("k"), "entry with null TTL should never expire");
    }

    @Test
    void lazyExpiryOnGetRemovesEntry() throws InterruptedException {
        cache.set("k", 1, Duration.ofMillis(50));
        Thread.sleep(80);

        // get() should detect expiry and return null
        assertNull(cache.get("k"));

        // entry should be removed from the map
        cache.runMaintenance();
        assertNull(cache.get("k"), "expired entry should be fully removed");
    }

    @Test
    void backgroundCleanupRemovesExpiredEntryWithoutGet() throws InterruptedException {
        cache.set("a", 1, Duration.ofMillis(50));
        cache.set("b", 2); // no TTL
        cache.runMaintenance(); // drain ADD events so expiry queue is populated

        Thread.sleep(80);
        cache.runMaintenance(); // should expire "a" in background

        assertNull(cache.get("a"), "expired entry should be removed by background cleanup");
        assertEquals(2, cache.get("b"), "non-expiring entry should survive");
    }

    @Test
    void evictionAndTtlCoexist() throws InterruptedException {
        SimpleCache<String, Integer> smallCache = new SimpleCache<>(
                CacheConfig.<String>builder().capacity(3).build()
        );
        try {
            smallCache.set("a", 1, Duration.ofMillis(50));
            smallCache.set("b", 2);
            smallCache.set("c", 3);

            // cache is full (3 entries). add a 4th → triggers eviction of LRU
            smallCache.set("d", 4);
            smallCache.runMaintenance();

            // "a" is LRU and should be evicted by capacity enforcement
            assertNull(smallCache.get("a"));
            assertEquals(4, smallCache.get("d"));

            // wait for "a"'s TTL to pass — it was already evicted, should be a no-op
            Thread.sleep(80);
            smallCache.runMaintenance();
            assertNull(smallCache.get("a"));
        } finally {
            smallCache.stop();
        }
    }

    @Test
    void resetWithLongerTtlPreventsExpiry() throws InterruptedException {
        cache.set("k", 1, Duration.ofMillis(50));
        // immediately re-set with a much longer TTL
        cache.set("k", 2, Duration.ofMillis(5000));

        Thread.sleep(100); // original 50ms TTL has passed
        assertEquals(2, cache.get("k"), "re-set entry with longer TTL should not be expired");
    }

    @Test
    void resetWithNoTtlRemovesExpiry() throws InterruptedException {
        cache.set("k", 1, Duration.ofMillis(50));
        // re-set without TTL — should clear expiry
        cache.set("k", 2);

        Thread.sleep(100);
        assertEquals(2, cache.get("k"), "re-set entry without TTL should never expire");
    }

    @Test
    void explicitRemoveOfTtlEntryDoesNotCauseIssuesOnMaintenance() throws InterruptedException {
        cache.set("k", 1, Duration.ofMillis(100));
        cache.runMaintenance(); // drain ADD event, enqueue expiry
        cache.remove("k");

        Thread.sleep(150); // TTL has passed
        // maintenance should handle the stale PQ entry gracefully
        assertDoesNotThrow(() -> cache.runMaintenance());
        assertNull(cache.get("k"));
    }

    @Test
    void multipleKeysWithDifferentTtls() throws InterruptedException {
        cache.set("short", 1, Duration.ofMillis(50));
        cache.set("long", 2, Duration.ofMillis(500));
        cache.set("none", 3);

        Thread.sleep(80);
        assertNull(cache.get("short"), "short TTL entry should be expired");
        assertEquals(2, cache.get("long"), "long TTL entry should still be alive");
        assertEquals(3, cache.get("none"), "no-TTL entry should still be alive");

        Thread.sleep(500);
        assertNull(cache.get("long"), "long TTL entry should be expired now");
        assertEquals(3, cache.get("none"), "no-TTL entry should still be alive");
    }

    @Test
    void backgroundExpiryCleanupRemovesFromEvictionPolicy() throws InterruptedException {
        SimpleCache<String, Integer> smallCache = new SimpleCache<>(
                CacheConfig.<String>builder().capacity(3).build()
        );
        try {
            smallCache.set("a", 1, Duration.ofMillis(50));
            smallCache.set("b", 2, Duration.ofMillis(50));
            smallCache.runMaintenance(); // drain ADDs, populate expiry queue

            Thread.sleep(80);
            smallCache.runMaintenance(); // expire "a" and "b"

            assertNull(smallCache.get("a"));
            assertNull(smallCache.get("b"));

            // now add 3 new entries — should fit without evicting anything
            // (capacity is 3, and "a" and "b" were cleaned up)
            smallCache.set("c", 3);
            smallCache.set("d", 4);
            smallCache.set("e", 5);
            smallCache.runMaintenance();

            assertEquals(3, smallCache.get("c"));
            assertEquals(4, smallCache.get("d"));
            assertEquals(5, smallCache.get("e"));
        } finally {
            smallCache.stop();
        }
    }
}
