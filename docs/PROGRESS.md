# Tiến độ dự án Smart Attendance

**Cập nhật lần cuối:** 26/10/2026 (Asia/Bangkok)  
**Tác giả tiếp quản:** Senior Android Engineer & AI Coding Agent

---

## 1. Trạng thái các Phase

- **Phase 0 (Khởi tạo & Khảo sát):** ĐÃ HOÀN THÀNH. Đã kiểm tra toàn bộ workspace, JDK 17, Android SDK 35, Build Tools 35.0.0, Gradle Wrapper, emulator Android 15.
- **Phase 1–8 (Phát triển chức năng cốt lõi):** ĐÃ HOÀN THÀNH.
  - Kiến trúc Android Native hiện đại: Jetpack Compose + Material 3 (Theme sáng/tối), MVVM, StateFlow, Coroutines.
  - Cơ sở dữ liệu cục bộ Room: Entities, DAO, quan hệ Foreign Keys, Unique Indexes, Seed 30 sinh viên mẫu, 3 lớp học, 2 giảng viên, 1 quản trị viên, 4 buổi học.
  - Quản lý phiên đăng nhập: Demo Mode cục bộ (phân quyền theo vai trò Admin / Lecturer Minh Anh / Lecturer Hoàng Nam) và Online Mode (Supabase Auth qua Keystore Token Vault).
  - Quản lý lớp học & buổi học: Lên lịch, mở/đóng buổi, chốt danh sách sinh viên `session_roster` tại thời điểm tạo để giữ nguyên mẫu số sĩ số lịch sử.
  - Quét thẻ sinh viên CameraX + Google ML Kit (hỗ trợ 4 định dạng: QR Code, Code 128, Code 39, EAN-13), cơ chế debounce/cooldown và xác nhận commit an toàn.
  - Nghiệp vụ điểm danh: Có mặt (PRESENT), Đi muộn (LATE), Có phép (EXCUSED), Vắng (ABSENT). Chống trùng lặp tuyệt đối ở cả ViewModel, Repository và SQLite Unique Constraint.
  - Thống kê & Xuất CSV: Bộ lọc theo lớp, buổi và khoảng ngày. Xuất CSV UTF-8 BOM qua Storage Access Framework (SAF), escape chuẩn RFC 4180 và chống Formula Injection.
  - Phân hệ Quản trị: Quản lý sinh viên, lớp học, thẻ sinh viên, danh sách lớp (enrollment), tài khoản giảng viên và ghi lịch sử thay đổi (Audit Log).
- **Phase 9 (Kiểm thử chuyên sâu & Sửa lỗi thực tế):** ĐÃ HOÀN THÀNH.
  - Sửa lỗi biên dịch Gradle Kotlin DSL: Thêm `import java.util.Base64` trong `app/build.gradle.kts`.
  - Bổ sung bộ unit test `SessionAndDaoTest.kt` kiểm tra các ràng buộc Foreign Key, Unique Constraint, Session time boundaries, quyền quản trị và xóa bản ghi.
  - Chạy toàn bộ 23 Unit Tests: 23/23 ĐẠT (`AttendanceRulesTest`, `LocalRepositoryTest`, `SessionAndDaoTest`, `SampleDataTest`).
  - Chạy bộ kiểm thử thiết bị trên Android 15 (API 35): 5/5 ĐẠT (`DeviceIntegrationTest`, `UiSmokeTest`).
  - Chạy bộ kiểm thử Backend & 12 Concurrent Requests trên PostgreSQL: ĐẠT.
- **Phase 10 (Hoàn thiện tài liệu, Báo cáo kiểm thử & Đóng gói APK):** ĐÃ HOÀN THÀNH.
  - Chạy thành công `:app:lintDebug` (0 lỗi).
  - Build thành công APK thật: `app/build/outputs/apk/debug/app-debug.apk` (43.8 MB).
  - Cài đặt Streamed Install qua `adb` lên Android Emulator API 35 và khởi chạy thành công.
  - Bổ sung tài liệu kiểm thử chi tiết tại `docs/TEST_REPORT.md`.
  - Lưu trữ ảnh chụp thực tế ứng dụng đang chạy tại `docs/screenshots/live-running.png`.
- **Phase 11 (Đơn giản hóa dữ liệu sinh viên & Nhập danh sách sinh viên từ CSV):** ĐÃ HOÀN THÀNH.
  - **Đơn giản hóa mô hình sinh viên:** Giao diện thêm, sửa, xem chi tiết và tìm kiếm sinh viên chỉ hiển thị và thao tác với 3 trường thông tin cốt lõi: `MASV` (chuỗi ký tự duy nhất), `HoTen`, `Lop`.
  - Giữ nguyên cấu trúc dữ liệu không phá hủy: Bảo toàn lịch sử điểm danh, bảng `enrollments`, `session_roster`, `attendance_records` và thẻ `student_cards`.
  - **Nhập dữ liệu hàng loạt từ CSV (`tinhoc28.csv`):**
    - Hỗ trợ tệp 1.084 sinh viên, 35 lớp (22 lớp PM28.xx và 13 lớp UD28.xx), xử lý chuẩn UTF-8 BOM, bỏ qua dòng trống sau header.
    - Bộ đọc CSV `StudentCsvImporter.kt` hỗ trợ cả Android Storage Access Framework (SAF) và tệp đính kèm tích hợp sẵn `sample-data/tinhoc28.csv` (`assets/tinhoc28.csv`).
    - Hộp thoại xem trước (Preview Dialog): Thống kê số dòng hợp lệ (1.084), số mới, số đã có, danh sách 35 lớp phát hiện, lỗi (nếu có).
    - 3 chế độ xử lý trùng: `SKIP` (bỏ qua mã đã có), `UPDATE` (cập nhật họ tên và lớp, bảo toàn lịch sử điểm danh), `INSERT` (chỉ thêm mới).
    - Tự động tạo mã lớp, gán giảng viên phụ trách mặc định và liên kết danh sách sinh viên theo lớp.
    - Cả hai chế độ hoạt động: Room SQLite Transaction trong Demo Mode và PostgreSQL Stored Procedure `admin_import_students` trong Online Mode (`202610260002_import_students.sql`).
  - **Kiểm thử thực tế trên Emulator Medium Phone (API 37):**
    - Đã cài đặt APK, nhập trực tiếp 1.084 sinh viên và 35 lớp thành công (tổng sinh viên tăng lên 1.114, tổng lớp tăng lên 38).
    - Tìm kiếm nhanh theo MASV `2823250124` (Trần Quốc Bảo - PM28.01) phản hồi tức thì.
    - Lọc theo từng lớp trong 35 lớp hoạt động chính xác.
    - 31/31 Unit Tests đạt chuẩn (bổ sung `StudentCsvImporterTest` và `BulkImportRepositoryTest`).

- **Phase 12 (Hoàn thiện Online Mode với Supabase Cloud & Ánh xạ Giảng viên/Môn học):** ĐÃ HOÀN THÀNH.
  - **Tương thích Supabase Cloud & Publishable Key:**
    - Cấu hình kết nối thành công với dự án Supabase Cloud `https://fpnvxjvpeaulvxdwktdz.supabase.co`.
    - Hỗ trợ đầy đủ Publishable Key mới (`sb_publishable_...`) và định dạng biến môi trường `NEXT_PUBLIC_SUPABASE_...`.
    - Đã xác thực API Gateway và kiểm tra kết nối PostgREST/Auth thành công.
  - **Nâng cấp Migration `202610260002_import_students.sql`:**
    - Tuyệt đối không tự gán ADMIN làm giảng viên: Kiểm tra nghiêm ngặt `role == 'LECTURER'` của tài khoản được phân công.
    - Không suy đoán tên môn học: Yêu cầu Admin cung cấp thông tin môn học hợp lệ trước khi tạo lớp mới.
    - Hỗ trợ ánh xạ lớp linh hoạt (`class_mappings`): Tự động liên kết vào lớp đã có hoặc tạo lớp mới kèm giảng viên/môn học phân công.
    - Kiểm tra và đảm bảo quyền `ADMIN` của người gọi RPC, ghi Audit Log đầy đủ.
  - **Hoàn thiện UI Ánh xạ Giảng viên & Lớp trên ứng dụng:**
    - Màn hình Preview & Mapping hiển thị rõ các lớp đã có trong hệ thống và danh sách lớp mới cần phân công.
    - Cho phép cấu hình nhanh Môn học và chọn Giảng viên phụ trách (chỉ lọc các tài khoản có vai trò `LECTURER` đang hoạt động, loại trừ tài khoản `ADMIN`).
    - Khóa nút xác nhận và hiển thị thông báo nếu chưa chọn Giảng viên hoặc thiếu tên môn học.
  - **Kiểm thử & Đóng gói:**
    - 31/31 Unit Tests đạt 100% với cấu hình JVM C1 (`-XX:TieredStopAtLevel=1`).
    - Backend integration test trên PostgreSQL 18 kèm 12 concurrent requests chạy thành công 100%.
    - Đóng gói thành công APK mới `app-debug.apk` (43.2 MB) sẵn sàng đăng nhập và chạy Online.
    - Cập nhật tài liệu hướng dẫn triển khai an toàn lên Cloud tại `docs/SUPABASE_SETUP.md`.

- **Phase 13 (Tách biệt hoàn toàn Lớp học & Buổi học — Handoff Classes & Sessions):** ĐÃ HOÀN THÀNH.
  - **Mô hình kiến trúc mới:**
    - **Sinh viên (Students):** Chuẩn hóa chỉ có 3 trường dữ liệu: `MASV`, `HoTen`, `Lop`.
    - **Lớp học (Classes):** Chỉ quản lý danh sách sinh viên (`class_code`, `class_name`). Bỏ hoàn toàn ràng buộc môn học và giảng viên ở cấp độ lớp. Form tạo lớp PM28.02 chỉ hỏi mã lớp và tên lớp.
    - **Buổi học (Attendance Sessions):** Quản lý trực tiếp `class_id`, `subject_name`, `lecturer_id`, thời gian, trạng thái và chốt `session_roster`. Giảng viên và môn học thuộc về từng buổi học cụ thể. Một lớp có thể học nhiều môn với nhiều giảng viên khác nhau.
    - **Phân quyền Buổi học:** Kiểm tra theo giảng viên được phân công của buổi học (`attendance_sessions.lecturer_id`), Giảng viên A không xem hay quét điểm danh buổi của Giảng viên B dù dạy cùng lớp. ADMIN có quyền quản lý toàn hệ thống nhưng không được tự đổi role thành LECTURER.
  - **Nâng cấp Room SQLite Migration v1 → v2 (`MIGRATION_1_2`):**
    - Chuyển `classes.subject_name` và `classes.lecturer_id` thành nullable để tương thích ngược.
    - Thêm `attendance_sessions.subject_name` và `attendance_sessions.lecturer_id` (kèm foreign key và index).
    - Backfill dữ liệu lịch sử tự động, không phá hủy DB, bảo toàn 100% dữ liệu Demo cũ.
    - Đã sinh schema `app/schemas/.../2.json` tự động qua Room Compiler.
  - **Triển khai & Kiểm thử PostgreSQL Migration 003 (`202610260003_separate_classes_sessions.sql`):**
    - Tích hợp vào `scripts/test-backend.ps1` và kiểm thử thành công trên cụm PostgreSQL 18.
    - RPC `list_active_lecturers()`: Trả về danh sách giảng viên ACTIVE/LECTURER có tên thật (không lộ email/mật khẩu).
    - RPC `admin_save_student_with_class(p_masv, p_full_name, p_class_code, p_mode)`: Thêm sinh viên đơn lẻ chuẩn 3 trường, tự tạo lớp nếu chưa có.
    - RPC `admin_import_students(payload, mode)`: Khóa đồng thời bằng advisory lock `24681012`, tự động tạo lớp chưa có, upsert sinh viên không trùng, không tạo thẻ giả trên Cloud.
  - **Hoàn thiện Giao diện Jetpack Compose:**
    - **Tạo buổi học (`CreateSessionScreen`):** Tải danh sách giảng viên động từ `list_active_lecturers()`, xử lý đầy đủ trạng thái Loading, Empty warning (hướng dẫn tạo Auth account trên Supabase), Error. Nhập môn học bắt buộc. Giảng viên bị khóa vào chính mình; Admin chọn bất kỳ giảng viên hoạt động.
    - **Quản trị sinh viên (`AdminEditor`):** Form Thêm/Sửa sinh viên đúng 3 trường MASV, Họ tên, Lớp. Form Lớp học chỉ gồm mã lớp và tên lớp.
    - **Import CSV (`AdminScreen`):** Bỏ yêu cầu ánh xạ môn học/giảng viên. Xem trước sĩ số, các lớp phát hiện, 3 chế độ SKIP/UPDATE/INSERT và thực hiện nhập 1-click.
    - **Chi tiết buổi học & Báo cáo (`AttendanceScreen`, `ClassScreens`, `CsvExporter`):** Hiển thị rõ môn học, lớp, tên giảng viên phụ trách lấy từ buổi học.
  - **Kiểm thử & Đóng gói:**
    - 32/32 Unit Tests đạt 100% (`testDebugUnitTest`).
    - Backend integration test và 12 concurrent requests: ĐẠT 100%.
    - Build thành công APK `app-debug.apk` (43.1 MB).

- **Phase 14 (Tối ưu hóa hiệu năng toàn diện & Phân trang Server-Side — Performance Optimization):** ĐÃ HOÀN THÀNH.
  - **Khắc phục triệt để hiện tượng Lag, Giật và Nguy cơ OOM:**
    - Chấm dứt việc tải toàn bộ 1.084+ sinh viên vào RAM khi mở màn hình quản trị qua hàm `app_snapshot()`.
    - Triển khai **True Server-Side Pagination + Lazy Loading** ở cả tầng Room SQLite (Demo Mode) và Supabase PostgreSQL (Online Mode).
  - **Phân trang sinh viên & Lưu cấu hình bằng DataStore:**
    - Tích hợp `androidx.datastore:datastore-preferences:1.1.2` (`StudentPreferences.kt`).
    - Bộ chọn số dòng: `10` (mặc định), `20`, `50`, `100` (tối đa). Tuyệt đối không có All/Unlimited. Lựa chọn được tự động lưu bền vững cho các lần mở ứng dụng tiếp theo qua DataStore.
    - Điều hướng phân trang đầy đủ: Trang đầu (`<<`), Trang trước (`<`), Trang hiện tại (`Trang 1 / 109`), Trang tiếp theo (`>`), Trang cuối (`>>`).
    - Hiển thị rõ dải bản ghi: `Hiển thị 1–10 / 1.084 sinh viên`.
    - Khi đổi số dòng hoặc đổi bộ lọc/từ khóa, tự động trở về trang 1 và tải đúng lát cắt dữ liệu.
  - **Tìm kiếm thông minh (Smart Search):**
    - Tìm kiếm trực tiếp từ Database theo `MASV`, `Họ tên`, hoặc `Mã lớp`.
    - Cơ chế Debounce 400ms qua Coroutines Flow tránh spam truy vấn khi gõ phím liên tục.
    - Request ID & Job Cancellation: Hủy ngay request cũ đang chờ nếu có từ khóa mới, đảm bảo phản hồi cũ không bao giờ ghi đè kết quả mới.
    - Xử lý tiếng Việt Unicode chính xác, tìm kiếm không phân biệt chữ hoa/thường (`ILIKE`).
    - Trạng thái rõ ràng: Loading Skeleton/Bar, Empty State ("Không tìm thấy sinh viên phù hợp"), Error State kèm nút "Thử lại".
  - **Tối ưu PostgreSQL & Supabase Migration 004 (`202610260004_paginate_students.sql`):**
    - Cài đặt extension `pg_trgm` (trigram) phục vụ tìm kiếm chuỗi con siêu tốc.
    - Thiết lập các chỉ mục tối ưu: `students(student_code ASC, id ASC)`, GIN trigram index trên `full_name` và `student_code`, index trên `classes(class_code)` và `enrollments(student_id, class_id)`.
    - Viết RPC chuyên dụng `admin_get_students_page(p_page, p_page_size, p_search, p_class_code)` trả về JSON phân trang chuẩn (`items`, `page`, `pageSize`, `totalItems`, `totalPages`, `hasNextPage`, `hasPreviousPage`). Phía server tự động clamp `pageSize` từ 1 đến 100.
    - RPC `get_student_by_id(uuid)` tải chi tiết đúng 1 sinh viên khi cần chỉnh sửa.
    - Kiểm tra bảo mật RLS nghiêm ngặt: Chỉ có tài khoản vai trò ADMIN mới được phép gọi RPC quản trị sinh viên.
  - **Tối ưu Room SQLite (Demo Mode):**
    - Viết truy vấn Room DAO phân trang thật với `LIMIT :pageSize OFFSET :offset` và `COUNT(DISTINCT s.id)`. Tuyệt đối không tải toàn bộ sinh viên rồi cắt mảng bằng `.take(10)`.
    - Viết truy vấn `totalStudents()` và `totalClasses()` phục vụ aggregate đếm nhẹ cho Dashboard.
  - **Tối ưu Jetpack Compose UI:**
    - Thay thế việc kết xuất danh sách sinh viên khổng lồ bằng danh sách chỉ hiển thị đúng trang được chọn (10, 20, 50, 100).
    - Đơn giản hóa form sinh viên chỉ gồm 3 trường: `MASV`, `HoTen`, `Lop` (không hỏi môn học/giảng viên).
  - **Kiểm thử & Đóng gói APK:**
    - 36/36 Unit Tests đạt 100% (`LocalRepositoryTest`, `AttendanceRulesTest`, `BulkImportRepositoryTest`, v.v.).
    - Backend integration test trên PostgreSQL 18 kèm 12 concurrent requests chạy thành công 100%.
    - Đóng gói thành công APK mới `app-debug.apk` (49.1 MB) tích hợp DataStore và phân trang hoàn chỉnh.

- **Phase 15 (Sửa lỗi HTTP 400 khi Import CSV 1.084 sinh viên ở chế độ UPDATE & Tự động Chuyển lớp an toàn):** ĐÃ HOÀN THÀNH.
  - **Nguyên nhân gốc rễ HTTP 400:**
    - Trên Supabase Cloud, RPC `admin_import_students` của Migration 002 vẫn giữ logic cũ bắt buộc phải có `class_mappings` với `subject_name` và `lecturer_id` (nếu thiếu sẽ quăng lỗi `MISSING_CLASS_MAPPING`, PostgREST trả về HTTP 400).
    - `SupabaseApi.kt` trước đây chỉ hiển thị generic "HTTP 400" mà giấu đi message, error code, details và hint thực tế của Supabase.
  - **Khắc phục toàn diện:**
    - Tạo migration mới `supabase/migrations/202610260005_fix_import_csv_update.sql`:
      - Idempotent: Bỏ ràng buộc `NOT NULL` trên `classes.subject_name` và `classes.lecturer_id` (Lớp học chỉ thuần túy quản lý danh sách sinh viên).
      - Đảm bảo `attendance_sessions` có `subject_name` và `lecturer_id` (Môn học và giảng viên chỉ cấu hình khi tạo buổi học).
      - Nâng cấp RPC `public.admin_import_students(payload jsonb, mode text DEFAULT 'SKIP')`:
        - Chuẩn hóa đầu vào đúng 3 trường: `masv`, `ho_ten`, `lop`. Tuyệt đối không đòi hỏi môn học hay giảng viên khi import.
        - Tự động tạo lớp học nếu chưa tồn tại.
        - **Xử lý chuyển lớp an toàn trong UPDATE mode:** Khi sinh viên chuyển sang lớp mới, đặt trạng thái các lớp cũ thành `INACTIVE`, kích hoạt lớp mới `ACTIVE`. Lịch sử điểm danh (`attendance_records` và `session_roster`) của các buổi học trong quá khứ được bảo toàn 100%, không bị ảnh hưởng.
        - Không tạo trùng bản ghi enrollment, không tạo thẻ giả trên Cloud.
        - Khi import lại file (re-import) trong chế độ UPDATE: Không tạo trùng, ghi nhận `0 newStudents`.
    - Nâng cấp `SupabaseApi.kt`: Bóc tách chi tiết `[Mã lỗi: $code]`, `serverMessage`, `Chi tiết: $details`, `Gợi ý: $hint` khi API trả về mã lỗi HTTP không thành công.
    - Cập nhật `LocalAttendanceRepository.kt`: Đồng bộ xử lý chuyển lớp trong UPDATE mode ở Local Demo.
  - **Kiểm thử thực tế:**
    - PostgreSQL 18 backend integration test: Chạy toàn bộ 5 migrations (`0001` → `0005`), kiểm thử chuyển lớp, re-import trùng và 12 concurrent requests: **100% PASSED**.
    - Unit tests: **37 / 37 ĐẠT 100%** (bổ sung bài test chuyển lớp bảo toàn lịch sử điểm danh trong `BulkImportRepositoryTest`).
    - Build APK mới: `app-debug.apk` (49.1 MB) biên dịch thành công.

- **Phase 16 (Khắc phục triệt để lỗi Đăng nhập Online & Lỗi Quét QR MASV Thuần):** ĐÃ HOÀN THÀNH.
  - **Nguyên nhân gốc rễ:**
    1. *Lỗi Đăng nhập Online:* Remote `app_snapshot` trả về JSON chứa `"subject_name": null` (và `"lecturer_id": null`) cho các buổi học lịch sử. Android dùng `AttendanceSession` với non-null `String = ""`. Trong Kotlinx Serialization, một key có giá trị `null` literal trong JSON sẽ quăng `SerializationException: Expected string value for a non-null key 'subject_name', got null literal instead` làm crash toàn bộ quá trình đăng nhập.
    2. *Lỗi Quét QR MASV Thuần (`2823156783`):* `record_attendance` trước đây chỉ tra cứu duy nhất trong bảng `student_cards WHERE card_code = p_card_code`. Sinh viên import từ CSV không sinh thẻ giả, và QR trên thẻ sinh viên vật lý in thuần túy MASV (`2823156783`), dẫn đến backend trả về `INVALID_CARD`.
    3. *Lỗi Thời gian Buổi học:* `record_attendance` và `AttendanceRules` gộp chung các trường hợp chưa đến giờ, hết giờ và đã đóng thành `SESSION_CLOSED`, khiến UI không thể thông báo chính xác cho giảng viên; chuyển đổi thời gian chưa ghim chuẩn múi giờ `Asia/Ho_Chi_Minh` (GMT+7).
  - **Giải pháp toàn diện đã triển khai:**
    1. **Tách Remote DTO & Xử lý Null an toàn (`RemoteDto.kt`):**
       - Tạo các DTO `RemoteSessionDto`, `RemoteStudentDto`, `RemoteProfileDto`, `RemoteAttendanceRecordDto`, v.v. với toàn bộ trường tùy chọn/nullable từ Supabase được khai báo `String? = null`.
       - Ánh xạ an toàn sang Domain Model: `subject_name` rỗng hoặc null tự động gán `"Chưa cập nhật môn học"`, `lecturer_id` null gán `""`.
       - Thêm cơ chế giải mã danh sách theo từng phần tử (`decodeListSafely`): nếu một bản ghi đơn lẻ bị lỗi format, hệ thống tự bỏ qua và tiếp tục tải các bản ghi còn lại, không làm đổ vỡ toàn bộ snapshot/dashboard.
       - Thêm thuộc tính tiện ích `AttendanceSession.displaySubject`.
    2. **Tra cứu QR đa tầng (Hỗ trợ MASV thuần, Tiền tố, Barcode):**
       - Bậc 1: Tra cứu trực tiếp theo MASV (`student_code = v_scan`).
       - Bậc 2: Tra cứu MASV sau khi cắt bỏ tiền tố `STUDENT_` nếu có (`student_code = substring(v_scan from 9)`).
       - Bậc 3: Tra cứu mã thẻ / barcode vật lý đã đăng ký (`student_cards.card_code = v_scan AND is_active`).
       - Bậc 4: Tra cứu mã thẻ kèm tiền tố `STUDENT_` (`student_cards.card_code = 'STUDENT_' || v_scan AND is_active`).
       - Kiểm tra thẻ bị vô hiệu hóa: Nếu thẻ tồn tại nhưng `is_active = false`, từ chối ngay với `INVALID_CARD`.
       - Đồng bộ 100% logic giữa PostgreSQL Migration 006 và `LocalAttendanceRepository.kt`.
    3. **Phân rã trạng thái thời gian và thông báo tiếng Việt chính xác:**
       - `OPEN` + `now < start_time` ➔ `NOT_STARTED` ("Chưa đến giờ điểm danh").
       - `OPEN` + `start_time <= now < end_time` ➔ Cho phép quét (`PRESENT` / `LATE`).
       - `OPEN` + `now >= end_time` ➔ `SESSION_EXPIRED` ("Đã hết giờ điểm danh").
       - `CLOSED` ➔ `SESSION_CLOSED` ("Buổi học đã đóng").
       - Trạng thái khác `OPEN` ➔ `SESSION_NOT_OPEN` ("Buổi học chưa được mở").
       - Cập nhật thông báo chi tiết trong `AttendanceRules.kt` và `ScannerScreen.kt` (phân biệt rõ lỗi quét, sai lớp, quét trùng, thẻ hỏng và mất kết nối mạng).
    4. **Chuẩn hóa Múi giờ Việt Nam (`Asia/Ho_Chi_Minh`):**
       - `CreateSessionScreen.kt` sử dụng `ZoneId.of("Asia/Ho_Chi_Minh")` cho cả thời điểm khởi tạo và tính toán epoch milliseconds UTC, bảo đảm buổi học tạo từ máy ảo hay thiết bị ngoài múi giờ luôn khớp giờ thực tại Việt Nam.
    5. **Tạo Migration 006 (`202610260006_fix_login_and_qr_scan.sql`):**
       - Cập nhật an toàn `app_snapshot` và `record_attendance`.
       - Phân quyền `GRANT EXECUTE ON FUNCTION public.now_ms() ... TO authenticated`.
  - **Kiểm thử & Đóng gói:**
    - Backend Integration Test: Chạy thành công toàn bộ 6 migrations (`0001` → `0006`), vượt qua tất cả 9 assertions mới của Migration 006 và 12 concurrent requests trên PostgreSQL 18.
    - Android Unit Tests: **41 / 41 ĐẠT 100%** (bao gồm `RemoteDtoDeserializationTest`, `LocalRepositoryTest`, `AttendanceRulesTest`).
    - Build APK: Đóng gói thành công `app-debug.apk` (49.1 MB).

---

## 2. Phần tồn đọng & Giới hạn môi trường

1. **Kích hoạt tài khoản Giảng viên trên Cloud:**
   - Cần tạo ít nhất một tài khoản Giảng viên (LECTURER) qua Supabase Dashboard (Authentication → Users), sau đó đăng nhập ADMIN trên ứng dụng và gạt bật "Hoạt động" cho tài khoản này để phân công lớp khi import CSV.
2. **Kiểm thử trên thiết bị phần cứng thực tế:**
   - Khả năng quét liên tục ở môi trường thực tế (độ rung tay, các mức ánh sáng phòng học, tốc độ phản hồi flash vật lý) cần nghiệm thu trực tiếp trên điện thoại thật khi bàn giao.
