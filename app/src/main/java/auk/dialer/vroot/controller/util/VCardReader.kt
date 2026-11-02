package auk.dialer.vroot.controller.util

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/** Name, numbers and e-mails of one card, the parts the imports keep. */
data class VCardEntry(
    val name: String,
    val phoneNumbers: List<String>,
    val emails: List<String>
)

/**
 * Reads the vCard files phones actually export, not only the plain form the app writes itself.
 * Samsung and older Android phones write vCard 2.1, where accented text is quoted-printable
 * ("FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:H=C3=A9l=C3=A8ne") and may be split over lines
 * ending in "=". iPhones write vCard 3.0, with grouped properties ("item1.TEL;type=CELL:...") and
 * long lines folded onto lines starting with a space.
 */
object VCardReader {

    fun parse(text: String): List<VCardEntry> {
        val entries = mutableListOf<VCardEntry>()
        var inCard = false
        var fullName = ""
        var structuredName = ""
        val numbers = mutableListOf<String>()
        val emails = mutableListOf<String>()

        for (line in unfold(text)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val head = line.substring(0, colon)
            val rawValue = line.substring(colon + 1)
            val parts = head.split(';')
            val property = parts[0].substringAfterLast('.').trim().uppercase()
            val params = parts.drop(1).map { it.trim().uppercase() }

            when (property) {
                "BEGIN" -> if (rawValue.trim().equals("VCARD", ignoreCase = true)) {
                    inCard = true
                    fullName = ""
                    structuredName = ""
                    numbers.clear()
                    emails.clear()
                }
                "END" -> if (inCard && rawValue.trim().equals("VCARD", ignoreCase = true)) {
                    inCard = false
                    val name = fullName.ifBlank { structuredName }
                    if (name.isNotBlank() || numbers.isNotEmpty()) {
                        entries += VCardEntry(name.trim(), numbers.toList(), emails.toList())
                    }
                }
                "FN" -> if (inCard) fullName = unescape(decode(rawValue, params))
                "N" -> if (inCard) structuredName = nameFromParts(decode(rawValue, params))
                "TEL" -> if (inCard) {
                    val number = decode(rawValue, params).removePrefix("tel:").trim()
                    if (number.isNotEmpty()) numbers += number
                }
                "EMAIL" -> if (inCard) {
                    val email = unescape(decode(rawValue, params)).trim()
                    if (email.isNotEmpty()) emails += email
                }
            }
        }
        return entries
    }

    /**
     * Joins folded lines: a line starting with a space or tab continues the previous one, and in
     * quoted-printable text a line ending in "=" goes on on the next.
     */
    private fun unfold(text: String): List<String> {
        val lines = mutableListOf<String>()
        val pending = StringBuilder()
        var softBreak = false
        for (raw in text.split("\r\n", "\n", "\r")) {
            when {
                softBreak -> pending.append(raw)
                (raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty() && pending.isEmpty() ->
                    lines[lines.lastIndex] = lines.last() + raw.substring(1)
                else -> pending.append(raw)
            }
            softBreak = pending.contains("QUOTED-PRINTABLE", ignoreCase = true) && pending.endsWith("=")
            if (softBreak) {
                pending.setLength(pending.length - 1)
            } else if (pending.isNotEmpty()) {
                lines += pending.toString()
                pending.setLength(0)
            }
        }
        if (pending.isNotEmpty()) lines += pending.toString()
        return lines
    }

    private fun decode(value: String, params: List<String>): String {
        val quotedPrintable = params.any { it == "ENCODING=QUOTED-PRINTABLE" || it == "QUOTED-PRINTABLE" }
        if (!quotedPrintable) return value
        val charsetName = params.firstOrNull { it.startsWith("CHARSET=") }?.substringAfter('=') ?: "UTF-8"
        val charset = try { Charset.forName(charsetName) } catch (e: Exception) { Charsets.UTF_8 }
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '=' && i + 2 <= value.lastIndex) {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes.write(hex)
                    i += 3
                    continue
                }
            }
            bytes.write(c.toString().toByteArray(charset))
            i++
        }
        return String(bytes.toByteArray(), charset)
    }

    /** "Family;Given;Middle;Prefix;Suffix" as it reads: "Prefix Given Middle Family Suffix". */
    private fun nameFromParts(value: String): String {
        val fields = splitUnescaped(value).map { unescape(it).trim() }
        val family = fields.getOrElse(0) { "" }
        val given = fields.getOrElse(1) { "" }
        val middle = fields.getOrElse(2) { "" }
        val prefix = fields.getOrElse(3) { "" }
        val suffix = fields.getOrElse(4) { "" }
        return listOf(prefix, given, middle, family, suffix).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun splitUnescaped(value: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                current.append(c).append(value[i + 1])
                i += 2
                continue
            }
            if (c == ';') {
                fields += current.toString()
                current.setLength(0)
            } else {
                current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }

    private fun unescape(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
}
