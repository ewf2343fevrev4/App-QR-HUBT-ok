package com.smartattendance.app.feature.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartattendance.app.core.*
import com.smartattendance.app.domain.model.*
import com.smartattendance.app.ui.*
import com.smartattendance.app.ui.theme.TabularNumbers
import java.time.LocalDate

@Composable
fun ReportsScreen(
    initialSessionId: String?,
    vm: AppViewModel,
    state: AppState,
    navigate: (String) -> Unit
) {
    val data = state.data
    var classId by rememberSaveable {
        mutableStateOf(data.sessions.find { it.id == initialSessionId }?.class_id ?: data.classes.firstOrNull()?.id.orEmpty())
    }
    var sessionId by rememberSaveable { mutableStateOf(initialSessionId ?: "") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<ByteArray?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            pending?.let { vm.export(uri, it) } ?: vm.notify("Vui lòng chọn xuất CSV lại sau khi màn hình được khôi phục.")
        }
        pending = null
    }

    val dateError = runCatching {
        if (from.isNotBlank()) LocalDate.parse(from)
        if (to.isNotBlank()) LocalDate.parse(to)
        require(from.isBlank() || to.isBlank() || from <= to)
    }.isFailure

    val sessions = data.sessions.filter {
        it.class_id == classId && (from.isBlank() || it.session_date >= from) && (to.isBlank() || it.session_date <= to)
    }.sortedByDescending { it.start_time }

    val session = sessions.find { it.id == sessionId } ?: sessions.firstOrNull()
    val cls = data.classes.find { it.id == classId }

    Page {
        Heading("Báo cáo chuyên cần", "Từ dữ liệu điểm danh đến bức tranh toàn lớp.")

        // Selectors
        Choice(
            label = "Lớp học",
            items = data.classes,
            selected = cls,
            text = { "${it.class_code} • ${it.class_name}" },
            onSelect = {
                classId = it.id
                sessionId = ""
            }
        )

        // Date Range Fields
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = from,
                onValueChange = { from = it },
                modifier = Modifier.weight(1f),
                label = { Text("Từ ngày") },
                placeholder = { Text("yyyy-MM-dd") },
                singleLine = true,
                isError = dateError,
                leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(10.dp)
            )
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                modifier = Modifier.weight(1f),
                label = { Text("Đến ngày") },
                placeholder = { Text("yyyy-MM-dd") },
                singleLine = true,
                isError = dateError,
                leadingIcon = { Icon(Icons.Outlined.Event, null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(10.dp)
            )
        }

        if (dateError) {
            Text(
                text = "Định dạng ngày hợp lệ: yyyy-MM-dd (Từ ngày không được sau Đến ngày).",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Choice(
            label = "Buổi học",
            items = sessions,
            selected = session,
            text = { "${it.session_date} • ${it.session_name}" },
            onSelect = { sessionId = it.id }
        )

        if (session == null || cls == null || dateError) {
            EmptyState("Chưa có dữ liệu phù hợp", "Vui lòng chọn lớp học và khoảng thời gian có buổi học.")
        } else {
            val rows = data.rows(session.id)
            val stats = Statistics.from(rows)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Tổng kết buổi học",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                StatusBadge(session.status)
            }

            Stats(stats)

            // Segmented Distribution Legend
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Phân bố chi tiết trạng thái",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    SegmentedStatsBar(
                        stats = stats,
                        height = 10.dp,
                        showLegend = true
                    )
                }
            }

            // Export Actions
            Button(
                onClick = {
                    pending = CsvExporter.export(cls, session, rows)
                    launcher.launch(CsvExporter.filename(cls, session))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                enabled = !state.busy
            ) {
                Icon(Icons.Outlined.FileDownload, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Xuất báo cáo CSV (UTF-8 BOM)", style = MaterialTheme.typography.titleSmall)
            }

            OutlinedButton(
                onClick = { navigate("attendance/${session.id}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            ) {
                Text("Xem danh sách chi tiết", style = MaterialTheme.typography.titleSmall)
            }
        }

        // Student Ranking Section
        Section("Chuyên cần theo sinh viên")
        Text(
            text = "Tổng hợp tỷ lệ tham gia các buổi đã đóng trong khoảng thời gian đã chọn.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val finished = sessions.filter { it.status == "CLOSED" }.map { it.id }.toSet()
        val studentIds = data.roster.filter { it.session_id in finished }.map { it.student_id }.distinct()

        if (studentIds.isEmpty()) {
            EmptyState("Chưa có buổi học đã đóng", "Dữ liệu chuyên cần tổng hợp dựa trên các buổi học đã đóng.")
        } else if (!dateError) {
            val studentStatsList = data.students.filter { it.id in studentIds }
                .map { student ->
                    val enrolledSessions = data.roster.filter { it.student_id == student.id && it.session_id in finished }.map { it.session_id }.toSet()
                    val attended = data.records.count {
                        it.student_id == student.id && it.session_id in enrolledSessions && it.status in setOf("PRESENT", "LATE")
                    }
                    val rate = if (enrolledSessions.isEmpty()) 0f else (attended * 100f) / enrolledSessions.size
                    Triple(student, "$attended/${enrolledSessions.size} buổi", rate)
                }
                .sortedByDescending { it.third }

            GroupedCard {
                studentStatsList.forEachIndexed { index, (student, attendanceSummary, rate) ->
                    GroupedItem(
                        title = student.full_name,
                        subtitle = "${student.student_code} • $attendanceSummary",
                        icon = Icons.Outlined.Person,
                        onClick = { navigate("student/${student.id}") },
                        trailing = {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (rate >= 80f) Color(0xFF087B69).copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "%.1f%%".format(rate),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNumbers),
                                    fontWeight = FontWeight.Bold,
                                    color = if (rate >= 80f) Color(0xFF087B69) else MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        showDivider = index < studentStatsList.lastIndex
                    )
                }
            }
        }
    }
}
