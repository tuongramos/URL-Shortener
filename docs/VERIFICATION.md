# Nghiệm thu bản Android / Kotlin

Ngày kiểm tra: **17/11/2026**, giờ Việt Nam. Bản chạy hiện tại là Android native Kotlin/Jetpack Compose và backend Kotlin/Ktor. Kết quả này thay cho nghiệm thu Python/TypeScript trước khi chuyển đổi.

## Build và kiểm thử tự động

| Nhóm | Kết quả |
| --- | --- |
| Shared URL validator | 4/4 đạt |
| Backend Base62/cache/SQLite/cấu hình | 10/10 đạt |
| Backend API Ktor | 9/9 đạt |
| Android ViewModel/state/repository contract | 10/10 đạt |
| Compose instrumented trên Medium_Phone, Android 15/API 35 | 5/5 đạt |
| Tổng | **38/38 đạt** |
| APK debug | **Build thành công**, app/build/outputs/apk/debug/app-debug.apk |
| Script setup.ps1 | Chạy build APK, server installDist và test JVM thành công |
| Script start.ps1 | Khởi động server Ktor cục bộ thành công |
| Script run-android.ps1 | adb reverse, cài APK và mở MainActivity thành công |
| PowerShell parser | Cả bốn script hiện tại hợp lệ cú pháp |

Đã kiểm tra thêm bằng Windows PowerShell 5.1: script trước dùng chuỗi tiếng Việt UTF-8 không BOM gây đọc sai ký tự và lỗi parser. Thông báo trong script đã chuyển sang ASCII không dấu; cả bốn script parse được trên PowerShell 5.1 và 7. Chạy `run-android.ps1` bằng PowerShell 5.1 đã build thành công, cài APK và mở MainActivity trên emulator. Script chờ Android khởi động hoàn tất tối đa 120 giây trước khi cài APK. Giao diện Android vẫn giữ tiếng Việt có dấu.

Gradle 9.4.1, AGP 9.2.1, Kotlin 2.4.21, Compose BOM 2026.09.00, Ktor 3.6.1; compile SDK 37, target SDK 35, min SDK 26. JDK chạy build/server trên máy hiện tại là JDK 25 đi kèm Android Studio. Gradle Wrapper có checksum SHA-256.

## Kiểm tra chức năng Kotlin

- Đúng ví dụ Base62 trong sách và biên 62^7.
- Cùng URL/mode dùng lại, khác 301/302 có mã riêng.
- URL HTTP/HTTPS và giới hạn 4096; giữ query/fragment/percent encoding.
- JSON sai kiểu, mode string/float/bool, trường thừa và dữ liệu lỗi bị từ chối.
- SQLite transaction IMMEDIATE và UNIQUE; nhiều yêu cầu trùng chỉ tạo một bản ghi.
- Mã a và A phân biệt.
- TTL hết hạn, LRU, clear/reset độc lập, không cache âm.
- Bộ đếm đúng khi truy cập đồng thời; metadata/stats không tăng redirect.
- Demo tắt trả 404; nghiệp vụ tạo vẫn hoạt động.
- ViewModel chống gửi lặp, giữ input khi lỗi, cho retry, phục hồi input/result và lưu cấu hình server.
- UI Compose có form, nút disabled khi loading, kết quả, callback copy/open và sao chép tay; không tự mở URL ngắn khi vừa tạo.

## HTTP thật và dữ liệu trước chuyển đổi

Task Kotlin **:server:demoCheck** đã chạy thành công trên server thật:

1. Tạo/dùng lại URL 302.
2. Clear/reset, nhận MISS rồi HIT.
3. Nhận đúng hai request, một hit, một miss và một DB lookup; hit rate 0.5.
4. Metadata không làm tăng bộ đếm.
5. Tạo 301 riêng, kiểm tra 404/422.
6. Theo URL ngắn thật tới /demo/target, nhận 200 và nội dung “Chuyển hướng thành công”.

Đã khởi động lại tiến trình Ktor thật: RAM/counters bắt đầu 0, đọc được bản ghi ID 1 từ dữ liệu cũ. URL tạo trực tiếp trong app Android còn tồn tại, redirect đầu sau restart trả 302/MISS/Location đúng; theo chuyển hướng tới trang đích cục bộ thành công.

SQLite cũ được giữ tại backend/data/shortener.db theo .env hiện hữu; bản cài mới mặc định dùng server/data/shortener.db. Không cần Python để đọc DB cũ.

## Quan sát trên emulator

Đã quan sát trực tiếp giao diện dọc: giữ nền xanh nhạt, logo, tiêu đề, thẻ trắng và nút xanh đậm theo thiết kế cũ. Nhập URL đích cục bộ trong app, tạo URL ngắn bằng repository HTTP thật, hiện URL gốc và kết quả; bấm Sao chép nhận “Đã sao chép liên kết”.

Bấm Mở liên kết đã gọi Chrome Android qua ACTION_VIEW. Chrome của AVD dừng ở màn hình thiết lập lần đầu có phần tài khoản/điều khoản; chưa hoàn tất thiết lập đó thay người dùng, nên **chưa ghi nhận trang đích hiển thị trực tiếp trong Chrome Android**. HTTP theo redirect đến trang đích đã được kiểm chứng riêng bằng client thật. Trước buổi báo cáo, người dùng tự hoàn tất thiết lập trình duyệt nếu được hỏi.

Sau khi quan sát, đã hoàn thiện việc bỏ focus/ẩn bàn phím khi gửi và build lại; cả năm kiểm thử Compose được chạy lại thành công trên APK cuối.

## Phạm vi và giới hạn

- Đã xóa nguồn web/Python và các bản lưu legacy theo yêu cầu; app/server/shared hiện là Kotlin.
- Tài liệu quy trình gốc còn trong dự án để tham khảo lịch sử, không phải lệnh chạy hiện tại.
- Không dùng WebView hoặc nhúng server Python trong Android.
- Chưa triển khai Internet; chỉ localhost/USB/emulator hoặc LAN khi cấu hình.
- Chưa kiểm tra điện thoại vật lý, mạng Wi-Fi khác hoặc bản release HTTPS.
- Chưa có load balancer, ID phân tán, analytics, rate limiting hoặc cache dùng chung nhiều process.
- Emulator có thể cần tự hoàn tất thiết lập Chrome lần đầu; không tự đăng nhập hay thay quyền bảo mật của trình duyệt.

Xem [README](../README.md), [DEMO](DEMO.md), [API](API.md) và [giải thích code Kotlin](GIAI_THICH_CODE.md).
