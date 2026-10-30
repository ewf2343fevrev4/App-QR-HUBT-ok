package com.smartattendance.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartattendance.app.domain.model.*
import com.smartattendance.app.domain.usecase.statusLabel
import com.smartattendance.app.ui.theme.*

// ==========================================
// SCREEN LAYOUT & STRUCTURAL SCAFFOLDING
// ==========================================

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
fun Heading(title: String, subtitle: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun Section(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (action != null) {
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

// ==========================================
// GROUPED INSET CARDS & LIST ITEMS
// ==========================================

@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(content = content)
    }
}

@Composable
fun GroupedItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.secondary,
    iconContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val clickModifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(clickModifier)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (icon != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconContainerColor
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp),
                        tint = iconTint
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
            )
        }
    }
}

// ==========================================
// CARD & METRIC COMPONENTS
// ==========================================

@Composable
fun InfoCard(
    title: String,
    detail: String,
    icon: ImageVector = Icons.Outlined.Info,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    val shape = RoundedCornerShape(14.dp)
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            shape = shape,
            border = cardBorder,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            content = { rowContent() }
        )
    } else {
        Card(
            shape = shape,
            border = cardBorder,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            content = { rowContent() }
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.secondary,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(14.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Card(
        modifier = modifier.then(clickModifier),
        shape = shape,
        border = border,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accentColor
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TabularNumbers),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==========================================
// STATUS BADGES & CHIPS
// ==========================================

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val isDark = isSystemInDarkTheme()
    val accentColor = statusColor(status, isDark)
    val containerColor = accentColor.copy(alpha = if (isDark) 0.16f else 0.12f)
    val displayText = label ?: statusLabel(status)

    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(color = accentColor, shape = CircleShape)
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
    }
}

@Composable
fun Badge(status: String) {
    StatusBadge(status = status)
}

// ==========================================
// SEARCH & SELECTION CONTROLS
// ==========================================

@Composable
fun ModernSearchField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Tìm kiếm...",
    onClear: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onChange("")
                        onClear?.invoke()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, label: String = "Tìm kiếm") {
    ModernSearchField(value = value, onChange = onChange, placeholder = label)
}

@Composable
fun <T> Choice(
    label: String,
    items: List<T>,
    selected: T?,
    text: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = selected?.let(text) ?: "Chọn…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text(item),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (item == selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ==========================================
// SEGMENTED PROGRESS & STATISTICS
// ==========================================

@Composable
fun SegmentedStatsBar(
    present: Int,
    late: Int,
    excused: Int,
    absent: Int,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    showLegend: Boolean = false
) {
    val total = present + late + excused + absent
    val isDark = isSystemInDarkTheme()
    val pColor = statusColor("PRESENT", isDark)
    val lColor = statusColor("LATE", isDark)
    val eColor = statusColor("EXCUSED", isDark)
    val aColor = statusColor("ABSENT", isDark)

    val presentWeight by animateFloatAsState(
        targetValue = if (total > 0) present.toFloat() / total else 0f,
        animationSpec = tween(400),
        label = "presentWeight"
    )
    val lateWeight by animateFloatAsState(
        targetValue = if (total > 0) late.toFloat() / total else 0f,
        animationSpec = tween(400),
        label = "lateWeight"
    )
    val excusedWeight by animateFloatAsState(
        targetValue = if (total > 0) excused.toFloat() / total else 0f,
        animationSpec = tween(400),
        label = "excusedWeight"
    )
    val absentWeight by animateFloatAsState(
        targetValue = if (total > 0) absent.toFloat() / total else 0f,
        animationSpec = tween(400),
        label = "absentWeight"
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (total > 0) {
                Row(Modifier.fillMaxSize()) {
                    if (presentWeight > 0.001f) {
                        Box(
                            Modifier
                                .weight(presentWeight)
                                .fillMaxHeight()
                                .background(pColor)
                        )
                    }
                    if (lateWeight > 0.001f) {
                        Box(
                            Modifier
                                .weight(lateWeight)
                                .fillMaxHeight()
                                .background(lColor)
                        )
                    }
                    if (excusedWeight > 0.001f) {
                        Box(
                            Modifier
                                .weight(excusedWeight)
                                .fillMaxHeight()
                                .background(eColor)
                        )
                    }
                    if (absentWeight > 0.001f) {
                        Box(
                            Modifier
                                .weight(absentWeight)
                                .fillMaxHeight()
                                .background(aColor)
                        )
                    }
                }
            }
        }
        if (showLegend && total > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(
                    Triple("Có mặt", present, pColor),
                    Triple("Muộn", late, lColor),
                    Triple("Có phép", excused, eColor),
                    Triple("Vắng", absent, aColor)
                ).forEach { (label, count, color) ->
                    val pct = (count * 100f) / total
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(color, CircleShape)
                        )
                        Text(
                            text = "$label ${"%.0f".format(pct)}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNumbers),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentedStatsBar(
    stats: Statistics,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    showLegend: Boolean = false
) {
    SegmentedStatsBar(
        present = stats.present,
        late = stats.late,
        excused = stats.excused,
        absent = stats.absent,
        modifier = modifier,
        height = height,
        showLegend = showLegend
    )
}

@Composable
fun Stats(stats: Statistics) {
    val isDark = isSystemInDarkTheme()
    val pColor = statusColor("PRESENT", isDark)
    val lColor = statusColor("LATE", isDark)
    val aColor = statusColor("ABSENT", isDark)
    val eColor = statusColor("EXCUSED", isDark)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 4 metric tiles with tabular numbers and semantic status dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("Có mặt", stats.present, pColor),
                Triple("Muộn", stats.late, lColor),
                Triple("Vắng", stats.absent, aColor),
                Triple("Có phép", stats.excused, eColor)
            ).forEach { (label, value, color) ->
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(color, CircleShape)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TabularNumbers),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Segmented distribution progress bar + participation rate
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tỷ lệ tham gia",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "%.1f%% / %d SV".format(stats.participation, stats.total),
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TabularNumbers),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                SegmentedStatsBar(
                    stats = stats,
                    height = 8.dp,
                    showLegend = false
                )
            }
        }
    }
}

// ==========================================
// FEEDBACK & UTILITY STATES
// ==========================================

@Composable
fun EmptyState(
    text: String,
    detail: String = "Dữ liệu sẽ xuất hiện tại đây khi được tạo.",
    icon: ImageVector = Icons.Outlined.Inbox
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(14.dp)
                        .size(28.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun Confirm(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Xác nhận")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Hủy")
            }
        }
    )
}
