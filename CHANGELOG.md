# Changelog

## 1.1

Book Tracker 1.1 adds Reading map, collection progress, bulk collection editing and per-book reading goals. It also improves gallery performance, navigation, metadata lookup, graphs and backups.

This release covers the changes from the original 1.0 release (build 33) to 1.1 (build 73).

### Per-book reading goals

- Set a deadline in days, weeks or months, or choose how many pages to read each day.
- Swipe between Pages read and Book goal in Book details. An active goal opens on the goal page automatically.
- See today's target, pages still to read today, estimated time for today's reading, total pages and time remaining, days remaining and projected finish date.
- Deadline goals recalculate the daily quota as the deadline approaches. Logged pages reduce today's remaining quota.
- Each active book goal has its own ongoing notification showing today's remaining pages and estimated time. Tap it to open that book's goal. Reading-session notifications stay separate.
- Remaining-time estimates use the book's own timed reading first, then books sharing a Publisher collection, then your overall reading pace. The same fallback applies in Book details; sessions excluded from statistics do not affect the estimate.
- Goals are stored locally and included in backups. The swipe pages have more space between them and rounded progress bars.

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
- Stats, Book details and Edit book fit the available screen area, including Android gesture and button navigation insets. Stats uses the same sizing across Week, Month, Year and All time.
- Text fitting and truncation support smaller screens and larger system text without adding scrolling to those pages.
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
- Portable JSON backups include available local cover images, collection types and book goals along with books, sessions, notes and settings.
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
