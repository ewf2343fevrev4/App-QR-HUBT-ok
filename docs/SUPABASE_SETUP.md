# Thiết lập Online Mode (Supabase Cloud)

1. Tạo Supabase project phát triển. Lưu URL và Publishable / Anon key trong cấu hình cục bộ `supabase.properties`.
2. **Triển khai Database Schema & Functions (Không xóa hay reset Database):**
   - **Bước 2a (Khởi tạo Schema):** Trong **SQL Editor** của Supabase Dashboard, chạy nội dung tệp [`supabase/migrations/202610260001_initial.sql`](file:///C:/Users/TIGER/Desktop/ST2/supabase/migrations/202610260001_initial.sql). (Đã chạy thành công trên Cloud).
   - **Bước 2b (Chức năng Nhập sinh viên & Ánh xạ lớp):** Trong **SQL Editor**, chạy tiếp nội dung tệp [`supabase/migrations/202610260002_import_students.sql`](file:///C:/Users/TIGER/Desktop/ST2/supabase/migrations/202610260002_import_students.sql).
     > **Lưu ý an toàn:** Migration này sử dụng `CREATE OR REPLACE FUNCTION` độc lập, an toàn tuyệt đối, không DROP bảng, không TRUNCATE và không làm mất bất kỳ dữ liệu nào đã có trên Cloud.

3. Trong **Authentication → Users**, tạo tài khoản quản trị đầu tiên bằng email của bạn. Trigger tạo profile ở trạng thái chưa hoạt động. Kích hoạt quyền `ADMIN` bằng lệnh SQL trong SQL Editor:

```sql
update public.profiles
set role='ADMIN', is_active=true, full_name='Quản trị hệ thống'
where id='UUID_AUTH_CUA_ADMIN';
```

4. **Tạo và kích hoạt tài khoản Giảng viên (LECTURER):**
   - Vào **Authentication → Users**, tạo một hoặc nhiều tài khoản giảng viên (ví dụ: `giangvien1@domain.com`).
   - Mở ứng dụng Android, đăng nhập bằng tài khoản ADMIN.
   - Vào **Cài đặt → Quản trị dữ liệu → Phân hệ: Tài khoản**, chọn tài khoản vừa tạo:
     - Gạt switch **Hoạt động** sang Bật (`is_active = true`).
     - Vai trò mặc định là `LECTURER`.
   - *Nguyên tắc bảo mật:* Hệ thống nghiêm cấm tự gán ADMIN làm giảng viên hoặc suy đoán môn học. Do đó, cần có ít nhất một tài khoản Giảng viên đang hoạt động để phân công phụ trách lớp học khi nhập CSV.

5. Cấu hình tệp `supabase.properties` tại thư mục gốc dự án:

```properties
SUPABASE_URL=https://fpnvxjvpeaulvxdwktdz.supabase.co
SUPABASE_ANON_KEY=sb_publishable_9KAjKbP8kfeAj1jlF_30UQ_BqEvenjl
```
*(Hỗ trợ cả định dạng `NEXT_PUBLIC_SUPABASE_URL` và `sb_publishable_...`)*

6. Build và cài đặt ứng dụng:
   - Đăng xuất Demo (nếu đang ở Demo), đăng nhập bằng email và mật khẩu của tài khoản ADMIN đã kích hoạt.
   - Nhìn thấy banner trên cùng: `ONLINE • admin@...` màu tím.

7. **Quy trình Nhập sinh viên hàng loạt (Bulk Import CSV) trên Online Mode:**
   - Tại màn hình Quản trị, bấm **Nhập từ CSV**.
   - Chọn tệp từ bộ nhớ (hoặc tệp mẫu `tinhoc28.csv`).
   - Xem trước: Hệ thống phân tích số dòng, số sinh viên mới/cũ, số lớp phát hiện.
   - **Ánh xạ môn học & giảng viên:**
     - Với các lớp đã tồn tại trong Database: Tự động giữ nguyên thông tin môn học và giảng viên, chỉ thêm sinh viên vào danh sách lớp (`enrollments`).
     - Với các lớp mới: Admin nhập tên Môn học phân công (bắt buộc) và chọn Giảng viên phụ trách từ danh sách các tài khoản `LECTURER` đang hoạt động (không tự gán ADMIN).
   - Chọn chế độ trùng:
     - `SKIP`: Giữ nguyên bản ghi cũ nếu đã có.
     - `UPDATE`: Cập nhật họ tên sinh viên theo file mới, bảo toàn lịch sử điểm danh và mã thẻ.
     - `INSERT`: Chỉ thêm sinh viên mới.
   - Bấm **Tiến hành nhập**: Dữ liệu được đẩy lên máy chủ và lưu nguyên tử qua RPC `admin_import_students`.

## Nghiệm thu trên project thật

- Với lecturer A và B ở hai lớp: A không nhìn thấy lớp, roster và records của B.
- Gửi insert REST trực tiếp vào attendance_records: phải bị từ chối, kể cả token ADMIN.
- Scan thẻ hợp lệ, sai lớp, bị khóa, trùng; kiểm tra giờ do server ghi.
- Hai thiết bị quét cùng một thẻ/buổi: chỉ có một record.
- Tắt mạng khi quét: không được báo thành công nếu không có phản hồi. Bật mạng và làm mới/quét lại.
- Thu hồi/disable profile, hết hạn access token, refresh token, đăng xuất; kiểm tra session recovery.
- Điều chỉnh điểm danh phải có audit; đổi enrollment không làm đổi sĩ số buổi cũ.

Kiểm thử PostgreSQL cục bộ đã kiểm tra phần SQL nhưng không xác minh hạ tầng Auth, TLS, PostgREST, email hoặc cấu hình project thật. Những bước này vẫn phải được chạy sau khi có credentials.

Nguồn chính thức: [Database functions](https://supabase.com/docs/guides/database/functions), [Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security), [API keys](https://supabase.com/docs/guides/api/api-keys).
