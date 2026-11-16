# Giải thích mã nguồn URL Shortener để chuẩn bị demo

Đối chiếu trực tiếp với mã nguồn ngày **16/11/2026**. Tài liệu giải thích các hàm do dự án viết, gồm backend, frontend, script và kiểm thử. Các hàm của thư viện như `fetch`, `execute`, `Depends` được giải thích tại nơi chúng được dùng; không cần học thuộc toàn bộ mã nguồn thư viện.

Đường đọc nhanh: **mục 1** nắm luồng; **mục 5–7** nắm Base62/SQLite/cache; **mục 9–10** nắm HTTP và khởi động; **mục 12–13** nắm giao diện; **mục 15–18** tra script/test; **mục 19** luyện hỏi đáp. Mỗi hàm sản phẩm có giải thích đầu vào, đầu ra, cách chạy hoặc câu trả lời miệng tương ứng.

## 1. Đọc ứng dụng từ trên xuống

Hãy hình dung dịch vụ như một quầy gửi đồ:

- URL gốc là món đồ cần giữ thông tin.
- ID là số thứ tự do cơ sở dữ liệu cấp.
- Mã Base62 là cách viết gọn số thứ tự đó.
- SQLite là sổ ghi lâu dài: mã nào tương ứng với URL nào.
- Cache RAM là những mục vừa được tra, đặt sẵn trên bàn để dùng lại.
- FastAPI là người tiếp nhận yêu cầu và điều phối các bước.
- Frontend là màn hình giúp người dùng nhập và nhận kết quả.

**Một URL không được nén thành URL khác.** Hệ thống tạo một địa chỉ mới trỏ tới một bản ghi. Khi mở địa chỉ mới, server đọc bản ghi và hướng trình duyệt tới URL gốc.

### Luồng khởi động

```text
scripts/start.ps1 → Uvicorn import app.main:app
  → create_app()
    → Settings.from_env() → Settings.__post_init__()
    → LinkService.__init__()
      → Database.__init__() + RedirectCache.__init__()
    → đăng ký route, xử lý lỗi và tài nguyên tĩnh
  → lifespan() → Database.initialize()
  → bắt đầu nhận yêu cầu
```

Tạo đối tượng `Database` không đồng nghĩa đã tạo bảng. Tạo bảng diễn ra trong `initialize()` khi ứng dụng bắt đầu vòng đời phục vụ.

### Luồng tạo liên kết

```text
Form submit trong main.ts
  → shortenUrl() gửi POST /api/v1/links
  → FastAPI đọc JSON và tạo CreateLink
    → integer_status() và valid_url() kiểm tra dữ liệu
  → service() cung cấp LinkService cho route
  → routes.create_link()
    → LinkService.create()
      → Database.create_or_get()
        → đã có: trả Link cũ + True
        → chưa có: INSERT lấy ID → encode(ID) → UPDATE mã → commit
      → LinkService.response()
    → trả 200 nếu dùng lại, 201 nếu tạo mới
  → frontend hiển thị kết quả
```

Đây là sơ đồ quan hệ gọi hàm, không có nghĩa mọi nhánh dependency và kiểm tra request của FastAPI luôn được thực hiện đúng theo thứ tự dòng trong sơ đồ. Điểm quan trọng: body không hợp lệ sẽ không đi vào nghiệp vụ tạo bản ghi.

### Luồng mở liên kết

```text
Người dùng mở GET /r/{code}
  → routes.redirect()
    → RedirectCache.resolve(code, Database.find)
      → có mục còn hạn: HIT, lấy Link từ RAM
      → không có: MISS, gọi Database.find(code)
        → có bản ghi: nạp cache
        → không có: trả None
    → có Link: trả 301/302 + Location
    → không có Link: trả 404
  → trình duyệt nhận Location và truy cập trang đích
```

Server rút gọn không tải trang đích để hiển thị hộ người dùng. Trình duyệt thực hiện yêu cầu tiếp theo tới trang đích.

## 2. Một số cú pháp sẽ gặp nhiều lần

| Cú pháp | Cách hiểu trong dự án |
| --- | --- |
| `self` | Đối tượng hiện tại, ví dụ cache đang được dùng |
| `cls` | Lớp hiện tại trong class method, ví dụ `Settings` |
| `__init__` | Hàm chuẩn bị đối tượng ngay khi tạo đối tượng |
| `@dataclass` | Python tự tạo các hàm thông thường cho lớp dữ liệu, như hàm khởi tạo |
| `frozen=True` | Chặn việc gán lại thuộc tính theo cách thông thường sau khi tạo |
| `@classmethod` | Hàm nhận lớp `cls`, có thể gọi bằng tên lớp |
| `@staticmethod` | Hàm nằm trong lớp nhưng không cần `self` hay `cls` |
| `@router.get(...)` / `@router.post(...)` | Đăng ký đường dẫn HTTP cho hàm bên dưới |
| `Depends(service)` | FastAPI gọi dependency để lấy giá trị và truyền vào tham số |
| `Link \| None` | Kết quả có thể là một `Link` hoặc không tìm thấy |
| `tuple[Link, bool]` | Trả hai giá trị: bản ghi và cờ cho biết có dùng lại hay không |
| `**dict(row)` | Bung các cặp tên/giá trị thành tham số có tên |
| `with` | Thực hiện một khối có bước vào/ra được quản lý; tác dụng cụ thể tùy đối tượng |
| `yield` | Tạm nhường quyền xử lý cho bên dùng; sau đó tiếp tục phần dọn dẹp |
| `try/finally` | Phần `finally` vẫn chạy khi hoàn tất bình thường, lỗi hoặc rời khối bằng `return` |
| `async` / `await` | Biểu diễn xử lý bất đồng bộ; trong JS, `await` đợi Promise mà không cần chặn toàn bộ giao diện |
| `=>` trong TypeScript | Cách viết hàm ngắn; các callback sự kiện không nhất thiết có tên |
| `as T` trong TypeScript | Khẳng định kiểu với trình biên dịch; không tự kiểm tra dữ liệu khi chạy |

## 3. Backend: cấu hình — `backend/app/config.py`

### Lớp `Settings`

Lưu địa chỉ công khai, đường dẫn DB, giới hạn cache, TTL, chế độ demo và nơi đặt frontend đã build. Mặc định `demo_mode=False`; tệp `.env.example` chủ động bật demo để dùng cục bộ.

`PROJECT_ROOT = Path(__file__).resolve().parents[2]` tìm thư mục gốc từ vị trí của chính tệp code. Vì vậy, đường dẫn DB không phụ thuộc việc terminal đang đứng ở thư mục nào.

### `Settings.__post_init__(self)`

**Được gọi khi nào?** Dataclass gọi tự động sau khi tạo `Settings`.

**Làm gì?**

1. Bỏ dấu `/` cuối `public_base_url` để ghép `/r/{code}` không tạo dấu `/` thừa.
2. Dùng `TypeAdapter(AnyHttpUrl)` kiểm tra cấu trúc địa chỉ HTTP/HTTPS, gồm các trường hợp như port không hợp lệ.
3. Dùng `urlsplit()` tách địa chỉ thành các phần để kiểm tra riêng: phải có hostname; không nhận path, query, fragment, username hoặc khoảng trắng theo các điều kiện trong code.
4. Yêu cầu dung lượng cache ít nhất 1 và TTL lớn hơn 0, hữu hạn. Biểu thức so sánh cũng từ chối `NaN` và vô cực cho TTL.
5. Chuyển đường dẫn DB và frontend thành đường dẫn tuyệt đối theo gốc dự án.

**Vì sao dùng `object.__setattr__`?** `Settings` là dataclass đóng băng. Trong bước chuẩn hóa ban đầu, code dùng cơ chế này để đặt giá trị đã chuẩn hóa; sau đó code thông thường không nên sửa cấu hình.

**Ví dụ:** `database_path=Path('backend/data/shortener.db')` trở thành đường dẫn đầy đủ trong dự án, dù terminal đang ở `docs`.

**Khi lỗi:** ném exception và không tạo được cấu hình hợp lệ. Đây là lỗi cấu hình lúc khởi động, không phải lỗi 422 của người dùng nhập URL.

**Câu trả lời miệng:** “Hàm này kiểm tra và chuẩn hóa cấu hình một lần, giúp server không chạy với địa chỉ hoặc giới hạn cache sai.”

### `Settings.from_env(cls)`

**Đầu vào:** không cần truyền đối số khi gọi `Settings.from_env()`; hàm tự đọc `.env` tại gốc và biến môi trường của tiến trình.

**Các bước:** đọc `.env` thành dictionary; ghép với `os.environ`; giá trị trùng ở `os.environ` thắng vì được đặt sau. Sau đó kiểm tra `DEMO_MODE`, chuyển chuỗi số thành `int`/`float` và gọi `cls(...)` để tạo `Settings`. Khi đó `__post_init__()` lại kiểm tra cấu hình vừa tạo.

**Đầu ra:** một đối tượng `Settings`.

**Ví dụ:** `.env` ghi `CACHE_MAX_ITEMS=1000` nhưng biến môi trường đặt `CACHE_MAX_ITEMS=20` thì ứng dụng dùng 20.

**Lưu ý:** chỉ nhận `true`, `false`, `1`, `0` sau khi đổi chữ thường. Hàm không tự theo dõi thay đổi `.env`; cần khởi động lại để nạp cấu hình mới.

**Câu trả lời miệng:** “Hàm này tách cấu hình khỏi code, ưu tiên biến môi trường khi triển khai và chuyển dữ liệu chuỗi thành đúng kiểu.”

## 4. Backend: dữ liệu vào/ra — `backend/app/schemas.py`

### Lớp `CreateLink`

Là mẫu dữ liệu request, không phải bảng DB. FastAPI dùng Pydantic để đọc body thành đối tượng này trước khi gọi nghiệp vụ tạo liên kết.

- `long_url`: chuỗi bắt buộc.
- `redirect_status`: chỉ 301 hoặc 302; bỏ qua thì mặc định 302.
- `extra='forbid'`: từ chối trường không được định nghĩa, giúp phát hiện request sai.
- `strict=True`: hạn chế việc âm thầm đổi kiểu đầu vào.

### `CreateLink.integer_status(cls, value)`

Chạy với `mode='before'`, tức kiểm tra giá trị gốc của trường trước bước kiểm tra kiểu của trường đó. `type(value) is not int` từ chối `"301"`, `301.0` và `True`.

Sau khi hàm trả về số nguyên, `Literal[301, 302]` còn kiểm tra giá trị: số nguyên `307` vẫn bị từ chối. **Hàm này kiểm tra kiểu; `Literal` giới hạn giá trị.** Hai lớp kiểm tra bổ sung cho nhau.

**Đầu ra:** chính giá trị đầu vào nếu là số nguyên. **Khi sai:** `ValueError`, sau đó FastAPI xử lý thành lỗi request 422.

**Câu trả lời miệng:** “Em chỉ nhận số nguyên 301 hoặc 302, không tự đổi chuỗi hay số thập phân thành mã HTTP.”

### `CreateLink.valid_url(cls, value)`

**Đầu vào:** `long_url` sau khi đã được kiểm tra là chuỗi.

**Các bước:**

1. `strip()` bỏ khoảng trắng hai đầu.
2. Từ chối chuỗi rỗng hoặc dài quá 4096 ký tự sau trim.
3. Regex từ chối khoảng trắng bên trong, ký tự điều khiển và DEL; điều kiện riêng từ chối dấu `\`.
4. `urlsplit()` tách URL; yêu cầu scheme HTTP/HTTPS và có phần địa chỉ mạng.
5. `http_url.validate_python(value)` kiểm tra thêm bằng parser URL của Pydantic.
6. Trả chuỗi đã trim, không lấy chuỗi URL được parser chuẩn hóa lại.

**Ví dụ:** `  https://example.com/a?x=%2F#part  ` được lưu thành `https://example.com/a?x=%2F#part`. Query và fragment không bị xóa.

**Vì sao không chỉ dùng `urlsplit()`?** Tách được thành phần không đồng nghĩa mọi thành phần đều hợp lệ; Pydantic bổ sung kiểm tra, chẳng hạn port.

**Giới hạn:** URL hợp lệ về cấu trúc không chứng minh trang đích đang tồn tại hoặc đáng tin cậy. Hàm không truy cập Internet, không xác minh nội dung trang và không bảo đảm rút gọn xong là trang đích sẽ mở được.

**Câu trả lời miệng:** “Hàm chỉ nhận URL HTTP/HTTPS hợp lệ về cấu trúc, giữ đúng địa chỉ người dùng và không tải trang đích.”

### Lớp `LinkResponse`

Định nghĩa dữ liệu trả qua API: ID, mã, URL ngắn đầy đủ, URL gốc, chế độ, thời gian và `reused`. Pydantic kiểm tra/biểu diễn dữ liệu đầu ra theo model; thời gian trong Python được serialize thành chuỗi trong JSON.

### Lớp `DemoStats`

Định nghĩa bảy trường thống kê. Nó không tự đếm; các giá trị được tạo bởi `RedirectCache`. Các lớp schema không tự ghi DB.

## 5. Backend: Base62 — `backend/app/base62.py`

### Hằng số `ALPHABET`

```text
0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ
```

Vị trí của ký tự chính là giá trị chữ số: `0→0`, `10→a`, `35→z`, `36→A`, `61→Z`. Đổi thứ tự bảng chữ cái sẽ đổi kết quả mã hóa.

### `encode(number)`

**Mục đích:** chuyển một số nguyên không âm sang biểu diễn cơ số 62.

**Đầu vào/ra:** `encode(62)` trả `"10"`; `encode(11157)` trả `"2TX"`.

**Giải thích từng bước:**

```python
if isinstance(number, bool) or not isinstance(number, int) or number < 0:
    raise ValueError(...)
```

Loại số âm, số thực và chuỗi. Cần loại riêng `bool` vì trong Python, `bool` là lớp con của `int`; `isinstance(True, int)` là `True`.

```python
if number == 0:
    return "0"
```

Nếu không có trường hợp đặc biệt này thì vòng lặp không chạy và kết quả của số 0 sẽ thành chuỗi rỗng.

```python
number, remainder = divmod(number, 62)
digits.append(ALPHABET[remainder])
```

`divmod` trả thương nguyên và số dư. Lưu ký tự ứng với số dư, rồi tiếp tục chia phần thương.

| Lần | Số đang xét | Thương | Dư | Ký tự vừa lấy |
| --- | --- | --- | --- | --- |
| 1 | 11157 | 179 | 59 | X |
| 2 | 179 | 2 | 55 | T |
| 3 | 2 | 0 | 2 | 2 |

Danh sách đang là `['X', 'T', '2']`, tức lấy từ hàng thấp lên hàng cao. `reversed(digits)` đảo lại và `''.join(...)` ghép thành `2TX`.

**Tác động dữ liệu:** không đọc/ghi SQLite, không dùng cache, không sửa đối tượng bên ngoài. Độ phức tạp theo số chữ số là `O(log₆₂ N)` với `N > 0`.

**Câu hỏi hay gặp:**

- **Có phải hash URL không?** Không. Hàm nhận ID số, không nhận URL.
- **Hai ID khác nhau có cùng mã không?** Không với phép chuyển cơ số đầy đủ và cùng bảng chữ cái. Nếu tự cắt mã thì bảo đảm đó không còn đúng.
- **Có giải mã Base62 thành URL gốc không?** Chỉ có thể đổi mã số về ID; URL gốc vẫn cần ánh xạ đã lưu. Bản hiện tại tìm trực tiếp bằng `short_code`, không có hàm decode.
- **Vì sao không đủ 7 ký tự?** Độ dài Base62 tăng theo ID. Mã `1` là hợp lệ. ID `62^7` cần 8 ký tự.
- **Đây có phải cơ chế bảo mật?** Không. ID tuần tự khiến mã dễ đoán; liên kết ngắn không phải mật khẩu hay quyền truy cập.

**Câu trả lời miệng:** “Hàm liên tục chia ID cho 62, đổi số dư sang ký tự rồi đảo thứ tự. Nó chỉ viết gọn ID, còn URL gốc nằm trong DB.”

## 6. Backend: SQLite — `backend/app/database.py`

### Lớp dữ liệu `Link`

Đại diện một bản ghi đã lấy từ DB trong bộ nhớ Python, gồm `id`, `short_code`, `long_url`, `redirect_status`, `created_at`. Dataclass tạo hàm khởi tạo tự động; `frozen=True` giúp tránh sửa nhầm bản ghi trong cache. Nó không phải ORM và không tự chạy câu SQL.

### `Database.__init__(self, path)`

Nhận `Path` của tệp SQLite và lưu vào `self.path`. Chưa mở kết nối, chưa tạo bảng và chưa đọc dữ liệu.

**Câu trả lời miệng:** “Hàm khởi tạo chỉ ghi nhớ DB nằm ở đâu; kết nối được mở khi có thao tác cần dùng.”

### `Database.connect(self)`

Đây là context manager do `@contextmanager` tạo từ một generator. Khi dùng `with self.connect() as connection`, hàm:

1. Mở kết nối mới bằng `sqlite3.connect(self.path, timeout=10)`.
2. Đặt `row_factory = sqlite3.Row`, giúp đọc kết quả theo tên cột thay vì chỉ theo vị trí.
3. `yield connection` giao kết nối cho khối `with` sử dụng.
4. Chạy `finally: connection.close()` để đóng kết nối sau đó, kể cả khi có lỗi.

`timeout=10` cho phép chờ khóa DB trước khi báo lỗi khi DB bận; đây không phải hạn mức 10 giây cho mọi câu SQL.

**Phân biệt quan trọng:** `with self.connect()` trong dự án quản lý **đóng kết nối**. `with connection` của SQLite trong `create_or_get()` quản lý **commit/rollback transaction**; nó không thay thế việc đóng kết nối. Đây là hai lớp có trách nhiệm khác nhau.

Mỗi thao tác có kết nối riêng, tránh chia sẻ tùy tiện cùng một connection giữa nhiều thread. Một đối tượng `Database` dùng chung không có nghĩa một connection được dùng chung.

**Câu trả lời miệng:** “Hàm cấp một kết nối cho một thao tác và bảo đảm đóng nó, kể cả khi thao tác thất bại.”

### `Database.initialize(self)`

Được `lifespan()` gọi lúc khởi động. Hàm tạo thư mục DB nếu chưa có, mở connection, đặt `journal_mode=WAL`, tạo bảng nếu chưa tồn tại rồi commit.

**Ý nghĩa các phần của bảng:**

| Khai báo | Vai trò |
| --- | --- |
| `id INTEGER PRIMARY KEY AUTOINCREMENT` | SQLite cấp ID; ứng dụng không tự lấy số lượng bản ghi cộng 1 |
| `short_code TEXT COLLATE BINARY UNIQUE` | Mã không trùng và so sánh có phân biệt hoa/thường |
| `long_url TEXT NOT NULL` | Mỗi bản ghi phải có URL gốc |
| `CHECK (redirect_status IN (301, 302))` | DB cũng chặn chế độ ngoài 301/302 |
| `created_at TEXT NOT NULL` | Lưu thời gian tạo theo UTC dưới dạng chuỗi ISO |
| `UNIQUE (long_url, redirect_status)` | Cùng URL và cùng chế độ chỉ có một bản ghi |

**WAL:** là chế độ journal hỗ trợ đọc và ghi cùng tồn tại thuận lợi hơn; không biến SQLite thành DB có nhiều writer đồng thời và không phải giải pháp mở rộng vô hạn.

**Chi tiết có thể được hỏi:** `short_code` không khai báo `NOT NULL` vì lúc INSERT cần lấy ID trước. Nó tạm là NULL bên trong transaction, rồi UPDATE mã trước commit. Luồng tạo của ứng dụng không commit một bản ghi còn thiếu mã; code khác ghi trực tiếp DB vẫn phải tự tuân thủ điều kiện này. `CREATE TABLE IF NOT EXISTS` cũng không tự nâng cấp schema của một bảng cũ đã có.

**Câu trả lời miệng:** “Hàm chuẩn bị DB và các ràng buộc để dữ liệu không trùng, không nhận chế độ sai và giữ được sau khi tắt ứng dụng.”

### `Database.create_or_get(self, long_url, redirect_status)`

Đây là hàm quan trọng nhất của luồng tạo URL. Nó trả `(Link, reused)`: `reused=True` khi dùng lại, `False` khi tạo mới.

**Các bước và lý do:**

1. `with self.connect()` mở connection riêng.
2. `with connection` thiết lập phạm vi commit/rollback cho transaction được mở bên trong.
3. `BEGIN IMMEDIATE` bắt đầu transaction và lấy quyền ghi ngay từ đầu, trước câu kiểm tra trùng.
4. `SELECT ... WHERE long_url = ? AND redirect_status = ?` tìm đúng cặp dữ liệu.
5. Có dòng: chuyển `sqlite3.Row` thành `dict`, bung vào `Link(...)`, trả bản ghi và `True`.
6. Chưa có: lấy thời gian UTC, INSERT URL/chế độ/thời gian.
7. `cursor.lastrowid` lấy ID vừa được SQLite cấp.
8. `encode(link_id)` tạo mã và UPDATE đúng bản ghi vừa INSERT.
9. Trả `Link(...), False`; khi thoát `with connection` bình thường, transaction được commit, rồi connection được đóng.

**Vì sao không `SELECT COUNT(*) + 1`?** Hai request có thể đọc cùng số lượng; xóa bản ghi cũng làm cách đó sai. DB cấp ID dưới cơ chế đồng bộ của nó.

**Vì sao có `assert link_id is not None`?** Đó là kiểm tra giả định nội bộ: INSERT thành công phải lấy được ID. Nó không thay thế kiểm tra dữ liệu request; Python chạy chế độ tối ưu có thể bỏ `assert`.

**Hai request giống nhau đến cùng lúc thì sao?**

```text
A: BEGIN IMMEDIATE → có quyền ghi
B: BEGIN IMMEDIATE → chờ
A: SELECT chưa có → INSERT → UPDATE mã → COMMIT
B: lấy được quyền ghi → SELECT đã có → trả bản ghi cũ
```

Nếu A giữ khóa quá thời gian chờ, B có thể nhận lỗi DB và API trả 503; không phải mọi request đồng thời đều được bảo đảm thành công ngay. Ràng buộc UNIQUE là lớp bảo vệ dữ liệu bổ sung. Cách này đơn giản và phù hợp bản demo; các thao tác ghi vẫn phải chờ nhau.

**Vì sao `return` trước dòng commit mà dữ liệu vẫn được lưu?** Python xử lý thoát context manager trước khi hoàn tất việc trả giá trị cho bên gọi. Nếu commit thất bại, hàm không trả thành công như dự kiến mà ném exception.

**Nếu encode hoặc UPDATE lỗi?** Transaction rollback: không để lại một lần tạo chỉ mới có URL nhưng thiếu mã.

**Chống SQL injection:** dữ liệu đi qua các dấu `?` và tuple tham số, không ghép chuỗi URL vào câu SQL. Dấu nháy trong dữ liệu được coi là dữ liệu.

**Ví dụ:** lần đầu `(https://example.com, 302)` tạo ID 1/mã `1`; lần sau cùng cặp trả ID 1/mã `1`. Với chế độ 301, đó là cặp khác nên tạo bản ghi riêng.

**Câu trả lời miệng:** “Hàm tìm liên kết cũ trong một transaction đã lấy quyền ghi. Nếu chưa có thì để SQLite cấp ID, đổi sang Base62 và lưu đủ dữ liệu rồi mới commit.”

### `Database.find(self, code)`

Mở connection, chạy SELECT có tham số theo `short_code`, gọi `fetchone()`. Có kết quả thì trả `Link(**dict(row))`; không có thì trả `None`.

Hàm này không tự trả 404 và không tự đếm cache miss. Nó chỉ làm công việc truy vấn. Cùng hàm này được dùng trong metadata và luồng redirect; chỉ khi gọi qua `RedirectCache.resolve()` mới có bộ đếm redirect tương ứng.

**Câu trả lời miệng:** “Hàm tìm ánh xạ bằng mã; việc quyết định trả metadata hay chuyển hướng thuộc lớp gọi nó.”

### `Database.healthy(self)`

Chạy `SELECT 1 FROM links LIMIT 1` để kiểm tra có thể truy cập bảng. Bảng rỗng vẫn là trạng thái khỏe: `fetchone()` có thể trả `None` nhưng hàm vẫn trả `True` nếu truy vấn không lỗi.

Hàm không kiểm tra Internet, không truy cập URL đích và không chứng minh ổ đĩa còn đủ dung lượng cho mọi lần ghi tiếp theo. Nếu kết nối/query lỗi, exception đi tới handler DB của ứng dụng.

**Câu trả lời miệng:** “Health kiểm tra server có đọc được bảng DB; không yêu cầu đã có dữ liệu.”

## 7. Backend: cache — `backend/app/cache.py`

### `RedirectCache.__init__(self, max_items, ttl, clock=monotonic)`

Lưu giới hạn số mục, thời gian sống và hàm lấy thời gian; tạo `RLock`, `OrderedDict` rỗng và bộ đếm bằng 0.

Mỗi phần tử cache là `code → (Link, expires_at)`. Giá trị thực tế chứa cả `Link`, không chỉ URL. Đầu OrderedDict là mục ít được sử dụng gần đây hơn; mục mới/thường được chạm sẽ ở cuối.

`monotonic()` đo thời gian trôi qua, phù hợp tính TTL vì không phụ thuộc chỉnh đồng hồ lịch. `clock` được truyền vào giúp kiểm thử thay bằng đồng hồ giả; không cần `sleep()` chờ hết TTL.

`RLock` ngăn các thread sửa trạng thái dùng chung cùng lúc và cho phép thread đang giữ khóa lấy lại chính khóa đó nếu cần. Code hiện tại chủ yếu dùng khóa để bảo vệ các thao tác tổng hợp; không dựa vào GIL để coi toàn bộ luồng là nguyên tử.

**Câu trả lời miệng:** “Hàm tạo một cache RAM có giới hạn, có hạn dùng và có khóa bảo vệ, riêng cho instance ứng dụng này.”

### `RedirectCache._empty_counters()`

Static method trả dictionary **mới** với năm bộ đếm bằng 0: `redirect_requests`, `cache_hits`, `cache_misses`, `db_lookup_count`, `not_found`. Không có `cache_size` hoặc `hit_rate` ở đây vì hai số đó được tính từ trạng thái hiện tại.

Được dùng khi khởi tạo và reset. Dấu `_` thể hiện hàm dùng nội bộ theo quy ước Python, không phải cơ chế cấm truy cập tuyệt đối.

### `RedirectCache._purge_expired(self)`

Lấy thời gian hiện tại, duyệt bản sao `list(self._items.items())`, xóa những mục có `expires <= now`. Duyệt bản sao vì xóa ngay trong khi duyệt OrderedDict gốc có thể làm việc lặp không hợp lệ.

Không có thread nền chạy cứ mỗi giây. Việc dọn diễn ra khi `resolve()` hoặc `stats()` gọi hàm này. Một mục hết hạn có thể còn nằm vật lý trong RAM đến lần gọi kế tiếp; nó sẽ bị dọn trước khi được dùng để trả HIT.

Hàm nội bộ này không tự lấy khóa. Các chỗ gọi hiện tại đều đang ở trong `with self._lock`.

**Độ phức tạp:** quét `O(K)` với K mục; đây là lựa chọn đơn giản khi cache mặc định tối đa 1000 mục. Không nên mô tả toàn bộ luồng cache này luôn `O(1)`.

### `RedirectCache.resolve(self, code, lookup)`

`lookup` là một hàm sẽ được gọi khi cần tra DB. Route truyền `current.database.find`, tức truyền khả năng gọi hàm, không gọi nó trước.

**Tất cả các bước sau nằm trong khóa:**

1. Tăng `redirect_requests` cho yêu cầu đã vào luồng redirect.
2. Dọn các mục hết hạn.
3. Nếu có code: tăng `cache_hits`, `move_to_end(code)` để đánh dấu vừa dùng, trả `(Link, 'HIT')`.
4. Nếu không có: tăng `cache_misses` và `db_lookup_count`, rồi gọi `lookup(code)`.
5. DB không có: tăng `not_found`, trả `(None, 'MISS')`; không nạp cache âm.
6. DB có: lưu `(link, self.clock() + self.ttl)`.
7. Nếu vượt giới hạn: `popitem(last=False)` bỏ mục đầu, tức ít được dùng gần đây nhất.
8. Trả `(Link, 'MISS')`.

**Ví dụ khi DB đã có mã `a` và vừa clear/reset:**

| Yêu cầu | Kết quả cache | Tổng request | Hit | Miss | DB lookup |
| --- | --- | --- | --- | --- | --- |
| GET `/r/a` lần 1 | MISS | 1 | 0 | 1 | 1 |
| GET `/r/a` lần 2 | HIT | 2 | 1 | 1 | 1 |

**Ví dụ LRU với sức chứa 2:** truy cập `a`, `b`, `a`, `c` sẽ loại `b` vì `a` vừa được dùng lại.

**Các chi tiết dễ bị hỏi:**

- HIT thay thứ tự LRU nhưng không đổi thời điểm hết hạn. TTL tính từ lúc nạp, không kéo dài theo mỗi lần đọc.
- Tạo URL không gọi hàm này, nên tạo xong chưa có mục cache.
- Không tìm thấy cũng là một MISS và một lần tra DB.
- Nếu lookup DB ném lỗi, các bộ đếm request/miss/lookup đã tăng; `db_lookup_count` thể hiện số lần cố gọi tra DB trong luồng này, không bảo đảm mọi truy vấn đều thành công.
- Khóa được giữ cả khi đọc DB. Cách này làm bộ đếm nhất quán và tránh nhiều lần nạp trùng khi đồng thời, nhưng cũng khiến các request redirect chờ nhau trong đoạn này. Không dùng nó để khẳng định hệ thống đạt lưu lượng lớn.

**Câu trả lời miệng:** “Hàm tra RAM trước. Có thì HIT và không đọc DB; không có thì MISS, gọi DB rồi nạp kết quả còn tồn tại vào cache.”

### `RedirectCache.stats(self)`

Lấy khóa, dọn mục hết hạn, đọc bộ đếm và trả dictionary kết quả gồm năm bộ đếm, `cache_size=len(self._items)` và `hit_rate`.

```text
hit_rate = hits / (hits + misses)
```

Nếu chưa có lần tra cứu thì trả 0, tránh chia cho 0. Một HIT và một MISS cho `hit_rate=0.5`, tức 50%, không phải số 50 trong response JSON.

Hàm không tăng bộ đếm. Tuy nhiên nó có thể dọn mục đã hết hạn, nên không nên nói rằng stats hoàn toàn không tác động trạng thái cache. Dictionary kết quả là bản trả riêng; không trao nguyên dictionary bộ đếm nội bộ cho bên gọi sửa.

### `RedirectCache.clear(self)`

Lấy khóa và xóa các mục trong `_items`. Không xóa SQLite và không đặt bộ đếm về 0. Lượt mở liên kết hợp lệ tiếp theo thường phải đọc DB rồi nạp lại.

### `RedirectCache.reset_stats(self)`

Lấy khóa và thay `_counters` bằng một dictionary 0 mới. Không xóa `_items`; nếu cache còn mục hợp lệ thì sau reset, request đầu vẫn có thể HIT.

**So sánh phải nhớ:**

| Thao tác | Liên kết trong SQLite | Cache RAM | Bộ đếm RAM |
| --- | --- | --- | --- |
| Clear cache | Giữ | Xóa | Giữ |
| Reset stats | Giữ | Giữ | Về 0 |
| Tạo instance ứng dụng mới / khởi động lại tiến trình | Giữ tệp DB | Bắt đầu rỗng | Bắt đầu 0 |

Việc reset và clear ở hai HTTP request riêng không phải một thao tác nguyên tử chung. Trong lúc demo cần tránh những lượt mở liên kết khác xen vào nếu muốn quan sát chính xác 2 request.

## 8. Backend: dịch vụ — `backend/app/services.py`

### `LinkService.__init__(self, settings)`

Giữ cấu hình, tạo đối tượng `Database(settings.database_path)` và `RedirectCache(...)`. `create_app()` tạo service một lần cho instance ứng dụng; các route dùng lại service qua `app.state`.

Không tạo lại cache cho từng request. Nếu làm vậy, request sau sẽ không còn cache của request trước. Nhiều process vẫn có service/cache riêng, dù cùng đọc một tệp DB.

### `LinkService.response(self, link, reused)`

Chuyển `Link` thành dictionary bằng `asdict(link)`, thêm URL ngắn đầy đủ và cờ `reused`, rồi tạo `LinkResponse`.

Ví dụ `public_base_url=http://127.0.0.1:8000`, code `a` sẽ ghép thành `http://127.0.0.1:8000/r/a`. Hàm không dựa vào port của trang Vite, không suy ra host từ header request và không thêm DB query.

**Câu trả lời miệng:** “Hàm biến bản ghi nội bộ thành cấu trúc API, đồng thời ghép origin đã cấu hình với mã để tạo URL dùng được.”

### `LinkService.create(self, payload)`

Nhận `CreateLink` đã kiểm tra, gọi `database.create_or_get(...)`, nhận `link, reused`, rồi gọi `self.response(...)`.

Đây là lớp phối hợp: route lo HTTP, Database lo SQL, hàm này lo nối nghiệp vụ với response. Nó không quyết định status 200/201 và không nạp cache chuyển hướng.

## 9. Backend: các route — `backend/app/routes.py`

### `service(request)`

Trả `request.app.state.service`. `Request` ở đây là đối tượng của server do FastAPI cung cấp, không phải trường JSON người dùng gửi.

Các route khai báo `Depends(service)` để FastAPI gọi hàm này và truyền service vào tham số `current`. Đây là dependency lấy service đã có, không tạo DB/cache mới.

### `demo_service(current=Depends(service))`

Lấy service qua dependency ở trên. Nếu `demo_mode=False`, ném `HTTPException(404, ...)`; nếu bật, trả lại service đó.

Các route demo dùng dependency này để chia sẻ cùng một điều kiện bật/tắt. **Đây là cổng cấu hình, không phải đăng nhập hay phân quyền quản trị**. Nếu bật demo trên một server mà người khác truy cập được, người đó cũng có thể gọi API clear/reset.

### `health(current)` — GET `/api/v1/health`

Gọi `current.database.healthy()`. Nếu không lỗi, trả `{'status': 'ok'}` với HTTP 200. Dù không dùng trực tiếp giá trị `True`, lời gọi vẫn cần thiết vì một lỗi DB sẽ ném exception thay vì tiếp tục trả OK.

### `create_link(payload, response, current)` — POST `/api/v1/links`

Gọi `current.create(payload)` để tạo hoặc dùng lại. Đặt `response.status_code = 200 if result.reused else 201`, rồi trả model kết quả.

`Response` được truyền vào để điều chỉnh status động, không phải dữ liệu người dùng. Khai báo route ghi 201 làm mặc định, nhưng nhánh dùng lại ghi đè thành 200. `responses={...}` chủ yếu mô tả response bổ sung trong OpenAPI; bản thân phần mô tả không thực thi nghiệp vụ.

**Câu trả lời miệng:** “Route nhận request hợp lệ, gọi service và dùng cờ reused để phân biệt 201 tạo mới với 200 đã tồn tại.”

### `metadata(code, current)` — GET `/api/v1/links/{code}`

Gọi `database.find(code)` trực tiếp. Không có bản ghi thì 404. Có thì dùng `current.response(link, reused=True)` để trả thông tin.

Không gọi cache nên không tăng bộ đếm redirect, giúp giảng viên xem ID/mã mà không làm thay đổi thí nghiệm MISS/HIT. `reused=True` ở đây thể hiện đang đọc một bản ghi đã tồn tại, không có nghĩa request GET vừa tạo hay vừa thực hiện một lần POST dùng lại.

### `redirect(code, current)` — GET `/r/{code}`

Gọi `current.cache.resolve(code, current.database.find)`. Chú ý **không có dấu `()` sau `find`**: route truyền hàm để cache chỉ gọi khi MISS.

Nếu nhận `None`, ném 404 kèm `X-Cache: MISS` và `Cache-Control: no-store`.

Nếu có `Link`, tạo `RedirectResponse` với:

- URL đích là `link.long_url`.
- Status lấy từ `link.redirect_status`, truyền tường minh để không vô tình dùng status mặc định khác của lớp response.
- `X-Cache` cho biết server vừa HIT hay MISS.
- `Cache-Control: no-store` cho 302.
- `Cache-Control: private, max-age=300` cho 301.

RedirectResponse tạo phản hồi HTTP chứa header `Location`. Trình duyệt theo header để tới trang đích; route không gọi `fetch()` trang đích.

**Hai cache khác nhau:** X-Cache phản ánh RAM của server. Cache-Control điều khiển khả năng lưu phản hồi HTTP ở phía client/intermediary theo chính sách header. Một phản hồi được trình duyệt dùng lại có thể không tạo request mới tới server, nên không tăng stats.

Không có route HEAD riêng; dùng GET không tự theo redirect để quan sát 301/302. Các phản hồi HTTP phải truyền được URL qua header; ký tự Unicode có thể được lớp HTTP percent-encode khi cần.

**Câu trả lời miệng:** “Route lấy ánh xạ qua cache, xử lý mã sai thành 404 và trả Location cùng status 301 hoặc 302 để trình duyệt tự điều hướng.”

### `stats(current)` — GET `/api/v1/demo/stats`

Sau khi qua `demo_service`, gọi `current.cache.stats()` và trả model `DemoStats`. Route chỉ cung cấp dữ liệu, không có bảng thống kê trên giao diện chính và không tự tăng lượt redirect.

### `clear_cache(current)` — POST `/api/v1/demo/cache/clear`

Gọi `cache.clear()`, rồi trả thống kê hiện tại. Nó không xóa liên kết. Chọn POST vì thao tác này thay đổi trạng thái server; không dùng GET để thực hiện reset.

### `reset_stats(current)` — POST `/api/v1/demo/stats/reset`

Gọi `cache.reset_stats()`, rồi trả thống kê hiện tại. Nó không làm cache lạnh. Muốn demo MISS đầu tiên phải clear cache riêng, không chỉ reset counters.

## 10. Backend: khởi tạo ứng dụng — `backend/app/main.py`

### `create_app(settings=None)`

Nhận cấu hình tùy chọn. Không truyền thì gọi `Settings.from_env()`. Sau đó tạo service, tạo FastAPI, gắn service vào `app.state`, đăng ký các handler và router, đăng ký trang chủ/tài nguyên tĩnh, rồi trả đối tượng ứng dụng.

Các hàm được khai báo lồng trong nó sử dụng `settings` và `current` của lần tạo ứng dụng đó. Cách này giúp kiểm thử tạo nhiều app với DB/cache riêng thay vì sửa một biến toàn cục dùng chung cho mọi test.

`app = create_app()` ở cuối tệp cung cấp đối tượng cho Uvicorn import bằng `app.main:app`: phần trước dấu `:` là module, phần sau là tên đối tượng.

**Câu trả lời miệng:** “Đây là hàm lắp ráp ứng dụng: cấu hình, service, route, xử lý lỗi và frontend. Chạy server là trách nhiệm của Uvicorn.”

### `lifespan(app)`

Async context manager được FastAPI sử dụng khi bắt đầu/kết thúc phục vụ. Trước `yield`, gọi `current.database.initialize()` để chuẩn bị DB. Đến `yield` thì nhường quyền cho ứng dụng phục vụ. Nếu có code sau `yield`, đó sẽ là phần dọn dẹp lúc kết thúc; bản hiện tại không có bước dọn dẹp riêng vì connection DB đã đóng theo từng thao tác.

Không nhầm với `Database.connect()`: một hàm quản lý vòng đời ứng dụng, một hàm quản lý vòng đời connection.

### `validation_error(request, error)`

Handler cho `RequestValidationError`. Trả HTTP 422 và một thông báo tiếng Việt thống nhất. Không trả toàn bộ cấu trúc lỗi Pydantic hoặc dữ liệu nội bộ cho frontend.

`request` và `error` là tham số theo giao diện handler; code hiện tại không dùng chúng để ghép response. Handler được đăng ký bởi decorator, không được gọi trực tiếp từ route.

### `database_error(request, error)`

Handler cho lỗi thuộc `sqlite3.Error`, như lỗi DB bận hoặc không truy cập được. Ghi exception vào log server rồi trả 503 với thông báo chung “Máy chủ đang bận. Bạn hãy thử lại.”

Nó không bắt mọi loại exception trong toàn ứng dụng. Không nên nói rằng mọi lỗi Python đều được chuyển thành 503. Nội dung log dùng để chẩn đoán, không gửi thẳng stack trace về trình duyệt.

### `index()` — GET `/`

Tìm `frontend_dist/index.html`. Có tệp thì `FileResponse` gửi trang đã build, kèm `Cache-Control: no-cache`. Header này cho phép lưu nhưng cần kiểm tra lại tính mới trước khi tái sử dụng theo quy tắc HTTP; nó không đồng nghĩa `no-store`.

Chưa có tệp thì trả 503 với hướng dẫn build frontend. `include_in_schema=False` chỉ ẩn route khỏi tài liệu OpenAPI, không chặn truy cập `/`.

Khối `app.mount('/assets', StaticFiles(...))` không phải hàm riêng của dự án. Nó giao các URL tài nguyên như CSS/JS cho bộ phục vụ file tĩnh. Việc kiểm tra thư mục assets và mount diễn ra khi tạo app; nên build trước khi khởi động hoặc khởi động lại sau lần build đầu.

**Về `def` và `async def`:** các route nghiệp vụ dùng SQLite đồng bộ được viết `def`; FastAPI hỗ trợ chạy các path operation đồng bộ qua thread pool. Điều đó không làm SQLite hoặc một tiến trình server tự có khả năng mở rộng phân tán. Xem tài liệu FastAPI ở cuối để phân biệt đồng thời với nhiều process.

## 11. Frontend: kiểu dữ liệu — `frontend/src/types.ts`

### Interface `ShortLink`

Mô tả các trường frontend dự kiến nhận từ API. `redirect_status: 301 | 302` nghĩa là trình biên dịch chỉ cho dùng hai giá trị đó trong vị trí này; `created_at` là chuỗi JSON, không tự thành đối tượng JavaScript `Date`.

Interface TypeScript bị loại khi build. Nó không tự xác minh dữ liệu từ mạng. Backend có kiểm tra model thật khi chạy; còn frontend hiện dùng khẳng định kiểu `as ShortLink` sau khi đọc JSON, chưa có bộ kiểm tra schema response riêng trong trình duyệt.

## 12. Frontend: gọi API — `frontend/src/api.ts`

### `ApiError.constructor(message, status)`

Hàm khởi tạo lỗi riêng của ứng dụng. `super(message)` gọi constructor lớp `Error` để giữ thông báo và thông tin lỗi thông thường. `public status: number` vừa khai báo tham số vừa lưu nó thành thuộc tính của đối tượng lỗi.

Ví dụ: `new ApiError('Liên kết chưa hợp lệ...', 422)` có `message` để hiện cho người dùng và `status` để quyết định đặt thông báo cạnh ô nhập. Giá trị `status=0` là quy ước nội bộ cho lỗi không có response HTTP sử dụng được, không phải status HTTP server gửi về.

### `shortenUrl(longUrl)`

**Đầu vào:** URL gốc đã được phía giao diện kiểm tra sơ bộ. **Đầu ra:** một `Promise<ShortLink>`; khi lỗi thì Promise bị reject bằng `ApiError`.

**Các bước:**

1. Tạo `AbortController` để có thể yêu cầu hủy chờ fetch.
2. Đặt timer: sau 10.000 ms gọi `controller.abort()`.
3. `fetch('/api/v1/links', ...)` gửi POST tới cùng origin của trang. Khi phát triển, Vite proxy chuyển tiếp đường dẫn này sang FastAPI.
4. Header nói body là JSON; `JSON.stringify` biến object thành chuỗi JSON. Giao diện luôn gửi `redirect_status: 302`.
5. `signal` nối fetch với AbortController.
6. Nếu `response.ok` là false: 422 thành thông báo URL chưa hợp lệ; các status lỗi khác thành thông báo chung, kèm status để caller phân biệt.
7. Nếu thành công: `await response.json()` đọc/parse body, rồi trả với kiểu dự kiến `ShortLink`.
8. Trong catch: `ApiError` đã biết được giữ nguyên; các lỗi khác được đổi thành thông báo chưa kết nối được máy chủ.
9. Trong finally: `clearTimeout(timeout)` dọn timer để không giữ một lần hủy không còn cần thiết.

**Callback timer `() => controller.abort()`** là một hàm ngắn được chạy sau thời gian chờ nếu chưa dọn timer. Nó không tự chạy ngay lúc `setTimeout` được gọi.

**Chi tiết phải hiểu:**

- `fetch` thường không ném exception chỉ vì server trả 422/500; code cần kiểm tra `response.ok`.
- Parse JSON lỗi cũng đi vào catch chung. Vì vậy thông báo “Chưa kết nối...” trong bản này bao gồm cả một số response không đọc được, không chỉ mất mạng.
- Hủy fetch không bảo đảm server dừng xử lý. Nếu server đã commit nhưng client bị timeout, người dùng gửi lại cùng cặp URL/chế độ sẽ được cơ chế tái sử dụng ở DB bảo vệ khỏi tạo thêm bản ghi trùng.
- Hàm chỉ POST tạo URL, không tự GET liên kết ngắn để xem trước. Nếu tự GET, nó có thể làm tăng bộ đếm redirect ngoài thao tác mở thực sự.

**Câu trả lời miệng:** “Hàm gửi URL tới API, chờ tối đa khoảng 10 giây phía client, chuyển lỗi kỹ thuật thành thông báo dễ hiểu và trả dữ liệu liên kết cho giao diện.”

## 13. Frontend: sự kiện giao diện — `frontend/src/main.ts`

Đầu tệp import CSS và các hàm API. Sau đó lấy sẵn các phần tử HTML theo ID. Biến `pending` ghi nhận một lần gửi đang chờ; các `const` như `shortLink`, `status`, `result` giữ tham chiếu đến các phần tử, không phải bản sao của nội dung HTML.

### `element<T extends HTMLElement>(id)`

Tìm phần tử bằng `document.getElementById(id)`. Nếu không có thì ném lỗi rõ tên phần tử bị thiếu. Nếu có thì trả với kiểu generic `T` để code có thể dùng đúng thuộc tính, ví dụ `value` của input hoặc `href` của anchor.

**Giới hạn:** `as T` không kiểm tra phần tử thực sự có đúng loại HTML mà caller yêu cầu hay không. Nó giúp TypeScript khi lập trình, không thay thế kiểm tra DOM lúc chạy. Hàm có kiểm tra phần tử tồn tại.

**Câu trả lời miệng:** “Hàm gom thao tác tìm phần tử và báo lỗi sớm nếu HTML không khớp với JavaScript.”

### `showInputError(message)`

Đặt `fieldError.textContent`, bỏ trạng thái hidden, gắn `aria-invalid='true'` vào input và đưa focus về ô nhập. `textContent` thể hiện thông báo dưới dạng chữ, không phân tích thông báo như HTML.

`aria-invalid` giúp công cụ hỗ trợ nhận biết ô nhập có lỗi; `focus()` giúp người dùng sửa ngay bằng bàn phím. Hàm không xóa giá trị vừa nhập.

### Callback `input.addEventListener('input', () => ...)`

Chạy khi người dùng sửa nội dung. Ẩn lỗi cũ và bỏ `aria-invalid` để họ không tiếp tục thấy cảnh báo của dữ liệu trước đó. Hàm này không khẳng định nội dung mới đã đúng và không gửi API; kiểm tra lại diễn ra khi submit.

### Callback `form.addEventListener('submit', async (event) => ...)`

Đây là luồng chính của màn hình. Cùng sự kiện submit được dùng cho bấm nút và nhấn Enter trong form, không cần hai luồng tạo URL riêng.

**Giai đoạn A — chặn hành vi mặc định và kiểm tra:**

1. `event.preventDefault()` tránh form tự chuyển trang/tải lại theo cách HTML mặc định.
2. Nếu `pending` đang true thì return, tránh gửi thêm lần nữa.
3. Xóa lỗi/trạng thái cũ và ẩn kết quả trước, tránh hiển thị nhầm kết quả cũ như của yêu cầu mới.
4. Lấy `input.value.trim()`.
5. `new URL(url)` thử phân tích địa chỉ; kiểm tra protocol HTTP/HTTPS, tiền tố đầy đủ `http(s)://`, ký tự không cho phép và độ dài tối đa.
6. Nếu sai: gọi `showInputError` rồi return, không gửi mạng.

**Vì sao backend vẫn phải kiểm tra?** Người khác có thể dùng curl, `/docs` hoặc viết request riêng và bỏ qua giao diện. Frontend giúp trải nghiệm; backend là nơi quyết định dữ liệu có được chấp nhận hay không.

**Giai đoạn B — đang xử lý:**

Đặt `pending=true`, disable nút, đặt input `readOnly`, gắn `aria-busy` và thay chữ thành “Đang rút gọn...”. `pending` bảo vệ logic ngay cả khi có hai sự kiện đến rất nhanh; disable cung cấp phản hồi trực quan. `readOnly` vẫn cho nhìn/chọn nội dung nhưng ngăn sửa lúc request đang chờ.

**Giai đoạn C — thành công:**

`await shortenUrl(url)` nhận model. Gán nội dung và địa chỉ cho các liên kết, gán URL gốc bằng `textContent`, lưu URL ngắn vào ô copy, reset nhãn copy, ẩn ô copy tay và hiện kết quả. `link.reused` quyết định thông báo tạo mới hay dùng lại.

Các thuộc tính `textContent`, `href` và `value` có vai trò khác nhau: chữ người dùng nhìn thấy, địa chỉ khi bấm và nội dung ô nhập. Code không ghép URL gốc vào `innerHTML`, giúp tránh việc chuỗi như `<img ...>` bị hiểu thành thẻ HTML khi hiển thị.

**Giai đoạn D — lỗi:**

Nếu là `ApiError` 422, báo cạnh ô nhập. Các lỗi khác được đặt ở dòng status chung. Nội dung nhập vẫn còn để người dùng thử lại; kết quả cũ đã ẩn.

**Giai đoạn E — luôn khôi phục:**

Trong finally, đặt `pending=false`, bật lại nút, bỏ readOnly/aria-busy và đổi nhãn về “Rút gọn”. Nhờ vậy request lỗi không làm giao diện bị kẹt ở trạng thái đang gửi.

**Câu trả lời miệng:** “Hàm submit kiểm tra dữ liệu, khóa thao tác gửi lặp, gọi API rồi hiển thị kết quả hoặc lỗi; cuối cùng luôn mở lại form.”

### Callback `copyButton.addEventListener('click', async () => ...)`

Lấy URL hiện tại từ ô copy rồi disable nút trong lúc sao chép. `await navigator.clipboard.writeText(value)` yêu cầu trình duyệt ghi vào clipboard.

- Thành công: đổi nhãn thành “Đã sao chép” và cập nhật dòng status.
- Thất bại hoặc API clipboard không dùng được: hiện ô copy tay, focus và chọn toàn bộ URL bằng `select()`, sau đó hướng dẫn người dùng tự sao chép.
- Finally: bật lại nút copy.

Hàm không đọc lịch sử clipboard và không thay đổi quyền trình duyệt. Clipboard có thể không khả dụng theo môi trường/chính sách của trình duyệt, nên cần nhánh dự phòng. Trong nhánh lỗi, code chỉ chọn sẵn chữ; người dùng vẫn cần lệnh sao chép của họ.

### Callback `copyValue.addEventListener('click', () => copyValue.select())`

Khi người dùng bấm ô copy tay, chọn lại toàn bộ URL để tiện sao chép. Không tạo request mạng, không tạo liên kết mới và không tự ghi clipboard.

### Vì sao không có hàm riêng cho “Mở liên kết”?

Đây là thẻ `<a>` có `href` được gán sau thành công. Trình duyệt xử lý mở URL. `target='_blank'` mở tab/cửa sổ mới theo hành vi trình duyệt; `rel='noopener noreferrer'` hạn chế quan hệ với trang mở ra và không gửi referrer theo chính sách thuộc tính đó.

**Không cần viết `fetch(short_url)` để mở.** Trình duyệt sẽ gửi GET khi người dùng thật sự bấm liên kết; server trả redirect.

## 14. HTML, CSS và cấu hình không có hàm nghiệp vụ riêng

### `frontend/index.html`

- Cung cấp cấu trúc form, input, nút, vùng lỗi và vùng kết quả.
- Các ID phải khớp tên được tìm bằng `element()`.
- `novalidate` để ứng dụng chủ động trình bày thông báo kiểm tra thay vì để thông báo mặc định của form chặn submit trước.
- `hidden` làm kết quả/ô copy tay chưa xuất hiện lúc đầu.
- `role='alert'`, `role='status'`, `aria-live` hỗ trợ công bố lỗi và trạng thái.
- Tệp `main.ts` được nạp ở cuối body dưới dạng module; Vite xử lý khi phát triển và build.

### `frontend/src/style.css`

Không có hàm TypeScript/Python. CSS quy định bố cục, màu, focus, trạng thái disabled và xuống dòng cho URL dài. Media query `max-width:640px` đổi hàng input/nút thành cột trên màn hình hẹp. `[hidden]{display:none!important}` bảo đảm phần tử được JavaScript đánh dấu hidden thực sự ẩn. Animation bị bỏ theo `prefers-reduced-motion` để hỗ trợ người dùng cần giảm chuyển động.

### `frontend/vite.config.ts`

`defineConfig({...})` là hàm thư viện Vite dùng để mô tả cấu hình; dự án không tự định nghĩa hàm này. Cổng dev là 5173; `strictPort=true` để không âm thầm đổi cổng khi cổng đã bận. Proxy `/api` và `/r` tới FastAPI cổng 8000. `preview` ở 4173 chỉ là cấu hình xem bản build tĩnh, không phải server xử lý SQLite.

### `frontend/src/vite-env.d.ts`

Tham chiếu kiểu `vite/client`, giúp TypeScript hiểu các import tài nguyên như CSS. Không có mã nghiệp vụ được chạy trong trình duyệt từ tệp khai báo kiểu này.

### `frontend/tsconfig.json`, `frontend/package.json`, lockfile và requirements

`tsconfig` quy định TypeScript kiểm tra code như thế nào. Lệnh build chạy kiểm tra kiểu trước rồi để Vite sinh bundle; `noEmit` của tsc không có nghĩa Vite cũng không được tạo tệp.

`package.json` khai báo lệnh và phụ thuộc; lockfile cố định cây phụ thuộc npm. Requirements chốt thư viện Python. Đây là cấu hình/công cụ, không phải các hàm xử lý URL. Các thư mục `node_modules`, `.venv` và `frontend/dist` chứa thư viện hoặc kết quả sinh tự động; khi bảo vệ code nên tập trung các tệp source của dự án.

## 15. Script PowerShell

### `Invoke-Checked(Executable, Arguments)` — `scripts/setup.ps1`

Nhận tên/đường dẫn chương trình và mảng tham số. `& $Executable @Arguments` gọi chương trình, trong đó `@Arguments` truyền từng phần tử làm một đối số thay vì nối chúng thành một chuỗi lệnh.

Sau khi chương trình kết thúc, kiểm tra `$LASTEXITCODE`; khác 0 thì `throw` để không tiếp tục các bước sau như thể cài đặt thành công. `$ErrorActionPreference='Stop'` hỗ trợ dừng với lỗi PowerShell; hàm còn kiểm tra riêng mã thoát của chương trình ngoài.

**Phần script bao quanh:** tìm gốc từ `$PSScriptRoot`, chuyển vào dự án, tạo venv nếu chưa có, cài requirements, chỉ tạo `.env` nếu chưa tồn tại, dùng `npm ci`/build và chạy test nếu không đặt `-SkipTests`. Các cặp Push-Location/Pop-Location trong finally trả terminal về thư mục trước dù có lỗi.

**Giới hạn hiện tại:** việc kiểm tra `.venv` chỉ xét tệp `python.exe` tồn tại. Một venv có tệp launcher nhưng Python gốc đã bị gỡ vẫn có thể hỏng; trường hợp đó cần khôi phục/tạo lại venv. Không nói script tự phát hiện mọi kiểu hỏng môi trường.

### Phần chạy chính của `scripts/start.ps1`

Tệp này không khai báo hàm riêng. Nó nhận địa chỉ bind, port và cờ reload; kiểm tra đã có venv và `dist/index.html`, dựng mảng tham số cho Uvicorn rồi gọi chương trình Python. Nếu bật reload thì thêm `--reload`. `ValidateRange` giới hạn port từ 1 đến 65535.

`BindAddress` quyết định server lắng nghe ở đâu. `PUBLIC_BASE_URL` quyết định URL được ghi vào kết quả API. Hai thứ không tự đổi theo nhau: chạy port 8001 vẫn cần cấu hình public URL tương ứng nếu muốn URL ngắn trả về dùng cổng 8001.

**Câu trả lời miệng:** “Setup chuẩn bị môi trường; start khởi động Uvicorn. Lúc báo cáo em chạy một tiến trình không reload để cache và bộ đếm không tự reset khi sửa tệp.”

## 16. Script kiểm tra HTTP — `scripts/demo_check.py`

Đây là công cụ gọi server đang chạy thật, khác TestClient trong pytest. Nó tạo/dùng lại các liên kết mẫu và clear/reset trạng thái demo của server, nên chạy riêng khi tập thuyết trình để các request khác không làm lệch số đếm.

### `NoRedirect.redirect_request(self, req, fp, code, msg, headers, newurl)`

Ghi đè hành vi redirect của `urllib.request.HTTPRedirectHandler`. Trả `None` để không tạo request đi theo URL mới. Nhờ vậy script quan sát response 301/302 ban đầu thay vì chỉ thấy 200 của trang đích.

Các tham số theo giao diện thư viện; hàm không cần dùng giá trị của chúng để thực hiện chính sách “không theo redirect”. Đây không phải route redirect của server.

### `main()`

1. Đặt stdout UTF-8 để in được tiếng Việt trong console/pipe Windows.
2. Đọc tham số `--base-url`, `--target`, `--follow` bằng argparse.
3. Tạo HTTP opener không dùng proxy hệ thống cho các request này và có `NoRedirect`.
4. Gọi helper `request()` để kiểm tra health, tạo URL 302 rồi gửi lại và xác nhận `reused`.
5. Clear cache, reset counters và GET URL ngắn hai lần: phải MISS rồi HIT.
6. Đọc metadata/stats; assert số đếm chính xác.
7. Tạo URL 301 riêng, kiểm tra status, Location và Cache-Control; kiểm tra thêm 404 và 422.
8. Nếu có `--follow`, dùng opener khác không chặn redirect để theo liên kết thật, đòi response 200 và nội dung trang offline.
9. In PASS nếu tất cả assert thành công.

**Lưu ý:** các `assert` ở đây phục vụ kiểm chứng demo, không phải bảo vệ dữ liệu nghiệp vụ. Script cần backend đang chạy; `--follow` với target mặc định cần cả server offline cổng 9000. Nếu thay target tùy ý thì kiểm tra nội dung offline trong nhánh `--follow` cũng phải phù hợp target đó.

### Hàm lồng `request(method, path, body=None)`

Nằm trong `main()` và dùng chung `args`/`opener` qua closure.

- Có body: serialize JSON và encode thành bytes.
- POST không có body: dùng `b''` để gửi body rỗng.
- Dựng `Request` từ base URL và path, với method/header.
- Gửi với timeout 10 giây cho thao tác mạng.
- Nếu thư viện phát sinh `HTTPError` cho một status cần kiểm tra như 302/404, giữ nó như đối tượng response để vẫn đọc được code, header và body.
- Dùng `with response` để đóng tài nguyên sau khi đọc.
- Nếu Content-Type là JSON thì parse JSON; nếu không thì decode thành text.
- Trả bộ ba `(response.status, response.headers, parsed)`.

Lỗi mạng như không kết nối được server không được helper đổi thành response giả. Khi đó script dừng với lỗi để người demo biết phải khởi động server/kiểm tra địa chỉ.

### `if __name__ == '__main__': main()`

Chỉ gọi main khi chạy tệp trực tiếp. Nếu import lớp `NoRedirect` từ tệp này để dùng nơi khác thì không tự chạy cả kịch bản demo.

### Script trong `docs/offline/index.html`

Chỉ lấy `window.location.href` và gán vào `textContent` của phần tử địa chỉ. Đây là đoạn chạy trực tiếp, không phải hàm do dự án đặt tên. Nó cho thấy trình duyệt đã đến URL đích, gồm cả fragment; fragment là phần trình duyệt dùng, thường không được gửi trong HTTP request tới server đích.

## 17. Các hàm kiểm thử backend

Hàm kiểm thử không tham gia xử lý request của người dùng thật. Chúng chạy khi gọi pytest và dùng DB/cache riêng. Một hàm có `parametrize` được chạy với nhiều bộ dữ liệu, nên 58 test case không đồng nghĩa có 58 hàm `def test_...` khác nhau.

### `backend/tests/conftest.py`: ba fixture

| Hàm | Đầu vào, công việc và lý do |
| --- | --- |
| `settings(tmp_path)` | Tạo Settings với DB trong thư mục tạm, demo bật và dist tạm. Không ghi DB thật của dự án. `tmp_path` do pytest cấp. |
| `application(settings)` | Tạo instance app mới từ cấu hình fixture. Mỗi test có service/cache độc lập. |
| `client(application)` | Mở TestClient trong `with` để chạy vòng đời app; `yield` cho test sử dụng; thoát để kết thúc vòng đời. `follow_redirects=False` giữ response 301/302 cho test đọc. |

### `backend/tests/test_base62.py`

| Hàm | Kiểm chứng |
| --- | --- |
| `test_known_encoding(number, expected)` | So sánh encode với bảy ví dụ, gồm hai số trong sách, số 0 và biên `62^7`; phát hiện đổi bảng chữ cái hoặc cắt mã. |
| `test_reject_invalid_number(value)` | Dùng `pytest.raises(ValueError)` để đòi hàm từ chối số âm, bool, số thực và chuỗi. |

### `backend/tests/test_cache.py`

| Hàm | Công việc và kết quả mong đợi |
| --- | --- |
| `link(code)` | Helper tạo một Link giả từ code; thay cho DB lookup thật để tập trung kiểm tra cache. |
| `test_ttl_expires_without_refresh_on_hit()` | Truyền `clock=lambda: time[0]`, tự đổi thời gian 0 → 9 → 10; kiểm tra HIT ở 9 không làm TTL 10 gia hạn. Lambda chỉ trả thời gian giả hiện tại. |
| `test_lru_evicts_least_recently_used()` | Cache sức chứa 2; truy cập a, b, a, c; xác nhận a còn và b đã bị loại. |
| `test_clear_and_reset_are_independent()` | Reset phải giữ cache; clear phải giữ số đếm đã có. |
| `test_concurrent_redirects_count_exactly_once()` | Gửi 50 lần resolve bằng tối đa 8 worker. Với lookup thành công, chỉ một lần MISS/DB lookup và 49 HIT; phát hiện thiếu khóa hoặc đếm lệch. Lambda trong pool gọi resolve cho cùng mã a. |

### `backend/tests/test_config.py`

| Hàm | Kiểm chứng |
| --- | --- |
| `test_paths_are_anchored_to_project(monkeypatch, tmp_path)` | Đổi thư mục làm việc tạm rồi tạo Settings; đường dẫn DB vẫn theo gốc dự án. `monkeypatch` phục hồi thay đổi sau test. |
| `test_invalid_public_base_url(base)` | Từ chối scheme sai, host thiếu, path/query và port sai. |
| `test_invalid_cache_settings(kwargs)` | Từ chối sức chứa 0, TTL 0 và TTL NaN. |

### `backend/tests/test_api.py`

| Hàm | Công việc và lý do |
| --- | --- |
| `create(client, url=..., mode=302)` | Helper gửi POST tạo URL, giảm lặp mã trong các test; không phải hàm service sản phẩm. |
| `test_health_and_openapi(client)` | Health trả OK, `/docs` có thể mở và OpenAPI có đường dẫn tạo URL. |
| `test_create_and_reuse_preserves_url(client, url)` | Kiểm tra trim, URL ngắn đúng origin, thời gian UTC, lần đầu 201/lần sau 200, dữ liệu giữ nguyên và Location đúng URL. |
| `test_default_mode_and_separate_modes(client)` | Bỏ mode phải là 302; cùng URL mode 301 tạo mã khác; status và Cache-Control của mỗi loại chính xác. |
| `test_invalid_urls(client, url)` | Nhiều chuỗi sai phải trả 422 và thông báo dạng chuỗi, không trở thành liên kết hợp lệ. |
| `test_invalid_payloads(client, payload)` | Từ chối thiếu trường, kiểu sai, mode sai và trường thừa; gồm `301.0`, `True`, `"301"`. |
| `test_redirect_miss_hit_and_metadata_do_not_count(client)` | Tạo không nạp cache; metadata/stats không tăng redirect; hai lượt mở là MISS/HIT; reset và clear độc lập. |
| `test_not_found_and_case_sensitivity(client, application)` | Tạo đến ID 36 để so mã `a` của 10 với `A` của 36; kiểm tra 404 và bộ đếm not_found, metadata sai không tăng redirect. |
| `test_concurrent_duplicate_creation(client, settings)` | 16 yêu cầu qua tối đa 8 thread chỉ có một 201, các lần còn lại 200; DB có đúng một dòng và không có mã NULL đã commit. Lambda gửi cùng request qua helper create. |
| `test_restart_keeps_database_but_resets_ram(settings)` | Tạo hai app kế tiếp cùng tệp DB: app mới cache/counters 0, liên kết cũ còn và request đầu MISS. Đây là mô phỏng vòng đời mới bằng TestClient; kiểm tra restart tiến trình thật được ghi trong tài liệu nghiệm thu riêng. |
| `test_demo_disabled(settings)` | Dùng `replace(settings, demo_mode=False)` để tạo cấu hình mới; ba API demo 404 nhưng API tạo liên kết vẫn dùng được. |
| `test_static_frontend_served_without_hiding_api(settings)` | Tạo dist giả ở thư mục tạm; index/asset/API trả đúng, URL API không tồn tại vẫn 404. Không khởi động trình duyệt trong test này. |
| `test_missing_frontend_has_actionable_error(client)` | Khi chưa có dist, trang chủ trả 503 thay vì giả vờ có giao diện. |
| `test_database_failure_has_no_internal_details(client, application, monkeypatch)` | Thay tạm hàm DB bằng hàm gây lỗi; API phải trả 503 và không lộ chuỗi lỗi nội bộ. |
| Hàm lồng `fail(*args)` | Hàm giả cố ý ném `sqlite3.OperationalError`; chỉ phục vụ test lỗi DB và được monkeypatch khôi phục sau test. `*args` nhận các đối số mà hàm gốc thường nhận. |

## 18. Các hàm kiểm thử frontend — `frontend/tests/form.test.ts`

Vitest chạy trong happy-dom, tức môi trường DOM mô phỏng. Fetch và clipboard được mock để ép cả thành công và lỗi một cách lặp lại được. Các test này kiểm tra hành vi, không chứng minh bố cục đẹp trên mọi thiết bị hay quyền clipboard thật của mọi trình duyệt.

### Các hàm helper

| Hàm | Vai trò |
| --- | --- |
| `el<T>(id)` | Lấy phần tử trong DOM fixture với kiểu T; bản test giả định ID tồn tại, không có nhánh báo thiếu như `element()` của source. |
| `submit(url=sample.long_url)` | Đặt input rồi phát sự kiện submit có thể cancel; mô phỏng logic gửi form, không thực hiện một click vật lý bằng chuột. |
| `respond(payload=sample, status=201)` | Tạo đối tượng Response JSON giả để fetchMock trả cho source. Không gọi server thật. |
| `successfulSubmit()` | Gọi submit rồi dùng `vi.waitFor()` đợi vùng kết quả hiện, giúp các test sau đọc trạng thái sau xử lý async. Callback trong waitFor là điều kiện cần thỏa, không phải chờ cứng một số giây. |

### Callback `beforeEach(async () => ...)`

Trước mỗi case: reset cache module để code khởi tạo lại, reset mock fetch/clipboard, đặt kết quả mặc định thành công, dựng DOM từ HTML thật và import lại `main.ts` để đăng ký các listener mới.

`document.body.innerHTML = ...` ở đây chỉ dựng fixture HTML của dự án. Nó khác việc ứng dụng dùng dữ liệu URL do người dùng nhập để tạo innerHTML. Việc bỏ thẻ script khỏi fixture tránh dựa vào cơ chế tự chạy script của môi trường HTML; test chủ động import module.

### Callback `afterEach(() => ...)`

Trả timer về thật và bỏ các global đã stub. Nhờ đó test timeout dùng đồng hồ giả không làm ảnh hưởng test tiếp theo.

### Callback `describe('Form rút gọn', () => ...)`

Nhóm các test dưới một tên dễ đọc. `describe` không phải chức năng người dùng sử dụng; các callback `it(...)` bên trong mới mô tả từng tình huống kiểm chứng.

### Mười hai callback `it(...)`

| Tình huống | Cách ép điều kiện và điều cần đúng |
| --- | --- |
| Báo lỗi trước khi gọi server và cho sửa/gửi lại | Submit FTP, đòi không gọi fetch, có aria-invalid; phát input để sửa lỗi rồi gửi URL hợp lệ. |
| Giới hạn độ dài | Gửi URL vượt 4096, không được gọi server và phải báo giới hạn. |
| Gửi đúng API và không tự truy cập mã ngắn | Kiểm tra fetch chỉ một lần, đúng path, body mode 302 và link mở đúng URL. |
| Khóa gửi lặp/mở khóa | Fetch trả Promise chưa hoàn tất; submit hai lần chỉ gọi một request; sau khi chủ động resolve Promise thì form được mở. Callback executor của Promise giữ hàm `resolve` để test tự quyết định lúc server giả phản hồi. |
| Hiển thị ký tự HTML an toàn | Response giả chứa chuỗi dạng thẻ img; phải hiện đúng text và không tạo phần tử img trong vùng URL gốc. |
| Thông báo tái sử dụng | Response có reused=true, thông báo phải nói liên kết đã có. |
| Sao chép thành công | Click nút, xác nhận writeText nhận đúng URL và status báo thành công. |
| Clipboard bị chặn | Mock writeText reject; ô copy tay hiện, được focus và chọn toàn bộ nội dung. |
| Mất kết nối/cho gửi lại | Fetch lỗi một lần; input được giữ, kết quả ẩn và lần gửi sau thành công. |
| Lỗi 422 cạnh ô nhập | Response giả 422; thông báo đúng vị trí, không lộ thông báo parser nội bộ. |
| Không lộ lỗi 500 | Response chứa chuỗi stack trace giả; UI chỉ hiện thông báo chung. |
| Timeout 10 giây | Dùng fake timers; callback fetch mock đăng ký sự kiện abort, callback abort reject Promise bằng AbortError. Tiến đồng hồ 10 giây phải hiện lỗi và mở lại nút. Đây là mô phỏng hủy fetch, không đợi mạng thật 10 giây. |

## 19. Những câu thường bị hỏi và câu trả lời ngắn

**1. Đoạn nào thực sự rút gọn URL?**

`Database.create_or_get()` phối hợp việc cấp ID và lưu dữ liệu; `encode()` đổi ID thành mã. Không có một hàm nén toàn bộ URL thành chuỗi ngắn.

**2. Frontend có tự sinh mã được không?**

Không trong thiết kế này. ID phải do backend/DB kiểm soát để tránh việc nhiều trình duyệt tự chọn trùng số.

**3. Vì sao chia thành route, service và database?**

Route lo giao tiếp HTTP, service phối hợp nghiệp vụ, Database lo SQL. Cách tách này giúp đọc code, kiểm thử và thay nơi lưu trữ dễ hơn. Nó là cách tổ chức của dự án, không phải bắt buộc phải có nhiều server.

**4. Khóa cache và transaction DB khác nhau ở đâu?**

`RLock` bảo vệ RAM giữa các thread trong một process. Transaction và khóa SQLite bảo vệ thao tác trên DB. RLock không đồng bộ cache giữa hai process; SQLite không tự đồng bộ các dictionary RAM của ứng dụng.

**5. Vì sao tạo xong lần mở đầu tiên vẫn MISS?**

Tạo liên kết chỉ ghi DB. Cache được nạp khi có request redirect, giúp quan sát rõ luồng cache-aside trong demo.

**6. Cache mất có làm hỏng liên kết không?**

Không nếu DB còn nguyên. Request MISS sẽ tìm DB và nạp lại. DB mất hoặc không truy cập được là vấn đề khác; cache không thay thế sao lưu DB.

**7. Gửi hai URL có nội dung tương tự có dùng lại không?**

Chỉ so khớp chuỗi URL sau trim và mode. Không tự gộp `https://example.com` với `https://example.com/`, không sắp xếp query hoặc bỏ fragment.

**8. HTTP và HTTPS có được coi là cùng URL không?**

Không, hai chuỗi khác nhau và có thể trỏ tới hành vi khác nhau.

**9. Tại sao 301/302 có hai mã riêng?**

Đây là phần bổ sung phục vụ demo hai chính sách redirect ổn định. Cùng URL + cùng mode dùng lại, đổi mode tạo cặp khác.

**10. Base62 có bảo mật URL gốc không?**

Không. Người biết URL ngắn có thể mở nó; ID tuần tự còn khiến mã có thể đoán. Không dùng mã ngắn như cơ chế phân quyền.

**11. `reused` là dữ liệu lưu trong DB không?**

Không. Đó là thông tin được tính khi phục vụ request, cho biết kết quả của lần tạo/dùng lại; metadata luôn gán true để biểu thị bản ghi đã tồn tại.

**12. Cache TTL và cache 301 có cùng một đồng hồ không?**

Không. TTL thuộc ứng dụng và dùng monotonic của server. Max-age của phản hồi HTTP được client/cache HTTP xử lý. Dù hai mặc định cùng 300 giây, chúng độc lập.

**13. Server có biết mọi lượt người dùng bấm liên kết không?**

Không. Stats đếm GET redirect mà server nhận được, gồm cả request có thể do công cụ tạo; phản hồi được trình duyệt cache có thể không quay lại server. Đây không phải hệ thống analytics hoàn chỉnh.

**14. URL gốc có dấu `#` thì server đích nhận dấu `#` không?**

Ứng dụng giữ fragment trong ánh xạ và Location. Trình duyệt dùng fragment ở phía client, thường không gửi phần đó tới server đích trong HTTP request.

**15. URL ngắn có luôn ngắn hơn URL gốc không?**

Không tuyệt đối. Với URL gốc rất ngắn và origin local có port, địa chỉ kết quả có thể dài hơn. Lợi ích rõ nhất với URL dài hoặc khi dùng tên miền ngắn. Thiết kế dùng một ánh xạ ngắn để chia sẻ, không chứng minh mọi chuỗi đều giảm ký tự.

**16. Tắt DEMO_MODE có tắt rút gọn URL không?**

Không. Chỉ ba API demo bị chặn; API tạo, metadata và redirect vẫn hoạt động.

**17. Nếu DB chậm, cache này có còn phục vụ tốt không?**

HIT tránh truy vấn, nhưng code đang giữ một khóa chung cả trong lookup MISS. Request khác có thể phải chờ. Đây là lựa chọn đơn giản của demo, chưa phải cache tối ưu cho tải cao.

**18. Dùng nhiều worker thì sao?**

Mỗi process có một service, cache và bộ đếm riêng; khi đó không thể đọc một stats rồi coi là tổng toàn dịch vụ. Cần cache/counters dùng chung và đánh giá lại lưu trữ khi mở rộng.

**19. Hàm nào bảo đảm dữ liệu còn sau khởi động lại?**

Không phải một hàm RAM đặc biệt. `create_or_get()` commit dữ liệu vào tệp SQLite; instance mới mở lại cùng đường dẫn đó. Cache/counters được tạo mới nên không tồn tại qua restart.

**20. Tại sao GET metadata không dùng cache?**

Để xem dữ liệu mà không can thiệp thí nghiệm redirect. Điều đó làm metadata đọc DB; chức năng này không được dùng để đánh giá hit rate của cache redirect.

**21. Dữ liệu nhập ở frontend đã kiểm tra rồi, SQL tham số còn cần không?**

Cần. Client có thể bị bỏ qua, và dữ liệu hợp lệ cũng có thể chứa ký tự như dấu nháy. SQL có tham số tách câu lệnh khỏi dữ liệu.

**22. Dùng assert để kiểm tra request được không?**

Không nên. Request được kiểm tra bằng Pydantic và validator. `assert` nội bộ/test phục vụ phát hiện giả định sai, có thể bị bỏ khi Python chạy tối ưu.

## 20. Học theo thứ tự nào trước buổi demo?

1. Thuộc ba luồng ở mục 1: khởi động, tạo URL và mở URL.
2. Nắm kỹ `encode()`, `create_or_get()`, `resolve()` và `routes.redirect()`.
3. Nắm `valid_url()`, `LinkService.response()` và callback submit để giải thích dữ liệu đi qua các lớp.
4. Phân biệt clear/reset/restart; biết kiểm tra `Location`, status và `X-Cache`.
5. Đọc các hàm còn lại để biết chúng phục vụ cấu hình, phục vụ file, xử lý lỗi hay kiểm thử.

Khi bị chỉ vào một hàm bất kỳ, trả lời theo khuôn: **Hàm được ai gọi → nhận dữ liệu gì → làm những bước nào → trả gì/thay đổi gì → vì sao cần nó.** Không cần đọc lại từng ký tự code cho người hỏi.

Ví dụ trả lời trọn ý cho `resolve()`:

> Hàm này được route redirect gọi với mã ngắn và hàm tìm DB. Nó lấy khóa, đếm request và dọn cache hết hạn. Nếu có mục còn hạn thì trả Link kèm HIT. Nếu chưa có thì đếm MISS, tìm DB, nạp cache và loại mục LRU nếu đầy. Không tìm thấy thì trả None để route báo 404. Mục đích là giảm việc đọc DB lặp lại và cung cấp số liệu quan sát trong demo.

## 21. Nguồn kỹ thuật để tra cứu thêm

Code dự án là căn cứ chính của tài liệu. Các đặc điểm framework và thư viện có thể tra tại nguồn chính thức:

- [Python sqlite3: context manager của connection](https://docs.python.org/3.12/library/sqlite3.html#how-to-use-the-connection-context-manager): phân biệt commit/rollback với đóng connection trong mục 6.
- [FastAPI dependencies](https://fastapi.tiangolo.com/tutorial/dependencies/): cơ chế `Depends` trong mục 9.
- [FastAPI lifespan](https://fastapi.tiangolo.com/advanced/events/): phần trước/sau yield trong mục 10.
- [FastAPI async và hàm đồng bộ](https://fastapi.tiangolo.com/async/#path-operation-functions): cơ chế gọi path operation `def` trong mục 10.
- [MDN Fetch API](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch): kiểm tra response và hủy request trong mục 12.
- [MDN Cache-Control](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Cache-Control): phân biệt no-cache/no-store và max-age.

## 22. Ghi nhận môi trường ngày 16/11/2026

Trong lúc đối chiếu code, launcher của `.venv` còn trỏ tới Python gốc trong phiên bản runtime cũ đã không còn tồn tại. Đã dùng Python 3.12.14 của runtime hiện có để nâng cấp lại venv tại chỗ, giữ các package đã cài, source và DB. `pip check` không có xung đột; chạy lại **58 test backend và 12 test frontend đều đạt**. File cấu hình venv trước khi sửa được lưu trong `.runtime/pyvenv-before-2026-11-16.cfg`.

Việc sửa này khôi phục môi trường hiện tại, không bảo đảm một bản venv dựa vào runtime ứng dụng sẽ dùng mãi sau mọi lần cập nhật/gỡ runtime. Trước buổi demo nên kiểm tra lệnh Python; dùng Python hệ thống cài ổn định và tạo lại venv khi cần theo README. Không có thay đổi thuật toán hoặc nghiệp vụ sản phẩm trong lần viết tài liệu này.
