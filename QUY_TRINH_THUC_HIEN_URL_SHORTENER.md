# Quy trình thực hiện dự án URL Shortener

> Tài liệu kế hoạch triển khai. Các cấu trúc, API và lệnh bên dưới là hướng dẫn cho giai đoạn viết code, chưa phải thông báo đã hoàn thành sản phẩm.

## 1. Mục tiêu và công nghệ đã chọn

Xây dựng ứng dụng web rút gọn URL phục vụ báo cáo Chapter 8, đồng thời có thể sử dụng để tạo và mở liên kết thật.

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| Server | Python + FastAPI | Kiểm tra URL, tạo mã ngắn, cung cấp API, chuyển hướng HTTP |
| Lưu trữ | SQLite | Lưu liên kết lâu dài trên ổ đĩa |
| Giao diện | TypeScript, HTML, CSS | Nhập URL, hiển thị kết quả, sao chép và mở liên kết |
| Cache | Bộ nhớ RAM của server | Tăng tốc tra cứu các mã đã truy cập |
| Công cụ frontend đề xuất | Vite với mẫu `vanilla-ts` | Chạy khi phát triển và biên dịch TypeScript |

Vite là đề xuất để tổ chức frontend TypeScript thuần. Bộ công nghệ người dùng đã chọn không bao gồm React hoặc framework giao diện khác.

### Nguyên tắc giao diện

- Một trang, bố cục đơn giản và có thể dùng trên điện thoại hoặc laptop.
- Ưu tiên thao tác rút gọn và mở liên kết thành công.
- Thông báo bằng tiếng Việt, dễ hiểu.
- Không đưa thuật ngữ Base62, ID, cache hit/miss vào luồng sử dụng chính.
- Phần kỹ thuật phục vụ báo cáo được trình bày qua API, tài liệu và công cụ kiểm tra HTTP.

## 2. Phạm vi bản đầu

### Chức năng cần hoàn thành

- [ ] Nhập một URL HTTP hoặc HTTPS hợp lệ.
- [ ] Tạo URL ngắn với mã Base62 sinh từ ID do SQLite cấp.
- [ ] Trả lại liên kết đã có khi cùng URL và cùng chế độ chuyển hướng được gửi lại.
- [ ] Sao chép URL ngắn.
- [ ] Mở URL ngắn bằng trình duyệt và đến đúng trang đích.
- [ ] Lưu dữ liệu sau khi tắt và khởi động lại server.
- [ ] Dùng cache RAM cho luồng chuyển hướng.
- [ ] Trả thông báo phù hợp khi URL đầu vào sai hoặc mã ngắn không tồn tại.
- [ ] Có cách kiểm tra 301/302 và cache khi báo cáo.

### Chức năng có thể bổ sung sau khi bản đầu chạy ổn

- Danh sách các liên kết vừa tạo trong phiên sử dụng, nếu cần.
- Triển khai trên một địa chỉ HTTPS công khai để người khác sử dụng.
- Tên miền ngắn riêng.
- Rate limiting khi mở dịch vụ cho người dùng bên ngoài.
- Redis và cơ sở dữ liệu phù hợp hơn khi mở rộng.

Tài khoản, QR code, mã tùy chọn, dashboard phân tích và giao diện quản trị chưa nằm trong bản đầu. Liên kết đã tạo không có chức năng sửa hoặc xóa trong phạm vi báo cáo.

## 3. Trải nghiệm người dùng

### Bố cục một trang

1. Tiêu đề: **Rút gọn liên kết**.
2. Mô tả ngắn: **Tạo liên kết ngắn để chia sẻ dễ dàng hơn.**
3. Ô nhập: **Dán liên kết của bạn**.
4. Nút: **Rút gọn**.
5. Khu vực kết quả chỉ xuất hiện sau khi thành công:
   - URL ngắn có thể bấm mở.
   - Nút **Sao chép**.
   - Nút **Mở liên kết**.
   - URL gốc để người dùng đối chiếu.
6. Một dòng thông báo khi đang xử lý, thành công hoặc có lỗi.

### Các trạng thái cần thiết

| Trạng thái | Hành vi |
|---|---|
| Chưa nhập | Ô nhập trống, có nhãn hướng dẫn |
| Đang gửi | Nút đổi thành “Đang rút gọn...” và tránh gửi lặp |
| Thành công | Hiện liên kết ngắn và các nút thao tác |
| Đã sao chép | Hiện “Đã sao chép liên kết” |
| URL sai | Hiện thông báo cạnh ô nhập, giữ nội dung để sửa |
| Server không phản hồi | Hiện “Chưa kết nối được máy chủ. Bạn hãy thử lại.” |
| Sao chép tự động thất bại | Cho phép chọn và sao chép liên kết bằng tay |

Không hiển thị bảng thống kê kỹ thuật trong trang chính. Giao diện không tự truy cập URL ngắn để tạo bản xem trước, tránh phát sinh yêu cầu chuyển hướng ngoài ý muốn.

## 4. Kiến trúc và cấu trúc thư mục dự kiến

### Trách nhiệm từng phần

- **Frontend:** thu nhận dữ liệu, gọi API, hiển thị kết quả và lỗi.
- **FastAPI:** kiểm tra dữ liệu, điều phối tạo liên kết và chuyển hướng.
- **SQLite:** cấp ID trong phạm vi cơ sở dữ liệu và lưu ánh xạ bền vững.
- **Cache RAM:** lưu ánh xạ đã tra cứu, giới hạn số phần tử và thời gian tồn tại.

### Cấu trúc dự kiến

```text
url-shortener/
  backend/
    app/
      __init__.py
      main.py          # Khởi tạo FastAPI và đăng ký các route
      config.py        # Địa chỉ công khai, đường dẫn DB, cấu hình cache
      database.py      # Kết nối, khởi tạo và truy vấn SQLite
      schemas.py       # Dữ liệu request và response
      base62.py        # Chuyển ID thành mã Base62
      cache.py         # Cache RAM và bộ đếm phục vụ demo
      services.py      # Quy trình tạo liên kết và tra cứu
      routes.py        # API và route chuyển hướng
    tests/
    data/              # Dữ liệu SQLite khi chạy
    requirements.txt
    requirements-dev.txt
  frontend/
    src/
      main.ts          # Sự kiện nhập URL và hiển thị kết quả
      api.ts           # Gọi API
      types.ts         # Kiểu dữ liệu TypeScript
      style.css
    index.html
    vite.config.ts
    package.json
    package-lock.json
  docs/
    API.md
    DEMO.md
  .env.example
  .gitignore
  README.md
```

Cấu trúc này có thể điều chỉnh khi viết code. Mục tiêu là tách phần thuật toán, lưu trữ và giao diện để thuận tiện giải thích.

## 5. Trình tự triển khai

| Giai đoạn | Việc thực hiện | Điều kiện hoàn thành |
|---|---|---|
| 1. Chuẩn bị | Cài Python, Node.js; tạo môi trường và thư mục | Chạy được công cụ cần thiết |
| 2. Khung server | Tạo FastAPI, cấu hình, endpoint health | `/api/v1/health` trả thành công |
| 3. Lưu trữ | Tạo bảng SQLite và lớp truy vấn | Ghi và đọc lại liên kết, dữ liệu tồn tại sau khởi động lại |
| 4. Base62 | Viết hàm chuyển đổi và kiểm thử | Các ví dụ đã biết cho kết quả đúng |
| 5. Tạo liên kết | Hoàn thành POST tạo URL ngắn | Nhận URL thật, trả mã và URL ngắn, xử lý gửi lại |
| 6. Chuyển hướng | Hoàn thành route GET và xử lý mã sai | Mở liên kết ngắn đến đúng URL gốc |
| 7. Cache | Bổ sung tra cứu RAM, TTL và bộ đếm | Truy cập đầu là miss, truy cập sau là hit |
| 8. Giao diện | Tạo form TypeScript và khu vực kết quả | Dùng được toàn bộ thao tác chính trên trình duyệt |
| 9. Tích hợp | Kết nối frontend với backend, xử lý lỗi | Không có lỗi kết nối hoặc lỗi giao diện trong luồng chính |
| 10. Kiểm thử | Chạy kiểm thử tự động và kiểm tra thủ công | Các trường hợp ở mục 12 đạt yêu cầu |
| 11. Đóng gói | Viết hướng dẫn và chuẩn bị môi trường demo | Có thể khởi động lại dự án theo README |
| 12. Demo | Tập trình bày theo kịch bản | Thực hiện được đầy đủ kịch bản, có phương án mất Internet |

Hoàn thành luồng tạo URL và chuyển hướng trước khi thêm cache hoặc chỉnh giao diện.

## 6. Chuẩn bị môi trường

### Công cụ

- Python 3.12 hoặc 3.13 làm phiên bản mục tiêu đề xuất.
- Node.js bản LTS còn được hỗ trợ và tương thích với Vite. Hướng dẫn Vite tại thời điểm soạn tài liệu yêu cầu Node.js 20.19+ hoặc 22.12+.
- VS Code hoặc trình soạn thảo quen dùng.
- Trình duyệt có Developer Tools.
- Git nếu cần quản lý phiên bản.

### Tạo môi trường Python

Các lệnh minh họa bên dưới chạy tại thư mục gốc `url-shortener` trong PowerShell, sau khi đã cài Python:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install fastapi "uvicorn[standard]"
.\.venv\Scripts\python.exe -m pip install pytest httpx
```

Đề xuất dùng module `sqlite3` của Python cho bản đầu. Tạo các tệp requirements chứa phiên bản thư viện thực tế đã kiểm thử. Sau này chạy lại dự án bằng requirements đã chốt, thay vì cài tùy ý phiên bản mới nhất.

### Tạo frontend

Lệnh này chỉ thực hiện khi bắt đầu viết code và chưa có thư mục frontend:

```powershell
npm.cmd create vite@latest frontend -- --template vanilla-ts
Set-Location frontend
npm.cmd install
```

Lưu `package-lock.json` cùng source để tái lập phiên bản. Dùng `npm.cmd ci` khi cài lại từ lockfile.

### Cấu hình dự kiến

| Cấu hình | Giá trị phát triển đề xuất | Ý nghĩa |
|---|---|---|
| `PUBLIC_BASE_URL` | `http://127.0.0.1:8000` | Địa chỉ dùng để tạo URL ngắn |
| `DATABASE_PATH` | `backend/data/shortener.db` | Đường dẫn lưu dữ liệu, được chuẩn hóa theo thư mục dự án |
| `CACHE_MAX_ITEMS` | `1000` | Giới hạn số ánh xạ trong RAM |
| `CACHE_TTL_SECONDS` | `300` | Thời gian tồn tại mỗi mục cache |
| `DEMO_MODE` | `true` khi demo cục bộ | Cho phép xem và reset bộ đếm/cache |

Dung lượng và TTL cache là lựa chọn cho bản demo, có thể điều chỉnh khi kiểm thử. Bản đầu chạy một tiến trình server để cache và bộ đếm nhất quán.

## 7. Thiết kế dữ liệu và tạo ID

### Bảng `links`

| Trường | Nội dung |
|---|---|
| `id` | ID số nguyên do SQLite cấp, khóa chính |
| `short_code` | Mã Base62 của ID, có ràng buộc duy nhất |
| `long_url` | URL gốc đã kiểm tra |
| `redirect_status` | 301 hoặc 302, mặc định 302 |
| `created_at` | Thời gian tạo theo UTC |

Quy ước cho bản demo: ràng buộc duy nhất trên cặp `(long_url, redirect_status)`. Cùng URL và cùng chế độ sẽ tái sử dụng liên kết. Cùng URL nhưng chế độ 301/302 khác nhau được tạo thành hai liên kết để so sánh. Trường chế độ chuyển hướng là phần bổ sung của ứng dụng phục vụ demo.

### Các bước thực hiện

- [ ] Khởi tạo bảng khi ứng dụng bắt đầu chạy.
- [ ] Dùng đường dẫn DB cố định, tránh tạo nhầm nhiều tệp khi đổi thư mục chạy.
- [ ] Dùng câu truy vấn có tham số.
- [ ] Chỉ commit khi ID, mã ngắn và URL gốc đã được lưu đầy đủ trong cùng transaction.
- [ ] Khi hai yêu cầu đồng thời tạo cùng một cặp URL/chế độ, dùng ràng buộc duy nhất và đọc lại bản ghi đã có.
- [ ] Sử dụng kết nối SQLite phù hợp với từng thao tác/request, không chia sẻ một kết nối thiếu kiểm soát giữa các luồng.
- [ ] Cấu hình thời gian chờ khi DB bận. Cân nhắc WAL sau khi kiểm thử phiên bản SQLite đi kèm môi trường chạy.

ID tự tăng chỉ giải quyết cấp ID trong cơ sở dữ liệu của bản triển khai này. Khi mở rộng thành nhiều nơi cấp ID độc lập, phải thiết kế lại cơ chế ID toàn cục.

## 8. Thiết kế Base62 và quy trình tạo liên kết

### Quy ước Base62

Bảng ký tự dùng đúng thứ tự:

```text
0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ
```

- Chia ID liên tiếp cho 62, đổi phần dư thành ký tự rồi đọc theo chiều ngược lại.
- Phân biệt chữ hoa và chữ thường trong toàn bộ luồng tra cứu.
- Giữ đầy đủ kết quả mã hóa, không cắt ngắn để ép độ dài.
- Base62 là biểu diễn số. URL gốc được lấy lại từ ánh xạ đã lưu.

### Ví dụ kiểm tra

| ID | Mã Base62 mong đợi |
|---|---|
| `1` | `1` |
| `61` | `Z` |
| `62` | `10` |
| `11157` | `2TX` |
| `2009215674938` | `zn9edcu` |

Độ dài tăng theo ID. Bản đầu cho phép mã biến thiên độ dài. Nếu đặt giới hạn 7 ký tự, phải giữ miền ID nhỏ hơn `62^7`, không suy ra giới hạn đó chỉ từ số lượng bản ghi.

### Luồng tạo URL

1. Nhận URL và chế độ chuyển hướng.
2. Bỏ khoảng trắng ở hai đầu, kiểm tra cấu trúc URL và scheme HTTP/HTTPS.
3. Kiểm tra cặp URL/chế độ đã có hay chưa.
4. Nếu đã có, trả bản ghi hiện tại.
5. Nếu chưa có, cấp ID, chuyển Base62 và lưu bản ghi trong transaction.
6. Ghép `PUBLIC_BASE_URL` với `/r/{short_code}` để tạo URL ngắn.
7. Trả dữ liệu cho frontend.

Không tự xóa query string hoặc thay đổi path khi xử lý URL. Giới hạn độ dài URL đầu vào đề xuất là 4096 ký tự, đây là lựa chọn triển khai có thể điều chỉnh. Server không cần tải nội dung của trang đích để rút gọn liên kết.

## 9. Thiết kế API

Các đường dẫn dưới đây là đặc tả đề xuất cho dự án, không phải bản sao nguyên văn API trong sách.

| Phương thức | Đường dẫn | Chức năng | Kết quả |
|---|---|---|---|
| GET | `/api/v1/health` | Kiểm tra server | 200 |
| POST | `/api/v1/links` | Tạo hoặc tái sử dụng URL ngắn | 201 khi tạo mới, 200 khi đã có |
| GET | `/api/v1/links/{code}` | Xem metadata của liên kết để kiểm tra | 200 hoặc 404 |
| GET | `/r/{code}` | Chuyển hướng thật | 301, 302 hoặc 404 |
| GET | `/api/v1/demo/stats` | Xem bộ đếm phục vụ báo cáo | 200 khi bật demo mode |
| POST | `/api/v1/demo/cache/clear` | Làm rỗng cache | 200 khi bật demo mode |
| POST | `/api/v1/demo/stats/reset` | Đặt lại bộ đếm | 200 khi bật demo mode |

### Request tạo URL

```json
{
  "long_url": "https://example.com/article?chapter=8",
  "redirect_status": 302
}
```

Frontend chính chỉ gửi chế độ mặc định 302. Dùng `/docs` hoặc công cụ HTTP để tạo liên kết 301 lúc báo cáo.

### Response cần có

- `id`, `short_code`, `short_url`, `long_url`.
- `redirect_status`, `created_at`.
- `reused`: cho biết liên kết đã tồn tại hay vừa được tạo.

### Quy ước lỗi

- 422 cho URL không hợp lệ hoặc chế độ ngoài 301/302.
- 404 cho mã ngắn không tồn tại.
- Lỗi kết nối hoặc lỗi server có thông báo chung dễ hiểu ở frontend.
- Frontend không hiển thị nguyên stack trace hoặc chi tiết lỗi nội bộ.

Endpoint metadata không đi qua cache chuyển hướng và không tăng bộ đếm truy cập. Các endpoint demo chỉ dành cho môi trường báo cáo cục bộ, tắt khi mở dịch vụ công khai.

## 10. Chuyển hướng, cache và bộ đếm

### Luồng chuyển hướng

1. Người dùng mở `/r/{code}`.
2. Server tra cache RAM.
3. Cache hit: lấy URL và chế độ chuyển hướng từ cache.
4. Cache miss: tìm bản ghi trong SQLite.
5. Nếu tìm thấy, đưa ánh xạ vào cache và tạo phản hồi chuyển hướng.
6. Nếu không có bản ghi, trả 404 với thông báo “Liên kết không tồn tại”.

Phản hồi thành công có mã 301/302 và header `Location` đúng URL gốc. Phải truyền status code rõ ràng khi dùng `RedirectResponse`, vì mặc định của response này là 307.

### Chính sách cache RAM

- Khóa là `short_code`.
- Giá trị gồm `long_url`, `redirect_status` và thời điểm hết hạn.
- Cache có TTL và giới hạn phần tử. Khi đầy, loại bỏ phần tử theo LRU hoặc một chính sách cố định đã ghi rõ.
- Khóa đồng bộ khi sửa cache và bộ đếm nếu thao tác có thể chạy trên nhiều luồng.
- Chỉ nạp cache khi có lượt chuyển hướng, không nạp sẵn trong thao tác tạo URL. Điều này giúp demo lần đầu miss, lần sau hit.
- Xóa cache không xóa dữ liệu SQLite. Reset bộ đếm không xóa cache.
- Khởi động lại server làm mất cache và bộ đếm RAM, còn liên kết trong SQLite vẫn tồn tại.

### Chỉ số phục vụ demo

| Chỉ số | Ý nghĩa |
|---|---|
| `redirect_requests` | Số yêu cầu GET đến route chuyển hướng mà server nhận |
| `cache_hits` | Số lần tìm thấy ánh xạ còn hạn trong cache |
| `cache_misses` | Số lần phải tìm DB vì cache thiếu hoặc hết hạn |
| `db_lookup_count` | Số truy vấn tìm liên kết phục vụ chuyển hướng, không tính API xem metadata |
| `not_found` | Số yêu cầu chuyển hướng không tìm thấy liên kết |
| `cache_size` | Số ánh xạ hiện có trong cache |
| `hit_rate` | `cache_hits / (cache_hits + cache_misses)`, bằng 0 khi chưa có lần tra cứu |

Gọi API thống kê không làm thay đổi các bộ đếm này. Thống kê là số yêu cầu server quan sát được trong phiên chạy, không khẳng định đo được mọi lượt nhấp của người dùng.

### Phân biệt hai loại cache

| Loại | Lưu ở đâu | Lưu dữ liệu gì |
|---|---|---|
| Cache RAM của ứng dụng | Server FastAPI | Ánh xạ mã ngắn và URL gốc |
| Cache HTTP redirect | Trình duyệt hoặc bộ đệm HTTP | Phản hồi chuyển hướng, tùy mã HTTP và header cache |

Đề xuất cho demo:

- Liên kết 302 mặc định gửi `Cache-Control: no-store` để thuận tiện quan sát yêu cầu ở server.
- Liên kết 301 dùng header cache rõ ràng, chẳng hạn `Cache-Control: private, max-age=300`, khi muốn minh họa khả năng tái sử dụng phản hồi ở trình duyệt.
- Thêm header `X-Cache: HIT` hoặc `X-Cache: MISS` vào phản hồi chuyển hướng để kiểm tra cache RAM.
- Dùng liên kết riêng cho 301 và 302, tránh đổi chế độ của một URL đã bị trình duyệt cache.

Hành vi cache thực tế còn phụ thuộc trình duyệt và cấu hình. Không mô tả 302 là mã tuyệt đối không được cache.

## 11. Tích hợp giao diện và chạy ứng dụng

### Khi phát triển

- FastAPI chạy tại `http://127.0.0.1:8000`.
- Vite chạy tại `http://127.0.0.1:5173`.
- Đề xuất cấu hình Vite proxy cho `/api` tới FastAPI. Frontend gọi đường dẫn API tương đối.
- Liên kết kết quả dùng `PUBLIC_BASE_URL` của server, không ghép nhầm với cổng 5173.
- Nếu frontend gọi trực tiếp khác origin, khai báo CORS cho đúng origin dùng thực tế.

Sau khi đã có các tệp ứng dụng theo cấu trúc dự kiến, chạy từ thư mục gốc dự án:

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --app-dir backend --host 127.0.0.1 --port 8000
```

Tại cửa sổ PowerShell khác:

```powershell
Set-Location frontend
npm.cmd run dev -- --host 127.0.0.1 --port 5173
```

Có thể dùng `--reload` lúc phát triển. Khi tập demo, chạy một tiến trình ổn định và tắt reload để tránh mất cache/bộ đếm khi sửa tệp.

### Bản thuận tiện cho buổi demo

- Biên dịch frontend thành các tệp tĩnh trong `frontend/dist`.
- Cho FastAPI phục vụ trang chính và tài nguyên đã build, đồng thời giữ nguyên các route API và `/r`.
- Khi đó chỉ cần mở một địa chỉ của server và chạy một tiến trình ứng dụng.
- Kiểm tra lại toàn bộ luồng sau khi build, không chỉ kiểm tra trên Vite.

### Điều kiện để dùng thực tế trên thiết bị khác

`127.0.0.1` chỉ trỏ tới chính máy đang mở liên kết. Người khác cần một địa chỉ truy cập được tới server:

- Demo cùng mạng: dùng IP LAN của laptop, cấu hình server lắng nghe trên giao diện mạng phù hợp và đặt `PUBLIC_BASE_URL` theo địa chỉ đó.
- Sử dụng qua Internet: chạy server trên môi trường lưu được tệp SQLite, cấu hình tên miền và HTTPS, rồi cập nhật `PUBLIC_BASE_URL`.
- Nếu có reverse proxy, cấu hình đường dẫn và thông tin host được tin cậy một cách rõ ràng.
- Kiểm tra việc lưu dữ liệu sau khi khởi động lại hoặc triển khai lại. Dùng ổ đĩa lưu trữ bền vững cho tệp SQLite.

Bản đầu ưu tiên một máy chủ. Đây là triển khai có thể dùng cho nhu cầu nhỏ và demo, không phải cam kết đáp ứng quy mô lưu lượng lớn trong bài toán của sách.

## 12. Kiểm thử và điều kiện nghiệm thu

### Kiểm thử tự động cần có

| Nhóm | Tình huống | Kết quả mong đợi |
|---|---|---|
| Base62 | Các ID mẫu ở mục 8 | Khớp toàn bộ mã mong đợi |
| Tạo URL | URL HTTP/HTTPS hợp lệ | Lưu dữ liệu và trả URL ngắn |
| Tạo URL | Cùng URL và cùng chế độ gửi lại | Tái sử dụng liên kết, không tăng số bản ghi |
| Tạo URL | Hai yêu cầu đồng thời trùng dữ liệu | Không tạo hai ánh xạ cạnh tranh |
| Kiểm tra đầu vào | Chuỗi rỗng, URL sai, scheme không hỗ trợ | Trả lỗi rõ ràng |
| Redirect | Liên kết mặc định | 302 và Location chính xác |
| Redirect | Liên kết chế độ 301 | 301 và Location chính xác |
| Redirect | Mã không có trong DB | 404 |
| URL gốc | Có query string, ký tự mã hóa, fragment | Giữ đúng địa chỉ đích theo quy ước đã chốt |
| Cache | Xóa cache rồi truy cập hai lần | Lần đầu MISS, lần sau HIT |
| Cache | Hết TTL hoặc bị loại bỏ khi đầy | Tra lại DB và nạp lại cache |
| Lưu trữ | Tắt và mở lại server | URL cũ hoạt động, cache và bộ đếm bắt đầu lại |
| API demo | Xem metadata hoặc stats | Không tự tăng bộ đếm redirect |
| API demo | Tắt demo mode | Các thao tác quản lý cache/bộ đếm không truy cập được |

Dùng database tạm và cache độc lập cho kiểm thử. Khi kiểm tra mã 301/302 bằng TestClient, tắt tự động đi theo chuyển hướng để đọc status và Location. Không dùng thời gian phản hồi đo đơn lẻ để khẳng định hiệu năng.

### Kiểm tra giao diện thủ công

- [ ] Dán URL và nhấn Enter hoặc bấm nút đều hoạt động.
- [ ] Có nhãn nhập liệu, thao tác bàn phím và trạng thái đang xử lý rõ ràng.
- [ ] URL dài không làm vỡ bố cục.
- [ ] Sao chép thành công, có phương án sao chép tay khi trình duyệt không cho phép.
- [ ] Mở liên kết đến đúng trang đích.
- [ ] Người dùng sửa và gửi lại sau lỗi được.
- [ ] Giao diện dùng được trên màn hình điện thoại.
- [ ] Dữ liệu đầu vào được hiển thị bằng nội dung văn bản an toàn.
- [ ] Kiểm tra cả bản phát triển và bản frontend đã build.

## 13. Kịch bản demo trước giảng viên

| Bước | Thao tác | Nội dung giải thích |
|---|---|---|
| 1 | Mở giao diện một trang | Mục đích và cách dùng dịch vụ |
| 2 | Dán URL dài, bấm Rút gọn | Gửi API, tạo ID, đổi Base62, lưu SQLite |
| 3 | Sao chép và mở URL ngắn | Dịch vụ thực hiện chuyển hướng thật |
| 4 | Gửi lại URL với cùng chế độ | Tái sử dụng ánh xạ đã có |
| 5 | Xem metadata qua `/docs` | Đối chiếu ID và mã Base62 |
| 6 | Xóa cache và reset bộ đếm, gọi URL 302 hai lần | Quan sát MISS rồi HIT, số lần tra DB |
| 7 | Tạo URL 301 riêng, kiểm tra response | So sánh ý nghĩa 301/302 và chính sách cache |
| 8 | Khởi động lại server, mở URL đã tạo | SQLite giữ dữ liệu, cache RAM bắt đầu lại |

Lưu ý khi thực hiện:

- Dùng Developer Tools với Preserve log để quan sát phản hồi chuyển hướng. Khi minh họa cache trình duyệt, không bật tùy chọn Disable cache.
- Có thể dùng công cụ HTTP không tự theo redirect để đọc chính xác status, Location và X-Cache. Không dùng riêng request HEAD nếu ứng dụng mới chỉ triển khai GET.
- `fetch` trên trình duyệt thường tự theo redirect. Chế độ manual cũng có giới hạn quan sát do bảo mật, nên không thiết kế giao diện chính phụ thuộc vào việc đọc status redirect bằng cách đó.
- Kiểm tra cache RAM qua yêu cầu HTTP và bộ đếm phía server, tách khỏi thí nghiệm cache trình duyệt.
- Nếu Internet không ổn định, chuẩn bị một trang đích cục bộ độc lập mà server truy cập được; vẫn trình diễn bằng chuyển hướng HTTP thật.
- Chuẩn bị trước câu trả lời về ID tự tăng, độ dài Base62 và giới hạn cache trong một tiến trình.

## 14. Bàn giao và các bước mở rộng

### Hồ sơ bàn giao

- [ ] Source backend và frontend.
- [ ] Requirements và lockfile đã chốt.
- [ ] `.env.example` với các cấu hình cần thiết.
- [ ] README có hướng dẫn cài, chạy, build và kiểm thử trên Windows.
- [ ] API.md mô tả request, response và lỗi.
- [ ] DEMO.md ghi thao tác trình bày và dữ liệu mẫu.
- [ ] `.gitignore` bỏ qua `.venv`, `node_modules`, dữ liệu SQLite chạy thật và cấu hình cá nhân.
- [ ] Kiểm tra khởi động từ một môi trường cài mới theo README.

### Trước khi mở dịch vụ công khai

1. Chốt địa chỉ công khai và HTTPS.
2. Bảo đảm SQLite nằm trên ổ lưu bền vững và có phương án sao lưu.
3. Tắt endpoint điều khiển demo.
4. Bổ sung giới hạn yêu cầu và xử lý lạm dụng phù hợp với phạm vi sử dụng.
5. Kiểm tra liên kết từ một thiết bị hoặc mạng khác.
6. Ghi rõ khả năng phục vụ hiện tại và theo dõi lỗi thực tế trước khi mở rộng.

Nếu cần nhiều tiến trình hoặc nhiều server, chuyển cache/bộ đếm dùng chung sang Redis hoặc dịch vụ phù hợp, đồng thời đánh giá lại SQLite và cách cấp ID. Đây là giai đoạn mở rộng sau bản đầu.

## 15. Tài liệu tham khảo

Các quyết định về giao diện, tên API, TTL cache và cấu trúc thư mục trong tài liệu này là đề xuất cho dự án.

- [FastAPI: khởi tạo ứng dụng và tài liệu API tương tác](https://fastapi.tiangolo.com/tutorial/first-steps/)
- [FastAPI: kiểm thử với TestClient](https://fastapi.tiangolo.com/tutorial/testing/)
- [FastAPI: RedirectResponse và các response tùy chỉnh](https://fastapi.tiangolo.com/advanced/custom-response/#redirectresponse)
- [FastAPI: cấu hình CORS](https://fastapi.tiangolo.com/tutorial/cors/)
- [Vite: yêu cầu môi trường và mẫu TypeScript](https://vite.dev/guide/)
- [Python: module sqlite3](https://docs.python.org/3/library/sqlite3.html)
- [SQLite: Write-Ahead Logging](https://www.sqlite.org/wal.html)
- [RFC 9110: ý nghĩa 301 và 302](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.4.2)
- [RFC 9111: quy tắc cache HTTP](https://www.rfc-editor.org/rfc/rfc9111.html)
