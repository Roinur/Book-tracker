package com.roinur.booktracker

import com.roinur.booktracker.BookFitText as Text

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
                    Text("\u2039", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
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
                    text = if (settingsActive) "\u00D7" else "\u2699",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
internal fun BookBottomBar(selected: BookTrackerTab, pagePosition: () -> Float, onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: (Float) -> Unit, onSelected: (BookTrackerTab) -> Unit) {
    Box(Modifier.fillMaxWidth().height(62.dp).padding(bottom = 10.dp), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(
            Modifier.fillMaxWidth(0.70f).height(46.dp)
                .pointerInput(Unit) {
                    val velocity = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { velocity.resetTracking(); onDragStart() },
                        onDragCancel = { onDragEnd(0f) },
                        onDragEnd = { onDragEnd(velocity.calculateVelocity().x / density) },
                        onHorizontalDrag = { change, amount ->
                            velocity.addPosition(change.uptimeMillis, change.position)
                            change.consume(); onDrag(amount)
                        }
                    )
                }
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(24.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                .padding(3.dp)
        ) {
            val segmentWidth = maxWidth / 2
            Box(Modifier.graphicsLayer { translationX = segmentWidth.toPx() * pagePosition() }.width(segmentWidth).fillMaxHeight()
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(21.dp)))
            Row(Modifier.fillMaxSize()) {
                BookTrackerTab.entries.forEach { tab ->
                    val active = selected == tab
                    Surface(
                        onClick = { onSelected(tab) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(21.dp), color = Color.Transparent
                    ) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            BookMiniIcon(tab.symbol,
                                if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                Modifier.size(19.dp))
                            Text(tab.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
        // Filled silhouettes with transparent details remain readable on every theme surface.
        fun shape(block: Path.() -> Unit) = Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            block()
        }
        when (kind) {
            "stats" -> repeat(3) { i ->
                val height = h * (0.34f + i * 0.23f)
                drawRoundRect(tint.copy(alpha = 0.72f + i * 0.14f),
                    Offset(w * (0.08f + i * 0.30f), h * 0.91f - height),
                    Size(w * 0.23f, height), CornerRadius(w * 0.07f))
            }
            "library" -> {
                drawPath(shape {
                    moveTo(w*.06f,h*.17f)
                    cubicTo(w*.20f,h*.09f,w*.37f,h*.12f,w*.47f,h*.20f)
                    lineTo(w*.47f,h*.88f)
                    cubicTo(w*.32f,h*.79f,w*.18f,h*.79f,w*.06f,h*.85f); close()
                }, tint.copy(alpha=.78f))
                drawPath(shape {
                    moveTo(w*.53f,h*.20f)
                    cubicTo(w*.66f,h*.10f,w*.82f,h*.10f,w*.94f,h*.17f)
                    lineTo(w*.94f,h*.85f)
                    cubicTo(w*.79f,h*.79f,w*.66f,h*.79f,w*.53f,h*.88f); close()
                    moveTo(w*.72f,h*.13f); lineTo(w*.83f,h*.13f)
                    lineTo(w*.83f,h*.49f); lineTo(w*.775f,h*.43f)
                    lineTo(w*.72f,h*.49f); close()
                }, tint)
            }
            "pages", "notes" -> drawPath(shape {
                moveTo(w*.16f,h*.08f); lineTo(w*.63f,h*.08f)
                lineTo(w*.86f,h*.31f); lineTo(w*.86f,h*.92f)
                lineTo(w*.16f,h*.92f); close()
                moveTo(w*.63f,h*.10f); lineTo(w*.63f,h*.32f)
                lineTo(w*.84f,h*.32f); close()
                for (y in listOf(.46f,.61f,.76f)) {
                    addRect(androidx.compose.ui.geometry.Rect(w*.29f,h*y,w*.71f,h*(y+.065f)))
                }
            }, tint)
            "time" -> drawPath(shape {
                addOval(androidx.compose.ui.geometry.Rect(w*.08f,h*.08f,w*.92f,h*.92f))
                moveTo(w*.45f,h*.23f); lineTo(w*.55f,h*.23f)
                lineTo(w*.55f,h*.47f); lineTo(w*.73f,h*.63f)
                lineTo(w*.66f,h*.71f); lineTo(w*.45f,h*.52f); close()
            }, tint)
            "streak" -> drawPath(shape {
                moveTo(w*.46f,h*.05f)
                cubicTo(w*.51f,h*.27f,w*.18f,h*.36f,w*.17f,h*.63f)
                cubicTo(w*.14f,h*.98f,w*.81f,h*1.03f,w*.84f,h*.65f)
                cubicTo(w*.87f,h*.47f,w*.72f,h*.29f,w*.68f,h*.25f)
                lineTo(w*.62f,h*.43f)
                cubicTo(w*.60f,h*.24f,w*.54f,h*.12f,w*.46f,h*.05f); close()
                moveTo(w*.51f,h*.49f)
                cubicTo(w*.48f,h*.66f,w*.33f,h*.67f,w*.35f,h*.79f)
                cubicTo(w*.40f,h*.97f,w*.70f,h*.88f,w*.65f,h*.73f)
                cubicTo(w*.63f,h*.64f,w*.55f,h*.59f,w*.51f,h*.49f); close()
            }, tint)
        }
    }
}
