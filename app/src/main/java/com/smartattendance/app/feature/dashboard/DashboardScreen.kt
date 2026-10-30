package com.smartattendance.app.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartattendance.app.core.AppState
import com.smartattendance.app.ui.*
import com.smartattendance.app.ui.theme.TabularNumbers
import java.time.LocalDate

@Composable
fun DashboardScreen(state: AppState, navigate: (String) -> Unit) {
    val today = LocalDate.now().toString()
    val sessions = state.data.sessions.filter { it.session_date == today }
    val ids = sessions.map { it.id }
    val attendanceCount = state.data.records.count { it.session_id in ids && it.status in setOf("PRESENT", "LATE") }

    Page {
        // Eyebrow and greeting
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "KHÔNG GIAN GIẢNG DẠY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Xin chào,\n${state.user?.full_name ?: "Giảng viên"}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Theo dõi lớp học, ghi nhận từng lần có mặt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Hero Gradient Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF65D8CB), CircleShape)
                                )
                                Text(
                                    text = "HÔM NAY • $today",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNumbers),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${sessions.size} buổi học",
                            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = TabularNumbers),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "$attendanceCount lượt tham gia được ghi nhận",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TabularNumbers),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Button(
                        onClick = { navigate("sessions") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Quét thẻ sinh viên",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Metric Summary Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                label = "LỚP PHỤ TRÁCH",
                value = "${state.data.classes.size}",
                subtitle = "Tổng số lớp học",
                icon = Icons.Outlined.School,
                modifier = Modifier.weight(1f),
                onClick = { navigate("classes") }
            )
            MetricTile(
                label = "HỒ SƠ SINH VIÊN",
                value = "${state.data.students.size}",
                subtitle = "Đã được ghi danh",
                icon = Icons.Outlined.People,
                modifier = Modifier.weight(1f)
            )
        }

        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { navigate("create") },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Tạo buổi", style = MaterialTheme.typography.titleSmall)
            }
            OutlinedButton(
                onClick = { navigate("reports") },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Báo cáo", style = MaterialTheme.typography.titleSmall)
            }
        }

        // Section: Classes
        Section("Lớp học của bạn", "Xem tất cả") { navigate("classes") }
        if (state.data.classes.isEmpty()) {
            EmptyState("Chưa có lớp được phân công", "Liên hệ quản trị viên hoặc tạo mới lớp học.")
        } else {
            val previewClasses = state.data.classes.take(3)
            GroupedCard {
                previewClasses.forEachIndexed { index, cls ->
                    val enrolledCount = state.data.enrollments.count { it.class_id == cls.id && it.status == "ACTIVE" }
                    GroupedItem(
                        title = cls.class_code,
                        subtitle = "${cls.class_name} • $enrolledCount sinh viên",
                        icon = Icons.Outlined.School,
                        onClick = { navigate("class/${cls.id}") },
                        showDivider = index < previewClasses.lastIndex
                    )
                }
            }
        }

        // Section: Today's Schedule
        if (sessions.isNotEmpty()) {
            Section("Lịch hôm nay (${sessions.size})")
            GroupedCard {
                sessions.forEachIndexed { index, session ->
                    val classCode = state.data.classes.find { it.id == session.class_id }?.class_code.orEmpty()
                    GroupedItem(
                        title = session.session_name,
                        subtitle = if (classCode.isNotBlank()) "Lớp $classCode • ${session.displaySubject}" else session.displaySubject,
                        icon = Icons.Outlined.Event,
                        onClick = { navigate("attendance/${session.id}") },
                        trailing = { StatusBadge(session.status) },
                        showDivider = index < sessions.lastIndex
                    )
                }
            }
        }
    }
}
