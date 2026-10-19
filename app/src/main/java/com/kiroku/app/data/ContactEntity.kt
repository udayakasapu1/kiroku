package com.kiroku.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A person at the company a link is about. A link can have several. */
@Entity(tableName = "contacts", indices = [Index(value = ["link_id"])])
data class ContactEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "link_id") val linkId: String,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    /** Facility address. */
    val address: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    fun isBlank(): Boolean = name.isBlank() && email.isBlank() && phone.isBlank() && address.isBlank()
}
