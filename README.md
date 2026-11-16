# URL Shortener

Ứng dụng rút gọn URL theo `QUY_TRINH_THUC_HIEN_URL_SHORTENER.md`: Python/FastAPI, SQLite, Base62, cache RAM TTL/LRU và TypeScript thuần với Vite. Giao diện tiếng Việt, không dùng React. Bản đầu chạy một tiến trình, dùng cho Chapter 8 và nhu cầu nhỏ.

## Chạy trên máy hiện tại

Môi trường `.venv`, thư viện frontend và bản build đã được chuẩn bị. Mở PowerShell tại thư mục dự án:

```powershell
.\scripts\start.ps1
```

Mở **http://127.0.0.1:8000**. API tương tác tại **http://127.0.0.1:8000/docs**. Dừng bằng `Ctrl+C`. Nếu cổng 8000 đang có server, dùng server đó hoặc dừng nó trước khi chạy lại.

Nếu PowerShell chặn tệp `.ps1`, có thể chạy trực tiếp, không cần đổi chính sách thực thi của máy:

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --app-dir backend --host 127.0.0.1 --port 8000
```

## Cài đặt từ đầu

Cần Python 3.12 hoặc 3.13 và Node.js 22.12+ thuộc một nhánh còn được hỗ trợ. Môi trường đã kiểm thử: Python 3.12.14, Node.js 24.14.0, npm 11.13.0, SQLite 3.53.1. Các phiên bản phụ thuộc Python được chốt trong requirements; npm sử dụng `package-lock.json`.

```powershell
python --version
node --version
npm.cmd --version
.\scripts\setup.ps1
.\scripts\start.ps1
```

Nếu Python không nằm trong PATH, truyền đường dẫn Python đã cài:

```powershell
.\scripts\setup.ps1 -Python 'C:\duong-dan\Python312\python.exe'
```

Hoặc cài thủ công:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r backend/requirements-dev.txt
Copy-Item .env.example .env
Push-Location frontend
npm.cmd ci
npm.cmd run build
npm.cmd test
Pop-Location
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\python.exe -m uvicorn app.main:app --app-dir backend --host 127.0.0.1 --port 8000
```

Chỉ sao chép `.env.example` nếu chưa có `.env`; giữ cấu hình riêng khi cài lại. Môi trường Python hiện tại được tạo từ Python đi kèm Codex vì máy chưa đăng ký Python hệ thống. Khi chuyển máy hoặc gỡ runtime đó, tạo lại `.venv` bằng Python đã cài; không sao chép `.venv` giữa các máy.

## Phát triển giao diện

Chạy backend ở terminal thứ nhất. Terminal thứ hai:

```powershell
Set-Location frontend
npm.cmd run dev
```

Mở http://127.0.0.1:5173. Vite proxy `/api` và `/r` đến backend; frontend gọi API tương đối nên không cần CORS. URL ngắn lấy từ `PUBLIC_BASE_URL`, mặc định vẫn ở cổng 8000. Sau thay đổi giao diện, chạy lại `npm.cmd run build` rồi khởi động lại backend để phục vụ bản mới. Vite preview chỉ phục vụ tệp tĩnh, không thay thế backend tích hợp.

## Cấu hình

Ứng dụng đọc `.env` ở gốc dự án, biến môi trường hệ điều hành được ưu tiên. Đường dẫn DB tương đối luôn tính theo gốc dự án, không theo thư mục terminal.

| Biến | Mặc định khi không có `.env` | Ý nghĩa |
| --- | --- | --- |
| `PUBLIC_BASE_URL` | `http://127.0.0.1:8000` | Origin HTTP/HTTPS, không có path/query/fragment |
| `DATABASE_PATH` | `backend/data/shortener.db` | SQLite bền vững |
| `CACHE_MAX_ITEMS` | `1000` | Số ánh xạ tối đa, loại bỏ theo LRU |
| `CACHE_TTL_SECONDS` | `300` | TTL tính từ lúc nạp, không gia hạn khi hit |
| `DEMO_MODE` | `false` | Bật các API thống kê và quản lý cache |

`.env.example` bật `DEMO_MODE=true` cho buổi báo cáo cục bộ. Khởi động lại server sau khi đổi cấu hình. Cache chỉ nạp khi redirect; tạo URL, metadata và health không nạp cache. Clear cache giữ DB và bộ đếm; reset bộ đếm giữ cache. Khởi động lại chỉ giữ DB.

## Kiểm thử và demo

```powershell
.\.venv\Scripts\python.exe -m pytest
Push-Location frontend
npm.cmd run build
npm.cmd test
Pop-Location
# Cần backend đang chạy và DEMO_MODE=true:
.\.venv\Scripts\python.exe scripts/demo_check.py
```

Backend có 58 kiểm thử, dùng DB tạm, cache riêng và đồng hồ giả cho TTL; không xóa DB thật, không truy cập Internet. Bao gồm yêu cầu trùng đồng thời, URL có query/fragment, 301/302, LRU/TTL, bộ đếm, tắt demo và khởi động lại. Frontend có 12 kiểm thử Vitest/happy-dom cho gửi lặp, lỗi kết nối, timeout, nội dung an toàn và clipboard bị chặn; đây là kiểm thử DOM mô phỏng, bổ sung cho thao tác trên Chrome. Script HTTP demo sẽ tạo liên kết mẫu và reset bộ đếm/cache trong phiên server đang chạy; nên chạy riêng lúc tập demo. Xem [kịch bản demo](docs/DEMO.md), [đặc tả API](docs/API.md) và [kết quả nghiệm thu](docs/VERIFICATION.md).

## Cấu trúc

Để chuẩn bị giải thích code khi demo, đọc [GIAI_THICH_CODE.md](docs/GIAI_THICH_CODE.md): từng hàm backend/frontend, script, kiểm thử, ví dụ chạy và các câu hỏi thường gặp.

```text
backend/app/      FastAPI, cấu hình, SQLite, Base62, service và cache
backend/tests/    Kiểm thử thuật toán, API, đồng thời và lưu trữ
backend/data/     SQLite lúc chạy (không đưa vào source)
frontend/src/     TypeScript, CSS; HTML ở frontend/index.html
frontend/dist/    Bản build do FastAPI phục vụ (được tạo lại)
scripts/          Cài đặt, khởi động và kiểm tra HTTP
docs/             API, kịch bản demo, nghiệm thu và trang đích ngoại tuyến
```

Không có tài khoản, mã tùy chọn, QR, sửa/xóa liên kết hoặc dashboard. Cùng URL sau khi trim và cùng 301/302 dùng lại một bản ghi; hai chế độ dùng mã riêng. URL được kiểm tra cấu trúc, không tải trang đích. Không bỏ query, fragment hoặc chuẩn hóa lại chuỗi trước khi lưu. Header `Location` giữ nguyên các URL ASCII/percent-encoded; ký tự Unicode trong URL được lớp HTTP percent-encode khi cần. Mã Base62 phân biệt chữ hoa/thường, biến thiên độ dài và không cắt mã.

## Dùng từ thiết bị khác

`127.0.0.1` chỉ dùng trên chính máy này. Với LAN, đặt `PUBLIC_BASE_URL=http://<IP-LAN>:8000`, khởi động với `--host 0.0.0.0` và kiểm tra truy cập từ máy khác. Cấu hình firewall phù hợp khi thực sự cần. Bản bàn giao chưa triển khai Internet. Trước khi công khai: có HTTPS, địa chỉ cố định, ổ lưu DB bền vững và sao lưu; đặt `DEMO_MODE=false`, bổ sung rate limiting và xử lý lạm dụng. Cache/bộ đếm chỉ nhất quán trong một tiến trình, không chạy nhiều worker cho bản demo.

## Tham khảo

Đã đối chiếu dự án với Chapter 8 của sách người dùng cung cấp. Xem [CHAPTER_8_ALIGNMENT.md](docs/CHAPTER_8_ALIGNMENT.md) để ghép phần lý thuyết với demo, phân biệt phạm vi một máy chủ với thiết kế phân tán và kiểm tra các phép ước lượng trong PDF.

[FastAPI lifespan/testing](https://fastapi.tiangolo.com/advanced/testing-events/), [FastAPI static files](https://fastapi.tiangolo.com/tutorial/static-files/), [Vite](https://vite.dev/guide/), [Python sqlite3](https://docs.python.org/3/library/sqlite3.html), [SQLite WAL](https://www.sqlite.org/wal.html).
