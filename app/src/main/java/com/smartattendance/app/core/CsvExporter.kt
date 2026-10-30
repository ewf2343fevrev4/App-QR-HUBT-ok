package com.smartattendance.app.core

import com.smartattendance.app.domain.model.*
import com.smartattendance.app.domain.usecase.statusLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {
    fun cell(value: String): String {
        val dangerous = value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || value.startsWith('\t') || value.startsWith('\r') || value.startsWith('\n')
        val safe = if (dangerous) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
    fun export(schoolClass: SchoolClass, session: AttendanceSession, rows: List<AttendanceRow>): ByteArray {
        val header = listOf("STT", "Mã sinh viên", "Họ tên", "Lớp", "Môn học", "Buổi học", "Ngày", "Trạng thái", "Thời gian điểm danh", "Ghi chú")
        val data = rows.mapIndexed { i, row -> listOf((i + 1).toString(), row.student.student_code, row.student.full_name, schoolClass.class_code, session.displaySubject, session.session_name, session.session_date, statusLabel(row.status), row.record?.let { formatTime(it.scanned_at) } ?: "", row.record?.note ?: "") }
        return ("\uFEFF" + (listOf(header) + data).joinToString("\r\n", postfix = "\r\n") { it.joinToString(",", transform = ::cell) }).toByteArray(Charsets.UTF_8)
    }
    fun filename(schoolClass: SchoolClass, session: AttendanceSession) = "${schoolClass.class_code}_${session.session_date}_${session.id.take(8)}.csv".replace(Regex("[^a-zA-Z0-9_.-]"), "_")
}
fun formatTime(millis: Long): String = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
