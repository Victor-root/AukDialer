package auk.dialer.vroot.controller

import auk.dialer.vroot.controller.util.VCardReader
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import auk.dialer.vroot.R
import auk.dialer.vroot.modal.`interface`.ICallLogRepository
import auk.dialer.vroot.modal.`interface`.IContactsRepository
import auk.dialer.vroot.modal.data.CallLogEntry
import auk.dialer.vroot.modal.data.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class BackupViewModel(
    private val contactsRepo: IContactsRepository,
    private val callLogRepo: ICallLogRepository
) : ViewModel() {

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun exportContacts(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contacts = contactsRepo.getDeviceContactsWithDetails()
                val vCard = buildString {
                    contacts.forEach { contact ->
                        append("BEGIN:VCARD\n")
                        append("VERSION:3.0\n")
                        append("FN:${contact.name}\n")
                        contact.phoneNumbers.forEach { number ->
                            append("TEL;TYPE=CELL:$number\n")
                        }
                        contact.emails.forEach { email ->
                            append("EMAIL;TYPE=INTERNET:$email\n")
                        }
                        append("END:VCARD\n")
                    }
                }
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(vCard)
                    }
                }
                _status.value = context.getString(R.string.backup_contacts_export_success)
            } catch (e: Exception) {
                _status.value = context.getString(R.string.backup_export_failed, e.message)
            }
        }
    }

    fun importContacts(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val text = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                    VCardReader.parse(text).forEach { entry ->
                        contactsRepo.saveContact(
                            Contact(
                                id = "0",
                                name = entry.name.ifBlank { entry.phoneNumbers.first() },
                                phoneNumbers = entry.phoneNumbers,
                                emails = entry.emails
                            )
                        )
                    }
                }
                _status.value = context.getString(R.string.backup_contacts_import_success)
            } catch (e: Exception) {
                _status.value = context.getString(R.string.backup_import_failed, e.message)
            }
        }
    }

    fun exportCallLogs(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logs = callLogRepo.getCalls()
                val jsonString = json.encodeToString(logs)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(jsonString)
                    }
                }
                _status.value = context.getString(R.string.backup_call_logs_export_success)
            } catch (e: Exception) {
                _status.value = context.getString(R.string.backup_export_failed, e.message)
            }
        }
    }

    fun importCallLogs(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = BufferedReader(InputStreamReader(inputStream)).readText()
                    val logs = json.decodeFromString<List<CallLogEntry>>(jsonString)
                    val failed = logs.count { log -> !callLogRepo.saveCallLog(log) }
                    if (failed > 0) {
                        _status.value = context.getString(
                            R.string.backup_import_failed,
                            context.resources.getQuantityString(R.plurals.backup_call_logs_not_restored, failed, failed)
                        )
                        return@launch
                    }
                }
                _status.value = context.getString(R.string.backup_call_logs_import_success)
            } catch (e: Exception) {
                _status.value = context.getString(R.string.backup_import_failed, e.message)
            }
        }
    }

    fun clearStatus() {
        _status.value = null
    }
}
