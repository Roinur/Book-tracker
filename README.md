# Book Tracker

<img src="app/src/main/res/drawable/app_icon.png" width="96" alt="Book Tracker icon">

An Android reading journal for books, reading plans, sessions, notes and personal goals. Your library is stored locally; no account is required.

**Version 1.2** adds reading plans, yearly text/image exports, optional Book List and Obsidian integrations, recap cards and completion-rating prompts. [What changed in 1.2](docs/RELEASE_1.2.md).

## Screenshots

Book Tracker 1.2 on a real device, with the maintainer's own library and reading history.

<table>
<tr><th>Library</th><th>Gallery</th></tr>
<tr><td><img src="docs/screenshots/library.png" width="320" alt="Library list with current books and reading progress"></td><td><img src="docs/screenshots/library-gallery.png" width="320" alt="Gallery of current books with covers"></td></tr>
<tr><th>Stats</th><th>Reading recap</th></tr>
<tr><td><img src="docs/screenshots/stats.png" width="320" alt="October 2026 reading statistics and heatmap"></td><td><img src="docs/screenshots/reading-recap.png" width="320" alt="October reading recap with totals, records and finished books"></td></tr>
<tr><th>Reading plans</th><th>Yearly reading list export</th></tr>
<tr><td><img src="docs/screenshots/reading-plans.png" width="320" alt="2026 reading plan with priority tiers and remaining reading estimates"></td><td><img src="docs/screenshots/reading-list-export.png" width="320" alt="Image export preview of the library grouped by reading year"></td></tr>
<tr><th>Book details</th><th>Reading map</th></tr>
<tr><td><img src="docs/screenshots/book-details.png" width="320" alt="Book details with progress, reading goal, pace and session history"></td><td><img src="docs/screenshots/reading-map.png" width="320" alt="Reading map connecting 147 books across 17 collection families"></td></tr>
<tr><th>Reading goals</th><th>Reading progress</th></tr>
<tr><td><img src="docs/screenshots/reading-goals.png" width="320" alt="Daily, monthly and yearly reading goals"></td><td><img src="docs/screenshots/reading-progress.png" width="320" alt="Monthly reading progress and completed compendium parts"></td></tr>
<tr><th>Author trends</th><th>Collection trends</th></tr>
<tr><td><img src="docs/screenshots/trends-authors.png" width="320" alt="Author trends"></td><td><img src="docs/screenshots/trends-collections.png" width="320" alt="Collection trends"></td></tr>
<tr><th>Settings</th><th>About</th></tr>
<tr><td><img src="docs/screenshots/settings-1.2.png" width="320" alt="Book Tracker 1.2 Settings categories"></td><td><img src="docs/screenshots/about-1.2.png" width="320" alt="Book Tracker 1.2 version and build"></td></tr>
</table>

## Features

- Library list and gallery, pinned books, search and manual reading status. Gallery cards support adjustable columns, frosted cover previews and optional hidden titles.
- Typed collections for themes, book types, publishers and other groups. Expand collection chips, assign or remove them across selected books, undo assignments, rename and merge.
- Expand collections in Add book and Edit book to see all chips. Scroll while expanded, then collapse to return to the fixed layout.
- Reading map connects books and overlapping collections, with status colours and filters by collection type.
- Collection progress shows status breakdowns and actual pages read. Tap a collection for pace, remaining time, session estimates and books closest to completion.
- Sort by total pages, title, author, rating or progress; deselect sorting to use date-added order. New books start as Not started.
- Light, dark and adaptive themes, selectable accent colours and an optional Extra dark setting.
- Tap an author in Book details to search your entire library for that author.
- Reading timer with pause/resume, manual sessions and durations longer than 24 hours. Continue reading follows your latest session, independent of library filters.
- Swipe left to start reading; swipe right to pin or unpin.
- Automatically saved reading notes, global note search and copying notes in date order.
- Daily and monthly time/page goals, yearly book goals, heatmaps and reading progress summaries.
- An ongoing Reading goals notification shows daily, monthly and yearly progress. Settings has independent switches for overall reading-goal and individual book-goal notifications.
- Reading trends compares authors or collections over Today, Week, Month, Year or All time. Choose Pages, Time or Sessions, compare totals or share, and inspect positive ratings or average ratings.
- Interactive Stats and book graphs: hold and drag to inspect points. Stats keeps the same sizing across periods and fits the available screen area.
- Hold Week, Month, Year or All time on Stats for a themed reading recap of the selected period, with reading totals, daily records, streaks, covers and completion ratings. Copy, save or share the recap as a PNG image; holding the selected tab preserves its browsed historical dates.
- Month uses calendar months, including leap-year February and 31-day months. Historical months include the whole month; current-month totals stop today while the chart shows the full month's days.
- Settings opens a compact category overview with dedicated Appearance, Notifications, Data & backups, Google Drive backup, Book List, Obsidian export and About pages. Purpose-specific icons, compact panels and square 8 dp buttons follow the Book List style. Toolbar and Android Back return from a category to the overview.
- Per-book deadlines or daily page targets, with today's remaining pages, estimated reading time and finish date. Swipe the Pages read card to set a goal. Active goals have a separate ongoing notification. Time estimates use book pace, then publisher pace, then overall pace.
- Compendium parts with inclusive page ranges, individual covers and ratings. Inspect each work through segmented progress bars and the reading screen. Reading time is split proportionally when a session crosses parts, and completed works appear separately in statistics until the compendium is finished.
- Scan contents pages with the camera or import photos to propose part titles and start pages. Recognition runs on the device; review, deselect and edit the proposals before applying them. Multiple photos can be combined.
- Historical sessions can count toward lifetime totals while being excluded from dated statistics.
- ISBN scanning and metadata from Open Library, Google Books and Libris, with missing fields filled across matching editions.
- Cover search by ISBN, title or author, optional Google Images search, and local cover selection.
- Page calculator for session logs in compendiums and editions with different page numbering.
- Verified JSON backups with embedded covers, collection types and book goals. Check a backup before restoring, export local recovery copies and inspect preserved original import data.
- Optional Google Drive backup, manual or automatic, with Wi-Fi-only scheduling and five verified versions. Download a version into the import preview before restoring to an empty library. See [Drive setup](docs/GOOGLE_DRIVE_BACKUP_SETUP.md).
- Optional automatic [Obsidian Markdown export](docs/OBSIDIAN_MARKDOWN_EXPORT.md) to an existing Drive vault folder. Stable book files, a durable background queue and conditional updates preserve personal notes outside the app's marked block. This is separate from backups and does not import Obsidian edits.
- Optional personal [Book List](docs/BOOK_LIST_SYNC.md) in a newly created Google spreadsheet. Sync library fields both ways, edit ownership and reading priority, review conflicts, and keep undated reading totals separate from heatmap activity. Existing spreadsheets are untouched.

## Install and update

Download the signed APK from [GitHub Releases](https://github.com/Roinur/Book-Tracker/releases/latest). Android 8.0 (API 26) or later is required; the release targets ARM64 devices.

Install an update over the existing app. **Do not uninstall or clear storage to update.** Android requires the same application ID and signing key. Export a backup in Settings and keep a copy outside the phone.

The application ID is `com.roinur.booktracker`. Current release: **1.2, Android build 121**.

Release history: [1.0](https://github.com/Roinur/Book-tracker/releases/tag/v1.0) preserves the original first release (Android build 33). [1.1](https://github.com/Roinur/Book-tracker/releases/tag/v1.1) covers build 88, including Reading map, collection editing and progress, book goals, compendium parts, contents scanning, Drive backup and gallery improvements. [1.2](https://github.com/Roinur/Book-tracker/releases/tag/v1.2) covers build 121 and the [new features since 1.1](docs/RELEASE_1.2.md). See the [changelog](CHANGELOG.md).

## Build

Requirements: JDK 17, Android SDK platform 34 and Build Tools 34.0.0. Open this directory in Android Studio and let Gradle resolve dependencies, or configure `ANDROID_HOME` / an ignored `local.properties` file.

```powershell
git clone https://github.com/Roinur/Book-tracker.git
cd Book-tracker
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testReleaseUnitTest
```

On macOS/Linux use `bash ./gradlew` instead of `.\gradlew.bat`. The debug app has a separate `.debug` application ID. It does not replace the daily release.

For signed releases, follow [release signing](RELEASE_SIGNING.md), then:

```powershell
.\gradlew.bat :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Instrumentation tests use uniquely named temporary databases. With a release signing configuration and an attached test device:

```powershell
.\gradlew.bat :app:connectedReleaseAndroidTest
```

The first build needs internet access. No private export, signing key or local SDK path belongs in this repository.

## Data and privacy

Library records, notes and active-session state are local. Optional Drive backup uploads complete snapshots after you enable it. Book List optionally syncs library overview fields with a new Google Sheet, and Obsidian export separately sends Markdown notes to a chosen Drive folder. These files are not end-to-end encrypted by Book Tracker. Android system backup may also copy app data depending on device settings. Metadata and cover searches send search terms to Open Library, Google Books or Libris. Optional Google Images search opens Google's website inside the app. ML Kit processes ISBN barcodes and contents-page photos on the device; its SDK may send operational metrics to Google. See [privacy](PRIVACY.md).

See [data compatibility](DATA_COMPATIBILITY.md) before changing storage or import code and [architecture](ARCHITECTURE.md) for the source layout.

## Credits and license

Book Tracker grew out of Roinur's Sauce Tracker.

No license is granted for Book Tracker source code. Copyright (c) 2026 Roinur. All rights reserved. Third-party components retain their own licenses; see [third-party notices](THIRD_PARTY_NOTICES.md). Book cover artwork remains the property of its respective rights holders and is not included as sample library data.
