# Book List

Book List creates a new personal Google spreadsheet from the app's library.
It has one **Book List** tab. It never selects, links or edits a pre-existing spreadsheet.

## Setup

1. In the Google Cloud project registered for Book Tracker, enable **Google Sheets API** as well as Google Drive API. The existing Android OAuth client and `drive.file` scope are reused.
2. Open **Settings → Google Drive backup** and connect Google Drive. Automatic backup can remain off.
3. Return to the Settings overview, open **Book List**, choose **Create new Book List**, then **Create and link**.
4. Use **Open list** to edit the new spreadsheet. **Sync now** imports and exports changes immediately.

The list has author, title, total pages, reading status, weight, actual pages read, hours read, collections, finish date, ISBN, rating and optional printed bookmark. Weight is an expected rating on a 0 to 5 scale while a book is unfinished. On completion, the actual whole-star rating replaces that expectation. Half-star expectations are supported; final ratings stay at whole stars. An unrated finished book retains its previous expectation until a final rating is given. Editing the finished book's Weight in the sheet updates the app's final rating. Old 0 to 10 values are divided by two without changing stored expectations. Set weight in the book editor using the list-fields button beside Parts, then save the book. All books in the personal library are treated as owned for presentation. The old ownership column is hidden for compatibility with existing sync records.

The ranked view and adjustable page-length filter sit to the right of the library table. Filter the main table by reading status using its header controls. Finish dates use YYYY-MM-DD. The hidden Book Tracker ID column must remain attached to its book when sorting. Sort the whole table, not one column alone.

Book titles are mint, authors blue-gray and page counts blue. Completed status is dark green with white text, Unread is brown, Reading is yellow, and On hold is lavender. Weight colours preserve the reference scale, rescaled to 0/red, 3.5/yellow and 5/green. Gridlines remain enabled. The reference's native grid is used without explicit cell borders. Main headers use regular text and normal row height. Dropdowns and the main filter end at the last book row and extend when books are added.

The table sorts by author, then title after synchronization. Ranked Books contains author, title, pages and weight, sorted by weight descending. Unset weights sort last and display as empty. Equal weights sort by author and title. For this personal list, one user's expected/final rating replaces the original two-person average. The unread-book view keeps that ranking and offers Smaller/Larger plus a page threshold. Completed authors in Ranked Books are turquoise with bold black text, adapting the reference's both-read styling to this personal list. Ranked Books uses the reference's bold 13-point spanning heading. Main-table headers remain regular 10-point Arial; Pages so far counters use bold 11-point text. Hidden supporting columns keep status attached to each sorted result. Formula separators follow the spreadsheet's region, including Swedish semicolons.

## Synchronization

App edits queue synchronization. Opening the app also queues a check. A background job checks about every 15 minutes, subject to Android scheduling and the Drive Wi-Fi setting. This is not continuous live collaboration while the app is closed.

Changes to different fields merge independently. If both sides change the same field differently, Book List pauses the merge and Settings offers a choice of the app or spreadsheet value. A choice applies only to the exact values reviewed.

Add a book by entering a title on a new row with an empty ID. Book Tracker assigns its identity on sync. Missing rows are restored from the app rather than deleting books, sessions or notes. To remove a book, use the app's confirmed Remove book action. A simultaneous edit in the list requires conflict review.

Changes to Pages Read and Hours Read update lifetime totals without inventing dated sessions, so they do not add heatmap activity. Existing sessions and notes are kept. Completed status does not fill unread pages. Finish the active reading session before importing progress or status for that book. A verified local recovery copy is saved before importing library changes.

Pausing two-way sync stops scheduled checks; manual Sync now remains available. Unlink keeps the spreadsheet and library. Connection details and the merge baseline stay on this installation and are excluded from portable backups. Book identities, ownership, weight and undated total adjustments are included in backups.

References: [Sheets values](https://developers.google.com/workspace/sheets/api/guides/values) and [Google Sheets authorization](https://developers.google.com/workspace/sheets/api/scopes).

## Pages so far

The Pages so far section lists completed books by finish date and colours each title by its effective rating. A final 5 is gold with bold blue text. Its total sums actual pages read in the completed books, never their full page counts merely because they are finished. The separate Library summary includes all actual pages and hours, including books still in progress.
