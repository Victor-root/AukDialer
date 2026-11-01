package auk.dialer.vroot.modal.`interface`

import android.accounts.Account
import android.net.Uri
import auk.dialer.vroot.modal.data.Contact

interface IContactsRepository {
    fun getContacts(includePrivate: Boolean = true): List<Contact>
    fun getContactById(contactId: String): Contact?

    /**
     * Every contact in the device address book with all its numbers and emails, including those
     * with no number. Private contacts are not part of it: they have their own export.
     */
    fun getDeviceContactsWithDetails(): List<Contact>
    fun getContactByNumber(number: String): Contact?
    fun toggleFavorite(contactId: String, isFavorite: Boolean)
    /**
     * Creates or updates [contact]. An existing contact whose isPrivate no longer matches where it is
     * stored is moved to the other storage. False when nothing could be written.
     */
    fun saveContact(contact: Contact): Boolean
    fun deleteContact(contactId: String)
    fun deleteContacts(contactIds: List<String>)
    fun moveContacts(contactIds: List<String>, accountName: String?, accountType: String?)
    fun getAvailableAccounts(): List<Account>
    fun findDuplicates(): List<List<Contact>>
    fun mergeContacts(targetContactId: String, sourceContactIds: List<String>)
    fun setCustomRingtone(contactId: String, ringtoneUri: String?)
    fun formatAllPhoneNumbers(onProgress: ((current: Int, total: Int) -> Unit)? = null)

    fun makeContactPrivate(contactId: String)
    fun makeContactPublic(contactId: String)
    fun exportPrivateContacts(uri: Uri)
    fun importPrivateContacts(uri: Uri)
}