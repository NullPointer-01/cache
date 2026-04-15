package org.nullpointer.cache.evictionpolicy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LRUEvictionPolicyTest {

    private LRUEvictionPolicy<String> policy;

    @BeforeEach
    void setUp() {
        policy = new LRUEvictionPolicy<>();
    }

    @Test
    void evictionCandidateIsEmptyWhenNothingAdded() {
        assertEquals(Optional.empty(), policy.evictionCandidate());
    }

    @Test
    void evictionCandidateReturnsLeastRecentlyAccessedKey() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        policy.onKeyAccess("c");
        assertEquals(Optional.of("a"), policy.evictionCandidate());
    }

    @Test
    void evictionCandidateIsIdempotent() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        assertEquals(policy.evictionCandidate(), policy.evictionCandidate());
    }

    @Test
    void onKeyAccessPromotesKeySoItIsNoLongerCandidate() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        policy.onKeyAccess("a"); // "a" is now MRU; "b" is LRU
        assertEquals(Optional.of("b"), policy.evictionCandidate());
    }

    @Test
    void onKeyRemoveAdvancesEvictionCandidateToNextLruKey() {
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
    void evictionCandidateDoesNotMutateState() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        // calling evictionCandidate multiple times must not advance the pointer
        policy.evictionCandidate();
        policy.evictionCandidate();
        assertEquals(Optional.of("a"), policy.evictionCandidate());
    }

    @Test
    void repeatedOnKeyAccessKeepsKeyAsMostRecent() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        policy.onKeyAccess("a");
        policy.onKeyAccess("a");
        policy.onKeyAccess("a");
        assertEquals(Optional.of("b"), policy.evictionCandidate());
    }

    @Test
    void lruOrderAfterMixedAccessAndRemoveOperations() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        policy.onKeyAccess("c");
        policy.onKeyAccess("a"); // order: [b, c, a]
        policy.onKeyAccess("c"); // order: [b, a, c]
        assertEquals(Optional.of("b"), policy.evictionCandidate());
        policy.onKeyRemove("b");
        assertEquals(Optional.of("a"), policy.evictionCandidate());
        policy.onKeyRemove("a");
        assertEquals(Optional.of("c"), policy.evictionCandidate());
    }
}
