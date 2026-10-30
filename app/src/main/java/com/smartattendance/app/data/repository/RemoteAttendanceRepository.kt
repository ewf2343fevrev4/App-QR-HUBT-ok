package com.smartattendance.app.data.repository

import com.smartattendance.app.data.remote.*
import com.smartattendance.app.domain.model.*
import com.smartattendance.app.domain.repository.AttendanceRepository
import kotlinx.serialization.json.*

class RemoteAttendanceRepository(private val api: SupabaseApi) : AttendanceRepository {
    override val isDemo = false

    override suspend fun snapshot(): Snapshot {
        val root = api.rpc("app_snapshot").jsonObject
        @Suppress("UNCHECKED_CAST")
        val profiles = (root["profiles"]?.decodeListSafely(api.json, RemoteProfileDto.serializer()) { it.toDomain() } ?: emptyList()) as List<Profile>
        @Suppress("UNCHECKED_CAST")
        val students = (root["students"]?.decodeListSafely(api.json, RemoteStudentDto.serializer()) { it.toDomain() } ?: emptyList()) as List<Student>
        @Suppress("UNCHECKED_CAST")
        val cards = (root["cards"]?.decodeListSafely(api.json, RemoteStudentCardDto.serializer()) { it.toDomain() } ?: emptyList()) as List<StudentCard>
        @Suppress("UNCHECKED_CAST")
        val classes = (root["classes"]?.decodeListSafely(api.json, RemoteSchoolClassDto.serializer()) { it.toDomain() } ?: emptyList()) as List<SchoolClass>
        @Suppress("UNCHECKED_CAST")
        val enrollments = (root["enrollments"]?.decodeListSafely(api.json, RemoteEnrollmentDto.serializer()) { it.toDomain() } ?: emptyList()) as List<Enrollment>
        @Suppress("UNCHECKED_CAST")
        val sessions = (root["sessions"]?.decodeListSafely(api.json, RemoteSessionDto.serializer()) { it.toDomain() } ?: emptyList()) as List<AttendanceSession>
        @Suppress("UNCHECKED_CAST")
        val roster = (root["roster"]?.decodeListSafely(api.json, RemoteRosterEntryDto.serializer()) { it.toDomain() } ?: emptyList()) as List<RosterEntry>
        @Suppress("UNCHECKED_CAST")
        val records = (root["records"]?.decodeListSafely(api.json, RemoteAttendanceRecordDto.serializer()) { it.toDomain() } ?: emptyList()) as List<AttendanceRecord>
        @Suppress("UNCHECKED_CAST")
        val logs = (root["logs"]?.decodeListSafely(api.json, RemoteAuditLogDto.serializer()) { it.toDomain() } ?: emptyList()) as List<AuditLog>

        return Snapshot(
            profiles = profiles,
            students = students,
            cards = cards,
            classes = classes,
            enrollments = enrollments,
            sessions = sessions,
            roster = roster,
            records = records,
            logs = logs
        )
    }

    override suspend fun createSession(session: AttendanceSession) {
        api.rpc("create_attendance_session", buildJsonObject {
            put("payload", api.json.encodeToJsonElement(session))
        })
    }

    override suspend fun setSessionStatus(id: String, status: String) {
        api.rpc("set_session_status", buildJsonObject {
            put("p_session_id", id)
            put("p_status", status)
        })
    }

    override suspend fun scan(sessionId: String, rawValue: String): ScanResult {
        val res = api.rpc("record_attendance", buildJsonObject {
            put("p_session_id", sessionId)
            put("p_card_code", rawValue)
        })
        val dto = api.json.decodeFromJsonElement<RemoteScanResultDto>(res)
        return dto.toDomain()
    }

    override suspend fun adjust(sessionId: String, studentId: String, status: String, reason: String) {
        api.rpc("adjust_attendance", buildJsonObject {
            put("p_session_id", sessionId)
            put("p_student_id", studentId)
            put("p_status", status)
            put("p_reason", reason)
        })
    }

    override suspend fun administer(entity: String, payload: JsonObject) {
        api.rpc("admin_save", buildJsonObject {
            put("p_entity", entity)
            put("payload", payload)
        })
    }

    override suspend fun listActiveLecturers(): List<LecturerProfile> {
        val result = api.rpc("list_active_lecturers")
        return api.json.decodeFromJsonElement<List<LecturerProfile>>(result)
    }

    override suspend fun saveStudentWithClass(masv: String, hoTen: String, classCode: String, mode: String) {
        api.rpc("admin_save_student_with_class", buildJsonObject {
            put("p_masv", masv.trim())
            put("p_full_name", hoTen.trim())
            put("p_class_code", classCode.trim())
            put("p_mode", mode)
        })
    }

    override suspend fun importStudents(
        students: List<ImportStudentRow>,
        mappings: List<ClassMapping>,
        mode: String
    ): ImportResult {
        // Chunk requests to avoid request body size / timeout limits on large CSV files
        val chunks = students.chunked(250)
        var totalProcessed = 0
        var newStudents = 0
        var updatedStudents = 0
        var newClasses = 0
        var newEnrollments = 0

        for (chunk in chunks) {
            val body = buildJsonObject {
                put("mode", mode)
                put("payload", buildJsonObject {
                    put("students", JsonArray(chunk.map {
                        buildJsonObject {
                            put("masv", it.masv.trim())
                            put("ho_ten", it.hoTen.trim())
                            put("lop", it.lop.trim())
                        }
                    }))
                })
            }
            val res = api.json.decodeFromJsonElement<ImportResult>(api.rpc("admin_import_students", body))
            totalProcessed += res.totalProcessed
            newStudents += res.newStudents
            updatedStudents += res.updatedStudents
            newClasses += res.newClasses
            newEnrollments += res.newEnrollments
        }

        return ImportResult(
            totalProcessed = totalProcessed,
            newStudents = newStudents,
            updatedStudents = updatedStudents,
            newClasses = newClasses,
            newEnrollments = newEnrollments
        )
    }

    override suspend fun getStudentsPage(
        page: Int,
        pageSize: Int,
        search: String?,
        classCode: String?
    ): PaginatedResult<StudentItem> {
        val safePage = maxOf(1, page)
        val safeSize = pageSize.coerceIn(1, 100)
        val cleanSearch = search?.trim()?.takeIf { it.isNotBlank() }
        val cleanClass = classCode?.trim()?.takeIf { it.isNotBlank() && it != "Tất cả" }

        val body = buildJsonObject {
            put("p_page", safePage)
            put("p_page_size", safeSize)
            cleanSearch?.let { put("p_search", it) }
            cleanClass?.let { put("p_class_code", it) }
        }
        val result = api.rpc("admin_get_students_page", body)
        val dto = api.json.decodeFromJsonElement<RemotePaginatedResultDto>(result)
        return dto.toDomain()
    }

    override suspend fun getStudentById(studentId: String): Student? {
        val body = buildJsonObject {
            put("p_student_id", studentId)
        }
        val result = api.rpc("get_student_by_id", body)
        if (result is JsonNull) return null
        val dto = api.json.decodeFromJsonElement<RemoteStudentDto>(result)
        return dto.toDomain()
    }
}
