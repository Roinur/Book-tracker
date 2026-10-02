package com.roinur.booktracker

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun BookCollectionBookCard(book: BookRow, selected: Boolean, gallery: Boolean,
                                    modifier: Modifier = Modifier, preview: String = book.collections, hideTitle: Boolean = false,
                                    onSelect: () -> Unit) {
    val collections = splitBookCollections(preview).joinToString(" · ")
    if (gallery) BookGalleryTile(book, selected, modifier, onSelect,
        onPin = {}, onReadGesture = {}, onRatingPreview = {}, onRatingCommit = {}, onRatingCancel = {},
        collectionsPreview = collections, hideTitle = hideTitle)
    else Row(modifier) {
        BookLibraryRow(book, selected, false, 0L, 0L, 0L, 0L, onSelect,
            onTogglePinned = {}, onToggleFinished = {}, onRatingPreview = {}, onRatingCommit = {}, onRatingCancel = {},
            collectionsPreview = collections)
    }
}
