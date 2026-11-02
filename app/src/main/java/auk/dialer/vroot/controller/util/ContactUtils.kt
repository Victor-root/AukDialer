package auk.dialer.vroot.controller.util

import android.accounts.Account
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.ui.graphics.vector.ImageVector
import auk.dialer.vroot.R

object ContactUtils {
    fun getFriendlyAccountName(context: Context, account: Account): String {
        return when {
            account.type == "com.google" -> account.name
            account.type == "com.whatsapp" -> context.getString(R.string.brand_whatsapp)
            account.type.contains("telegram", ignoreCase = true) -> context.getString(R.string.brand_telegram)
            account.type.contains("xiaomi", ignoreCase = true) -> context.getString(R.string.account_mi_account)
            account.type.contains("sim", ignoreCase = true) -> context.getString(R.string.account_sim_card)
            else -> account.name
        }
    }

    /** The service an account belongs to, as a picker titles it: a Google account's own name is its address. */
    fun getAccountTitle(context: Context, account: Account): String =
        if (account.type == "com.google") context.getString(R.string.brand_google) else getFriendlyAccountName(context, account)

    /** The account itself under its [getAccountTitle], or null when that already says it. */
    fun getAccountSubtitle(context: Context, account: Account): String? =
        account.name.takeIf { it != getAccountTitle(context, account) }

    fun getAccountIcon(account: Account): ImageVector {
        return when {
            account.type == "com.google" -> Icons.Default.Email
            account.type.contains("sim", ignoreCase = true) -> Icons.Default.SimCard
            else -> Icons.Default.AccountCircle
        }
    }

    fun formatContactName(name: String, displayOrder: Int): String {
        if (displayOrder == 1) {
            val parts = name.trim().split("\\s+".toRegex())
            if (parts.size > 1) {
                return "${parts.last()}, ${parts.dropLast(1).joinToString(" ")}"
            }
        }
        return name
    }
}
