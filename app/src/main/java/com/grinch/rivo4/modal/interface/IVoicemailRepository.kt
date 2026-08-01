package com.grinch.rivo4.modal.`interface`

import com.grinch.rivo4.modal.data.Voicemail
import com.grinch.rivo4.modal.data.VoicemailProbeResult
import com.grinch.rivo4.modal.data.VoicemailStatus

interface IVoicemailRepository {
    fun isDefaultDialer(): Boolean

    /** Why voicemail is or is not working right now. */
    fun getStatus(): VoicemailStatus
    fun getVoicemails(): List<Voicemail>
    fun markAsRead(id: Long, isRead: Boolean): Result<Unit>
    fun delete(id: Long): Result<Unit>

    /** Registers the carrier SMS filter on every active SIM. Returns how many accepted it. */
    fun registerSmsFilter(): Result<Int>

    /** Asks each carrier for the mailbox credentials. Requires SEND_SMS. */
    fun requestProvisioning(): List<VoicemailProbeResult>

    /** Runs a sync now and returns how many new messages were imported. */
    fun syncNow(): Result<Int>
}
