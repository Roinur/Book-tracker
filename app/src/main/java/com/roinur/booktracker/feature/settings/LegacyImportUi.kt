package com.roinur.booktracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun LegacyImportDialog(plan: LegacyImportPreview?, busy: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    if (plan == null) return
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text("Import legacy export") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(plan.summary, style = MaterialTheme.typography.titleSmall)
                Text("Adds to your library. Existing books are kept. The complete original file is preserved, including data Book Tracker cannot display.")
                Text("Source records", style = MaterialTheme.typography.titleSmall)
                plan.counts.forEach { (name, count) -> Text("$name: $count") }
                if (plan.unlinkedContent > 0) Text("${plan.unlinkedContent} records cannot be linked to a book. They remain intact in the archive.")
                Text("Unknown session end times stay unknown. Exact milliseconds, titles, page labels, goals and other fields remain in the legacy import archive and exported backups.")
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy) { Text(if (busy) "Importingâ€¦" else "Add and preserve all data") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") } }
    )
}

@Composable
internal fun TrackerBackupImportDialog(plan: TrackerBackupPreview?, busy: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    if (plan == null) return
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text("Restore Book Tracker backup") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${plan.bookCount} books Â· ${plan.noteCount} notes Â· ${plan.sessionCount} sessions", style = MaterialTheme.typography.titleSmall)
                Text("${plan.goalCount} reading goal record Â· ${plan.legacyArchiveCount} preserved legacy import archive")
                Text(if (plan.integrityProtected) "Integrity checksum verified." else "Legacy backup: structure verified, but this older format has no whole-backup checksum.")
                if (plan.exportedAt.isNotBlank()) Text("Exported ${plan.exportedAt}", style = MaterialTheme.typography.bodySmall)
                Text("Safe restore never deletes or replaces your library. Current V3 backups can only be restored into an empty library, which prevents accidental duplicate imports.")
                Text("Content ID ${plan.contentSha256.take(12)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy) { Text(if (busy) "Restoringâ€¦" else "Restore verified backup") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") } }
    )
}

@Composable
internal fun LegacyArchivePanel(archives: List<LegacyArchiveInfo>, onExport: (String) -> Unit) {
    if (archives.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    BookPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        BookDisclosureHeader("Preserved import data", null, expanded,
            "${archives.size} ${if (archives.size == 1) "import" else "imports"}") { expanded = !expanded }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(horizontal = 16.dp)) {
                archives.forEachIndexed { index, archive ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val counts = JSONObject(archive.counts)
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(bookLocalDate(archive.importedAt)?.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: archive.importedAt,
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        listOf("Books" to counts.optInt("BookModel"), "Sessions" to counts.optInt("ReadingSessionModel"), "Notes" to counts.optInt("ThoughtModel")).forEach { (label, count) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(count.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        OutlinedButton(onClick = { onExport(archive.hash) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                            Text("Export original")
                        }
                    }
                }
            }
        }
    }
}
