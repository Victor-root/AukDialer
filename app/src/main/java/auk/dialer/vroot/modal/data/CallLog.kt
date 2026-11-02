package auk.dialer.vroot.modal.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class CallLogEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long,
    val photoUri: String?,
    val contactId: String?,
    val simLabel: String? = null,
    val isBlocked: Boolean = false,
    val types: List<Int> = emptyList(),
    val ids: List<Long> = emptyList()
) {
    val count: Int get() = types.size.coerceAtLeast(1)
}

/**
 * Folds back-to-back calls with the same number on the same day into one row, the newest call's,
 * which carries the types and ids of all of them. A row never spans days: the list files each row
 * under its day, which would hide the earlier calls under a later date. For the recents list only:
 * anything that shows, deletes or saves individual calls works on the calls themselves.
 */
fun List<CallLogEntry>.groupedConsecutive(): List<CallLogEntry> {
    val zone = ZoneId.systemDefault()
    fun dayOf(entry: CallLogEntry): LocalDate = Instant.ofEpochMilli(entry.date).atZone(zone).toLocalDate()
    val grouped = mutableListOf<CallLogEntry>()
    for (call in this) {
        val last = grouped.lastOrNull()
        if (last != null && last.number == call.number && dayOf(last) == dayOf(call)) {
            grouped[grouped.lastIndex] = last.copy(types = last.types + call.types, ids = last.ids + call.ids)
        } else {
            grouped += call
        }
    }
    return grouped
}
