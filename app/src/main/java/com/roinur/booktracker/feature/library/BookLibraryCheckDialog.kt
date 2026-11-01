package com.roinur.booktracker

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun BookLibraryCheckDialog(vm: BookTrackerViewModel, onClose: () -> Unit) {
    val history = remember(vm.allBookSessions) { vm.allBookSessions.filter {
        it.session.pagesRead > 0 || it.session.durationSeconds > 0 || it.session.pageReached > 0
    }.mapTo(hashSetOf()) { it.session.bookId } }
    val queue = remember { vm.statsBooks.filter { bookLibraryIssues(it, vm.collectionTypes, it.id in history, vm.activeBookId).isNotEmpty() }
        .sortedBy { it.title.lowercase(Locale.ROOT) }.map { it.id } }
    var cursor by remember { mutableStateOf(0) }
    var updated by remember { mutableStateOf(0) }
    var skipped by remember { mutableStateOf(0) }
    var protected by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val books = remember(vm.statsBooks) { vm.statsBooks.associateBy { it.id } }
    val candidate = queue.withIndex().drop(cursor).firstOrNull { (_, id) -> books[id]?.let {
        bookLibraryIssues(it, vm.collectionTypes, id in history, vm.activeBookId).isNotEmpty()
    } == true }
    val book = candidate?.value?.let(books::get)
    if (book == null) {
        LibraryCheckFrame("Library check", onClose, footer = {
            Button(onClick = onClose, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }) {
            Icon(painterResource(R.drawable.ic_check_24), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Text(if (queue.isEmpty()) "No issues found" else "Check complete", style = MaterialTheme.typography.titleLarge)
            Text("${vm.statsBooks.size} books checked", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (updated + skipped > 0) Text("$updated updated · $skipped skipped", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val position = requireNotNull(candidate).index
    key(book.id) {
        LibraryCheckBook(vm, book, bookLibraryIssues(book, vm.collectionTypes, book.id in history, vm.activeBookId),
            "${position + 1} / ${queue.size}", saving, error,
            onClose = { if (!saving) onClose() },
            onSkip = { skipped++; cursor = position + 1; error = "" },
            onSave = { fix ->
                saving = true; error = ""
                vm.saveLibraryCheck(fix, !protected) { problem ->
                    if (problem == null) { protected = true; updated++; cursor = position + 1 }
                    else error = problem
                    saving = false
                }
            })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryCheckBook(vm: BookTrackerViewModel, book: BookRow, issues: List<BookLibraryIssue>, progress: String,
    saving: Boolean, saveError: String, onClose: () -> Unit, onSkip: () -> Unit, onSave: (BookLibraryFix) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val colors = MaterialTheme.colorScheme
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.authors) }
    val initialPages = book.pageCount.takeIf { it > 0 }?.toString().orEmpty()
    var pages by remember { mutableStateOf(initialPages) }
    var isbn by remember { mutableStateOf(book.isbn) }
    var cover by remember { mutableStateOf(book.coverUrl) }
    var names by remember { mutableStateOf(splitBookCollections(book.collections)) }
    var typeChanges by remember { mutableStateOf<Map<String, BookCollectionType>>(emptyMap()) }
    var status by remember { mutableStateOf(book.status) }
    val initialDate = remember { runCatching { Instant.parse(book.finishedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
        .getOrElse { runCatching { LocalDate.parse(book.finishedAt).toString() }.getOrDefault("") } }
    var date by remember { mutableStateOf(initialDate) }
    var showDetails by remember { mutableStateOf(issues.any { it in setOf(BookLibraryIssue.TITLE, BookLibraryIssue.AUTHOR, BookLibraryIssue.PAGES, BookLibraryIssue.PAGE_RANGE) }) }
    var choosingStatus by remember { mutableStateOf(false) }
    var addingType by remember { mutableStateOf<BookCollectionType?>(null) }
    var editingCollection by remember { mutableStateOf<String?>(null) }
    var coverStage by remember { mutableStateOf<String?>(null) }
    var validation by remember { mutableStateOf("") }
    val allTypes = vm.collectionTypes + typeChanges.mapKeys { it.key.lowercase(Locale.ROOT) }
    val actualTypeChanges = typeChanges.filter { (name, type) -> vm.collectionTypes[name.lowercase(Locale.ROOT)] != type }
    val cleanCollections = normalizeBookCollections(names.joinToString(", "))
    val dirty = title != book.title || author != book.authors || pages != initialPages || isbn != book.isbn || cover != book.coverUrl ||
        cleanCollections != normalizeBookCollections(book.collections) || status != book.status || date != initialDate || actualTypeChanges.isNotEmpty()
    val context = LocalContext.current
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; cover = uri.toString() }
        coverStage = null
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { image ->
        image?.let { runCatching { saveCoverBitmapToInternalStorage(context, it) }.onSuccess { cover = it } }
        coverStage = null
    }
    fun save() {
        val result = runCatching {
            val count = if (pages == initialPages) null else requireNotNull(pages.toIntOrNull()) { "Enter the total number of pages." }
            if (count != null) require(count >= maxOf(book.currentPage, book.parts.maxOfOrNull { it.endPage } ?: 0, 1)) { "Total pages must include the current page and all parts." }
            val completion = if (date == initialDate) null else runCatching { LocalDate.parse(date.trim()) }.getOrElse { error("Use YYYY-MM-DD for the completion date.") }
            if (status == BookStatus.FINISHED && book.status != BookStatus.FINISHED && book.finishedAt.isBlank()) require(completion != null) { "Choose the completion date." }
            BookLibraryFix(book.id, title.takeIf { it != book.title }, author.takeIf { it != book.authors }, count,
                isbn.takeIf { it != book.isbn }, cover.takeIf { it != book.coverUrl }, cleanCollections.takeIf { it != normalizeBookCollections(book.collections) },
                status.takeIf { it != book.status }, completion.takeIf { status == BookStatus.FINISHED }, actualTypeChanges)
        }
        result.onSuccess(onSave).onFailure { validation = it.message ?: "Check the fields." }
    }
    LibraryCheckFrame("Library check", onClose, closeEnabled = !saving, footer = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSkip, enabled = !saving, shape = shape, modifier = Modifier.weight(1f)) { Text("Skip") }
            Button(onClick = ::save, enabled = dirty && !saving, shape = shape, modifier = Modifier.weight(1.6f)) { Text(if (saving) "Saving…" else "Save & next") }
        }
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Review suggestions", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Text(progress, style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCoverImage(cover, title, Modifier.width(76.dp).height(114.dp).fittedClickable(shape, enabled = !saving) { coverStage = "source" }, maxDimensionPx = 384)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title.ifBlank { "Untitled book" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = colors.primary,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (author.isNotBlank()) Text(author, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${book.status.label} · ${book.currentPage}${if (book.pageCount > 0) "/${book.pageCount}" else ""} pages", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                TextButton(onClick = { coverStage = "source" }, enabled = !saving, contentPadding = PaddingValues(0.dp), shape = shape) { Text(if (cover.isBlank()) "Add cover" else "Change cover") }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            issues.forEach { Text(it.label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
        }
        if (BookLibraryIssue.STATUS in issues && status == BookStatus.READING) OutlinedButton(onClick = { status = BookStatus.WISHLIST }, enabled = !saving, shape = shape) { Text("Set Not started") }
        Text("Collections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (names.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEach { name -> FilterChip(selected = true, enabled = !saving, onClick = { editingCollection = name },
                label = { Text(name) }, shape = shape,
                trailingIcon = { Icon(painterResource(R.drawable.ic_chevron_right_24), null, Modifier.size(16.dp).rotate(90f)) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(BookCollectionType.PUBLISHER, BookCollectionType.TYPE, BookCollectionType.THEME).forEach { role ->
                OutlinedButton(onClick = { addingType = role }, enabled = !saving, shape = shape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Text("Add ${role.label.lowercase(Locale.ROOT)}", style = MaterialTheme.typography.labelLarge) }
            }
        }
        OutlinedButton(onClick = { choosingStatus = true }, enabled = !saving && vm.activeBookId != book.id, shape = shape, modifier = Modifier.fillMaxWidth()) {
            Text("Status · ${status.label}", Modifier.weight(1f)); Icon(painterResource(R.drawable.ic_chevron_right_24), null, Modifier.size(18.dp).rotate(90f))
        }
        if (status == BookStatus.FINISHED) OutlinedTextField(date, { date = it }, label = { Text("Finished · YYYY-MM-DD") }, enabled = !saving,
            singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().fittedClickable(shape, enabled = !saving) { showDetails = !showDetails }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Book information", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(painterResource(R.drawable.ic_chevron_right_24), null, Modifier.size(20.dp).rotate(if (showDetails) 270f else 90f), tint = colors.primary)
        }
        if (showDetails) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, enabled = !saving, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(author, { author = it }, label = { Text("Author") }, enabled = !saving, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pages, { pages = it }, label = { Text("Total pages") }, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(isbn, { isbn = it }, label = { Text("ISBN · optional") }, enabled = !saving, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
        }
        if (validation.isNotBlank() || saveError.isNotBlank()) Text(saveError.ifBlank { validation }, color = colors.error, style = MaterialTheme.typography.bodySmall)
    }
    if (choosingStatus) LibraryCheckFrame("Reading status", { choosingStatus = false }) {
        BookStatus.entries.forEach { option ->
            OutlinedButton(onClick = { status = option; choosingStatus = false }, shape = shape, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (status == option) colors.primary else colors.onSurface)) { Text(option.label) }
        }
    }
    addingType?.let { role ->
        LibraryCheckCollectionPicker((vm.collectionSuggestions + names).distinctBy { it.lowercase(Locale.ROOT) }, allTypes, role, { addingType = null }) { name, needsType ->
            if (names.none { it.equals(name, true) }) names = names + name
            if (needsType) typeChanges = typeChanges + (name to role)
            addingType = null
        }
    }
    editingCollection?.let { name -> LibraryCheckFrame(name, { editingCollection = null }) {
        Text("Collection type applies to every book using it.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        BookCollectionType.entries.forEach { role ->
            OutlinedButton(onClick = { typeChanges = typeChanges + (name to role); editingCollection = null }, shape = shape, modifier = Modifier.fillMaxWidth()) {
                Text(role.label, Modifier.weight(1f))
                if (allTypes[name.lowercase(Locale.ROOT)] == role) Icon(painterResource(R.drawable.ic_check_24), null, Modifier.size(18.dp))
            }
        }
        TextButton(onClick = {
            names = names.filterNot { it.equals(name, true) }
            if (vm.collectionSuggestions.none { it.equals(name, true) }) typeChanges = typeChanges.filterKeys { !it.equals(name, true) }
            editingCollection = null
        }) { Text("Remove from this book", color = colors.error) }
    } }
    if (coverStage == "source") CoverSourceDialog({ coverStage = null }, { coverStage = "external"; gallery.launch(arrayOf("image/*")) },
        { coverStage = "external"; camera.launch(null) }, {
            vm.prepareCoverSearch(listOf(title, author, isbn, names.filter { allTypes[it.lowercase(Locale.ROOT)] == BookCollectionType.PUBLISHER }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(" "))
            coverStage = "search"
        }, cover, title)
    if (coverStage == "search") CoverSearchDialog(vm, { coverStage = null }) { cover = it; coverStage = null }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryCheckCollectionPicker(names: List<String>, types: Map<String, BookCollectionType>, role: BookCollectionType,
    onClose: () -> Unit, onPick: (String, Boolean) -> Unit) {
    var query by remember { mutableStateOf("") }
    val available = names.filter { (types[it.lowercase(Locale.ROOT)] == role || it.lowercase(Locale.ROOT) !in types) && it.contains(query, true) }
    val clean = query.trim()
    val existing = names.firstOrNull { it.equals(clean, true) }
    val existingType = existing?.let { types[it.lowercase(Locale.ROOT)] }
    LibraryCheckFrame("Add ${role.label.lowercase(Locale.ROOT)}", onClose) {
        OutlinedTextField(query, { query = it }, label = { Text("Search or create") }, singleLine = true, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            available.forEach { name -> FilterChip(selected = false, onClick = { onPick(name, name.lowercase(Locale.ROOT) !in types) }, label = { Text(name) }, shape = RoundedCornerShape(8.dp)) }
        }
        if (clean.isNotBlank() && existing == null) Button(onClick = { onPick(clean, true) }, enabled = clean.none { it in ",;\n\r" }, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Text("Create $clean", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (existingType != null && existingType != role) Text("$existing is a ${existingType.label.lowercase(Locale.ROOT)} collection. Change its type from its chip first.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LibraryCheckFrame(title: String, onClose: () -> Unit, closeEnabled: Boolean = true,
    footer: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = { if (closeEnabled) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.88f),
            shape = RoundedCornerShape(10.dp), color = colors.surfaceContainerLow, border = BorderStroke(1.dp, colors.outlineVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onClose, enabled = closeEnabled, shape = RoundedCornerShape(8.dp)) { Text("Close") }
                }
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
                footer?.invoke()
            }
        }
        }
    }
}
