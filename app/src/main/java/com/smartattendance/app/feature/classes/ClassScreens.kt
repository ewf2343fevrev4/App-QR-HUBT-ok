package com.smartattendance.app.feature.classes

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
import com.smartattendance.app.domain.model.*
import com.smartattendance.app.ui.*
import com.smartattendance.app.ui.theme.TabularNumbers

@Composable
fun ClassesScreen(data: Snapshot, navigate: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }

    Page {
        Heading("Lớp học", "Quản lý danh sách lớp và sinh viên phụ trách.")
        ModernSearchField(
            value = query,
            onChange = { query = it },
            placeholder = "Tìm theo mã lớp hoặc tên môn…"
        )

        val list = data.classes.filter { "${it.class_name} ${it.class_code}".contains(query, true) }
        if (list.isEmpty()) {
            EmptyState("Không có lớp phù hợp", "Thử thay đổi từ khóa tìm kiếm của bạn.")
        } else {
            GroupedCard {
                list.forEachIndexed { index, cls ->
                    val enrolledCount = data.enrollments.count { it.class_id == cls.id && it.status == "ACTIVE" }
                    val sessionCount = data.sessions.count { it.class_id == cls.id }
                    GroupedItem(
                        title = cls.class_code,
                        subtitle = "${cls.class_name} • $enrolledCount SV • $sessionCount buổi",
                        icon = Icons.Outlined.School,
                        onClick = { navigate("class/${cls.id}") },
                        showDivider = index < list.lastIndex
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailsScreen(id: String, data: Snapshot, navigate: (String) -> Unit) {
    val cls = data.classes.find { it.id == id } ?: return
    val sessions = data.sessions.filter { it.class_id == id }.sortedByDescending { it.start_time }
    val members = data.enrollments.filter { it.class_id == id && it.status == "ACTIVE" }.map { it.student_id }.toSet()
    val completed = sessions.filter { it.status == "CLOSED" }
    val historicalRows = completed.flatMap { data.rows(it.id) }
    val participation = Statistics.from(historicalRows).participation

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Page {
        Heading(cls.class_code, cls.class_name)

        // Class Overview Banner
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
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Groups,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${members.size} sinh viên chính thức",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${sessions.size} buổi đã tạo • %.1f%% tỷ lệ có mặt lịch sử".format(participation),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularNumbers),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Button(
            onClick = { navigate("create?classId=$id") },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Tạo buổi học cho lớp này", style = MaterialTheme.typography.titleSmall)
        }

        // Tab Selector (Buổi học vs Danh sách sinh viên)
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)) }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Buổi học (${sessions.size})", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Sinh viên (${members.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        if (selectedTab == 0) {
            if (sessions.isEmpty()) {
                EmptyState("Chưa có buổi học nào", "Nhấn nút Tạo buổi học ở trên để bắt đầu.")
            } else {
                GroupedCard {
                    sessions.forEachIndexed { index, session ->
                        val lecturer = data.profiles.find { it.id == session.lecturer_id }
                        val detail = buildString {
                            append(session.session_date)
                            if (lecturer != null) append(" • GV: ${lecturer.full_name}")
                        }
                        GroupedItem(
                            title = "${session.subject_name} • ${session.session_name}",
                            subtitle = detail,
                            icon = Icons.Outlined.Event,
                            onClick = { navigate("attendance/${session.id}") },
                            trailing = { StatusBadge(session.status) },
                            showDivider = index < sessions.lastIndex
                        )
                    }
                }
            }
        } else {
            val studentList = data.students.filter { it.id in members }.sortedBy { it.student_code }
            if (studentList.isEmpty()) {
                EmptyState("Lớp chưa có sinh viên", "Thêm sinh viên vào lớp trong mục Quản trị.")
            } else {
                GroupedCard {
                    studentList.forEachIndexed { index, student ->
                        val initials = student.full_name.trim().split(" ")
                            .filter { it.isNotBlank() }
                            .takeLast(2)
                            .mapNotNull { it.firstOrNull()?.uppercase() }
                            .joinToString("")
                            .ifBlank { "SV" }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(34.dp),
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
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = student.full_name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = student.student_code,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularNumbers),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            StatusBadge(student.status)
                            IconButton(
                                onClick = { navigate("student/${student.id}") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        if (index < studentList.lastIndex) {
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
    }
}

@Composable
fun SessionsScreen(data: Snapshot, navigate: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("OPEN") }

    Page {
        Heading("Chọn buổi điểm danh", "Mở buổi học để bắt đầu quét thẻ sinh viên.")

        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val options = listOf(
                "OPEN" to "Đang mở",
                "SCHEDULED" to "Đã lên lịch",
                "CLOSED" to "Đã đóng",
                "ALL" to "Tất cả"
            )
            options.forEach { (key, label) ->
                val selected = filter == key
                FilterChip(
                    selected = selected,
                    onClick = { filter = key },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Button(
            onClick = { navigate("create") },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Tạo buổi học mới", style = MaterialTheme.typography.titleSmall)
        }

        val list = data.sessions.filter { filter == "ALL" || it.status == filter }.sortedByDescending { it.start_time }
        if (list.isEmpty()) {
            EmptyState("Chưa có buổi phù hợp", "Chọn một bộ lọc khác hoặc tạo một buổi học mới.")
        } else {
            GroupedCard {
                list.forEachIndexed { index, session ->
                    val cls = data.classes.find { it.id == session.class_id }
                    val lecturer = data.profiles.find { it.id == session.lecturer_id }
                    val subtitle = buildString {
                        append(session.displaySubject)
                        if (cls != null) append(" (${cls.class_code})")
                        append(" • ")
                        append(session.session_date)
                        if (lecturer != null) append(" • GV: ${lecturer.full_name}")
                    }
                    GroupedItem(
                        title = session.session_name,
                        subtitle = subtitle,
                        icon = Icons.Outlined.QrCodeScanner,
                        onClick = {
                            navigate(if (session.status == "OPEN") "scanner/${session.id}" else "attendance/${session.id}")
                        },
                        trailing = { StatusBadge(session.status) },
                        showDivider = index < list.lastIndex
                    )
                }
            }
        }
    }
}
