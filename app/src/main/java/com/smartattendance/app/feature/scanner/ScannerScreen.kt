package com.smartattendance.app.feature.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartattendance.app.core.*
import com.smartattendance.app.domain.usecase.*
import com.smartattendance.app.ui.*
import com.smartattendance.app.ui.theme.TabularNumbers

@Composable
fun ScannerScreen(
    id: String,
    appVm: AppViewModel,
    state: AppState,
    navigate: (String) -> Unit
) {
    val vm: ScannerViewModel = viewModel(key = "scanner-$id")
    val scanState by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    var permission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var requested by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it
        requested = true
    }

    var torch by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55) }.getOrNull() }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                appVm.refresh()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            tone?.release()
        }
    }

    LaunchedEffect(Unit) {
        if (!permission && !requested) {
            requested = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(scanState.result) {
        if (scanState.result?.code in setOf("PRESENT", "LATE")) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            tone?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        }
    }

    val session = state.data.sessions.find { it.id == id } ?: return
    val classCode = state.data.classes.find { it.id == session.class_id }?.class_code.orEmpty()

    // Infinite animation for laser beam
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserOffset"
    )

    Page {
        Heading(
            title = "Quét thẻ sinh viên",
            subtitle = if (classCode.isNotBlank()) "${session.session_name} • Lớp $classCode" else session.session_name
        )

        if (session.status != "OPEN") {
            EmptyState(
                text = "Buổi học chưa mở hoặc đã đóng",
                detail = "Vui lòng mở một buổi học hợp lệ để bắt đầu quét thẻ."
            )
        } else if (!permission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NoPhotography,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(14.dp)
                                .size(32.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = "Cần quyền truy cập Camera",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Cho phép ứng dụng sử dụng camera để quét mã QR và mã vạch thẻ sinh viên một cách chính xác.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { launcher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cấp quyền camera")
                    }
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    ) {
                        Text("Mở cài đặt ứng dụng")
                    }
                }
            }
        } else {
            // Camera Viewfinder HUD Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Live Camera Stream
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    torch = torch,
                    busy = scanState.busy,
                    onCode = { raw -> vm.scan(raw) { appVm.scan(id, it) } },
                    onReady = {
                        hasFlash = it
                        ready = true
                        cameraError = null
                    },
                    onError = { cameraError = it }
                )

                // Aperture Overlay Mask with Corner Reticles and Oscillating Laser Line
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val targetWidth = 260.dp.toPx()
                    val targetHeight = 180.dp.toPx()
                    val left = (size.width - targetWidth) / 2f
                    val top = (size.height - targetHeight) / 2f
                    val right = left + targetWidth
                    val bottom = top + targetHeight

                    val dimColor = Color.Black.copy(alpha = 0.55f)

                    // 1. Dimmed outer periphery (4 non-overlapping rectangles)
                    drawRect(dimColor, Offset(0f, 0f), Size(size.width, top))
                    drawRect(dimColor, Offset(0f, bottom), Size(size.width, size.height - bottom))
                    drawRect(dimColor, Offset(0f, top), Size(left, targetHeight))
                    drawRect(dimColor, Offset(right, top), Size(size.width - right, targetHeight))

                    // 2. Corner Reticles (Precision L-brackets)
                    val bracketLength = 24.dp.toPx()
                    val reticleColor = Color(0xFF65D8CB)
                    val strokeWidth = 3.5.dp.toPx()

                    // Top-Left
                    drawLine(reticleColor, Offset(left, top + bracketLength), Offset(left, top), strokeWidth, StrokeCap.Round)
                    drawLine(reticleColor, Offset(left, top), Offset(left + bracketLength, top), strokeWidth, StrokeCap.Round)

                    // Top-Right
                    drawLine(reticleColor, Offset(right - bracketLength, top), Offset(right, top), strokeWidth, StrokeCap.Round)
                    drawLine(reticleColor, Offset(right, top), Offset(right, top + bracketLength), strokeWidth, StrokeCap.Round)

                    // Bottom-Left
                    drawLine(reticleColor, Offset(left, bottom - bracketLength), Offset(left, bottom), strokeWidth, StrokeCap.Round)
                    drawLine(reticleColor, Offset(left, bottom), Offset(left + bracketLength, bottom), strokeWidth, StrokeCap.Round)

                    // Bottom-Right
                    drawLine(reticleColor, Offset(right - bracketLength, bottom), Offset(right, bottom), strokeWidth, StrokeCap.Round)
                    drawLine(reticleColor, Offset(right, bottom), Offset(right, bottom - bracketLength), strokeWidth, StrokeCap.Round)

                    // 3. Oscillating Laser Beam Sweep
                    val currentLaserY = top + (targetHeight * laserOffset)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xFF65D8CB).copy(alpha = 0.8f),
                                Color(0xFF007E87),
                                Color(0xFF65D8CB).copy(alpha = 0.8f),
                                Color.Transparent
                            ),
                            startX = left,
                            endX = right
                        ),
                        topLeft = Offset(left + 2.dp.toPx(), currentLaserY - 1.5.dp.toPx()),
                        size = Size(targetWidth - 4.dp.toPx(), 3.dp.toPx())
                    )
                }

                // Top Floating Status Pill
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val dotColor = when {
                            scanState.busy -> Color(0xFF60A5FA)
                            ready -> Color(0xFF34D399)
                            else -> Color(0xFFFBBF24)
                        }
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(dotColor, CircleShape)
                        )
                        Text(
                            text = when {
                                scanState.busy -> "Đang lưu điểm danh…"
                                ready -> "Camera sẵn sàng"
                                else -> "Đang mở camera…"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                // Top-Right Torch Toggle Button
                if (hasFlash) {
                    FilledTonalIconButton(
                        onClick = { torch = !torch },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = if (torch) Color(0xFFFBBF24) else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (torch) Icons.Outlined.FlashOff else Icons.Outlined.FlashOn,
                            contentDescription = if (torch) "Tắt đèn" else "Bật đèn",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Bottom Hint
                Text(
                    text = "Đặt mã thẻ sinh viên vào giữa khung quét",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }

            if (scanState.busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Camera Errors
        cameraError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        // Network / Server Connection Issue Card
        scanState.error?.let {
            val title = if (it.contains("kết nối", ignoreCase = true) || it.contains("mạng", ignoreCase = true)) {
                "MẤT KẾT NỐI MÁY CHỦ"
            } else {
                "CHƯA GHI NHẬN ĐƯỢC ĐIỂM DANH"
            }
            InfoCard(title, it, Icons.Outlined.CloudOff)
        }

        // Instant Result Feedback Card with Spring Animation
        scanState.result?.let { result ->
            val studentClass = result.student?.let { state.data.studentClassCodes(it.id) }
            val isSuccess = result.code in setOf("PRESENT", "LATE")
            val resultBorder = BorderStroke(
                1.dp,
                if (isSuccess) Color(0xFF087B69).copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            )

            AnimatedVisibility(
                visible = true,
                enter = scaleIn(animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)) + fadeIn()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = resultBorder,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSuccess) Color(0xFF087B69) else MaterialTheme.colorScheme.error
                                ) {
                                    Icon(
                                        imageVector = if (isSuccess) Icons.Outlined.Check else Icons.Outlined.Close,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(16.dp)
                                    )
                                }
                                Text(
                                    text = scanMessage(result.code),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            StatusBadge(result.code)
                        }

                        if (result.student != null) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = result.student.full_name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "MASV: ${result.student.student_code}${if (!studentClass.isNullOrBlank()) " • Lớp: $studentClass" else ""}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularNumbers),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                result.record?.let { rec ->
                                    Text(
                                        text = formatTime(rec.scanned_at),
                                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TabularNumbers),
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Hỗ trợ QR Code, Code 128, Code 39 và EAN-13. Sau khi có tín hiệu, hãy đưa thẻ tiếp theo vào khung quét.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedButton(
            onClick = { navigate("attendance/$id") },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Xem danh sách điểm danh", style = MaterialTheme.typography.titleSmall)
        }
    }
}
