package com.kiroku.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kiroku.app.util.UrlUtils

/**
 * One saved link. Column names mirror the Supabase `knowledge_items` table so a sync
 * can be added later; `site_name`, `notes`, `pinned`, `fetch_state` and `fetch_error`
 * are local-only extras. `category` holds a [CategoryEntity] id (or null = Unsorted).
 */
@Entity(
    tableName = "knowledge_items",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["status"]),
        Index(value = ["category"]),
        Index(value = ["created_at"]),
    ],
)
data class LinkEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val tags: List<String> = emptyList(),
    @ColumnInfo(name = "source_domain") val sourceDomain: String? = null,
    @ColumnInfo(name = "preview_image_url") val previewImageUrl: String? = null,
    @ColumnInfo(name = "site_name") val siteName: String? = null,
    val notes: String = "",
    val status: String = Status.SAVED,
    val pinned: Boolean = false,
    @ColumnInfo(name = "fetch_state") val fetchState: String = FetchState.PENDING,
    @ColumnInfo(name = "fetch_error") val fetchError: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    fun displayTitle(): String = title?.takeIf { it.isNotBlank() } ?: UrlUtils.displayUrl(url)
    fun displayDomain(): String = sourceDomain ?: siteName ?: UrlUtils.displayUrl(url)
}
