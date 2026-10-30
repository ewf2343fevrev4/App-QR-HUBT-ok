package com.smartattendance.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartattendance.app.core.StudentCsvImporter
import com.smartattendance.app.data.local.*
import com.smartattendance.app.data.repository.LocalAttendanceRepository
import com.smartattendance.app.domain.model.AttendanceSession
import com.smartattendance.app.domain.model.ClassMapping
import com.smartattendance.app.domain.model.ImportStudentRow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class BulkImportRepositoryTest {

    private lateinit var db: AttendanceDatabase
    private lateinit var repo: LocalAttendanceRepository
    private val now = 1_700_000_000_000L

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

    private fun resolveTinhoc28File(): File {
        val root = System.getProperty("projectRoot")?.let { File(it, "sample-data/tinhoc28.csv") }
        if (root != null && root.exists()) return root
        val direct = File("sample-data/tinhoc28.csv")
        if (direct.exists()) return direct
        val parent = File("../sample-data/tinhoc28.csv")
        if (parent.exists()) return parent
        error("Could not find sample-data/tinhoc28.csv")
    }

    @Test
    fun `bulk import 1084 students from tinhoc28 csv populates students classes and enrollments`() {
        runBlocking {
            val file = resolveTinhoc28File()
            val (validRows, _) = StudentCsvImporter.parse(file.inputStream())
            assertEquals(1084, validRows.size)

            val initialStudentCount = repo.snapshot().students.size // 30 demo students
            val initialClassCount = repo.snapshot().classes.size // 3 demo classes

            val result = repo.importStudents(validRows, mode = "SKIP")

            assertEquals(1084, result.totalProcessed)
            assertEquals(1084, result.newStudents)
            assertEquals(0, result.updatedStudents)
            assertEquals(35, result.newClasses)
            assertEquals(1084, result.newEnrollments)

            val snapshot = repo.snapshot()
            assertEquals(initialStudentCount + 1084, snapshot.students.size)
            assertEquals(initialClassCount + 35, snapshot.classes.size)

            // Verify first student was created and properly enrolled
            val first = snapshot.students.find { it.student_code == "2823250124" }
            assertNotNull("Student 2823250124 should exist", first)
            assertEquals("Trần Quốc Bảo", first!!.full_name)

            val pm2801 = snapshot.classes.find { it.class_code == "PM28.01" }
            assertNotNull("Class PM28.01 should exist", pm2801)

            val enrollment = snapshot.enrollments.find { it.class_id == pm2801!!.id && it.student_id == first.id }
            assertNotNull("Student should be enrolled in PM28.01", enrollment)

            // Verify demo card was automatically generated
            val card = snapshot.cards.find { it.card_code == "STUDENT_2823250124" }
            assertNotNull("Card STUDENT_2823250124 should exist", card)
            assertEquals(first.id, card!!.student_id)
        }
    }

    @Test
    fun `re-importing same csv produces zero duplicates`() {
        runBlocking {
            val sampleRows = listOf(
                ImportStudentRow("2823000001", "Học Sinh A", "PM28.01"),
                ImportStudentRow("2823000002", "Học Sinh B", "UD28.01")
            )

            val firstResult = repo.importStudents(sampleRows, mode = "SKIP")
            assertEquals(2, firstResult.newStudents)
            assertEquals(2, firstResult.newClasses)

            // Second import of the identical rows in SKIP mode
            val secondResult = repo.importStudents(sampleRows, mode = "SKIP")
            assertEquals(0, secondResult.newStudents)
            assertEquals(0, secondResult.newClasses)
            assertEquals(0, secondResult.newEnrollments)
            assertEquals(0, secondResult.updatedStudents)
        }
    }

    @Test
    fun `import in UPDATE mode modifies student name without affecting attendance history`() {
        runBlocking {
            val sampleRows = listOf(
                ImportStudentRow("2823000001", "Tên Ban Đầu", "PM28.01")
            )
            repo.importStudents(sampleRows, mode = "SKIP")

            val studentId = repo.snapshot().students.first { it.student_code == "2823000001" }.id
            val classId = repo.snapshot().classes.first { it.class_code == "PM28.01" }.id

            // Create a session and check in
            val session = AttendanceSession(
                id = "test-session-123",
                class_id = classId,
                session_name = "Buổi học kiểm thử",
                session_date = "2026-10-26",
                start_time = now - 1000,
                late_after = now + 900_000,
                end_time = now + 3_600_000,
                status = "OPEN",
                created_by = DemoSeed.ADMIN,
                subject_name = "Tin học đại cương",
                lecturer_id = DemoSeed.LECTURER
            )
            repo.createSession(session)
            val scanRes = repo.scan(session.id, "STUDENT_2823000001")
            assertEquals("PRESENT", scanRes.code)

            // Now re-import with updated name in UPDATE mode
            val updatedRows = listOf(
                ImportStudentRow("2823000001", "Tên Đã Cập Nhật", "PM28.01")
            )
            val updateResult = repo.importStudents(updatedRows, mode = "UPDATE")
            assertEquals(1, updateResult.updatedStudents)
            assertEquals(0, updateResult.newStudents)

            // Verify name was updated
            val updatedStudent = repo.snapshot().students.first { it.student_code == "2823000001" }
            assertEquals("Tên Đã Cập Nhật", updatedStudent.full_name)

            // Verify attendance history remains unchanged
            val record = repo.snapshot().records.find { it.session_id == session.id && it.student_id == studentId }
            assertNotNull("Attendance record must be preserved", record)
            assertEquals("PRESENT", record!!.status)
        }
    }

    @Test
    fun `saveStudentWithClass creates student and auto creates class`() {
        runBlocking {
            repo.saveStudentWithClass("2823250124", "Trần Quốc Bảo", "PM28.01")
            val snapshot = repo.snapshot()
            val cls = snapshot.classes.find { it.class_code == "PM28.01" }
            assertNotNull("Class PM28.01 should be auto-created", cls)
            val st = snapshot.students.find { it.student_code == "2823250124" }
            assertNotNull("Student should be created", st)
            assertEquals("Trần Quốc Bảo", st!!.full_name)
            val enrolled = snapshot.enrollments.any { it.class_id == cls!!.id && it.student_id == st.id }
            assertTrue("Student should be enrolled in PM28.01", enrolled)
        }
    }

    @Test
    fun `createSession rejects ADMIN as lecturer or empty subject`() {
        runBlocking {
            val classId = repo.snapshot().classes.first().id
            val invalidLecturerSession = AttendanceSession(
                id = "invalid-lec-session",
                class_id = classId,
                session_name = "Buổi lỗi giảng viên",
                session_date = "2026-10-26",
                start_time = now - 1000,
                late_after = now + 900_000,
                end_time = now + 3_600_000,
                status = "OPEN",
                created_by = DemoSeed.ADMIN,
                subject_name = "Tin học",
                lecturer_id = DemoSeed.ADMIN // Admin cannot be lecturer
            )
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repo.createSession(invalidLecturerSession) }
            }

            val emptySubjectSession = invalidLecturerSession.copy(
                lecturer_id = DemoSeed.LECTURER,
                subject_name = "" // Empty subject rejected
            )
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.createSession(emptySubjectSession) }
            }
        }
    }

    @Test
    fun `non admin user cannot execute bulk import`() {
        runBlocking {
            val lecturerRepo = LocalAttendanceRepository(db, DemoSeed.LECTURER) { now }
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    lecturerRepo.importStudents(
                        listOf(ImportStudentRow("2823999999", "Nguyễn Lỗi", "PM28.01")),
                        mode = "SKIP"
                    )
                }
            }
        }
    }

    @Test
    fun `import in UPDATE mode correctly transfers student to new class and preserves past attendance`() {
        runBlocking {
            val initialRow = listOf(ImportStudentRow("2823000099", "Tran Van Chuyen", "PM28.01"))
            repo.importStudents(initialRow, mode = "SKIP")

            val studentId = repo.snapshot().students.first { it.student_code == "2823000099" }.id
            val pm2801 = repo.snapshot().classes.first { it.class_code == "PM28.01" }

            // Create past session in PM28.01 and scan
            val session = AttendanceSession(
                id = "transfer-session-1",
                class_id = pm2801.id,
                session_name = "Buổi học lớp PM28.01",
                session_date = "2026-10-26",
                start_time = now - 1000,
                late_after = now + 900_000,
                end_time = now + 3_600_000,
                status = "OPEN",
                created_by = DemoSeed.ADMIN,
                subject_name = "Lập trình Android",
                lecturer_id = DemoSeed.LECTURER
            )
            repo.createSession(session)
            val scanRes = repo.scan(session.id, "STUDENT_2823000099")
            assertEquals("PRESENT", scanRes.code)

            // Re-import with new class PM28.02 in UPDATE mode
            val transferRow = listOf(ImportStudentRow("2823000099", "Tran Van Chuyen", "PM28.02"))
            val updateRes = repo.importStudents(transferRow, mode = "UPDATE")
            assertEquals(1, updateRes.newEnrollments)

            val snapshot = repo.snapshot()
            val pm2802 = snapshot.classes.first { it.class_code == "PM28.02" }

            val enrollment01 = snapshot.enrollments.find { it.class_id == pm2801.id && it.student_id == studentId }
            val enrollment02 = snapshot.enrollments.find { it.class_id == pm2802.id && it.student_id == studentId }

            assertNotNull(enrollment01)
            assertEquals("INACTIVE", enrollment01!!.status)

            assertNotNull(enrollment02)
            assertEquals("ACTIVE", enrollment02!!.status)

            // Past attendance record is intact
            val record = snapshot.records.find { it.session_id == session.id && it.student_id == studentId }
            assertNotNull(record)
            assertEquals("PRESENT", record!!.status)

            // Re-importing identical data in UPDATE mode produces 0 duplicates
            val reimportRes = repo.importStudents(transferRow, mode = "UPDATE")
            assertEquals(0, reimportRes.newStudents)
            assertEquals(0, reimportRes.updatedStudents)
        }
    }
}
