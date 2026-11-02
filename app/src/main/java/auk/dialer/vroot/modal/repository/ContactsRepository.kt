package auk.dialer.vroot.modal.repository

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.util.Log
import auk.dialer.vroot.BuildConfig
import auk.dialer.vroot.R
import auk.dialer.vroot.modal.data.AddressEntry
import auk.dialer.vroot.modal.data.Contact
import auk.dialer.vroot.modal.data.ContactEvent
import auk.dialer.vroot.modal.data.ContactSaveResult
import auk.dialer.vroot.modal.data.EmailEntry
import auk.dialer.vroot.modal.data.PhoneNumberEntry
import auk.dialer.vroot.modal.`interface`.IContactsRepository
import auk.dialer.vroot.modal.db.PrivateContactDao
import auk.dialer.vroot.modal.db.PrivateContactEntity
import auk.dialer.vroot.controller.util.deduplicateNumbers
import auk.dialer.vroot.controller.util.areNumbersEqual
import auk.dialer.vroot.controller.util.CallBackgroundStore
import auk.dialer.vroot.controller.util.decodeSampledBitmap
import java.io.File
import java.util.UUID

class ContactsRepository(
    private val context: Context,
    private val privateContactDao: PrivateContactDao
) : IContactsRepository {

    private val contentResolver: ContentResolver = context.contentResolver
    private val preferenceManager = auk.dialer.vroot.controller.util.PreferenceManager(context)
    private val unknownLabel: String get() = context.getString(R.string.label_unknown)

    private companion object {
        const val DEBUG_TAG = "AukContactDebug"
        const val CONTACT_PHOTO_SIZE = 720
        const val MAX_BATCH_OPERATIONS = 400
    }

    private fun formatName(rawName: String): String {
        return rawName
    }

    override fun getContacts(includePrivate: Boolean): List<Contact> {
        val contactsMap = LinkedHashMap<String, Contact>()
        
        if (includePrivate) {
            privateContactDao.getAll().forEach {
                val contact = it.toContact()
                contactsMap[contact.id] = contact
            }
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )

        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val starredIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    val number = cursor.getString(numberIdx) ?: continue

                    val existingContact = contactsMap[id]
                    if (existingContact != null) {
                        val numbers = existingContact.phoneNumbers.toMutableList()
                        if (numbers.none { areNumbersEqual(it, number) }) {
                            numbers.add(number)
                            contactsMap[id] = existingContact.copy(phoneNumbers = numbers)
                        }
                    } else {
                        contactsMap[id] = Contact(
                            id = id,
                            name = formatName(cursor.getString(nameIdx) ?: unknownLabel),
                            photoUri = cursor.getString(photoIdx),
                            isFavorite = cursor.getInt(starredIdx) == 1,
                            phoneNumbers = mutableListOf(number)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val list = contactsMap.values.toList()
        
        // Nicknames, and the family names sorting by last name files contacts under.
        val nicknameMap = mutableMapOf<String, String>()
        val familyNameMap = mutableMapOf<String, String>()
        val nicknameType = ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE
        val nameType = ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
        try {
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA3),
                "${ContactsContract.Data.MIMETYPE} IN (?, ?)",
                arrayOf(nicknameType, nameType),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    when (cursor.getString(1)) {
                        nicknameType -> cursor.getString(2)?.let { nicknameMap[id] = it }
                        nameType -> cursor.getString(3)?.takeIf { it.isNotBlank() }?.let { familyNameMap.putIfAbsent(id, it) }
                    }
                }
            }
        } catch (e: Exception) {}

        val accounts = contactAccounts()
        val finalList = list.map { contact ->
            if (contact.isPrivate) return@map contact
            val account = accounts[contact.id]
            contact.copy(
                nickname = nicknameMap[contact.id] ?: contact.nickname,
                familyName = familyNameMap[contact.id],
                accountName = account?.first,
                accountType = account?.second
            )
        }

        return finalList.sortedBy { it.name.lowercase() }
    }

    private fun resolveLookupKey(lookupKey: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                Uri.encode(lookupKey)
            )
            var resolved: String? = null
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) resolved = cursor.getString(0)
            }
            resolved
        } catch (e: Exception) {
            null
        }
    }

    private fun getNumbersForContactId(contactId: String): List<String> {
        val numbers = mutableListOf<String>()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { numbers.add(it) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return numbers
    }

    override fun getDeviceContactsWithDetails(): List<Contact> {
        val ids = mutableListOf<String>()
        try {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) cursor.getString(0)?.let(ids::add)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ids.mapNotNull { getContactById(it) }
    }

    override fun getContactById(contactId: String): Contact? {
        if (contactId.startsWith("p")) {
            val id = contactId.substring(1).toLongOrNull() ?: return null
            return privateContactDao.getById(id)?.toContact()
        }
        val resolvedId = if (contactId.toLongOrNull() != null) {
            contactId
        } else {
            resolveLookupKey(contactId) ?: return null
        }
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_URI,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.STARRED,
            ContactsContract.Data.CUSTOM_RINGTONE,
            ContactsContract.Data.RAW_CONTACT_ID,
            ContactsContract.Contacts.NAME_RAW_CONTACT_ID
        )

        var contact: Contact? = null

        try {
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                "${ContactsContract.Data.CONTACT_ID} = ?",
                arrayOf(resolvedId),
                "${ContactsContract.Data._ID} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME_PRIMARY)
                val photoIdx = cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val data2Idx = cursor.getColumnIndex(ContactsContract.Data.DATA2)
                val data3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                val starredIdx = cursor.getColumnIndex(ContactsContract.Data.STARRED)
                val ringtoneIdx = cursor.getColumnIndex(ContactsContract.Data.CUSTOM_RINGTONE)
                val rawContactIdx = cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID)
                val nameRawContactIdx = cursor.getColumnIndex(ContactsContract.Contacts.NAME_RAW_CONTACT_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    val mimeType = cursor.getString(mimeIdx)
                    val data1 = cursor.getString(data1Idx) ?: continue
                    val isStarred = cursor.getInt(starredIdx) == 1
                    val ringtone = cursor.getString(ringtoneIdx)

                    val currentContact = contact ?: run {
                        val (accName, accType) = getAccountInfo(resolvedId)
                        Contact(
                            id = id,
                            name = formatName(cursor.getString(nameIdx) ?: unknownLabel),
                            photoUri = cursor.getString(photoIdx),
                            isFavorite = isStarred,
                            customRingtone = ringtone,
                            accountName = accName,
                            accountType = accType
                        )
                    }

                    contact = when (mimeType) {
                        // The parts of the name shown, not those of another linked entry (WhatsApp's own).
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            val nameRawContactId = cursor.getString(nameRawContactIdx)
                            if (nameRawContactId == null || nameRawContactId == cursor.getString(rawContactIdx)) {
                                currentContact.copy(
                                    givenName = cursor.getString(data2Idx),
                                    familyName = cursor.getString(data3Idx)
                                )
                            } else {
                                currentContact
                            }
                        }
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            val entry = PhoneNumberEntry(
                                number = data1,
                                type = cursor.getInt(data2Idx),
                                label = cursor.getString(data3Idx)
                            )
                            val alreadyPresent = currentContact.phones.any { areNumbersEqual(it.number, data1) }
                            currentContact.copy(
                                phoneNumbers = deduplicateNumbers(currentContact.phoneNumbers + data1),
                                phones = if (alreadyPresent) currentContact.phones else currentContact.phones + entry
                            )
                        }
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            val entry = EmailEntry(
                                address = data1,
                                type = cursor.getInt(data2Idx),
                                label = cursor.getString(data3Idx)
                            )
                            val alreadyPresent = currentContact.emailEntries.any { it.address == data1 }
                            currentContact.copy(
                                emails = (currentContact.emails + data1).distinct(),
                                emailEntries = if (alreadyPresent) currentContact.emailEntries else currentContact.emailEntries + entry
                            )
                        }
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                            val entry = AddressEntry(
                                address = data1,
                                type = cursor.getInt(data2Idx),
                                label = cursor.getString(data3Idx)
                            )
                            val alreadyPresent = currentContact.addressEntries.any { it.address == data1 }
                            currentContact.copy(
                                addresses = (currentContact.addresses + data1).distinct(),
                                addressEntries = if (alreadyPresent) currentContact.addressEntries else currentContact.addressEntries + entry
                            )
                        }
                        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> {
                            currentContact.copy(nickname = data1)
                        }
                        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val event = ContactEvent(type, label, data1)
                            currentContact.copy(events = (currentContact.events + event).distinct())
                        }
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                            currentContact.copy(notes = data1)
                        }
                        else -> currentContact
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return contact
    }

    override fun toggleFavorite(contactId: String, isFavorite: Boolean) {
        if (contactId.startsWith("p")) {
            val id = contactId.substring(1).toLongOrNull() ?: return
            privateContactDao.getById(id)?.let {
                privateContactDao.update(it.copy(isFavorite = isFavorite))
            }
            return
        }
        val contentValue = ContentValues().apply {
            put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
        }
        val updateUri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
            .appendPath(contactId)
            .build()
        // The provider can refuse it (contact gone meanwhile, read-only account): the star stays as it was.
        try {
            contentResolver.update(updateUri, contentValue, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getPhotoBytes(uriString: String): ByteArray? {
        val bitmap = decodeSampledBitmap(contentResolver, Uri.parse(uriString), CONTACT_PHOTO_SIZE) ?: return null
        return try {
            java.io.ByteArrayOutputStream().use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** The contact's entries, oldest first: the first is the one whose account [getAccountInfo] gives. */
    private fun getRawContactIds(contactId: String): List<String> {
        val ids = mutableListOf<String>()
        try {
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
                arrayOf(contactId),
                "${ContactsContract.RawContacts._ID} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
                while (cursor.moveToNext()) {
                    ids.add(cursor.getString(idIdx))
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return ids
    }

    private fun getRawContactId(contactId: String): String? {
        return getRawContactIds(contactId).firstOrNull()
    }

    /** The entry the contact's displayed name comes from, among those linked into it. */
    private fun getNameRawContactId(contactId: String): String? {
        return try {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.NAME_RAW_CONTACT_ID),
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun deleteDataRowsOperation(rawContactIds: List<String>, mimeType: String): ContentProviderOperation {
        val placeholders = rawContactIds.joinToString(",") { "?" }
        return ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data.RAW_CONTACT_ID} IN ($placeholders) AND ${ContactsContract.Data.MIMETYPE}=?",
                (rawContactIds + mimeType).toTypedArray()
            )
            .build()
    }

    /** A phone number, e-mail or address as edited: phones, e-mails and addresses all keep it in DATA1 to DATA3. */
    private data class TypedValue(val value: String, val type: Int, val label: String?)

    private class DataRow(val id: String, val value: String, val type: Int, val label: String?)

    /**
     * Brings the contact's [mimeType] rows in line with [edited]. A row whose value is still there
     * stays, given the edited type and label, and keeps the rest (an address's street and city, say);
     * a row whose value is gone is deleted; a value no row holds is added to [newRowsRawContactId].
     */
    private fun syncDataRows(
        ops: MutableList<ContentProviderOperation>,
        rawContactIds: List<String>,
        newRowsRawContactId: String,
        mimeType: String,
        edited: List<TypedValue>,
        sameValue: (String, String) -> Boolean
    ) {
        val rows = mutableListOf<DataRow>()
        val placeholders = rawContactIds.joinToString(",") { "?" }
        try {
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3),
                "${ContactsContract.Data.RAW_CONTACT_ID} IN ($placeholders) AND ${ContactsContract.Data.MIMETYPE}=?",
                (rawContactIds + mimeType).toTypedArray(),
                "${ContactsContract.Data._ID} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val value = cursor.getString(1) ?: continue
                    rows.add(DataRow(cursor.getString(0), value, cursor.getInt(2), cursor.getString(3)))
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            return
        }

        val keptRowIds = mutableSetOf<String>()
        edited.forEach { entry ->
            val matching = rows.filter { sameValue(it.value, entry.value) }
            if (matching.isEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, newRowsRawContactId)
                        .withValue(ContactsContract.Data.MIMETYPE, mimeType)
                        .withValue(ContactsContract.Data.DATA1, entry.value)
                        .withValue(ContactsContract.Data.DATA2, entry.type)
                        .withValue(ContactsContract.Data.DATA3, entry.label)
                        .build()
                )
                return@forEach
            }
            keptRowIds += matching.map { it.id }
            // Retyped differently (a number's spacing): the row shown, the oldest, takes the new spelling.
            val respelled = matching.none { it.value == entry.value }
            matching.forEachIndexed { index, row ->
                val value = if (respelled && index == 0) entry.value else row.value
                if (value != row.value || row.type != entry.type || row.label != entry.label) {
                    ops.add(
                        ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                            .withSelection("${ContactsContract.Data._ID}=?", arrayOf(row.id))
                            .withValue(ContactsContract.Data.DATA1, value)
                            .withValue(ContactsContract.Data.DATA2, entry.type)
                            .withValue(ContactsContract.Data.DATA3, entry.label)
                            .build()
                    )
                }
            }
        }
        rows.filter { it.id !in keptRowIds }.forEach { row ->
            ops.add(
                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection("${ContactsContract.Data._ID}=?", arrayOf(row.id))
                    .build()
            )
        }
    }

    /**
     * Writes the edited first and last names into [rawContactId]'s existing name row, so what the
     * form does not show (middle name, prefix, suffix, phonetic names) stays, and the provider
     * rebuilds the display name from all of them. Inserts the row where there is none yet.
     */
    private fun structuredNameOperation(rawContactId: String, contact: Contact): ContentProviderOperation {
        val name = ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
        val selection = "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?"
        val selectionArgs = arrayOf(rawContactId, name)
        val hasNameRow = try {
            contentResolver.query(ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data._ID), selection, selectionArgs, null)
                ?.use { it.moveToFirst() } == true
        } catch (e: SecurityException) {
            false
        }
        val hasParts = contact.givenName != null || contact.familyName != null
        return if (hasNameRow) {
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(selection, selectionArgs)
                .apply {
                    if (hasParts) {
                        withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, contact.givenName)
                        withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, contact.familyName)
                    } else {
                        withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
                    }
                }
                .build()
        } else {
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, name)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, contact.givenName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, contact.familyName)
                .build()
        }
    }

    /**
     * Each contact's account, as (name, type), for the whole address book in one query: the one of
     * its first entry, as [getAccountInfo] reads it. Device storage has no account and maps to nulls.
     */
    private fun contactAccounts(): Map<String, Pair<String?, String?>> {
        val accounts = mutableMapOf<String, Pair<String?, String?>>()
        try {
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.RawContacts.CONTACT_ID,
                    ContactsContract.RawContacts.ACCOUNT_NAME,
                    ContactsContract.RawContacts.ACCOUNT_TYPE
                ),
                "${ContactsContract.RawContacts.DELETED} = 0",
                null,
                "${ContactsContract.RawContacts._ID} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(0) ?: continue
                    accounts.getOrPut(contactId) { cursor.getString(1) to cursor.getString(2) }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return accounts
    }

    private fun getAccountInfo(contactId: String): Pair<String?, String?> {
        try {
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE),
                "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
                arrayOf(contactId),
                "${ContactsContract.RawContacts._ID} ASC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                    val typeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                    val type = if (typeIdx != -1) cursor.getString(typeIdx) else null
                    return Pair(name, type)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(null, null)
    }

    override fun saveContact(contact: Contact): ContactSaveResult {
        val isExisting = contact.id.isNotEmpty() && contact.id != "0"
        val storedPrivately = contact.id.startsWith("p")
        return if (isExisting && storedPrivately != contact.isPrivate) moveContact(contact) else writeContact(contact)
    }

    /**
     * Saves [contact], edits included, into the storage it now asks for, then removes the copy
     * left in the other one. Only once the new copy exists: until then the old one is the only one.
     */
    private fun moveContact(contact: Contact): ContactSaveResult {
        val oldId = contact.id
        val newId: String?
        if (contact.isPrivate) {
            val localId = privateContactDao.insert(
                PrivateContactEntity.fromContact(contact.copy(id = "0", photoUri = keepPrivatePhoto(contact.photoUri)))
            )
            if (localId <= 0) return ContactSaveResult.FAILED
            newId = "p$localId"
        } else {
            val result = writeContact(contact.copy(id = ""))
            if (result != ContactSaveResult.SAVED) return result
            newId = contact.phoneNumbers.firstNotNullOfOrNull { number ->
                getContactByNumber(number)?.id?.takeIf { it.isNotBlank() && !it.startsWith("p") }
            }
        }
        deleteContactInternal(oldId, false)
        CallBackgroundStore.carryBlocking(context, oldId, newId, contact.phoneNumbers)
        return ContactSaveResult.SAVED
    }

    private fun writeContact(contact: Contact): ContactSaveResult {
        if (contact.isPrivate) {
            val entity = PrivateContactEntity.fromContact(contact.copy(photoUri = keepPrivatePhoto(contact.photoUri)))
            if (entity.localId == 0L) {
                privateContactDao.insert(entity)
            } else {
                val previousPhoto = privateContactDao.getById(entity.localId)?.photoUri
                privateContactDao.update(entity)
                if (previousPhoto != entity.photoUri) deletePrivatePhoto(previousPhoto)
            }
            return ContactSaveResult.SAVED
        }
        val ops = ArrayList<ContentProviderOperation>()
        val photoBytes = contact.photoUri?.let { getPhotoBytes(it) }

        val effectivePhones = if (contact.phones.isNotEmpty()) {
            contact.phones
        } else {
            contact.phoneNumbers.map { PhoneNumberEntry(it) }
        }
        val effectiveEmails = if (contact.emailEntries.isNotEmpty()) {
            contact.emailEntries
        } else {
            contact.emails.map { EmailEntry(it) }
        }
        val effectiveAddresses = contact.addressEntries.ifEmpty {
            contact.addresses.map { AddressEntry(it) }
        }

        val isNew = contact.id.isEmpty() || contact.id == "0"
        if (isNew) {
            val rawContactIndex = ops.size
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, contact.accountType)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, contact.accountName)
                    .withValue(ContactsContract.RawContacts.STARRED, if (contact.isFavorite) 1 else 0)
                    .withValue(ContactsContract.RawContacts.CUSTOM_RINGTONE, contact.customRingtone)
                    .build()
            )

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, contact.givenName)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, contact.familyName)
                    .build()
            )

            if (photoBytes != null) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                        .build()
                )
            }

            if (contact.nickname != null) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Nickname.NAME, contact.nickname)
                        .withValue(ContactsContract.CommonDataKinds.Nickname.TYPE, ContactsContract.CommonDataKinds.Nickname.TYPE_DEFAULT)
                        .build()
                )
            }

            if (contact.notes != null) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Note.NOTE, contact.notes)
                        .build()
                )
            }

            effectivePhones.forEach { entry ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, entry.number)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, entry.type)
                        .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, entry.label)
                        .build()
                )
            }

            effectiveEmails.forEach { entry ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, entry.address)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, entry.type)
                        .withValue(ContactsContract.CommonDataKinds.Email.LABEL, entry.label)
                        .build()
                )
            }

            effectiveAddresses.forEach { entry ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, entry.address)
                        .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, entry.type)
                        .withValue(ContactsContract.CommonDataKinds.StructuredPostal.LABEL, entry.label)
                        .build()
                )
            }

            contact.events.forEach { event ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, event.date)
                        .withValue(ContactsContract.CommonDataKinds.Event.TYPE, event.type)
                        .withValue(ContactsContract.CommonDataKinds.Event.LABEL, event.label)
                        .build()
                )
            }
        } else {
            val rawContactIds = getRawContactIds(contact.id)
            val current = getContactById(contact.id)
            if (rawContactIds.isEmpty() || current == null) return ContactSaveResult.FAILED
            // A linked contact gathers several entries (another account, WhatsApp): each keeps its own
            // data, changed only where the user changed it. What is new goes to the first entry, the
            // one whose account the contact shows, and so does a change of account.
            val primaryRawContactId = rawContactIds.first()
            val nameRawContactId = getNameRawContactId(contact.id)?.takeIf { it in rawContactIds } ?: primaryRawContactId

            if (getAccountInfo(contact.id) != (contact.accountName to contact.accountType)) {
                ops.add(
                    ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI)
                        .withSelection("${ContactsContract.RawContacts._ID}=?", arrayOf(primaryRawContactId))
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, contact.accountType)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, contact.accountName)
                        .build()
                )
            }

            ops.add(structuredNameOperation(nameRawContactId, contact))

            if (contact.photoUri != current.photoUri) {
                val photo = ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                if (contact.photoUri == null) {
                    ops.add(deleteDataRowsOperation(rawContactIds, photo))
                } else if (photoBytes != null) {
                    ops.add(deleteDataRowsOperation(listOf(primaryRawContactId), photo))
                    // Super primary, so the contact shows it rather than a linked entry's own photo.
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, primaryRawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, photo)
                            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                            .withValue(ContactsContract.Data.IS_PRIMARY, 1)
                            .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                            .build()
                    )
                }
            }

            if (contact.nickname != current.nickname) {
                val nickname = ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE
                ops.add(deleteDataRowsOperation(rawContactIds, nickname))
                if (contact.nickname != null) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, primaryRawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, nickname)
                            .withValue(ContactsContract.CommonDataKinds.Nickname.NAME, contact.nickname)
                            .withValue(ContactsContract.CommonDataKinds.Nickname.TYPE, ContactsContract.CommonDataKinds.Nickname.TYPE_DEFAULT)
                            .build()
                    )
                }
            }

            if (contact.notes != current.notes) {
                val note = ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
                ops.add(deleteDataRowsOperation(rawContactIds, note))
                if (contact.notes != null) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, primaryRawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, note)
                            .withValue(ContactsContract.CommonDataKinds.Note.NOTE, contact.notes)
                            .build()
                    )
                }
            }

            syncDataRows(
                ops, rawContactIds, primaryRawContactId,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                effectivePhones.map { TypedValue(it.number, it.type, it.label) },
                ::areNumbersEqual
            )
            syncDataRows(
                ops, rawContactIds, primaryRawContactId,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                effectiveEmails.map { TypedValue(it.address, it.type, it.label) }
            ) { a, b -> a == b }
            syncDataRows(
                ops, rawContactIds, primaryRawContactId,
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                effectiveAddresses.map { TypedValue(it.address, it.type, it.label) }
            ) { a, b -> a == b }
        }

        return try {
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            if (BuildConfig.DEBUG_TOOLS) {
                Log.d(DEBUG_TAG, "writeContact id=${contact.id} account=${contact.accountType}|${contact.accountName} ops=${ops.size} first=${results.firstOrNull()?.uri}")
            }
            ContactSaveResult.SAVED
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_TOOLS) {
                Log.d(DEBUG_TAG, "writeContact id=${contact.id} account=${contact.accountType}|${contact.accountName} failed", e)
            }
            e.printStackTrace()
            // Where the default account for new contacts is a cloud one, the provider rejects
            // a new contact in device storage (no account) with this exception.
            val deviceStorageRefused = e is IllegalArgumentException && isNew &&
                contact.accountType == null && contact.accountName == null
            if (deviceStorageRefused) ContactSaveResult.DEVICE_STORAGE_REFUSED else ContactSaveResult.FAILED
        }
    }

    private fun clearCallBackground(contactId: String) {
        val numbers: List<String> = try {
            getContactById(contactId)?.phoneNumbers ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        CallBackgroundStore.clearBlocking(context, contactId, numbers)
    }

    private fun deleteContactInternal(contactId: String, clearBackground: Boolean) {
        if (clearBackground) clearCallBackground(contactId)
        if (contactId.startsWith("p")) {
            val id = contactId.substring(1).toLongOrNull() ?: return
            deletePrivateContact(id)
            return
        }
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
        contentResolver.delete(uri, null, null)
    }

    private fun deletePrivateContact(localId: Long) {
        val photoUri = privateContactDao.getById(localId)?.photoUri
        privateContactDao.deleteById(localId)
        deletePrivatePhoto(photoUri)
    }

    private val privatePhotosDir: File get() = File(context.filesDir, "private_photos")

    /**
     * A private contact's photo as a copy in the app's own storage: the system contact photo is
     * deleted along with the public contact, and access to a picked gallery image does not last.
     */
    private fun keepPrivatePhoto(photoUri: String?): String? {
        if (photoUri == null || isPrivatePhoto(photoUri)) return photoUri
        val bytes = getPhotoBytes(photoUri) ?: return photoUri
        return try {
            val file = File(privatePhotosDir.apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
            file.writeBytes(bytes)
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            photoUri
        }
    }

    private fun isPrivatePhoto(photoUri: String): Boolean {
        val uri = Uri.parse(photoUri)
        return uri.scheme == "file" && uri.path?.let { File(it).parentFile } == privatePhotosDir
    }

    private fun deletePrivatePhoto(photoUri: String?) {
        if (photoUri == null || !isPrivatePhoto(photoUri)) return
        Uri.parse(photoUri).path?.let { File(it).delete() }
    }

    override fun deleteContact(contactId: String) {
        deleteContactInternal(contactId, true)
    }

    override fun deleteContacts(contactIds: List<String>) {
        val ops = ArrayList<ContentProviderOperation>()
        contactIds.forEach { id ->
            clearCallBackground(id)
            if (id.startsWith("p")) {
                val lid = id.substring(1).toLongOrNull()
                if (lid != null) deletePrivateContact(lid)
                return@forEach
            }
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id)
            ops.add(ContentProviderOperation.newDelete(uri).build())
        }
        applyInBatches(ops)
    }

    override fun moveContacts(contactIds: List<String>, accountName: String?, accountType: String?) {
        val ops = ArrayList<ContentProviderOperation>()
        contactIds.forEach { id ->
            // A private contact has no system entry to re-file: it is copied into the account instead.
            if (id.startsWith("p")) {
                getContactById(id)?.let { moveContact(it.copy(isPrivate = false, accountName = accountName, accountType = accountType)) }
                return@forEach
            }
            val rawContactId = getRawContactId(id)
            if (rawContactId != null) {
                ops.add(
                    ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI)
                        .withSelection("${ContactsContract.RawContacts._ID}=?", arrayOf(rawContactId))
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                        .build()
                )
            }
        }
        applyInBatches(ops)
    }

    /** Sends [ops] in chunks: the provider refuses a single batch beyond about 500 operations. */
    private fun applyInBatches(ops: List<ContentProviderOperation>) {
        ops.chunked(MAX_BATCH_OPERATIONS).forEach { chunk ->
            try {
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(chunk))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getCloudDefaultAccount(): Account? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return null
        return try {
            val default = ContactsContract.RawContacts.DefaultAccount.getDefaultAccountForNewContacts(contentResolver)
            val cloud = ContactsContract.RawContacts.DefaultAccount.DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_CLOUD
            if (default.state == cloud) default.account else null
        } catch (e: SecurityException) {
            null
        }
    }

    override fun getAvailableAccounts(): List<Account> {
        return try {
            val allAccounts = AccountManager.get(context).accounts.toList()
            val syncAdapters = ContentResolver.getSyncAdapterTypes()
            val contactAccountTypes = syncAdapters
                .filter { it.authority == ContactsContract.AUTHORITY }
                .map { it.accountType }
                .toSet()

            val existingAccountKeys = mutableSetOf<String>()
            try {
                contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val nameCol = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                    val typeCol = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
                    while (cursor.moveToNext()) {
                        val name = if (nameCol != -1) cursor.getString(nameCol) else null
                        val type = if (typeCol != -1) cursor.getString(typeCol) else null
                        if (!name.isNullOrBlank() && !type.isNullOrBlank()) {
                            existingAccountKeys.add("$name|$type")
                        }
                    }
                }
            } catch (e: Exception) {
            }

            val filtered = allAccounts.filter { account ->
                val isSyncable = try { ContentResolver.getIsSyncable(account, ContactsContract.AUTHORITY) > 0 } catch (e: Exception) { false }
                val matchesType = account.type in contactAccountTypes
                val hasRawContacts = existingAccountKeys.contains("${account.name}|${account.type}")
                (matchesType && isSyncable) || hasRawContacts
            }

            if (filtered.isNotEmpty()) filtered else allAccounts
        } catch (e: SecurityException) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun getContactByNumber(number: String): Contact? {
        if (auk.dialer.vroot.controller.util.isVoicemailNumber(context, number)) {
            return Contact(
                id = "voicemail",
                name = context.getString(R.string.settings_voicemail_title),
                photoUri = "voicemail://icon",
                phoneNumbers = listOf(number)
            )
        }

        privateContactDao.getAll().forEach {
            val contact = it.toContact()
            if (contact.phoneNumbers.any { num -> areNumbersEqual(num, number) }) {
                return contact
            }
        }

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(
            ContactsContract.PhoneLookup.CONTACT_ID,
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.STARRED,
            ContactsContract.PhoneLookup.CUSTOM_RINGTONE
        )

        try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.CONTACT_ID)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    val starredIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.STARRED)
                    val ringtoneIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.CUSTOM_RINGTONE)

                    val id = if (idIdx != -1) cursor.getString(idIdx).orEmpty() else ""
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) else unknownLabel
                    val photoUri = if (photoIdx != -1) cursor.getString(photoIdx) else null
                    val starred = if (starredIdx != -1) cursor.getInt(starredIdx) == 1 else false
                    val ringtone = if (ringtoneIdx != -1) cursor.getString(ringtoneIdx) else null

                    val numbers = if (id.isNotBlank()) {
                        deduplicateNumbers(listOf(number) + getNumbersForContactId(id))
                    } else {
                        listOf(number)
                    }

                    return Contact(
                        id = id,
                        name = formatName(name ?: unknownLabel),
                        photoUri = photoUri,
                        isFavorite = starred,
                        phoneNumbers = numbers,
                        customRingtone = ringtone
                    )
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return null
    }

    override fun findDuplicates(): List<List<Contact>> {
        val allContacts = getContacts()
        val duplicates = mutableListOf<List<Contact>>()

        val byName = allContacts.groupBy { it.name.lowercase().trim() }
            .filter { it.value.size > 1 }

        val byNumber = mutableMapOf<String, MutableSet<Contact>>()
        allContacts.forEach { contact ->
            contact.phoneNumbers.forEach { number ->
                val normalized = number.replace(Regex("[^0-9+]"), "")
                if (normalized.length >= 7) {
                    byNumber.getOrPut(normalized) { mutableSetOf() }.add(contact)
                }
            }
        }
        val byNumberFiltered = byNumber.filter { it.value.size > 1 }

        val processedIds = mutableSetOf<String>()

        byName.values.forEach { group ->
            duplicates.add(group)
            processedIds.addAll(group.map { it.id })
        }

        byNumberFiltered.values.forEach { group ->
            val uniqueGroup = group.filter { it.id !in processedIds }
            if (uniqueGroup.size > 1) {
                duplicates.add(uniqueGroup)
            }
        }

        return duplicates
    }

    override fun mergeContacts(targetContactId: String, sourceContactIds: List<String>) {
        val targetContact = getContactById(targetContactId) ?: return
        val ops = ArrayList<ContentProviderOperation>()

        sourceContactIds.forEach { sourceId ->
            if (sourceId == targetContactId) return@forEach
            val sourceContact = getContactById(sourceId) ?: return@forEach

            sourceContact.phoneNumbers.forEach { number ->
                if (!targetContact.phoneNumbers.contains(number)) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, getRawContactId(targetContactId))
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build())
                }
            }

            CallBackgroundStore.clearBlocking(context, sourceId, emptyList())

            ops.add(ContentProviderOperation.newDelete(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, sourceId))
                .build())
        }

        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun setCustomRingtone(contactId: String, ringtoneUri: String?) {
        if (contactId.startsWith("p")) {
            val id = contactId.substring(1).toLongOrNull() ?: return
            privateContactDao.getById(id)?.let {
                privateContactDao.update(it.copy(customRingtone = ringtoneUri))
            }
            return
        }
        val contentValue = ContentValues().apply {
            put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri)
        }
        val updateUri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
            .appendPath(contactId)
            .build()
        contentResolver.update(updateUri, contentValue, null, null)
    }

    override fun formatAllPhoneNumbers(onProgress: ((current: Int, total: Int) -> Unit)?) {
        val allContacts = getContacts(true)
        val ops = ArrayList<ContentProviderOperation>()
        val total = allContacts.size

        allContacts.forEachIndexed { index, contact ->
            onProgress?.invoke(index + 1, total)
            val updatedNumbers = contact.phoneNumbers.map { it.replace(" ", "") }
            if (updatedNumbers != contact.phoneNumbers) {
                if (contact.isPrivate) {
                    val id = contact.id.substring(1).toLongOrNull()
                    if (id != null) {
                        privateContactDao.getById(id)?.let {
                            val phones = it.toContact().phones.map { entry -> entry.copy(number = entry.number.replace(" ", "")) }
                            privateContactDao.update(it.withPhones(phones))
                        }
                    }
                } else {
                    // Every linked entry of the contact: the number may live in any of them.
                    val rawIds = getRawContactIds(contact.id)
                    contact.phoneNumbers.forEachIndexed { i, oldNum ->
                        val newNum = updatedNumbers[i]
                        if (newNum != oldNum) {
                            rawIds.forEach { rawId ->
                                ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                                    .withSelection(
                                        "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Phone.NUMBER}=?",
                                        arrayOf(rawId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, oldNum)
                                    )
                                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNum)
                                    .build())
                            }
                        }
                    }
                }
            }
        }

        applyInBatches(ops)
    }

    override fun makeContactPrivate(contactId: String) {
        val contact = getContactById(contactId) ?: return
        if (!contact.isPrivate) moveContact(contact.copy(isPrivate = true))
    }

    override fun makeContactPublic(contactId: String) {
        val contact = getContactById(contactId) ?: return
        if (contact.isPrivate) moveContact(contact.copy(isPrivate = false))
    }

    override fun exportPrivateContacts(uri: Uri) {
        val privateContacts = privateContactDao.getAll().map { it.toContact() }
        val vcfContent = buildString {
            privateContacts.forEach { contact ->
                append("BEGIN:VCARD\n")
                append("VERSION:3.0\n")
                append("FN:${contact.name}\n")
                contact.phoneNumbers.forEach { append("TEL;TYPE=CELL:$it\n") }
                contact.emails.forEach { append("EMAIL;TYPE=HOME:$it\n") }
                append("END:VCARD\n")
            }
        }
        try {
            context.contentResolver.openOutputStream(uri)?.use { 
                it.write(vcfContent.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun importPrivateContacts(uri: Uri) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
            val vCards = content.split("BEGIN:VCARD")
            vCards.forEach { vCard ->
                if (vCard.isBlank()) return@forEach
                var name = ""
                val numbers = mutableListOf<String>()
                val emails = mutableListOf<String>()
                
                vCard.lines().forEach { line ->
                    when {
                        line.startsWith("FN:") -> name = line.substring(3)
                        line.startsWith("TEL") -> {
                            val parts = line.split(":")
                            if (parts.size > 1) numbers.add(parts[1])
                        }
                        line.startsWith("EMAIL") -> {
                            val parts = line.split(":")
                            if (parts.size > 1) emails.add(parts[1])
                        }
                    }
                }
                
                if (name.isNotBlank() || numbers.isNotEmpty()) {
                    saveContact(Contact(
                        id = "0",
                        name = if (name.isBlank()) numbers.firstOrNull() ?: unknownLabel else name,
                        phoneNumbers = numbers,
                        emails = emails,
                        isPrivate = true
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}