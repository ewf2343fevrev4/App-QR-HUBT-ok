package com.smartattendance.app.data.local

import androidx.room.*
import com.smartattendance.app.domain.model.*

@Entity(tableName = "profiles", primaryKeys = ["id"], indices = [Index(value=["email"], unique=true)])
data class ProfileEntity(@Embedded val value: Profile)
@Entity(tableName = "students", primaryKeys = ["id"], indices = [Index(value=["student_code"], unique=true)])
data class StudentEntity(@Embedded val value: Student)
@Entity(tableName = "student_cards", primaryKeys = ["id"], indices = [Index(value=["card_code"], unique=true), Index("student_id")], foreignKeys = [ForeignKey(entity=StudentEntity::class, parentColumns=["id"], childColumns=["student_id"], onDelete=ForeignKey.RESTRICT)])
data class CardEntity(@Embedded val value: StudentCard)
@Entity(tableName = "classes", primaryKeys = ["id"], indices = [Index(value=["class_code"], unique=true), Index("lecturer_id")], foreignKeys=[ForeignKey(entity=ProfileEntity::class,parentColumns=["id"],childColumns=["lecturer_id"],onDelete=ForeignKey.RESTRICT)])
data class ClassEntity(@Embedded val value: SchoolClass)
@Entity(tableName = "enrollments", primaryKeys = ["id"], indices = [Index(value=["class_id","student_id"], unique=true), Index("student_id")], foreignKeys=[ForeignKey(entity=ClassEntity::class,parentColumns=["id"],childColumns=["class_id"],onDelete=ForeignKey.RESTRICT),ForeignKey(entity=StudentEntity::class,parentColumns=["id"],childColumns=["student_id"],onDelete=ForeignKey.RESTRICT)])
data class EnrollmentEntity(@Embedded val value: Enrollment)
@Entity(tableName = "attendance_sessions", primaryKeys = ["id"], indices=[Index("class_id"), Index("created_by"), Index("lecturer_id")], foreignKeys=[ForeignKey(entity=ClassEntity::class,parentColumns=["id"],childColumns=["class_id"],onDelete=ForeignKey.RESTRICT), ForeignKey(entity=ProfileEntity::class,parentColumns=["id"],childColumns=["created_by"],onDelete=ForeignKey.RESTRICT), ForeignKey(entity=ProfileEntity::class,parentColumns=["id"],childColumns=["lecturer_id"],onDelete=ForeignKey.RESTRICT)])
data class SessionEntity(@Embedded val value: AttendanceSession)
@Entity(tableName = "session_roster", primaryKeys=["session_id","student_id"], indices=[Index("student_id")], foreignKeys=[ForeignKey(entity=SessionEntity::class,parentColumns=["id"],childColumns=["session_id"],onDelete=ForeignKey.RESTRICT),ForeignKey(entity=StudentEntity::class,parentColumns=["id"],childColumns=["student_id"],onDelete=ForeignKey.RESTRICT)])
data class RosterEntity(@Embedded val value: RosterEntry)
@Entity(tableName = "attendance_records", primaryKeys=["id"], indices=[Index(value=["session_id","student_id"],unique=true),Index("student_id"),Index("recorded_by")], foreignKeys=[ForeignKey(entity=RosterEntity::class,parentColumns=["session_id","student_id"],childColumns=["session_id","student_id"],onDelete=ForeignKey.RESTRICT), ForeignKey(entity=ProfileEntity::class,parentColumns=["id"],childColumns=["recorded_by"],onDelete=ForeignKey.RESTRICT)])
data class RecordEntity(@Embedded val value: AttendanceRecord)
@Entity(tableName = "audit_logs", primaryKeys=["id"], indices=[Index("actor_id")], foreignKeys=[ForeignKey(entity=ProfileEntity::class,parentColumns=["id"],childColumns=["actor_id"],onDelete=ForeignKey.RESTRICT)])
data class AuditEntity(@Embedded val value: AuditLog)
