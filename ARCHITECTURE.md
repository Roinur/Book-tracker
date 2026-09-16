# Architecture

Kotlin sources live under `app/src/main/java/com/roinur/booktracker` with the application namespace `com.roinur.booktracker`.

- `app`: app composition, navigation, shared ViewModel and preference keys.
- `data/database`: SQLite schema and record operations, in the `com.roinur.booktracker.data.database` package.
- `data/backup`: backup snapshots and import orchestration, in its own Kotlin package.
- `data/stats`: read-only statistics queries and aggregation, in its own Kotlin package.
- `data/imports`: legacy import conversion, original archives and native backup validation.
- `data/lookup`, `data/media`: metadata lookup and local cover storage.
- `background`: the reading timer foreground service.
- `feature`: books, library, reading, notes, stats and settings screens.
- `core`: models, formatting, shared UI and trend algorithms.

The ViewModel calls BookBackupService and BookStatsRepository directly; backup and statistics operations no longer belong to BookTrackerDatabase. All three share one database helper. Other folders still use the root Kotlin package, and the shared ViewModel remains. This is an incremental separation, not yet a full feature-ViewModel architecture.

An active session has one app-level owner and must never depend on filtered library results. Storage and backup identifiers are compatibility contracts, not branding strings. Preserve them during refactors.
