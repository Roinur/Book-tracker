package com.roinur.booktracker

import androidx.compose.material3.Text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun BookSettingsContent(
    vm: BookTrackerViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onPickBackupFolder: () -> Unit,
    onExportLegacy: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BookSettingsActionPanel(
            vm = vm,
            onImport = onImport,
            onExport = onExport,
            onPickBackupFolder = onPickBackupFolder
        )
        LegacyArchivePanel(vm.preservedLegacyArchives, onExportLegacy)
        BookSectionCard {
            Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = vm.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun BookSettingsActionPanel(
    vm: BookTrackerViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onPickBackupFolder: () -> Unit
) {
    BookSectionCard {
        Text("Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Import a backup or legacy export. Existing data and the complete original are preserved.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onImport, enabled = !vm.importBusy, modifier = Modifier.weight(1f)) {
                Text(if (vm.importBusy) "Working…" else "Import Data")
            }
            Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                Text("Export Data")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onPickBackupFolder, modifier = Modifier.weight(1f)) {
                Text("Backup folder")
            }
            OutlinedButton(onClick = vm::backupNow, modifier = Modifier.weight(1f)) {
                Text("Backup now")
            }
        }
        Button(onClick = vm::toggleBookAutoBackup, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.bookAutoBackupEnabled) "Automatic Backup: On" else "Automatic Backup: Off")
        }
        Text(
            "Backup folder: ${vm.backupFolderLabel()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
