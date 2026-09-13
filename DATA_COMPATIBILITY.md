# Data compatibility

The application ID, signing identity, database name, preference keys and native backup format are retained in version 1.0. An alias preserves the previous launcher component name for existing shortcuts.

The import code uses generic Legacy/Archive names. Existing serialized `bookly_sources` and `bookly_links` keys/table names intentionally remain: changing them for cosmetic reasons would invalidate older backups or orphan preserved data.

Native backups include original imported bytes and mappings alongside editable Book Tracker records. Unknown imported fields stay in the original archive. V3 backups include integrity validation and are restored only into an empty library; restoration must never silently replace current data.

Finished status is independent of page progress. Completing a 200-page book after reading 180 pages must retain 180 pages. Completion dates and active-session state are independent records.

Instrumentation tests create and delete only randomly named test databases. Never point tests at the daily library database, uninstall the release app or clear its storage.
