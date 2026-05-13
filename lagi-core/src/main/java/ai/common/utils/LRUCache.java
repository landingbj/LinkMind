package ai.common.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LRUCache<K, V> {
    private final int maxCacheSize;
    private long expirationTimeInMillis;
    private final LinkedHashMap<K, V> map;
    private final Map<K, Long> timestampMap;
    private final Lock lock = new ReentrantLock();
    private ScheduledExecutorService executorService;

    public LRUCache(int capacity, long expirationTime, TimeUnit timeUnit) {
        this(capacity);
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
                return this.size() > LRUCache.this.maxCacheSize;
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
                remove(key);
                return false;
            }
            return map.containsKey(key);
        } finally {
            lock.unlock();
        }
    }

    public V get(K key) {
        lock.lock();
        try {
            if (expirationTimeInMillis != -1 && isExpired(key)) {
                remove(key);
                return null;
            }
            return map.get(key);
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            map.put(key, value);
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
            return map.remove(key);
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
                    map.remove(key);
                    timestampMap.remove(key);
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

    private boolean isExpired(K key) {
        Long insertionTime = timestampMap.get(key);
        return insertionTime == null || (System.currentTimeMillis() - insertionTime >= expirationTimeInMillis);
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
}
