package com.kiroku.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A free-text note, Keep-style. Lives in the same folders as links. */
@Entity(
    tableName = "notes",
    indices = [Index(value = ["status"]), Index(value = ["category"]), Index(value = ["updated_at"])],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    val body: String = "",
    val category: String? = null,
    val pinned: Boolean = false,
    val status: String = Status.SAVED,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    fun isBlank(): Boolean = title.isBlank() && body.isBlank()
}
