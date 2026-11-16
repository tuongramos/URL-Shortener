# Kết quả triển khai và nghiệm thu

Ngày kiểm tra: **12/11/2026**, giờ Việt Nam. Phạm vi: bản đầu trong tài liệu quy trình gốc; triển khai cục bộ, một tiến trình FastAPI.

## Đã triển khai

- [x] Backend Python/FastAPI, health endpoint và OpenAPI `/docs`.
- [x] SQLite cấp ID và lưu URL/mã/chế độ/thời gian UTC trong transaction; đường dẫn cố định theo dự án, WAL và timeout khi bận.
- [x] Base62 đúng bảng ký tự và toàn bộ ví dụ trong quy trình, không cắt mã, tra cứu phân biệt hoa/thường.
- [x] HTTP/HTTPS, trim hai đầu, giới hạn 4096 ký tự, giữ path/query/fragment; server không tải trang đích.
- [x] Tạo mới 201, tái sử dụng 200, ràng buộc cặp URL/chế độ; yêu cầu trùng đồng thời chỉ tạo một bản ghi.
- [x] Redirect thật 301/302, `Location`, `Cache-Control` và `X-Cache`; 404 khi không có mã.
- [x] Cache RAM có TTL, giới hạn LRU, khóa đồng bộ và bộ đếm demo.
- [x] Metadata không thay đổi thống kê redirect; clear cache/reset counters độc lập; demo tắt thì trả 404.
- [x] Giao diện TypeScript thuần bằng tiếng Việt: nhập URL, pending, kết quả, sao chép/mở, thông báo lỗi và sao chép tay.
- [x] Vite proxy trong phát triển; FastAPI phục vụ frontend đã build và tài nguyên tĩnh.
- [x] Requirements chốt phiên bản, npm lockfile, `.env.example`, `.gitignore`, README, API, DEMO và script PowerShell.
- [x] Trang đích ngoại tuyến độc lập và script kiểm chứng HTTP thật.

## Kiểm thử tự động

| Nhóm | Kết quả | Nội dung chính |
| --- | --- | --- |
| Pytest backend | **58/58 đạt** | Base62, dữ liệu đầu vào, đồng thời, HTTP, cache, cấu hình và lưu trữ |
| Vitest frontend | **12/12 đạt** | Gửi/sửa lỗi, gửi lặp, URL dài, nội dung HTML an toàn, tái sử dụng, clipboard, mất kết nối, 422/500 và timeout |
| TypeScript + Vite build | **Đạt** | Sinh HTML/CSS/JS, không có lỗi TypeScript |
| `pip check` | **Đạt** | Không có xung đột phụ thuộc Python |
| Script PowerShell | **Đạt** | Parse cú pháp và chạy thực tế setup/start |

Frontend dùng happy-dom để kiểm tra hành vi và trạng thái; bộ kiểm thử này không đo bố cục hiển thị. Pytest có một cảnh báo từ Starlette rằng hỗ trợ `httpx` trong TestClient sẽ bị loại bỏ về sau; các phiên bản đã chốt vẫn chạy thành công. Chưa thay sang `httpx2` trong bản này.

## Tích hợp HTTP và lưu trữ

- [x] Trang chính và các tệp CSS/JS đã build trả 200 qua FastAPI.
- [x] Trang Vite trả 200; POST qua proxy `/api/v1/links` hoạt động, URL ngắn dùng cổng backend 8000.
- [x] Chạy `scripts/demo_check.py --follow`: xác nhận tạo/tái sử dụng, 301/302, `Location`, MISS → HIT, metadata không tăng bộ đếm và lỗi 404/422.
- [x] Hai lần redirect 302 sau clear/reset: `redirect_requests=2`, `cache_hits=1`, `cache_misses=1`, `db_lookup_count=1`, `cache_size=1`, `hit_rate=0.5`.
- [x] Theo chuyển hướng đến trang offline cổng 9000, nhận 200 và nội dung “Chuyển hướng thành công”.
- [x] Khởi động lại tiến trình server thật: liên kết cũ còn trong DB; trước lượt truy cập mới, cache và bộ đếm bằng 0; redirect cũ trả 302/MISS.
- [x] Sau khởi động lại, server phục vụ bản cuối cùng và từ chối chế độ dạng số thập phân với 422.

## Kiểm tra trên Chrome của người dùng

- [x] Mở giao diện đã build, nhập URL và bấm Rút gọn.
- [x] Quan sát trạng thái “Đang rút gọn...” và nút bị vô hiệu hóa trong lúc gửi.
- [x] Quan sát liên kết đã có được tái sử dụng.
- [x] Bấm Sao chép, nhận thông báo “Đã sao chép liên kết”.
- [x] Bấm Mở liên kết, đến trang đích thật; query và fragment còn nguyên trên thanh địa chỉ.
- [x] Nhập scheme FTP rồi nhấn Enter; hiện lỗi cạnh ô nhập, giữ nội dung để sửa.
- [x] Quan sát bố cục hẹp ở cửa sổ khoảng 580px và màn hình rộng 1920px.

Chưa kiểm tra trên điện thoại thật hoặc thiết bị/mạng khác. Tình huống clipboard bị chặn, mất kết nối và timeout đã được kiểm chứng bằng kiểm thử frontend; chưa ép các điều kiện đó trực tiếp trong Chrome. Không thay đổi quyền bảo mật của trình duyệt để thử clipboard.

## Cài đặt từ môi trường sạch

Đã sao chép source vào thư mục kiểm tra riêng, không dùng lại `.venv`, `node_modules`, `dist` hoặc DB thật; chạy `scripts/setup.ps1` để tạo Python venv mới, cài requirements và `npm ci` từ lockfile. Build, 58 kiểm thử backend và 12 kiểm thử frontend đều đạt. Đã chạy `start.ps1 -Port 8001` từ thư mục khác với origin cấu hình tương ứng: trang chính/health hoạt động, tạo URL ngắn đúng cổng và DB nằm ở gốc bản sao. Thư mục kiểm tra được dọn sau khi hoàn tất.

Phiên bản đã kiểm thử: Python 3.12.14, SQLite 3.53.1, Node.js 24.14.0, npm 11.13.0. Môi trường Python ban đầu dùng runtime đi kèm Codex; README có hướng dẫn tạo lại bằng Python hệ thống.

## Ngoài phạm vi bản đầu

Chưa triển khai tên miền/HTTPS công khai, rate limiting, tài khoản, QR, dashboard, Redis hoặc nhiều worker. Muốn chia sẻ URL cho máy khác cần cấu hình địa chỉ truy cập được; `127.0.0.1` hiện chỉ dùng trên máy chạy server. Các bước mở dịch vụ công khai được ghi trong README.
