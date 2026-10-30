package com.smartattendance.app.feature.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartattendance.app.core.AppState
import com.smartattendance.app.domain.model.*
import com.smartattendance.app.ui.Choice
import kotlinx.serialization.json.*
import java.util.UUID

@Composable fun AdminEditor(
    entity: String,
    existing: JsonObject?,
    state: AppState,
    onDismiss: () -> Unit,
    onSave: (JsonObject, String?) -> Unit
) {
    val existingId = existing?.get("id")?.jsonPrimitive?.content
    val existingClass = remember(entity, existingId) {
        if (entity == "students" && existingId != null) {
            state.data.studentClasses(existingId).firstOrNull()
        } else null
    }

    val values = remember(entity, existing) {
        mutableStateMapOf<String, String>().apply {
            existing?.forEach { (k, v) -> put(k, v.jsonPrimitive.content) }
            putIfAbsent("id", UUID.randomUUID().toString())
            putIfAbsent("created_at", System.currentTimeMillis().toString())
            putIfAbsent("status", "ACTIVE")
            putIfAbsent("is_active", "true")
            putIfAbsent("barcode_format", "QR_CODE")
            putIfAbsent("role", "LECTURER")
            if (entity == "students") {
                val resolvedClassCode = existing?.get("class_code")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: existingClass?.class_code.orEmpty()
                put("class_code", resolvedClassCode)
                put("class_id", existingClass?.id.orEmpty())
            }
        }
    }
    var error by remember { mutableStateOf<String?>(null) }

    @Composable fun field(key: String, label: String, enabled: Boolean = true) {
        OutlinedTextField(
            value = values[key].orEmpty(),
            onValueChange = { values[key] = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            enabled = enabled
        )
    }

    @Composable fun active(boolean: Boolean = false) {
        val key = if (boolean) "is_active" else "status"
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Trạng thái hoạt động")
            Switch(
                checked = if (boolean) values[key] == "true" else values[key] == "ACTIVE",
                onCheckedChange = { values[key] = if (boolean) it.toString() else if (it) "ACTIVE" else "INACTIVE" }
            )
        }
    }

    @Composable fun studentChoice() {
        Choice(
            "Sinh viên",
            state.data.students,
            state.data.students.find { it.id == values["student_id"] },
            { "${it.student_code} • ${it.full_name}" },
            { values["student_id"] = it.id }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Thêm dữ liệu" else "Chỉnh sửa dữ liệu") },
        text = {
            Column(
                Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (entity) {
                    "students" -> {
                        // Exactly 3 fields: MASV, HoTen, Lop
                        field("student_code", "Mã sinh viên (MASV) *")
                        field("full_name", "Họ và tên (HoTen) *")
                        field("class_code", "Mã lớp (Lop) *")
                        if (state.data.classes.isNotEmpty()) {
                            Text("Hoặc chọn lớp có sẵn:", style = MaterialTheme.typography.bodySmall)
                            Choice(
                                "Chọn từ danh sách lớp",
                                state.data.classes,
                                state.data.classes.find { it.class_code == values["class_code"] },
                                { "${it.class_code} • ${it.class_name}" },
                                { values["class_code"] = it.class_code }
                            )
                        }
                        active()
                    }
                    "classes" -> {
                        // Only class_code and class_name
                        field("class_code", "Mã lớp (ví dụ: PM28.02) *")
                        field("class_name", "Tên lớp (tùy chọn, mặc định như mã lớp)")
                    }
                    "student_cards" -> {
                        studentChoice()
                        field("card_code", "Nội dung mã thẻ (rawValue) *")
                        Choice("Định dạng", listOf("QR_CODE", "CODE_128", "CODE_39", "EAN_13"), values["barcode_format"], { it }, { values["barcode_format"] = it })
                        active(true)
                        Text("Để thay thẻ, vô hiệu thẻ cũ rồi thêm thẻ mới.", style = MaterialTheme.typography.bodySmall)
                    }
                    "enrollments" -> {
                        studentChoice()
                        Choice("Lớp học", state.data.classes, state.data.classes.find { it.id == values["class_id"] }, { "${it.class_code} • ${it.class_name}" }, { values["class_id"] = it.id })
                        active()
                        Text("Thay đổi chỉ ảnh hưởng các buổi tạo sau này.", style = MaterialTheme.typography.bodySmall)
                    }
                    "profiles" -> {
                        field("full_name", "Họ tên *")
                        field("email", "Email", state.demo)
                        Choice("Vai trò", listOf("LECTURER", "ADMIN"), values["role"], { if (it == "ADMIN") "Quản trị viên" else "Giảng viên" }, { values["role"] = it })
                        active(true)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        fun value(key: String) = values[key].orEmpty().trim()
                        val id = value("id")
                        val created = value("created_at").toLongOrNull() ?: System.currentTimeMillis()
                        val json = Json { encodeDefaults = true }
                        val payload = when (entity) {
                            "students" -> {
                                val code = value("student_code")
                                val name = value("full_name")
                                val group = value("class_code")
                                require(code.matches(Regex("[A-Za-z0-9_-]{3,40}"))) {
                                    "Mã sinh viên (3-40 ký tự) không hợp lệ."
                                }
                                require(name.isNotBlank()) {
                                    "Họ tên sinh viên không được để trống."
                                }
                                require(group.isNotBlank()) {
                                    "Lớp của sinh viên không được để trống."
                                }
                                json.encodeToJsonElement(
                                    Student(
                                        id = id,
                                        student_code = code,
                                        full_name = name,
                                        status = value("status"),
                                        created_at = created
                                    )
                                )
                            }
                            "classes" -> {
                                val code = value("class_code")
                                require(code.isNotEmpty()) { "Vui lòng nhập mã lớp." }
                                val name = value("class_name").ifBlank { code }
                                json.encodeToJsonElement(
                                    SchoolClass(
                                        id = id,
                                        class_code = code,
                                        class_name = name,
                                        subject_name = null,
                                        lecturer_id = null,
                                        semester = "",
                                        academic_year = "",
                                        created_at = created
                                    )
                                )
                            }
                            "student_cards" -> {
                                require(value("student_id").isNotEmpty() && value("card_code").isNotEmpty()) { "Vui lòng chọn sinh viên và nhập mã thẻ." }
                                json.encodeToJsonElement(StudentCard(id, value("student_id"), values["card_code"].orEmpty(), value("barcode_format"), value("is_active").toBoolean(), created))
                            }
                            "enrollments" -> {
                                require(value("student_id").isNotEmpty() && value("class_id").isNotEmpty()) { "Vui lòng chọn sinh viên và lớp học." }
                                json.encodeToJsonElement(Enrollment(id, value("class_id"), value("student_id"), value("status"), created))
                            }
                            else -> {
                                require(value("full_name").isNotEmpty() && value("email").contains("@")) { "Vui lòng nhập họ tên và email hợp lệ." }
                                json.encodeToJsonElement(Profile(id, value("full_name"), value("email"), value("role"), value("is_active").toBoolean(), created))
                            }
                        }
                        error = null
                        val classCode = values["class_code"]?.takeIf { it.isNotBlank() }
                        onSave(payload.jsonObject, classCode)
                    } catch (e: Exception) {
                        error = e.message ?: "Vui lòng nhập đủ thông tin hợp lệ."
                    }
                },
                enabled = !state.busy
            ) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
