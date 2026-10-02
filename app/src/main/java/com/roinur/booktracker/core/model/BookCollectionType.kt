package com.roinur.booktracker

internal enum class BookCollectionType(val label: String) {
    PUBLISHER("Publisher"),
    TYPE("Type"),
    THEME("Theme"),
    OTHER("Other");

    companion object {
        fun fromStorage(raw: String?): BookCollectionType = entries.firstOrNull { it.name == raw } ?: OTHER
    }
}
