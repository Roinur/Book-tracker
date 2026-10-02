# Changelog

## 1.1

Version 1.1 (Android build 73) includes all changes since the original 1.0 release (build 33). Later APKs previously uploaded under 1.0 are part of this update.

- Gallery scrolling uses display-sized cover copies, a bounded memory cache, coalesced image loads and a smaller footer blur layer. Original covers remain available for details and backups.

- Added per-book goals: finish within days, weeks or months, or read a chosen number of pages each day.
- Swipe the Pages read card to view and configure a book goal. Active goals appear first, with today's remaining pages, estimated time and projected finish.
- Book goals have their own ongoing notifications and are included in native backups.
- Remaining-time estimates use the book's own pace first, then matching publisher collections, then overall reading pace. Sessions excluded from statistics do not affect estimates.
- Added Reading map with linked books and collections, reading-status colours, type filters and cover previews.
- Collections can be classified as Theme, Type, Publisher or Other. Expand the library chips to see all collections.
- Edit collections across multiple selected books, remove assignments, undo changes, rename collections and merge them with confirmation.
- Added Collection progress with status breakdowns, actual page progress, reading pace and estimated time to finish. Tap a collection for insights and use View books to open its library results.
- Updated gallery covers with a frosted information area, proportional card sizes and an option to hide titles.
- Refreshed cover selection and improved collection edit navigation, scrolling and selection feedback.
- Cached covers locally and refined backup and recovery handling.
- Added spacing between the Pages read and Book goal swipe pages, with rounded progress bars.

- Added an optional Extra dark setting while keeping the existing light, dark and adaptive themes.
- Sort books by total pages. Deselect the active sort to return to date-added order.
- New books start as Not started.
- Prefill cover search with the book's ISBN when available, otherwise its title and author.

- Added optional Google Images cover selection and ISBN cover searches.
- Combine matching ISBN metadata from Open Library, Google Books and Libris, including Swedish page counts.
- Preserve filled-in book fields when fetching metadata again.
- Include local covers in backups and keep recovery copies before important changes.
- Added a page calculator and improved note editing with the keyboard open.
- Refreshed navigation, interactive graphs and documentation screenshots.

- Continue reading follows the most recent session across the full library.
- Swipe left starts a reading session; swipe right pins or unpins.
- ISBN camera preview uses a square center crop without side bars.
- Replaced documentation screenshots with user-provided captures.

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
