package com.smartattendance.app.feature.attendance

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
import androidx.compose.ui.unit.dp
import com.smartattendance.app.core.*
import com.smartattendance.app.domain.model.AttendanceSession
import com.smartattendance.app.domain.model.LecturerProfile
import com.smartattendance.app.domain.usecase.AttendanceRules
import com.smartattendance.app.ui.*
import java.time.*
import java.util.UUID

private val VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh")

@Composable
fun CreateSessionScreen(
    classId: String?,
    vm: AppViewModel,
    state: AppState,
    onCreated: (String) -> Unit
) {
    val initial = remember { ZonedDateTime.now(VIETNAM_ZONE).withSecond(0).withNano(0) }
    var selectedId by rememberSaveable { mutableStateOf(classId ?: state.data.classes.firstOrNull()?.id.orEmpty()) }
    var subjectName by rememberSaveable { mutableStateOf("") }
    var selectedLecturerId by rememberSaveable {
        mutableStateOf(if (state.user?.role == "LECTURER") state.user.id else "")
    }
    var name by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(initial.toLocalDate().toString()) }
    var start by rememberSaveable { mutableStateOf(String.format("%02d:%02d", initial.hour, initial.minute)) }
    var late by rememberSaveable {
        val l = initial.plusMinutes(15)
        mutableStateOf(String.format("%02d:%02d", l.hour, l.minute))
    }
    var end by rememberSaveable {
        val e = initial.plusHours(2)
        mutableStateOf(String.format("%02d:%02d", e.hour, e.minute))
    }
    var open by rememberSaveable { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Dynamic lecturers fetch from list_active_lecturers() RPC
    var lecturers by remember { mutableStateOf<List<LecturerProfile>>(emptyList()) }
    var isLoadingLecturers by remember { mutableStateOf(true) }
    var lecturerError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            isLoadingLecturers = true
            lecturerError = null
            lecturers = vm.listActiveLecturers()
            if (state.user?.role == "ADMIN" && selectedLecturerId.isBlank() && lecturers.isNotEmpty()) {
                selectedLecturerId = lecturers.first().id
            }
        } catch (e: Exception) {
            lecturerError = e.message ?: "Không tải được danh sách giảng viên."
        } finally {
            isLoadingLecturers = false
        }
    }

    Page {
        Heading("Buổi học mới", "Thiết lập môn học, giảng viên phụ trách và bắt đầu điểm danh.")

        // 1. Class Choice
        Choice(
            label = "Lớp học *",
            items = state.data.classes,
            selected = state.data.classes.find { it.id == selectedId },
            text = { "${it.class_code} • ${it.class_name}" },
            onSelect = { selectedId = it.id }
        )

        // 2. Subject Name
        OutlinedTextField(
            value = subjectName,
            onValueChange = { subjectName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tên môn học *") },
            placeholder = { Text("Ví dụ: Lập trình Android, Cơ sở dữ liệu") },
            leadingIcon = { Icon(Icons.Outlined.Book, null, modifier = Modifier.size(20.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        // 3. Lecturer Selection
        if (state.user?.role == "ADMIN") {
            when {
                isLoadingLecturers -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Đang tải danh sách giảng viên…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                lecturerError != null -> {
                    Text(
                        text = "Lỗi tải giảng viên: $lecturerError",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                lecturers.isEmpty() -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = "Chưa có tài khoản Giảng viên (LECTURER) hoạt động.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Quản trị viên cần tạo tài khoản Auth trên Supabase Dashboard và kích hoạt profile vai trò LECTURER trước khi tạo buổi học.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                else -> {
                    Choice(
                        label = "Giảng viên phụ trách *",
                        items = lecturers,
                        selected = lecturers.find { it.id == selectedLecturerId },
                        text = { it.full_name },
                        onSelect = { selectedLecturerId = it.id }
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = state.user?.full_name ?: "",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Giảng viên phụ trách") },
                leadingIcon = { Icon(Icons.Outlined.Person, null, modifier = Modifier.size(20.dp)) },
                enabled = false,
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }

        // 4. Session Name & Schedule Info
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tên buổi học *") },
            placeholder = { Text("Ví dụ: Tuần 1 • Giới thiệu") },
            leadingIcon = { Icon(Icons.Outlined.EditCalendar, null, modifier = Modifier.size(20.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ngày (yyyy-MM-dd)") },
            leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, modifier = Modifier.size(20.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        // Time Inputs Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = start,
                onValueChange = { start = it },
                modifier = Modifier.weight(1f),
                label = { Text("Bắt đầu") },
                placeholder = { Text("HH:mm") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
            OutlinedTextField(
                value = late,
                onValueChange = { late = it },
                modifier = Modifier.weight(1f),
                label = { Text("Tính muộn") },
                placeholder = { Text("HH:mm") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
            OutlinedTextField(
                value = end,
                onValueChange = { end = it },
                modifier = Modifier.weight(1f),
                label = { Text("Kết thúc") },
                placeholder = { Text("HH:mm") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }

        // Auto Open Switch Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mở điểm danh khi tạo",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Cho phép quét thẻ ngay sau khi tạo thành công",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = open, onCheckedChange = { open = it })
            }
        }

        Text(
            text = "Múi giờ: ${ZoneId.systemDefault().id}. Buổi học trong cùng một ngày. Danh sách sinh viên được chốt từ sĩ số hiện tại của lớp.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        val canCreate = !state.busy &&
                selectedId.isNotEmpty() &&
                subjectName.isNotBlank() &&
                selectedLecturerId.isNotBlank() &&
                name.isNotBlank()

        Button(
            onClick = {
                try {
                    val day = LocalDate.parse(date)
                    fun timestamp(t: String) = day.atTime(LocalTime.parse(t)).atZone(VIETNAM_ZONE).toInstant().toEpochMilli()
                    require(selectedId.isNotEmpty()) { "Vui lòng chọn lớp học." }
                    require(subjectName.isNotBlank()) { "Vui lòng nhập tên môn học." }
                    require(selectedLecturerId.isNotBlank()) { "Vui lòng chọn giảng viên phụ trách." }
                    require(name.isNotBlank()) { "Vui lòng nhập tên buổi học." }

                    val session = AttendanceSession(
                        id = UUID.randomUUID().toString(),
                        class_id = selectedId,
                        session_name = name.trim(),
                        session_date = date,
                        start_time = timestamp(start),
                        late_after = timestamp(late),
                        end_time = timestamp(end),
                        status = if (open) "OPEN" else "SCHEDULED",
                        created_by = state.user!!.id,
                        subject_name = subjectName.trim(),
                        lecturer_id = selectedLecturerId
                    )
                    AttendanceRules.validateSession(session)
                    require(session.end_time > System.currentTimeMillis()) { "Giờ kết thúc phải ở tương lai." }
                    error = null
                    vm.createSession(session) { onCreated(session.id) }
                } catch (e: Exception) {
                    error = if (e is java.time.format.DateTimeParseException) "Ngày hoặc giờ sai định dạng." else (e.message ?: "Lỗi tạo buổi học.")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            enabled = canCreate
        ) {
            Text("Tạo buổi học", style = MaterialTheme.typography.titleSmall)
        }
    }
}
