package auk.dialer.vroot.controller.util

import android.content.Context
import androidx.compose.runtime.Immutable
import auk.dialer.vroot.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * What the screens show of people. In screenshot mode, everything personal is hidden so a screenshot
 * can be shared as is: a name becomes "Contact 3", the same one on every screen; a number keeps its
 * country code and last two digits; anything else (e-mail, address, company, date, note) turns to
 * dots of the same length; photos give way to the letter avatar. Only what is shown changes.
 */
@Immutable
class PrivacyMask(val enabled: Boolean, private val contactPattern: String) {

    /**
     * A person's [name], or a number shown in its place. [shown] is how the screen writes it (the
     * family name first, say): the pseudonym follows the name itself, so it stays the same everywhere.
     */
    fun name(name: String, shown: String = name): String = when {
        !enabled -> shown
        name.isBlank() || name in pseudonymNames -> name
        name.none { it.isLetter() } -> maskDigits(name)
        else -> {
            val label = contactPattern.format(pseudonyms.getOrPut(name) { nextPseudonym.incrementAndGet() })
            pseudonymNames.add(label)
            label
        }
    }

    /** A phone number, formatted the way the app shows numbers. */
    fun number(number: String): String {
        val formatted = formatPhoneNumber(number)
        return if (enabled) maskDigits(formatted) else formatted
    }

    /** Any other personal text. */
    fun text(value: String): String =
        if (enabled) value.map { if (it.isLetterOrDigit()) MASK else it }.joinToString("") else value

    /** A photo, which screenshot mode leaves out. */
    fun photo(uri: String?): String? = uri.takeUnless { enabled }

    companion object {
        val Off = PrivacyMask(enabled = false, contactPattern = "")

        /** The mask as the settings stand, for what is shown outside the screens: notifications. */
        fun of(context: Context, prefs: PreferenceManager) = PrivacyMask(
            prefs.getBoolean(PreferenceManager.KEY_SCREENSHOT_MODE, false),
            context.getString(R.string.screenshot_mode_contact)
        )

        private const val MASK = '•'
        private const val VISIBLE_LAST_DIGITS = 2

        // Shared by every screen and both activities, so a person keeps one pseudonym throughout.
        private val pseudonyms = ConcurrentHashMap<String, Int>()
        private val pseudonymNames: MutableSet<String> = ConcurrentHashMap.newKeySet()
        private val nextPseudonym = AtomicInteger()

        /** The country code ("+1" in "+1 202-555-0143") and the last digits stay, the rest is masked. */
        private fun maskDigits(number: String): String {
            val digitCount = number.count { it.isDigit() }
            val countryCodeEnd = if (number.startsWith('+')) {
                number.indexOfFirst { !it.isDigit() && it != '+' }.takeIf { it > 0 } ?: 0
            } else {
                0
            }
            var digitsSeen = 0
            return number.mapIndexed { index, char ->
                if (!char.isDigit()) return@mapIndexed char
                digitsSeen++
                if (index < countryCodeEnd || digitsSeen > digitCount - VISIBLE_LAST_DIGITS) char else MASK
            }.joinToString("")
        }
    }
}
