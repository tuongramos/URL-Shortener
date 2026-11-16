from collections import OrderedDict
from threading import RLock
from time import monotonic
from typing import Callable

from .database import Link


class RedirectCache:
    """Cache TTL + LRU; bộ đếm và thao tác tra cứu được đồng bộ trong một tiến trình."""

    def __init__(self, max_items: int, ttl: float, clock: Callable[[], float] = monotonic):
        self.max_items = max_items
        self.ttl = ttl
        self.clock = clock
        self._lock = RLock()
        self._items: OrderedDict[str, tuple[Link, float]] = OrderedDict()
        self._counters = self._empty_counters()

    @staticmethod
    def _empty_counters() -> dict[str, int]:
        return dict(redirect_requests=0, cache_hits=0, cache_misses=0, db_lookup_count=0, not_found=0)

    def _purge_expired(self) -> None:
        now = self.clock()
        for code, (_, expires) in list(self._items.items()):
            if expires <= now:
                del self._items[code]

    def resolve(self, code: str, lookup: Callable[[str], Link | None]) -> tuple[Link | None, str]:
        with self._lock:
            self._counters["redirect_requests"] += 1
            self._purge_expired()
            if code in self._items:
                self._counters["cache_hits"] += 1
                self._items.move_to_end(code)
                return self._items[code][0], "HIT"
            self._counters["cache_misses"] += 1
            self._counters["db_lookup_count"] += 1
            link = lookup(code)
            if link is None:
                self._counters["not_found"] += 1
                return None, "MISS"
            self._items[code] = (link, self.clock() + self.ttl)
            if len(self._items) > self.max_items:
                self._items.popitem(last=False)
            return link, "MISS"

    def stats(self) -> dict[str, int | float]:
        with self._lock:
            self._purge_expired()
            hits, misses = self._counters["cache_hits"], self._counters["cache_misses"]
            return {**self._counters, "cache_size": len(self._items), "hit_rate": hits / (hits + misses) if hits + misses else 0.0}

    def clear(self) -> None:
        with self._lock:
            self._items.clear()

    def reset_stats(self) -> None:
        with self._lock:
            self._counters = self._empty_counters()
