package com.smartattendance.app.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartattendance.app.BuildConfig
import com.smartattendance.app.core.*
import com.smartattendance.app.domain.usecase.statusLabel
import com.smartattendance.app.ui.*

@Composable
fun SettingsScreen(vm: AppViewModel, state: AppState, navigate: (String) -> Unit) {
    var reset by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }

    Page {
        Heading("Cài đặt", "Quản lý tài khoản, giao diện và dữ liệu ứng dụng.")

        // User Account Profile Card
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
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = state.user?.full_name.orEmpty().ifBlank { "Người dùng" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = state.user?.email.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(state.user?.role.orEmpty())
            }
        }

        // Environment & Data Mode
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
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(22.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = if (state.demo) "Chế độ dữ liệu cục bộ (Room DB)" else "Kết nối máy chủ Supabase",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (state.demo) {
                            "Dữ liệu Demo lưu trực tiếp trong SQLite Room trên thiết bị, không tự đồng bộ với máy chủ."
                        } else {
                            "Mọi giao dịch quét thẻ và quyền truy cập được xác minh trực tiếp qua Supabase RPC."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Appearance Theme Choice
        Section("Giao diện & Trải nghiệm")
        Choice(
            label = "Chủ đề màu sắc",
            items = listOf("SYSTEM", "LIGHT", "DARK"),
            selected = state.theme,
            text = {
                when (it) {
                    "SYSTEM" -> "Theo hệ thống"
                    "LIGHT" -> "Chế độ sáng (Light)"
                    else -> "Chế độ tối (Dark)"
                }
            },
            onSelect = vm::theme
        )

        // Administrative link if role is ADMIN
        if (state.user?.role == "ADMIN") {
            Section("Quản trị hệ thống")
            GroupedCard {
                GroupedItem(
                    title = "Bảng điều khiển Quản trị",
                    subtitle = "Quản lý sinh viên, gán mã thẻ, tạo lớp, phân công giảng viên & tra cứu lịch sử",
                    icon = Icons.Outlined.AdminPanelSettings,
                    onClick = { navigate("admin") }
                )
            }
        }

        // Danger / Action Buttons
        Section("Thao tác dữ liệu")
        if (state.demo) {
            OutlinedButton(
                onClick = { reset = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                enabled = !state.busy
            ) {
                Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Khôi phục dữ liệu demo mẫu", style = MaterialTheme.typography.titleSmall)
            }
        }

        Button(
            onClick = { logout = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            enabled = !state.busy
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Đăng xuất / Chuyển chế độ", style = MaterialTheme.typography.titleSmall)
        }

        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "HUBT SA • Phiên bản ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Giải pháp điểm danh đại học bằng camera QR và mã vạch đa định dạng.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }

    if (reset) {
        Confirm(
            title = "Khôi phục dữ liệu demo?",
            message = "Toàn bộ thay đổi và bản ghi điểm danh hiện tại sẽ được khởi tạo lại theo kịch bản dữ liệu mẫu chuẩn.",
            onDismiss = { reset = false },
            onConfirm = {
                reset = false
                vm.resetDemo()
            }
        )
    }

    if (logout) {
        Confirm(
            title = "Đăng xuất tài khoản?",
            message = "Dữ liệu đã lưu trên thiết bị được giữ nguyên an toàn. Bạn có thể chọn đăng nhập lại hoặc vào Demo.",
            onDismiss = { logout = false },
            onConfirm = {
                logout = false
                vm.logout()
            }
        )
    }
}
