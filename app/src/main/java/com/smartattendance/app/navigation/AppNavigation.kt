package com.smartattendance.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.smartattendance.app.R
import com.smartattendance.app.core.*
import com.smartattendance.app.feature.auth.LoginScreen
import com.smartattendance.app.feature.welcome.WelcomeScreen
import androidx.compose.runtime.saveable.rememberSaveable
import com.smartattendance.app.feature.dashboard.DashboardScreen
import com.smartattendance.app.feature.classes.*
import com.smartattendance.app.feature.attendance.*
import com.smartattendance.app.feature.scanner.ScannerScreen
import com.smartattendance.app.feature.students.StudentScreen
import com.smartattendance.app.feature.reports.ReportsScreen
import com.smartattendance.app.feature.settings.SettingsScreen
import com.smartattendance.app.feature.admin.AdminScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(vm: AppViewModel, state: AppState) {
    val snackbar = remember { SnackbarHostState() }
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    key(state.user?.id, state.demo, state.revision) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "dashboard"
        val navigate: (String) -> Unit = { nav.navigate(it) { launchSingleTop = true } }
        val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

        // Pulsating dot for Demo badge
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        val isDark = state.theme == "DARK" || (state.theme == "SYSTEM" && isSystemInDarkTheme())
        val bgRes = if (isDark) R.drawable.hubt_bg_dark else R.drawable.hubt_bg

        Box(modifier = Modifier.fillMaxSize()) {
            // Ambient Blurred HUBT Wallpaper Pattern (clearly visible brand watermark)
            Image(
                painter = painterResource(bgRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = if (isDark) 0.92f else 0.72f
            )

            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    if (state.user != null) {
                        TopAppBar(
                            modifier = Modifier.drawBehind {
                                drawLine(
                                    color = dividerColor,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1f
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                            ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "HUBT SA",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // Inline Environment Status Pill Badge
                                if (state.demo) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha),
                                                        shape = CircleShape
                                                    )
                                            )
                                            Text(
                                                text = "DEMO CỤC BỘ",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(
                                                        color = Color(0xFF087B69),
                                                        shape = CircleShape
                                                    )
                                            )
                                            Text(
                                                text = "ONLINE",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            val isRootTab = route in listOf("dashboard", "classes", "sessions", "reports?sessionId={sessionId}", "settings")
                            if (!isRootTab) {
                                IconButton(onClick = { nav.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Quay lại",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { vm.refresh() },
                                enabled = !state.busy
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Làm mới",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { navigate("settings") }) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Cài đặt",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (state.user != null) {
                    NavigationBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(
                                    color = dividerColor,
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 1f
                                )
                            },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        tonalElevation = 0.dp,
                        windowInsets = NavigationBarDefaults.windowInsets
                    ) {
                        listOf(
                            Triple("dashboard", "Tổng quan", Icons.Outlined.Dashboard),
                            Triple("classes", "Lớp học", Icons.Outlined.School),
                            Triple("sessions", "Quét thẻ", Icons.Outlined.QrCodeScanner),
                            Triple("reports", "Báo cáo", Icons.Outlined.BarChart)
                        ).forEach { (target, label, icon) ->
                            val selected = route.startsWith(target)
                            NavigationBarItem(
                                selected = selected,
                                alwaysShowLabel = true,
                                onClick = {
                                    nav.navigate(target) {
                                        popUpTo("dashboard")
                                        launchSingleTop = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = state.user == null && showWelcome,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "WelcomeRootTransition"
            ) { isWelcome ->
                if (isWelcome) {
                    WelcomeScreen(
                        onExplore = {
                            showWelcome = false
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) {
                if (state.busy) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = Color.Transparent
                    )
                }

                when {
                    state.starting -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.app_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp)
                                )
                                Text(
                                    text = "HUBT SA",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                    state.user == null -> {
                        LoginScreen(vm, state.busy)
                    }
                    else -> {
                        NavHost(
                            navController = nav,
                            startDestination = "dashboard",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            enterTransition = {
                                fadeIn(animationSpec = tween(200)) + slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = tween(250)
                                )
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(150))
                            },
                            popEnterTransition = {
                                fadeIn(animationSpec = tween(200)) + slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.End,
                                    animationSpec = tween(250)
                                )
                            },
                            popExitTransition = {
                                fadeOut(animationSpec = tween(150))
                            }
                        ) {
                            composable("dashboard") { DashboardScreen(state, navigate) }
                            composable("classes") { ClassesScreen(state.data, navigate) }
                            composable("class/{id}") {
                                ClassDetailsScreen(
                                    it.arguments?.getString("id").orEmpty(),
                                    state.data,
                                    navigate
                                )
                            }
                            composable("sessions") { SessionsScreen(state.data, navigate) }
                            composable(
                                "create?classId={classId}",
                                arguments = listOf(
                                    navArgument("classId") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) {
                                CreateSessionScreen(
                                    it.arguments?.getString("classId"),
                                    vm,
                                    state
                                ) { id ->
                                    nav.popBackStack()
                                    navigate("attendance/$id")
                                }
                            }
                            composable("attendance/{id}") {
                                AttendanceScreen(
                                    it.arguments?.getString("id").orEmpty(),
                                    vm,
                                    state,
                                    navigate
                                )
                            }
                            composable("scanner/{id}") {
                                ScannerScreen(
                                    it.arguments?.getString("id").orEmpty(),
                                    vm,
                                    state,
                                    navigate
                                )
                            }
                            composable("student/{id}") {
                                StudentScreen(
                                    it.arguments?.getString("id").orEmpty(),
                                    state.data,
                                    navigate
                                )
                            }
                            composable(
                                "reports?sessionId={sessionId}",
                                arguments = listOf(
                                    navArgument("sessionId") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) {
                                ReportsScreen(
                                    it.arguments?.getString("sessionId"),
                                    vm,
                                    state,
                                    navigate
                                )
                            }
                            composable("settings") { SettingsScreen(vm, state, navigate) }
                            composable("admin") { AdminScreen(vm, state) }
                        }
                    }
                }
            }
        }
    }
}
}
}
}

