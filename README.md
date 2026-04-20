# Cache

A high-performance, thread-safe, in-memory cache library in Java 17 with lock-free concurrency, pluggable eviction policies (LRU/LFU/FIFO), per-key TTL, and asynchronous maintenance via striped event buffers.

## Features

- **Lock-free reads and writes** — data operations use `ConcurrentHashMap`; no lock contention on the hot path
- **Pluggable eviction policies** — LRU, LFU, and FIFO out of the box; implement `EvictionPolicy<K>` for custom strategies
- **Per-key TTL** — optional expiry per entry via `set(key, value, Duration)`, with lazy expiry on reads and background cleanup
- **Striped event buffers** — 16 thread-striped buffers minimize contention across producers
- **MPSC circular buffer** — fixed-size, allocation-free ring buffer for read-access tracking with recency-biased overflow
- **Asynchronous maintenance** — a configurable background thread drains buffered events, expires entries, and enforces capacity

---

## Architecture

```
┌─────────────────────────────────────────────────────-─────────┐
│                         SimpleCache                           │
│                                                               │
│  ┌───────────────────┐        ┌───────────────────────────┐   │
│  │ ConcurrentHashMap │        │     EvictionPolicy        │   │
│  │ <K, CacheEntry>   │        │  (LRU / LFU / FIFO)       │   │
│  └────────┬──────────┘        └────────────-▲─────────────┘   │
│           │                                 │                 │
│    get/set/remove                    drain events             │
│           │                                 │                 │
│  ┌────────▼─────────────────────────────────┴────────────-─┐  │
│  │               16 × StripedBuffer                        │  │
│  │  ┌──────────────────┐   ┌────────────────────────────┐  │  │
│  │  │  CircularBuffer  │   │  ConcurrentLinkedQueue     │  │  │
│  │  │  (read events)   │   │  (write events: ADD/UPD/RM)│  │  │
│  │  └──────────────────┘   └────────────────────────────┘  │  │
│  └─────────────────────────────────────────────────────────┘  │
│                              ▲                                │
│                    scheduled │ (configurable interval)        │
│                 ┌────────────┴──────────┐                     │
│                 │  ScheduledExecutor    │                     │
│                 └───────────────────────┘                     │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐     │
│  │  PriorityQueue<CacheEntry> — TTL expiry queue        │     │
│  └──────────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────-┘
```

### Hot Path (`get` / `set` / `remove`)

Operates directly on the `ConcurrentHashMap`, then records an event into the calling thread's striped buffer. **No locks acquired.**

### Maintenance Phase

A single background thread (or opportunistic `tryLock` on writes) drains buffered events into the eviction policy, processes the TTL expiry queue, and removes entries exceeding capacity.

---

## Usage

### Basic

```java
import org.nullpointer.cache.*;
import java.time.Duration;

CacheConfig<String> config = CacheConfig.<String>builder()
        .capacity(1000)
        .build();

try (Cache<String, String> cache = new SimpleCache<>(config)) {
    cache.set("key", "value");
    cache.set("session", "abc123", Duration.ofMinutes(30)); // per-key TTL

    String val = cache.get("key");   // "value"
    cache.remove("key");
}
```

### Custom Eviction Policy & Maintenance Interval

```java
import org.nullpointer.cache.evictionpolicy.LFUEvictionPolicy;

CacheConfig<String> config = CacheConfig.<String>builder()
        .capacity(500)
        .evictionPolicy(new LFUEvictionPolicy<>())
        .maintenanceInterval(Duration.ofMillis(200))
        .build();

Cache<String, String> cache = new SimpleCache<>(config);
```

---

## Eviction Policies

| Policy | Class | Eviction Strategy | Time Complexity |
|--------|-------|-------------------|-----------------|
| **LRU** | `LRUEvictionPolicy` | Least recently accessed key | O(1) access, O(1) eviction |
| **LFU** | `LFUEvictionPolicy` | Least frequently accessed key (ties broken by insertion order) | O(1) access, O(1) eviction, O(n) remove on bucket drain |
| **FIFO** | `FIFOEvictionPolicy` | Oldest inserted key (re-access does not change order) | O(1) access, O(1) eviction |

Implement `EvictionPolicy<K>` for custom strategies:

```java
public interface EvictionPolicy<K> {
    void onKeyAccess(K key);
    void onKeyRemove(K key);
    Optional<K> evictionCandidate();
}
```

---

## Core Components

### `SimpleCache`

The primary cache implementation. Coordinates the data map, striped buffers, expiry queue, and eviction policy under a deferred maintenance model.

### `CacheConfig`

Builder-pattern configuration holding capacity, eviction policy, and maintenance interval. Defaults to LRU with 1-second maintenance.

### `CacheEntry`

Wraps a key-value pair with an optional expiry timestamp (`expiresAtNanos`). Implements `Comparable` for priority queue ordering by expiry time.

### `CircularBuffer`

Lock-free MPSC (multi-producer, single-consumer) ring buffer with power-of-two capacity. Producers claim slots via `AtomicLong.getAndIncrement()`; the consumer snaps forward if lapped. 256 slots per stripe.

- **Zero allocation** — pre-allocated `AtomicReferenceArray`
- **Recency-biased overflow** — oldest unread events silently overwritten
- **Structurally bounded** — no racy size checks

### `StripedBuffer`

Combines a `CircularBuffer` (read events) with a `ConcurrentLinkedQueue` (write events). Each thread maps to one of 16 stripes via `threadId % 16`.

### Write Events vs Read Events

| | Read events | Write events |
|---|---|---|
| Buffer | `CircularBuffer` (bounded, lossy) | `ConcurrentLinkedQueue` (unbounded) |
| Loss tolerance | Tolerated — advisory for eviction ordering | Not tolerated — affects correctness |
| Event types | Key access | `ADD`, `UPDATE`, `REMOVE` |

---

## TTL / Expiry

- **Per-key TTL**: `cache.set(key, value, Duration.ofMinutes(5))`
- **No TTL**: `cache.set(key, value)` or `cache.set(key, value, null)` — entry never expires
- **Lazy expiry**: `get()` checks `isExpired()` and CAS-removes stale entries immediately
- **Background expiry**: maintenance drains a `PriorityQueue<CacheEntry>` ordered by `expiresAtNanos`, removing entries whose deadline has passed

---

## Concurrency Model

1. **Data layer** — `ConcurrentHashMap` provides lock-free reads and writes
2. **Event recording** — thread-striped buffers eliminate cross-thread contention
3. **Maintenance** — a `ReentrantLock` serializes all eviction policy mutations
   - **Opportunistic**: `tryLock()` on every `set()`/`remove()` — drains if lock is free
   - **Scheduled**: background thread acquires lock every 1 second (configurable)
4. **Drain order**: write buffer drained first (correctness), then read buffer (bounded to 16 per stripe per run)

---

## Design Trade-offs

| Property | Guaranteed |
|---|---|
| Lock-free reads (`get`) | ✅ |
| Lock-free writes at data layer (`map.put`) | ✅ |
| Eviction policy only touched under lock | ✅ |
| No data races / corrupted structures | ✅ |
| Eventual capacity convergence | ✅ |
| Exact capacity enforcement at all times | ❌ |
| Perfectly ordered eviction decisions | ❌ |

**Temporary capacity overshoot**: concurrent `set()` calls may push map size beyond capacity until the next maintenance run.

**Cross-stripe event reordering**: events from different stripes may reach the eviction policy in a different order than they occurred.

**Stale eviction decisions**: `get()` accesses are buffered and not visible to the eviction policy until drained.

---

## Building & Testing

Requires **Java 17+** and **Maven 3.6+**.

```bash
# Compile
mvn compile

# Run all tests
mvn test
```

---

## License

MIT
