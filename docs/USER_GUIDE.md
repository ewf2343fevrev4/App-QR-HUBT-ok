# Hướng dẫn sử dụng

## Demo

Mở app → **Vào Demo • Quản trị viên**. Banner `CHẾ ĐỘ DEMO – DỮ LIỆU CỤC BỘ` luôn hiển thị. Hai nút demo giảng viên cho phép kiểm tra phân quyền: Minh Anh có CNTT01/02, Hoàng Nam có CNTT03. Dữ liệu các vai trò cùng nằm trong một Room database.

## Buổi học và quét thẻ

1. Chọn Lớp học → lớp → Tạo buổi học mới (hoặc nút Tạo buổi ở tổng quan).
2. Điền tên, ngày yyyy-MM-dd, giờ HH:mm. Bắt đầu ≤ mốc đi muộn < kết thúc. Múi giờ hiện trên form. Chọn mở ngay hoặc lên lịch.
3. Ở chi tiết buổi, chọn Mở điểm danh nếu đang lên lịch. Scan chỉ được ghi khi đã đến giờ bắt đầu và chưa hết giờ.
4. Chọn Quét thẻ, cấp quyền camera. Đặt QR hoặc mã vạch vào khung, giữ thẻ rõ nét. Flash chỉ bật được nếu camera hỗ trợ.
5. Chờ thông báo kết quả; thành công có họ tên/mã/giờ/trạng thái và âm/rung. Đưa thẻ tiếp theo vào khung. Lặp lại cùng thẻ không tạo bản ghi mới.
6. Chọn Xem danh sách điểm danh để kiểm tra số lượng. Chỉ đóng buổi khi đã hoàn tất; CLOSED không mở lại.

Nếu từ chối quyền, bấm Cấp quyền camera hoặc Mở cài đặt ứng dụng khi hệ điều hành không hiện hộp thoại nữa. Camera bị ứng dụng khác chiếm hoặc không có camera sau sẽ có thông báo, không tự báo đã quét.

## Bộ thẻ thử

Ở CNTT01 / buổi Thực hành quét thẻ:

| Ảnh | Kỳ vọng |
|---|---|
| 202610001–202610020.png | Có mặt hoặc đi muộn tùy thời gian |
| 202610021–202610029.png | Sai lớp |
| 202610030.png | Sinh viên bị vô hiệu hóa |
| invalid-card.png | Mã thẻ không hợp lệ |
| code128-202610001.png | Cùng thẻ sinh viên 202610001 |
| code39-202610001.png | Mã thẻ thay thế của 202610001 |
| ean13-202610002.png | Mã thẻ thay thế của 202610002 |

Thử quét lại mã đã thành công để kiểm tra DUPLICATE. Nội dung `202610001` đơn thuần không phải thẻ đã đăng ký; QR phải chứa `STUDENT_202610001`.

## Sửa điểm danh và báo cáo

Chọn một sinh viên trong danh sách buổi → chọn Có mặt/Đi muộn/Có phép/Vắng → nhập lý do ít nhất 3 ký tự → Lưu & ghi lịch sử. Chọn Vắng xóa record hiện tại nhưng giữ audit. Không thể sửa người ngoài roster.

Báo cáo → chọn lớp, ngày và buổi → xem thống kê → Xuất CSV → chọn vị trí lưu bằng hộp thoại Android. CSV có BOM, dấu ngoặc kép/escape đúng chuẩn, chống công thức Excel. Nếu hủy hộp thoại, app không báo đã xuất. Mẫu số là sĩ số chốt khi tạo buổi. Tỷ lệ tham gia = (Có mặt + Muộn) / tổng sĩ số; có phép vẫn nằm trong mẫu số.

## Quản trị

Quản trị viên vào Cài đặt → Quản trị dữ liệu:

- Sinh viên: thêm/sửa hồ sơ, khóa bằng công tắc Hoạt động.
- Lớp học: thêm/sửa môn, mã, học kỳ và giảng viên.
- Thẻ: chọn sinh viên, nhập **đúng rawValue** và định dạng; thay thẻ bằng vô hiệu thẻ cũ rồi thêm thẻ mới.
- Danh sách lớp: thêm/sửa quan hệ sinh viên–lớp, dùng công tắc để rút khỏi lớp. Không làm thay đổi buổi đã tạo.
- Tài khoản: Demo có thể tạo hồ sơ demo; Online tạo Auth user trong Supabase Dashboard rồi kích hoạt/phân quyền tại đây.
- Lịch sử thay đổi: chọn mục để xem giá trị trước/sau và người thao tác.

## Cài đặt

Chọn giao diện Sáng/Tối/Theo hệ thống. Đăng xuất để đổi tài khoản hoặc chế độ. Khôi phục demo yêu cầu xác nhận và xóa toàn bộ thay đổi **trong database demo**. Không tác động Supabase. Gỡ ứng dụng/xóa dữ liệu Android cũng xóa Room demo; tệp CSV đã lưu qua SAF vẫn nằm ở vị trí đã chọn.
