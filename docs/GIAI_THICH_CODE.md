

## 1. Ba module và luồng chạy

- **app**: Activity, ViewModel, repository HTTP và giao diện Compose.
- **server**: tiếp nhận HTTP, kiểm tra request, SQLite JDBC, Base62, cache và CLI demo.
- **shared**: model JSON và kiểm tra URL dùng chung trên Android/JVM.

~~~text
Người dùng nhập
→ ShortenerScreen gọi updateInput/shorten
→ ShortenerViewModel kiểm tra dữ liệu và trạng thái
→ HttpShortenerRepository POST JSON
→ Ktor shortenerModule
→ LinkService.create
→ Database.createOrGet
→ ID → Base62.encode → UPDATE mã → commit
→ ShortLink JSON về app
→ StateFlow đổi → Compose vẽ lại kết quả
~~~

Khi mở URL ngắn:

~~~text
MainActivity gửi ACTION_VIEW cho trình duyệt
→ GET /r/{code} trên Ktor
→ RedirectCache.resolve
→ HIT: lấy RAM; MISS: Database.find rồi nạp RAM
→ HTTP 301/302 + Location
→ Trình duyệt yêu cầu URL đích
~~~

Giao diện native không tự GET URL ngắn để tạo preview, nên chỉ tạo mã không làm tăng bộ đếm redirect. Không dùng WebView.

## 3. shared: model và URL validator

### Các data class trong Models.kt

- **CreateLinkRequest**: longUrl và redirectStatus mặc định 302; @SerialName ánh xạ sang long_url/redirect_status để giữ hợp đồng API.
- **ShortLink**: ID kiểu Long, mã, URL ngắn đầy đủ, URL gốc, mode, thời gian và reused. Server lẫn app dùng cùng định nghĩa, tránh khai báo hai model lệch nhau.
- **DemoStats**: counters, cacheSize và hitRate. Chỉ mô tả dữ liệu, không tự tăng số đếm.
- **ApiMessage**: thông báo lỗi ở trường detail, không chứa stack trace.
- **HealthResponse**: trường status mặc định ok.

Data class không phải bảng DB/ORM. SQLite được truy vấn trực tiếp bằng JDBC. Khác interface TypeScript cũ, serializer Kotlin thực sự parse JSON lúc chạy.

### UrlValidator.validate(raw)

Nhận chuỗi do người dùng nhập, trả URL sau trim hoặc ném IllegalArgumentException.

1. Bỏ khoảng trắng hai đầu.
2. Từ chối rỗng hoặc quá 4096 ký tự.
3. Từ chối khoảng trắng bên trong, ký tự điều khiển, DEL và backslash.
4. Dùng URI tách địa chỉ; chỉ HTTP/HTTPS và phải có authority.
5. Kiểm tra host/port bằng URL, kiểm tra tên miền qua IDN hoặc host IPv6.
6. Trả chuỗi đã trim, giữ nguyên path/query/fragment và các ký tự percent-encoded đã có.

Không tải website để kiểm tra nó có đang sống hay không. Frontend và server đều gọi cùng hàm: app báo lỗi sớm; server vẫn tự bảo vệ khi request đến trực tiếp từ công cụ HTTP.

**Trả lời miệng:** “Hàm nhận URL HTTP/HTTPS hợp lệ về cấu trúc, giữ đúng chuỗi địa chỉ sau trim và không cần truy cập trang đích.”

## 4. app: MainActivity.onCreate

onCreate chạy khi Activity được tạo. Nó bật edge-to-edge, mở SharedPreferences giữ địa chỉ server và gọi setContent để vẽ UI.

Bên trong setContent:

- LienKetTheme đặt màu và MaterialTheme.
- viewModelFactory/initializer tạo ViewModel với SavedStateHandle, địa chỉ server đã lưu và callback ghi lại địa chỉ.
- collectAsStateWithLifecycle quan sát StateFlow khi phù hợp vòng đời.
- ShortenerScreen nhận trạng thái và các callback; UI không tự biết cách ghi SQLite.

**Callback onCopy:** lấy ShortLink hiện tại; ClipboardManager.setPrimaryClip ghi toàn bộ shortUrl vào clipboard. Thành công báo copySucceeded, lỗi báo copyFailed. Không sao chép mỗi mã ngắn và không đọc lịch sử clipboard.

**Callback onOpen:** tạo Intent.ACTION_VIEW với Uri của URL ngắn rồi startActivity. Android chọn trình duyệt phù hợp; lỗi thì báo openFailed. Đây là mở trình duyệt ngoài app, không phải WebView và không phải app tự theo redirect.

**Callback persistServerUrl:** ghi địa chỉ vào SharedPreferences để lần mở app sau dùng lại. Lưu cấu hình này không lưu toàn bộ SQLite trên điện thoại.

## 5. app: trạng thái và ViewModel

### ShortenerUiState

Giữ input, isLoading, result, error, message, copied, manualCopy và serverUrl. UI được vẽ từ trạng thái này. result=null thì vùng kết quả không hiện.

ViewModel có MutableStateFlow riêng; công bố asStateFlow để bên ngoài chỉ quan sát. Khởi tạo phục hồi input, JSON của result và serverUrl từ SavedStateHandle khi có.

### updateInput(value)

Nếu đang gửi thì bỏ qua để URL trong request không bị đổi giữa chừng. Nếu được sửa, ghi SavedStateHandle và cập nhật input, xóa lỗi ô nhập. Không gọi API.

### shorten()

Hàm trung tâm của app:

1. Nếu isLoading đang true thì return, tránh gửi lặp.
2. Gọi UrlValidator.validate. Sai thì xóa kết quả cũ, hiện lỗi cạnh input, giữ nội dung người dùng.
3. Ghi nhận địa chỉ server và đặt trạng thái đang gửi trước khi launch coroutine.
4. viewModelScope.launch gọi repository.create.
5. Thành công: lưu result dưới dạng JSON vào SavedStateHandle; cập nhật message theo reused.
6. ApiException 422 hiện lỗi nhập; lỗi khác hiện message chung.
7. CancellationException được ném tiếp, không biến việc hủy do vòng đời thành lỗi mạng giả.
8. finally luôn đặt isLoading=false.

Đặt loading trước launch giúp hai lần bấm nhanh không tạo hai coroutine tạo liên kết. SavedStateHandle không phải DB server: nó giữ trạng thái giao diện, không phải ánh xạ chia sẻ cho mọi người.

### copySucceeded()

Đặt copied=true, ẩn manualCopy và thông báo “Đã sao chép liên kết”. Hàm này không trực tiếp ghi clipboard; MainActivity làm thao tác nền tảng rồi báo kết quả.

### copyFailed()

Hiện vùng SelectionContainer chứa URL để chọn/sao chép tay và thông báo phù hợp. Chỉ chọn văn bản ở UI, không thay quyền của clipboard.

### openFailed()

Thông báo chưa mở được trình duyệt và hướng dẫn dùng URL bằng tay. Không sửa hoặc xóa liên kết đã tạo.

### setServerUrl(raw)

Nếu đang gửi thì từ chối thay cấu hình. Gọi ServerAddress.normalize, lưu cấu hình qua callback và SavedStateHandle rồi cập nhật state. Trả null khi thành công, trả String lỗi để dialog tiếp tục hiển thị khi sai.

Địa chỉ này là nơi app gọi API. Nó khác PUBLIC_BASE_URL mà server dùng khi tạo URL ngắn; hai bên phải thống nhất địa chỉ truy cập được.

### onCleared()

Đóng repository nếu nó là Closeable rồi gọi super. Khi ViewModel thực sự bị xóa, client HTTP không bị giữ tài nguyên vô hạn. ViewModel tồn tại qua nhiều lần recomposition; không tạo client mới mỗi lần UI vẽ.

## 6. app: repository và lỗi HTTP

### Interface ShortenerRepository.create(serverUrl, longUrl)

Định nghĩa hành vi tạo URL dưới dạng suspend. ViewModel chỉ cần biết interface này. Kiểm thử có thể thay bằng repository giả để ép chậm/lỗi mà không cần mạng.

### ApiException(status, message)

Lỗi có status và thông báo cho UI. Status 0 là quy ước lỗi không nhận/đọc được response thích hợp, không phải mã HTTP do server trả.

### ServerAddress.normalize(raw)

Gọi validator chung, bỏ slash cuối và từ chối path/query/fragment/userInfo. Chỉ nhận origin, ví dụ http://127.0.0.1:8000 hoặc http://192.168.1.20:8000.

### HttpShortenerRepository: cấu hình client

HttpClient dùng engine OkHttp; ContentNegotiation dùng Kotlin JSON; request timeout 10 giây, connect timeout 5 giây, socket timeout 10 giây. followRedirects=false để API tạo không tự đi theo phản hồi 3xx bất thường.

### HttpShortenerRepository.create(serverUrl, longUrl)

POST tới origin + /api/v1/links; gửi CreateLinkRequest, content type JSON. Chỉ chấp nhận 200/201. 422 thành ApiException riêng cho ô nhập; status lỗi khác thành thông báo server chung.

Thành công đọc ShortLink bằng serializer và kiểm tra URL ngắn/mode. Không gọi URL ngắn để preview. CancellationException giữ nguyên; lỗi mạng/parse khác được đổi thành thông báo kết nối chung, không hiện nội dung nội bộ.

Timeout client không chứng minh server chưa commit. Nếu client thử lại cùng URL/mode, DB vẫn tái sử dụng bản ghi.

### HttpShortenerRepository.close()

Đóng HttpClient để giải phóng tài nguyên. Không xóa liên kết hoặc DB.

## 7. app: các hàm Compose

Compose mô tả giao diện từ state. Khi state thay đổi, Compose có thể gọi lại phần cần thiết; không đặt INSERT hoặc request mạng trực tiếp trong thân hàm vẽ UI.

| Hàm | Chức năng |
| --- | --- |
| LienKetTheme(content) | Đặt bảng màu sáng cố định theo UI cũ, giữ nền xanh nhạt/forest/olive, không tự đổi sang dynamic color. |
| ShortenerScreen(...) | Ghép header, intro, form, result, status, steps và footer; quản lý dialog kết nối, focus và vùng cuộn. |
| BrandHeader(onSettings) | Logo liên kết, tên thương hiệu, mô tả ngắn; dấu ba chấm mở cấu hình server. |
| Intro() | Dòng giới thiệu, tiêu đề hai màu và mô tả giữ theo UI đã chốt. |
| ResultContent(...) | Hiện URL ngắn, Sao chép, Mở liên kết, lựa chọn sao chép tay và URL gốc. |
| Steps() | Vẽ ba bước 01/02/03 và các đường nối. Không thực hiện rút gọn URL. |
| Footer() | Dòng chân trang và đường phân cách. |
| ServerDialog(...) | Cho sửa origin API, hiện lỗi cấu hình và gọi onSave; chỉ đóng khi lưu thành công hoặc chọn Đóng. |
| LinkGlyph(modifier, color) | Canvas vẽ biểu tượng liên kết bằng Path, cùng phong cách hình cũ; không xử lý URL. |
| CopyGlyph(modifier, color) | Canvas vẽ biểu tượng sao chép; hành vi sao chép do callback onCopy quyết định. |
| IdlePreview() | Preview trạng thái ban đầu trong Android Studio, không gọi backend. |
| ResultPreview() | Preview kết quả mẫu để kiểm tra bố cục; dữ liệu mẫu không tự ghi SQLite. |

### Callback và hiệu ứng trong ShortenerScreen

- **LaunchedEffect(state.error):** nếu có lỗi, đưa focus về input để sửa. Đây là hiệu ứng focus, không phải request HTTP.
- **onValueChange:** chuyển text mới tới updateInput.
- **onGo và onClick Rút gọn:** bỏ focus, gọi onShorten và yêu cầu ẩn bàn phím. Cùng một nghiệp vụ cho Enter/Go và nút.
- **AnimatedVisibility:** hiện vùng kết quả khi result khác null.
- **onCopy/onOpen:** chuyển hành động ra Activity/ViewModel; Composable không tự lưu DB.
- **onSettings/onDismiss:** đổi trạng thái dialog trong rememberSaveable.
- **onSave trong dialog:** gọi setServerUrl; kết quả String là lỗi, null là lưu thành công.

safeDrawingPadding tránh status/navigation bar; imePadding xử lý vùng bàn phím; verticalScroll hỗ trợ khi kết quả dài. Text hiển thị URL như chữ bình thường, không parse thành HTML. SelectionContainer cho chọn/copy text dài.

## 8. server: Settings

### Khối init của Settings

Kiểm tra PUBLIC_BASE_URL là HTTP/HTTPS có host, port hợp lệ, không path/query/fragment/credentials; cacheMaxItems > 0, TTL hữu hạn > 0; cổng server trong 1..65535. Lỗi cấu hình nên làm startup thất bại thay vì phục vụ URL sai.

### Settings.projectRoot()

Từ user.dir đi lên tới nơi có settings.gradle.kts. Vì vậy chạy task từ thư mục con vẫn có thể tìm .env theo gốc. Nếu không thấy marker, dùng thư mục hiện hành; khi phân phối server riêng cần cấu hình/vị trí chạy rõ ràng.

### Settings.fromEnvironment(root, environment)

Đọc tệp .env nếu có; bỏ dòng rỗng/comment, tách key=value và bỏ cặp dấu nháy bao quanh. Ghép fileValues + environment để biến môi trường thắng; kiểm tra mode, parse số và resolve đường dẫn DB tuyệt đối theo root.

Đây là parser .env đơn giản, không phải một shell: không hỗ trợ mọi loại nội suy/escape phức tạp. Khởi động lại để nạp thay đổi cấu hình.

## 9. server: Base62.encode(id)

Nhận Long không âm; 0 trả “0”. Lặp số % 62 lấy dư, tra ALPHABET, chia số cho 62; đảo StringBuilder ở cuối. Không đọc/ghi DB.

Ví dụ 11157: dư 59→X, dư 55→T, dư 2→2; đảo thành 2TX. Ví dụ 2009215674938 thành zn9edcu. Không cắt mã, không tự sinh random và không nhận URL làm đầu vào.

**Vì sao không va chạm?** Với ID duy nhất, cùng bảng chữ cái và giữ đủ kết quả, hai số khác nhau có biểu diễn khác nhau. **Vì sao không bảo mật?** ID tuần tự khiến mã có thể đoán; mã ngắn không phải mật khẩu.

## 10. server: Database

Link là bản ghi nội bộ. Creation gồm Link và reused. Constructor Database giữ path và nạp driver org.sqlite.JDBC; chưa tạo bảng.

### connect()

Tạo SQLiteConfig, đặt busy timeout 10 giây và transaction mode IMMEDIATE; mở JDBC connection tới tệp tuyệt đối. Các caller dùng use để luôn đóng connection. Không chia sẻ một connection giữa các request.

### initialize()

Tạo thư mục DB; bật WAL; tạo bảng links nếu chưa tồn tại. Schema giữ tương thích bản trước: ID tự tăng, short_code UNIQUE/COLLATE BINARY, URL và thời gian không null, mode 301/302 và UNIQUE(long_url, redirect_status).

short_code tạm NULL khi INSERT lấy ID, nhưng luồng tạo sẽ UPDATE mã trước commit. CREATE TABLE IF NOT EXISTS không tự migrate mọi kiểu schema cũ khác.

### createOrGet(longUrl, redirectStatus)

1. Mở connection và đặt autoCommit=false; với SQLiteConfig, transaction IMMEDIATE lấy quyền ghi trước SELECT.
2. SELECT cặp URL/mode bằng PreparedStatement.
3. Có bản ghi: commit để kết thúc transaction, trả Creation(existing,true).
4. Chưa có: INSERT URL/mode/thời gian UTC.
5. SELECT last_insert_rowid() trên chính connection đó, không dùng COUNT(*)+1.
6. Base62.encode(id), UPDATE mã và commit.
7. Nếu bất kỳ bước nào lỗi, cố rollback rồi ném lỗi tiếp; use đóng connection.

Hai request giống nhau: A lấy quyền ghi trước, B chờ; A commit rồi B SELECT sẽ thấy bản ghi. UNIQUE bảo vệ dữ liệu thêm. Nếu DB bận quá thời gian chờ vẫn có thể báo 503; không khẳng định mọi request đồng thời hoàn thành tức thì.

PreparedStatement và setString/setInt/setLong tách dữ liệu khỏi SQL, tránh ghép URL trực tiếp vào câu lệnh. Chỉ sau khi commit mới trả kết quả tạo mới.

### find(code)

SELECT có tham số theo short_code; trả Link hoặc null. Không tự gửi 404 và không tự tăng counters. Metadata và redirect dùng cùng hàm nhưng chỉ redirect gọi qua cache.

### healthy()

Chạy SELECT 1 FROM links LIMIT 1; không lỗi thì trả true, kể cả bảng rỗng. Không kiểm tra Internet/trang đích hoặc chứng minh ổ đĩa còn đủ cho mọi lần ghi tương lai.

### ResultSet.toLink()

Extension function lấy các cột theo tên và tạo Link. Nó chuyển kết quả query sang dữ liệu Kotlin, không chạy truy vấn mới.

## 11. server: RedirectCache

Constructor tạo LinkedHashMap với accessOrder=true (thứ tự truy cập), khóa lock và counters. Entry gồm Link + expires. clock mặc định dùng System.nanoTime tính thời gian trôi; test có thể truyền đồng hồ giả.

### purgeExpired()

Xóa mục expires <= clock hiện tại. Được gọi bên trong khóa bởi resolve/stats, không có tác vụ nền chạy liên tục. Quét K mục nên đoạn này O(K), không nói toàn bộ cache luôn O(1).

### resolve(code, lookup)

synchronized(lock) bảo vệ cả luồng:
- Tăng request và dọn mục hết hạn.
- Có code: tăng HIT; việc đọc LinkedHashMap cập nhật thứ tự access; trả Link to “HIT”.
- Không có: tăng MISS/lookup, gọi lookup(code).
- Không có bản ghi: tăng notFound, trả null to “MISS”, không cache âm.
- Có: nạp với expires = clock + TTL.
- Nếu quá sức chứa: bỏ key đầu (ít dùng gần đây nhất); trả Link to “MISS”.

lookup là hàm được truyền bằng database::find, chỉ gọi khi cần. HIT không gia hạn TTL. Khóa được giữ cả trong DB lookup: đơn giản và tránh nạp trùng nhưng có thể khiến request khác chờ; chưa tối ưu tải cao. Counters lookup tăng trước lần cố đọc DB nên cả lần truy vấn gặp lỗi cũng đã được tính.

### stats()

Lấy khóa, dọn hết hạn, trả DemoStats với cacheSize và hitRate = hits/(hits+misses), hoặc 0 nếu chưa tra. Không tăng request/hit/miss. Đọc stats có thể làm số mục RAM giảm do dọn hết hạn.

### clear()

Chỉ xóa items; giữ SQLite và counters. Lượt redirect hợp lệ tiếp theo sẽ MISS nếu chưa có request khác nạp trước.

### resetStats()

Chỉ đưa năm counters về 0; giữ cache. Vì vậy request đầu sau reset vẫn có thể HIT. Muốn thí nghiệm lạnh cần cả clear và reset, tránh request khác xen vào.

## 12. server: LinkService

Constructor tạo Database và RedirectCache theo Settings; cache gắn với service của một process, không tạo lại mỗi request.

### create(request)

Kiểm tra mode 301/302, kiểm tra URL bằng shared validator, gọi database.createOrGet rồi response. Không nạp cache và không quyết định HTTP 200/201.

### response(link, reused)

Tạo ShortLink và ghép PUBLIC_BASE_URL + /r/ + shortCode. Không lấy origin từ app/Host header tùy ý và không query thêm. Frontend có thể gọi API bằng địa chỉ khác origin chia sẻ, nên cần cấu hình hai bên đúng.

## 13. server: Application.kt

### main()

Đọc Settings, khởi động embeddedServer Netty đúng host/port, gắn shortenerModule và start(wait=true). Đây là server JVM chạy trên máy tính, không chạy bên trong Activity Android.

### Application.shortenerModule(settings, service)

Extension function lắp ứng dụng:
- initialize DB;
- ContentNegotiation serialize JSON;
- StatusPages đăng ký xử lý lỗi;
- routing đăng ký các callback HTTP.

Các thao tác JDBC/cache có DB lookup được đưa vào withContext(Dispatchers.IO) để không chạy trực tiếp trên coroutine phục vụ request cần tránh blocking.

### Các callback lỗi StatusPages

- IllegalArgumentException: 422/thông báo request chung.
- SQLException: ghi log, trả 503 chung.
- Throwable còn lại: CancellationException ném tiếp; lỗi khác ghi log, trả 500 chung.
- Không gửi stack trace/đường dẫn DB cho app.

### Các callback route

| Route | Việc thực hiện |
| --- | --- |
| GET / | Trả HealthResponse cho origin server; giao diện chính nằm trong app Android. |
| GET /api/v1/health | healthy() trên dispatcher IO rồi JSON ok. |
| POST /api/v1/links | Parse JsonElement, bắt buộc object và long_url là JSON string, mode nếu có phải integer thật; decode model, gọi service, chọn 200 hoặc 201 theo reused. |
| GET /api/v1/links/{code} | find trực tiếp DB, null→404; có→metadata/reused=true; không tăng redirect counters. |
| GET /r/{code} | resolve qua cache; set X-Cache; null→404/no-store; có→Cache-Control, Location và status 301/302 tường minh. |
| GET /api/v1/demo/stats | Tắt demo→404; bật→cache.stats. |
| POST /api/v1/demo/cache/clear | Tắt demo→404; bật→clear và stats. |
| POST /api/v1/demo/stats/reset | Tắt demo→404; bật→resetStats và stats. |
| GET /demo/target | Trang HTML đích do Kotlin phục vụ để demo không cần Internet. |
| GET /docs | Trang mô tả API do Kotlin phục vụ, không phải Swagger/FastAPI. |

Kotlin serializer có thể đọc một số số nguyên được ghi dưới dạng string, nên route kiểm tra JsonPrimitive.isString và content.toIntOrNull trước decode để giữ quy tắc API cũ. Trường lạ được serializer từ chối.

Redirect dùng URI.toASCIIString() để truyền URL qua Location. Header X-Cache nói về RAM server; Cache-Control nói về chính sách cache phản hồi HTTP. 302 gửi no-store; 301 gửi private,max-age=300. Trình duyệt tự theo Location; server không tải trang đích để hiển thị hộ.





## 17. Câu hỏi nhanh khi bảo vệ

**URL có được nén không?** Không, mã là đại diện cho một ánh xạ trong DB.

**Vì sao phải có server dù app đã viết Kotlin?** Một trình duyệt bất kỳ cần một nơi nhận HTTP để xử lý URL ngắn. DB nằm riêng trong điện thoại sẽ không tự trở thành dịch vụ chuyển hướng dùng chung.

**Copy ra trình duyệt có dùng được không?** Có, copy toàn URL; server đang chạy và địa chỉ truy cập được. Trong USB/emulator, adb reverse giúp cả trình duyệt thiết bị lẫn máy tính dùng 127.0.0.1:8000.

**Vì sao không chỉ viết ViewModel mà cần repository?** Tách mạng khỏi trạng thái màn hình, dễ ép tình huống chậm/lỗi trong kiểm thử.

**Compose có tự giữ mọi biến không?** Không. StateFlow/ViewModel giữ trạng thái nghiệp vụ UI; remember/rememberSaveable và SavedStateHandle giải quyết các phạm vi khác nhau.

**Có đủ 7 ký tự không?** Base62 tăng theo ID; không cắt hoặc đệm. Giới hạn 7 ký tự cần miền ID < 62^7.

**Có an toàn vì mã khó đoán không?** Không; ID tuần tự dễ đoán, mã không phải cơ chế phân quyền.

**Cache mất có mất link không?** Không nếu SQLite còn và dùng đúng đường dẫn. Clear cache khác reset counters và khác xóa DB.

**RLock Python cũ thay bằng gì?** synchronized(lock) bảo vệ RAM giữa các thread trong process. SQLite transaction bảo vệ dữ liệu DB, không tự đồng bộ dictionary RAM giữa nhiều process.

**Vì sao tạo không nạp cache?** Để redirect đầu tiên minh họa MISS/DB lookup; lần sau HIT.

**HIT có kéo dài TTL không?** Không, chỉ đổi thứ tự LRU.

**Tại sao metadata không dùng cache?** Để xem bản ghi mà không làm lệch thí nghiệm redirect.

**301 có luôn làm server không nhận request sau không?** Không tuyệt đối; xét chính sách HTTP/client. Counters chỉ biết request thực sự tới server.

**Ứng dụng đã phân tán chưa?** Chưa. Một Ktor process, một SQLite DB và cache RAM; load balancer/ID toàn cục là phần lý thuyết mở rộng.

Khi được chỉ vào một hàm, trả lời theo thứ tự: **ai gọi → đầu vào → các bước → kết quả/tác động dữ liệu → lý do cần**. Ưu tiên nắm encode, createOrGet, resolve, route redirect và ViewModel.shorten.
