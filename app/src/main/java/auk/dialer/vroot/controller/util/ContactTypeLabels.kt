package auk.dialer.vroot.controller.util

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone as PhoneIcon
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

object ContactTypeLabels {

    data class TypeOption(val type: Int, val labelResFallback: String)

    val phoneTypeOptions: List<Int> = listOf(
        Phone.TYPE_MOBILE,
        Phone.TYPE_HOME,
        Phone.TYPE_WORK,
        Phone.TYPE_MAIN,
        Phone.TYPE_OTHER
    )

    val emailTypeOptions: List<Int> = listOf(
        Email.TYPE_HOME,
        Email.TYPE_WORK,
        Email.TYPE_OTHER
    )

    fun phoneTypeLabel(context: Context, type: Int, label: String?): String {
        return try {
            Phone.getTypeLabel(context.resources, type, label).toString()
        } catch (e: Exception) {
            label ?: ""
        }
    }

    fun emailTypeLabel(context: Context, type: Int, label: String?): String {
        return try {
            Email.getTypeLabel(context.resources, type, label).toString()
        } catch (e: Exception) {
            label ?: ""
        }
    }

    fun phoneTypeIcon(type: Int): ImageVector = when (type) {
        Phone.TYPE_MOBILE -> Icons.Default.Smartphone
        Phone.TYPE_HOME -> Icons.Default.Home
        Phone.TYPE_WORK -> Icons.Default.Work
        Phone.TYPE_MAIN -> Icons.Default.Star
        else -> Icons.Default.PhoneIcon
    }

    fun emailTypeIcon(type: Int): ImageVector = when (type) {
        Email.TYPE_HOME -> Icons.Default.Home
        Email.TYPE_WORK -> Icons.Default.Work
        else -> Icons.Default.Email
    }
}
