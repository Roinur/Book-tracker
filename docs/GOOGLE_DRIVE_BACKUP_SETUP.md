# Google Drive backup

In Settings, connect Google Drive and enable automatic backup. Book Tracker keeps
five verified versions in its own **Book Tracker Backups** folder. Connecting an
account alone does not upload anything. On a replacement phone, restore a backup
before enabling uploads. Restore uses the existing import preview and only runs
into an empty library. It refuses to overwrite an existing library. This is a
backup/restore feature, not two-way synchronization.

The backup contains books, reading sessions, notes, goals, collection types,
compendium parts, preserved import records and available portable covers.
Google accounts, access tokens and phone-specific folder permissions are not
exported. Files are not end-to-end encrypted. Disconnecting keeps cloud copies.
Background timing depends on Android and network access; Wi-Fi-only is enabled
by default. Manual backup uploads immediately using the available network.

## Google Cloud registration

Use the same Google Cloud project as Sauce Tracker, or a separate project.
Enable **Google Drive API**, configure the consent screen and add the scope
`https://www.googleapis.com/auth/drive.file`. In testing, add your Google account
as a test user. Then create an **Android OAuth client** for the release app:

- Package: `com.roinur.booktracker`
- SHA-1: `82:BE:CE:FB:AC:71:E5:24:30:11:41:FB:74:65:B2:DC:09:0F:C6:4C`

This fingerprint is from the release signing certificate. No client secret,
API key, server or downloaded credentials file belongs in the APK. A different
signing key or the debug package needs its own registered Android client.
Public Google authorization requires completing Google's consent-screen
requirements; adding an Android client alone does not publish the consent app.

## Verification

1. Connect the Google account. Nothing should upload yet.
2. Enable automatic backup, confirming this phone as the backup source.
3. Use **Back up now** and check the verified-upload timestamp.
4. Open **Restore from Google Drive**, choose a version and inspect the import
   preview before confirming. Test restoration on a separate phone or test data.
5. Change a note or goal. A backup is queued after 15 seconds, subject to Android
   background scheduling and network constraints; an hourly job catches up.
6. Pause backup or disconnect. Existing Drive copies remain available.

`BookDriveBackupTest` checks cloud/native checksums, portable covers, a database
round trip, failed uploads, five-version retention and concurrent writers using
a fake transport. It does not use a Google account or upload personal data.

References: [Android authorization](https://developer.android.com/identity/authorization)
and [Drive file scope](https://developers.google.com/workspace/drive/api/guides/api-specific-auth).
