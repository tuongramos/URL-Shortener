"""Kiểm tra demo trên HTTP thật, không tự đi theo redirect và không cần Internet."""
import argparse
import json
import sys
from urllib.error import HTTPError
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def main():
    # Windows có thể dùng cp1252 cho stdout khi lệnh được thu qua pipe.
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--target", default="http://127.0.0.1:9000/?chapter=8&source=demo#chapter-8")
    parser.add_argument("--follow", action="store_true", help="Mở đích thật; cần chạy trang offline ở cổng 9000")
    args = parser.parse_args()
    opener = build_opener(ProxyHandler({}), NoRedirect())

    def request(method, path, body=None):
        data = json.dumps(body).encode() if body is not None else (b"" if method == "POST" else None)
        req = Request(args.base_url.rstrip("/") + path, data=data, method=method,
                      headers={"Content-Type": "application/json"})
        try:
            response = opener.open(req, timeout=10)
        except HTTPError as error:
            response = error
        with response:
            payload = response.read()
            parsed = json.loads(payload) if "application/json" in response.headers.get("Content-Type", "") else payload.decode()
            return response.status, response.headers, parsed

    assert request("GET", "/api/v1/health")[2] == {"status": "ok"}
    body = {"long_url": args.target, "redirect_status": 302}
    status, _, link = request("POST", "/api/v1/links", body)
    assert status in {200, 201}
    status, _, repeated = request("POST", "/api/v1/links", body)
    assert status == 200 and repeated["reused"] and repeated["id"] == link["id"]
    assert request("POST", "/api/v1/demo/cache/clear")[0] == 200, "Cần bật DEMO_MODE=true trong .env"
    request("POST", "/api/v1/demo/stats/reset")
    path = f"/r/{link['short_code']}"
    for expected in ("MISS", "HIT"):
        status, headers, _ = request("GET", path)
        assert status == 302 and headers["Location"] == args.target
        assert headers["X-Cache"] == expected and headers["Cache-Control"] == "no-store"
        print(f"302 | X-Cache: {expected} | Location: {headers['Location']}")
    request("GET", f"/api/v1/links/{link['short_code']}")
    stats = request("GET", "/api/v1/demo/stats")[2]
    assert stats == dict(redirect_requests=2, cache_hits=1, cache_misses=1,
                         db_lookup_count=1, not_found=0, cache_size=1, hit_rate=0.5)
    print(json.dumps(stats, indent=2))
    _, _, permanent = request("POST", "/api/v1/links", {**body, "redirect_status": 301})
    status, headers, _ = request("GET", f"/r/{permanent['short_code']}")
    assert status == 301 and headers["Location"] == args.target
    assert headers["Cache-Control"] == "private, max-age=300"
    assert permanent["id"] != link["id"]
    assert request("GET", "/r/missing-code")[0] == 404
    assert request("POST", "/api/v1/links", {"long_url": "ftp://example.com"})[0] == 422
    if args.follow:
        with build_opener(ProxyHandler({})).open(link["short_url"], timeout=10) as response:
            assert response.status == 200
            assert "Chuyển hướng thành công" in response.read().decode()
            print(f"Đã theo chuyển hướng tới trang đích: {response.url}")
    print("PASS: tạo/tái sử dụng, 301/302, Location, MISS/HIT, bộ đếm, 404 và 422.")


if __name__ == "__main__":
    main()
