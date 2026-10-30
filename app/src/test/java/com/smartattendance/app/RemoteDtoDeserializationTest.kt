package com.smartattendance.app

import com.smartattendance.app.data.remote.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RemoteDtoDeserializationTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `session with null subject_name and null lecturer_id deserializes safely with fallback`() {
        val payload = """
            {
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "class_id": "550e8400-e29b-41d4-a716-446655440001",
                "session_name": "Lập trình Web",
                "session_date": "2026-10-26",
                "start_time": 1700000000000,
                "late_after": 1700000900000,
                "end_time": 1700007200000,
                "status": "OPEN",
                "created_by": "00000000-0000-0000-0000-000000000001",
                "created_at": 1700000000000,
                "subject_name": null,
                "lecturer_id": null
            }
        """.trimIndent()

        val elem = json.parseToJsonElement(payload)
        val dto = json.decodeFromJsonElement<RemoteSessionDto>(elem)
        val domain = dto.toDomain()

        assertEquals("550e8400-e29b-41d4-a716-446655440000", domain.id)
        assertEquals("Chưa cập nhật môn học", domain.subject_name)
        assertEquals("Chưa cập nhật môn học", domain.displaySubject)
        assertEquals("", domain.lecturer_id)
    }

    @Test
    fun `student with null optional fields deserializes safely`() {
        val payload = """
            {
                "id": "10000000-0000-0000-0000-000000000001",
                "student_code": "2823156783",
                "full_name": "Nguyễn Văn Tú",
                "date_of_birth": null,
                "gender": null,
                "email": null,
                "phone": null,
                "avatar_url": null,
                "status": null,
                "created_at": null
            }
        """.trimIndent()

        val elem = json.parseToJsonElement(payload)
        val dto = json.decodeFromJsonElement<RemoteStudentDto>(elem)
        val domain = dto.toDomain()

        assertEquals("2823156783", domain.student_code)
        assertEquals("Nguyễn Văn Tú", domain.full_name)
        assertEquals("", domain.date_of_birth)
        assertEquals("", domain.gender)
        assertEquals("", domain.email)
        assertEquals("", domain.phone)
        assertEquals("ACTIVE", domain.status)
    }

    @Test
    fun `scan result with null student or record does not fail`() {
        val payload = """
            {
                "code": "SESSION_EXPIRED",
                "student": null,
                "record": null
            }
        """.trimIndent()

        val elem = json.parseToJsonElement(payload)
        val dto = json.decodeFromJsonElement<RemoteScanResultDto>(elem)
        val domain = dto.toDomain()

        assertEquals("SESSION_EXPIRED", domain.code)
        assertNull(domain.student)
        assertNull(domain.record)
    }

    @Test
    fun `decodeListSafely drops malformed item without crashing whole list`() {
        val array = JsonArray(listOf(
            JsonObject(mapOf(
                "id" to JsonPrimitive("1"),
                "student_code" to JsonPrimitive("202610001"),
                "full_name" to JsonPrimitive("Sinh viên 1")
            )),
            JsonObject(mapOf(
                // Missing required fields
                "invalid_field" to JsonPrimitive(123)
            )),
            JsonObject(mapOf(
                "id" to JsonPrimitive("2"),
                "student_code" to JsonPrimitive("202610002"),
                "full_name" to JsonPrimitive("Sinh viên 2")
            ))
        ))

        @Suppress("UNCHECKED_CAST")
        val students = array.decodeListSafely(json, RemoteStudentDto.serializer()) { it.toDomain() } as List<com.smartattendance.app.domain.model.Student>

        assertEquals(2, students.size)
        assertEquals("202610001", students[0].student_code)
        assertEquals("202610002", students[1].student_code)
    }
}

