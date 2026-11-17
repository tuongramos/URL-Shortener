# Đối chiếu Chapter 8 với bản Kotlin

Nguồn đã đối chiếu ở phiên trước: Chapter 8 — Design A URL Shortener, trang PDF 133–148 của sách người dùng cung cấp. Bản hiện tại chuyển toàn bộ app/backend sang Kotlin; các nguyên lý bên dưới được giữ.

| Nội dung sách | Bản triển khai Kotlin |
| --- | --- |
| Rút gọn và chuyển hướng | App Compose gọi POST /api/v1/links; server Ktor xử lý GET /r/{code} |
| Lưu ID/mã/URL gốc trong DB quan hệ | Database.kt dùng SQLite JDBC, giữ schema tương thích dữ liệu trước |
| ID → Base62 | Base62.encode(Long), bảng ký tự 0-9/a-z/A-Z |
| Ví dụ 11157 → 2TX; 2009215674938 → zn9edcu | Có kiểm thử Kotlin cho cả hai |
| Tra URL đã có trước khi tạo | createOrGet, UNIQUE(URL, mode), transaction IMMEDIATE |
| 301/302 | Mode mặc định 302, mode 301 tạo qua API/CLI demo |
| Tra cache trước DB | RedirectCache.resolve, TTL/LRU, MISS/HIT và counters |
| ID phân tán/load balancer/nhiều web server | Chưa triển khai; giải thích bằng lý thuyết |
| Rate limiter, analytics, DB replication/sharding | Phần mở rộng, chưa nằm trong bản demo cục bộ |

## Các điểm phải nói đúng

- Base62 biểu diễn ID, không nén URL; cần DB để lấy URL gốc.
- Table 8-3 (PDF 144) nêu độ dài Base62 tăng theo ID. Mã ngắn như 1 hoặc 10 hợp lệ; không cần đệm đủ 7 ký tự. Muốn giới hạn tối đa 7 thì miền ID phải nhỏ hơn 62^7.
- 62^6 = 56.800.235.584; 62^7 = 3.521.614.606.208. Không cắt mã để ép độ dài.
- ID do SQLite cấp chỉ giải quyết một DB của bản hiện tại, không thay thế bộ sinh ID toàn cục.
- Cùng URL và cùng chế độ dùng lại. Mode, created_at, API demo, TTL và LRU là chi tiết bổ sung của ứng dụng để thuyết trình.
- Tên API trong sách là minh họa; đường dẫn của dự án có cùng vai trò nhưng không giống nguyên văn.
- Phân biệt cache RAM ở server và cache redirect HTTP ở trình duyệt. Bộ đếm không phải analytics đo mọi lượt nhấp.
- Giao diện là Android Compose thật, không tải website trong WebView. Backend cũng là Kotlin, không bọc hoặc khởi động Python.

## Kiểm tra lại phép ước lượng trong PDF

Bản PDF có số liệu chưa nhất quán ở phần ước lượng trang 134–135. Với 100 triệu URL/ngày trong 10 năm: 36,5 tỷ/năm, 365 tỷ/10 năm. Nếu riêng mỗi URL trung bình 100 byte thì dung lượng là **36,5 TB** theo đơn vị thập phân, chưa tính ID/index/metadata/replica. Không chép con số 365 TB trong PDF mà chưa nêu giả định khác.

Demo không chứng minh khả năng phục vụ 100 triệu URL/ngày. Nó minh họa các luồng cốt lõi trên một máy chủ. Xem [DEMO.md](DEMO.md) để nối lý thuyết với thao tác trong app.
