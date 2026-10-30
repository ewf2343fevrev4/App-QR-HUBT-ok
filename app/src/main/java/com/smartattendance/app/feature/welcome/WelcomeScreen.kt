package com.smartattendance.app.feature.welcome

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*
import kotlin.random.Random

// ---------------------------------------------------------------------------
// 1. STAGE SPECIFICATIONS & VIETNAMESE COPYWRITING
// ---------------------------------------------------------------------------

data class OnboardingStage(
    val title: String,
    val subtitle: String,
    val primaryColor: Color,
    val accentColor: Color,
    val glowColor: Color
)

val ONBOARDING_STAGES = listOf(
    // Stage 1: HUBT University Logo
    OnboardingStage(
        title = "HUBT Smart Attendance",
        subtitle = "Nền tảng quản lý lớp học và điểm danh thông minh dành cho ĐH Kinh doanh & Công nghệ Hà Nội.",
        primaryColor = Color(0xFF0E6BA8),
        accentColor = Color(0xFF38BDF8),
        glowColor = Color(0xFF1687A7)
    ),
    // Stage 2: Smart QR Code Symbol
    OnboardingStage(
        title = "Mã QR Định Danh Duy Nhất",
        subtitle = "Mỗi sinh viên và lớp học được mã hóa bằng định danh QR bảo mật, sẵn sàng kết nối tức thì.",
        primaryColor = Color(0xFF00E5FF),
        accentColor = Color(0xFF00B4D8),
        glowColor = Color(0xFF00E5FF)
    ),
    // Stage 3: Smartphone with Active Camera Scan
    OnboardingStage(
        title = "Một Lần Quét. Kết Nối Lớp Học.",
        subtitle = "Mở camera quét nhanh thẻ sinh viên chỉ trong tích tắc mà không cần thiết bị rườm rà.",
        primaryColor = Color(0xFF38BDF8),
        accentColor = Color(0xFF10B981),
        glowColor = Color(0xFF10B981)
    ),
    // Stage 4: Precision Checkmark "V" Icon
    OnboardingStage(
        title = "Điểm Danh Chuẩn Xác Tuyệt Đối",
        subtitle = "Xác thực tức thời, tổng hợp báo cáo tự động và minh bạch hóa dữ liệu chuyên cần.",
        primaryColor = Color(0xFF10B981),
        accentColor = Color(0xFF34D399),
        glowColor = Color(0xFF10B981)
    )
)

// ---------------------------------------------------------------------------
// 2. ETHEREAL 3D CONSTELLATION & ANAMORPHIC STREAK PARTICLE CLOUD
// (Matches the exact cinematic look from the reference video @0:00:01)
// ---------------------------------------------------------------------------

class EtherealConstellationCloud {
    val nodeCount = 150
    val rawX = FloatArray(nodeCount)
    val rawY = FloatArray(nodeCount)
    val rawZ = FloatArray(nodeCount)
    val burstDirX = FloatArray(nodeCount)
    val burstDirY = FloatArray(nodeCount)
    val burstDirZ = FloatArray(nodeCount)
    val streakLength = FloatArray(nodeCount)
    val pulsePhase = FloatArray(nodeCount)

    val projX = FloatArray(nodeCount)
    val projY = FloatArray(nodeCount)
    val projZ = FloatArray(nodeCount)
    val projScale = FloatArray(nodeCount)
    val projAlpha = FloatArray(nodeCount)

    init {
        val rand = Random(1337)
        val Rx = 1.15f
        val Ry = 0.85f
        val Rz = 1.15f
        for (i in 0 until nodeCount) {
            val phi = acos(rand.nextDouble(-1.0, 1.0).toFloat())
            val theta = rand.nextDouble(0.0, 2.0 * Math.PI).toFloat()
            val r = rand.nextDouble(0.80, 1.15).toFloat()
            rawX[i] = r * Rx * sin(phi) * cos(theta)
            rawY[i] = r * Ry * cos(phi)
            rawZ[i] = r * Rz * sin(phi) * sin(theta)
            burstDirX[i] = sin(phi) * cos(theta)
            burstDirY[i] = cos(phi)
            burstDirZ[i] = sin(phi) * sin(theta)
            streakLength[i] = rand.nextDouble(10.0, 26.0).toFloat()
            pulsePhase[i] = rand.nextDouble(0.0, 2.0 * Math.PI).toFloat()
        }
    }

    fun updateAndDraw(
        scope: DrawScope,
        time: Float,
        centerX: Float,
        centerY: Float,
        baseScale: Float,
        cloudAlpha: Float,
        contractFactor: Float,
        scatterOffset: Float = 0f,
        primaryColor: Color = Color(0xFF00E5FF),
        accentColor: Color = Color(0xFF38BDF8)
    ) {
        if (cloudAlpha <= 0.01f) return

        val alpha = time * 0.55f
        val beta = sin(time * 0.75f) * 0.16f
        val cosA = cos(alpha)
        val sinA = sin(alpha)
        val cosB = cos(beta)
        val sinB = sin(beta)
        val cameraD = 3.6f

        // 1. Project 3D points with radial dispersion / convergence offset
        for (i in 0 until nodeCount) {
            val x = (rawX[i] + burstDirX[i] * scatterOffset) * contractFactor
            val y = (rawY[i] + burstDirY[i] * scatterOffset) * contractFactor
            val z = (rawZ[i] + burstDirZ[i] * scatterOffset) * contractFactor

            val x1 = x * cosA + z * sinA
            val z1 = -x * sinA + z * cosA
            val y2 = y * cosB - z1 * sinB
            val z2 = y * sinB + z1 * cosB

            val scale = cameraD / (cameraD - z2)
            projX[i] = centerX + x1 * scale * baseScale
            projY[i] = centerY + y2 * scale * baseScale
            projZ[i] = z2
            projScale[i] = scale

            val normZ = ((z2 + 1.2f) / 2.4f).coerceIn(0.1f, 1.0f)
            val shimmer = 0.85f + 0.15f * sin(time * 2.8f + pulsePhase[i])
            projAlpha[i] = normZ * shimmer * cloudAlpha
        }

        // 2. Delicate hairline constellation links
        val maxDist = 36f * (scope.size.width / 400f)
        for (i in 0 until nodeCount) {
            var conns = 0
            val p1x = projX[i]
            val p1y = projY[i]
            for (j in (i + 1) until nodeCount) {
                if (conns >= 2) break
                val p2x = projX[j]
                val p2y = projY[j]
                val dx = p1x - p2x
                val dy = p1y - p2y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < maxDist) {
                    val lineAlpha = (1f - dist / maxDist) * ((projAlpha[i] + projAlpha[j]) / 2f) * 0.45f
                    if (lineAlpha > 0.02f) {
                        scope.drawLine(
                            color = primaryColor.copy(alpha = lineAlpha),
                            start = Offset(p1x, p1y),
                            end = Offset(p2x, p2y),
                            strokeWidth = 1.0f
                        )
                        conns++
                    }
                }
            }
        }

        // 3. Horizontal light streaks & bright pinpricks
        for (i in 0 until nodeCount) {
            val px = projX[i]
            val py = projY[i]
            val sc = projScale[i]
            val a = projAlpha[i]

            // Soft horizontal anamorphic streak
            val slen = streakLength[i] * sc * (scope.size.width / 400f)
            scope.drawLine(
                color = accentColor.copy(alpha = (a * 0.40f).coerceIn(0f, 1f)),
                start = Offset(px - slen, py),
                end = Offset(px + slen, py),
                strokeWidth = 1.2f
            )

            // Soft glow
            val rGlow = 2.8f * sc
            scope.drawCircle(
                color = primaryColor.copy(alpha = (a * 0.30f).coerceIn(0f, 1f)),
                radius = rGlow,
                center = Offset(px, py)
            )

            // Pure bright star core
            val rCore = 1.0f * sc
            scope.drawCircle(
                color = Color(0xFFF0F9FF).copy(alpha = a.coerceIn(0f, 1f)),
                radius = rCore,
                center = Offset(px, py)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. LOGO TRANSITION HELPERS: DISPERSAL OUTWARD & CONVERGENCE INWARD
// ---------------------------------------------------------------------------

private fun getStageAnim(stageIndex: Int, current: Int, previous: Int, p: Float): Pair<Float, Float> {
    if (current == previous) {
        return if (stageIndex == current) Pair(1f, 1f) else Pair(0f, 1f)
    }
    return when (stageIndex) {
        previous -> {
            // Outgoing logo: disperses and dissolves as particles scatter outward (p: 0.0 -> 0.38)
            if (p < 0.38f) {
                val prog = p / 0.38f
                val alpha = (1f - prog).coerceIn(0f, 1f)
                val scale = 1f + prog * 0.12f
                Pair(alpha, scale)
            } else {
                Pair(0f, 1.12f)
            }
        }
        current -> {
            // Incoming logo: gathers and materializes as particles converge inward (p: 0.52 -> 1.0)
            if (p < 0.52f) {
                Pair(0f, 0.85f)
            } else {
                val prog = ((p - 0.52f) / 0.48f).coerceIn(0f, 1f)
                val alpha = prog
                val scale = 0.85f + prog * 0.15f
                Pair(alpha, scale)
            }
        }
        else -> Pair(0f, 1f)
    }
}

private fun drawTransitionSwarm(
    scope: DrawScope,
    p: Float,
    centerX: Float,
    centerY: Float,
    canvasW: Float,
    primaryColor: Color,
    accentColor: Color
) {
    if (p <= 0.01f || p >= 0.99f) return

    val sparkCount = 44
    val maxRadius = canvasW * 0.44f
    val minRadius = canvasW * 0.14f

    // 1. Dispersion Sparks (p: 0.0 -> 0.42): Small particles explode outward from the departing logo
    if (p < 0.42f) {
        val disperseProgress = p / 0.42f
        val burstAlpha = (1f - disperseProgress).coerceIn(0f, 1f)
        val easeProgress = 1f - (1f - disperseProgress) * (1f - disperseProgress)

        for (i in 0 until sparkCount) {
            val angle = i * (2f * Math.PI.toFloat() / sparkCount) + (i % 7) * 0.15f
            val dist = minRadius + easeProgress * (maxRadius - minRadius) * (0.85f + (i % 5) * 0.08f)
            val px = centerX + cos(angle) * dist
            val py = centerY + sin(angle) * dist

            val trailDist = dist - 16f * (1f - easeProgress * 0.5f)
            val tx = centerX + cos(angle) * trailDist
            val ty = centerY + sin(angle) * trailDist

            scope.drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, accentColor.copy(alpha = burstAlpha * 0.85f)),
                    start = Offset(tx, ty),
                    end = Offset(px, py)
                ),
                start = Offset(tx, ty),
                end = Offset(px, py),
                strokeWidth = 2.2f
            )
            scope.drawCircle(
                color = Color.White.copy(alpha = burstAlpha),
                radius = 2.2f,
                center = Offset(px, py)
            )
        }
    }

    // 2. Convergence Sparks (p: 0.35 -> 0.82): Small particles rush inward to form the next logo
    if (p in 0.35f..0.82f) {
        val convergeProgress = (p - 0.35f) / 0.47f
        val gatherAlpha = when {
            convergeProgress < 0.20f -> convergeProgress / 0.20f
            convergeProgress > 0.80f -> (1f - convergeProgress) / 0.20f
            else -> 1.0f
        }.coerceIn(0f, 1f)

        val easeIn = convergeProgress * convergeProgress

        for (i in 0 until sparkCount) {
            val spiralAngle = i * (2f * Math.PI.toFloat() / sparkCount) + easeIn * 1.8f
            val dist = maxRadius - easeIn * (maxRadius - minRadius * 0.6f) * (0.90f + (i % 4) * 0.07f)
            val px = centerX + cos(spiralAngle) * dist
            val py = centerY + sin(spiralAngle) * dist

            val headDist = dist - 18f * (1f - easeIn * 0.4f)
            val hx = centerX + cos(spiralAngle + 0.12f) * headDist
            val hy = centerY + sin(spiralAngle + 0.12f) * headDist

            scope.drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(primaryColor.copy(alpha = gatherAlpha * 0.85f), Color.Transparent),
                    start = Offset(px, py),
                    end = Offset(hx, hy)
                ),
                start = Offset(px, py),
                end = Offset(hx, hy),
                strokeWidth = 2.2f
            )
            scope.drawCircle(
                color = Color.White.copy(alpha = gatherAlpha),
                radius = 2.2f,
                center = Offset(px, py)
            )
            scope.drawCircle(
                color = accentColor.copy(alpha = gatherAlpha * 0.45f),
                radius = 4.5f,
                center = Offset(px, py)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 4. MAIN WELCOME SCREEN COMPOSABLE
// ---------------------------------------------------------------------------

@Composable
fun WelcomeScreen(
    onExplore: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val view = LocalView.current
    val activity = context as? Activity

    DisposableEffect(view, activity) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatus = insetsController?.isAppearanceLightStatusBars ?: false
        val prevNav = insetsController?.isAppearanceLightNavigationBars ?: false
        insetsController?.isAppearanceLightStatusBars = false
        insetsController?.isAppearanceLightNavigationBars = false
        onDispose {
            insetsController?.isAppearanceLightStatusBars = prevStatus
            insetsController?.isAppearanceLightNavigationBars = prevNav
        }
    }

    // Load Bitmap for crystal-clear Stage 1 graphics
    var hubtLogoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val logoBmp = BitmapFactory.decodeResource(context.resources, R.drawable.hubt_official_logo)
                if (logoBmp != null) hubtLogoBitmap = logoBmp.asImageBitmap()
            } catch (_: Exception) {}
        }
    }

    var currentStage by remember { mutableIntStateOf(0) }
    var previousStage by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    var transitionJob by remember { mutableStateOf<Job?>(null) }

    val constellationCloud = remember { EtherealConstellationCloud() }

    // Morphing transition progress: 0f to 1f
    val transitionProgress = remember { Animatable(0f) }

    // Centralized, flicker-free stage transition function
    fun switchStage(newStage: Int) {
        if (newStage == currentStage && previousStage == currentStage) return
        val fromStage = currentStage
        transitionJob?.cancel()
        transitionJob = coroutineScope.launch {
            // Set departing stage to previousStage
            previousStage = fromStage
            // Snap progress to 0f FIRST so the new stage is NEVER rendered at 1.0f on frame 1
            transitionProgress.snapTo(0f)
            // Now set currentStage to new target
            currentStage = newStage
            // Run smooth 1250ms transition
            transitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 1250,
                    easing = CubicBezierEasing(0.20f, 0.05f, 0.20f, 1.0f)
                )
            )
            // Finalize state: both point to currentStage at rest
            previousStage = newStage
        }
    }

    // Initial entrance crystallization on mount
    LaunchedEffect(Unit) {
        transitionProgress.snapTo(0f)
        transitionProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 1250,
                easing = CubicBezierEasing(0.20f, 0.05f, 0.20f, 1.0f)
            )
        )
    }

    // Auto-advance timer (every 3.8 seconds), reset on user interaction
    var userInteractedTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(currentStage, userInteractedTime) {
        delay(3800)
        switchStage((currentStage + 1) % ONBOARDING_STAGES.size)
    }

    val frameTime by produceState(0f) {
        var startNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (startNanos == 0L) startNanos = nanos
                value = (nanos - startNanos) / 1_000_000_000f
            }
        }
    }

    val activeStageData = ONBOARDING_STAGES[currentStage]
    val animatedGlowColor by animateColorAsState(
        targetValue = activeStageData.glowColor,
        animationSpec = tween(650),
        label = "ambientGlow"
    )

    var totalDragX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF061017)) // Deep Obsidian canvas
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onDragEnd = {
                        if (totalDragX < -50f) {
                            userInteractedTime = System.currentTimeMillis()
                            switchStage((currentStage + 1) % ONBOARDING_STAGES.size)
                        } else if (totalDragX > 50f) {
                            userInteractedTime = System.currentTimeMillis()
                            switchStage((currentStage - 1 + ONBOARDING_STAGES.size) % ONBOARDING_STAGES.size)
                        }
                        totalDragX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount
                    }
                )
            }
    ) {
        // Subtle HUBT background pattern watermark at 3.5% opacity for rich depth
        Image(
            painter = painterResource(R.drawable.hubt_bg_dark),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.035f
        )

        // Dynamic Ambient Glow behind the particle cloud & card
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(340.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedGlowColor.copy(alpha = 0.22f),
                                animatedGlowColor.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.width * 0.5f
                        )
                    )
                }
        )

        // Top Modern Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0E222F).copy(alpha = 0.65f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1687A7).copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(activeStageData.primaryColor)
                    )
                    Text(
                        text = "HUBT SA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "•",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "SMART ATTENDANCE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        letterSpacing = 1.4.sp
                    )
                }
            }
        }

        // Center Visual Area: 3D Holographic Canvas & Ethereal Particle Swarm
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 260.dp, top = 70.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height
                val centerX = canvasW / 2.0f
                val centerY = canvasH / 2.0f
                val p = transitionProgress.value

                // Continuous 3D Perspective Floating
                val rotX = sin(frameTime * 1.15f) * 0.12f
                val rotY = cos(frameTime * 0.90f) * 0.15f

                val prevStageData = ONBOARDING_STAGES[previousStage]
                val currStageData = ONBOARDING_STAGES[currentStage]
                val activePrimary = if (currentStage == previousStage) currStageData.primaryColor else lerp(prevStageData.primaryColor, currStageData.primaryColor, p)
                val activeAccent = if (currentStage == previousStage) currStageData.accentColor else lerp(prevStageData.accentColor, currStageData.accentColor, p)

                val contractFactor: Float
                val scatterOffset: Float
                val cloudAlpha: Float
                val swirlBoost: Float

                if (currentStage == previousStage) {
                    contractFactor = 1.0f
                    scatterOffset = 0.0f
                    cloudAlpha = 0.55f
                    swirlBoost = 0.0f
                } else {
                    when {
                        p < 0.38f -> {
                            val prog = p / 0.38f
                            contractFactor = 0.85f + prog * 0.50f
                            scatterOffset = prog * 0.65f
                            cloudAlpha = 0.55f + prog * 0.45f
                            swirlBoost = sin(prog * Math.PI.toFloat()) * 2.2f
                        }
                        p < 0.72f -> {
                            val prog = (p - 0.38f) / 0.34f
                            contractFactor = 1.35f - prog * 0.85f
                            scatterOffset = 0.65f * (1f - prog)
                            cloudAlpha = 1.0f
                            swirlBoost = (1f - prog) * 2.2f
                        }
                        else -> {
                            val prog = (p - 0.72f) / 0.28f
                            contractFactor = 0.50f + prog * 0.50f
                            scatterOffset = 0.0f
                            cloudAlpha = 1.0f - prog * 0.45f
                            swirlBoost = 0.0f
                        }
                    }
                }

                // 1. Draw Ethereal 3D Constellation Cloud (Streaks + Links) across ALL stages
                constellationCloud.updateAndDraw(
                    scope = this,
                    time = frameTime + swirlBoost,
                    centerX = centerX,
                    centerY = centerY,
                    baseScale = canvasW * 0.35f,
                    cloudAlpha = cloudAlpha,
                    contractFactor = contractFactor,
                    scatterOffset = scatterOffset,
                    primaryColor = activePrimary,
                    accentColor = activeAccent
                )

                // 2. Draw High-Velocity Transition Spark Swarm (Explode out & Spiral in)
                drawTransitionSwarm(
                    scope = this,
                    p = p,
                    centerX = centerX,
                    centerY = centerY,
                    canvasW = canvasW,
                    primaryColor = activePrimary,
                    accentColor = activeAccent
                )

                // -----------------------------------------------------------------
                // A. STAGE 1: Crystal-Clear Official HUBT Logo
                // -----------------------------------------------------------------
                val (logoAlpha, logoScale) = getStageAnim(0, currentStage, previousStage, p)
                if (logoAlpha > 0.01f && hubtLogoBitmap != null) {
                    val bmp = hubtLogoBitmap!!
                    val logoWidth = canvasW * 0.72f * logoScale
                    val logoHeight = logoWidth * (bmp.height.toFloat() / bmp.width.toFloat())

                    val scaleX = 1f + rotY * 0.25f
                    val scaleY = 1f - rotX * 0.20f
                    val offsetY = sin(frameTime * 1.5f) * 8.dp.toPx()

                    // 3D Floor Reflection
                    val floorY = centerY + logoHeight * 0.65f
                    translate(left = centerX - logoWidth / 2f, top = floorY + offsetY * 0.3f) {
                        scale(scaleX = scaleX, scaleY = -0.38f, pivot = Offset(logoWidth / 2f, 0f)) {
                            drawImage(
                                image = bmp,
                                dstSize = androidx.compose.ui.unit.IntSize(logoWidth.toInt(), logoHeight.toInt()),
                                alpha = 0.20f * logoAlpha,
                                colorFilter = ColorFilter.tint(Color(0xFF0E6BA8), BlendMode.SrcIn)
                            )
                        }
                    }

                    // Main Crisp, Pristine Solid HUBT Logo
                    translate(left = centerX - logoWidth / 2f, top = centerY - logoHeight / 2f + offsetY) {
                        scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset(logoWidth / 2f, logoHeight / 2f)) {
                            // Luminous Cyan Aura
                            drawImage(
                                image = bmp,
                                dstSize = androidx.compose.ui.unit.IntSize(logoWidth.toInt(), logoHeight.toInt()),
                                alpha = 0.38f * logoAlpha,
                                colorFilter = ColorFilter.tint(Color(0xFF00E5FF), BlendMode.SrcIn)
                            )
                            // Razor-Sharp Official Artwork
                            drawImage(
                                image = bmp,
                                dstSize = androidx.compose.ui.unit.IntSize(logoWidth.toInt(), logoHeight.toInt()),
                                alpha = logoAlpha
                            )
                        }
                    }
                }

                // -----------------------------------------------------------------
                // B. STAGE 2: Smart QR Code
                // -----------------------------------------------------------------
                val (qrAlpha, qrScale) = getStageAnim(1, currentStage, previousStage, p)
                if (qrAlpha > 0.01f) {
                    val qrSize = canvasW * 0.58f * qrScale
                    val half = qrSize / 2f
                    val scaleX = 1f + rotY * 0.20f
                    val scaleY = 1f - rotX * 0.16f
                    val offsetY = sin(frameTime * 1.5f) * 6.dp.toPx()

                    translate(left = centerX - half, top = centerY - half + offsetY) {
                        scale(scaleX, scaleY, Offset(half, half)) {
                            // Outer QR border glow
                            drawRoundRect(
                                color = Color(0xFF00E5FF).copy(alpha = 0.85f * qrAlpha),
                                topLeft = Offset(0f, 0f),
                                size = Size(qrSize, qrSize),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )

                            // 3 Corner Finder Squares
                            fun drawFinder(x: Float, y: Float) {
                                val fSize = qrSize * 0.26f
                                drawRoundRect(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.90f * qrAlpha),
                                    topLeft = Offset(x, y),
                                    size = Size(fSize, fSize),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                )
                                val coreSize = fSize * 0.50f
                                val offsetCore = (fSize - coreSize) / 2f
                                drawRoundRect(
                                    color = Color(0xFFF0F9FF).copy(alpha = 0.95f * qrAlpha),
                                    topLeft = Offset(x + offsetCore, y + offsetCore),
                                    size = Size(coreSize, coreSize),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                )
                            }

                            drawFinder(qrSize * 0.08f, qrSize * 0.08f)
                            drawFinder(qrSize * 0.66f, qrSize * 0.08f)
                            drawFinder(qrSize * 0.08f, qrSize * 0.66f)

                            // Rich Authentic QR Matrix Data Modules ("Vân QR")
                            val gridCols = 17
                            val step = (qrSize * 0.84f) / (gridCols - 1)
                            val mSize = step * 0.74f
                            val modOffset = (step - mSize) / 2f
                            val startX = qrSize * 0.08f
                            val startY = qrSize * 0.08f

                            fun isInFinder(gx: Int, gy: Int): Boolean {
                                if (gx <= 5 && gy <= 5) return true      // Top-Left Finder & margin
                                if (gx >= 11 && gy <= 5) return true     // Top-Right Finder & margin
                                if (gx <= 5 && gy >= 11) return true     // Bottom-Left Finder & margin
                                return false
                            }

                            for (gx in 0 until gridCols) {
                                for (gy in 0 until gridCols) {
                                    if (isInFinder(gx, gy)) continue

                                    // Authentic QR pattern generation
                                    val isTiming = (gy == 5 && gx in 6..10 && gx % 2 == 0) ||
                                                   (gx == 5 && gy in 6..10 && gy % 2 == 0)
                                    val isAlignment = (gx in 12..14 && gy in 12..14) &&
                                                      (gx == 12 || gx == 14 || gy == 12 || gy == 14 || (gx == 13 && gy == 13))
                                    val hash = (gx * 37 + gy * 19 + (gx xor gy) * 13)
                                    val isData = (hash % 10 < 6)

                                    if (isTiming || isAlignment || isData) {
                                        val mx = startX + gx * step + modOffset
                                        val my = startY + gy * step + modOffset

                                        val isHighlight = (gx * 5 + gy * 3) % 11 == 0
                                        val isAccent = (gx + gy) % 4 == 0
                                        val modColor = when {
                                            isHighlight -> Color(0xFFF0F9FF).copy(alpha = 0.95f * qrAlpha)
                                            isAccent -> Color(0xFF00E5FF).copy(alpha = 0.92f * qrAlpha)
                                            else -> Color(0xFF38BDF8).copy(alpha = 0.82f * qrAlpha)
                                        }

                                        drawRoundRect(
                                            color = modColor,
                                            topLeft = Offset(mx, my),
                                            size = Size(mSize, mSize),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.8.dp.toPx(), 1.8.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // -----------------------------------------------------------------
                // C. STAGE 3: Smartphone with Active Camera Scanner & Moving Laser
                // -----------------------------------------------------------------
                val (phoneAlpha, phoneScale) = getStageAnim(2, currentStage, previousStage, p)
                if (phoneAlpha > 0.01f) {
                    val pw = canvasW * 0.48f * phoneScale
                    val ph = pw * 1.95f
                    val scaleX = 1f + rotY * 0.20f
                    val scaleY = 1f - rotX * 0.16f
                    val offsetY = sin(frameTime * 1.5f) * 6.dp.toPx()

                    translate(left = centerX - pw / 2f, top = centerY - ph / 2f + offsetY) {
                        scale(scaleX, scaleY, Offset(pw / 2f, ph / 2f)) {
                            // Phone outer frame
                            drawRoundRect(
                                color = Color(0xFF94A3B8).copy(alpha = 0.80f * phoneAlpha),
                                topLeft = Offset(0f, 0f),
                                size = Size(pw, ph),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                            )

                            // Horizontal Bar at the bottom of the phone (moved down from top)
                            drawRoundRect(
                                color = Color(0xFF94A3B8).copy(alpha = 0.80f * phoneAlpha),
                                topLeft = Offset(pw / 2f - 24.dp.toPx(), ph - 18.dp.toPx()),
                                size = Size(48.dp.toPx(), 4.dp.toPx()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            )

                            // Camera viewfinder brackets [ ]
                            val vfSize = pw * 0.58f
                            val vfLeft = (pw - vfSize) / 2f
                            val vfTop = (ph - vfSize) / 2f
                            val bracketLen = vfSize * 0.28f

                            fun drawCorner(x: Float, y: Float, dx: Float, dy: Float) {
                                drawLine(Color(0xFF38BDF8).copy(alpha = phoneAlpha), Offset(x, y), Offset(x + dx * bracketLen, y), 2.5.dp.toPx())
                                drawLine(Color(0xFF38BDF8).copy(alpha = phoneAlpha), Offset(x, y), Offset(x, y + dy * bracketLen), 2.5.dp.toPx())
                            }

                            drawCorner(vfLeft, vfTop, 1f, 1f)
                            drawCorner(vfLeft + vfSize, vfTop, -1f, 1f)
                            drawCorner(vfLeft, vfTop + vfSize, 1f, -1f)
                            drawCorner(vfLeft + vfSize, vfTop + vfSize, -1f, -1f)

                            // Active Laser Beam Sweeping Up and Down
                            val laserProgress = (sin(frameTime * 3.2f) + 1f) / 2f
                            val laserY = vfTop + laserProgress * vfSize

                            drawLine(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF10B981).copy(alpha = 0.95f * phoneAlpha),
                                        Color(0xFF34D399).copy(alpha = 1.0f * phoneAlpha),
                                        Color(0xFF10B981).copy(alpha = 0.95f * phoneAlpha),
                                        Color.Transparent
                                    )
                                ),
                                start = Offset(vfLeft - 10.dp.toPx(), laserY),
                                end = Offset(vfLeft + vfSize + 10.dp.toPx(), laserY),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }
                }

                // -----------------------------------------------------------------
                // D. STAGE 4: Precision Checkmark "V" Icon in Shield Ring
                // -----------------------------------------------------------------
                val (checkAlpha, checkScale) = getStageAnim(3, currentStage, previousStage, p)
                if (checkAlpha > 0.01f) {
                    val ringR = canvasW * 0.28f * checkScale
                    val scaleX = 1f + rotY * 0.20f
                    val scaleY = 1f - rotX * 0.16f
                    val offsetY = sin(frameTime * 1.5f) * 6.dp.toPx()

                    translate(left = centerX, top = centerY + offsetY) {
                        scale(scaleX, scaleY, Offset.Zero) {
                            // 1. Luminous Outer Ambient Aura
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF10B981).copy(alpha = 0.32f * checkAlpha),
                                        Color(0xFF00E5FF).copy(alpha = 0.12f * checkAlpha),
                                        Color.Transparent
                                    ),
                                    center = Offset.Zero,
                                    radius = ringR * 1.45f
                                ),
                                radius = ringR * 1.45f,
                                center = Offset.Zero
                            )

                            // 2. Expanding Pulse Wave Ring
                            val pulseProgress = (frameTime * 0.75f) % 1.0f
                            val pulseR = ringR * (0.88f + pulseProgress * 0.45f)
                            val pulseA = (1f - pulseProgress) * 0.45f * checkAlpha
                            drawCircle(
                                color = Color(0xFF34D399).copy(alpha = pulseA),
                                radius = pulseR,
                                center = Offset.Zero,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx())
                            )

                            // 3. 3D Floor Shadow / Reflection beneath the shield ring
                            val floorY = ringR * 1.30f
                            drawOval(
                                color = Color(0xFF10B981).copy(alpha = 0.22f * checkAlpha),
                                topLeft = Offset(-ringR * 0.75f, floorY - 6.dp.toPx()),
                                size = Size(ringR * 1.5f, 14.dp.toPx())
                            )

                            // 4. Solid Obsidian-Emerald Shield Disc
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF0D3B2E).copy(alpha = 0.90f * checkAlpha),
                                        Color(0xFF04181A).copy(alpha = 0.96f * checkAlpha)
                                    ),
                                    center = Offset(0f, -ringR * 0.20f),
                                    radius = ringR * 1.1f
                                ),
                                radius = ringR,
                                center = Offset.Zero
                            )

                            // 5. Multi-tone Glowing Emerald Edge Border
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color(0xFF10B981).copy(alpha = checkAlpha),
                                        Color(0xFF34D399).copy(alpha = checkAlpha),
                                        Color(0xFF38BDF8).copy(alpha = checkAlpha),
                                        Color(0xFF10B981).copy(alpha = checkAlpha)
                                    ),
                                    center = Offset.Zero
                                ),
                                radius = ringR,
                                center = Offset.Zero,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5.dp.toPx())
                            )

                            // 6. High-Tech Biometric Inner Dashed Accent Ring
                            drawCircle(
                                color = Color(0xFF34D399).copy(alpha = 0.45f * checkAlpha),
                                radius = ringR * 0.86f,
                                center = Offset.Zero,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                                )
                            )

                            // 7. Floating 3D Sparkle Pinpricks with Anamorphic Streaks around Shield
                            val sparkles = listOf(
                                Offset(-ringR * 0.85f, -ringR * 0.75f),
                                Offset(ringR * 0.90f, -ringR * 0.65f),
                                Offset(-ringR * 0.80f, ringR * 0.80f),
                                Offset(ringR * 0.85f, ringR * 0.75f)
                            )
                            for ((idx, sp) in sparkles.withIndex()) {
                                val spA = (0.35f + 0.65f * sin(frameTime * 3.0f + idx * 1.6f)).coerceIn(0f, 1f) * checkAlpha
                                drawCircle(Color(0xFF34D399).copy(alpha = spA * 0.40f), radius = 4.dp.toPx(), center = sp)
                                drawCircle(Color.White.copy(alpha = spA), radius = 1.5.dp.toPx(), center = sp)
                                drawLine(
                                    color = Color(0xFF00E5FF).copy(alpha = spA * 0.60f),
                                    start = Offset(sp.x - 10.dp.toPx(), sp.y),
                                    end = Offset(sp.x + 10.dp.toPx(), sp.y),
                                    strokeWidth = 1.2.dp.toPx()
                                )
                            }

                            // 8. Precision Golden Checkmark "V" (Perfect Mathematical Balance)
                            val checkSpan = ringR * 0.72f
                            val checkPath = Path().apply {
                                moveTo(-checkSpan * 0.48f, -checkSpan * 0.05f)
                                lineTo(-checkSpan * 0.12f, checkSpan * 0.35f)
                                lineTo(checkSpan * 0.48f, -checkSpan * 0.35f)
                            }

                            // Glowing Checkmark Underlayer
                            drawPath(
                                path = checkPath,
                                color = Color(0xFF10B981).copy(alpha = 0.55f * checkAlpha),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 10.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // Razor-Sharp Pure White Checkmark Top Layer
                            drawPath(
                                path = checkPath,
                                color = Color.White.copy(alpha = checkAlpha),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 5.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }
        }

        // Ambient Bottom Radial Glow behind the action card
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(300.dp)
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                animatedGlowColor.copy(alpha = 0.18f),
                                Color(0xFF061017)
                            )
                        )
                    )
                }
        )

        // Bottom Sheet / Action Card
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF0C1922).copy(alpha = 0.92f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A4C).copy(alpha = 0.65f)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Page Indicator Dots (5 stages)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in ONBOARDING_STAGES.indices) {
                        val isSelected = i == currentStage
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 26.dp else 6.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "dotWidth"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (isSelected) activeStageData.primaryColor else Color.White.copy(alpha = 0.22f),
                            animationSpec = tween(300),
                            label = "dotColor"
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(dotWidth)
                                .clip(RoundedCornerShape(3.dp))
                                .background(dotColor)
                                .clickable {
                                    userInteractedTime = System.currentTimeMillis()
                                    switchStage(i)
                                }
                        )
                    }
                }

                // Animated Vietnamese Titles and Subtitles with smooth crossfade
                AnimatedContent(
                    targetState = activeStageData,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(250))
                    },
                    label = "textCrossfade"
                ) { stage ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                    ) {
                        Text(
                            text = stage.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stage.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            maxLines = 2
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                // SINGLE PRIMARY CTA BUTTON: "Khám phá"
                Button(
                    onClick = onExplore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(0.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF00B4D8),
                                        Color(0xFF10B981)
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Khám phá",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
