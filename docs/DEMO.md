# Kịch bản báo cáo Chapter 8

Đã đối chiếu với Chapter 8 trong sách người dùng cung cấp (trang PDF 133–148). Xem [bảng đối chiếu và các lưu ý khi trình bày](CHAPTER_8_ALIGNMENT.md).

Chuẩn bị phần hỏi đáp về mã nguồn bằng [giải thích từng hàm](GIAI_THICH_CODE.md), đặc biệt `encode()`, `Database.create_or_get()`, `RedirectCache.resolve()` và `routes.redirect()`.

Lời dẫn gợi ý: “Demo này triển khai các luồng cốt lõi của Chapter 8 trên một máy chủ: ID → Base62, lưu ánh xạ URL, chuyển hướng HTTP và cache trước DB. SQLite cấp ID trong bản demo; kiến trúc phân tán được trình bày trong phần lý thuyết.”

## Chuẩn bị

1. Cài/build theo README. `.env`: `DEMO_MODE=true`, `PUBLIC_BASE_URL=http://127.0.0.1:8000`.
2. Chạy `.\scripts\start.ps1`, không bật reload, chỉ một tiến trình.
3. Chạy trang đích ngoại tuyến độc lập trong PowerShell khác, từ gốc dự án:

```powershell
.\.venv\Scripts\python.exe -m http.server 9000 --bind 127.0.0.1 --directory docs/offline
```

Mở trực tiếp http://127.0.0.1:9000/ để xác nhận trang đích. URL mẫu dùng trong toàn bộ demo:

```text
http://127.0.0.1:9000/?chapter=8&source=demo#chapter-8
```

Không cần Internet hoặc tải font/tài nguyên bên ngoài. Có thể thay bằng URL HTTPS thật khi có mạng.

## Thao tác trình bày

| Bước | Thao tác | Điểm giải thích |
| --- | --- | --- |
| 1 | Mở http://127.0.0.1:8000, dán URL mẫu, nhấn Enter | Form TypeScript gọi FastAPI |
| 2 | Sao chép rồi bấm Mở liên kết | Chuyển hướng HTTP thật đến trang đích ngoại tuyến |
| 3 | Gửi lại URL mẫu | Dùng lại liên kết; DB không tạo bản ghi trùng |
| 4 | Mở `/docs`, GET metadata với mã vừa tạo | ID SQLite → Base62, phân biệt hoa/thường |
| 5 | POST clear cache, POST reset stats trong `/docs` | Hai thao tác độc lập; SQLite giữ nguyên |
| 6 | GET URL ngắn hai lần qua curl.exe, không dùng `-L` | Lần đầu MISS, lần sau HIT |
| 7 | GET stats trong `/docs` | 2 request, 1 hit, 1 miss, 1 DB lookup, hit rate 0.5 |
| 8 | POST tạo cùng URL, `redirect_status=301`; kiểm tra mã riêng | 301 so với 302 và cache HTTP |
| 9 | Dừng FastAPI bằng Ctrl+C và chạy lại | Liên kết cũ vẫn mở được; RAM và bộ đếm về đầu |
| 10 | Nhập URL sai và mở `/r/missing-code` | 422 và 404 với thông báo phù hợp |

Thay `<code>` bằng mã thực tế:

```powershell
curl.exe -s -D - -o NUL http://127.0.0.1:8000/r/<code>
```

Không dùng `-I` vì lệnh đó gửi HEAD; ứng dụng hỗ trợ GET. Không dùng `-L` nếu cần đọc mã redirect đầu tiên. DevTools bật Preserve log để xem chuỗi redirect. Khi minh họa cache trình duyệt, không bật Disable cache.

## Kiểm chứng tự động trên HTTP thật

Khi cả backend và trang đích offline đang chạy:

```powershell
.\.venv\Scripts\python.exe scripts/demo_check.py --follow
```

Script tạo/dùng lại URL 302, clear cache/reset counters, kiểm tra MISS→HIT, stats chính xác, metadata không tăng bộ đếm, URL 301 riêng, lỗi 404/422 và theo chuyển hướng đến trang offline. Script thay đổi cache/bộ đếm phiên demo, không xóa liên kết. Có thể chạy lại với cùng DB. Dùng `--base-url` hoặc `--target` để thay địa chỉ.

## Câu hỏi thường gặp

- **Vì sao dùng ID tự tăng?** SQLite cấp ID duy nhất trong DB này; transaction bảo đảm ID và mã cùng được lưu. Không giải quyết ID toàn cục trên nhiều hệ thống độc lập.
- **Vì sao mã dài dần?** Mã là biểu diễn của ID theo cơ số 62, không cắt chuỗi. ID `62^7` cần 8 ký tự.
- **Sách nói 7 ký tự, vì sao mã demo có thể chỉ là `1`?** Phần ước lượng cho thấy 7 ký tự đủ chứa miền mã cho quy mô giả định. Table 8-3 (trang PDF 144) nêu độ dài Base62 tăng cùng ID; không bắt buộc mọi mã phải đủ 7 ký tự.
- **Base62 có giải mã lại URL được không?** Chỉ biểu diễn ID, cần ánh xạ SQLite để lấy URL gốc.
- **Tại sao lần đầu MISS?** Tạo URL không nạp cache. Lần redirect đầu mới tra SQLite và nạp RAM.
- **Cache đầy hoặc hết TTL?** Loại mục hết hạn; nếu quá dung lượng, bỏ mục ít được dùng gần đây nhất. TTL tính từ khi nạp.
- **301 có làm bộ đếm giảm không?** Trình duyệt có thể dùng lại phản hồi và không gửi request mới. Stats chỉ tính request server quan sát được.
- **302 có tuyệt đối không cache không?** Không. Ứng dụng chủ động gửi `no-store` cho 302; khả năng cache HTTP phụ thuộc mã và header.
- **Có phục vụ nhiều worker không?** Bản này chạy một worker. Nhiều worker có RAM và stats riêng; cần thiết kế lại cache dùng chung nếu mở rộng.

## Trước buổi báo cáo

- Chạy pytest, `npm.cmd test` và build; chạy `demo_check.py --follow`.
- Thử thủ công form, bàn phím Enter/Tab, sao chép, mở liên kết và màn hình điện thoại.
- Thử URL dài và chuỗi có ký tự HTML; nội dung người dùng phải xuất hiện như văn bản.
- Thử gửi lại sau lỗi và lúc backend tắt; không để frontend hiển thị lỗi nội bộ.
- Giữ lại một URL đã tạo, khởi động lại server và mở URL đó.
- Dừng server offline và FastAPI bằng Ctrl+C khi không cần nữa.
