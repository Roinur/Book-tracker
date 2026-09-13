package com.roinur.booktracker

import androidx.compose.material3.Text

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookTopBar(
    title: String,
    themeMode: ThemeMode,
    accentMode: AccentMode,
    settingsActive: Boolean,
    onBack: (() -> Unit)? = null,
    onCycleThemeMode: () -> Unit,
    onAccentModeSelected: (AccentMode) -> Unit,
    onSettings: () -> Unit
) {
    CenterAlignedTopAppBar(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                    Text("‹", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                }
            } else {
                ThemeToggleWithAccentPicker(
                    themeMode = themeMode,
                    accentMode = accentMode,
                    onCycleThemeMode = onCycleThemeMode,
                    onAccentModeSelected = onAccentModeSelected,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        },
        actions = {
            IconButton(
                onClick = onSettings,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = if (settingsActive) "×" else "⚙",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
internal fun BookBottomBar(
    selected: BookTrackerTab,
    onSelected: (BookTrackerTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 42.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookTrackerTab.entries.forEach { tab ->
            val active = selected == tab
            val itemScale by animateFloatAsState(
                targetValue = if (active) 1f else 0.92f,
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                label = "bookBottomItemScale"
            )
            val activeFill by animateFloatAsState(
                targetValue = if (active) 0.48f else 0f,
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                label = "bookBottomItemFill"
            )
            Surface(
                onClick = { onSelected(tab) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer(scaleX = itemScale, scaleY = itemScale),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = activeFill)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        BookMiniIcon(
                            kind = tab.symbol,
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookMiniIcon(
    kind: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.11f).coerceAtLeast(2f)
        when (kind) {
            "stats" -> {
                val bars = listOf(0.52f, 0.78f, 0.36f)
                bars.forEachIndexed { index, fraction ->
                    val x = w * (0.28f + index * 0.22f)
                    drawLine(
                        tint,
                        Offset(x, h * 0.82f),
                        Offset(x, h * (0.82f - fraction)),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
            "library" -> {
                val gap = w * 0.06f
                val bookWidth = (w - gap * 2f) / 3f
                repeat(3) { index ->
                    val left = index * (bookWidth + gap)
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(left, h * 0.18f),
                        size = Size(bookWidth, h * 0.64f),
                        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                        style = Stroke(width = stroke * 0.72f)
                    )
                }
            }
            "pages" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.18f),
                    size = Size(w * 0.76f, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = Stroke(width = stroke)
                )
                drawLine(tint, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.82f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.22f, h * 0.36f), Offset(w * 0.42f, h * 0.36f), strokeWidth = stroke * 0.68f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.58f, h * 0.36f), Offset(w * 0.78f, h * 0.36f), strokeWidth = stroke * 0.68f, cap = StrokeCap.Round)
            }
            "time" -> {
                drawCircle(tint, radius = w * 0.34f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = stroke))
                drawLine(tint, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.29f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.66f, h * 0.58f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            "streak" -> {
                val flame = Path().apply {
                    moveTo(w * 0.5f, h * 0.88f)
                    cubicTo(w * 0.22f, h * 0.70f, w * 0.22f, h * 0.42f, w * 0.44f, h * 0.30f)
                    cubicTo(w * 0.48f, h * 0.18f, w * 0.48f, h * 0.12f, w * 0.58f, h * 0.06f)
                    cubicTo(w * 0.62f, h * 0.28f, w * 0.86f, h * 0.36f, w * 0.78f, h * 0.64f)
                    cubicTo(w * 0.74f, h * 0.78f, w * 0.63f, h * 0.86f, w * 0.5f, h * 0.88f)
                    close()
                }
                drawPath(flame, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
        }
    }
}
