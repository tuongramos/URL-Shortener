# Liên kết — URL Shortener Android / Kotlin

Ứng dụng Android native bằng **Kotlin + Jetpack Compose**, backend **Kotlin + Ktor**, lưu trữ SQLite. Các model và kiểm tra URL được dùng chung bằng Kotlin. Không dùng WebView, Python, TypeScript hoặc Node.js để chạy bản hiện tại.

Giao diện giữ nền xanh nhạt, thẻ trắng, nút xanh đậm, nội dung tiếng Việt và luồng thao tác của thiết kế đã chốt; bố cục được chuyển sang màn hình điện thoại. Mã web/Python cũ đã được bỏ theo yêu cầu. Tài liệu quy trình gốc được giữ làm tài liệu lịch sử; bộ hướng dẫn chạy bên dưới áp dụng cho bản Kotlin.

## Mở trong Android Studio

1. Chọn **File → Open** và mở chính thư mục **D:\Projects\URL Shortener** (nơi có settings.gradle.kts).
2. Chờ Gradle Sync. Dùng **Gradle JDK là JDK đi kèm Android Studio**, hoặc JDK phù hợp đã cài; không dùng Java 8 cũ trong PATH.
3. Chọn cấu hình chạy **app**, chọn emulator/điện thoại và bấm Run.
4. Chạy server ở terminal riêng và nối cổng như bên dưới.

Dự án gồm ba module: **app** (Android/Compose), **server** (Ktor/JVM), **shared** (model JSON và URL validator). Android Studio Preview có sẵn trạng thái ban đầu và kết quả trong ShortenerScreen.kt.

## Chạy demo cục bộ trên máy hiện tại

Terminal PowerShell thứ nhất, tại gốc dự án:

~~~powershell
.\scripts\start.ps1
~~~

Lệnh này hiện chạy **Ktor**, không chạy FastAPI. Giữ terminal mở. Mặc định server tại http://127.0.0.1:8000, tài liệu API tại http://127.0.0.1:8000/docs.

Mở AVD có sẵn trong **Device Manager** của Android Studio. Terminal thứ hai:

~~~powershell
.\scripts\run-android.ps1
~~~

Nếu có nhiều thiết bị:

~~~powershell
.\scripts\run-android.ps1 -Serial emulator-5554
~~~

Script build APK, thực hiện **adb reverse tcp:8000 tcp:8000**, cài app và mở app. Nhờ ánh xạ cổng này, app và trình duyệt trên emulator/điện thoại USB có thể dùng cùng URL http://127.0.0.1:8000 như máy tính. Đây là cách demo không cần Internet, IP LAN hoặc thay đổi firewall. Nếu rút USB/khởi động lại emulator, chạy lại bước nối cổng.

URL đích ngoại tuyến để dán vào app:

~~~text
http://127.0.0.1:8000/demo/target?chapter=8#chapter-8
~~~

Rút gọn → Sao chép toàn bộ URL ngắn → Mở liên kết hoặc dán vào trình duyệt. Server phải đang chạy. Mã đơn lẻ như “a” chưa phải một URL đầy đủ.

## Cài và build từ đầu

Cần Android Studio, Android SDK Platform 37, Build Tools 36.0.0, Platform-Tools và JDK 17+ tương thích Gradle 9.4.1. Máy hiện tại dùng JDK 25 đi kèm Android Studio. Gradle Wrapper đã kèm mã kiểm tra SHA-256 của bản phân phối.

~~~powershell
.\scripts\setup.ps1
~~~

Lệnh cài/build chạy ba module Kotlin và các kiểm thử JVM. Không cài Python hoặc Node.js. Lần đầu cần mạng để tải Gradle/Maven; sau khi tải phụ thuộc, luồng demo với trang đích cục bộ không cần Internet.

Nếu cần chỉ build:

~~~powershell
.\scripts\setup.ps1 -SkipTests
~~~

APK debug ở **app/build/outputs/apk/debug/app-debug.apk**. Có thể cài APK này trên thiết bị Android 8.0/API 26 trở lên. Bản debug cho phép HTTP để nối server cục bộ; bản release mặc định không cho HTTP rõ văn bản và cần cấu hình HTTPS phù hợp trước khi dùng như bản phát hành.

Nếu PowerShell chặn script, có thể dùng terminal Android Studio và Gradle trực tiếp sau khi đặt JAVA_HOME:

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :server:run
# Terminal khác:
.\gradlew.bat :app:assembleDebug
~~~

## Kết nối điện thoại qua Wi-Fi LAN nếu cần

Mặc định ưu tiên emulator/USB với adb reverse. Nếu không dùng USB:

1. Đặt PUBLIC_BASE_URL trong .env thành http://IP-LAN-CUA-MAY-TINH:8000.
2. Chạy server với .\scripts\start.ps1 -BindAddress 0.0.0.0.
3. Trong app bấm dấu ba chấm cạnh phần đầu trang, nhập cùng địa chỉ LAN trong **Kết nối máy chủ**.
4. Máy tính và điện thoại phải truy cập được nhau; kiểm tra cấu hình mạng/firewall của máy theo nhu cầu thực tế.

Không dùng địa chỉ 10.0.2.2 làm URL chia sẻ chung cho máy tính và điện thoại thật: đó là địa chỉ đặc biệt của emulator. Dự án chưa được đưa lên Internet.

## Cấu hình server

Ứng dụng đọc .env ở gốc dự án, biến môi trường được ưu tiên. Không ghi đè .env đã tồn tại.

| Biến | Mặc định | Vai trò |
| --- | --- | --- |
| PUBLIC_BASE_URL | http://127.0.0.1:8000 | Origin dùng trong URL ngắn, không kèm path/query/fragment |
| DATABASE_PATH | server/data/shortener.db | Đường dẫn SQLite tương đối theo gốc dự án |
| CACHE_MAX_ITEMS | 1000 | Dung lượng cache LRU |
| CACHE_TTL_SECONDS | 300 | TTL từ lúc nạp, HIT không gia hạn |
| DEMO_MODE | false | Bật API thống kê/clear/reset; .env.example đặt true cho demo |
| SERVER_HOST | 127.0.0.1 | Địa chỉ server lắng nghe |
| SERVER_PORT | 8000 | Cổng server |

**Giữ dữ liệu cũ:** .env hiện tại vẫn có thể trỏ tới backend/data/shortener.db. Ktor đọc được đúng schema SQLite của bản trước; không cần Python để sử dụng tệp đó. Khi cài mới, .env.example dùng server/data/shortener.db. Không xóa hoặc đổi DATABASE_PATH nếu muốn tiếp tục dùng các mã đã tạo.

BindAddress/Port quyết định nơi server lắng nghe; chúng không tự thay PUBLIC_BASE_URL. Khi đổi cổng, cần đồng bộ origin của server, kết nối app và ánh xạ adb reverse.

## Kiểm thử

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :shared:test :server:test :app:testDebugUnitTest :app:assembleDebug

# Cần emulator hoặc điện thoại đã kết nối:
.\gradlew.bat :app:connectedDebugAndroidTest

# Cần server đang chạy, DEMO_MODE=true:
.\gradlew.bat :server:demoCheck
~~~

Kiểm thử tự động dùng DB tạm và cache riêng. Kịch bản demoCheck gọi HTTP thật, tạo/dùng lại liên kết mẫu và clear/reset cache/counters của server đang chạy; tránh các request khác xen vào khi kiểm tra số đếm.

## Cấu trúc và tài liệu

~~~text
app/src/main/kotlin/vn/lienket/app/       Activity, ViewModel, repository và Compose UI
app/src/test/                            Kiểm thử trạng thái app trên JVM
app/src/androidTest/                     Kiểm thử Compose trên thiết bị
server/src/main/kotlin/vn/lienket/server/ Ktor, SQLite, Base62, cache và CLI demo
server/src/test/                         Kiểm thử backend Kotlin
shared/src/main/kotlin/vn/lienket/shared/ Model JSON và URL validator
scripts/                                Script PowerShell chạy/build/cài app
docs/                                   API, demo, giải thích Kotlin, nghiệm thu
~~~

- [DEMO.md](docs/DEMO.md): kịch bản báo cáo Chapter 8 trên app Android.
- [API.md](docs/API.md): request, response và lỗi của server Ktor.
- [GIAI_THICH_CODE.md](docs/GIAI_THICH_CODE.md): giải thích các hàm Kotlin.
- [CHAPTER_8_ALIGNMENT.md](docs/CHAPTER_8_ALIGNMENT.md): đối chiếu nội dung chương sách.
- [VERIFICATION.md](docs/VERIFICATION.md): kết quả build/kiểm thử và giới hạn đã xác minh.

SQLite cấp ID, Base62 dùng bảng 0-9/a-z/A-Z và không cắt mã. Cùng URL sau trim + cùng chế độ trả lại liên kết cũ. Cache chỉ nạp trong redirect, metadata không tăng counters. Clear cache không xóa DB/reset counters; reset counters không xóa cache. Bản đầu chạy một process, chưa có ID phân tán, load balancer, analytics hoặc rate limiting.

Nguồn cấu hình: [Android Compose](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler), [AGP 9.2](https://developer.android.com/build/releases/agp-9-2-0-release-notes), [Ktor](https://ktor.io/docs/server-create-a-new-project.html).
