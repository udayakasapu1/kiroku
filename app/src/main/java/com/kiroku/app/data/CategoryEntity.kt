package com.kiroku.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user-defined link folder. Links point at it through their `category` column (= this id). */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Key into the app's folder icon set (see ui/components/FolderStyle.kt). */
    val icon: String,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
