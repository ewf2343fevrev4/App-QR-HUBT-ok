package com.smartattendance.app.data.repository

import androidx.room.withTransaction
import com.smartattendance.app.data.local.*
import com.smartattendance.app.domain.model.*
import com.smartattendance.app.domain.repository.AttendanceRepository
import com.smartattendance.app.domain.usecase.AttendanceRules
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID

class LocalAttendanceRepository(
    private val db: AttendanceDatabase,
    private val actorId: String,
    private val clock: () -> Long = System::currentTimeMillis
) : AttendanceRepository {
    override val isDemo = true
    private val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private suspend fun actor() = dao.profiles().firstOrNull { it.value.id == actorId && it.value.is_active }?.value
        ?: error("Tài khoản không hoạt động.")

    override suspend fun snapshot(): Snapshot = db.withTransaction {
        val user = actor()
        val admin = user.role == "ADMIN"
        // In migration 003: active lecturers can view classes (class is student group)
        val classes = dao.classes().map { it.value }.filter { admin || user.role == "LECTURER" }
        val sessions = dao.sessions().map { it.value }.filter { admin || it.lecturer_id == user.id }
        val sessionIds = sessions.map { it.id }.toSet()
        val classIds = classes.map { it.id }.toSet()
        val enrollments = dao.enrollments().map { it.value }.filter { admin || it.class_id in classIds }
        val roster = dao.roster().map { it.value }.filter { admin || it.session_id in sessionIds }
        val records = dao.records().map { it.value }.filter { admin || it.session_id in sessionIds }
        val studentIds = (enrollments.map { it.student_id } + roster.map { it.student_id }).toSet()
        val students = dao.students().map { it.value }.filter { admin || it.id in studentIds }
        val cards = dao.cards().map { it.value }.filter { admin || it.student_id in studentIds }
        val logs = dao.logs().map { it.value }.filter { admin || it.actor_id == user.id }

        Snapshot(
            profiles = dao.profiles().map { it.value }.filter { admin || it.id == user.id },
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

    override suspend fun createSession(session: AttendanceSession) = db.withTransaction {
        val user = actor()
        AttendanceRules.validateSession(session)
        require(session.end_time > clock()) { "Giờ kết thúc phải ở tương lai." }
        require(dao.sessions().none { it.value.id == session.id }) { "Buổi học đã tồn tại." }
        val targetClass = dao.classes().firstOrNull { it.value.id == session.class_id }?.value
            ?: error("Không tìm thấy lớp.")
        val lecturerProfile = dao.profiles().firstOrNull { it.value.id == session.lecturer_id && it.value.is_active && it.value.role == "LECTURER" }?.value
            ?: error("Giảng viên phụ trách không hợp lệ hoặc chưa được kích hoạt vai trò LECTURER.")
        if (user.role != "ADMIN") {
            check(user.role == "LECTURER" && session.lecturer_id == user.id) {
                "Giảng viên chỉ có thể tạo buổi học do chính mình phụ trách."
            }
        }
        val actual = session.copy(
            created_by = user.id,
            created_at = clock(),
            subject_name = session.subject_name.trim(),
            lecturer_id = lecturerProfile.id
        )
        dao.save(SessionEntity(actual))
        val active = dao.students().filter { it.value.status == "ACTIVE" }.map { it.value.id }.toSet()
        dao.enrollments().map { it.value }
            .filter { it.class_id == session.class_id && it.status == "ACTIVE" && it.student_id in active }
            .forEach { dao.insert(RosterEntity(RosterEntry(session.id, it.student_id))) }
        audit("CREATE_SESSION", "attendance_sessions", session.id, "", json.encodeToString(actual), "")
    }

    override suspend fun setSessionStatus(id: String, status: String) = db.withTransaction {
        val old = dao.sessions().first { it.value.id == id }.value
        val user = actor()
        check(AttendanceRules.canManageSession(user, old)) { "Không có quyền quản lý buổi học." }
        require(status in setOf("OPEN", "CLOSED") && old.status != "CLOSED") { "Buổi đã đóng không thể mở lại." }
        if (status == "OPEN") require(clock() < old.end_time) { "Buổi học đã hết thời gian." }
        val updated = old.copy(status = status)
        dao.save(SessionEntity(updated))
        audit("SESSION_STATUS", "attendance_sessions", id, json.encodeToString(old), json.encodeToString(updated), "")
    }

    override suspend fun scan(sessionId: String, rawValue: String): ScanResult = db.withTransaction {
        val session = dao.sessions().firstOrNull { it.value.id == sessionId }?.value
            ?: return@withTransaction ScanResult("SESSION_CLOSED")
        val user = actor()
        val now = clock()

        val scanCode = rawValue.trim()
        if (scanCode.isEmpty() || scanCode.length > 512) {
            return@withTransaction ScanResult("INVALID_CARD")
        }

        // Check if an issued card has been explicitly deactivated/revoked
        val directCard = dao.cards().firstOrNull { it.value.card_code == scanCode }?.value
        if (directCard != null && !directCard.is_active) {
            return@withTransaction ScanResult("INVALID_CARD")
        }
        if (directCard == null && !scanCode.startsWith("STUDENT_")) {
            val prefixedCard = dao.cards().firstOrNull { it.value.card_code == "STUDENT_$scanCode" }?.value
            if (prefixedCard != null && !prefixedCard.is_active) {
                return@withTransaction ScanResult("INVALID_CARD")
            }
        }

        // Multi-strategy Student Resolution:
        // 1. Direct student_code match (Raw MASV on physical student card)
        var student = dao.students().firstOrNull { it.value.student_code == scanCode }?.value

        // 2. Direct student_code match stripping 'STUDENT_' prefix
        if (student == null && scanCode.startsWith("STUDENT_")) {
            val stripped = scanCode.removePrefix("STUDENT_")
            student = dao.students().firstOrNull { it.value.student_code == stripped }?.value
        }

        // 3. Registered card / barcode in student_cards
        if (student == null) {
            student = directCard?.let { c -> dao.students().firstOrNull { it.value.id == c.student_id }?.value }
        }

        // 4. Registered card in student_cards with 'STUDENT_' prefix
        if (student == null) {
            val card = dao.cards().firstOrNull { it.value.card_code == "STUDENT_$scanCode" && it.value.is_active }?.value
            student = card?.let { c -> dao.students().firstOrNull { it.value.id == c.student_id }?.value }
        }

        val existing = dao.records().firstOrNull { it.value.session_id == sessionId && it.value.student_id == student?.id }?.value
        val enrolled = dao.roster().any { it.value.session_id == sessionId && it.value.student_id == student?.id }

        val status = AttendanceRules.evaluate(
            actor = user,
            session = session,
            student = student,
            cardActive = (student != null),
            enrolled = enrolled,
            duplicate = (existing != null),
            now = now
        )

        if (status !in setOf("PRESENT", "LATE")) {
            return@withTransaction ScanResult(
                code = status,
                student = if (status == "DUPLICATE") student else null,
                record = if (status == "DUPLICATE") existing else null
            )
        }
        val record = AttendanceRecord(UUID.randomUUID().toString(), sessionId, student!!.id, status, now, user.id, "SCAN", "", now)
        dao.insert(RecordEntity(record))
        ScanResult(status, student, record)
    }

    override suspend fun adjust(sessionId: String, studentId: String, status: String, reason: String) = db.withTransaction {
        val session = dao.sessions().first { it.value.id == sessionId }.value
        val user = actor()
        check(AttendanceRules.canManageSession(user, session)) { "Không có quyền quản lý buổi học." }
        require(reason.trim().length >= 3) { "Cần lý do điều chỉnh ít nhất 3 ký tự." }
        require(status in setOf("PRESENT", "LATE", "EXCUSED", "ABSENT"))
        require(dao.roster().any { it.value.session_id == sessionId && it.value.student_id == studentId }) { "Sinh viên không thuộc buổi học." }
        val old = dao.records().firstOrNull { it.value.session_id == sessionId && it.value.student_id == studentId }?.value
        val updated = if (status == "ABSENT") null else AttendanceRecord(old?.id ?: UUID.randomUUID().toString(), sessionId, studentId, status, old?.scanned_at ?: clock(), actorId, "MANUAL", reason.trim(), old?.created_at ?: clock())
        if (updated == null) dao.removeRecord(sessionId, studentId) else dao.save(RecordEntity(updated))
        audit("ADJUST_ATTENDANCE", "attendance_records", "$sessionId/$studentId", json.encodeToString(old), json.encodeToString(updated), reason.trim())
    }

    override suspend fun administer(entity: String, payload: JsonObject) = db.withTransaction {
        check(actor().role == "ADMIN") { "Chức năng chỉ dành cho quản trị viên." }
        val id = payload["id"]?.jsonPrimitive?.content ?: error("Thiếu ID.")
        var before = ""
        when (entity) {
            "students" -> {
                val item = json.decodeFromJsonElement<Student>(payload)
                require(item.student_code.matches(Regex("[A-Za-z0-9_-]{3,40}")) && item.full_name.isNotBlank()) { "Mã sinh viên hoặc họ tên không hợp lệ." }
                require(item.status in setOf("ACTIVE", "INACTIVE"))
                before = dao.students().find { it.value.id == id }?.let { json.encodeToString(it.value) } ?: ""
                dao.save(StudentEntity(item))
            }
            "classes" -> {
                val item = json.decodeFromJsonElement<SchoolClass>(payload)
                require(item.class_code.isNotBlank()) { "Mã lớp không được để trống." }
                val savedClass = item.copy(
                    class_name = item.class_name.ifBlank { item.class_code },
                    subject_name = item.subject_name?.takeIf { it.isNotBlank() },
                    lecturer_id = item.lecturer_id?.takeIf { it.isNotBlank() }
                )
                before = dao.classes().find { it.value.id == id }?.let { json.encodeToString(it.value) } ?: ""
                dao.save(ClassEntity(savedClass))
            }
            "student_cards" -> {
                val item = json.decodeFromJsonElement<StudentCard>(payload)
                require(item.card_code.isNotBlank() && item.card_code.length <= 512) { "Mã thẻ cần từ 1 đến 512 ký tự." }
                require(item.barcode_format in setOf("QR_CODE", "CODE_128", "CODE_39", "EAN_13"))
                before = dao.cards().find { it.value.id == id }?.let { json.encodeToString(it.value) } ?: ""
                dao.save(CardEntity(item))
            }
            "enrollments" -> {
                val item = json.decodeFromJsonElement<Enrollment>(payload)
                require(item.status in setOf("ACTIVE", "INACTIVE"))
                before = dao.enrollments().find { it.value.id == id }?.let { json.encodeToString(it.value) } ?: ""
                dao.save(EnrollmentEntity(item))
            }
            "profiles" -> {
                val item = json.decodeFromJsonElement<Profile>(payload)
                require(item.full_name.isNotBlank() && item.email.contains("@") && item.role in setOf("ADMIN", "LECTURER"))
                require(item.id != actorId || (item.role == "ADMIN" && item.is_active)) { "Không thể tự vô hiệu hóa hoặc hạ quyền tài khoản quản trị hiện tại." }
                before = dao.profiles().find { it.value.id == id }?.let { json.encodeToString(it.value) } ?: ""
                dao.save(ProfileEntity(item))
            }
            else -> error("Loại dữ liệu không được hỗ trợ.")
        }
        audit("ADMIN_SAVE", entity, id, before, payload.toString(), "Quản lý dữ liệu")
    }

    override suspend fun listActiveLecturers(): List<LecturerProfile> = db.withTransaction {
        val user = actor()
        check(user.role == "ADMIN" || (user.role == "LECTURER" && user.is_active)) { "Không có quyền." }
        dao.profiles().map { it.value }
            .filter { it.is_active && it.role == "LECTURER" }
            .sortedBy { it.full_name }
            .map { LecturerProfile(it.id, it.full_name) }
    }

    override suspend fun saveStudentWithClass(
        masv: String,
        hoTen: String,
        classCode: String,
        mode: String
    ) = db.withTransaction {
        check(actor().role == "ADMIN") { "Chức năng chỉ dành cho quản trị viên." }
        val code = masv.trim()
        val name = hoTen.trim()
        val group = classCode.trim()
        require(code.matches(Regex("[A-Za-z0-9_-]{3,40}"))) { "Mã sinh viên phải từ 3 đến 40 ký tự." }
        require(name.isNotBlank()) { "Họ tên sinh viên không được để trống." }
        require(group.isNotBlank()) { "Lớp sinh viên không được để trống." }

        val now = clock()
        var cls = dao.classes().firstOrNull { it.value.class_code == group }?.value
        if (cls == null) {
            cls = SchoolClass(
                id = UUID.randomUUID().toString(),
                class_code = group,
                class_name = group,
                created_at = now
            )
            dao.save(ClassEntity(cls))
        }

        val existing = dao.students().firstOrNull { it.value.student_code == code }?.value
        val studentId: String
        if (existing == null) {
            studentId = UUID.randomUUID().toString()
            val newStudent = Student(id = studentId, student_code = code, full_name = name, status = "ACTIVE", created_at = now)
            dao.save(StudentEntity(newStudent))
            dao.save(CardEntity(StudentCard(UUID.randomUUID().toString(), studentId, "STUDENT_$code", created_at = now)))
        } else {
            studentId = existing.id
            if (mode == "INSERT") error("Sinh viên có mã $code đã tồn tại.")
            if (mode == "UPDATE" && existing.full_name != name) {
                dao.save(StudentEntity(existing.copy(full_name = name)))
            }
        }

        if (mode == "UPDATE") {
            dao.enrollments().map { it.value }
                .filter { it.student_id == studentId && it.class_id != cls.id && it.status == "ACTIVE" }
                .forEach { dao.save(EnrollmentEntity(it.copy(status = "INACTIVE"))) }
        }
        val enrolled = dao.enrollments().any { it.value.class_id == cls.id && it.value.student_id == studentId && it.value.status == "ACTIVE" }
        if (!enrolled) {
            val existingEnrollment = dao.enrollments().firstOrNull { it.value.class_id == cls.id && it.value.student_id == studentId }?.value
            if (existingEnrollment != null) {
                dao.save(EnrollmentEntity(existingEnrollment.copy(status = "ACTIVE")))
            } else {
                dao.save(EnrollmentEntity(Enrollment(UUID.randomUUID().toString(), cls.id, studentId, "ACTIVE", now)))
            }
        }
        audit("ADMIN_SAVE", "students", studentId, "", json.encodeToString(mapOf("masv" to code, "hoTen" to name, "lop" to group)), "Lưu sinh viên kèm lớp")
    }

    override suspend fun importStudents(
        students: List<ImportStudentRow>,
        mappings: List<ClassMapping>,
        mode: String
    ): ImportResult = db.withTransaction {
        check(actor().role == "ADMIN") { "Chức năng chỉ dành cho quản trị viên." }
        require(mode in setOf("SKIP", "UPDATE", "INSERT")) { "Chế độ import không hợp lệ: $mode" }
        val now = clock()
        val existingStudents = dao.students().map { it.value }
        val existingClasses = dao.classes().map { it.value }
        val existingEnrollments = dao.enrollments().map { it.value }

        val classMap = existingClasses.associateBy { it.class_code }.toMutableMap()
        val mappingsMap = mappings.associateBy { it.classCode }
        var newClassesCount = 0

        // Auto-create classes for all groups in CSV
        val allGroups = (students.map { it.lop.trim() } + mappings.map { it.classCode.trim() }).distinct()
        for (group in allGroups) {
            if (!classMap.containsKey(group) && group.isNotBlank()) {
                val mapping = mappingsMap[group]
                val className = mapping?.className?.takeIf { it.isNotBlank() } ?: group
                val newClass = SchoolClass(
                    id = UUID.randomUUID().toString(),
                    class_code = group,
                    class_name = className,
                    subject_name = mapping?.subjectName?.takeIf { it.isNotBlank() },
                    lecturer_id = mapping?.lecturerId?.takeIf { it.isNotBlank() },
                    semester = mapping?.semester ?: "",
                    academic_year = mapping?.academicYear ?: "",
                    created_at = now
                )
                dao.save(ClassEntity(newClass))
                classMap[group] = newClass
                newClassesCount++
            }
        }

        val studentMap = existingStudents.associateBy { it.student_code }.toMutableMap()
        val cardMap = dao.cards().map { it.value }.associateBy { it.student_id }.toMutableMap()
        var newStudentsCount = 0
        var updatedStudentsCount = 0
        var newEnrollmentsCount = 0

        val enrollmentSet = existingEnrollments.filter { it.status == "ACTIVE" }.map { it.class_id to it.student_id }.toMutableSet()

        for (row in students) {
            val group = row.lop.trim()
            val targetClass = classMap[group] ?: error("Không thể tạo lớp $group")
            val code = row.masv.trim()
            val name = row.hoTen.trim()

            val existing = studentMap[code]
            val studentId: String
            if (existing == null) {
                studentId = UUID.randomUUID().toString()
                val newStudent = Student(
                    id = studentId,
                    student_code = code,
                    full_name = name,
                    status = "ACTIVE",
                    created_at = now
                )
                dao.save(StudentEntity(newStudent))
                studentMap[code] = newStudent
                newStudentsCount++

                val card = StudentCard(
                    id = UUID.randomUUID().toString(),
                    student_id = studentId,
                    card_code = "STUDENT_$code",
                    barcode_format = "QR_CODE",
                    is_active = true,
                    created_at = now
                )
                dao.save(CardEntity(card))
                cardMap[studentId] = card
            } else {
                studentId = existing.id
                if (mode == "INSERT") {
                    error("Sinh viên có mã $code đã tồn tại trong hệ thống.")
                }
                if (mode == "UPDATE" && existing.full_name != name) {
                    val updated = existing.copy(full_name = name)
                    dao.save(StudentEntity(updated))
                    studentMap[code] = updated
                    updatedStudentsCount++
                }
                if (!cardMap.containsKey(studentId)) {
                    val card = StudentCard(
                        id = UUID.randomUUID().toString(),
                        student_id = studentId,
                        card_code = "STUDENT_$code",
                        barcode_format = "QR_CODE",
                        is_active = true,
                        created_at = now
                    )
                    dao.save(CardEntity(card))
                    cardMap[studentId] = card
                }
            }

            val pair = targetClass.id to studentId
            if (mode == "UPDATE") {
                // Deactivate enrollments in any other classes for this student
                existingEnrollments.filter { it.student_id == studentId && it.class_id != targetClass.id && it.status == "ACTIVE" }
                    .forEach { old ->
                        dao.save(EnrollmentEntity(old.copy(status = "INACTIVE")))
                        enrollmentSet.remove(old.class_id to studentId)
                    }
            }

            if (!enrollmentSet.contains(pair)) {
                val existingEnrollment = existingEnrollments.firstOrNull { it.class_id == targetClass.id && it.student_id == studentId }
                if (existingEnrollment != null) {
                    if (existingEnrollment.status != "ACTIVE") {
                        dao.save(EnrollmentEntity(existingEnrollment.copy(status = "ACTIVE")))
                        enrollmentSet.add(pair)
                        newEnrollmentsCount++
                    }
                } else {
                    val enrollment = Enrollment(
                        id = UUID.randomUUID().toString(),
                        class_id = targetClass.id,
                        student_id = studentId,
                        status = "ACTIVE",
                        created_at = now
                    )
                    dao.save(EnrollmentEntity(enrollment))
                    enrollmentSet.add(pair)
                    newEnrollmentsCount++
                }
            }
        }

        audit(
            action = "BULK_IMPORT_STUDENTS",
            type = "students",
            id = "batch-${students.size}",
            old = "",
            new = "Imported ${students.size} rows ($mode)",
            reason = "Nhập danh sách sinh viên: $newStudentsCount mới, $updatedStudentsCount cập nhật, $newClassesCount lớp mới, $newEnrollmentsCount phân lớp"
        )

        ImportResult(
            totalProcessed = students.size,
            newStudents = newStudentsCount,
            updatedStudents = updatedStudentsCount,
            newClasses = newClassesCount,
            newEnrollments = newEnrollmentsCount
        )
    }

    override suspend fun getStudentsPage(
        page: Int,
        pageSize: Int,
        search: String?,
        classCode: String?
    ): PaginatedResult<StudentItem> = db.withTransaction {
        val user = actor()
        check(user.role == "ADMIN") { "Chỉ ADMIN mới có quyền truy cập quản trị sinh viên." }
        val safePage = maxOf(1, page)
        val safeSize = pageSize.coerceIn(1, 100)
        val offset = (safePage - 1) * safeSize
        val cleanSearch = search?.trim()?.takeIf { it.isNotBlank() }
        val cleanClass = classCode?.trim()?.takeIf { it.isNotBlank() && it != "Tất cả" }

        val total = dao.countStudents(cleanSearch, cleanClass)
        val totalPages = if (total == 0) 1 else ((total + safeSize - 1) / safeSize)
        val rawItems = dao.getStudentsPage(safeSize, offset, cleanSearch, cleanClass)
        val items = rawItems.map {
            StudentItem(
                id = it.id,
                student_code = it.student_code,
                full_name = it.full_name,
                class_code = it.class_code,
                status = it.status,
                created_at = it.created_at
            )
        }
        PaginatedResult(
            items = items,
            page = safePage,
            pageSize = safeSize,
            totalItems = total,
            totalPages = totalPages,
            hasNextPage = safePage < totalPages,
            hasPreviousPage = safePage > 1
        )
    }

    override suspend fun getStudentById(studentId: String): Student? = db.withTransaction {
        dao.getStudentById(studentId)?.value
    }

    private suspend fun audit(action: String, type: String, id: String, old: String, new: String, reason: String) {
        dao.insert(AuditEntity(AuditLog(UUID.randomUUID().toString(), actorId, action, type, id, old, new, reason, clock())))
    }
}
