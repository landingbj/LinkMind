package ai.common.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LRUCache<K, V> {
    private final int maxCacheSize;
    private long expirationTimeInMillis;
    private final LinkedHashMap<K, V> map;
    private final Map<K, Long> timestampMap;
    private final Lock lock = new ReentrantLock();
    private ScheduledExecutorService executorService;
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong putCount = new AtomicLong();
    private final AtomicLong expirationCount = new AtomicLong();
    private final AtomicLong capacityEvictionCount = new AtomicLong();
    private final AtomicLong explicitRemovalCount = new AtomicLong();

    public LRUCache(int capacity, long expirationTime, TimeUnit timeUnit) {
        this(capacity);
        if (expirationTime <= 0) {
            throw new IllegalArgumentException("expirationTime must be greater than zero");
        }
        this.expirationTimeInMillis = timeUnit.toMillis(expirationTime);
        this.executorService = Executors.newSingleThreadScheduledExecutor();
        this.executorService.scheduleAtFixedRate(this::removeExpiredEntries, expirationTimeInMillis, expirationTimeInMillis, TimeUnit.MILLISECONDS);
    }

    public LRUCache(int capacity) {
        this.maxCacheSize = capacity;
        this.map = new LinkedHashMap<K, V>(capacity, 0.75F, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean shouldEvict = this.size() > LRUCache.this.maxCacheSize;
                if (shouldEvict) {
                    timestampMap.remove(eldest.getKey());
                    capacityEvictionCount.incrementAndGet();
                }
                return shouldEvict;
            }
        };
        this.timestampMap = new LinkedHashMap<>();
        this.expirationTimeInMillis = -1;
        this.executorService = null;
    }

    public boolean containsKey(K key) {
        lock.lock();
        try {
            if (expirationTimeInMillis != -1 && isExpired(key)) {
                removeExpired(key);
                missCount.incrementAndGet();
                return false;
            }
            boolean contains = map.containsKey(key);
            if (contains) {
                hitCount.incrementAndGet();
            } else {
                missCount.incrementAndGet();
            }
            return contains;
        } finally {
            lock.unlock();
        }
    }

    public V get(K key) {
        lock.lock();
        try {
            if (expirationTimeInMillis != -1 && isExpired(key)) {
                removeExpired(key);
                missCount.incrementAndGet();
                return null;
            }
            V value = map.get(key);
            if (value != null || map.containsKey(key)) {
                hitCount.incrementAndGet();
            } else {
                missCount.incrementAndGet();
            }
            return value;
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            map.put(key, value);
            putCount.incrementAndGet();
            if (expirationTimeInMillis != -1) {
                timestampMap.put(key, System.currentTimeMillis());
            }
        } finally {
            lock.unlock();
        }
    }

    public V remove(K key) {
        lock.lock();
        try {
            timestampMap.remove(key);
            V removed = map.remove(key);
            if (removed != null) {
                explicitRemovalCount.incrementAndGet();
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a snapshot of the current keys. Safe to iterate without holding
     * the cache lock; expired entries are skipped (and removed) if expiration
     * is enabled.
     */
    public List<K> keys() {
        lock.lock();
        try {
            List<K> snapshot = new ArrayList<>(map.size());
            if (expirationTimeInMillis == -1) {
                snapshot.addAll(map.keySet());
                return snapshot;
            }
            for (K key : new ArrayList<>(map.keySet())) {
                if (isExpired(key)) {
                    removeExpired(key);
                } else {
                    snapshot.add(key);
                }
            }
            return snapshot;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    public CacheStats getStats() {
        return new CacheStats(hitCount.get(), missCount.get(), putCount.get(), expirationCount.get(),
                capacityEvictionCount.get(), explicitRemovalCount.get(), size());
    }

    private boolean isExpired(K key) {
        Long insertionTime = timestampMap.get(key);
        return insertionTime == null || (System.currentTimeMillis() - insertionTime >= expirationTimeInMillis);
    }

    private void removeExpired(K key) {
        timestampMap.remove(key);
        if (map.remove(key) != null) {
            expirationCount.incrementAndGet();
        }
    }

    private void removeExpiredEntries() {
        if (expirationTimeInMillis == -1) {
            return;
        }
        lock.lock();
        try {
            Iterator<Map.Entry<K, Long>> iterator = timestampMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<K, Long> entry = iterator.next();
                if (System.currentTimeMillis() - entry.getValue() >= expirationTimeInMillis) {
                    map.remove(entry.getKey());
                    iterator.remove();
                    expirationCount.incrementAndGet();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public void clear() {
        lock.lock();
        try {
            map.clear();
            timestampMap.clear();
        } finally {
            lock.unlock();
        }
    }

    public static final class CacheStats {
        private final long hits;
        private final long misses;
        private final long puts;
        private final long expirations;
        private final long capacityEvictions;
        private final long explicitRemovals;
        private final int size;

        private CacheStats(long hits, long misses, long puts, long expirations,
                           long capacityEvictions, long explicitRemovals, int size) {
            this.hits = hits;
            this.misses = misses;
            this.puts = puts;
            this.expirations = expirations;
            this.capacityEvictions = capacityEvictions;
            this.explicitRemovals = explicitRemovals;
            this.size = size;
        }

        public long getHits() {
            return hits;
        }

        public long getMisses() {
            return misses;
        }

        public long getPuts() {
            return puts;
        }

        public long getExpirations() {
            return expirations;
        }

        public long getCapacityEvictions() {
            return capacityEvictions;
        }

        public long getExplicitRemovals() {
            return explicitRemovals;
        }

        public int getSize() {
            return size;
        }
    }
}
