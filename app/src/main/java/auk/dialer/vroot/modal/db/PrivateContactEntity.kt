package auk.dialer.vroot.modal.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import auk.dialer.vroot.modal.data.Contact
import auk.dialer.vroot.modal.data.ContactEvent
import auk.dialer.vroot.modal.data.EmailEntry
import auk.dialer.vroot.modal.data.PhoneNumberEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Entries with their type and label; contacts saved before those were kept hold bare values. */
private inline fun <reified T> decodeEntries(json: String, fromBare: (String) -> T): List<T> =
    runCatching { Json.decodeFromString<List<T>>(json) }
        .recoverCatching { Json.decodeFromString<List<String>>(json).map(fromBare) }
        .getOrDefault(emptyList())

@Entity(tableName = "private_contacts")
data class PrivateContactEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val name: String,
    val nickname: String? = null,
    val phoneNumbersJson: String,
    val emailsJson: String,
    val addressesJson: String,
    val eventsJson: String,
    val photoUri: String? = null,
    val isFavorite: Boolean = false,
    val customRingtone: String? = null,
    val notes: String? = null
) {
    fun toContact(): Contact {
        val phones = decodeEntries(phoneNumbersJson) { PhoneNumberEntry(it) }
        val emails = decodeEntries(emailsJson) { EmailEntry(it) }
        return Contact(
            id = "p$localId",
            name = name,
            nickname = nickname,
            phoneNumbers = phones.map { it.number },
            emails = emails.map { it.address },
            phones = phones,
            emailEntries = emails,
            addresses = runCatching { Json.decodeFromString<List<String>>(addressesJson) }.getOrDefault(emptyList()),
            events = runCatching { Json.decodeFromString<List<ContactEvent>>(eventsJson) }.getOrDefault(emptyList()),
            photoUri = photoUri,
            isFavorite = isFavorite,
            customRingtone = customRingtone,
            isPrivate = true,
            notes = notes
        )
    }

    fun withPhones(phones: List<PhoneNumberEntry>): PrivateContactEntity =
        copy(phoneNumbersJson = Json.encodeToString(phones))

    companion object {
        fun fromContact(contact: Contact): PrivateContactEntity {
            return PrivateContactEntity(
                localId = if (contact.id.startsWith("p")) contact.id.substring(1).toLongOrNull() ?: 0L else 0L,
                name = contact.name,
                nickname = contact.nickname,
                phoneNumbersJson = Json.encodeToString(contact.phones.ifEmpty { contact.phoneNumbers.map { PhoneNumberEntry(it) } }),
                emailsJson = Json.encodeToString(contact.emailEntries.ifEmpty { contact.emails.map { EmailEntry(it) } }),
                addressesJson = Json.encodeToString(contact.addresses),
                eventsJson = Json.encodeToString(contact.events),
                photoUri = contact.photoUri,
                isFavorite = contact.isFavorite,
                customRingtone = contact.customRingtone,
                notes = contact.notes
            )
        }
    }
}
