package auk.dialer.vroot.controller.util

import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import auk.dialer.vroot.modal.data.AddressEntry
import auk.dialer.vroot.modal.data.Contact
import auk.dialer.vroot.modal.data.ContactEvent

/**
 * Writes contacts as vCard 3.0, the counterpart of [VCardReader]: every card carries the structured
 * name (N) the format requires besides the display name, and text is escaped, so a name holding a
 * comma or a semicolon reads back the same in other apps.
 */
object VCardWriter {

    fun cards(contacts: List<Contact>): String = buildString {
        contacts.forEach { contact ->
            append(
                card(
                    contact.name, contact.givenName, contact.familyName, contact.phoneNumbers, contact.emails,
                    nickname = contact.nickname, company = contact.company,
                    addresses = contact.addressEntries.ifEmpty { contact.addresses.map { AddressEntry(it) } },
                    events = contact.events
                )
            )
        }
    }

    fun card(
        name: String,
        givenName: String?,
        familyName: String?,
        phones: List<String>,
        emails: List<String>,
        nickname: String? = null,
        company: String? = null,
        addresses: List<AddressEntry> = emptyList(),
        events: List<ContactEvent> = emptyList()
    ): String = buildString {
        line("BEGIN:VCARD")
        line("VERSION:3.0")
        // Family;Given;Additional;Prefix;Suffix. A name never split into parts goes in as given name.
        val given = givenName?.takeIf { it.isNotBlank() } ?: name.takeIf { familyName.isNullOrBlank() }
        line("N:${escape(familyName.orEmpty())};${escape(given.orEmpty())};;;")
        line("FN:${escape(name)}")
        nickname?.takeIf { it.isNotBlank() }?.let { line("NICKNAME:${escape(it)}") }
        company?.takeIf { it.isNotBlank() }?.let { line("ORG:${escape(it)}") }
        phones.filter { it.isNotBlank() }.forEach { line("TEL;TYPE=CELL:${escape(it)}") }
        emails.filter { it.isNotBlank() }.forEach { line("EMAIL;TYPE=INTERNET:${escape(it)}") }
        // The address is kept whole as one line, the street part: it is stored that way on the phone.
        addresses.filter { it.address.isNotBlank() }.forEach { entry ->
            val type = when (entry.type) {
                StructuredPostal.TYPE_HOME -> ";TYPE=HOME"
                StructuredPostal.TYPE_WORK -> ";TYPE=WORK"
                else -> ""
            }
            line("ADR$type:;;${escape(entry.address)};;;;")
        }
        // vCard 3.0 only names the birthday; the wedding anniversary goes the way Apple and Evolution write it.
        events.forEach { event ->
            val day = EventDates.parse(event.date) ?: return@forEach
            when (event.type) {
                Event.TYPE_BIRTHDAY -> line("BDAY:${EventDates.store(day)}")
                Event.TYPE_ANNIVERSARY -> line("X-ANNIVERSARY:${EventDates.store(day)}")
            }
        }
        line("END:VCARD")
    }

    private fun StringBuilder.line(text: String) {
        append(text).append("\r\n")
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(",", "\\,")
        .replace(";", "\\;")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
}
