package com.smartattendance.app.domain.repository

import com.smartattendance.app.domain.model.*
import kotlinx.serialization.json.JsonObject

interface AttendanceRepository {
    val isDemo: Boolean
    suspend fun snapshot(): Snapshot
    suspend fun createSession(session: AttendanceSession)
    suspend fun setSessionStatus(id: String, status: String)
    suspend fun scan(sessionId: String, rawValue: String): ScanResult
    suspend fun adjust(sessionId: String, studentId: String, status: String, reason: String)
    suspend fun administer(entity: String, payload: JsonObject)
    suspend fun listActiveLecturers(): List<LecturerProfile>
    suspend fun saveStudentWithClass(masv: String, hoTen: String, classCode: String, mode: String = "UPDATE")
    suspend fun importStudents(
        students: List<ImportStudentRow>,
        mappings: List<ClassMapping> = emptyList(),
        mode: String = "SKIP"
    ): ImportResult
    suspend fun getStudentsPage(
        page: Int,
        pageSize: Int,
        search: String? = null,
        classCode: String? = null
    ): PaginatedResult<StudentItem>
    suspend fun getStudentById(
        studentId: String
    ): Student?
}
