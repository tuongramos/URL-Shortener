# Liên kết — URL Shortener Android / Kotlin

Ứng dụng Android rút gọn URL, viết bằng Kotlin và Jetpack Compose. Server đi kèm dùng Kotlin/Ktor và SQLite. Dự án gồm ba module: `app`, `server`, `shared`.

## Hướng dẫn dành cho người tải source về chấm bài

**Bản source có đầy đủ mã nguồn và Gradle Wrapper để import lại. Để sử dụng chức năng rút gọn URL, cần chạy cả server Ktor và app Android. Bấm Run app trong Android Studio không tự khởi động server.**

Các bước dưới đây dành cho Windows và PowerShell. Có thể giải nén dự án ở vị trí bất kỳ trên máy; không cần giữ đường dẫn của máy tác giả.

### 1. Chuẩn bị môi trường

- Android Studio tương thích Android Gradle Plugin **9.2.1**.
- **JDK 25** cho Gradle theo cấu hình `gradle/gradle-daemon-jvm.properties`. Java/Kotlin của dự án tạo bytecode JVM 17.
- Trong SDK Manager, cài **Android SDK Platform 37**, Android SDK Platform-Tools và các Build Tools mà Gradle yêu cầu.
- Một emulator hoặc điện thoại Android **8.0 / API 26 trở lên**. Khi dùng điện thoại thật, bật USB debugging và chấp nhận kết nối ADB trên điện thoại.
- Kết nối Internet trong lần đầu để tải Gradle, JDK nếu cần, SDK và thư viện. Không cần cài Python, Node.js, MySQL hoặc một server cơ sở dữ liệu riêng.

### 2. Giải nén và mở trong Android Studio

1. Giải nén toàn bộ file ZIP.
2. Trong Android Studio, chọn **File → Open**.
3. Chọn thư mục `URL Shortener` có chứa `settings.gradle.kts`, `gradlew.bat`, `app`, `server`, `shared`. Không chỉ mở riêng thư mục `app`.
4. Chờ Gradle Sync hoàn tất và cài các SDK được yêu cầu nếu có.
5. Trong phần cài đặt Gradle của Android Studio, dùng JDK phù hợp cấu hình trên. Nếu JDK đi kèm Android Studio là JDK 25 thì có thể dùng JDK đó.

### 3. Cài đặt và build lần đầu

Mở **Terminal** trong Android Studio, dùng PowerShell và đặt thư mục làm việc tại gốc dự án, nơi có `gradlew.bat`.

```powershell
.\scripts\setup.ps1
```

Script sẽ tạo `.env` từ `.env.example` nếu chưa có, build APK, build server và chạy các kiểm thử JVM. Không ghi đè `.env` đã tồn tại. Lần đầu có thể mất thời gian vì phải tải thư viện.

Các script tự tìm JDK đi kèm Android Studio ở vị trí cài mặc định nếu `JAVA_HOME` chưa được đặt. Nếu đang có `JAVA_HOME` trỏ tới Java cũ, hoặc cài Android Studio/SDK ở vị trí khác, cần đặt `JAVA_HOME` và `ANDROID_HOME` đúng trên máy đang chạy. Thiết lập JDK trong Android Studio và `JAVA_HOME` của Terminal có thể khác nhau.

### 4. Khởi động server

Trong Terminal thứ nhất, tại gốc dự án:

```powershell
.\scripts\start.ps1
```

Giữ Terminal này mở trong suốt lúc sử dụng app. Server mặc định chạy tại `http://127.0.0.1:8000`.

Mở trình duyệt trên máy tính vào `http://127.0.0.1:8000/docs` để kiểm tra server đã hoạt động.

### 5. Chạy ứng dụng Android

Khởi động một emulator từ **Device Manager** và chờ đến màn hình chính. Có thể thay emulator bằng điện thoại USB đã được ADB nhận diện.

Mở Terminal thứ hai ở gốc dự án rồi chạy:

```powershell
.\scripts\run-android.ps1
```

Script sẽ build APK, chạy `adb reverse tcp:8000 tcp:8000`, cài APK và mở ứng dụng. Nhờ ánh xạ cổng này, app truy cập được server đang chạy trên máy tính qua địa chỉ mặc định `http://127.0.0.1:8000`.

Nếu có nhiều thiết bị, chỉ định đúng serial của thiết bị:

```powershell
.\scripts\run-android.ps1 -Serial emulator-5554
```

Nếu muốn dùng nút **Run** của Android Studio: sau khi server đã chạy và đã thiết lập `adb reverse` cho thiết bị, chọn cấu hình `app`, chọn thiết bị rồi bấm Run. Cần thiết lập lại ánh xạ cổng sau khi khởi động lại emulator hoặc ngắt kết nối USB; chạy lại `run-android.ps1` là cách thực hiện nhanh.

### 6. Kiểm tra chức năng

1. Giữ server chạy và mở app.
2. Dán URL mẫu bên dưới vào ô nhập:

   ```text
   http://127.0.0.1:8000/demo/target?chapter=8#chapter-8
   ```

3. Bấm rút gọn để nhận URL ngắn.
4. Sao chép toàn bộ URL ngắn, rồi mở bằng nút mở liên kết hoặc dán vào trình duyệt.
5. Trang đích demo hiển thị thông báo chuyển hướng thành công.

Trang đích demo được server phục vụ ngay trên máy tính nên không cần Internet sau khi đã build và tải đủ phụ thuộc. Nếu trình duyệt trên emulator hỏi thiết lập lần đầu, hoàn tất thiết lập trước khi mở liên kết.

## Một số lỗi thường gặp

| Hiện tượng | Cách xử lý |
| --- | --- |
| Gradle Sync yêu cầu SDK hoặc JDK | Cài SDK Platform 37 và JDK phù hợp; dự án cấu hình Gradle daemon dùng JDK 25. Kiểm tra kết nối Internet khi tải phụ thuộc. |
| Terminal báo thiếu JDK hoặc dùng Java quá cũ | Đặt `JAVA_HOME` trỏ đến JDK phù hợp. Cài đặt Gradle JDK trong Android Studio không tự thay `JAVA_HOME` của Terminal. |
| Không tìm thấy Android SDK / adb | Cài Platform-Tools trong SDK Manager; đặt `ANDROID_HOME` đúng thư mục SDK nếu không dùng vị trí mặc định. |
| PowerShell không cho chạy file `.ps1` | Dùng các lệnh Gradle trực tiếp trong phần bên dưới, sau khi đặt JDK và SDK đúng. |
| App hiện giao diện nhưng báo chưa kết nối được máy chủ | Kiểm tra `start.ps1` vẫn đang chạy và mở được `/docs` trên máy tính; chạy lại `run-android.ps1` để thiết lập ánh xạ cổng. |
| Cổng 8000 đang được sử dụng | Dừng phiên server cũ hoặc ứng dụng đang chiếm cổng trước khi chạy lại. |
| Có nhiều thiết bị kết nối | Dùng tham số `-Serial` để chọn đúng emulator hoặc điện thoại. |

## Dùng Gradle trực tiếp khi cần

Các lệnh dưới đây chạy bằng PowerShell tại gốc dự án, với `JAVA_HOME` trỏ tới JDK phù hợp và SDK đã được Android Studio nhận diện.

Build APK, server và kiểm thử JVM:

```powershell
.\gradlew.bat :app:assembleDebug :server:installDist :shared:test :server:test :app:testDebugUnitTest
```

Tạo cấu hình demo nếu `.env` chưa tồn tại rồi chạy server:

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
.\gradlew.bat :server:run
```

Trong Terminal khác, với một thiết bị đã khởi động và kết nối, dùng ADB trong Android SDK Platform-Tools để ánh xạ cổng trước khi bấm Run app. Nếu SDK ở vị trí mặc định trên Windows:

```powershell
$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adbPath reverse tcp:8000 tcp:8000
```

Nếu SDK được cài ở nơi khác, sửa `$adbPath` theo đường dẫn thực tế trên máy đang chạy.

APK debug sau khi build nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

## Cấu hình và dữ liệu

`setup.ps1` tạo `.env` từ `.env.example`. Server đọc `.env` ở gốc dự án; biến môi trường được ưu tiên hơn giá trị trong file.

| Biến | Giá trị trong cấu hình mẫu | Vai trò |
| --- | --- | --- |
| `PUBLIC_BASE_URL` | `http://127.0.0.1:8000` | Origin dùng tạo URL ngắn, không có path/query/fragment |
| `DATABASE_PATH` | `server/data/shortener.db` | Đường dẫn SQLite tương đối theo gốc dự án |
| `CACHE_MAX_ITEMS` | `1000` | Số phần tử tối đa của cache LRU |
| `CACHE_TTL_SECONDS` | `300` | TTL tính từ thời điểm nạp cache |
| `DEMO_MODE` | `true` | Bật API thống kê/clear/reset để demo cục bộ |
| `SERVER_HOST` | `127.0.0.1` | Địa chỉ server lắng nghe |
| `SERVER_PORT` | `8000` | Cổng server |

Không cần mang theo database của máy tác giả. Khi chạy lần đầu, server tự tạo SQLite và bảng dữ liệu mới. Nếu dùng lại thư mục phát triển đã có `.env`, giữ nguyên `DATABASE_PATH` nếu muốn tiếp tục sử dụng các liên kết đã tạo trước đó; một số cấu hình cũ có thể trỏ tới `backend/data/shortener.db`.

Nếu cần kết nối điện thoại qua Wi-Fi LAN thay cho USB: đặt `PUBLIC_BASE_URL` thành địa chỉ IP LAN của máy tính, chạy `start.ps1 -BindAddress 0.0.0.0`, và nhập cùng địa chỉ đó trong phần **Kết nối máy chủ** của app. Máy tính và điện thoại cần truy cập được nhau. Cách demo mặc định dùng emulator/USB và `adb reverse`.

## Cấu trúc mã nguồn

```text
app/          Ứng dụng Android, Compose UI, ViewModel, HTTP repository
server/       Ktor API, SQLite, Base62, cache và kiểm thử server
shared/       Model JSON, kiểm tra URL dùng chung và kiểm thử
scripts/      Script PowerShell cài đặt, chạy server và chạy app
gradle/       Gradle Wrapper và cấu hình JDK của Gradle daemon
docs/         Tài liệu API, kịch bản demo, giải thích code và nghiệm thu
```

SQLite cấp ID, sau đó mã hóa Base62 bằng bảng `0-9/a-z/A-Z`. Cùng URL sau trim và cùng chế độ chuyển hướng trả lại liên kết cũ. Cache LRU có TTL phục vụ redirect; metadata không làm tăng bộ đếm redirect. Dự án hiện chạy server một process, chưa triển khai công khai trên Internet.

## Kiểm thử và tài liệu bổ sung

Kiểm thử JVM và build APK:

```powershell
.\gradlew.bat :shared:test :server:test :app:testDebugUnitTest :app:assembleDebug
```

Kiểm thử Compose trên một emulator hoặc điện thoại đã kết nối:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Kiểm tra HTTP thật khi server demo đang chạy và `DEMO_MODE=true`:

```powershell
.\gradlew.bat :server:demoCheck
```

Task `demoCheck` thay đổi dữ liệu và reset cache/counters phục vụ demo; nên chạy trên dữ liệu thử nghiệm.

- [Kịch bản demo](docs/DEMO.md).
- [Tài liệu API](docs/API.md).
- [Giải thích code Kotlin](docs/GIAI_THICH_CODE.md).
- [Đối chiếu nội dung Chapter 8](docs/CHAPTER_8_ALIGNMENT.md).
- [Kết quả kiểm tra đã ghi nhận](docs/VERIFICATION.md).

`QUY_TRINH_THUC_HIEN_URL_SHORTENER.md` được giữ làm tài liệu lịch sử. Hướng dẫn chạy phiên bản Android/Kotlin hiện tại nằm trong README này.

## Nội dung bản ZIP nộp source

Bản ZIP giữ mã nguồn, tài nguyên Android, kiểm thử, cấu hình Gradle, Gradle Wrapper, `.env.example`, script và tài liệu. Các thư mục build/cache, bộ công cụ tải về, môi trường Python cũ, lịch sử `.git`, database chạy thử, `.env` riêng và `local.properties` của máy tác giả không được đóng gói.

Android Studio/Gradle sẽ tải các phụ thuộc và tạo lại file build khi mở trên máy khác. Việc này giúp bản source nhỏ hơn giới hạn 100 MB; bản ZIP không phải một gói chạy ngoại tuyến có sẵn toàn bộ SDK và thư viện.
