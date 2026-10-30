package com.smartattendance.app.domain.model

import kotlinx.serialization.Serializable

@Serializable data class Profile(val id: String, val full_name: String, val email: String, val role: String = "LECTURER", val is_active: Boolean = true, val created_at: Long = 0)
@Serializable data class Student(val id: String, val student_code: String, val full_name: String, val date_of_birth: String = "", val gender: String = "", val email: String = "", val phone: String = "", val avatar_url: String = "", val status: String = "ACTIVE", val created_at: Long = 0)
@Serializable data class StudentCard(val id: String, val student_id: String, val card_code: String, val barcode_format: String = "QR_CODE", val is_active: Boolean = true, val created_at: Long = 0)
@Serializable data class SchoolClass(
    val id: String,
    val class_code: String,
    val class_name: String,
    val subject_name: String? = null,
    val lecturer_id: String? = null,
    val semester: String = "",
    val academic_year: String = "",
    val created_at: Long = 0
)
@Serializable data class Enrollment(val id: String, val class_id: String, val student_id: String, val status: String = "ACTIVE", val created_at: Long = 0)
@Serializable data class AttendanceSession(
    val id: String,
    val class_id: String,
    val session_name: String,
    val session_date: String,
    val start_time: Long,
    val late_after: Long,
    val end_time: Long,
    val status: String = "SCHEDULED",
    val created_by: String,
    val created_at: Long = 0,
    val subject_name: String = "Chưa cập nhật môn học",
    val lecturer_id: String = ""
) {
    val displaySubject: String get() = subject_name.ifBlank { "Chưa cập nhật môn học" }
}
@Serializable data class LecturerProfile(val id: String, val full_name: String)
@Serializable data class RosterEntry(val session_id: String, val student_id: String)
@Serializable data class AttendanceRecord(val id: String, val session_id: String, val student_id: String, val status: String, val scanned_at: Long, val recorded_by: String, val method: String = "SCAN", val note: String = "", val created_at: Long = 0)
@Serializable data class AuditLog(val id: String, val actor_id: String, val action: String, val entity_type: String, val entity_id: String, val old_value: String = "", val new_value: String = "", val reason: String = "", val created_at: Long)
@Serializable data class Snapshot(
    val profiles: List<Profile> = emptyList(), val students: List<Student> = emptyList(),
    val cards: List<StudentCard> = emptyList(), val classes: List<SchoolClass> = emptyList(),
    val enrollments: List<Enrollment> = emptyList(), val sessions: List<AttendanceSession> = emptyList(),
    val roster: List<RosterEntry> = emptyList(), val records: List<AttendanceRecord> = emptyList(),
    val logs: List<AuditLog> = emptyList()
) {
    fun rows(sessionId: String): List<AttendanceRow> = roster.filter { it.session_id == sessionId }.mapNotNull { entry ->
        students.find { it.id == entry.student_id }?.let { student ->
            AttendanceRow(student, records.find { it.session_id == sessionId && it.student_id == student.id })
        }
    }.sortedBy { it.student.student_code }

    fun studentClasses(studentId: String): List<SchoolClass> =
        enrollments.filter { it.student_id == studentId && it.status == "ACTIVE" }
            .mapNotNull { e -> classes.find { it.id == e.class_id } }

    fun studentClassCodes(studentId: String): String =
        studentClasses(studentId).joinToString(", ") { it.class_code }.ifBlank { "Chưa xếp lớp" }
}
data class AttendanceRow(val student: Student, val record: AttendanceRecord?) { val status get() = record?.status ?: "ABSENT" }
@Serializable data class ScanResult(val code: String, val student: Student? = null, val record: AttendanceRecord? = null)
data class Statistics(val total: Int, val present: Int, val late: Int, val excused: Int, val absent: Int) {
    val participation: Float get() = if (total == 0) 0f else (present + late) * 100f / total
    companion object {
        fun from(rows: List<AttendanceRow>) = Statistics(rows.size, rows.count { it.status == "PRESENT" }, rows.count { it.status == "LATE" }, rows.count { it.status == "EXCUSED" }, rows.count { it.status == "ABSENT" })
    }
}

@Serializable data class ImportStudentRow(
    val masv: String,
    val hoTen: String,
    val lop: String,
    val line: Int = 0
)

@Serializable data class ClassMapping(
    val classCode: String,
    val targetClassId: String? = null,
    val className: String = "Lớp $classCode",
    val subjectName: String = "",
    val lecturerId: String = "",
    val semester: String = "1",
    val academicYear: String = "2026–2027"
)

@Serializable data class ImportPreview(
    val totalRows: Int,
    val validRows: List<ImportStudentRow>,
    val errorRows: List<String>,
    val newCount: Int,
    val existingCount: Int,
    val detectedClasses: List<String>
)

@Serializable data class ImportResult(
    val totalProcessed: Int,
    val newStudents: Int,
    val updatedStudents: Int,
    val newClasses: Int,
    val newEnrollments: Int
)

@Serializable
data class StudentItem(
    val id: String,
    val student_code: String,
    val full_name: String,
    val class_code: String = "",
    val status: String = "ACTIVE",
    val created_at: Long = 0
)

@Serializable
data class PaginatedResult<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean
)

