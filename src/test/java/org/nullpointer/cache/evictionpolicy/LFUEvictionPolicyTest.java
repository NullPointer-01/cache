package org.nullpointer.cache.evictionpolicy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LFUEvictionPolicyTest {

    private LFUEvictionPolicy<String> policy;

    @BeforeEach
    void setUp() {
        policy = new LFUEvictionPolicy<>();
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
    void keyWithLowestFrequencyIsCandidate() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        policy.onKeyAccess("b"); // freq: a=1, b=2
        assertEquals(Optional.of("a"), policy.evictionCandidate());
    }

    @Test
    void repeatedAccessIncreasesFrequencyProtectingKeyFromEviction() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("a");
        policy.onKeyAccess("a"); // freq: a=3
        policy.onKeyAccess("b"); // freq: b=1
        assertEquals(Optional.of("b"), policy.evictionCandidate());
    }

    @Test
    void tieBreakEvictsLeastRecentlyAccessedAmongSameFrequency() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        policy.onKeyAccess("c"); // all freq=1; insertion order: a, b, c
        assertEquals(Optional.of("a"), policy.evictionCandidate());
    }

    @Test
    void accessingKeyInTiePromotesItOutOfMinFreqBucket() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b"); // freq: a=1, b=1; LRU of min-bucket is "a"
        policy.onKeyAccess("a"); // freq: a=2, b=1; "b" is now sole min-freq key
        assertEquals(Optional.of("b"), policy.evictionCandidate());
    }

    @Test
    void onKeyRemoveUpdatesEvictionCandidate() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("b");
        policy.onKeyAccess("b"); // freq: a=1, b=2
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
    void newKeyAfterRemovalStartsAtFrequencyOne() {
        policy.onKeyAccess("a");
        policy.onKeyAccess("a");
        policy.onKeyAccess("a"); // freq: a=3
        policy.onKeyRemove("a");
        policy.onKeyAccess("b"); // freq: b=1
        assertEquals(Optional.of("b"), policy.evictionCandidate());
    }
}