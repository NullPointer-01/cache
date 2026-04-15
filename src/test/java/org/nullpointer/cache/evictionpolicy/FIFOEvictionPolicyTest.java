package org.nullpointer.cache.evictionpolicy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FIFOEvictionPolicyTest {

    private FIFOEvictionPolicy<String> policy;

    @BeforeEach
    void setUp() {
        policy = new FIFOEvictionPolicy<>();
    }

    @Test
    void evictionCandidateIsEmptyWhenNothingAccessed() {
        assertEquals(Optional.empty(), policy.evictionCandidate());
    }

    @Test
    void evictionCandidateIsIdempotent() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        assertEquals(policy.evictionCandidate(), policy.evictionCandidate());
    }

    @Test
    void evictionCandidateIsFirstInsertedKey() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        policy.onKeyAccess("c");
        assertEquals(Optional.of("a"), policy.evictionCandidate());
    }

    @Test
    void reAccessingKeyDoesNotChangeItsEvictionOrder() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        policy.onKeyAccess("a"); // re-access — "a" must remain first
        assertEquals(Optional.of("a"), policy.evictionCandidate());
    }

    @Test
    void onKeyRemoveAdvancesToNextInsertedKey() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        policy.onKeyAccess("c");
        policy.onKeyRemove("a");
        assertEquals(Optional.of("b"), policy.evictionCandidate());
    }

    @Test
    void onKeyRemoveOfLastKeyMakesCandidateEmpty() {
        policy.onKeyAccess("a");
        policy.onKeyRemove("a");
        assertEquals(Optional.empty(), policy.evictionCandidate());
    }

    @Test
    void removingMiddleKeyPreservesOrderOfRemainingKeys() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        policy.onKeyAccess("c");
        policy.onKeyRemove("b"); // remove middle
        assertEquals(Optional.of("a"), policy.evictionCandidate());
        policy.onKeyRemove("a");
        assertEquals(Optional.of("c"), policy.evictionCandidate());
    }
}