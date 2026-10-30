package com.smartattendance.app.feature.students

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartattendance.app.core.formatTime
import com.smartattendance.app.domain.model.*
import com.smartattendance.app.ui.*
import com.smartattendance.app.ui.theme.TabularNumbers

@Composable
fun StudentScreen(id: String, data: Snapshot, navigate: (String) -> Unit) {
    val student = data.students.find { it.id == id } ?: return
    val memberships = data.enrollments.filter { it.student_id == id && it.status == "ACTIVE" }.map { it.class_id }
    val sessionIds = data.roster.filter { it.student_id == id }.map { it.session_id }
    val sessions = data.sessions.filter { it.id in sessionIds }.sortedByDescending { it.start_time }
    val cards = data.cards.filter { it.student_id == id }

    val initials = student.full_name.trim().split(" ")
        .filter { it.isNotBlank() }
        .takeLast(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifBlank { "SV" }

    Page {
        // Student Profile Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = student.full_name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "MASV: ${student.student_code} • ${data.studentClassCodes(student.id)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularNumbers),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                StatusBadge(student.status)
            }
        }

        // Section: Classes
        Section("Lớp đang tham gia (${memberships.size})")
        val studentClasses = data.classes.filter { it.id in memberships }
        if (studentClasses.isEmpty()) {
            EmptyState("Chưa tham gia lớp nào", "Sinh viên chưa được ghi danh vào lớp học.")
        } else {
            GroupedCard {
                studentClasses.forEachIndexed { index, cls ->
                    GroupedItem(
                        title = cls.class_code,
                        subtitle = cls.class_name,
                        icon = Icons.Outlined.School,
                        onClick = { navigate("class/${cls.id}") },
                        showDivider = index < studentClasses.lastIndex
                    )
                }
            }
        }

        // Section: Cards
        Section("Thẻ sinh viên (${cards.size})")
        if (cards.isEmpty()) {
            EmptyState("Chưa gán thẻ", "Chưa có thẻ vật lý hoặc mã QR/barcode nào được liên kết.")
        } else {
            GroupedCard {
                cards.forEachIndexed { index, card ->
                    GroupedItem(
                        title = card.card_code,
                        subtitle = "Định dạng: ${card.barcode_format}",
                        icon = Icons.Outlined.QrCode,
                        trailing = { StatusBadge(if (card.is_active) "ACTIVE" else "INACTIVE") },
                        showDivider = index < cards.lastIndex
                    )
                }
            }
        }

        // Section: Closed Sessions Attendance Stats
        Section("Chuyên cần tổng thể")
        val closedSessions = sessions.filter { it.status == "CLOSED" }
        val closedRows = closedSessions.map { session ->
            AttendanceRow(student, data.records.find { it.session_id == session.id && it.student_id == id })
        }
        Stats(Statistics.from(closedRows))

        // Section: Detailed History
        Section("Lịch sử điểm danh (${sessions.size})")
        if (sessions.isEmpty()) {
            EmptyState("Chưa có lịch sử điểm danh", "Lịch sử sẽ hiển thị khi sinh viên được đưa vào danh sách buổi học.")
        } else {
            GroupedCard {
                sessions.forEachIndexed { index, session ->
                    val record = data.records.find { it.session_id == session.id && it.student_id == id }
                    val status = record?.status ?: "ABSENT"
                    val classCode = data.classes.find { it.id == session.class_id }?.class_code.orEmpty()
                    val subtitle = buildString {
                        append(session.session_date)
                        if (classCode.isNotBlank()) append(" • Lớp $classCode")
                        if (record != null) append(" • Lúc ${formatTime(record.scanned_at)}")
                    }
                    GroupedItem(
                        title = session.session_name,
                        subtitle = subtitle,
                        icon = Icons.Outlined.Event,
                        onClick = { navigate("attendance/${session.id}") },
                        trailing = { StatusBadge(status) },
                        showDivider = index < sessions.lastIndex
                    )
                }
            }
        }
    }
}
