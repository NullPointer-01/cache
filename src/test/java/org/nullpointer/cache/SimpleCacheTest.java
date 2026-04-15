package org.nullpointer.cache;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nullpointer.cache.evictionpolicy.LRUEvictionPolicy;
import org.nullpointer.cache.storage.InMemoryStorage;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCacheTest {

    private Cache<String, Integer> cache;
    private static CacheFactory<String, Integer> factory;

    @BeforeAll
    static void init() {
        factory = new CacheFactory<>();
    }

    @BeforeEach
    void setUp() {
        cache = factory.defaultCache(3);
    }

    private Cache<String, Integer> buildCache(int capacity) {
        return new SimpleCache<>(new InMemoryStorage<>(capacity), new LRUEvictionPolicy<>());
    }

    @Test
    void getMissingKeyReturnsNull() {
        assertNull(cache.get("absent"));
    }

    @Test
    void setThenGetReturnsStoredValue() {
        cache.set("x", 10);
        assertEquals(10, cache.get("x"));
    }

    @Test
    void settingExistingKeyOverwritesValue() {
        cache.set("x", 1);
        cache.set("x", 99);
        assertEquals(99, cache.get("x"));
    }

    @Test
    void lruKeyIsEvictedWhenCacheIsFull() {
        // insertion order: a → b → c; "a" is LRU
        cache.set("a", 1);
        cache.set("b", 2);
        cache.set("c", 3);
        cache.set("d", 4); // triggers eviction of "a"

        assertNull(cache.get("a"));
        assertEquals(2, cache.get("b"));
        assertEquals(3, cache.get("c"));
        assertEquals(4, cache.get("d"));
    }

    @Test
    void getAccessPromotesKeyAndProtectsItFromEviction() {
        cache.set("a", 1);
        cache.set("b", 2);
        cache.set("c", 3);
        cache.get("a");    // promote "a" → LRU order: [b, c, a]
        cache.set("d", 4); // "b" is evicted

        assertNull(cache.get("b"));
        assertEquals(1, cache.get("a"));
        assertEquals(3, cache.get("c"));
        assertEquals(4, cache.get("d"));
    }

    @Test
    void updateViaSetPromotesKeyAndProtectsItFromEviction() {
        cache.set("a", 1);
        cache.set("b", 2);
        cache.set("c", 3);
        cache.set("a", 9); // overwrite "a" → LRU order: [b, c, a]
        cache.set("d", 4); // "b" is evicted

        assertNull(cache.get("b"));
        assertEquals(9, cache.get("a"));
        assertEquals(3, cache.get("c"));
        assertEquals(4, cache.get("d"));
    }

    @Test
    void multipleSequentialEvictionsPreserveLruOrdering() {
        cache.set("a", 1);
        cache.set("b", 2);
        cache.set("c", 3);
        cache.set("d", 4); // evicts "a"
        cache.set("e", 5); // evicts "b"
        cache.set("f", 6); // evicts "c"

        assertNull(cache.get("a"));
        assertNull(cache.get("b"));
        assertNull(cache.get("c"));
        assertEquals(4, cache.get("d"));
        assertEquals(5, cache.get("e"));
        assertEquals(6, cache.get("f"));
    }

    @Test
    void capacityOfOneAlwaysEvictsPreviousEntry() {
        Cache<String, Integer> singleSlot = buildCache(1);
        singleSlot.set("a", 1);
        singleSlot.set("b", 2);

        assertNull(singleSlot.get("a"));
        assertEquals(2, singleSlot.get("b"));
    }

    @Test
    void interlevedGetsAndSetsEvictCorrectKey() {
        cache.set("a", 1);
        cache.set("b", 2);
        cache.get("a");    // LRU order: [b, a]
        cache.set("c", 3); // LRU order: [b, a, c]
        cache.get("b");    // LRU order: [a, c, b]
        cache.set("d", 4); // "a" is evicted

        assertNull(cache.get("a"));
        assertEquals(2, cache.get("b"));
        assertEquals(3, cache.get("c"));
        assertEquals(4, cache.get("d"));
    }
}
