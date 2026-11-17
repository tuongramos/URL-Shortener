# Demo Chapter 8 — Android Kotlin / Jetpack Compose / Ktor

## Chuẩn bị

Mở dự án gốc trong Android Studio, Gradle Sync, chọn module app. Cần .env có DEMO_MODE=true và origin thống nhất http://127.0.0.1:8000 cho demo USB/emulator.

Terminal 1:

~~~powershell
.\scripts\start.ps1
~~~

Mở emulator bằng Device Manager, rồi terminal 2:

~~~powershell
.\scripts\run-android.ps1 -Serial emulator-5554
~~~

Script thực hiện adb reverse để loopback trên thiết bị truy cập được server của máy tính. Trình duyệt trên máy tính dùng cùng URL được. Không cần đưa server lên Internet.

URL mẫu hoàn toàn cục bộ:

~~~text
http://127.0.0.1:8000/demo/target?chapter=8#chapter-8
~~~

## Lời dẫn

“Đây là app Android native viết bằng Kotlin/Jetpack Compose và server Kotlin/Ktor. Demo triển khai ID → Base62, lưu ánh xạ trong SQLite, chuyển hướng HTTP và cache trước DB theo Chapter 8. Bản này chạy một máy chủ, chưa triển khai load balancer hoặc bộ sinh ID phân tán.”

## Thứ tự trình bày

| Bước | Thao tác | Điều giải thích |
| --- | --- | --- |
| 1 | Mở app Android; dán URL mẫu; bấm Rút gọn hoặc Go trên bàn phím | Compose → ViewModel → repository → API Ktor |
| 2 | Sao chép toàn bộ URL ngắn, dán vào trình duyệt hoặc bấm Mở liên kết | Trình duyệt gửi GET; server trả HTTP 302/Location |
| 3 | Gửi lại cùng URL | Trả bản ghi cũ, không sinh mã mới |
| 4 | Mở metadata của mã qua API | Đối chiếu ID và Base62 |
| 5 | Chạy demoCheck bên dưới | Quan sát MISS rồi HIT và một lần đọc DB |
| 6 | Xem 301/302 từ demoCheck và API | Hai mã cho cùng URL nhưng mode khác |
| 7 | Dừng server, chạy lại, mở URL cũ | SQLite giữ dữ liệu, RAM và counters bắt đầu lại |
| 8 | Nhập FTP/URL sai trong app | Thông báo tại ô nhập; sửa và gửi lại được |
| 9 | Nhắc phạm vi một process và hướng mở rộng | Không tuyên bố đã thử tải ở quy mô bài toán sách |

## Kịch bản Kotlin kiểm chứng HTTP thật

Khi server đang chạy:

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :server:demoCheck
~~~

Task tạo/dùng lại URL, clear/reset, gọi redirect hai lần, đọc metadata/stats, tạo mode 301 riêng, kiểm tra 404/422 và theo redirect đến trang đích thật. Có thể chạy lại; tránh bấm liên kết đồng thời vì stats là của cả phiên server.

Đọc response trực tiếp, thay CODE bằng mã thực tế:

~~~powershell
curl.exe -s -D - -o NUL http://127.0.0.1:8000/r/CODE
~~~

Không dùng -L nếu cần xem status redirect đầu tiên. Dùng GET để minh họa; không phụ thuộc HEAD. Header X-Cache nói về RAM, Cache-Control nói về phản hồi HTTP phía client. Trình duyệt dùng cache 301 có thể không tăng counters.

## Nếu dùng điện thoại thật

Ưu tiên USB + adb reverse như README. Với Wi-Fi, cần origin IP LAN của laptop, server bind 0.0.0.0 và địa chỉ kết nối app tương ứng (dấu ba chấm ở phần đầu trang). Hai thiết bị phải truy cập được nhau. Chỉ copy mã đơn lẻ không mở được trên web; phải copy toàn URL, server đang chạy và địa chỉ truy cập được.

## Trước báo cáo

- Chạy test Kotlin và build APK theo README.
- Thử app trên thiết bị sẽ dùng; kết nối USB/ADB còn hiệu lực.
- Giữ một URL cũ để thử sau restart.
- Đọc [GIAI_THICH_CODE.md](GIAI_THICH_CODE.md) để chuẩn bị giải thích các hàm Kotlin.
- Đọc [CHAPTER_8_ALIGNMENT.md](CHAPTER_8_ALIGNMENT.md): Base62 không bắt buộc đủ 7 ký tự; ID SQLite chỉ duy nhất trong DB hiện tại.
