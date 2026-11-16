# Đối chiếu dự án với Chapter 8

Nguồn đã đọc: *System Design Interview: An Insider's Guide, Second Edition*, Chapter 8 — *Design A URL Shortener*, trang PDF **133–148** trong tệp người dùng cung cấp `system-design-interview-insiders-2nd.pdf`. Số trang dưới đây là số thứ tự trang trong trình đọc PDF, tính trang đầu là 1. Đã đọc đầy đủ nội dung chương và kiểm tra trực quan các hình/bảng liên quan đến DB, Base62, luồng tạo và chuyển hướng.

## Kết luận

**Dự án phù hợp để demo các cơ chế cốt lõi của Chapter 8 sau phần lý thuyết. Không cần sửa thuật toán Base62 hoặc luồng tạo/chuyển hướng hiện tại để phục vụ mục tiêu này.** Đây là bản triển khai chạy trên một máy chủ, dùng SQLite và cache RAM; chưa triển khai đầy đủ hệ thống phân tán trong Figure 8-8 hoặc kiểm chứng lưu lượng quy mô lớn của phần ước lượng.

## Nội dung sách và phần đã triển khai

| Nội dung Chapter 8 | Vị trí trong PDF | Dự án hiện tại | Kết luận cho demo |
| --- | --- | --- | --- |
| Nhận URL dài, trả URL ngắn và chuyển hướng trở lại URL gốc | 134, 136–138 | POST `/api/v1/links`, GET `/r/{code}` | Đúng chức năng cốt lõi |
| Không cung cấp sửa/xóa liên kết trong phạm vi thiết kế | 134 | Không có endpoint sửa/xóa | Phù hợp phạm vi |
| So sánh HTTP 301 và 302 | 138 | Hỗ trợ cả hai; frontend mặc định 302, dùng API để tạo 301 | Có thể minh họa trên HTTP thật |
| Lưu ID và ánh xạ mã ngắn ↔ URL gốc trong cơ sở dữ liệu quan hệ | 140, Figure 8-4 | SQLite: `id`, `short_code`, `long_url`; thêm chế độ và thời gian tạo | Đúng mô hình; SQLite là lựa chọn triển khai cục bộ |
| So sánh hash kèm xử lý va chạm với Base62 | 141–144, Table 8-3 | Triển khai nhánh Base62 được sách chọn cho thiết kế cuối | Không cần cài thêm nhánh hash để demo thiết kế đã chọn |
| Bảng ký tự Base62: số, chữ thường, chữ hoa theo thứ tự quy định | 143, Figure 8-6 | `0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ` | Khớp |
| Ví dụ `11157 → 2TX` và `2009215674938 → zn9edcu` | 143, 145–146 | Có kiểm thử tự động cho cả hai | Khớp ví dụ trong sách |
| Kiểm tra URL đã có; nếu chưa có thì cấp ID, đổi Base62 và lưu | 145, Figure 8-7 | Transaction SQLite; tra trùng trước, cấp ID, đổi mã và commit cùng nhau | Đúng luồng; có xử lý yêu cầu đồng thời |
| Khi mở URL ngắn, tra cache trước rồi mới tra DB | 146, Figure 8-8 | Cache RAM TTL/LRU; MISS thì tra SQLite và nạp cache | Đúng nguyên lý; có `X-Cache` và bộ đếm để quan sát |
| ID duy nhất toàn cục khi triển khai phân tán | 146 | ID tự tăng chỉ trong một SQLite DB | Chưa có bộ sinh ID toàn cục trên nhiều nơi cấp ID độc lập |
| Load balancer và nhiều web server, cache ngoài tiến trình | 146, Figure 8-8 | Một tiến trình FastAPI, cache cùng tiến trình | Trình bày bằng lý thuyết, không tuyên bố đã triển khai |
| Rate limiting, mở rộng DB/web server, analytics, tính sẵn sàng và độ tin cậy | 147 | Chưa triển khai hạ tầng tương ứng; stats hiện tại chỉ phục vụ demo | Là nội dung thảo luận/mở rộng; sách nêu ở phần wrap-up |

Các tệp triển khai dùng để đối chiếu: `backend/app/base62.py`, `database.py`, `cache.py`, `services.py`, `routes.py` và `schemas.py`; kiểm thử trong `backend/tests/` và `frontend/tests/`.

## Các điểm cần nói đúng khi thuyết trình

### Độ dài mã không bắt buộc cố định 7 ký tự

Trang PDF 140–141 tính số ký tự cần thiết cho miền mã ở quy mô 365 tỷ URL: `62^6 = 56.800.235.584`, còn `62^7 = 3.521.614.606.208`, nên 7 ký tự đủ biểu diễn miền số đó. **Table 8-3 ở trang 144 nói rõ độ dài mã trong cách Base62 thay đổi và tăng cùng ID.**

Vì vậy, ID nhỏ sinh mã `1`, `2`, `10` hoặc `2TX` là đúng với phương pháp. Không cần đệm 0, cắt kết quả hoặc tạo ngẫu nhiên 7 ký tự. Dự án giữ toàn bộ kết quả; nếu muốn cam kết tối đa 7 ký tự thì phải giới hạn miền ID nhỏ hơn `62^7`. Số lượng bản ghi ít không tự bảo đảm ID nằm trong miền đó khi dùng một cơ chế cấp ID khác.

### Base62 biểu diễn ID, không mã hóa URL để tự giải mã lại

Luồng được chọn trong Figure 8-7 là **ID → Base62**. Khi mở liên kết, ứng dụng phải đọc ánh xạ đã lưu để lấy URL gốc. Không nói rằng lấy chuỗi Base62 rồi giải mã trực tiếp thành URL gốc. Với ID duy nhất và không cắt mã, phép chuyển cơ số không tạo va chạm giữa các ID khác nhau.

### Sự khác nhau của API và quy tắc tái sử dụng

Sách minh họa POST `api/v1/data/shorten` và GET `api/v1/shortUrl` (trang 136). Dự án dùng POST `/api/v1/links` và GET `/r/{code}` với cùng vai trò. Đây là lựa chọn tên route của ứng dụng, không phải sao chép nguyên văn API trong sách.

Sách kiểm tra URL đã tồn tại. Dự án bổ sung `redirect_status` để so sánh 301/302: **cùng URL và cùng chế độ** dùng lại một liên kết; cùng URL nhưng khác chế độ có hai mã riêng. Giao diện chính luôn gửi 302 nên luồng dùng thông thường vẫn tái sử dụng URL như Figure 8-7. Endpoint metadata, `reused`, `X-Cache`, thống kê, TTL và LRU là chi tiết triển khai hỗ trợ demo.

### Phân biệt cache ứng dụng và cache chuyển hướng HTTP

Figure 8-8 dùng cache phía dịch vụ để tránh đọc DB lặp lại. Dự án thể hiện bằng `MISS → HIT` và `db_lookup_count`.

Phần 301/302 ở trang 138 mô tả hành vi cache trình duyệt theo cách đơn giản hóa. Khi demo thực tế, cần xét cả header: dự án gửi `no-store` cho 302 và `private, max-age=300` cho 301. Không khẳng định mọi phản hồi 301 luôn được cache, mọi phản hồi 302 không bao giờ được cache hoặc bộ đếm server đo được mọi lượt nhấp. Cache trình duyệt có thể khiến server không nhận thêm yêu cầu.

### Quy mô và các phép ước lượng

Các giả định tải trong sách phục vụ thiết kế, không phải năng lực đã đo của ứng dụng cục bộ. Không tuyên bố demo SQLite này phục vụ 100 triệu URL/ngày, nhiều máy chủ hoặc có khả năng chịu lỗi phân tán.

Trong bản PDF được cung cấp, phần ước lượng ở trang 134–135 có chỗ ghi số liệu không nhất quán. Tính lại từ giả định 100 triệu URL/ngày, thời gian 10 năm, tỷ lệ đọc/ghi 10:1 và trung bình 100 byte/URL:

| Đại lượng | Cách tính | Kết quả |
| --- | --- | --- |
| Ghi mỗi giây | `100.000.000 / 86.400` | Khoảng 1.157, thường làm tròn 1.160 |
| Đọc mỗi giây | `10 × số lượt ghi mỗi giây` | Khoảng 11.574, thường làm tròn 11.600 |
| Số URL mỗi năm | `100.000.000 × 365` | 36,5 tỷ |
| Số URL trong 10 năm | `100.000.000 × 365 × 10` | 365 tỷ |
| Dung lượng riêng chuỗi URL | `365 tỷ × 100 byte` | **36,5 TB** theo đơn vị thập phân |

Con số 365 TB ở trang 135 không khớp các giả định này; không nên chép lại mà chưa nêu giả định khác. Dung lượng trên chưa tính ID, index, metadata, sao lưu hoặc bản sao DB. Những thành phần đó cần ước lượng riêng khi trình bày thiết kế đầy đủ.

## Lời dẫn và thứ tự demo sau phần lý thuyết

Có thể mở đầu bằng lời dẫn sau:

> Phần demo triển khai các luồng cốt lõi của Chapter 8 trên một máy chủ: tạo ID, chuyển Base62, lưu ánh xạ, chuyển hướng HTTP và tra cache trước cơ sở dữ liệu. SQLite cấp ID cho bản demo; các thành phần phân tán như load balancer và bộ sinh ID toàn cục thuộc phần thiết kế lý thuyết vừa trình bày.

1. **Liên hệ Figure 8-7:** nhập URL, tạo liên kết, xem metadata để đối chiếu ID và mã; gửi lại để chứng minh tái sử dụng.
2. **Liên hệ Figure 8-6:** dùng hai ví dụ trong kiểm thử để giải thích thuật toán, không sửa ID của DB đang demo để ép mã giống ví dụ sách.
3. **Liên hệ phần 301/302:** mở liên kết thật, rồi dùng công cụ HTTP không theo redirect để xem status và `Location`; tạo mã 301 riêng qua `/docs`.
4. **Liên hệ Figure 8-8:** clear cache/reset counters, gọi liên kết 302 hai lần; quan sát MISS → HIT và chỉ một lần đọc DB.
5. **Liên hệ Figure 8-4:** khởi động lại, liên kết cũ còn hoạt động; RAM và bộ đếm bắt đầu lại.
6. Kết thúc bằng giới hạn của bản một máy chủ và hướng mở rộng trong trang 147.

Xem thao tác/lệnh cụ thể trong [DEMO.md](DEMO.md). Các bước trong tài liệu sách là nội dung dùng để đối chiếu và thuyết trình; không tự tạo thêm yêu cầu triển khai hạ tầng hoặc cài cả hai thuật toán.
