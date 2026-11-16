import os
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit

from dotenv import dotenv_values
from pydantic import AnyHttpUrl, TypeAdapter

PROJECT_ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class Settings:
    public_base_url: str = "http://127.0.0.1:8000"
    database_path: Path = PROJECT_ROOT / "backend/data/shortener.db"
    cache_max_items: int = 1000
    cache_ttl_seconds: float = 300
    demo_mode: bool = False
    frontend_dist: Path = PROJECT_ROOT / "frontend/dist"

    def __post_init__(self) -> None:
        base = self.public_base_url.rstrip("/")
        TypeAdapter(AnyHttpUrl).validate_python(base)
        parts = urlsplit(base)
        if (
            parts.scheme not in {"http", "https"} or not parts.hostname
            or parts.query or parts.fragment or parts.path or parts.username
            or any(c.isspace() for c in base)
        ):
            raise ValueError("PUBLIC_BASE_URL phải là địa chỉ HTTP/HTTPS không có path, query hoặc fragment")
        if self.cache_max_items < 1 or not 0 < self.cache_ttl_seconds < float("inf"):
            raise ValueError("Giới hạn cache và TTL phải lớn hơn 0 và hữu hạn")
        object.__setattr__(self, "public_base_url", base)
        for name in ("database_path", "frontend_dist"):
            path = Path(getattr(self, name))
            object.__setattr__(self, name, (PROJECT_ROOT / path).resolve())

    @classmethod
    def from_env(cls) -> "Settings":
        values = {**dotenv_values(PROJECT_ROOT / ".env"), **os.environ}
        demo = str(values.get("DEMO_MODE", "false")).lower()
        if demo not in {"true", "false", "1", "0"}:
            raise ValueError("DEMO_MODE chỉ nhận true/false hoặc 1/0")
        return cls(
            public_base_url=str(values.get("PUBLIC_BASE_URL", "http://127.0.0.1:8000")),
            database_path=Path(str(values.get("DATABASE_PATH", "backend/data/shortener.db"))),
            cache_max_items=int(str(values.get("CACHE_MAX_ITEMS", "1000"))),
            cache_ttl_seconds=float(str(values.get("CACHE_TTL_SECONDS", "300"))),
            demo_mode=demo in {"true", "1"},
        )
