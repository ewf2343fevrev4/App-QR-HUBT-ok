package com.smartattendance.app.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

data class StudentWithClass(
    val id: String,
    val student_code: String,
    val full_name: String,
    val status: String,
    val created_at: Long,
    val class_code: String
)

@Dao interface AttendanceDao {
    @Query("SELECT * FROM profiles") suspend fun profiles(): List<ProfileEntity>
    @Query("SELECT * FROM students") suspend fun students(): List<StudentEntity>
    @Query("SELECT * FROM student_cards") suspend fun cards(): List<CardEntity>
    @Query("SELECT * FROM classes") suspend fun classes(): List<ClassEntity>
    @Query("SELECT * FROM enrollments") suspend fun enrollments(): List<EnrollmentEntity>
    @Query("SELECT * FROM attendance_sessions ORDER BY start_time DESC") suspend fun sessions(): List<SessionEntity>
    @Query("SELECT * FROM session_roster") suspend fun roster(): List<RosterEntity>
    @Query("SELECT * FROM attendance_records") suspend fun records(): List<RecordEntity>
    @Query("SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT 200") suspend fun logs(): List<AuditEntity>
    @Upsert suspend fun save(value: ProfileEntity)
    @Upsert suspend fun save(value: StudentEntity)
    @Upsert suspend fun save(value: CardEntity)
    @Upsert suspend fun save(value: ClassEntity)
    @Upsert suspend fun save(value: EnrollmentEntity)
    @Upsert suspend fun save(value: SessionEntity)
    @Insert suspend fun insert(value: RosterEntity)
    @Insert suspend fun insert(value: RecordEntity)
    @Upsert suspend fun save(value: RecordEntity)
    @Insert suspend fun insert(value: AuditEntity)
    @Query("DELETE FROM attendance_records WHERE session_id = :sessionId AND student_id = :studentId") suspend fun removeRecord(sessionId: String, studentId: String)
    @Query("SELECT COUNT(*) FROM students") suspend fun totalStudents(): Int
    @Query("SELECT COUNT(*) FROM classes") suspend fun totalClasses(): Int
    @Query("SELECT * FROM students WHERE id = :studentId LIMIT 1") suspend fun getStudentById(studentId: String): StudentEntity?
    @Query("""
        SELECT s.id, s.student_code, s.full_name, s.status, s.created_at,
               COALESCE(GROUP_CONCAT(DISTINCT c.class_code), '') AS class_code
        FROM students s
        LEFT JOIN enrollments e ON e.student_id = s.id AND e.status = 'ACTIVE'
        LEFT JOIN classes c ON c.id = e.class_id
        WHERE (:classCode IS NULL OR :classCode = '' OR :classCode = 'Tất cả' OR c.class_code = :classCode)
          AND (
            :search IS NULL OR :search = '' OR
            s.student_code LIKE '%' || :search || '%' OR
            s.full_name LIKE '%' || :search || '%' OR
            c.class_code LIKE '%' || :search || '%'
          )
        GROUP BY s.id
        ORDER BY s.student_code ASC, s.id ASC
        LIMIT :pageSize OFFSET :offset
    """)
    suspend fun getStudentsPage(pageSize: Int, offset: Int, search: String?, classCode: String?): List<StudentWithClass>

    @Query("""
        SELECT COUNT(DISTINCT s.id)
        FROM students s
        LEFT JOIN enrollments e ON e.student_id = s.id AND e.status = 'ACTIVE'
        LEFT JOIN classes c ON c.id = e.class_id
        WHERE (:classCode IS NULL OR :classCode = '' OR :classCode = 'Tất cả' OR c.class_code = :classCode)
          AND (
            :search IS NULL OR :search = '' OR
            s.student_code LIKE '%' || :search || '%' OR
            s.full_name LIKE '%' || :search || '%' OR
            c.class_code LIKE '%' || :search || '%'
          )
    """)
    suspend fun countStudents(search: String?, classCode: String?): Int
}
@Database(entities=[ProfileEntity::class,StudentEntity::class,CardEntity::class,ClassEntity::class,EnrollmentEntity::class,SessionEntity::class,RosterEntity::class,RecordEntity::class,AuditEntity::class], version=2, exportSchema=true)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun dao(): AttendanceDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Recreate classes table to allow nullable subject_name and lecturer_id
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `classes_new` (
                        `id` TEXT NOT NULL,
                        `class_code` TEXT NOT NULL,
                        `class_name` TEXT NOT NULL,
                        `subject_name` TEXT,
                        `lecturer_id` TEXT,
                        `semester` TEXT NOT NULL,
                        `academic_year` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`lecturer_id`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `classes_new` (`id`, `class_code`, `class_name`, `subject_name`, `lecturer_id`, `semester`, `academic_year`, `created_at`)
                    SELECT `id`, `class_code`, `class_name`, `subject_name`, `lecturer_id`, `semester`, `academic_year`, `created_at`
                    FROM `classes`
                """.trimIndent())
                db.execSQL("DROP TABLE `classes`")
                db.execSQL("ALTER TABLE `classes_new` RENAME TO `classes`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_classes_class_code` ON `classes` (`class_code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_classes_lecturer_id` ON `classes` (`lecturer_id`)")

                // 2. Recreate attendance_sessions table to add subject_name and lecturer_id with foreign key
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `attendance_sessions_new` (
                        `id` TEXT NOT NULL,
                        `class_id` TEXT NOT NULL,
                        `session_name` TEXT NOT NULL,
                        `session_date` TEXT NOT NULL,
                        `start_time` INTEGER NOT NULL,
                        `late_after` INTEGER NOT NULL,
                        `end_time` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `created_by` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `subject_name` TEXT NOT NULL DEFAULT '',
                        `lecturer_id` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`class_id`) REFERENCES `classes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`created_by`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`lecturer_id`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `attendance_sessions_new` (
                        `id`, `class_id`, `session_name`, `session_date`,
                        `start_time`, `late_after`, `end_time`, `status`,
                        `created_by`, `created_at`, `subject_name`, `lecturer_id`
                    )
                    SELECT 
                        s.`id`, s.`class_id`, s.`session_name`, s.`session_date`,
                        s.`start_time`, s.`late_after`, s.`end_time`, s.`status`,
                        s.`created_by`, s.`created_at`,
                        COALESCE((SELECT c.`subject_name` FROM `classes` c WHERE c.`id` = s.`class_id`), 'Môn học lịch sử'),
                        COALESCE((SELECT c.`lecturer_id` FROM `classes` c WHERE c.`id` = s.`class_id`), s.`created_by`)
                    FROM `attendance_sessions` s
                """.trimIndent())
                db.execSQL("DROP TABLE `attendance_sessions`")
                db.execSQL("ALTER TABLE `attendance_sessions_new` RENAME TO `attendance_sessions`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_sessions_class_id` ON `attendance_sessions` (`class_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_sessions_created_by` ON `attendance_sessions` (`created_by`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_sessions_lecturer_id` ON `attendance_sessions` (`lecturer_id`)")
            }
        }
    }
}
