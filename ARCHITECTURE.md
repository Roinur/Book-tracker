# Architecture

Kotlin sources live under `app/src/main/java/com/roinur/booktracker` and use the `com.roinur.booktracker` namespace.

- `app`: app composition, navigation, shared ViewModel and preference keys.
- `data/database`: SQLite storage.
- `data/imports`: legacy import conversion, original archives and native backup validation.
- `data/lookup`, `data/media`: metadata lookup and local cover storage.
- `background`: the reading timer foreground service.
- `feature`: books, library, reading, notes, stats and settings screens.
- `core`: models, formatting, shared UI and trend algorithms.

Folders currently organize responsibilities within one Kotlin package. The shared ViewModel and database helper remain; this is not yet a full repository/feature-ViewModel architecture.

An active session has one app-level owner and must never depend on filtered library results. Storage and backup identifiers are compatibility contracts, not branding strings. Preserve them during refactors.
