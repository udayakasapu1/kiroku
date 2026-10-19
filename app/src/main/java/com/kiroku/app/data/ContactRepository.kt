package com.kiroku.app.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ContactRepository(private val dao: ContactDao) {
    fun observeFor(linkId: String): Flow<List<ContactEntity>> = dao.observeFor(linkId)
    suspend fun getAll(): List<ContactEntity> = dao.getAll()

    /** Adds a new contact (empty id) or saves changes to an existing one. Blank contacts are ignored. */
    suspend fun save(linkId: String, contact: ContactEntity) {
        val clean = contact.copy(
            linkId = linkId,
            name = contact.name.trim(),
            email = contact.email.trim(),
            phone = contact.phone.trim(),
            address = contact.address.trim(),
        )
        if (clean.isBlank()) return
        dao.upsert(if (clean.id.isEmpty()) clean.copy(id = UUID.randomUUID().toString()) else clean)
    }

    suspend fun delete(id: String) = dao.delete(id)
    suspend fun deleteForLink(linkId: String) = dao.deleteForLink(linkId)
    suspend fun insertImported(contact: ContactEntity): Boolean = dao.insertIgnore(contact) != -1L

    companion object {
        /** A not-yet-saved contact (id is assigned on save). */
        fun draft(): ContactEntity = ContactEntity(id = "", linkId = "", createdAt = System.currentTimeMillis())
    }
}
