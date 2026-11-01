package com.roinur.booktracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun FinishReadingDialog(
    book: BookRow,
    pageDraft: String,
    onPageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (String?, Int?) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val fieldShape = RoundedCornerShape(8.dp)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var calculatorExpanded by rememberSaveable(book.id) { mutableStateOf(false) }
    var calculatorStart by rememberSaveable(book.id) { mutableStateOf(book.readingPage.toString()) }
    var calculatorEnd by rememberSaveable(book.id) { mutableStateOf("") }
    var confirmDiscard by rememberSaveable(book.id) { mutableStateOf(false) }
    val usingCalculator = calculatorEnd.isNotBlank()
    val calculatedPagesRead = if (usingCalculator) calculatedBookPage(0, calculatorStart, calculatorEnd) else null
    val bookmarkText = if (usingCalculator) calculatorEnd else book.bookmarkPage?.let { pageDraft }
    val entry = bookFinishReadingEntry(book, pageDraft, bookmarkText, calculatedPagesRead)
    fun hideKeyboard() { keyboard?.hide(); focus.clearFocus() }
    fun updateCalculation(start: String, end: String) {
        if (end.isNotBlank()) onPageChange(calculatedBookPage(book.currentPage, start, end)?.toString().orEmpty())
    }
    fun resetCalculator() {
        hideKeyboard()
        calculatorStart = book.readingPage.toString()
        calculatorEnd = ""
        onPageChange(book.readingPage.toString())
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center) {
            // IME changes available height, not the dialog's horizontal geometry.
            // Keep spacing stable and let only the form viewport shrink if needed.
            val padding = 18.dp
            Surface(Modifier.widthIn(max = 440.dp).fillMaxWidth().heightIn(max = maxHeight.coerceAtMost(740.dp)),
                shape = shape, color = colors.surfaceContainerLow, contentColor = colors.onSurface,
                border = BorderStroke(1.dp, colors.outlineVariant)) {
                Column {
                    Column(Modifier.fillMaxWidth().padding(horizontal = padding).padding(top = 8.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(painterResource(R.drawable.ic_clock_24), null, Modifier.size(22.dp), colors.primary)
                            Text("Finish reading", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { hideKeyboard(); onDismiss() }, shape = fieldShape,
                                contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Close") }
                        }
                        Text(book.title, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                    // Only the form scrolls. Clip it before the fixed footer so focus/IME
                    // scrolling can never draw a field or summary across the actions.
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth().clipToBounds()
                        .verticalScroll(rememberScrollState()).padding(padding),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedTextField(pageDraft, onPageChange, readOnly = usingCalculator,
                            label = { Text(if (usingCalculator) "Total pages read" else "Page reached") },
                            supportingText = { entry?.let { Text("${it.pagesRead} pages read", color = colors.primary) } },
                            textStyle = MaterialTheme.typography.headlineSmall,
                            singleLine = true, shape = fieldShape,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { hideKeyboard() }), modifier = Modifier.fillMaxWidth())
                        Column(Modifier.fillMaxWidth().clip(fieldShape).background(colors.surfaceContainer)
                            .border(1.dp, colors.outlineVariant, fieldShape)) {
                            BookDisclosureHeader("Page calculator", R.drawable.ic_calculator_24, calculatorExpanded,
                                compact = true) { calculatorExpanded = !calculatorExpanded }
                            if (calculatorExpanded) {
                                HorizontalDivider(color = colors.outlineVariant)
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(calculatorStart, {
                                        calculatorStart = it.filter(Char::isDigit).take(6)
                                        updateCalculation(calculatorStart, calculatorEnd)
                                    }, label = { Text("Start page") }, singleLine = true, shape = fieldShape,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }), modifier = Modifier.weight(1f))
                                    OutlinedTextField(calculatorEnd, {
                                        calculatorEnd = it.filter(Char::isDigit).take(6)
                                        if (calculatorEnd.isBlank()) onPageChange(book.readingPage.toString())
                                        else updateCalculation(calculatorStart, calculatorEnd)
                                    }, label = { Text("End page") }, singleLine = true, shape = fieldShape,
                                        isError = calculatorStart.isNotBlank() && calculatorEnd.isNotBlank() && calculatedPagesRead == null,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { hideKeyboard() }), modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                    Column(Modifier.fillMaxWidth().background(colors.surfaceContainerLow)
                        .padding(horizontal = padding).padding(top = 10.dp, bottom = padding),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (usingCalculator) Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painterResource(R.drawable.ic_bookmark_24), null, Modifier.size(18.dp), bookBookmarkColor())
                            Text(if (calculatedPagesRead != null) "Continue from page $calculatorEnd" else "Enter a valid page range",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = ::resetCalculator, shape = fieldShape,
                                contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Reset") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { hideKeyboard(); confirmDiscard = true }, shape = fieldShape,
                                border = BorderStroke(1.dp, colors.outlineVariant), modifier = Modifier.weight(1f).height(48.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Text("Discard", color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(onClick = { hideKeyboard(); onSave(bookmarkText, calculatedPagesRead) }, shape = fieldShape,
                                enabled = entry != null && (!usingCalculator || calculatedPagesRead != null),
                                modifier = Modifier.weight(2f).height(48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Icon(painterResource(R.drawable.ic_check_24), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Save session", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, shape = shape,
        containerColor = colors.surfaceContainerLow, tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, colors.outlineVariant, shape),
        title = { Text("Discard session?") },
        text = { Text("Time and pages from this session won't be saved.") },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Discard", color = colors.error) } })
}
