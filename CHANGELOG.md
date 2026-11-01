# Changelog

## 1.2 — 2026-11-01

Book Tracker 1.2 adds reading plans, yearly text/image reading-list exports, optional Google Sheets Book List synchronization, one-way Obsidian Markdown export, reading recap cards and a reorganized Settings experience. It also improves bookmarks, finishing a session, book goals and library maintenance.

Changes below are compared with **1.1 / build 88**, tag `v1.1` (`891a9e1`). This release is **1.2 / Android build 121**.

## Reading plans and Plan mode

- Create named reading plans by holding the Library status filter. Arrange covers in six renameable S–E tiers and reorder books within each tier.
- Filter/search the unassigned book pool, drag between tiers and scroll the editor while dragging. A book appears only once in a plan.
- Apply a plan's tier order to the Library's Plan view. Search the rest of the library without discarding that plan or its order.
- See remaining pages and estimated reading time for each tier and the whole plan, using the book/publisher/overall reading-pace fallbacks. Missing page counts and unavailable estimates are distinguished from zero remaining work.
- Save multiple plans locally and preserve them through native/Drive backups and restored book-ID remapping. Plans do not change ratings, reading status or reading statistics.

## Yearly reading lists and image exports

- Export from Reading progress → Sessions as text or a cover poster. Group reading by local calendar year, with finished-book numbering and unfinished progress percentages.
- Include finished books in their completion year. Include unfinished books only when at least 10% was read in that year; retain genuinely undated reading under Unknown year.
- Hold Copy reading list to choose collections. Remember collection exclusions and options for finished books only, author names and compendium parts.
- Export the entire library, including unread books, with undated unread titles listed separately. Exports do not modify library records or statistics.
- Generate a single long PNG with streamed scanlines and a tiled preview, supporting large libraries without one enormous in-memory bitmap. Preview, copy, save or share the image.
- Use consistent status strips: green for finished, yellow from 1% read and red for unread/below 1%. Centre book numbers above the strips and optionally show completion dates immediately after the title/author.
- Show actual pages read and reading hours/minutes beside each year heading. Preserve earlier reading years even when a cover appears only once, respect export filters and avoid counting compendium parts twice.

## Optional Book List in Google Sheets

- Create a separate personal spreadsheet from the library and synchronize supported overview fields both ways using stable book identities, account binding and field-level conflict review.
- Include author, title, page count, status, priority/weight, actual pages/hours read, collections, completion date, ISBN, rating and printed bookmark. Notes, session history and cover images are not uploaded to this overview sheet.
- Use Weight / 5 for expectations while unfinished; use the actual whole-star rating after finishing. Preserve and rescale older 0–10 values without treating an expectation as a final rating.
- Include Ranked Books, author ordering and a Smaller/Larger page-length filter. Use the reference palette, standard gridlines, compact rows, relevant dropdowns, status/rating colours and highlighted completed authors.
- Preserve local reading history and keep undated sheet adjustments out of calendar statistics. Existing app-created sheets are upgraded in place; unrelated pre-existing spreadsheets are never selected or edited.
- Pause or unlink synchronization while retaining both the spreadsheet and the local library.

## Optional Obsidian Markdown export

- Choose the existing `Böcker/BookTracker` folder in your existing vault with Google's folder Picker, then enable export separately from app backups. Public builds do not embed a personal account or Drive destination.
- Automatically queue saved book/note changes and export ordinary UTF-8 Markdown, one stable `book-<persistent-id>.md` per book. IDs survive title changes, restarts and native backup/restore.
- Export book information, notes, quotes and reading annotations inside the exact managed `booktracker:begin` / `booktracker:end` comment markers. New files include `booktracker_id`, export version and space for personal notes.
- Read before updating and preserve personal YAML, comments and `[[links]]` outside the managed block. Link books to shared author targets, including separately listed coauthors.
- Use conditional writes and retry-safe identities to avoid duplicate exports and stale overwrites. Malformed markers, conflicting identity and missing files produce reviewable errors instead of unsafe replacement.
- Keep local saving independent of offline/Drive failures; show pending work, confirmed uploads and retry controls. Disabling export or deleting an app book retains existing Markdown files.
- This is one-way export: Obsidian edits are not imported. Drive keeps the cloud files and syncs them to the computer; this is separate from JSON app backups.

## Reading, bookmarks and completion ratings

- Keep actual pages read separate from an optional printed-page bookmark. Resume from the bookmark and show its position in progress markers without inflating pages/time statistics.
- Enter the reached page when finishing: reading from 69 to 144 logs 75 pages. The calculator can fill actual pages and a printed endpoint; resetting it restores ordinary input.
- Refresh Finish reading with a compact header, calculator disclosure and fixed bookmark/action footer. Keep field/button widths stable when the keyboard opens, scroll only the available form area and confirm before discarding a session.
- **New completion rating prompt:** when an existing book changes to Finished manually or by logging its final page, ask for a 1–5-star rating only if it has no final rating. Save it or choose Not now. Existing ratings are retained, reopening an old finished book does not trigger a prompt, and repeated refreshes do not duplicate prompts.
- Preserve bookmarks in session editing and native/Drive backups. Completion status remains independent of actual pages read.

## Library check and minute goals

- Use Library check from the expanded collection controls to review missing metadata/classifications and possible status inconsistencies one book at a time.
- Apply selected corrections or skip/close, preserving reading history and manual statuses unless explicitly changed. Create a recovery snapshot before the first saved correction in a run.
- Set individual book goals in **minutes per day** as well as pages/deadlines. Show logged/remaining minutes in the goal card and notification and retain these targets in backups.

## Statistics and recap cards

- Hold Week, Month, Year or All time to open a reading recap in the current app theme. Holding the selected period preserves the historical interval you are browsing.
- Include pages, reading time, pace, streaks, active days, finished counts, daily records and featured covers/ratings; copy, save or share the PNG.
- Month now follows calendar months, including 28/29/30/31-day boundaries. Earlier months include their final day; the current month counts through today, with future chart days empty. Charts, heatmap and recap dates agree.
- Keep authoritative lifetime totals for All time, including undated imports. Dated recaps exclude undated/statistically excluded sessions and do not invent completion dates.

## Settings

- Replace the long Settings page with Appearance, Notifications, Data & backups, Google Drive backup, Book List, Obsidian export and About subpages.
- Add purposeful palette/bell/backup/cloud/linked-note/info icons, compact panels and the same square 8 dp action buttons as Book List. Back returns to the overview.
- About shows Book Tracker's identity, version and build. Explain persistent Drive storage and local Obsidian copies on the export page.

## Install/update and data

Download **BookTracker-1.2.apk** from this release. Requires Android 8.0+ and an ARM64 device. Install over the existing app with the same signing identity; **do not uninstall or clear storage**. Keep a backup outside the phone when moving devices.

The app ID remains `com.roinur.booktracker`. Local notes, sessions, plans, goals, imports and settings are retained. Google features remain optional and separately controlled. Google authorization for a self-built/signed APK needs the matching package/certificate registration; see the setup guides.

The pause-notification update cadence was investigated but not changed in this release. No claim is made that every cloud/offline/background or device condition has been exhaustively tested.

## Validation

- Built the release APK, release instrumentation APK and debug APK from a clean public checkout with JDK 17. Gradle uses an official SHA-256-pinned distribution. Private signing inputs stayed outside the public source tree; the public APK contains no personal Google account or Drive-folder hint.
- **194 JVM tests passed, 0 failures/errors.** Release lint-vital passed.
- **33 device tests passed** on a Nothing A024: completion-rating transitions/UI, reading-plan restore/remapping, bookmarks, library corrections, page/minute goals, Book List conflicts/idempotency, Markdown persistence, verified Drive backups, yearly poster rendering and recap gestures/dates. Databases, preferences and visual fixtures were isolated; no demonstration books were inserted into the daily library.
- App and test APK updates returned Success using `adb install -r`; About showed version 1.2 / build 121. The signing certificate matches the existing release identity. No uninstall or data reset was performed.
- The completion-rating UI test waits for the rendered Save state after selecting a star; its initial immediate accessibility read was corrected before the final passing run.
- APK third-party notices were inspected against the source notices. Copy/save/share controls and image-provider reads were tested, but no external sharing recipient or SAF destination was exercised. These tests use fake cloud transports; they do not claim a new full real-cloud/desktop Obsidian verification for this release.


## 1.1

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

## 1.0

Original first release, Android build 33.

- Library list and gallery, search, collections, pinned books, ratings and manual reading status.
- Reading timer, pause and resume, countdown, manual sessions and durations longer than 24 hours.
- Automatically saved and editable notes, global note search and copying book notes in date order.
- Daily and monthly pages/time goals, yearly book goals, heatmaps and reading trends.
- Historical sessions can remain in lifetime totals while being excluded from dated statistics.
- ISBN scanning, metadata and cover searches from Open Library and Google Books, and local cover selection.
- Verified JSON backups, automatic backups and preserved original import data.
- Light, dark and adaptive themes with accent colours.

- Use the device local timezone consistently for daily statistics, heatmaps, streaks and period boundaries.
- Aggregate graph minutes after summing session durations, matching daily totals.
- Stored timestamps and existing reading data remain unchanged.

- Public project identity and source namespace changed to Book Tracker.
- Tap the author in Book details to search all library books.
- Preserved existing storage and backup formats and the release application ID.
- Added build, signing, privacy and compatibility documentation.
