package ai.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LRUCacheTest {
    private LRUCache<Integer, String> cacheWithExpiration;
    private LRUCache<Integer, String> cacheWithoutExpiration;

    @BeforeEach
    void setUp() {
        cacheWithExpiration = new LRUCache<>(3, 100, TimeUnit.MILLISECONDS);
        cacheWithoutExpiration = new LRUCache<>(3);
    }

    @AfterEach
    void tearDown() {
        cacheWithExpiration.shutdown();
    }

    @Test
    void testPutAndGet() {
        cacheWithoutExpiration.put(1, "One");
        assertEquals("One", cacheWithoutExpiration.get(1));
        assertNull(cacheWithoutExpiration.get(2));
    }

    @Test
    void testEvictionWhenMaxSizeExceeded() {
        cacheWithoutExpiration.put(1, "One");
        cacheWithoutExpiration.put(2, "Two");
        cacheWithoutExpiration.put(3, "Three");
        cacheWithoutExpiration.put(4, "Four");

        assertNull(cacheWithoutExpiration.get(1));
        assertEquals("Two", cacheWithoutExpiration.get(2));
        assertEquals("Three", cacheWithoutExpiration.get(3));
        assertEquals("Four", cacheWithoutExpiration.get(4));
    }

    @Test
    void testContainsKey() {
        cacheWithoutExpiration.put(1, "One");
        assertTrue(cacheWithoutExpiration.containsKey(1));
        assertFalse(cacheWithoutExpiration.containsKey(2));
    }

    @Test
    void testExpiration() throws InterruptedException {
        cacheWithExpiration.put(1, "One");
        Thread.sleep(150);

        assertNull(cacheWithExpiration.get(1));
        assertFalse(cacheWithExpiration.containsKey(1));
    }

    @Test
    void testRemove() {
        cacheWithoutExpiration.put(1, "One");
        assertEquals("One", cacheWithoutExpiration.remove(1));
        assertNull(cacheWithoutExpiration.get(1));
    }

    @Test
    void testSize() {
        cacheWithoutExpiration.put(1, "One");
        cacheWithoutExpiration.put(2, "Two");

        assertEquals(2, cacheWithoutExpiration.size());
        cacheWithoutExpiration.remove(1);
        assertEquals(1, cacheWithoutExpiration.size());
    }

    @Test
    void testCacheStatsTracksHitsMissesAndCapacityEvictions() {
        cacheWithoutExpiration.put(1, "One");
        cacheWithoutExpiration.put(2, "Two");
        assertEquals("One", cacheWithoutExpiration.get(1));
        assertNull(cacheWithoutExpiration.get(99));
        cacheWithoutExpiration.put(3, "Three");
        cacheWithoutExpiration.put(4, "Four");
        cacheWithoutExpiration.remove(1);

        LRUCache.CacheStats stats = cacheWithoutExpiration.getStats();
        assertEquals(1, stats.getHits());
        assertEquals(1, stats.getMisses());
        assertEquals(4, stats.getPuts());
        assertEquals(1, stats.getCapacityEvictions());
        assertEquals(1, stats.getExplicitRemovals());
    }

    @Test
    void testExpirationTimeMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new LRUCache<Integer, String>(3, 0, TimeUnit.SECONDS));
    }
}
