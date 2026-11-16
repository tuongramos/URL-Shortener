# API URL Shortener v1

Base URL mặc định: `http://127.0.0.1:8000`. OpenAPI: `/openapi.json`; Swagger UI: `/docs`.

| Phương thức | Đường dẫn | Kết quả |
| --- | --- | --- |
| GET | `/api/v1/health` | 200 `{"status":"ok"}` khi đọc được DB |
| POST | `/api/v1/links` | 201 tạo mới, 200 tái sử dụng |
| GET | `/api/v1/links/{code}` | 200 metadata, 404 không có mã |
| GET | `/r/{code}` | 301/302 với `Location`, hoặc 404 |
| GET | `/api/v1/demo/stats` | 200 bộ đếm khi bật demo, 404 khi tắt |
| POST | `/api/v1/demo/cache/clear` | Xóa cache, trả stats hiện tại |
| POST | `/api/v1/demo/stats/reset` | Reset bộ đếm, trả stats hiện tại |

## Tạo hoặc dùng lại liên kết

```http
POST /api/v1/links
Content-Type: application/json

{"long_url":"https://example.com/article?chapter=8#section","redirect_status":302}
```

`redirect_status` có thể bỏ qua (302). Chỉ nhận số 301 hoặc 302. `long_url` là chuỗi HTTP/HTTPS tuyệt đối, 1–4096 ký tự sau khi trim; không nhận khoảng trắng bên trong, dấu backslash hoặc ký tự điều khiển. Không tự truy cập trang đích nên URL có cấu trúc hợp lệ vẫn có thể trỏ tới trang không hoạt động. Thuộc tính request không xác định trả 422.

Response minh họa (ID, mã và thời gian thực tế phụ thuộc DB):

```json
{
  "id": 1,
  "short_code": "1",
  "short_url": "http://127.0.0.1:8000/r/1",
  "long_url": "https://example.com/article?chapter=8#section",
  "redirect_status": 302,
  "created_at": "2026-11-12T00:00:00Z",
  "reused": false
}
```

Gửi lại cùng chuỗi URL sau trim và cùng chế độ trả 200, cùng ID/mã/thời gian, `reused=true`. Không chuẩn hóa URL để gộp `https://example.com` với `https://example.com/`, không đổi thứ tự query hoặc xóa fragment. Thay đổi chế độ tạo một bản ghi khác. ID SQLite được mã hóa Base62 theo `0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ`.

## Metadata và redirect

Metadata có cùng các trường response trên, `reused=true` biểu thị bản ghi đã có. Không dùng cache chuyển hướng và không tăng bộ đếm. Mã được tra cứu đúng hoa/thường.

```http
HTTP/1.1 302 Found
Location: https://example.com/article?chapter=8#section
X-Cache: MISS
Cache-Control: no-store
```

Lần tiếp theo khi ánh xạ còn trong RAM: `X-Cache: HIT`. Liên kết 301 dùng `Cache-Control: private, max-age=300`. Mỗi GET redirect server nhận sẽ được đếm; HEAD chưa được cung cấp (405). 404 redirect cũng có `X-Cache: MISS` và `Cache-Control: no-store`; không cache âm.

## Stats trong demo

```json
{
  "redirect_requests": 2,
  "cache_hits": 1,
  "cache_misses": 1,
  "db_lookup_count": 1,
  "not_found": 0,
  "cache_size": 1,
  "hit_rate": 0.5
}
```

`db_lookup_count` chỉ tính truy vấn phục vụ redirect. `hit_rate = hits / (hits + misses)`, hoặc 0 khi chưa có truy vấn. `cache_size` chỉ tính mục chưa hết hạn. TTL không gia hạn khi hit; chạm một mục sẽ đưa nó tới cuối hàng LRU. Đọc stats không tăng bộ đếm. Clear cache không xóa SQLite hoặc reset bộ đếm; reset bộ đếm không xóa cache. Các endpoint demo trả 404 khi `DEMO_MODE=false` và chỉ dùng cục bộ, không có xác thực quản trị.

## Lỗi

| Mã | Response minh họa | Cách xử lý |
| --- | --- | --- |
| 422 | `{"detail":"Dữ liệu chưa hợp lệ. Hãy nhập URL HTTP/HTTPS tối đa 4096 ký tự và chế độ 301 hoặc 302."}` | Sửa dữ liệu |
| 404 | `{"detail":"Liên kết không tồn tại"}` | Kiểm tra mã ngắn |
| 503 | `{"detail":"Máy chủ đang bận. Bạn hãy thử lại."}` | Thử lại sau |

Stack trace và đường dẫn DB không được trả cho người dùng. Chi tiết lỗi SQLite ghi vào log server. Nếu chưa build frontend, GET `/` trả 503 với hướng dẫn build; API vẫn hoạt động.
