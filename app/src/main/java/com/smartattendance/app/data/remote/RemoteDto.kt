package com.smartattendance.app.data.remote

import com.smartattendance.app.domain.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*

/**
 * Resilient Remote Data Transfer Objects (DTOs) for Supabase REST / RPC responses.
 * All nullable database columns and optional attributes are declared as nullable
 * with safe defaults to prevent JSON deserialization crashes (e.g., null literals
 * on legacy sessions, unassigned subjects, or missing student metadata).
 */

@Serializable
data class RemoteProfileDto(
    val id: String,
    val full_name: String = "",
    val email: String = "",
    val role: String? = null,
    val is_active: Boolean? = null,
    val created_at: Long? = null
) {
    fun toDomain(): Profile = Profile(
        id = id,
        full_name = full_name.ifBlank { email },
        email = email,
        role = role ?: "LECTURER",
        is_active = is_active ?: true,
        created_at = created_at ?: 0L
    )
}

@Serializable
data class RemoteStudentDto(
    val id: String,
    val student_code: String,
    val full_name: String,
    val date_of_birth: String? = null,
    val gender: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val avatar_url: String? = null,
    val status: String? = null,
    val created_at: Long? = null
) {
    fun toDomain(): Student = Student(
        id = id,
        student_code = student_code,
        full_name = full_name,
        date_of_birth = date_of_birth.orEmpty(),
        gender = gender.orEmpty(),
        email = email.orEmpty(),
        phone = phone.orEmpty(),
        avatar_url = avatar_url.orEmpty(),
        status = status ?: "ACTIVE",
        created_at = created_at ?: 0L
    )
}

@Serializable
data class RemoteStudentCardDto(
    val id: String,
    val student_id: String,
    val card_code: String,
    val barcode_format: String? = null,
    val is_active: Boolean? = null,
    val created_at: Long? = null
) {
    fun toDomain(): StudentCard = StudentCard(
        id = id,
        student_id = student_id,
        card_code = card_code,
        barcode_format = barcode_format ?: "QR_CODE",
        is_active = is_active ?: true,
        created_at = created_at ?: 0L
    )
}

@Serializable
data class RemoteSchoolClassDto(
    val id: String,
    val class_code: String,
    val class_name: String? = null,
    val subject_name: String? = null,
    val lecturer_id: String? = null,
    val semester: String? = null,
    val academic_year: String? = null,
    val created_at: Long? = null
) {
    fun toDomain(): SchoolClass = SchoolClass(
        id = id,
        class_code = class_code,
        class_name = class_name?.ifBlank { null } ?: class_code,
        subject_name = subject_name?.ifBlank { null },
        lecturer_id = lecturer_id?.ifBlank { null },
        semester = semester.orEmpty(),
        academic_year = academic_year.orEmpty(),
        created_at = created_at ?: 0L
    )
}

@Serializable
data class RemoteEnrollmentDto(
    val id: String,
    val class_id: String,
    val student_id: String,
    val status: String? = null,
    val created_at: Long? = null
) {
    fun toDomain(): Enrollment = Enrollment(
        id = id,
        class_id = class_id,
        student_id = student_id,
        status = status ?: "ACTIVE",
        created_at = created_at ?: 0L
    )
}

@Serializable
data class RemoteSessionDto(
    val id: String,
    val class_id: String,
    val session_name: String = "",
    val session_date: String = "",
    val start_time: Long = 0L,
    val late_after: Long = 0L,
    val end_time: Long = 0L,
    val status: String? = null,
    val created_by: String = "",
    val created_at: Long? = null,
    val subject_name: String? = null,
    val lecturer_id: String? = null
) {
    fun toDomain(): AttendanceSession = AttendanceSession(
        id = id,
        class_id = class_id,
        session_name = session_name.ifBlank { "Buổi học" },
        session_date = session_date,
        start_time = start_time,
        late_after = late_after,
        end_time = end_time,
        status = status ?: "SCHEDULED",
        created_by = created_by,
        created_at = created_at ?: 0L,
        subject_name = subject_name?.trim()?.ifBlank { null } ?: "Chưa cập nhật môn học",
        lecturer_id = lecturer_id?.trim().orEmpty()
    )
}

@Serializable
data class RemoteRosterEntryDto(
    val session_id: String,
    val student_id: String
) {
    fun toDomain(): RosterEntry = RosterEntry(session_id = session_id, student_id = student_id)
}

@Serializable
data class RemoteAttendanceRecordDto(
    val id: String,
    val session_id: String,
    val student_id: String,
    val status: String,
    val scanned_at: Long,
    val recorded_by: String,
    val method: String? = null,
    val note: String? = null,
    val created_at: Long? = null
) {
    fun toDomain(): AttendanceRecord = AttendanceRecord(
        id = id,
        session_id = session_id,
        student_id = student_id,
        status = status,
        scanned_at = scanned_at,
        recorded_by = recorded_by,
        method = method ?: "SCAN",
        note = note.orEmpty(),
        created_at = created_at ?: 0L
    )
}

@Serializable
data class RemoteAuditLogDto(
    val id: String,
    val actor_id: String,
    val action: String,
    val entity_type: String,
    val entity_id: String,
    val old_value: String? = null,
    val new_value: String? = null,
    val reason: String? = null,
    val created_at: Long
) {
    fun toDomain(): AuditLog = AuditLog(
        id = id,
        actor_id = actor_id,
        action = action,
        entity_type = entity_type,
        entity_id = entity_id,
        old_value = old_value.orEmpty(),
        new_value = new_value.orEmpty(),
        reason = reason.orEmpty(),
        created_at = created_at
    )
}

@Serializable
data class RemoteScanResultDto(
    val code: String,
    val student: RemoteStudentDto? = null,
    val record: RemoteAttendanceRecordDto? = null
) {
    fun toDomain(): ScanResult = ScanResult(
        code = code,
        student = student?.toDomain(),
        record = record?.toDomain()
    )
}

@Serializable
data class RemoteStudentItemDto(
    val id: String,
    val student_code: String,
    val full_name: String,
    val class_code: String? = null,
    val status: String? = null,
    val created_at: Long? = null
) {
    fun toDomain(): StudentItem = StudentItem(
        id = id,
        student_code = student_code,
        full_name = full_name,
        class_code = class_code.orEmpty(),
        status = status ?: "ACTIVE",
        created_at = created_at ?: 0L
    )
}

@Serializable
data class RemotePaginatedResultDto(
    val items: List<RemoteStudentItemDto> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 10,
    val totalItems: Int = 0,
    val totalPages: Int = 1,
    val hasNextPage: Boolean = false,
    val hasPreviousPage: Boolean = false
) {
    fun toDomain(): PaginatedResult<StudentItem> = PaginatedResult(
        items = items.map { it.toDomain() },
        page = page,
        pageSize = pageSize,
        totalItems = totalItems,
        totalPages = totalPages,
        hasNextPage = hasNextPage,
        hasPreviousPage = hasPreviousPage
    )
}

/**
 * Safely decodes a JsonArray of items element-by-element so that an anomaly
 * in a single corrupted or malformed item does not abort loading the entire dataset.
 */
inline fun <reified T> JsonElement?.decodeListSafely(
    json: Json,
    deserializer: KSerializer<T>,
    crossinline mapToDomain: (T) -> Any
): List<Any> {
    if (this !is JsonArray) return emptyList()
    val results = ArrayList<Any>(this.size)
    for (elem in this) {
        try {
            val item = json.decodeFromJsonElement(deserializer, elem)
            results.add(mapToDomain(item))
        } catch (_: Exception) {
            // Silently drop single unparsable item or record to preserve system availability
        }
    }
    return results
}

