from concurrent.futures import ThreadPoolExecutor

from app.cache import RedirectCache
from app.database import Link


def link(code):
    return Link(1, code, f"https://example.com/{code}", 302, "2026-11-12T00:00:00+00:00")


def test_ttl_expires_without_refresh_on_hit():
    time = [0.0]
    cache = RedirectCache(2, 10, clock=lambda: time[0])
    assert cache.resolve("a", link)[1] == "MISS"
    time[0] = 9
    assert cache.resolve("a", link)[1] == "HIT"
    time[0] = 10
    assert cache.stats()["cache_size"] == 0
    assert cache.resolve("a", link)[1] == "MISS"
    assert cache.stats()["db_lookup_count"] == 2


def test_lru_evicts_least_recently_used():
    cache = RedirectCache(2, 60)
    cache.resolve("a", link)
    cache.resolve("b", link)
    cache.resolve("a", link)
    cache.resolve("c", link)
    assert cache.stats()["cache_size"] == 2
    assert cache.resolve("a", link)[1] == "HIT"
    assert cache.resolve("b", link)[1] == "MISS"


def test_clear_and_reset_are_independent():
    cache = RedirectCache(2, 60)
    cache.resolve("a", link)
    cache.reset_stats()
    assert cache.stats()["cache_size"] == 1
    assert cache.stats()["redirect_requests"] == 0
    assert cache.resolve("a", link)[1] == "HIT"
    cache.clear()
    assert cache.stats()["cache_size"] == 0
    assert cache.stats()["cache_hits"] == 1


def test_concurrent_redirects_count_exactly_once():
    cache = RedirectCache(2, 60)
    with ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(lambda _: cache.resolve("a", link), range(50)))
    assert cache.stats() == dict(redirect_requests=50, cache_hits=49, cache_misses=1,
                                db_lookup_count=1, not_found=0, cache_size=1, hit_rate=49 / 50)
