package vn.lienket.server

import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class CoreTest {
    @Test fun bookBase62ExamplesAndBoundary() {
        mapOf(0L to "0", 1L to "1", 61L to "Z", 62L to "10", 11157L to "2TX", 2009215674938L to "zn9edcu", 3521614606208L to "10000000").forEach { (id, code) -> assertEquals(code, Base62.encode(id)) }
    }
    @Test fun base62RejectsNegative() { assertFailsWith<IllegalArgumentException> { Base62.encode(-1) } }
    private fun link(code: String) = Link(1, code, "https://example.com/$code", 302, "2026-11-17T00:00:00Z")
    @Test fun cacheFirstMissThenHit() {
        val cache = RedirectCache(2, 10.0)
        assertEquals("MISS", cache.resolve("a", ::link).second)
        assertEquals("HIT", cache.resolve("a", ::link).second)
        assertEquals(1L, cache.stats().dbLookupCount)
        assertEquals(0.5, cache.stats().hitRate)
    }
    @Test fun ttlDoesNotRefreshOnHit() {
        var time = 0.0
        val cache = RedirectCache(2, 10.0) { time }
        cache.resolve("a", ::link)
        time = 9.0
        assertEquals("HIT", cache.resolve("a", ::link).second)
        time = 10.0
        assertEquals(0, cache.stats().cacheSize)
        assertEquals("MISS", cache.resolve("a", ::link).second)
    }
    @Test fun lruEvictsLeastRecentlyUsed() {
        val cache = RedirectCache(2, 60.0)
        listOf("a", "b", "a", "c").forEach { cache.resolve(it, ::link) }
        assertEquals("HIT", cache.resolve("a", ::link).second)
        assertEquals("MISS", cache.resolve("b", ::link).second)
        assertEquals(2, cache.stats().cacheSize)
    }
    @Test fun resetAndClearAreIndependent() {
        val cache = RedirectCache(2, 60.0)
        cache.resolve("a", ::link)
        cache.resetStats()
        assertEquals(1, cache.stats().cacheSize)
        assertEquals(0L, cache.stats().redirectRequests)
        cache.resolve("a", ::link)
        cache.clear()
        assertEquals(0, cache.stats().cacheSize)
        assertEquals(1L, cache.stats().cacheHits)
    }
    @Test fun missingCodesCountButAreNotCached() {
        val cache = RedirectCache(2, 60.0)
        repeat(2) { assertNull(cache.resolve("missing") { null }.first) }
        assertEquals(2L, cache.stats().notFound)
        assertEquals(2L, cache.stats().dbLookupCount)
        assertEquals(0, cache.stats().cacheSize)
    }
    @Test fun concurrentCacheRequestsHaveExactCounters() {
        val cache = RedirectCache(2, 60.0)
        val pool = Executors.newFixedThreadPool(8)
        try { pool.invokeAll((1..50).map { Callable { cache.resolve("a", ::link) } }).forEach { it.get() } }
        finally { pool.shutdown() }
        assertEquals(50L, cache.stats().redirectRequests)
        assertEquals(49L, cache.stats().cacheHits)
        assertEquals(1L, cache.stats().dbLookupCount)
    }
    @Test fun concurrentDatabaseCreationOnlyInsertsOneRow() {
        val directory = Files.createTempDirectory("lienket-database-")
        val db = Database(directory.resolve("test.db"))
        db.initialize()
        val pool = Executors.newFixedThreadPool(8)
        try {
            val values = pool.invokeAll((1..16).map { Callable { db.createOrGet("https://example.com/concurrent", 302) } }).map { it.get() }
            assertEquals(1, values.count { !it.reused })
            assertEquals(1, values.map { it.link.id }.distinct().size)
            assertEquals("1", values.first().link.shortCode)
        } finally { pool.shutdown(); directory.toFile().deleteRecursively() }
    }
    @Test fun validatesServerConfiguration() {
        listOf("ftp://example.com", "https://example.com/path", "https://example.com?q=1", "http://example.com:99999").forEach { assertFails { Settings(publicBaseUrl = it) } }
        assertFails { Settings(cacheMaxItems = 0) }
        assertFails { Settings(cacheTtlSeconds = Double.NaN) }
    }
}
