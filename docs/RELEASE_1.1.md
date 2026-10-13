Book Tracker 1.1 adds Reading map, typed collections and bulk editing, collection progress, per-book goals, compendium parts, contents-page scanning and optional Google Drive backup. It also improves gallery performance, navigation, metadata lookup, graphs and data protection.

This release covers the changes from the original 1.0 release (build 33) to 1.1 (build 88).

### Compendium parts

- Define named works with inclusive page ranges inside a book. End pages include the printed page; 3 through 74 contains 72 pages. Older stored ranges are converted compatibly.
- Give each part its own cover and rating through the existing cover-selection tools. Part covers are cached locally and included in portable backups.
- Assign parts in compact rows; expand a row when editing its title or page range.
- Inspect parts through segmented progress bars in Continue reading, Pages read and the reading screen. See page counts, completed progress, reading time and estimated remaining time per work.
- Allocate session time proportionally to pages read in each part. Imported sessions without a reached page use chronologically accumulated pages, anchored by explicit endpoints.
- Completed parts appear as individual works in statistics until the parent compendium is finished. Totals and yearly book-goal counts are not inflated by the parts.
- Keep the pages/time breakdown free of part-only thumbnails, consistent with other books.

### Scan contents pages

- Use the camera or import a photo from Assign parts. On-device text recognition proposes titles in reading order and start pages when available.
- Review and deselect proposals, combine overlapping photos without duplicate works, and refine titles and inclusive page ranges before applying.
- Recover isolated page numbers through an extra recognition pass over the enlarged number column. Match angled rows using their geometry and ignore roman page-number fragments as titles.
- Camera capture and gallery import use the same recognition path. Unreadable page numbers remain editable instead of being invented.

### Google Drive backup

- Connect a Google account for optional complete library backups. Connecting alone does not enable uploads.
- Back up books, sessions, notes, collection types, reading and book goals, compendium parts, preserved original imports and available portable cover images.
- Keep five verified generations in the separate Book Tracker Backups folder. Uploads are downloaded and checksum-checked before publication and rotation of older versions.
- Enable automatic backup with an unmetered-network option, or upload manually. Unchanged library contents do not force duplicate automatic uploads.
- Detect another device changing the latest backup before publishing. Account connection state and authorization tokens are excluded from portable backups.
- Download a version into the existing import preview. Restoration requires confirmation and an empty library, protecting existing data from replacement or duplicate imports.
- Show the latest verified upload and operation status. Disconnecting retains cloud files. Drive files are not end-to-end encrypted by Book Tracker.

### Per-book reading goals

- Set a deadline in days, weeks or months, or choose how many pages to read each day.
- Swipe between Pages read and Book goal in Book details. An active goal opens on the goal page automatically.
- See today's target, pages still to read today, estimated time for today's reading, total pages and time remaining, days remaining and projected finish date.
- Deadline goals recalculate the daily quota as the deadline approaches. Logged pages reduce today's remaining quota.
- Each active book goal has its own ongoing notification showing today's remaining pages and estimated time. Tap it to open that book's goal. Reading-session notifications stay separate.
- Remaining-time estimates use the book's own timed reading first, then books sharing a Publisher collection, then your overall reading pace. The same fallback applies in Book details; sessions excluded from statistics do not affect the estimate.
- Goals are stored locally and included in backups. The swipe pages have more space between them and rounded progress bars.

### Reading-goal notifications

- A separate ongoing notification shows progress towards the daily, monthly and yearly reading goals. Daily and monthly targets use the selected Pages or Minutes metric; the yearly target counts books.
- Expand the notification to see all three targets, progress and remaining amounts. Tap it to open Reading goals.
- Reading goals and individual Book goals have independent notification switches in Settings. Keep either, both or neither enabled.
- Progress matches the app's goal circles, follows the device's local calendar and refreshes after saved reading and at the next day.
- The collapsed notification shows three horizontal progress rings; expanding it adds labels, targets and remaining amounts. Unfilled ring tracks have clearer contrast.

### Reading map

- Explore a network of books and their collections. A book can belong to several connected families, such as Philosophy, Religion and Classic literature.
- Pan, pinch to zoom, search for a book or family, and reset the view.
- Use distinct colours for Not started, Reading, On hold and Finished.
- Filter by All, Read, Finished, Reading, On hold or Not started, and by collection type.
- Tap a book for a cover preview, status and collection membership, then open its details.
- Tap a collection family to see its cover preview and member books.
- The graph area stays in place when selecting or dismissing nodes, with animated camera movement and selection feedback.

### Collections and bulk editing

- Classify collections as Theme, Type, Publisher or Other when creating them. Existing collections can be reclassified.
- Expand the library's horizontal collection chips into wrapping rows using the Collections icon. The chips keep the same appearance.
- The same icon expands collections in Add book and Edit book. Those screens allow scrolling while collections are expanded; collapsing returns to the fixed layout at the top.
- Find books missing a Theme, Type, Publisher or Other assignment through the Unassigned filter.
- Enter Edit, select several books, choose collections and press Done to apply assignments together. Book previews show collection membership while editing.
- Selecting a book shows its current collections. Remove assignments by deselecting them; mixed assignments are indicated when selected books differ.
- Book cards retain their normal size and selection border in edit mode. Collection chips remain scrollable in the collapsed view.
- Undo the last bulk assignment change.
- Hold a collection in expanded edit mode to rename it or change its type. Hold and drag one collection onto another to merge them, with a name/type confirmation before saving.

### Collection progress and insights

- Open Progress from the expanded collection controls.
- Compare finished-book counts, reading-status breakdowns and actual page progress across collections, with filters for Theme, Type, Publisher and Other.
- Tap a collection for estimated hours and reading sessions needed to finish, remaining pages and unfinished books.
- See total pages and reading time, pages per hour, minutes per page, average session duration and pages per session.
- Inspect average rating, average book length, the longest book, logged sessions, active reading days and the last reading date.
- See the books closest to completion and open their details directly.
- Estimates use the collection's timed sessions, falling back to your overall pace when needed. Missing page counts are identified rather than treated as zero-length books.
- View books opens that collection in the library. Back returns to Collection progress.

### Library and gallery

- Continue reading follows the most recently read book across the full library, independently of sorting and filters. An active or paused session takes priority.
- Swipe left to start a reading session; swipe right to pin or unpin.
- Pin and Unpin gestures use the app's theme-coloured vector icon in both list and gallery layouts.
- The Pages sort replaces Added and sorts by total book length. Tap the active sort again to clear it and return to date-added order. Title, Author, Rating and Progress remain available.
- Newly added books start as Not started.
- Gallery cards have cropped cover previews, a frosted information area and proportions suited to one through five columns.
- Hide titles in Library layout for a smaller information area with the author and progress. Collection editing uses that area for collection names.
- Gallery cover loads use display-sized images, a bounded memory cache and shared concurrent requests. Frosting is limited to the footer, and gallery rows retain stable identities while scrolling.
- Original cover files remain available for book details and backups; thumbnail optimisation does not replace them.

### Navigation, themes and screen fit

- Floating Stats and Library navigation, new navigation icons and animated page transitions.
- Swiping the navigation control previews the destination page before committing. Shorter drags and velocity-based switching make page changes more responsive.
- Library content continues behind the floating navigation; Stats reserves space for it.
- Stats, Book details and Edit book with collapsed collections fit the available screen area, including Android gesture and button navigation insets. Stats uses the same sizing across Week, Month, Year and All time.
- Text fitting and truncation support smaller screens and larger system text. Add book and Edit book allow scrolling when collections are expanded.
- Improved accent-colour contrast and touch feedback clipped to the shape of buttons and circular controls.
- Extra dark is an optional Settings toggle. Existing light, dark and adaptive theme behaviour remains available.
- Updated the Show graph and reading-notification icons, cover selection, library layout controls and disclosure headers to match the app's design.

### Statistics and Reading trends

**Improved in 1.1:**

- Hold and drag across the Stats graph or a book's graph to inspect successive points, instead of tapping each point separately.
- A selection guide and reserved value area keep graph inspection from moving the surrounding layout.
- Stats fits the viewport across all four periods while keeping Reading progress and Reading trends at the bottom.

**Reading trends is included in both the original 1.0 build 33 and 1.1:**

- Compare authors or collections over Today, Week, Month, Year or All time.
- Choose Pages, Time or Sessions through the active Reads control, or switch to Share to compare proportions.
- Compare selected authors/collections or view all, search the comparison list and adjust minimum activity/share filters.
- View all reading, positively rated reading or average ratings, with bucket and rating-adjustment controls.
- Historical sessions marked Exclude from statistics stay in lifetime totals but do not contribute to dated trends, heatmaps or reading-pace estimates.

### Book information and covers

- ISBN lookup combines matching information from Open Library, Google Books and the Swedish Libris catalogue instead of stopping at the first source.
- Fill missing title, author, page-count and cover fields from the matching sources. Libris adds Swedish edition information, parses page counts and normalises author names.
- Match ISBN-10 and ISBN-13 editions and preserve already filled-in book fields when fetching information again.
- Cover search starts with the ISBN when available, otherwise the title and author. Title search remains available, including fallback when an ISBN has no cover.
- Optional Google Images search opens inside the app, with image preview and cover selection.
- A redesigned Change cover dialog shows the current cover and Gallery, Camera and Search actions.
- Covers are cached locally for faster loading. Replaced covers are removed from the cache when no longer used.
- The ISBN scanner uses a square, centre-cropped camera preview without black side bars.

### Reading and data protection

- Expand Page calculator when finishing a session. Enter the printed start and end pages to calculate the pages read and update the book's position, useful for compendiums and different page numbering.
- Manual logging keeps note entry and action buttons accessible when the keyboard is open.
- Reading notifications open the matching session, with its book and session identity checked before resuming it.
- Portable JSON backups include available local cover images, collection types and book goals along with books, sessions, notes and preserved import data.
- Check backup validates a selected file and shows a report before restoration.
- Keep the latest five local recovery copies before imports, book/session edits and deletions. Export a recovery copy from Settings; a failed recovery copy cancels the protected change.
- Recovery copies and Preserved import data are collapsible, with export actions and clearer record counts.
- Backup and statistics responsibilities are separated from database record operations. Bulk collection changes use transactions, and cover caching runs in the background.

### Existing features retained from 1.0

Reading timers with pause/resume and countdowns; manual sessions with durations longer than 24 hours; automatically saved and editable notes; global note search; manual finish dates; ratings and reading statuses; daily/monthly pages or time goals and yearly book goals; heatmaps, Reading trends and progress summaries; automatic folder backups and preserved import archives. Reading data uses the device's local timezone.

### Install or update

Download **BookTracker-1.1.apk** below. Version 1.1, Android build 88. Requires Android 8.0 or later and an ARM64 device.

Install over the existing app to retain your library. The application ID and signing key are unchanged. Do not uninstall or clear app storage to update. Keep an exported backup outside the phone before changing devices.

Google Drive is optional; local use does not require a Google account. Restore into an empty library on a replacement phone before enabling uploads there. [Drive setup](https://github.com/Roinur/Book-tracker/blob/v1.1/docs/GOOGLE_DRIVE_BACKUP_SETUP.md) explains authorization and deployment configuration.

### Validation

All 85 JVM logic tests passed for this release. Seven device tests cover cloud/native checksums, portable covers, isolated database restoration, upload failures, concurrent writers and five-version retention using a fake transport. Contents recognition was checked with a photographed contents page. Real-account Drive connection, a verified upload and downloading that backup into the checked import preview have also been confirmed on a physical device. A complete real-cloud restore to a fresh installation and all Android background/network edge cases have not been exhaustively tested.

### Screenshots

<img src="https://raw.githubusercontent.com/Roinur/Book-tracker/v1.1/docs/screenshots/contents-scanning.png" width="320" alt="Review scanned compendium parts"> <img src="https://raw.githubusercontent.com/Roinur/Book-tracker/v1.1/docs/screenshots/drive-backup.png" width="320" alt="Google Drive backup settings">

[Source for build 88](https://github.com/Roinur/Book-tracker/tree/v1.1) · [Original 1.0 release](https://github.com/Roinur/Book-tracker/releases/tag/v1.0)
