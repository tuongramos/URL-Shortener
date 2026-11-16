from pathlib import Path

import pytest

from app.config import PROJECT_ROOT, Settings


def test_paths_are_anchored_to_project(monkeypatch, tmp_path):
    monkeypatch.chdir(tmp_path)
    settings = Settings(database_path=Path("backend/data/shortener.db"))
    assert settings.database_path == PROJECT_ROOT / "backend/data/shortener.db"


@pytest.mark.parametrize("base", ["ftp://example.com", "https://", "https://example.com/path", "https://example.com?q=1", "http://example.com:bad"])
def test_invalid_public_base_url(base):
    with pytest.raises(ValueError):
        Settings(public_base_url=base)


@pytest.mark.parametrize("kwargs", [{"cache_max_items": 0}, {"cache_ttl_seconds": 0}, {"cache_ttl_seconds": float("nan")}])
def test_invalid_cache_settings(kwargs):
    with pytest.raises(ValueError):
        Settings(**kwargs)
