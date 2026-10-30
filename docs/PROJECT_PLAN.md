# Smart Attendance — kế hoạch triển khai

Workspace: `C:\Users\TIGER\Desktop\ST2`. Ngày bắt đầu: 26/10/2026 (Asia/Bangkok).
Workspace ban đầu trống. Có JDK 17.0.8, Android Studio, SDK 34–36, PostgreSQL 18.
Không có thông tin Supabase. Demo là nguồn dữ liệu độc lập, không đồng bộ ngầm.

1. Foundation: Gradle Kotlin DSL, Compose/Material 3, navigation, MVVM, theme.
2. Data: domain/repository, Room quan hệ và transaction, dữ liệu demo; migrations/RLS/RPC Supabase.
3. Authentication/dashboard: demo theo vai trò, Supabase Auth, session an toàn.
4. Classes/sessions: lớp, hồ sơ, tạo lịch, mở/đóng; snapshot sĩ số tại lúc tạo buổi.
5. Camera: CameraX + ML Kit bundled, quyền, lifecycle, cooldown và kết quả sau commit.
6. Nghiệp vụ: kiểm thử quyền, thời gian, thẻ, lớp, trùng, sửa có audit.
7. Reports: thống kê theo roster, bộ lọc, CSV BOM qua SAF, chống formula injection.
8. Admin/settings: sinh viên, lớp, thẻ, enrollment, phân công, tài khoản, giao diện, reset demo.
9. QA: unit, Room/instrumentation khi có emulator, PostgreSQL integration; sửa lỗi thực tế.
10. Delivery: clean build, APK thật, dữ liệu QR, hướng dẫn và báo cáo bằng chứng.

## Quyết định
- Java 17, minSdk 26, compile/target SDK 35; dependency cố định, xác minh bằng Gradle.
- Kotlin 2 + Compose compiler plugin, Room KSP, manual dependency injection.
- Dữ liệu domain serializable; Room entities tách lớp. Supabase REST/Auth qua HTTPS và RPC.
- `session_roster` cố định mẫu số cho báo cáo lịch sử; chỉ sinh viên active được snapshot.
- Không tự gửi lại scan khi mạng lỗi: kết quả chưa xác nhận phải báo rõ; quét lại an toàn nhờ unique constraint.
- Đóng buổi là trạng thái cuối; sửa thủ công yêu cầu lý do và audit trong cùng transaction.

## Nguồn tham khảo chính thức
- https://developer.android.com/build/releases/agp-8-9-0-release-notes
- https://developer.android.com/jetpack/androidx/releases/compose-kotlin
- https://developers.google.com/ml-kit/vision/barcode-scanning/android
- https://supabase.com/docs/guides/database/functions

Chỉ đánh dấu kiểm thử đạt khi đã chạy; camera vật lý và Supabase thật cần môi trường tương ứng.
