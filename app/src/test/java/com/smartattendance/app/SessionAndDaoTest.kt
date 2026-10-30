package com.smartattendance.app

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartattendance.app.data.local.*
import com.smartattendance.app.data.repository.LocalAttendanceRepository
import com.smartattendance.app.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SessionAndDaoTest {
    private lateinit var db: AttendanceDatabase
    private lateinit var repo: LocalAttendanceRepository
    private val now = 1_700_000_000_000L
    private val sessionId = "40000000-0000-0000-0000-000000000004"

    @Before
    fun setup() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AttendanceDatabase::class.java).build()
            DemoSeed.populate(db, now)
            repo = LocalAttendanceRepository(db, DemoSeed.ADMIN) { now }
        }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `direct dao foreign key constraint enforces valid class and creator on session`() {
        val invalidSession = SessionEntity(
            AttendanceSession(
                id = UUID.randomUUID().toString(),
                class_id = "non-existent-class-id",
                session_name = "Buổi lỗi FK",
                session_date = "2026-10-26",
                start_time = now,
                late_after = now + 900_000,
                end_time = now + 5_400_000,
                status = "OPEN",
                created_by = DemoSeed.ADMIN,
                created_at = now,
                subject_name = "Môn học",
                lecturer_id = DemoSeed.LECTURER
            )
        )
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.dao().save(invalidSession) }
        }
    }

    @Test
    fun `direct dao primary key constraint enforces distinct roster entry`() {
        val duplicateRoster = RosterEntity(RosterEntry(sessionId, DemoSeed.studentId(1)))
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.dao().insert(duplicateRoster) }
        }
    }

    @Test
    fun `createSession rejects past end time and invalid boundaries`() {
        val pastSession = AttendanceSession(
            id = UUID.randomUUID().toString(),
            class_id = DemoSeed.classId(1),
            session_name = "Buổi quá khứ",
            session_date = "2026-10-26",
            start_time = now - 7_200_000,
            late_after = now - 3_600_000,
            end_time = now - 1_000,
            status = "OPEN",
            created_by = DemoSeed.ADMIN,
            subject_name = "Môn học",
            lecturer_id = DemoSeed.LECTURER
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.createSession(pastSession) }
        }

        val invalidBoundarySession = AttendanceSession(
            id = UUID.randomUUID().toString(),
            class_id = DemoSeed.classId(1),
            session_name = "Buổi sai mốc giờ",
            session_date = "2026-10-26",
            start_time = now + 3_600_000,
            late_after = now + 1_800_000, // late_after < start_time
            end_time = now + 7_200_000,
            status = "SCHEDULED",
            created_by = DemoSeed.ADMIN,
            subject_name = "Môn học",
            lecturer_id = DemoSeed.LECTURER
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.createSession(invalidBoundarySession) }
        }
    }

    @Test
    fun `non admin role cannot administer entities`() {
        val lecturerRepo = LocalAttendanceRepository(db, DemoSeed.LECTURER) { now }
        val payload = buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("student_code", "202619999")
            put("full_name", "Sinh viên mới")
            put("status", "ACTIVE")
            put("created_at", now)
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { lecturerRepo.administer("students", payload) }
        }
    }

    @Test
    fun `scan non-existent session returns SESSION_CLOSED`() {
        runBlocking {
            val result = repo.scan("non-existent-session-id", "STUDENT_202610001")
            assertEquals("SESSION_CLOSED", result.code)
            assertNull(result.student)
            assertNull(result.record)
        }
    }

    @Test
    fun `removeRecord directly deletes record while keeping roster`() {
        runBlocking {
            repo.scan(sessionId, "STUDENT_202610001")
            assertEquals(1, repo.snapshot().records.count { it.session_id == sessionId })
            db.dao().removeRecord(sessionId, DemoSeed.studentId(1))
            assertEquals(0, repo.snapshot().records.count { it.session_id == sessionId })
            // Roster entry remains intact
            assertEquals(20, repo.snapshot().roster.count { it.session_id == sessionId })
        }
    }
}

