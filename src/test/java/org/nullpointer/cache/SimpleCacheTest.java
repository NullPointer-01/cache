package org.nullpointer.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCacheTest {

    private SimpleCache<String, Integer> cache;

    @BeforeEach
    void setUp() {
        cache = new SimpleCache<>(CacheConfig.<String>builder().capacity(3).build());
    }

    @AfterEach
    void tearDown() {
        cache.stop();
    }

    private SimpleCache<String, Integer> buildCache(int capacity) {
        return new SimpleCache<>(CacheConfig.<String>builder().capacity(capacity).build());
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
        cache.runMaintenance();

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
        cache.runMaintenance();

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
        cache.runMaintenance();

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
        cache.runMaintenance();

        assertNull(cache.get("a"));
        assertNull(cache.get("b"));
        assertNull(cache.get("c"));
        assertEquals(4, cache.get("d"));
        assertEquals(5, cache.get("e"));
        assertEquals(6, cache.get("f"));
    }

    @Test
    void capacityOfOneAlwaysEvictsPreviousEntry() {
        SimpleCache<String, Integer> singleSlot = buildCache(1);
        singleSlot.set("a", 1);
        singleSlot.set("b", 2);
        singleSlot.runMaintenance();

        assertNull(singleSlot.get("a"));
        assertEquals(2, singleSlot.get("b"));
        singleSlot.stop();
    }

    @Test
    void interlevedGetsAndSetsEvictCorrectKey() {
        cache.set("a", 1);
        cache.set("b", 2);
        cache.runMaintenance();  // flush add events → LRU: [a, b]
        cache.get("a");          // promote a → read buffer
        cache.runMaintenance();  // flush → LRU: [b, a]
        cache.set("c", 3);
        cache.runMaintenance();  // flush → LRU: [b, a, c]
        cache.get("b");          // promote b → read buffer
        cache.set("d", 4);       // add d → write buffer
        cache.runMaintenance();  // Phase 1: onKeyAccess(d) → Phase 2: onKeyAccess(b)
                                 // LRU: [a, c, d, b] → evict a

        assertNull(cache.get("a"));
        assertEquals(2, cache.get("b"));
        assertEquals(3, cache.get("c"));
        assertEquals(4, cache.get("d"));
    }

    @Test
    void exactlyCapacityKeysStoredWithoutEviction() {
        cache.set("a", 1);
        cache.set("b", 2);
        cache.set("c", 3);

        assertEquals(1, cache.get("a"));
        assertEquals(2, cache.get("b"));
        assertEquals(3, cache.get("c"));
    }

    @Test
    void overwritingKeyAtFullCapacityDoesNotEvictAnyEntry() {
        cache.set("a", 1);
        cache.set("b", 2);
        cache.set("c", 3); // full
        cache.set("a", 99); // overwrite — must not evict "b" or "c"
        cache.runMaintenance();

        assertEquals(99, cache.get("a"));
        assertEquals(2, cache.get("b"));
        assertEquals(3, cache.get("c"));
    }

    @Test
    void removedKeyIsNoLongerRetrievable() {
        cache.set("a", 1);
        cache.remove("a");
        assertNull(cache.get("a"));
    }

    @Test
    void removeOnAbsentKeyIsNoOp() {
        cache.set("a", 1);
        assertDoesNotThrow(() -> cache.remove("ghost"));
        assertEquals(1, cache.get("a"));
    }

    @Test
    void removingKeyFreesCapacityForNewEntry() {
        cache.set("a", 1);
        cache.set("b", 2);
        cache.set("c", 3); // full
        cache.remove("b");
        cache.set("d", 4); // must not evict — slot is free
        cache.runMaintenance();

        assertEquals(1, cache.get("a"));
        assertNull(cache.get("b"));
        assertEquals(3, cache.get("c"));
        assertEquals(4, cache.get("d"));
    }
}
