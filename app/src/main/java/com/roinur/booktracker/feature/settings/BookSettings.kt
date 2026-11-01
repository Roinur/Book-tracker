package com.roinur.booktracker

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class BookSettingsPage(val title: String, val description: String, val icon: Int) {
    ROOT("Settings", "Choose a settings category.", R.drawable.ic_grid_24),
    APPEARANCE("Appearance", "Theme, accent color and Extra dark", R.drawable.ic_palette_24),
    NOTIFICATIONS("Notifications", "Reading goals and individual book reminders", R.drawable.ic_notification_24),
    LOCAL_DATA("Data & backups", "Import, export, local backups and recovery copies", R.drawable.ic_backup_24),
    DRIVE_BACKUP("Google Drive backup", "Verified app backups and cloud restore", R.drawable.ic_cloud_backup_24),
    BOOK_LIST("Book List", "Your library in a linked Google Sheet", R.drawable.ic_list_24),
    OBSIDIAN("Obsidian export", "Markdown notes in your existing Drive vault", R.drawable.ic_linked_notes_24),
    ABOUT("About", "Book Tracker version and build", R.drawable.ic_info_24)
}

@Composable
internal fun BookSettingsOverview(onNavigate: (BookSettingsPage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Choose a settings category.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        BookSettingsPage.entries.filter { it != BookSettingsPage.ROOT }.forEach { page ->
            BookPanel(contentPadding = PaddingValues(0.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 76.dp)
                    .fittedClickable(RoundedCornerShape(8.dp)) { onNavigate(page) }.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(page.icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(page.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(page.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(painterResource(R.drawable.ic_chevron_right_24), "Open ${page.title}", Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun BookSettingsContent(
    vm: BookTrackerViewModel,
    page: BookSettingsPage,
    onNavigate: (BookSettingsPage) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onPickBackupFolder: () -> Unit,
    onCheckBackup: () -> Unit,
    onExportRecovery: (String) -> Unit,
    onExportLegacy: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    var recoveryExpanded by rememberSaveable { mutableStateOf(false) }
    val cloudAllowed = !vm.importBusy && !vm.backupBusy && vm.pendingTrackerImport == null && vm.pendingLegacyImport == null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (page) {
        BookSettingsPage.ROOT -> BookSettingsOverview(onNavigate)
        BookSettingsPage.APPEARANCE -> {
        BookPanel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BookSettingsHeading("Theme & accent", R.drawable.ic_palette_24)
                    Text("Tap to change theme. Hold for accent colors.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ThemeToggleWithAccentPicker(vm.themeMode, vm.accentMode, vm::cycleThemeMode, vm::chooseAccentMode)
            }
        }
        BookPanel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Extra dark", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Switch(checked = vm.extraDark, onCheckedChange = vm::updateExtraDark)
            }
        }
        }
        BookSettingsPage.NOTIFICATIONS -> {
        BookPanel {
            BookSettingsHeading("Notifications", R.drawable.ic_notification_24)
            GoalNotificationSetting("Reading goals", "Daily, monthly and yearly", vm.readingGoalNotificationsEnabled) { enabled ->
                vm.updateReadingGoalNotifications(enabled)
                if (enabled) onRequestNotificationPermission()
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            GoalNotificationSetting("Book goals", "Individual book targets", vm.bookGoalNotificationsEnabled) { enabled ->
                vm.updateBookGoalNotifications(enabled)
                if (enabled) onRequestNotificationPermission()
            }
        }
        }
        BookSettingsPage.LOCAL_DATA -> {
        BookSettingsActionPanel(
            vm = vm,
            onImport = onImport,
            onExport = onExport,
            onPickBackupFolder = onPickBackupFolder
        )
        OutlinedButton(shape = BookSettingsButtonShape, onClick = onCheckBackup, enabled = !vm.importBusy, modifier = Modifier.fillMaxWidth()) { Text("Check backup") }
        BookPanel(contentPadding = PaddingValues(0.dp)) {
            BookDisclosureHeader("Recovery copies", null, recoveryExpanded,
                "${vm.recoveryCopies.size} ${if (vm.recoveryCopies.size == 1) "copy" else "copies"}") { recoveryExpanded = !recoveryExpanded }
            if (recoveryExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("The latest five copies before imports, book edits, session edits and deletions. Stored on this phone.", style = MaterialTheme.typography.bodySmall)
            if (vm.recoveryCopies.isEmpty()) Text("No recovery copies yet.", style = MaterialTheme.typography.bodySmall)
            vm.recoveryCopies.forEach { copy ->
                val stamp = java.time.Instant.ofEpochMilli(copy.createdAt).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm"))
                OutlinedButton(shape = BookSettingsButtonShape, onClick = { onExportRecovery(copy.name) }, modifier = Modifier.fillMaxWidth()) {
                    Text("$stamp · ${copy.reason}")
                }
            }
        }
                }
            }
        LegacyArchivePanel(vm.preservedLegacyArchives, onExportLegacy)
        }
        BookSettingsPage.DRIVE_BACKUP -> BookPanel {
            BookDriveBackupSettings(allowed = cloudAllowed, onRestore = vm::previewCloudBackup)
        }
        BookSettingsPage.BOOK_LIST -> BookPanel {
            BookListSyncSettings(allowed = cloudAllowed, onChanged = vm::reload)
        }
        BookSettingsPage.OBSIDIAN -> {
            BookPanel {
                BookSettingsHeading("How Drive sync works", R.drawable.ic_linked_notes_24)
                Text("Book Tracker keeps your notes on this phone and exports Markdown copies to Drive. Drive for desktop syncs those files to your computer; Obsidian reads the local vault.",
                    style = MaterialTheme.typography.bodySmall)
                Text("The exported files stay in Drive and use Google storage, including when available offline. They are not removed after syncing. Deleting a synced Drive file also removes it from the synced vault.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("App backups are a separate feature under Google Drive backup.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            BookPanel { BookMarkdownExportSettings(allowed = cloudAllowed) }
        }
        BookSettingsPage.ABOUT -> BookSettingsAbout()
        }
    }
}

@Composable
private fun GoalNotificationSetting(title: String, subtitle: String, enabled: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onChanged)
    }
}

@Composable
internal fun BookSettingsActionPanel(
    vm: BookTrackerViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onPickBackupFolder: () -> Unit
) {
    BookPanel {
        BookSettingsHeading("Data & backups", R.drawable.ic_backup_24)
        Text("Import a backup or legacy export. Existing data and the complete original are preserved.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(shape = BookSettingsButtonShape, onClick = onImport, enabled = !vm.importBusy, modifier = Modifier.weight(1f)) {
                Text(if (vm.importBusy) "Working…" else "Import Data")
            }
            Button(shape = BookSettingsButtonShape, onClick = onExport, modifier = Modifier.weight(1f)) {
                Text("Export Data")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(shape = BookSettingsButtonShape, onClick = onPickBackupFolder, modifier = Modifier.weight(1f)) {
                Text("Backup folder")
            }
            OutlinedButton(shape = BookSettingsButtonShape, onClick = vm::backupNow, enabled = !vm.backupBusy, modifier = Modifier.weight(1f)) {
                Text(if (vm.backupBusy) "Backing up…" else "Backup now")
            }
        }
        Button(shape = BookSettingsButtonShape, onClick = vm::toggleBookAutoBackup, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.bookAutoBackupEnabled) "Automatic Backup: On" else "Automatic Backup: Off")
        }
        Text(
            "Backup folder: ${vm.backupFolderLabel()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val backup = vm.backupStatus
        val success = backup.lastSuccessAtMs.takeIf { it > 0L }?.let {
            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm"))
        }
        Text(if (success == null) "No verified backup recorded yet" else "Last verified backup: $success",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (backup.error.isNotBlank()) {
            Text("Last backup failed: ${backup.error}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}
