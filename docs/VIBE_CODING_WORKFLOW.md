# Nhật ký quy trình Vibe Coding (VIBE_CODING_WORKFLOW)

**Dự án:** Smart Attendance – Điểm danh thông minh  
**Đề tài:** Xây dựng chương trình điểm danh sinh viên qua mã vạch trên thẻ sinh viên  
**Phương pháp:** Vibe Coding với Autonomous Coding Agent (AI-assisted Pair Programming)  
**Tác giả & Đội ngũ:** Senior Android Engineer & AI Coding Agent (OpenAI Codex khởi tạo → Google Antigravity / Gemini 3.8 Flash High tiếp quản & hoàn thiện)  
**Thời gian thực hiện:** 26/10/2026 (Asia/Bangkok)

---

## 1. Tổng quan phương pháp Vibe Coding trong dự án

Phương pháp **Vibe Coding** trong dự án này được định nghĩa là sự cộng tác giữa kỹ sư phần mềm (Human Architect / Supervisor) và AI Coding Agent (Autonomous Developer) theo chu trình khép kín:

$$\text{Analyze} \longrightarrow \text{Plan} \longrightarrow \text{Implement} \longrightarrow \text{Build} \longrightarrow \text{Test} \longrightarrow \text{Debug} \longrightarrow \text{Verify} \longrightarrow \text{Document} \longrightarrow \text{Deliver}$$

**Nguyên tắc cốt lõi đã áp dụng:**
1. **Không tạo bản mô phỏng (No Mock/Dummy):** Toàn bộ mã nguồn Kotlin, Room Database, CameraX, ML Kit và PostgreSQL RPC đều là mã chạy thực tế, không dùng dữ liệu giả lập tĩnh trong mã production.
2. **Kế thừa và không phá vỡ (Non-destructive inheritance):** Khi chuyển giao từ Codex sang Antigravity, toàn bộ mã nguồn đang hoạt động được kế thừa 100%, bảo toàn cấu trúc Database và không xóa dự án làm lại từ đầu.
3. **Kiểm chứng thực nghiệm (Empirical Verification):** Không bao giờ báo cáo thành công trước khi có kết quả chạy thực tế từ Compiler, Test Runner, Lint hoặc adb logcat.

---

## 2. Các công cụ và mô hình AI được sử dụng thực tế

| Giai đoạn | Công cụ / AI Model | Nhiệm vụ chính |
|---|---|---|
| **Phase 0 – 8 (Khởi tạo dự án)** | OpenAI Codex | Thiết lập cấu trúc dự án Gradle, triển khai Jetpack Compose screens, Room entities/DAO, CameraX + ML Kit integration, Supabase migration SQL, bộ dữ liệu mẫu 34 mã barcode bằng ZXing. |
| **Phase 9 – 10 (Tiếp quản, Sửa lỗi, Kiểm thử & Đóng gói)** | Google Antigravity (Gemini 3.8 Flash High) | Audit toàn bộ workspace, phát hiện và sửa lỗi biên dịch Gradle Kotlin DSL, bổ sung bộ unit test `SessionAndDaoTest`, điều phối chạy 23 unit tests, 5 Android connected instrumentation tests trên Emulator Android 15 (API 35), chạy kịch bản 12 concurrent requests trên PostgreSQL 18, clean build APK và cài đặt nghiệm thu trên thiết bị thật. |

---

## 3. Các quyết định kiến trúc quan trọng (Architectural Decisions)

Trong quá trình Vibe Coding, AI và kỹ sư đã thống nhất các quyết định kỹ thuật nền tảng:

### 3.1. Thiết kế Offline-First độc lập (Local Demo Mode)
- **Vấn đề:** Dự án đại học cần demo mượt mà ngay tại hội đồng chấm hoặc phòng lab không có kết nối Internet hoặc chưa có Supabase credentials.
- **Quyết định:** Sử dụng Room Database (SQLite) làm nguồn dữ liệu cục bộ hoàn chỉnh cho Demo Mode, tách biệt rõ ràng qua interface `AttendanceRepository`. Không phụ thuộc vào Supabase trong chế độ Demo nhưng giữ nguyên cấu trúc dữ liệu tương thích 1:1.

### 3.2. Chốt sĩ số lịch sử qua bảng `session_roster`
- **Vấn đề:** Nếu một sinh viên bị xóa khỏi lớp hoặc bị vô hiệu hóa thẻ ở tuần thứ 5, báo cáo của các buổi học từ tuần 1 đến tuần 4 sẽ bị sai lệch mẫu số nếu chỉ đếm sĩ số lớp hiện tại.
- **Quyết định:** Tại thời điểm tạo buổi học (`createSession`), sao chép toàn bộ sinh viên đang hoạt động của lớp vào bảng `session_roster`. Mọi thống kê chuyên cần lịch sử đều lấy mẫu số từ `session_roster`, đảm bảo tính toàn vẹn dữ liệu học thuật.

### 3.3. Chống trùng lặp đa tầng (Multi-tier Duplicate Prevention)
- **Vấn đề:** Camera quét với tốc độ 30 khung hình/giây có thể kích hoạt nhiều sự kiện quét cho cùng một thẻ trong tích tắc, hoặc hai thiết bị quét cùng một thẻ đồng thời.
- **Quyết định:**
  1. *Presentation layer:* `ScannerViewModel` có cờ `busy` và cooldown 2500ms đối với cùng một mã thẻ.
  2. *Application layer:* `AppViewModel` sử dụng Kotlin Coroutine `Mutex` tuần tự hóa các mutation.
  3. *Persistence layer (Local):* Bảng `attendance_records` có ràng buộc duy nhất cấp SQLite `UNIQUE(session_id, student_id)`.
  4. *Backend layer (Supabase):* Hàm RPC `record_attendance` khóa hàng buổi học bằng `SELECT ... FOR UPDATE`, kiểm tra và insert trong cùng một transaction nguyên tử.

### 3.4. Quản lý vòng đời CameraX và ML Kit
- **Vấn đề:** Rò rỉ bộ nhớ (Memory Leak) hoặc treo luồng UI khi phân tích luồng ảnh video liên tục.
- **Quyết định:** Áp dụng `STRATEGY_KEEP_ONLY_LATEST`, sử dụng `SingleThreadExecutor` cho luồng phân tích hình ảnh, đảm bảo đóng `ImageProxy` trong mọi nhánh xử lý (`onSuccess`, `onFailure`, `onComplete`). Tự động giải phóng Camera Provider và ML Kit scanner khi Composable rời khỏi Composition qua `DisposableEffect`.

### 3.5. An toàn xuất file CSV qua Storage Access Framework (SAF)
- **Vấn đề:** Không xin quyền `MANAGE_EXTERNAL_STORAGE` nguy hiểm; bảo đảm tiếng Việt có dấu hiển thị đúng trong Microsoft Excel; chống tấn công CSV Formula Injection.
- **Quyết định:** Dùng `ActivityResultContracts.CreateDocument("text/csv")` để người dùng chọn vị trí lưu. Đầu file thêm tiền tố UTF-8 BOM (`\uFEFF`). Bất kỳ ô dữ liệu nào bắt đầu bằng `=`, `+`, `-`, `@` đều được thêm dấu `'` ở đầu và bọc trong dấu ngoặc kép chuẩn RFC 4180.

---

## 4. Các lỗi thực tế phát hiện và cách xử lý (Troubleshooting Log)

Trong quá trình AI coding, nhiều lỗi kỹ thuật thực tế đã xuất hiện và được chẩn đoán, xử lý triệt để:

### Lỗi 1: Unresolved reference `util` trong `app/build.gradle.kts`
- **Hiện tượng:** Chạy `.\gradlew.bat testDebugUnitTest` báo lỗi:
  ```text
  e: file:///.../app/build.gradle.kts:15:17: Unresolved reference: util
  String(java.util.Base64.getUrlDecoder().decode(...))
  ```
- **Nguyên nhân gốc:** Trong Gradle Kotlin DSL, `java` là từ khóa trỏ đến `JavaPluginExtension` của dự án Gradle, làm lu mờ gói `java` của JDK.
- **Khắc phục:** Thêm `import java.util.Base64` ở đầu file cấu hình và gọi trực tiếp `Base64.getUrlDecoder()`.

### Lỗi 2: JUnit 4 `InvalidTestClassError: Method ... should be void`
- **Hiện tượng:** Khi viết test mới `SessionAndDaoTest`, phương thức test khai báo dạng expression body `= runBlocking { assertThrows(...) }` bị JUnit 4 từ chối chạy.
- **Nguyên nhân gốc:** `assertThrows` trả về instance `Throwable`, dẫn đến phương thức test có kiểu trả về không phải `void` (JUnit 4 bắt buộc `@Test` phải trả về `void`).
- **Khắc phục:** Chuyển đổi thành block body `{ runBlocking { ... } }` để hàm có kiểu trả về `Unit`.

### Lỗi 3: Room `@Upsert` nuốt ngoại lệ Unique Index
- **Hiện tượng:** Khi kiểm tra chèn thẻ sinh viên trùng `card_code` qua `dao.save(duplicateCard)` mong đợi ném `SQLiteConstraintException`, nhưng không có ngoại lệ nào xảy ra.
- **Nguyên nhân gốc:** `@Upsert` trong Room bắt `SQLiteConstraintException` với giả định là xung đột Khóa Chính và tự động fallback sang `UPDATE` theo PK. Khi ID chưa tồn tại, câu lệnh update ảnh hưởng 0 hàng mà không báo lỗi.
- **Khắc phục:** Sử dụng `@Insert` trên các entity có ràng buộc kiểm thử duy nhất (như `RosterEntity` và `RecordEntity`), xác nhận ngoại lệ `SQLiteConstraintException` được kích hoạt chính xác.

### Lỗi 4: Khóa file Windows khi chạy Clean Build (`app\build` lock)
- **Hiện tượng:** Chạy `clean :app:assembleDebug` thất bại với thông báo:
  ```text
  java.io.IOException: Unable to delete directory '...\app\build\intermediates\lint-cache\...'
  ```
- **Nguyên nhân gốc:** Trên Windows, tiến trình Gradle Daemon giữ file handle đối với các file JAR trong `lint-cache` sau khi chạy `lintDebug`.
- **Khắc phục:** Thực thi `.\gradlew.bat --stop` để giải phóng Gradle Daemons trước khi chạy Clean Build hoàn chỉnh.

### Lỗi 5: `FileNotFoundException` khi chạy Unit Test đọc tệp CSV mẫu từ thư mục con `app/`
- **Hiện tượng:** Khi chạy `./gradlew.bat testDebugUnitTest`, test tìm kiếm `sample-data/tinhoc28.csv` bị văng ngoại lệ `FileNotFoundException`.
- **Nguyên nhân gốc:** Working directory của subproject test trong Gradle là `app/`, trong khi thư mục `sample-data/` nằm ở workspace root `ST2/`.
- **Khắc phục:** Viết hàm trợ giúp `resolveTinhoc28File()` tra cứu `System.getProperty("projectRoot")` hoặc các đường dẫn tương đối fallback (`../sample-data/tinhoc28.csv`, `sample-data/tinhoc28.csv`).

### Lỗi 6: Header UTF-8 BOM và dòng trống trong `tinhoc28.csv`
- **Hiện tượng:** Cột đầu tiên bị dính ký tự `\uFEFF` khiến việc so sánh tên cột `header.equals("MASV")` trả về `false`, và dòng thứ hai chứa `,,` bị parse thành bản ghi sinh viên rỗng.
- **Nguyên nhân gốc:** Tệp CSV xuất từ Excel có BOM `0xEF, 0xBB, 0xBF` và dòng trống ngay sau tiêu đề.
- **Khắc phục:** Xử lý `trim()` ký tự `\uFEFF` ở đầu header, đồng thời kiểm tra và bỏ qua dòng trống hoặc dòng chỉ chứa dấu phẩy/khoảng trắng.

---

## 5. Bằng chứng kiểm chứng của lập trình viên (Verification Matrix)

| Hạng mục | Công cụ kiểm chứng | Kết quả | Bằng chứng |
|---|---|---|---|
| Môi trường phát triển | `CHECK_ENV.bat` | ĐẠT | Java 17.0.8, Android SDK 35, Build Tools 35.0.0, adb, Gradle wrapper sẵn sàng. |
| Logic nghiệp vụ & Room | `testDebugUnitTest` | ĐẠT (31/31 tests) | Xem XML kết quả tại `app/build/test-results/testDebugUnitTest/`. Bổ sung kiểm thử CSV Importer và Bulk Import Repository. |
| UI & ML Kit trên thiết bị | `connectedDebugAndroidTest` | ĐẠT (5/5 tests) | Chạy trên `SmartAttendance_Test(AVD)` Android 15 (API 35). Báo cáo tại `app/build/reports/androidTests/connected/debug/index.html`. |
| Nhập CSV 1.084 SV & 35 lớp | Kiểm thử trực tiếp Emulator | ĐẠT | Cài đặt `app-debug.apk` (43.1 MB) lên Medium Phone API 37, nhập 1.084 SV thành công (tổng 1.114 SV, 38 lớp). Tìm kiếm MASV `2823250124` (Trần Quốc Bảo - PM28.01). |
| Concurrency & RLS Database | `scripts/test-backend.ps1` | ĐẠT | Cụm PostgreSQL 18 cục bộ, chạy cả 2 migration `0001` và `0002_import_students`, 12 worker đồng thời xác nhận khóa hàng an toàn. |
| Android Lint | `lintDebug` | ĐẠT (0 errors) | Báo cáo tại `app/build/reports/lint-results-debug.html`. |
| Đóng gói APK | `assembleDebug` | ĐẠT | File `app-debug.apk` (43.1 MB) tại `app/build/outputs/apk/debug/`. |
| Cài đặt & chạy thật | `adb install -r` & `am start` | ĐẠT | Streamed Install thành công; chụp ảnh màn hình Admin, CSV Preview, Search kết quả tại `docs/screenshots/`. |

---

## 6. Bài học kinh nghiệm cho đề tài Vibe Coding

1. **AI Agent hiệu quả nhất khi có ràng buộc kiến trúc rõ ràng:** Việc quy định trước các nguyên tắc (MVVM, Room KSP, offline-first, RLS) giúp AI sinh mã nhất quán, tránh tình trạng viết toàn bộ logic vào Composable hay MainActivity.
2. **Kiểm thử tự động là chốt chặn quan trọng nhất của Vibe Coding:** Không bao giờ chấp nhận kết quả do AI tự báo cáo nếu không có log thực thi từ terminal. Việc chạy Robolectric và Android Instrumentation Tests giúp phát hiện ngay lập tức các lỗi logic tiềm ẩn.
3. **Mô hình AI mới (Gemini 3.8 Flash High) có khả năng tự sửa lỗi sâu:** Khi gặp lỗi hệ thống (như xung đột từ khóa Gradle Kotlin DSL hay cơ chế `@Upsert` của Room), Agent có khả năng tự đọc stack trace, phân tích mã nguồn bytecode/generated code và áp dụng giải pháp tối ưu mà không cần người dùng gợi ý cách sửa.

