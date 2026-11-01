# Book Tracker 1.2

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
