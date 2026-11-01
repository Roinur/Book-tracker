# Obsidian Markdown export

Markdown export is an independent opt-in feature. Google Drive JSON backup, local backups and Book List keep their own controls. Connecting or choosing a folder does not upload; enabling automatic Markdown export queues the existing library. No Obsidian changes are imported, and deleting an app book never deletes a Drive file.

## Connect an existing vault

1. Keep the existing vault in Drive and make its entire folder available offline on the computer.
2. In Book Tracker Settings, open **Obsidian export** → **Obsidian · Markdown export**. Use **Choose configured Zettelkasten folder**, if available, or **Choose existing Drive export folder**.
3. Choose the same Google account as Drive for desktop. In Google's Picker, select the existing **Böcker/BookTracker** folder inside the vault. The app creates neither a vault nor an export folder. The selected folder must be named BookTracker and writable. Confirm its location before selecting Use folder.
4. Enable **Automatic Markdown export**. Pending book changes are exported after about ten seconds, subject to network and Android background scheduling. A periodic job catches up about every fifteen minutes, including edits saved just before process termination.
5. Use **Export existing books** to queue all books again, or **Retry pending** after fixing an error. Offline notes always remain saved locally. Last confirmed export is recorded only after the uploaded content is read back and verified on Drive; a queued operation is not success.

Disabling cancels scheduled work and stops subsequent writes. An HTTP request already sent may finish. Files and file identities are kept. Re-enabling exports pending changes. Wi-Fi-only is specific to this feature and does not change backup's network setting.

For an installation-specific shortcut, an optional ignored `markdown-export-target.properties` can supply an existing export-folder ID. The hint narrows Google's Picker selection; it grants no permission and never enables export. Public release builds omit this hint and use normal folder selection. Never put credentials in the file.

## Google configuration and least privilege

The implementation uses Play services authorization 22.0.0 and Google's current Android Picker resource parameters, including folder selection, consent and `setOptOutIncludingGrantedScopes(true)`. It requests only `https://www.googleapis.com/auth/drive.file`, with no full-Drive scope. Enable both Google Drive API and Google Picker API in the same Google Cloud project as the existing Android OAuth registration. Keep the existing package and signing certificate registration. No web client secret, API key, custom OAuth redirect or token storage is needed by this app flow.

The export account/target, queue acknowledgement and reserved Drive IDs are installation state and are not portable library backup data. The book's existing `list_sync_key` UUID is library data and is already included in native backups. Google authorization must be granted again if needed after reinstall/restore. Previously app-created files can be rediscovered under their stable filenames. If an existing file is not granted to this app, grant that file through Google before trying to use it; `drive.file` does not provide unrestricted visibility of the account.

## File contract and safety

- Plain UTF-8 `text/markdown`, never converted to a Google document. One `book-<stable UUID>.md` per book. Other persistent IDs use a collision-free base64url encoding with a distinct prefix. A title edit never renames the file.
- New files contain YAML `booktracker_id: "<original ID>"` and `booktracker_export_version: 1`, followed by exact standalone `<!-- booktracker:begin -->` and `<!-- booktracker:end -->` marker lines.
- Available book information, general notes, reading notes, quotes, thoughts, words and countdowns are grouped under clear headings. Original dates, pages, duration, paragraphs and Swedish characters are retained. New documents end with **Mina Obsidian-anteckningar** outside the managed block.
- Author fields link to shared `[[Författare/Author Name|Author Name]]` targets, so books with the same author connect in Obsidian's graph. Commas (the app metadata's author separator), semicolons, newlines and spaced ampersands separate coauthors; spelling variants are not guessed or merged. Path/link delimiters in names are encoded without collisions. These can be unresolved Obsidian nodes; no extra Drive folders, author files, permissions or automatic deletions are required. Clicking an author link lets the user create an author note. Keep "Existing files only" off in Graph to show authors before their notes exist. Use **Export existing books** after upgrading to add author links to already exported files.
- Updates read the existing file, validate its YAML identity and unique ordered markers, and replace only the block interior. All bytes outside it, including user YAML properties, comments and wikilinks, remain unchanged. CRLF and a UTF-8 BOM are supported. Literal reserved marker text in app notes is escaped in the rendered block so it cannot become a delimiter.
- Reads compare metadata versions, strong resource ETags and content MD5 across the media download. The live v3 metadata response did not provide a usable ETag, so reads use the still-supported v2 file resource's documented `etag` and updates use v2's media PUT with `If-Match`. Lookup and retry-safe creation use v3. HTTP 412 is a conflict; missing/weak version metadata stops export rather than doing an unconditional write. A retry rereads the current file before merging.
- Before create, a generated Drive file ID is committed locally. Unknown upload results reuse that same ID, including after app restart; 409 is reconciled by reading and validating that file. Existing-name duplicates stop export for review.
- Missing previously confirmed files, moved/renamed files, read-only files, invalid markers and wrong IDs produce an error without automatic recreation or overwrite. Restore the file/filename/location or permission in Drive, then retry. Deleting a book only removes its local queue row.

SQLite triggers add or revise an outbox row in the same transaction as book/note changes, including live note autosave, deletion and restore. Acknowledgement is conditional on the exact local revision, so a newer edit remains pending. Scheduling, network and authorization failures cannot roll back an already saved note.

## Validation boundaries

`BookMarkdownTest` covers rendering and merge preservation, repeat/uncertain creates, process restart, title changes, restore discovery, offline retry, concurrent edits, duplicate/missing/renamed files and invalid markers/IDs. `GoogleMarkdownTransportTest` checks actual HTTP headers, UTF-8 request bodies, strict read verification and 412/403/404/409 handling against an isolated local server.

`BookMarkdownPersistenceTest` uses randomly named test databases and isolated preferences to check transactional outbox changes, reopen, stale acknowledgements, book deletion, backup/restore identity and disabled state. Existing `BookDriveBackupTest` remains the regression test for verified backups and retention. No test clears or replaces the daily library.


Official references checked 2026-10-28:

- [Google Picker for Android](https://developers.google.com/workspace/drive/picker/guides/desktop-mobile-picker)
- [Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Pre-generated IDs and retry-safe uploads](https://developers.google.com/workspace/drive/api/guides/manage-uploads#use_a_pre-generated_id_to_upload_files)
- [Drive file IDs](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/generateIds)
- [Drive HTTP and patch behavior](https://developers.google.com/workspace/drive/api/guides/performance)
- [Drive v2 resource ETag](https://developers.google.com/workspace/drive/api/reference/rest/v2/files)
- [Drive v2 conditional media update](https://developers.google.com/workspace/drive/api/reference/rest/v2/files/update)
