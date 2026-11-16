import sqlite3
from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace

import pytest
from fastapi.testclient import TestClient

from app.main import create_app


def create(client, url="https://example.com/article?chapter=8", mode=302):
    return client.post("/api/v1/links", json={"long_url": url, "redirect_status": mode})


def test_health_and_openapi(client):
    assert client.get("/api/v1/health").json() == {"status": "ok"}
    assert client.get("/docs").status_code == 200
    assert "/api/v1/links" in client.get("/openapi.json").json()["paths"]


@pytest.mark.parametrize("url", ["http://example.com", "https://example.com/a?x=%2F&b=1#part", "http://127.0.0.1:9000/"])
def test_create_and_reuse_preserves_url(client, url):
    response = create(client, f"  {url}  ")
    assert response.status_code == 201
    data = response.json()
    assert data["long_url"] == url
    assert data["short_url"] == f"http://127.0.0.1:8000/r/{data['short_code']}"
    assert data["reused"] is False
    assert data["created_at"].endswith(("Z", "+00:00"))
    repeated = create(client, url)
    assert repeated.status_code == 200
    assert repeated.json() == {**data, "reused": True}
    assert client.get(f"/r/{data['short_code']}").headers["location"] == url


def test_default_mode_and_separate_modes(client):
    temporary = client.post("/api/v1/links", json={"long_url": "https://example.com"}).json()
    permanent = create(client, "https://example.com", 301).json()
    assert temporary["redirect_status"] == 302
    assert permanent["short_code"] != temporary["short_code"]
    for data, policy in [(temporary, "no-store"), (permanent, "private, max-age=300")]:
        response = client.get(f"/r/{data['short_code']}")
        assert response.status_code == data["redirect_status"]
        assert response.headers["location"] == "https://example.com"
        assert response.headers["cache-control"] == policy
        assert response.headers["x-cache"] == "MISS"


@pytest.mark.parametrize("url", ["", "  ", "not a url", "example.com", "ftp://example.com", "javascript:alert(1)",
                                     "https://", "http:example.com", "https://example.com:bad", "https://example.com/a b",
                                     "https://example.com/\nheader", "https://example.com/\\evil", "https://example.com/" + "a" * 4096])
def test_invalid_urls(client, url):
    response = create(client, url)
    assert response.status_code == 422
    assert isinstance(response.json()["detail"], str)


@pytest.mark.parametrize("payload", [{}, {"long_url": None}, {"long_url": 42},
    {"long_url": "https://example.com", "redirect_status": 307},
    {"long_url": "https://example.com", "redirect_status": "301"},
    {"long_url": "https://example.com", "redirect_status": 301.0},
    {"long_url": "https://example.com", "redirect_status": True},
    {"long_url": "https://example.com", "extra": "bad"}])
def test_invalid_payloads(client, payload):
    assert client.post("/api/v1/links", json=payload).status_code == 422


def test_redirect_miss_hit_and_metadata_do_not_count(client):
    data = create(client).json()
    code = data["short_code"]
    assert client.get("/api/v1/demo/stats").json()["cache_size"] == 0
    assert client.get(f"/api/v1/links/{code}").json()["long_url"] == data["long_url"]
    assert client.get("/api/v1/demo/stats").json()["redirect_requests"] == 0
    assert client.get(f"/r/{code}").headers["x-cache"] == "MISS"
    assert client.get(f"/r/{code}").headers["x-cache"] == "HIT"
    expected = dict(redirect_requests=2, cache_hits=1, cache_misses=1,
                    db_lookup_count=1, not_found=0, cache_size=1, hit_rate=0.5)
    assert client.get("/api/v1/demo/stats").json() == expected
    assert client.get("/api/v1/demo/stats").json() == expected
    assert client.post("/api/v1/demo/stats/reset").json()["cache_size"] == 1
    assert client.get(f"/r/{code}").headers["x-cache"] == "HIT"
    assert client.post("/api/v1/demo/cache/clear").json()["redirect_requests"] == 1
    assert client.get(f"/r/{code}").headers["x-cache"] == "MISS"


def test_not_found_and_case_sensitivity(client, application):
    for index in range(1, 37):
        create(client, f"https://example.com/{index}")
    lower = client.get("/r/a")
    upper = client.get("/r/A")
    assert lower.headers["location"] == "https://example.com/10"
    assert upper.headers["location"] == "https://example.com/36"
    assert lower.headers["location"] != upper.headers["location"]
    missing = client.get("/r/missing-code")
    assert missing.status_code == 404
    assert missing.json()["detail"] == "Liên kết không tồn tại"
    before = application.state.service.cache.stats()
    assert client.get("/api/v1/links/missing-code").status_code == 404
    assert application.state.service.cache.stats() == before
    assert before["not_found"] == 1


def test_concurrent_duplicate_creation(client, settings):
    with ThreadPoolExecutor(max_workers=8) as pool:
        responses = list(pool.map(lambda _: create(client), range(16)))
    assert sum(response.status_code == 201 for response in responses) == 1
    assert {response.status_code for response in responses} == {200, 201}
    assert len({response.json()["short_code"] for response in responses}) == 1
    with sqlite3.connect(settings.database_path) as connection:
        assert connection.execute("SELECT COUNT(*) FROM links").fetchone()[0] == 1
        assert connection.execute("SELECT COUNT(*) FROM links WHERE short_code IS NULL").fetchone()[0] == 0


def test_restart_keeps_database_but_resets_ram(settings):
    with TestClient(create_app(settings), follow_redirects=False) as first:
        data = create(first).json()
        first.get(f"/r/{data['short_code']}")
        assert first.get("/api/v1/demo/stats").json()["cache_size"] == 1
    with TestClient(create_app(settings), follow_redirects=False) as second:
        assert second.get("/api/v1/demo/stats").json()["redirect_requests"] == 0
        assert second.get("/api/v1/demo/stats").json()["cache_size"] == 0
        response = second.get(f"/r/{data['short_code']}")
        assert response.status_code == 302
        assert response.headers["location"] == data["long_url"]
        assert response.headers["x-cache"] == "MISS"
        assert create(second).json()["id"] == data["id"]


def test_demo_disabled(settings):
    with TestClient(create_app(replace(settings, demo_mode=False))) as client:
        assert client.get("/api/v1/demo/stats").status_code == 404
        assert client.post("/api/v1/demo/cache/clear").status_code == 404
        assert client.post("/api/v1/demo/stats/reset").status_code == 404
        assert create(client).status_code == 201


def test_static_frontend_served_without_hiding_api(settings):
    settings.frontend_dist.mkdir()
    (settings.frontend_dist / "assets").mkdir()
    (settings.frontend_dist / "index.html").write_text("<h1>Rút gọn liên kết</h1>", encoding="utf-8")
    (settings.frontend_dist / "assets/main.js").write_text("console.log('ok')")
    with TestClient(create_app(settings)) as client:
        assert "Rút gọn liên kết" in client.get("/").text
        assert client.get("/assets/main.js").status_code == 200
        assert client.get("/api/v1/health").status_code == 200
        assert client.get("/api/unknown").status_code == 404


def test_missing_frontend_has_actionable_error(client):
    assert client.get("/").status_code == 503


def test_database_failure_has_no_internal_details(client, application, monkeypatch):
    def fail(*args):
        raise sqlite3.OperationalError("private internal path")
    monkeypatch.setattr(application.state.service.database, "create_or_get", fail)
    response = create(client)
    assert response.status_code == 503
    assert "private internal path" not in response.text
