package com.smartattendance.app

import com.smartattendance.app.domain.model.*
import com.smartattendance.app.domain.usecase.AttendanceRules
import com.smartattendance.app.core.CsvExporter
import org.junit.Assert.*
import org.junit.Test

class AttendanceRulesTest {
    private val user=Profile("teacher","Teacher","a@example.invalid")
    private val cls=SchoolClass("class","C1","Class","Subject","teacher")
    private val session=AttendanceSession("session","class","Lesson","2026-10-26",100,200,300,"OPEN","teacher",subject_name="Subject",lecturer_id="teacher")
    private val student=Student("student","202610001","Nguyễn An")
    private fun evaluate(now: Long=150, actor: Profile=user, s: AttendanceSession=session, st: Student?=student, active: Boolean=true, enrolled: Boolean=true, duplicate: Boolean=false)=AttendanceRules.evaluate(actor,cls,s,st,active,enrolled,duplicate,now)
    @Test fun `time boundaries are deterministic`() {
        assertEquals("PRESENT", evaluate(100))
        assertEquals("LATE", evaluate(200))
        assertEquals("NOT_STARTED", evaluate(99))
        assertEquals("SESSION_EXPIRED", evaluate(300))
    }
    @Test fun `invalid cards are not student codes`() {assertEquals("INVALID_CARD",evaluate(st=null));assertEquals("INVALID_CARD",evaluate(active=false))}
    @Test fun `reject wrong class duplicate and inactive`() {assertEquals("WRONG_CLASS",evaluate(enrolled=false));assertEquals("DUPLICATE",evaluate(duplicate=true));assertEquals("INACTIVE_STUDENT",evaluate(st=student.copy(status="INACTIVE")))}
    @Test fun `permission checked before card lookup information returned`() {assertEquals("FORBIDDEN",evaluate(actor=user.copy(id="other")));assertEquals("FORBIDDEN",evaluate(actor=user.copy(is_active=false)));assertEquals("PRESENT",evaluate(actor=user.copy(id="admin",role="ADMIN")))}
    @Test fun `closed and scheduled cannot scan`() {
        assertEquals("SESSION_CLOSED", evaluate(s=session.copy(status="CLOSED")))
        assertEquals("SESSION_NOT_OPEN", evaluate(s=session.copy(status="SCHEDULED")))
    }
    @Test(expected=IllegalArgumentException::class) fun `reject invalid session range`() {AttendanceRules.validateSession(session.copy(end_time=199))}
    @Test fun `csv quotes BOM Vietnamese and formulas`() {
        assertEquals("\"'=1+1\"",CsvExporter.cell("=1+1")); assertEquals("\"'  @SUM(A1)\"",CsvExporter.cell("  @SUM(A1)"));assertEquals("\"a,\"\"b\"\"\nline\"",CsvExporter.cell("a,\"b\"\nline"))
        val bytes=CsvExporter.export(cls,session,listOf(AttendanceRow(student,null)))
        assertArrayEquals(byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte()),bytes.take(3).toByteArray())
        assertTrue(bytes.toString(Charsets.UTF_8).contains("Nguyễn An"));assertTrue(bytes.toString(Charsets.UTF_8).contains("Vắng"))
    }
    @Test fun `statistics denominator includes excused and absent`() {
        val record=AttendanceRecord("r","session","student","PRESENT",100,"teacher")
        val stats=Statistics.from(listOf(AttendanceRow(student,record),AttendanceRow(student,record.copy(status="LATE")),AttendanceRow(student,record.copy(status="EXCUSED")),AttendanceRow(student,null)))
        assertEquals(4,stats.total);assertEquals(50f,stats.participation);assertEquals(1,stats.absent);assertEquals(0f,Statistics.from(emptyList()).participation)
    }
}
