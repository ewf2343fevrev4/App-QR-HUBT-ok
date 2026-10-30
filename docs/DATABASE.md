# Database và quy tắc lịch sử

| Bảng | Ràng buộc chính |
|---|---|
| profiles | UUID khớp Supabase Auth; role ADMIN/LECTURER; active gate |
| students | student_code unique; ACTIVE/INACTIVE |
| student_cards | card_code unique toàn hệ thống; FK student; active flag |
| classes | class_code unique; FK lecturer |
| enrollments | unique(class_id, student_id); active flag |
| attendance_sessions | FK class/creator; start ≤ late < end; 3 trạng thái |
| session_roster | PK(session_id, student_id), FK session/student |
| attendance_records | unique(session_id,student_id); FK kép roster; FK recorder |
| audit_logs | actor/action/entity/old/new/reason/time; client chỉ đọc |

Room schema v1 được KSP xuất tại `app/schemas/`. Không dùng destructive migration; nâng version sau này phải có migration và migration test. Các cột thời gian dùng epoch milliseconds UTC (Kotlin Long / PostgreSQL bigint), hiển thị theo múi giờ thiết bị. `session_date` là ngày học yyyy-MM-dd do giảng viên lựa chọn.

Khi tạo buổi, transaction sao chép các enrollment ACTIVE của sinh viên ACTIVE vào `session_roster`. Mẫu số báo cáo là số hàng roster, không phải sĩ số lớp hiện tại. Bỏ sinh viên khỏi lớp, khóa thẻ hoặc khóa hồ sơ sau này không làm đổi mẫu số cũ. Sinh viên bị khóa không thể scan; giảng viên vẫn có thể sửa bản ghi lịch sử kèm lý do.

Khoảng hợp lệ khi quét: `status=OPEN` và `start_time <= serverNow < end_time`. `serverNow >= late_after` là LATE, còn lại PRESENT. Demo dùng đồng hồ thiết bị với cùng quy tắc. Không có hàng ABSENT trong bảng record: không có record được tổng hợp là vắng; trước khi đóng buổi đây là trạng thái tạm thời. EXCUSED có record nhưng không nằm trong tử số tỷ lệ tham gia.

Buổi CLOSED không mở lại. Quản trị hỗ trợ vô hiệu hóa sinh viên, enrollment, thẻ và tài khoản để bảo toàn FK/lịch sử, thay cho xóa cứng. Đổi thẻ bằng thêm mã mới và khóa mã cũ; một mã cũ không được tái sử dụng tùy tiện.

SQL RLS SELECT được áp dụng theo lớp, profile active và ADMIN. Không có INSERT/UPDATE/DELETE grant cho client kể cả ADMIN. Các mutation chỉ qua RPC `security definer` có `search_path=''` và tên bảng fully qualified. Execute bị thu hồi khỏi PUBLIC/anon. Hàm snapshot chạy `security invoker` giữ RLS. Auth user mới luôn là LECTURER chưa kích hoạt, không tin role từ user metadata.

RPC scan khóa hàng session trước khi kiểm tra, dùng đồng hồ PostgreSQL, kiểm tra thẻ/student/roster/duplicate và insert trong cùng transaction. Khóa session đồng thời tuần tự hóa close, scan và adjustment; unique constraint thêm bảo vệ độc lập. Client không gửi `scanned_at`, `recorded_by` hay trạng thái mong muốn vào RPC scan.

Migrations không chứa credentials và không được tự động deploy. `seed.sql` cần hai lecturer đã kích hoạt; chỉ dùng trên project phát triển. Cụm PostgreSQL kiểm thử dùng Auth shim riêng; file bootstrap không phải migration production.
