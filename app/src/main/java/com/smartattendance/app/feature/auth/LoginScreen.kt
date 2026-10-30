package com.smartattendance.app.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.smartattendance.app.R
import com.smartattendance.app.core.AppViewModel
import com.smartattendance.app.data.local.DemoSeed
import com.smartattendance.app.ui.*

@Composable
fun LoginScreen(vm: AppViewModel, busy: Boolean) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Page {
        Spacer(Modifier.height(12.dp))

        // Branding Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.app_logo),
                        contentDescription = "HUBT SA Logo",
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
            Text(
                text = "HUBT SA",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Một lần quét. Một lớp học kết nối.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(Modifier.height(8.dp))
        Heading("Chào mừng trở lại", "Đăng nhập để quản lý lớp và điểm danh sinh viên.")

        if (!vm.onlineConfigured) {
            DemoRoleSection(vm, busy)
            Text(
                text = "Chưa cấu hình Supabase. Demo có đầy đủ chức năng với dữ liệu lưu trên thiết bị.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        }

        // Production Login Form
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email giảng viên") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.MailOutline, null, modifier = Modifier.size(20.dp)) },
                    shape = RoundedCornerShape(10.dp),
                    enabled = !busy
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mật khẩu") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (visible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    enabled = !busy
                )

                Button(
                    onClick = { vm.login(email, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !busy && vm.onlineConfigured && email.isNotBlank() && password.isNotBlank()
                ) {
                    Text("Đăng nhập tài khoản", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        if (vm.onlineConfigured) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            DemoRoleSection(vm, busy)
        }

        Text(
            text = "Dữ liệu demo chỉ lưu trữ trong cơ sở dữ liệu Room cục bộ trên thiết bị.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun DemoRoleSection(vm: AppViewModel, busy: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "TRẢI NGHIỆM NHANH BẰNG DỮ LIỆU DEMO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )

        GroupedCard {
            DemoRoleItem(
                title = "Quản trị viên (ADMIN)",
                roleDescription = "Toàn quyền quản lý sinh viên, thẻ, lớp và báo cáo",
                icon = Icons.Outlined.AdminPanelSettings,
                enabled = !busy,
                onClick = { vm.demo() },
                showDivider = true
            )
            DemoRoleItem(
                title = "GV Minh Anh (LECTURER)",
                roleDescription = "Môn Lập trình Di động & Thiết kế Hệ thống",
                icon = Icons.Outlined.Person,
                enabled = !busy,
                onClick = { vm.demo(DemoSeed.LECTURER) },
                showDivider = true
            )
            DemoRoleItem(
                title = "GV Hoàng Nam (LECTURER)",
                roleDescription = "Môn Cơ sở Dữ liệu Nâng cao",
                icon = Icons.Outlined.PersonOutline,
                enabled = !busy,
                onClick = { vm.demo(DemoSeed.LECTURER_TWO) },
                showDivider = false
            )
        }
    }
}

@Composable
private fun DemoRoleItem(
    title: String,
    roleDescription: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean
) {
    val clickModifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(clickModifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = roleDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
            )
        }
    }
}
