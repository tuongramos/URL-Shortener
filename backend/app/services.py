from dataclasses import asdict

from .cache import RedirectCache
from .config import Settings
from .database import Database, Link
from .schemas import CreateLink, LinkResponse


class LinkService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.database = Database(settings.database_path)
        self.cache = RedirectCache(settings.cache_max_items, settings.cache_ttl_seconds)

    def response(self, link: Link, reused: bool) -> LinkResponse:
        return LinkResponse(
            **asdict(link),
            short_url=f"{self.settings.public_base_url}/r/{link.short_code}",
            reused=reused,
        )

    def create(self, payload: CreateLink) -> LinkResponse:
        link, reused = self.database.create_or_get(payload.long_url, payload.redirect_status)
        return self.response(link, reused)
