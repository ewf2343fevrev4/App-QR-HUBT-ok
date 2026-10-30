# Giao thức Android ↔ Supabase

Android dùng HTTPS với header `apikey: <publishable/anon key>` và `Authorization: Bearer <user access token>`. Không có service-role key ở client. Payload và phản hồi là JSON snake_case; các model trong `domain/model/Models.kt` là hợp đồng dữ liệu.

## Authentication

- `POST /auth/v1/token?grant_type=password`: email/password, nhận access/refresh token và user id.
- `POST /auth/v1/token?grant_type=refresh_token`: tự refresh khi khôi phục phiên hoặc gặp 401.
- `POST /auth/v1/logout?scope=local`: thu hồi phiên hiện tại; luôn xóa token cục bộ.
- Sau login/refresh, phải tải `app_snapshot` và tìm profile đang active. Chưa active không được vào UI Online.

## RPC tại `/rest/v1/rpc/`

| Hàm | JSON đầu vào | Kết quả |
|---|---|---|
| app_snapshot | `{}` | profiles, students, cards, classes, enrollments, sessions, roster, records, logs theo RLS |
| create_attendance_session | `{payload: AttendanceSession}` | void; server ghi creator/time, tạo roster |
| set_session_status | `{p_session_id, p_status}` | void; OPEN hoặc CLOSED |
| record_attendance | `{p_session_id, p_card_code}` | `{code, student?, record?}` |
| adjust_attendance | `{p_session_id,p_student_id,p_status,p_reason}` | void; audit cùng transaction |
| admin_save | `{p_entity,payload}` | void; allowlist students/classes/student_cards/enrollments/profiles |

Profile chỉ được cập nhật nếu tài khoản Auth đã tồn tại. Email profile Online không được sửa qua admin_save. `payload.id` là UUID; các field mặc định vẫn phải được encode (Android `encodeDefaults=true`).

## Scan codes

- PRESENT/LATE: đã commit, trả student + record.
- DUPLICATE: bản ghi đã tồn tại, trả bản ghi hiện hữu, không đổi giờ hoặc trạng thái.
- INVALID_CARD: không khớp thẻ active (không suy đoán mã sinh viên).
- INACTIVE_STUDENT: hồ sơ bị vô hiệu hóa.
- WRONG_CLASS: không có trong roster của buổi.
- SESSION_CLOSED: chưa mở, đã đóng hoặc ngoài cửa sổ thời gian.
- FORBIDDEN: không xác thực/không có quyền lớp; không tiết lộ student.

Không truyền thông tin sinh viên của lớp khác khi sai lớp. Lỗi HTTP, I/O, TLS, timeout không bao giờ chuyển thành kết quả thành công. Một timeout có thể xảy ra sau khi server đã commit; cần tải lại hoặc quét lại để nhận DUPLICATE, không tự chèn record mới.

Không có API sync Demo → Online. Không lưu pending scan để phát lại ngầm.
