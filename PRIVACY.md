# Privacy

Book Tracker stores the library, reading sessions, notes, collections, compendium parts, goals and active timer state locally. A Google account is only needed for optional Google Drive backup. Book Tracker has no developer-operated account service or application analytics backend. Android system backup may include local app data according to device settings.

Book and cover lookups send search terms or ISBNs to Open Library, Google Books and/or Libris. Optional Google Images search loads Google's website in an in-app browser, where Google and any visited websites may use cookies and process browsing activity under their own policies. Downloading covers contacts the image provider. Selected covers are cached locally and can be included in backups.

Camera access is used for ISBN barcode scanning, optional cover photos and scanning contents pages. Contents scanning can also use a photo selected by the user. ML Kit processes barcode and contents-page images on the device; Book Tracker does not upload these photos for recognition. Google documents that ML Kit may contact its servers and send performance/utilization metrics. See [ML Kit terms and privacy](https://developers.google.com/ml-kit/terms).

JSON exports contain personal reading data and notes. Choose where to store and share them carefully. Original imported data is included for complete preservation.

## Optional Google Drive backup

Google Drive backup is off until the user connects a Google account and enables it. Connecting alone does not upload the library. Authorization uses Google's Android AuthorizationClient with the `drive.file` scope for files created or opened by this integration. Access tokens are not written to Book Tracker's preferences or logs. The connected account name and backup status are stored on this installation and are not exported in portable backups.

When enabled, Book Tracker sends complete backup snapshots to the user's Google Drive: books, sessions, notes, collection types, reading and book goals, compendium parts, preserved original imports and available cover images. Backups are stored in Book Tracker Backups, verified by downloading them again, and retained as five generations. Automatic backup uses an unmetered network by default; the user can change that setting. Manual backup uses the current network.

Drive backup files are not end-to-end encrypted by Book Tracker. Google processes the files under its own service policies, and people given access to them in Drive can read their contents. Turning off automatic backup stops future scheduled backups. Disconnecting clears Book Tracker's connection settings and leaves existing Drive copies in place. The user can delete those files in Drive and revoke Google access through their Google account's connected-app settings.

Downloading a backup opens the existing import preview. Restoration requires confirmation and an empty library; Book Tracker refuses to overwrite an existing library. No library contents are sent to the developer.
