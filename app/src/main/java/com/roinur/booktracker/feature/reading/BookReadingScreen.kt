package com.roinur.booktracker

import androidx.compose.material3.Text

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun BookReadingSessionScreen(
    book: BookRow,
    activeStartedAtMs: Long,
    activePausedAtMs: Long,
    activePausedTotalMs: Long,
    countdownEndMs: Long,
    countdownTotalMs: Long,
    nowMs: Long,
    modifier: Modifier = Modifier,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onCountdown: () -> Unit,
    onAction: (BookNoteKind) -> Unit
) {
    val elapsed = bookActiveElapsedSeconds(activeStartedAtMs, nowMs, activePausedAtMs, activePausedTotalMs)
    val paused = activePausedAtMs > 0L
    val countdownRemainingMs = (countdownEndMs - nowMs).coerceAtLeast(0L)
    val countdownActive = countdownRemainingMs > 0L
    val countdownProgress = if (countdownActive && countdownTotalMs > 0L) {
        1f - (countdownRemainingMs.toFloat() / countdownTotalMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxReadingHeight = (screenHeight - 92.dp).coerceAtLeast(480.dp)
    val targetHeight = (screenHeight * 0.86f).coerceAtMost(maxReadingHeight).coerceAtLeast(480.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(targetHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        BookCover(
            book = book,
            modifier = Modifier
                .matchParentSize()
                .blur(18.dp)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.74f))
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("You are reading", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (book.pageCount > 0) "${book.currentPage}/${book.pageCount}" else "${book.currentPage} pages",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                book.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(book.authors.ifBlank { "Unknown author" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            Text(
                bookFormatTimer(elapsed),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp, lineHeight = 64.sp),
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
            if (countdownActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Countdown", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(bookFormatCountdownClock(countdownRemainingMs), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(progress = { countdownProgress }, modifier = Modifier.fillMaxWidth())
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BookReadingAction(label = "Add Quote", kind = BookNoteKind.QUOTE, onClick = { onAction(BookNoteKind.QUOTE) }, modifier = Modifier.weight(1f))
                BookReadingAction(label = "Thought", kind = BookNoteKind.THOUGHT, onClick = { onAction(BookNoteKind.THOUGHT) }, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BookReadingAction(label = if (countdownActive) "Cancel timer" else "Countdown", kind = BookNoteKind.COUNTDOWN, onClick = onCountdown, modifier = Modifier.weight(1f))
                BookReadingAction(label = "Add Word", kind = BookNoteKind.WORD, onClick = { onAction(BookNoteKind.WORD) }, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text(if (paused) "Resume reading" else "Pause reading")
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text("Stop reading")
                }
            }
        }
    }
}

@Composable
internal fun BookReadingAction(
    label: String,
    kind: BookNoteKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "bookReadingActionScale"
    )
    Row(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .height(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true),
                onClick = onClick
            )
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookReadingActionIcon(
            kind = kind,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun BookReadingActionIcon(
    kind: BookNoteKind,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.105f).coerceAtLeast(2.2f)
        when (kind) {
            BookNoteKind.QUOTE -> {
                listOf(w * 0.36f, w * 0.64f).forEach { cx ->
                    drawCircle(tint, radius = w * 0.105f, center = Offset(cx, h * 0.42f))
                    drawLine(
                        tint,
                        Offset(cx + w * 0.05f, h * 0.44f),
                        Offset(cx - w * 0.07f, h * 0.66f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
            BookNoteKind.THOUGHT -> {
                val star = Path().apply {
                    moveTo(w * 0.50f, h * 0.08f)
                    lineTo(w * 0.62f, h * 0.38f)
                    lineTo(w * 0.92f, h * 0.50f)
                    lineTo(w * 0.62f, h * 0.62f)
                    lineTo(w * 0.50f, h * 0.92f)
                    lineTo(w * 0.38f, h * 0.62f)
                    lineTo(w * 0.08f, h * 0.50f)
                    lineTo(w * 0.38f, h * 0.38f)
                    close()
                }
                drawPath(star, tint)
            }
            BookNoteKind.COUNTDOWN -> {
                val center = Offset(w * 0.5f, h * 0.5f)
                val radius = w * 0.34f
                drawCircle(tint, radius = radius, center = center, style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawLine(tint, center, Offset(center.x, center.y - radius * 0.58f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, center, Offset(center.x + radius * 0.52f, center.y + radius * 0.18f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.36f, h * 0.14f), Offset(w * 0.50f, h * 0.04f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.64f, h * 0.14f), Offset(w * 0.50f, h * 0.04f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            BookNoteKind.WORD -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.17f, h * 0.16f),
                    size = Size(w * 0.66f, h * 0.68f),
                    cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
                    style = Stroke(width = stroke)
                )
                listOf(0.34f, 0.50f, 0.66f).forEach { y ->
                    drawLine(tint, Offset(w * 0.28f, h * y), Offset(w * 0.72f, h * y), strokeWidth = stroke, cap = StrokeCap.Round)
                }
            }
            BookNoteKind.NOTE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.2f, h * 0.12f),
                    size = Size(w * 0.6f, h * 0.76f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                    style = Stroke(width = stroke)
                )
                drawLine(tint, Offset(w * 0.32f, h * 0.36f), Offset(w * 0.68f, h * 0.36f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.32f, h * 0.54f), Offset(w * 0.62f, h * 0.54f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun FinishReadingDialog(
    book: BookRow,
    pageDraft: String,
    onPageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit
) {
    var calculatorExpanded by rememberSaveable(book.id) { mutableStateOf(false) }
    var calculatorStart by rememberSaveable(book.id) { mutableStateOf("") }
    var calculatorEnd by rememberSaveable(book.id) { mutableStateOf("") }
    fun updateCalculation(start: String, end: String) {
        calculatedBookPage(book.currentPage, start, end)?.let { onPageChange(it.toString()) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Finish reading", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                }
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(book.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    OutlinedTextField(
                        value = pageDraft,
                        onValueChange = onPageChange,
                        label = { Text("Page number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))) {
                        BookDisclosureHeader("Page calculator", R.drawable.ic_calculator_24, calculatorExpanded) { calculatorExpanded = !calculatorExpanded }
                        if (calculatorExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = calculatorStart,
                                onValueChange = { calculatorStart = it; updateCalculation(it, calculatorEnd) },
                                label = { Text("Start page") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = calculatorEnd,
                                onValueChange = { calculatorEnd = it; updateCalculation(calculatorStart, it) },
                                label = { Text("End page") }, singleLine = true,
                                isError = calculatorStart.isNotBlank() && calculatorEnd.isNotBlank() && calculatedBookPage(book.currentPage, calculatorStart, calculatorEnd) == null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                            )
                        }
                        val calculated = calculatedBookPage(book.currentPage, calculatorStart, calculatorEnd)
                        if (calculated != null) Text("${calculated - book.currentPage} pages read", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Save")
                    }
                    TextButton(onClick = onDiscard) {
                        Text("Don't save session")
                    }
                }
            }
        }
    }
}
