package com.smartattendance.app.feature.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.smartattendance.app.domain.usecase.statusLabel
import com.smartattendance.app.ui.*
import com.smartattendance.app.ui.theme.TabularNumbers

@Composable
fun AttendanceScreen(
    id: String,
    vm: AppViewModel,
    state: AppState,
    navigate: (String) -> Unit
) {
    val session = state.data.sessions.find { it.id == id } ?: return
    val cls = state.data.classes.find { it.id == session.class_id }
    val lecturer = state.data.profiles.find { it.id == session.lecturer_id }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var editing by remember { mutableStateOf<AttendanceRow?>(null) }
    var close by remember { mutableStateOf(false) }

    val rows = state.data.rows(id)

    Page {
        // Session Header
        val detailHeader = buildString {
            append("${session.displaySubject} • ")
            if (cls != null) append("Lớp ${cls.class_code} • ")
            append(session.session_date)
            if (lecturer != null) append("\nGiảng viên: ${lecturer.full_name}")
        }
        Heading(session.session_name, detailHeader)

        // Status & Time Band Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Ca học: ${formatTime(session.start_time)} → ${formatTime(session.end_time)}",
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TabularNumbers),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tính đi muộn từ: ${formatTime(session.late_after)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularNumbers),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(session.status)
            }
        }

        // Action Buttons
        if (session.status != "CLOSED") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (session.status == "OPEN") navigate("scanner/$id") else vm.setSession(id, "OPEN")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !state.busy
                ) {
                    Icon(
                        imageVector = if (session.status == "OPEN") Icons.Outlined.QrCodeScanner else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (session.status == "OPEN") "Quét thẻ" else "Mở điểm danh",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                OutlinedButton(
                    onClick = { close = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                    enabled = !state.busy
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Đóng buổi", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        // Statistics Block
        Stats(Statistics.from(rows))

        Text(
            text = "Ghi chú: Sinh viên chưa quét thẻ được tính vắng tạm thời. Kết quả vắng chính thức được chốt khi buổi học đóng.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedButton(
            onClick = { navigate("reports?sessionId=$id") },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Icon(
                imageVector = Icons.Outlined.BarChart,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Báo cáo và xuất CSV", style = MaterialTheme.typography.titleSmall)
        }

        // Search Field
        ModernSearchField(
            value = query,
            onChange = { query = it },
            placeholder = "Tìm theo tên hoặc mã sinh viên…"
        )

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filterOptions = listOf(
                "ALL" to "Tất cả (${rows.size})",
                "PRESENT" to "Có mặt (${rows.count { it.status == "PRESENT" }})",
                "LATE" to "Đi muộn (${rows.count { it.status == "LATE" }})",
                "ABSENT" to "Vắng (${rows.count { it.status == "ABSENT" }})",
                "EXCUSED" to "Có phép (${rows.count { it.status == "EXCUSED" }})"
            )
            filterOptions.forEach { (key, label) ->
                val selected = filter == key
                FilterChip(
                    selected = selected,
                    onClick = { filter = key },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNumbers),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // Filtered Student Roster
        val filtered = rows.filter {
            (filter == "ALL" || it.status == filter) &&
                    "${it.student.student_code} ${it.student.full_name}".contains(query, true)
        }

        if (filtered.isEmpty()) {
            EmptyState("Không có sinh viên phù hợp", "Thử thay đổi từ khóa tìm kiếm hoặc bộ lọc trạng thái.")
        } else {
            GroupedCard {
                filtered.forEachIndexed { index, row ->
                    val initials = row.student.full_name.trim().split(" ")
                        .filter { it.isNotBlank() }
                        .takeLast(2)
                        .mapNotNull { it.firstOrNull()?.uppercase() }
                        .joinToString("")
                        .ifBlank { "SV" }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = initials,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = row.student.full_name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = row.student.student_code,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularNumbers),
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                row.record?.let { rec ->
                                    Text(
                                        text = "• ${formatTime(rec.scanned_at)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularNumbers),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        StatusBadge(row.status)

                        IconButton(
                            onClick = { editing = row },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Chỉnh sửa",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (index < filtered.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                        )
                    }
                }
            }
        }
    }

    if (close) {
        Confirm(
            title = "Đóng buổi học?",
            message = "Sau khi đóng sẽ không thể quét hoặc mở lại buổi này. Bạn vẫn có thể điều chỉnh thủ công có ghi lý do.",
            onDismiss = { close = false },
            onConfirm = {
                close = false
                vm.setSession(id, "CLOSED")
            }
        )
    }

    editing?.let { row ->
        var status by remember(row) { mutableStateOf(row.status) }
        var reason by remember(row) { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { editing = null },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Điều chỉnh điểm danh",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = row.student.full_name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mã sinh viên: ${row.student.student_code}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularNumbers),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Choice(
                        label = "Trạng thái mới",
                        items = listOf("PRESENT", "LATE", "EXCUSED", "ABSENT"),
                        selected = status,
                        text = ::statusLabel,
                        onSelect = { status = it }
                    )

                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Lý do bắt buộc (ít nhất 3 ký tự)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    TextButton(
                        onClick = {
                            editing = null
                            navigate("student/${row.student.id}")
                        }
                    ) {
                        Text("Xem hồ sơ sinh viên", style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.adjust(id, row.student.id, status, reason) { editing = null }
                    },
                    shape = RoundedCornerShape(8.dp),
                    enabled = !state.busy && reason.trim().length >= 3
                ) {
                    Text("Lưu & ghi lịch sử")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { editing = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Hủy")
                }
            }
        )
    }
}
