package com.roinur.booktracker

import androidx.compose.foundation.layout.Arrangement
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
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy) { Text(if (busy) "Importing…" else "Add and preserve all data") } },
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
                Text("${plan.bookCount} books · ${plan.noteCount} notes · ${plan.sessionCount} sessions", style = MaterialTheme.typography.titleSmall)
                Text("${plan.goalCount} reading goal record · ${plan.legacyArchiveCount} preserved legacy import archive")
                Text(if (plan.integrityProtected) "Integrity checksum verified." else "Legacy backup: structure verified, but this older format has no whole-backup checksum.")
                if (plan.exportedAt.isNotBlank()) Text("Exported ${plan.exportedAt}", style = MaterialTheme.typography.bodySmall)
                Text("Safe restore never deletes or replaces your library. Current V3 backups can only be restored into an empty library, which prevents accidental duplicate imports.")
                Text("Content ID ${plan.contentSha256.take(12)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy) { Text(if (busy) "Restoring…" else "Restore verified backup") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") } }
    )
}

@Composable
internal fun LegacyArchivePanel(archives: List<LegacyArchiveInfo>, onExport: (String) -> Unit) {
    if (archives.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Preserved import data", style = MaterialTheme.typography.titleMedium)
            Text("These originals include every field and content type, even when there is no matching screen in Book Tracker. They are also included in Export Data backups.")
            archives.forEach { archive ->
                val counts = JSONObject(archive.counts)
                Text("${counts.optInt("BookModel")} books · ${counts.optInt("ThoughtModel")} thoughts · ${counts.optInt("ReadingSessionModel")} sessions")
                Text(counts.keys().asSequence().sorted().joinToString(" · ") { "${it.removeSuffix("Model")}: ${counts.getInt(it)}" }, style = MaterialTheme.typography.bodySmall)
                Text("Import ${bookLocalDate(archive.importedAt) ?: archive.importedAt} · ${archive.hash.take(12)}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onExport(archive.hash) }) { Text("Export original file") }
            }
        }
    }
}
