package com.smartattendance.app.feature.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.smartattendance.app.ui.theme.TabularNumbers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartattendance.app.core.*
import com.smartattendance.app.domain.model.*
import com.smartattendance.app.ui.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.UUID

@Composable fun AdminScreen(vm: AppViewModel, state: AppState) {
    if (state.user?.role != "ADMIN") {
        Page { EmptyState("Bạn không có quyền quản trị") }
        return
    }

    val scope = rememberCoroutineScope()
    val studentState by vm.studentState.collectAsState()
    var tab by rememberSaveable { mutableStateOf("students") }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedClassFilter by rememberSaveable { mutableStateOf("Tất cả") }
    var edit by remember { mutableStateOf<JsonObject?>(null) }
    var showingEditor by remember { mutableStateOf(false) }

    // CSV Import state
    var showImportChooser by remember { mutableStateOf(false) }
    var previewData by remember { mutableStateOf<ImportPreview?>(null) }
    var importMode by rememberSaveable { mutableStateOf("SKIP") }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var isReadingCsv by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isReadingCsv = true
                try {
                    val content = vm.readCsvFromUri(uri)
                    val (valid, errors) = StudentCsvImporter.parse(content)
                    previewData = StudentCsvImporter.buildPreview(valid, errors, state.data.students, state.data.classes)
                } catch (e: Exception) {
                    vm.notify(e.message ?: "Không đọc được tệp CSV.")
                } finally {
                    isReadingCsv = false
                }
            }
        }
    }

    val json = remember { Json { encodeDefaults = true } }

    fun label(value: String) = when (value) {
        "students" -> "Sinh viên"
        "classes" -> "Lớp học"
        "student_cards" -> "Thẻ sinh viên"
        "enrollments" -> "Danh sách lớp"
        "profiles" -> "Giảng viên / Tài khoản"
        else -> "Lịch sử thay đổi"
    }

    Page {
        Heading("Quản trị hệ thống", "Quản lý dữ liệu sinh viên, lớp học và phân quyền.")

        Choice(
            "Phân hệ quản trị",
            listOf("students", "classes", "student_cards", "enrollments", "profiles", "audit"),
            tab,
            ::label,
            { tab = it; query = ""; selectedClassFilter = "Tất cả" }
        )

        if (tab == "students") {
            // Summary Cards for Students tab
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    label = "TỔNG SỐ SINH VIÊN",
                    value = "${studentState.totalItems}",
                    icon = Icons.Outlined.Groups,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "TỔNG SỐ LỚP",
                    value = "${state.data.classes.size}",
                    icon = Icons.Outlined.School,
                    modifier = Modifier.weight(1f)
                )
            }

            // Action Buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { edit = null; showingEditor = true },
                    modifier = Modifier.weight(1f),
                    enabled = !state.busy
                ) {
                    Icon(Icons.Outlined.PersonAdd, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Thêm sinh viên")
                }
                FilledTonalButton(
                    onClick = { showImportChooser = true },
                    modifier = Modifier.weight(1f),
                    enabled = !state.busy && !isReadingCsv
                ) {
                    Icon(Icons.Outlined.FileUpload, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isReadingCsv) "Đang đọc…" else "Nhập từ CSV")
                }
            }

            // Search and Class Filter
            SearchField(
                value = studentState.searchQuery,
                onChange = { vm.onSearchQueryChanged(it) },
                label = "Tìm theo MASV, họ tên hoặc mã lớp"
            )

            val classOptions = remember(state.data.classes) {
                listOf("Tất cả") + state.data.classes.map { it.class_code }.distinct().sorted()
            }
            Choice(
                "Lọc theo lớp",
                classOptions,
                studentState.selectedClass,
                { it },
                { vm.onClassFilterChanged(it) }
            )

            // Page Size Selector (10, 20, 50, 100)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Hiển thị mỗi trang:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 20, 50, 100).forEach { size ->
                        FilterChip(
                            selected = studentState.pageSize == size,
                            onClick = { vm.setStudentPageSize(size) },
                            label = { Text("$size") }
                        )
                    }
                }
            }

            // Range & Page Count
            val startItem = if (studentState.totalItems == 0) 0 else (studentState.page - 1) * studentState.pageSize + 1
            val endItem = minOf(studentState.page * studentState.pageSize, studentState.totalItems)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Hiển thị $startItem–$endItem / ${studentState.totalItems} sinh viên",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Trang ${studentState.page} / ${studentState.totalPages}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (studentState.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (studentState.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            studentState.errorMessage ?: "Lỗi tải dữ liệu",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = { vm.retryLoadStudents() }) {
                            Icon(Icons.Outlined.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Thử lại")
                        }
                    }
                }
            } else if (!studentState.isLoading && studentState.items.isEmpty()) {
                EmptyState("Không tìm thấy sinh viên phù hợp")
            } else {
                studentState.items.forEachIndexed { index, student ->
                    val itemIndex = (studentState.page - 1) * studentState.pageSize + index + 1
                    InfoCard(
                        title = "$itemIndex. [${student.student_code}] ${student.full_name}",
                        detail = "Lớp: ${student.class_code.ifBlank { "Chưa xếp lớp" }}",
                        icon = Icons.Outlined.Person,
                        onClick = {
                            scope.launch {
                                val full = vm.getStudentById(student.id)
                                val obj = if (full != null) {
                                    json.encodeToJsonElement(full).jsonObject
                                } else {
                                    json.encodeToJsonElement(student).jsonObject
                                }
                                edit = obj
                                showingEditor = true
                            }
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Badge(student.status)
                                IconButton(onClick = {
                                    scope.launch {
                                        val full = vm.getStudentById(student.id)
                                        val obj = if (full != null) {
                                            json.encodeToJsonElement(full).jsonObject
                                        } else {
                                            json.encodeToJsonElement(student).jsonObject
                                        }
                                        edit = obj
                                        showingEditor = true
                                    }
                                }) {
                                    Icon(Icons.Outlined.Edit, "Chỉnh sửa")
                                }
                            }
                        }
                    )
                }
            }

            // Pagination Controls Footer
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { vm.goToFirstPage() },
                            enabled = studentState.hasPreviousPage && !studentState.isLoading
                        ) {
                            @Suppress("DEPRECATION")
                            Icon(Icons.Outlined.FirstPage, contentDescription = "Trang đầu")
                        }
                        IconButton(
                            onClick = { vm.goToPreviousPage() },
                            enabled = studentState.hasPreviousPage && !studentState.isLoading
                        ) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Trang trước")
                        }
                    }

                    Text(
                        "Trang ${studentState.page} / ${studentState.totalPages}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularNumbers),
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { vm.goToNextPage() },
                            enabled = studentState.hasNextPage && !studentState.isLoading
                        ) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "Trang tiếp theo")
                        }
                        IconButton(
                            onClick = { vm.goToLastPage() },
                            enabled = studentState.hasNextPage && !studentState.isLoading
                        ) {
                            @Suppress("DEPRECATION")
                            Icon(Icons.Outlined.LastPage, contentDescription = "Trang cuối")
                        }
                    }
                }
            }
        } else {
            // Other Admin Tabs
            SearchField(query, { query = it })

            if (tab != "audit" && (tab != "profiles" || state.demo)) {
                Button(onClick = { edit = null; showingEditor = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, null)
                    Text("Thêm ${label(tab).lowercase()}")
                }
            }

            if (tab == "profiles" && !state.demo) {
                Text(
                    "Tạo tài khoản Auth trong Supabase Dashboard, sau đó kích hoạt, đổi vai trò và phân công tại đây. Email đăng nhập được quản lý qua Supabase Auth.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val items = when (tab) {
                "classes" -> state.data.classes.map { Triple(it.class_code, "${it.class_name} • ${state.data.enrollments.count { e -> e.class_id == it.id && e.status == "ACTIVE" }} sinh viên", json.encodeToJsonElement(it).jsonObject) }
                "student_cards" -> state.data.cards.map { Triple(it.card_code, "${state.data.students.find { s -> s.id == it.student_id }?.full_name} • ${if (it.is_active) "Đang dùng" else "Vô hiệu"}", json.encodeToJsonElement(it).jsonObject) }
                "enrollments" -> state.data.enrollments.map { Triple(state.data.students.find { s -> s.id == it.student_id }?.full_name.orEmpty(), "${state.data.classes.find { c -> c.id == it.class_id }?.class_code} • ${it.status}", json.encodeToJsonElement(it).jsonObject) }
                "profiles" -> state.data.profiles.map { Triple(it.full_name, "${it.email} • ${it.role} • ${if (it.is_active) "Hoạt động" else "Chưa kích hoạt"}", json.encodeToJsonElement(it).jsonObject) }
                else -> emptyList()
            }.filter { "${it.first} ${it.second}".contains(query, true) }

            if (tab != "audit" && items.isEmpty()) EmptyState("Không có dữ liệu phù hợp")
            items.forEach { item ->
                InfoCard(item.first, item.second, Icons.Outlined.Edit, onClick = { edit = item.third; showingEditor = true })
            }

            if (tab == "audit") {
                Text("Hiển thị tối đa 200 thay đổi mới nhất.", style = MaterialTheme.typography.bodySmall)
                state.data.logs.filter { "${it.action} ${it.entity_type} ${it.reason}".contains(query, true) }.forEach { log ->
                    var expanded by remember(log.id) { mutableStateOf(false) }
                    InfoCard(
                        "${log.action} • ${log.entity_type}",
                        "${formatTime(log.created_at)}\n${state.data.profiles.find { it.id == log.actor_id }?.full_name ?: log.actor_id}\n${log.reason}",
                        Icons.Outlined.History,
                        onClick = { expanded = !expanded }
                    )
                    if (expanded) Text("ID: ${log.entity_id}\nTrước: ${log.old_value}\nSau: ${log.new_value}", style = MaterialTheme.typography.bodySmall)
                }
                if (state.data.logs.isEmpty()) EmptyState("Chưa có lịch sử thay đổi")
            }
        }
    }

    // CSV Source Chooser Dialog
    if (showImportChooser) {
        AlertDialog(
            onDismissRequest = { showImportChooser = false },
            title = { Text("Nhập danh sách sinh viên") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Chọn nguồn tệp CSV chứa danh sách sinh viên với 3 cột: MASV, HoTen, Lop.")
                    OutlinedButton(
                        onClick = {
                            showImportChooser = false
                            filePickerLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "application/vnd.ms-excel"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Chọn tệp CSV từ thiết bị…")
                    }
                    Button(
                        onClick = {
                            showImportChooser = false
                            scope.launch {
                                isReadingCsv = true
                                try {
                                    val content = vm.readSampleCsv()
                                    val (valid, errors) = StudentCsvImporter.parse(content)
                                    previewData = StudentCsvImporter.buildPreview(valid, errors, state.data.students, state.data.classes)
                                } catch (e: Exception) {
                                    vm.notify("Lỗi đọc tệp mẫu: ${e.message}")
                                } finally {
                                    isReadingCsv = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.School, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Dùng tệp mẫu tinhoc28.csv (1.084 SV)")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImportChooser = false }) { Text("Đóng") }
            }
        )
    }

    // CSV Import Preview Dialog
    previewData?.let { preview ->
        AlertDialog(
            onDismissRequest = { previewData = null },
            title = { Text("Xem trước dữ liệu CSV") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Tổng số dòng hợp lệ: ${preview.totalRows}", fontWeight = FontWeight.Bold)
                            Text("• Sinh viên mới: ${preview.newCount}")
                            Text("• Sinh viên đã tồn tại: ${preview.existingCount}")
                            Text("• Tổng số lớp phát hiện: ${preview.detectedClasses.size}")
                            if (preview.errorRows.isNotEmpty()) {
                                Text("• Số dòng lỗi/bỏ qua: ${preview.errorRows.size}", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Section("Các lớp được phát hiện (${preview.detectedClasses.size} lớp)")
                    Text(
                        preview.detectedClasses.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Hệ thống sẽ tự động tạo lớp học nếu chưa tồn tại và xếp sinh viên vào đúng danh sách lớp.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Section("Chế độ xử lý trùng mã sinh viên")
                    listOf(
                        "SKIP" to "Giữ nguyên bản ghi cũ (bỏ qua mã đã có)",
                        "UPDATE" to "Cập nhật họ tên và lớp (không đổi lịch sử điểm danh)",
                        "INSERT" to "Chỉ thêm sinh viên mới (báo lỗi nếu trùng)"
                    ).forEach { (code, desc) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = importMode == code, onClick = { importMode = code })
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(code, fontWeight = FontWeight.Bold)
                                Text(desc, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (preview.errorRows.isNotEmpty()) {
                        Section("Chi tiết lỗi (${preview.errorRows.size} dòng)")
                        preview.errorRows.take(10).forEach { err ->
                            Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (preview.errorRows.size > 10) {
                            Text("… và ${preview.errorRows.size - 10} dòng lỗi khác", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toImport = preview.validRows
                        previewData = null
                        vm.importStudents(toImport, importMode) { res ->
                            importResult = res
                        }
                    },
                    enabled = !state.busy && preview.validRows.isNotEmpty()
                ) {
                    Text("Tiến hành nhập (${preview.validRows.size} SV)")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewData = null }) { Text("Hủy") }
            }
        )
    }

    // CSV Import Result Dialog
    importResult?.let { res ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("Kết quả nhập danh sách") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Đã hoàn tất nhập sinh viên từ CSV:", fontWeight = FontWeight.SemiBold)
                    Text("• Tổng số dòng xử lý: ${res.totalProcessed}")
                    Text("• Sinh viên mới được thêm: ${res.newStudents}")
                    Text("• Sinh viên được cập nhật: ${res.updatedStudents}")
                    Text("• Lớp học mới được tạo: ${res.newClasses}")
                    Text("• Phân lớp (enrollment) được tạo: ${res.newEnrollments}")
                }
            },
            confirmButton = {
                Button(onClick = { importResult = null }) { Text("Hoàn tất") }
            }
        )
    }

    // Single item edit/create
    if (showingEditor) {
        AdminEditor(
            entity = tab,
            existing = edit,
            state = state,
            onDismiss = { showingEditor = false }
        ) { payload, classCode ->
            showingEditor = false
            if (tab == "students" && classCode != null) {
                val masv = payload["student_code"]?.jsonPrimitive?.content.orEmpty()
                val hoTen = payload["full_name"]?.jsonPrimitive?.content.orEmpty()
                vm.saveStudentWithClass(masv, hoTen, classCode) { vm.refresh() }
            } else {
                vm.administer(tab, payload) { vm.refresh() }
            }
        }
    }
}
