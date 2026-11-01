# Privacy

Book Tracker stores the library, reading sessions, notes, collections, compendium parts, goals and active timer state locally. A Google account is only needed for optional Google Drive backup, Book List synchronization or Obsidian Markdown export. Book Tracker has no developer-operated account service or application analytics backend. Android system backup may include local app data according to device settings.

Book and cover lookups send search terms or ISBNs to Open Library, Google Books and/or Libris. Optional Google Images search loads Google's website in an in-app browser, where Google and any visited websites may use cookies and process browsing activity under their own policies. Downloading covers contacts the image provider. Selected covers are cached locally and can be included in backups.

Camera access is used for ISBN barcode scanning, optional cover photos and scanning contents pages. Contents scanning can also use a photo selected by the user. ML Kit processes barcode and contents-page images on the device; Book Tracker does not upload these photos for recognition. Google documents that ML Kit may contact its servers and send performance/utilization metrics. See [ML Kit terms and privacy](https://developers.google.com/ml-kit/terms).

JSON exports contain personal reading data and notes. Choose where to store and share them carefully. Original imported data is included for complete preservation.

## Optional Google Drive backup

Google Drive backup is off until the user connects a Google account and enables it. Connecting alone does not upload the library. Authorization uses Google's Android AuthorizationClient with the `drive.file` scope for files created or opened by this integration. Access tokens are not written to Book Tracker's preferences or logs. The connected account name and backup status are stored on this installation and are not exported in portable backups.

When enabled, Book Tracker sends complete backup snapshots to the user's Google Drive: books, sessions, notes, collection types, reading and book goals, compendium parts, preserved original imports and available cover images. Backups are stored in Book Tracker Backups, verified by downloading them again, and retained as five generations. Automatic backup uses an unmetered network by default; the user can change that setting. Manual backup uses the current network.

Drive backup files are not end-to-end encrypted by Book Tracker. Google processes the files under its own service policies, and people given access to them in Drive can read their contents. Turning off automatic backup stops future scheduled backups. Disconnecting clears Book Tracker's connection settings and leaves existing Drive copies in place. The user can delete those files in Drive and revoke Google access through their Google account's connected-app settings.

Downloading a backup opens the existing import preview. Restoration requires confirmation and an empty library; Book Tracker refuses to overwrite an existing library. No library contents are sent to the developer.

## Optional Book List synchronization

Creating a linked Book List uploads library overview fields to a new personal Google spreadsheet: author, title, page count, ownership, reading status, reading priority, total pages and hours read, collections, completion date, ISBN, rating and printed bookmark. A hidden persistent book identifier links rows to the app. Individual notes, session history, cover images and original imports are not included in this spreadsheet.

Book List uses the existing Google authorization with the `drive.file` scope and accesses only a spreadsheet created and marked for this connection. It does not link or edit a user's pre-existing spreadsheet. Enabling it permits two-way updates of the overview fields. Account binding, merge baseline and conflict choices are kept on this installation and are not included in portable backups. Pause or unlink stops scheduled synchronization without deleting the spreadsheet or the library. Google and people given access to the spreadsheet can read and edit its contents under its sharing permissions. Book List is not end-to-end encrypted by Book Tracker.

## Optional Obsidian Markdown export

Markdown export is off until the user chooses an existing Google Drive folder and enables it separately. It uses Google's Android folder Picker and only the `drive.file` permission, with no full-Drive scope. Selected Google account, folder, file identity reservations and export status are stored on this installation, separately from backup controls. Access tokens remain in memory and are not saved or logged. Portable library backups include the book's stable UUID, but not the Google grant or installation's export connection state.

When enabled, Markdown export sends book metadata, general book notes, reading notes, quotes, thoughts, words and countdowns to plain UTF-8 files in the selected folder. It updates only the marked app block, preserving personal Obsidian text outside it. No Obsidian edits are imported into the app. Files are not end-to-end encrypted by Book Tracker; Google and anyone given access in Drive can read them. Turning export off stops subsequent exports and keeps existing files. Deleting an app book does not delete its Markdown file. The user can manage exported files and revoke Google access in Google's own interfaces.
