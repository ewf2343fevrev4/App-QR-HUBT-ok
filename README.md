# Smart Attendance – Điểm danh thông minh

Ứng dụng **Android Native bằng Kotlin và Jetpack Compose**, dùng camera quét thẻ sinh viên qua QR, Code 128, Code 39 và EAN-13. Giao diện tiếng Việt, Material 3, sáng/tối. Source nằm trực tiếp tại workspace root.

## Chạy nhanh

1. Mở thư mục này bằng **Android Studio → Open**, chờ Gradle Sync.
2. Dùng **JDK 17**, cài Android SDK Platform **35**, Build Tools **35.0.0**, Platform Tools. Android chạy ứng dụng cần **8.0 / API 26 trở lên**.
3. Android Studio tự tạo `local.properties`. Nếu dùng terminal, tạo dòng `sdk.dir=C:/Users/TEN_USER/AppData/Local/Android/Sdk` tương ứng máy của bạn.
4. Chọn thiết bị/emulator, Run `app`. Chọn **Vào Demo • Quản trị viên**; không cần tài khoản hoặc Internet.
5. Vào **Quét thẻ → Thực hành quét thẻ**, cấp quyền camera. Mở [thẻ mẫu](sample-data/print-cards.html) trên máy khác hoặc in ra để quét.

Buổi demo tự mở trong 2 giờ kể từ lúc dữ liệu được khởi tạo. Nếu đã hết thời gian, tạo buổi mới hoặc vào Cài đặt → Khôi phục dữ liệu demo.

## Chức năng

- Demo Room bền vững: 30 sinh viên giả lập ban đầu, 3 lớp, 2 giảng viên và 1 quản trị; 4 buổi học, bản ghi lịch sử mẫu.
- **Đơn giản hóa dữ liệu sinh viên:** Giao diện thêm, sửa, xem chi tiết và tìm kiếm tập trung vào 3 trường thông tin cốt lõi: `MASV` (chuỗi ký tự duy nhất), `HoTen`, `Lop`.
- **Nhập danh sách sinh viên hàng loạt từ CSV:** Hỗ trợ nhập tệp `tinhoc28.csv` (1.084 sinh viên, 35 lớp: 22 lớp PM28 và 13 lớp UD28), chuẩn UTF-8/BOM, bỏ qua dòng trống; xem trước số dòng hợp lệ, số sinh viên mới/cũ; 3 chế độ trùng `SKIP`, `UPDATE`, `INSERT`; tự động tạo lớp học và liên kết danh sách sinh viên.
- Tài khoản demo giảng viên chỉ thấy lớp được phân công. Online dùng Supabase Auth, refresh token mã hóa bằng Android Keystore.
- Quản lý lớp, danh sách sinh viên, thẻ, giảng viên, phân công; vô hiệu hóa thay vì xóa lịch sử.
- Tạo buổi, lên lịch, mở/đóng; chốt danh sách sinh viên từng buổi để giữ sĩ số lịch sử.
- CameraX → ML Kit tích hợp sẵn → repository → transaction → kết quả. Chống lặp ở scanner và unique constraint ở database.
- Có mặt, đi muộn, vắng có phép; sửa thủ công có lý do và audit trong cùng giao dịch.
- Báo cáo theo lớp, buổi và ngày; chuyên cần từng sinh viên; xuất CSV tiếng Việt UTF-8 BOM qua Android Storage Access Framework.
- Supabase PostgreSQL có RLS, RPC xác minh quyền, thời gian máy chủ và ghi nguyên tử. Không có service-role key trong Android.

## Build và cài APK

Trong PowerShell tại thư mục dự án:

```powershell
.\CHECK_ENV.bat
.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

Hoặc chép APK sang điện thoại, mở tệp và cho phép cài từ nguồn đang dùng. Đây là **debug APK**, dùng cho phát triển/trình diễn; bản phát hành cần keystore riêng và rà soát yêu cầu Google Play tại thời điểm xuất bản.

## Kiểm thử

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
powershell -ExecutionPolicy Bypass -File scripts\test-backend.ps1
```

Lệnh thứ hai cần emulator/thiết bị đang kết nối. Lệnh thứ ba cần PostgreSQL (mặc định `C:\Program Files\PostgreSQL\18\bin`), tự tạo cụm riêng tại `.tools/`, cổng loopback 55432, và dừng sau khi chạy. Không kết nối database đang dùng của bạn. `supabase/tests/bootstrap.sql` chỉ mô phỏng danh tính Auth cho cụm thử này, **không chạy lên Supabase**.

Tạo emulator kiểm thử riêng nếu máy có Android 35 Google Play x86_64 image:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-test-emulator.ps1
```

## QR và dữ liệu mẫu

[sample-data/](sample-data/) gồm CSV và **34 ảnh PNG thật**: 30 QR sinh viên, 1 Code 128, 1 Code 39, 1 EAN-13 và 1 QR không hợp lệ. Các rawValue khớp thẻ trong database demo. Unit test `SampleDataTest` tạo lại ảnh bằng ZXing, giải mã lại từng ảnh và xuất CSV tương ứng; ZXing chỉ là dependency kiểm thử, scanner ứng dụng dùng ML Kit.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*SampleDataTest' --rerun-tasks
```

## Supabase Online

Làm theo [hướng dẫn cấu hình Supabase](docs/SUPABASE_SETUP.md). Sao chép `supabase.properties.example` thành `supabase.properties`, điền URL HTTPS và **publishable/anon key**, build lại. Tệp cấu hình được `.gitignore` loại trừ. Không đưa `service_role` vào APK.

Demo và Online tách biệt hoàn toàn, không tự tải dữ liệu demo lên máy chủ. Tài khoản Auth Online do quản trị tạo qua Supabase Dashboard; kích hoạt, đổi vai trò và phân công tại màn hình Quản trị của ứng dụng.

## Kiến trúc và thư mục

| Đường dẫn | Nội dung |
|---|---|
| `app/src/main/.../domain` | Models, repository interface, quy tắc nghiệp vụ |
| `app/src/main/.../data` | Room, seed, local repository, Supabase Auth/RPC |
| `app/src/main/.../feature` | 12 nhóm màn hình chức năng |
| `app/src/main/.../core` | AppViewModel/StateFlow, CSV, định dạng thời gian |
| `app/src/main/.../navigation`, `ui` | Navigation Compose, theme, component |
| `app/src/test`, `app/src/androidTest` | Unit, Room, dữ liệu mã, kiểm thử thiết bị |
| `supabase/migrations`, `supabase/tests` | Schema, RLS, RPC, kiểm thử SQL |
| `sample-data` | CSV, QR/barcode PNG, trang in thẻ |
| `docs` | Kiến trúc, hướng dẫn, tiến độ, báo cáo kiểm thử |

Phiên bản dependency được cố định trong `app/build.gradle.kts`; Gradle Wrapper 8.11.1 kiểm tra SHA-256 distribution. Không cần cài Gradle hay Python riêng.

## Phạm vi và giới hạn

- Chưa triển khai lên Supabase thật vì không có project credentials; kiểm thử PostgreSQL cục bộ không thay thế kiểm thử Supabase Auth/HTTPS end-to-end.
- Demo dùng đồng hồ thiết bị; Online dùng thời gian máy chủ. Không có hàng đợi điểm danh offline trong Online. Mất mạng phải kiểm tra lại danh sách hoặc quét lại; unique constraint ngăn ghi trùng.
- Một buổi học nằm trong cùng ngày theo múi giờ thiết bị khi tạo; chưa hỗ trợ lịch lặp/qua đêm, Student Portal, XLSX/PDF hoặc push realtime đa thiết bị. Có nút Làm mới. Nhập CSV từ bộ nhớ (SAF) hoặc tệp mẫu `tinhoc28.csv` đã được tích hợp đầy đủ.
- Danh sách sinh viên theo buổi được chốt khi tạo, kể cả buổi lên lịch. Muốn thêm sinh viên vào một buổi khác cần tạo buổi mới.
- Thẻ là mã nhận diện, không chứng minh người cầm thẻ là chủ thẻ; giảng viên cần đối chiếu trực tiếp khi cần.
- Camera/flash trên điện thoại vật lý và khả năng quét liên tục 30–50 thẻ cần nghiệm thu tại thiết bị thực tế.

Xem [TEST_REPORT.md](docs/TEST_REPORT.md) để phân biệt rõ kiểm thử đã chạy, chưa chạy và bị chặn. Xem [VIBE_CODING_WORKFLOW.md](docs/VIBE_CODING_WORKFLOW.md) để theo dõi toàn bộ quy trình phát triển và bài học kinh nghiệm Vibe Coding.
# App-QR-HUBT-ok
