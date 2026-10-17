package com.phone.cleanerapp.storage.mediafiles.data

import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract

class ContactEntry(val id: Long, val name: String, val numbers: List<String>)

enum class ContactGroupType(val label: String) { NAME("Same name"), NUMBER("Same number"), EMPTY("No number") }

class ContactGroup(val type: ContactGroupType, val key: String, val contacts: List<ContactEntry>) {
    val title: String get() = contacts.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: key
    val meta: String get() = when (type) {
        ContactGroupType.NAME -> trf("%1\$s contacts with this name", contacts.size)
        ContactGroupType.NUMBER -> trf("%1\$s contacts share %2\$s", contacts.size, key)
        ContactGroupType.EMPTY -> tr("No phone number saved")
    }
    val initials: String get() = title.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
}

object ContactsRepo {

    fun load(ctx: Context): List<ContactGroup> {
        val cr = ctx.contentResolver
        val numbers = HashMap<Long, MutableList<String>>()
        cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) numbers.getOrPut(c.getLong(0)) { mutableListOf() }.add(c.getString(1) ?: "")
        }
        val all = mutableListOf<ContactEntry>()
        cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                all.add(ContactEntry(id, c.getString(1) ?: "", numbers[id] ?: emptyList()))
            }
        }
        val groups = mutableListOf<ContactGroup>()
        all.filter { it.name.isNotBlank() }.groupBy { it.name.trim().lowercase() }
            .filter { it.value.size > 1 }
            .forEach { groups.add(ContactGroup(ContactGroupType.NAME, it.key, it.value)) }
        val byNumber = HashMap<String, MutableList<ContactEntry>>()
        all.forEach { e ->
            e.numbers.map(::normalize).filter { it.length >= 6 }.toSet().forEach { n -> byNumber.getOrPut(n) { mutableListOf() }.add(e) }
        }
        byNumber.filter { it.value.size > 1 }.forEach { (n, list) ->
            // Skip if the same people were already grouped by name.
            val ids = list.map { it.id }.toSet()
            if (groups.none { g -> g.type == ContactGroupType.NAME && g.contacts.map { it.id }.toSet() == ids })
                groups.add(ContactGroup(ContactGroupType.NUMBER, n, list))
        }
        all.filter { it.numbers.isEmpty() }.forEach { groups.add(ContactGroup(ContactGroupType.EMPTY, it.name, listOf(it))) }
        return groups
    }

    private fun normalize(raw: String): String = raw.filter { it.isDigit() }.takeLast(9)

    /** Links all raw contacts of the group so Android shows (and syncs) them as one contact. Nothing is deleted. */
    fun merge(ctx: Context, group: ContactGroup) {
        val cr = ctx.contentResolver
        val rawIds = group.contacts.flatMap { contact ->
            val ids = mutableListOf<Long>()
            cr.query(
                ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID}=?", arrayOf(contact.id.toString()), null
            )?.use { while (it.moveToNext()) ids.add(it.getLong(0)) }
            ids
        }
        for (i in 1 until rawIds.size) {
            val v = ContentValues().apply {
                put(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, rawIds[0])
                put(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, rawIds[i])
                put(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
            }
            cr.update(ContactsContract.AggregationExceptions.CONTENT_URI, v, null, null)
        }
    }

    fun delete(ctx: Context, contact: ContactEntry) {
        ctx.contentResolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id), null, null)
    }
}
