package auk.dialer.vroot.controller.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

object SocialUtils {
    fun openWhatsApp(context: Context, number: String) {
        open(context, "https://api.whatsapp.com/send?phone=${internationalNumber(context, number).removePrefix("+")}")
    }

    fun openTelegram(context: Context, number: String) {
        open(context, "https://t.me/+${internationalNumber(context, number).removePrefix("+")}")
    }

    fun openSignal(context: Context, number: String) {
        open(context, "https://signal.me/#p/${internationalNumber(context, number)}")
    }

    private fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {}
    }

    /**
     * [number] in international form (+33612345678): these apps know a person by it, and a number
     * saved as "06 12 34 56 78" reached nobody. Read with the SIM's or network's country.
     */
    private fun internationalNumber(context: Context, number: String): String {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val country = (telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
            ?: telephony?.simCountryIso?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().country).uppercase(Locale.ROOT)
        return PhoneNumberUtils.formatNumberToE164(number, country)
            ?: number.filterIndexed { index, char -> char.isDigit() || (index == 0 && char == '+') }
    }
}
