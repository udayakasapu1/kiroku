package com.kiroku.app.data

object Status {
    const val SAVED = "saved"
    const val ARCHIVED = "archived"
}

object FetchState {
    const val PENDING = "PENDING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

/** Special folder keys used in navigation, besides real category ids. */
object FolderKeys {
    const val UNSORTED = "unsorted"
    const val ARCHIVED = "archived"

    /** Folder key -> value of a link's category column (null = Unsorted). */
    fun categoryOf(folderKey: String): String? =
        if (folderKey == UNSORTED || folderKey == ARCHIVED) null else folderKey
}

/** Result row of the per-folder count query. */
data class CategoryCount(val category: String?, val count: Int)

/** A folder idea offered on first launch and in "New folder". */
data class FolderSuggestion(val name: String, val icon: String)

object FolderSuggestions {
    /** The folders Kiroku shipped with before folders became editable (kept ids for old data). */
    val legacy = listOf(
        Triple("oem", "OEM", "car"),
        Triple("tier1", "Tier 1", "factory"),
        Triple("aftermarket", "Aftermarket", "wrench"),
        Triple("software", "Software", "code"),
        Triple("tools", "Tools", "toolbox"),
        Triple("org", "Org", "org"),
    )

    val starter = listOf(
        FolderSuggestion("Work", "work"),
        FolderSuggestion("Research", "science"),
        FolderSuggestion("Reading", "book"),
        FolderSuggestion("Ideas", "lightbulb"),
        FolderSuggestion("Tools", "toolbox"),
        FolderSuggestion("Software", "code"),
        FolderSuggestion("Companies", "org"),
        FolderSuggestion("Learning", "school"),
        FolderSuggestion("Travel", "globe"),
        FolderSuggestion("Favourites", "star"),
        FolderSuggestion("OEM", "car"),
        FolderSuggestion("Tier 1", "factory"),
        FolderSuggestion("Aftermarket", "wrench"),
        FolderSuggestion("EV & Energy", "bolt"),
    )
}
