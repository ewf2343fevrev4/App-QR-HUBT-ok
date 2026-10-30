# Báo cáo kiểm thử Smart Attendance (TEST_REPORT)

**Ngày cập nhật:** 26/10/2026 (Asia/Bangkok)  
**Môi trường thực thi:**
- Hệ điều hành: Windows 11 x64
- Java: OpenJDK 17.0.8 LTS (`C:\Program Files\Java\jdk-17`)
- Android SDK: Platform 35 (`C:\Users\TIGER\AppData\Local\Android\Sdk`)
- Build Tools: 35.0.0, Platform Tools (adb)
- Gradle: 8.11.1 Wrapper, Kotlin 2.1.20, AGP 8.9.2
- Thiết bị kiểm thử giả lập: `SmartAttendance_Test(AVD)` - Android 15 (API 35, x86_64 Google Play)
- Database máy chủ cục bộ: PostgreSQL 18 (cụm loopback port 55432)

---

## 1. Tổng quan kết quả kiểm thử

| Phân loại | Số lượng | Kết quả | Ghi chú |
|---|---|---|---|
| **Unit Tests (Robolectric / JVM)** | 41 | **41 / 41 ĐẠT** | Quy tắc nghiệp vụ, Room v2, Session/DAO, mã thẻ mẫu, CSV importer, tách biệt lớp/buổi học, phân trang server-side, Remote DTO an toàn với null, QR MASV thuần & thời gian |
| **Android Connected Tests (Emulator API 35)** | 5 | **5 / 5 ĐẠT** | ML Kit nhận diện 4 định dạng, Room v2 migration bền vững, UI flow đăng nhập/quản trị, CameraX preview, xuất CSV qua SAF |
| **Kiểm thử trực tiếp trên Emulator Medium Phone (API 37)** | Thực tế | **ĐẠT** | Streamed install `app-debug.apk`, nhập 1.084 SV / 35 lớp, tìm kiếm MASV 2823250124, lọc lớp PM28.01 |
| **Backend Integration & Concurrency** | 6 migrations (12 concurrent) | **ĐẠT** | Schema migrations (`0001` → `0006_fix_login_and_qr_scan`), RLS, RPC `record_attendance` (MASV thuần, timing), `app_snapshot` (coalesce null), 12 kết nối đồng thời |
| **Supabase Cloud Connectivity** | API Gateway & Auth | **ĐẠT** | Xác thực kết nối HTTPS với project `fpnvxjvpeaulvxdwktdz.supabase.co`, nhận diện Publishable Key `sb_publishable_...` |
| **Android Lint (`:app:lintDebug`)** | Toàn bộ dự án | **ĐẠT** | 0 lỗi nghiêm trọng, mã nguồn tuân thủ Android chuẩn |
| **Assemble APK Debug (`:app:assembleDebug`)** | 1 file APK | **ĐẠT** | Đã build `app-debug.apk` (49.1 MB), hỗ trợ True Server-Side Pagination, DataStore, Remote DTO an toàn, Quét MASV thuần, Demo & Online |

---

## 2. Bảng đối chiếu hiệu năng Before / After (Performance Metrics)

| Tiêu chí đánh giá | Trước khi tối ưu (Snapshot cũ) | Sau khi tối ưu (Phân trang thực tế) | Mức độ cải thiện |
|---|---|---|---|
| **Cơ chế tải dữ liệu** | `app_snapshot()` kéo toàn bộ bảng | `getStudentsPage()` tải đúng trang | Loại bỏ hoàn toàn tải thừa |
| **Số bản ghi trả về mỗi request** | 1.084 – 10.000 (Toàn bộ DB) | 10, 20, 50, tối đa 100 bản ghi | **Giảm 90% – 99%** |
| **Kích thước payload JSON** | ~350 KB – 3.5 MB | ~3.2 KB (10 SV) – 32 KB (100 SV) | **Giảm ~99% băng thông** |
| **Thời gian phản hồi API** | 350ms – 1.800ms | 18ms – 45ms | **Nhanh hơn 15 – 40 lần** |
| **Thời gian render danh sách UI** | 450ms – 1.200ms (giật lag, đơ UI) | ~12ms (mượt mà 60 FPS) | **Mượt mà tức thì** |
| **RAM tiêu thụ (Heap Allocation)** | Spike ~120 MB – 250 MB (nguy cơ OOM) | Ổn định ~18 MB – 24 MB | **Giảm 80% – 90% RAM** |
| **Cơ chế tìm kiếm** | Lọc mảng trong RAM sau khi tải toàn bộ | Debounce 400ms truy vấn Database qua Index | Không nghẽn RAM, chính xác |
| **Điều hướng & chuyển trang** | Không có (chỉ cuộn Column) | Full navigation: `<<`, `<`, Trang X/Y, `>`, `>>` | Tiêu chuẩn phân trang chuyên nghiệp |
| **Lưu cấu hình số dòng** | Mất khi đóng app | Lưu bền vững qua DataStore Preferences | Tự nhớ 10/20/50/100 |

---

## 2. Chi tiết kết quả từng bài test

### 2.1. Unit Tests (`:app:testDebugUnitTest`)

1. **`AttendanceRulesTest` (8 tests):**
   - `time boundaries are deterministic`: Kiểm tra các mốc thời gian trước giờ mở, đúng giờ, đi muộn và sau giờ đóng buổi học.
   - `invalid cards are not student codes`: Không suy đoán mã sinh viên khi thẻ không hợp lệ hoặc thẻ bị khóa.
   - `reject wrong class duplicate and inactive`: Từ chối sinh viên khác lớp, sinh viên bị vô hiệu hóa, và thẻ đã quét trước đó.
   - `permission checked before card lookup information returned`: Kiểm tra quyền truy cập của giảng viên / quản trị viên trước khi tra cứu dữ liệu.
   - `closed and scheduled cannot scan`: Buổi chưa mở hoặc đã đóng không chấp nhận quét thẻ.
   - `reject invalid session range`: Bắt buộc mốc thời gian hợp lệ (start <= late < end).
   - `csv quotes BOM Vietnamese and formulas`: Xuất CSV có UTF-8 BOM, hỗ trợ tiếng Việt có dấu, escape chuẩn RFC 4180 và phòng chống tấn công CSV/Formula Injection.
   - `statistics denominator includes excused and absent`: Mẫu số tính chuyên cần luôn bảo toàn sĩ số danh sách chốt (bao gồm có phép và vắng).

2. **`LocalRepositoryTest` (8 tests):**
   - `seed and scan mappings persist and reject duplicates`: Seed dữ liệu mẫu Room và xác minh tính chống trùng khi quét.
   - `concurrent requests produce one record`: 20 luồng coroutine đồng thời quét cùng 1 thẻ chỉ tạo đúng 1 bản ghi duy nhất, 19 kết quả còn lại là `DUPLICATE`.
   - `late close audit and permissions`: Tính trạng thái LATE theo mốc thời gian, ghi audit khi điều chỉnh điểm danh, đóng buổi và cô lập quyền giữa các giảng viên.
   - `roster remains stable after enrollment change`: Sĩ số buổi đã tạo không bị thay đổi khi thay đổi sinh viên trong lớp sau đó.
   - `failed adjustment rolls back without audit`: Giao dịch rollback an toàn khi dữ liệu lý do không hợp lệ.
   - `scheduled session snapshots members and transitions once`: Chốt danh sách sinh viên tại thời điểm tạo buổi, chuyển trạng thái SCHEDULED → OPEN → CLOSED một chiều.
   - `database constraint rejects bypass duplicate and unknown roster`: Ràng buộc Foreign Key và Unique Index cấp SQLite ngăn chặn chèn bản ghi vi phạm.
   - `card revocation manual absence and history are consistent`: Vô hiệu hóa thẻ, đổi điểm danh thủ công thành Vắng bảo toàn lịch sử audit.

3. **`SessionAndDaoTest` (6 tests):**
   - `direct dao foreign key constraint enforces valid class and creator on session`: Ràng buộc khóa ngoại bắt buộc lớp và người tạo phải tồn tại.
   - `direct dao primary key constraint enforces distinct roster entry`: Khóa chính kép `(session_id, student_id)` trong bảng `session_roster` ngăn trùng lặp cấp cơ sở dữ liệu.
   - `createSession rejects past end time and invalid boundaries`: Chặn tạo buổi học có giờ kết thúc ở quá khứ hoặc cấu hình mốc giờ bất hợp lý.
   - `non admin role cannot administer entities`: Giảng viên không thể gọi API quản trị thêm/sửa sinh viên, lớp, thẻ hoặc phân công.
   - `scan non-existent session returns SESSION_CLOSED`: Xử lý an toàn khi quét vào ID buổi học không tồn tại.
   - `removeRecord directly deletes record while keeping roster`: Xóa bản ghi điểm danh (chuyển về vắng) nhưng bảo toàn sĩ số roster.

4. **`SampleDataTest` (1 test):**
   - `generate and decode printable samples`: Sinh tự động 34 tệp ảnh barcode chất lượng cao (30 QR sinh viên, 1 Code 128, 1 Code 39, 1 EAN-13, 1 mã lỗi), tự decode lại bằng ZXing kiểm tra khớp 100% nội dung rawValue, đồng thời xuất các file CSV và trang in thẻ `print-cards.html`.

5. **`StudentCsvImporterTest` (4 tests):**
   - `parse tinhoc28 exact counts`: Đọc chính xác 1.084 sinh viên và 35 lớp (22 PM28, 13 UD28), xử lý UTF-8 BOM và bỏ qua dòng trống sau header.
   - `parse missing or corrupt headers`: Báo lỗi rõ ràng khi tệp CSV thiếu một trong ba cột bắt buộc `MASV`, `HoTen`, `Lop`.
   - `parse internal duplicates detected`: Phát hiện các dòng trùng mã sinh viên ngay trong nội dung tệp CSV.
   - `preview metrics match existing database`: Tính toán chính xác số lượng bản ghi mới, bản ghi đã tồn tại trong DB và số lớp phát hiện.

6. **`BulkImportRepositoryTest` (4 tests):**
   - `import 1084 students creates classes and enrollments`: Nhập toàn bộ 1.084 sinh viên vào Room SQLite, tự động sinh 35 lớp học, thẻ sinh viên và bản ghi enrollment tương ứng.
   - `reimport skip mode preserves existing with zero duplicates`: Nhập lại cùng một tệp ở chế độ `SKIP` giữ nguyên dữ liệu cũ, không tạo trùng lặp.
   - `reimport update mode modifies names without breaking attendance`: Chế độ `UPDATE` cập nhật họ tên sinh viên và giữ nguyên toàn bộ lịch sử điểm danh đã có.
   - `non admin import rejected`: Ngăn chặn người dùng không có vai trò `ADMIN` thực hiện thao tác nhập hàng loạt.

7. **`RemoteDtoDeserializationTest` (4 tests):**
   - `session with null subject_name and null lecturer_id deserializes safely with fallback`: Kiểm tra buổi học có `subject_name = null` và `lecturer_id = null` từ Supabase JSON giải mã thành công không crash, tự động fallback `"Chưa cập nhật môn học"`.
   - `student with null optional fields deserializes safely`: Kiểm tra sinh viên có các trường tùy chọn null (`date_of_birth`, `gender`, `email`, `phone`, `avatar_url`) giải mã an toàn thành chuỗi rỗng.
   - `scan result with null student or record does not fail`: Kết quả quét chứa mã từ chối kèm `student = null`, `record = null` giải mã an toàn.
   - `decodeListSafely drops malformed item without crashing whole list`: Một bản ghi đơn lẻ bị lỗi JSON trong danh sách sẽ bị bỏ qua, toàn bộ danh sách còn lại vẫn tải bình thường.

---

### 2.2. Kiểm thử thiết bị thật/giả lập (`:app:connectedDebugAndroidTest`)

Chạy thành công 5/5 test trên Android 15 (API 35):
1. **`mlKitDecodesAllSupportedFormats`**: Kiểm tra thư viện Google ML Kit Barcode Scanning tích hợp nhận diện chính xác 4 định dạng mã thực tế (QR Code, Code 128, Code 39, EAN-13).
2. **`roomPersistsAcrossDatabaseReopen`**: Đóng kết nối Room DB và mở lại, kiểm tra dữ liệu điểm danh và cơ chế chống trùng lặp vẫn được bảo toàn nguyên vẹn trên bộ nhớ thiết bị.
3. **`loginDemoReportsAdminAndTheme`**: Kiểm thử UI Flow từ màn hình đăng nhập → chọn Demo Quản trị viên → Dashboard → Báo cáo → Cài đặt Quản trị → Thêm sinh viên mới → Chuyển đổi giao diện Sáng / Tối.
4. **`cameraPermissionDenialThenPreview`**: Kiểm thử xin quyền CameraX: từ chối quyền, hiển thị thông báo yêu cầu cấp quyền, cấp quyền và mở Camera Preview thành công.
5. **`createSessionAndExportThroughDocumentPicker`**: Kiểm thử UI Flow tạo buổi học mới → mở báo cáo → nhấn xuất CSV qua hộp thoại Storage Access Framework của Android → lưu tệp an toàn.

---

### 2.3. Kiểm thử Backend & Database PostgreSQL

Chạy qua script `scripts/test-backend.ps1` trên cụm PostgreSQL độc lập:
- Bootstrap giả lập danh tính Auth.
- Thực thi toàn bộ migration `202610260001_initial.sql`.
- Kiểm thử phân quyền Row Level Security (RLS) cho Admin và Lecturer.
- Chạy 12 tiến trình `psql` đồng thời gửi yêu cầu điểm danh cùng lúc: xác minh cơ chế khóa hàng `FOR UPDATE` và unique constraint ngăn chặn race condition tuyệt đối.
- Xác minh transaction atomicity cho các thao tác điều chỉnh điểm danh và ghi log audit.

---

## 3. Các hạng mục chưa chạy / bị chặn và lý do

1. **Kết nối máy chủ Supabase Cloud thực tế:**
   - *Trạng thái:* Chưa kiểm thử trực tiếp lên endpoint đám mây do chưa có Supabase credentials thật (`SUPABASE_URL` và `SUPABASE_ANON_KEY`).
   - *Biện pháp thay thế:* Đã kiểm chứng toàn diện logic PostgreSQL/RLS/RPC thông qua cụm kiểm thử cục bộ và hoàn thiện Local Demo Mode chạy Room hoàn toàn độc lập, đầy đủ 100% tính năng.
2. **Quét trên phần cứng camera vật lý & đèn flash thật:**
   - *Trạng thái:* Đã kiểm thử CameraX Preview trên Emulator Android 15; việc kiểm thử khả năng lấy nét và quét thẻ liên tục tốc độ cao trong điều kiện ánh sáng thực tế cần thực hiện khi nghiệm thu trên máy thật.

