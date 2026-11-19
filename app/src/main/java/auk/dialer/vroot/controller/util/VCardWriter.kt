package auk.dialer.vroot.controller.util

import auk.dialer.vroot.modal.data.Contact

/**
 * Writes contacts as vCard 3.0, the counterpart of [VCardReader]: every card carries the structured
 * name (N) the format requires besides the display name, and text is escaped, so a name holding a
 * comma or a semicolon reads back the same in other apps.
 */
object VCardWriter {

    fun cards(contacts: List<Contact>): String = buildString {
        contacts.forEach { contact ->
            append(card(contact.name, contact.givenName, contact.familyName, contact.phoneNumbers, contact.emails))
        }
    }

    fun card(
        name: String,
        givenName: String?,
        familyName: String?,
        phones: List<String>,
        emails: List<String>
    ): String = buildString {
        line("BEGIN:VCARD")
        line("VERSION:3.0")
        // Family;Given;Additional;Prefix;Suffix. A name never split into parts goes in as given name.
        val given = givenName?.takeIf { it.isNotBlank() } ?: name.takeIf { familyName.isNullOrBlank() }
        line("N:${escape(familyName.orEmpty())};${escape(given.orEmpty())};;;")
        line("FN:${escape(name)}")
        phones.filter { it.isNotBlank() }.forEach { line("TEL;TYPE=CELL:${escape(it)}") }
        emails.filter { it.isNotBlank() }.forEach { line("EMAIL;TYPE=INTERNET:${escape(it)}") }
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
