package vn.lienket.server

import vn.lienket.shared.DemoStats

class RedirectCache(
    private val maxItems: Int,
    private val ttlSeconds: Double,
    private val clock: () -> Double = { System.nanoTime() / 1_000_000_000.0 },
) {
    private data class Entry(val link: Link, val expires: Double)
    private val items = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val lock = Any()
    private var requests = 0L
    private var hits = 0L
    private var misses = 0L
    private var lookups = 0L
    private var missing = 0L

    init { require(maxItems > 0 && ttlSeconds > 0 && ttlSeconds.isFinite()) }

    private fun purgeExpired() {
        val now = clock()
        items.entries.removeIf { it.value.expires <= now }
    }

    fun resolve(code: String, lookup: (String) -> Link?): Pair<Link?, String> = synchronized(lock) {
        requests++
        purgeExpired()
        val cached = items[code]
        if (cached != null) {
            hits++
            return@synchronized cached.link to "HIT"
        }
        misses++
        lookups++
        val link = lookup(code)
        if (link == null) {
            missing++
            return@synchronized null to "MISS"
        }
        items[code] = Entry(link, clock() + ttlSeconds)
        if (items.size > maxItems) items.remove(items.keys.first())
        link to "MISS"
    }

    fun stats(): DemoStats = synchronized(lock) {
        purgeExpired()
        DemoStats(requests, hits, misses, lookups, missing, items.size, if (hits + misses == 0L) 0.0 else hits.toDouble() / (hits + misses))
    }

    fun clear() = synchronized(lock) { items.clear() }
    fun resetStats() = synchronized(lock) { requests = 0; hits = 0; misses = 0; lookups = 0; missing = 0 }
}
