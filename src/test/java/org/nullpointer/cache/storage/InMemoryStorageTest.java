package org.nullpointer.cache.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nullpointer.cache.exceptions.KeyNotFoundException;
import org.nullpointer.cache.exceptions.CapacityExceededException;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryStorageTest {

    private InMemoryStorage<String, Integer> storage;

    @BeforeEach
    void setUp() {
        storage = new InMemoryStorage<>(3);
    }

    @Test
    void addedValueIsRetrievable() {
        storage.add("a", 1);
        assertEquals(1, storage.get("a"));
    }

    @Test
    void overwritingExistingKeyUpdatesValue() {
        storage.add("a", 1);
        storage.add("a", 42);
        assertEquals(42, storage.get("a"));
    }

    @Test
    void overwritingExistingKeyDoesNotCountAgainstCapacity() {
        storage.add("a", 1);
        storage.add("b", 2);
        storage.add("c", 3);
        // Capacity is full but "a" already exists — should not throw
        assertDoesNotThrow(() -> storage.add("a", 99));
        assertEquals(99, storage.get("a"));
    }

    @Test
    void throwsCapacityExceededExceptionWhenCapacityExceeded() {
        storage.add("a", 1);
        storage.add("b", 2);
        storage.add("c", 3);
        assertThrows(CapacityExceededException.class, () -> storage.add("d", 4));
    }

    @Test
    void throwsKeyNotFoundExceptionForAbsentKey() {
        assertThrows(KeyNotFoundException.class, () -> storage.get("missing"));
    }

    @Test
    void removedKeyIsNoLongerRetrievable() {
        storage.add("a", 1);
        storage.remove("a");
        assertThrows(KeyNotFoundException.class, () -> storage.get("a"));
    }

    @Test
    void removingAbsentKeyThrowsKeyNotFoundException() {
        assertThrows(KeyNotFoundException.class, () -> storage.remove("ghost"));
    }

    @Test
    void removingKeyFreesCapacityForNewEntry() {
        storage.add("a", 1);
        storage.add("b", 2);
        storage.add("c", 3);
        storage.remove("b");
        assertDoesNotThrow(() -> storage.add("d", 4));
        assertEquals(4, storage.get("d"));
    }

    @Test
    void multipleDistinctKeysAreStoredAndRetrievedIndependently() {
        storage.add("x", 10);
        storage.add("y", 20);
        storage.add("z", 30);
        assertEquals(10, storage.get("x"));
        assertEquals(20, storage.get("y"));
        assertEquals(30, storage.get("z"));
    }

}
