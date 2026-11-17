# API URL Shortener — Kotlin / Ktor

Base URL: http://127.0.0.1:8000. Trang /docs do server Ktor phục vụ mô tả endpoint; bản này không dùng Swagger/FastAPI. Kiểm chứng tương tác bằng task Kotlin **:server:demoCheck** hoặc công cụ HTTP.

| Method | Path | Kết quả |
| --- | --- | --- |
| GET | /api/v1/health | 200 khi truy cập được bảng SQLite |
| POST | /api/v1/links | 201 tạo mới, 200 tái sử dụng |
| GET | /api/v1/links/{code} | 200 metadata hoặc 404 |
| GET | /r/{code} | 301/302 + Location, X-Cache; hoặc 404 |
| GET | /api/v1/demo/stats | Stats khi bật demo; 404 khi tắt |
| POST | /api/v1/demo/cache/clear | Xóa cache, giữ DB/counters |
| POST | /api/v1/demo/stats/reset | Reset counters, giữ DB/cache |
| GET | /demo/target | Trang đích để thử chuyển hướng khi không có Internet |

## Tạo liên kết

~~~json
{
  "long_url": "https://example.com/article?chapter=8#section",
  "redirect_status": 302
}
~~~

Mode có thể bỏ qua, mặc định 302. Chỉ nhận JSON integer 301/302, không tự đổi chuỗi "301", bool hoặc số thập phân. URL được trim hai đầu, tối đa 4096 ký tự, HTTP/HTTPS tuyệt đối, không có khoảng trắng bên trong, backslash hay ký tự điều khiển. Không tải hoặc xác minh nội dung trang đích. URL không được tự bỏ query/fragment hoặc sắp xếp lại query. Trường thừa và JSON sai trả 422.

~~~json
{
  "id": 1,
  "short_code": "1",
  "short_url": "http://127.0.0.1:8000/r/1",
  "long_url": "https://example.com/article?chapter=8#section",
  "redirect_status": 302,
  "created_at": "2026-11-17T00:00:00Z",
  "reused": false
}
~~~

Ví dụ minh họa; ID và thời gian thực tế phụ thuộc DB. Cùng chuỗi URL sau trim + cùng mode dùng lại một bản ghi, trả 200/reused=true. Khác mode tạo hai mã riêng. Metadata trả cùng cấu trúc với reused=true để biểu thị bản ghi đã có, không đi qua cache redirect hoặc tăng counters.

## Redirect

GET /r/1 trả status rõ ràng 301 hoặc 302 và Location. URL ASCII/percent-encoded được giữ; ký tự Unicode được URI.toASCIIString() chuyển thành dạng truyền được qua header. Mã phân biệt hoa/thường.

- 302: Cache-Control: no-store.
- 301: Cache-Control: private, max-age=300.
- X-Cache: MISS hoặc HIT phản ánh cache RAM của server.
- Mã thiếu: 404, X-Cache: MISS và no-store; không lưu cache âm.

Một phản hồi HTTP được trình duyệt dùng lại có thể không quay lại server. Không nói 301 luôn cache hoặc 302 không bao giờ cache; xét cả header. Stats chỉ đếm request GET redirect server nhận được.

## Stats

~~~json
{
  "redirect_requests": 2,
  "cache_hits": 1,
  "cache_misses": 1,
  "db_lookup_count": 1,
  "not_found": 0,
  "cache_size": 1,
  "hit_rate": 0.5
}
~~~

TTL tính từ khi nạp; HIT chỉ cập nhật thứ tự LRU. Stats dọn các mục hết hạn nhưng không tăng bộ đếm. Clear và reset là hai thao tác độc lập. Tạo lại process làm RAM/counters bắt đầu lại; SQLite được giữ.

## Lỗi

422: dữ liệu không hợp lệ. 404: mã không tồn tại hoặc API demo bị tắt. 503: lỗi SQLite. 500: lỗi server chưa dự kiến. Response lỗi dạng **{"detail":"Thông báo tiếng Việt"}**; chi tiết nội bộ chỉ ghi log.

API demo dùng cho môi trường cục bộ, không phải hệ thống xác thực quản trị. Không có API sửa/xóa liên kết. Dùng một tiến trình server để stats/cache có ý nghĩa nhất quán.
