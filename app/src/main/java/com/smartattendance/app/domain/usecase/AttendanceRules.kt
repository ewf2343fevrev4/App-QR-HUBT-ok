package com.smartattendance.app.domain.usecase

import com.smartattendance.app.domain.model.*

object AttendanceRules {
    fun canManage(actor: Profile, schoolClass: SchoolClass) = actor.is_active && (actor.role == "ADMIN" || schoolClass.lecturer_id == actor.id)

    fun canManageSession(actor: Profile, session: AttendanceSession) =
        actor.is_active && (actor.role == "ADMIN" || (actor.role == "LECTURER" && session.lecturer_id == actor.id))

    fun validateSession(session: AttendanceSession) {
        require(session.session_name.isNotBlank()) { "Vui lòng nhập tên buổi học." }
        require(session.subject_name.isNotBlank()) { "Vui lòng nhập tên môn học." }
        require(session.lecturer_id.isNotBlank()) { "Vui lòng chọn giảng viên phụ trách." }
        require(session.start_time <= session.late_after && session.late_after < session.end_time) { "Giờ bắt đầu ≤ giờ đi muộn < giờ kết thúc." }
        require(session.status in setOf("SCHEDULED", "OPEN")) { "Trạng thái buổi mới không hợp lệ." }
    }

    fun evaluate(actor: Profile, session: AttendanceSession, student: Student?, cardActive: Boolean, enrolled: Boolean, duplicate: Boolean, now: Long): String = when {
        !canManageSession(actor, session) -> "FORBIDDEN"
        session.status == "CLOSED" -> "SESSION_CLOSED"
        session.status != "OPEN" -> "SESSION_NOT_OPEN"
        now < session.start_time -> "NOT_STARTED"
        now >= session.end_time -> "SESSION_EXPIRED"
        student == null || !cardActive -> "INVALID_CARD"
        student.status != "ACTIVE" -> "INACTIVE_STUDENT"
        !enrolled -> "WRONG_CLASS"
        duplicate -> "DUPLICATE"
        now >= session.late_after -> "LATE"
        else -> "PRESENT"
    }

    fun evaluate(actor: Profile, schoolClass: SchoolClass, session: AttendanceSession, student: Student?, cardActive: Boolean, enrolled: Boolean, duplicate: Boolean, now: Long): String =
        evaluate(actor, session.copy(lecturer_id = session.lecturer_id.ifBlank { schoolClass.lecturer_id ?: "" }), student, cardActive, enrolled, duplicate, now)
}
fun statusLabel(status: String) = when(status) {
    "PRESENT" -> "Có mặt"; "LATE" -> "Đi muộn"; "EXCUSED" -> "Có phép"; "ABSENT" -> "Vắng"
    "OPEN" -> "Đang mở"; "CLOSED" -> "Đã đóng"; "SCHEDULED" -> "Đã lên lịch"
    "ACTIVE" -> "Hoạt động"; "INACTIVE" -> "Vô hiệu hóa"; "ADMIN" -> "Quản trị viên"; else -> "Giảng viên"
}
fun scanMessage(code: String) = when(code) {
    "PRESENT" -> "Điểm danh thành công (Đúng giờ)"
    "LATE" -> "Điểm danh thành công (Đi muộn)"
    "DUPLICATE" -> "Sinh viên đã được điểm danh trước đó"
    "INVALID_CARD" -> "Không tìm thấy sinh viên hoặc mã thẻ không hợp lệ"
    "WRONG_CLASS" -> "Sinh viên không thuộc danh sách buổi học này"
    "INACTIVE_STUDENT" -> "Hồ sơ sinh viên đang bị vô hiệu hóa"
    "NOT_STARTED" -> "Chưa đến giờ điểm danh"
    "SESSION_EXPIRED" -> "Đã hết giờ điểm danh"
    "SESSION_CLOSED" -> "Buổi học đã đóng"
    "SESSION_NOT_OPEN" -> "Buổi học chưa được mở"
    "FORBIDDEN" -> "Bạn không có quyền điểm danh buổi học này"
    "NETWORK_ERROR" -> "Mất kết nối máy chủ"
    else -> "Chưa ghi nhận được điểm danh"
}
