# Smart Attendance — chuyển giao sửa mô hình lớp học / buổi học

## 1. Mô hình đúng

**Sinh viên (students):** `student_code` (MASV), `full_name` (Họ tên). Danh sách lớp nhập qua quan hệ `enrollments` theo `class_code`.

**Lớp học (classes):** `class_code`, `class_name`; mục đích duy nhất ở giao diện là quản lý danh sách sinh viên. Không bắt nhập môn học, không bắt nhập giảng viên, không tự sửa quyền ADMIN thành LECTURER. Các cột legacy `classes.subject_name`, `classes.lecturer_id` còn giữ trong PostgreSQL để tránh phá lịch sử nhưng UI mới KHÔNG dùng.

**Buổi học (attendance_sessions):** `class_id`, `subject_name`, `lecturer_id`, `session_name`, `session_date`, `start_time`, `late_after`, `end_time`, `status`. Chọn giảng viên ở thời điểm tạo buổi. Giảng viên phải là tài khoản Auth có profile `role='LECTURER'` và `is_active=true`; ADMIN chỉ có quyền quản lý/chọn giảng viên, không được tự chuyển role.

**Danh sách sinh viên của buổi học (session_roster):** tự chụp (snapshot) danh sách enrollment ACTIVE của lớp tại thời điểm tạo buổi học, độc lập với việc thêm/bớt sinh viên của lớp sau này. Giao diện chi tiết buổi phải liệt kê đúng roster và trạng thái điểm danh riêng của buổi.

**Demo/Online:** Demo sử dụng Room SQLite; Online sử dụng Supabase. Không trộn dữ liệu, không fallback khi mạng lỗi.

## 2. Migration Cloud có sẵn

File kèm: `202610260003_separate_classes_sessions.sql`.

- Chỉ triển khai **sau khi backup** và đã đối chiếu với schema hiện tại, trên môi trường thử nghiệm trước.
- Đòi hỏi `202610260001_initial.sql` đã áp dụng (migration 002 không bắt buộc).
- Không chạy lại migration 001, không xóa dữ liệu hoặc bảng hiện có.
- Thay đổi API hợp đồng tạo buổi (`subject_name` + `lecturer_id` bắt buộc), vì vậy **không chạy 003 trên production nếu APK cũ vẫn tạo buổi học**. Cập nhật Android rồi mới chuyển đồng bộ.
- Migration làm nullable các trường legacy ở `classes`; chuyển subject/lecturer thành cột bắt buộc ở `attendance_sessions` và backfill buổi lịch sử; tạo/sửa hàm import và permissions theo giáo viên của buổi.
- Tạo RPC `list_active_lecturers`, `admin_save_student_with_class` và cập nhật `admin_import_students` (chỉ MASV, họ tên, lớp).
- Không tạo QR production `STUDENT_<MASV>` trong migration này vì chưa xác nhận mã vạch thẻ thật.
- Bản SQL này là đề xuất từ tài liệu/schema được cung cấp; **chưa chạy trên Supabase hay PostgreSQL thực tế** trong môi trường trả lời. Agent phải chạy test/kiểm tra schema trước khi triển khai.

## 3. Công việc Android mà Agent phải làm

1. Audit mã nguồn và tất cả class/DAO/RPC/model hiện có. **Không tái tạo dự án và không dùng destructive migration.**
2. `StudentForm`: đúng **MASV, Họ tên, Lớp**, không có giảng viên hay môn học. Online gọi `admin_save_student_with_class(p_masv,p_full_name,p_class_code,p_mode)`; Demo Room tạo group (nếu chưa có), student, enrollment cùng transaction.
3. `Import CSV`: chỉ `MASV,HoTen,Lop`, tự tạo nhóm lớp chưa tồn tại, upsert thành viên không trùng, không yêu cầu `class_mappings`, môn học hay giảng viên. Online gọi `admin_import_students(payload,mode)` theo các lô có giới hạn; UI Preview và xử lý lỗi đúng.
4. `ClassList` và `ClassDetails`: hiển thị mã lớp/tên lớp và danh sách sinh viên, sĩ số. Không có giảng viên, môn học, buổi học như thuộc tính của lớp. Có thể đặt tab/đường dẫn riêng tới **Danh sách buổi học** liên quan đến lớp, nhưng không nhập chủ đề ở form lớp.
5. `SessionCreate`: bắt buộc chọn lớp, môn học, giảng viên, thời gian; tải giáo viên từ RPC `list_active_lecturers()` (chỉ id/full_name), không từ dữ liệu demo hay từ `classes.lecturer_id`. ADMIN chọn bất kỳ giảng viên ACTIVE/LECTURER; giảng viên tự tạo buổi chỉ được chọn bản thân; không giả danh giảng viên bằng đổi role.
6. `SessionDetails`: hiện tên lớp, môn học, tên giảng viên, thời gian, danh sách `session_roster`, trạng thái điểm danh. `attendance_sessions.lecturer_id` là nguồn đúng, không đọc `classes.lecturer_id`.
7. `RemoteAttendanceRepository` / DTO: cập nhật `create_attendance_session` để gửi cả `subject_name`, `lecturer_id`. Thêm `list_active_lecturers` và RPC thêm sinh viên 3 trường; xử lý thay đổi session DTO. Update serializer tương thích JSON không còn trường cũ do lớp không yêu cầu.
8. `Room` và Demo Repository: session cần có lecturer/subject; lớp không có dependency bắt buộc vào teacher/subject. Cung cấp migration Room có kiểm thử, bảo toàn SQLite demo; tránh xóa DB. Seed demo có thể tạo giáo viên và môn cho buổi, không ép vào lớp.
9. Phân quyền: kiểm tra backend theo *session lecturer*, không theo giáo viên gắn với class; teacher A không sửa/quét buổi của teacher B dù cùng lớp. ADMIN xem mọi buổi và quản lý.
10. Fix danh sách giảng viên: khi mở form tạo buổi luôn load lại và hiện loading/empty/error rõ ràng. Nếu chưa có profile ACTIVE/LECTURER, thông báo hướng dẫn tạo user ở Supabase Auth và kích hoạt profile. Kiểm tra sort, tìm kiếm và danh sách đầy đủ, không giới hạn vào teacher của lớp đã chọn.
11. Update docs/tests/APK. Chỉ báo pass những test đã thật sự chạy. Ghi snapshot trước/sau và mọi file đã thay đổi.

## 4. Kiểm thử nghiệm thu

- Thêm SV `2823250124 / Trần Quốc Bảo / PM28.01` khi Database chưa có lecturer: **thành công** và tự tạo PM28.01 nếu chưa có.
- Import lại CSV bằng SKIP không tạo trùng sinh viên/enrollment, dữ liệu cũ giữ nguyên.
- Tạo lớp PM28.02 chỉ gồm mã lớp/tên lớp, không bị hỏi môn học/giảng viên.
- Tạo buổi học yêu cầu lớp + môn + giáo viên + giờ hợp lệ; không nhận ADMIN là lecturer.
- Dropdown hiện toàn bộ profiles ACTIVE role LECTURER trên Supabase, phân biệt tên và UUID.
- Hai buổi cùng lớp khác giáo viên và môn vẫn giữ đúng người phụ trách; không rò quyền chỉnh sửa chéo.
- Thêm/bớt sinh viên khỏi lớp không thay đổi roster của buổi đã tạo.
- Scan, chống trùng, audit, thống kê, CSV, Demo/Online isolation còn hoạt động.
- Không có `student_cards` giả mạo trên môi trường Online vì import CSV không tự tạo mã thẻ.

