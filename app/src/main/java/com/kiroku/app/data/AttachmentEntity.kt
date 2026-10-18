package com.kiroku.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object OwnerType {
    const val LINK = "link"
    const val NOTE = "note"
}

/**
 * A file attached to a link or a note. The file itself is copied into the app's private
 * storage (files/attachments/[fileName]) so it keeps working even if the original moves.
 */
@Entity(
    tableName = "attachments",
    indices = [Index(value = ["owner_type", "owner_id"])],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_type") val ownerType: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    /** Original file name, shown to the user. */
    val name: String,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    val size: Long,
    /** Name of the stored copy inside files/attachments. */
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
