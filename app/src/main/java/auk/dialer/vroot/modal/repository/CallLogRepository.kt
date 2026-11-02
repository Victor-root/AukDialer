package auk.dialer.vroot.modal.repository

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.content.Context
import android.content.ComponentName
import android.content.ContentValues
import auk.dialer.vroot.R
import auk.dialer.vroot.modal.`interface`.ICallLogRepository
import auk.dialer.vroot.modal.data.CallLogEntry
import auk.dialer.vroot.modal.`interface`.IContactsRepository
import auk.dialer.vroot.modal.data.Contact
import auk.dialer.vroot.controller.util.phoneLookupKey

class CallLogRepository(
    private val contentResolver: ContentResolver,
    private val context: Context,
    private val contactsRepo: IContactsRepository
) : ICallLogRepository {

    private val preferenceManager = auk.dialer.vroot.controller.util.PreferenceManager(context)

    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    override fun getCalls(): List<CallLogEntry> {
        val callLogs = mutableListOf<CallLogEntry>()

        val allContacts = try { contactsRepo.getContacts() } catch (e: Exception) { emptyList() }
        val contactMap = mutableMapOf<String, Contact>()
        allContacts.forEach { contact ->
            contact.phoneNumbers.forEach { number ->
                contactMap[phoneLookupKey(number)] = contact
            }
        }

        val baseProjection = mutableListOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_PHOTO_URI,
            CallLog.Calls.CACHED_LOOKUP_URI,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.NUMBER_PRESENTATION
        )

        baseProjection.add("phone_account_label")
        baseProjection.add(CallLog.Calls.PHONE_ACCOUNT_ID)
        baseProjection.add(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)

        try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                baseProjection.toTypedArray(),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use { parseCursor(it, callLogs, contactMap) }
        } catch (e: Exception) {
            try {
                val safeProjection = arrayOf(
                    CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.CACHED_PHOTO_URI, CallLog.Calls.CACHED_LOOKUP_URI,
                    CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION,
                    CallLog.Calls.PHONE_ACCOUNT_ID, CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
                    CallLog.Calls.NUMBER_PRESENTATION
                )
                val cursor = contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    safeProjection,
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )
                cursor?.use { parseCursor(it, callLogs, contactMap) }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }

        return callLogs
    }

    override fun saveCallLog(entry: CallLogEntry): Boolean {
        return try {
            // Restoring the same backup twice would otherwise double every call.
            val alreadyLogged = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.DATE} = ? AND ${CallLog.Calls.TYPE} = ?",
                arrayOf(entry.number, entry.date.toString(), entry.type.toString()),
                null
            )?.use { it.moveToFirst() } ?: false
            if (alreadyLogged) return true

            val values = ContentValues().apply {
                put(CallLog.Calls.NUMBER, entry.number)
                put(CallLog.Calls.TYPE, entry.type)
                put(CallLog.Calls.DATE, entry.date)
                put(CallLog.Calls.DURATION, entry.duration)
                // Restored history, not calls waiting to be noticed.
                put(CallLog.Calls.NEW, 0)
            }
            contentResolver.insert(CallLog.Calls.CONTENT_URI, values) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun contactIdFromLookupUri(lookupUri: String): String? {
        return try {
            val segments = Uri.parse(lookupUri).pathSegments
            segments.lastOrNull { it.toLongOrNull() != null } ?: segments.lastOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseCursor(cursor: Cursor, callLogs: MutableList<CallLogEntry>, contactMap: Map<String, Contact>) {
        val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
        val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
        val cachedNameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
        val cachedPhotoIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
        val cachedLookupIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI)
        val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
        val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
        val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
        val presentationIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)

        val labelIdx = cursor.getColumnIndex("phone_account_label")
        val accountIdIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
        val componentNameIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)

        val simCache = mutableMapOf<String, String>()
        val unknownLabel = context.getString(R.string.label_unknown)

        while (cursor.moveToNext()) {
            val callId = cursor.getLong(idIdx)
            val presentation = if (presentationIdx != -1) cursor.getInt(presentationIdx) else CallLog.Calls.PRESENTATION_ALLOWED
            // A withheld or unknown caller has no number to show, call back, save or block: keep it
            // empty and name the row after the reason instead of leaving it blank.
            val hiddenLabel = when {
                presentation == CallLog.Calls.PRESENTATION_RESTRICTED -> context.getString(R.string.label_private_number)
                presentation == CallLog.Calls.PRESENTATION_PAYPHONE -> context.getString(R.string.label_payphone)
                presentation != CallLog.Calls.PRESENTATION_ALLOWED -> context.getString(R.string.label_unknown_number)
                cursor.getString(numberIdx).isNullOrBlank() -> context.getString(R.string.label_unknown_number)
                else -> null
            }
            val number = if (hiddenLabel != null) "" else cursor.getString(numberIdx) ?: unknownLabel
            val type = cursor.getInt(typeIdx)
            val date = cursor.getLong(dateIdx)
            val duration = cursor.getLong(durationIdx)
            
            var simLabel = if (labelIdx != -1) cursor.getString(labelIdx) else null
            
            // REJECTED_TYPE is a call the user declined, not one the block list stopped.
            val isBlocked = type == CallLog.Calls.BLOCKED_TYPE
            
            if (simLabel.isNullOrEmpty() && accountIdIdx != -1 && componentNameIdx != -1) {
                val accountId = cursor.getString(accountIdIdx)
                val componentStr = cursor.getString(componentNameIdx)
                
                if (!accountId.isNullOrEmpty() && !componentStr.isNullOrEmpty()) {
                    simLabel = simCache.getOrPut("$componentStr/$accountId") {
                        try {
                            val componentName = ComponentName.unflattenFromString(componentStr)
                            if (componentName != null) {
                                val handle = PhoneAccountHandle(componentName, accountId)
                                telecomManager?.getPhoneAccount(handle)?.label?.toString() ?: ""
                            } else ""
                        } catch (e: Exception) { "" }
                    }
                }
            }

            if (simLabel?.isEmpty() == true) simLabel = null

            val matchedContact = if (number.isEmpty()) null else contactMap[phoneLookupKey(number)]
            
            val displayName = hiddenLabel ?: matchedContact?.name ?: cursor.getString(cachedNameIdx)
            val photoUri = if (hiddenLabel != null) null else matchedContact?.photoUri ?: cursor.getString(cachedPhotoIdx)
            val contactId = if (hiddenLabel != null) null else matchedContact?.id ?: cursor.getString(cachedLookupIdx)?.let {
                contactIdFromLookupUri(it)
            }

            callLogs.add(
                CallLogEntry(
                    id = callId,
                    number = number,
                    name = displayName ?: number,
                    type = type,
                    date = date,
                    duration = duration,
                    photoUri = photoUri,
                    contactId = contactId,
                    simLabel = simLabel,
                    isBlocked = isBlocked,
                    types = listOf(type),
                    ids = listOf(callId)
                )
            )
        }
    }

    override fun deleteCallLog(number: String) {
        try {
            contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls.NUMBER} = ?",
                arrayOf(number)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun deleteCallLogsByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        try {
            val selection = "${CallLog.Calls._ID} IN (${ids.joinToString(",")})"
            contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun clearCallLogs() {
        try {
            contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
