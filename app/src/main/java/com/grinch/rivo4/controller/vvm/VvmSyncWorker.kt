package com.grinch.rivo4.controller.vvm

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Runs [VvmSyncEngine] through WorkManager so the sync survives the triggering
 * service or activity going away, and retries with backoff on transient IMAP
 * failures.
 */
class VvmSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val notify = inputData.getBoolean(KEY_NOTIFY_ON_NEW, false)
        val trigger = inputData.getString(KEY_TRIGGER_LABEL) ?: "unspecified"
        return try {
            val outcomes = VvmSyncEngine(applicationContext).syncAllProvisionedSubscriptions()
            val newCount = outcomes.sumOf { it.writtenNew }
            val anySuccess = outcomes.any { it.success }

            if (notify && newCount > 0) {
                try {
                    VvmNotifier(applicationContext).notifyNewVoicemails(newCount)
                } catch (e: Exception) {
                    Log.w(LOG_TAG, "Notification post failed", e)
                }
            }
            Log.i(LOG_TAG, "Sync finished trigger=$trigger new=$newCount")

            // No provisioned subscription is a success: retrying changes nothing.
            if (outcomes.isEmpty() || anySuccess) {
                Result.success()
            } else if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Worker uncaught error trigger=$trigger", e)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val LOG_TAG = "VvmSyncWorker"

        const val KEY_NOTIFY_ON_NEW = "notify_on_new"
        const val KEY_TRIGGER_LABEL = "trigger_label"

        const val WORK_AUTO_SYNC = "rivo.vvm.sync.auto"
        const val WORK_PERIODIC_SYNC = "rivo.vvm.sync.periodic"

        private const val MAX_RETRY_ATTEMPTS = 4
        private const val INITIAL_BACKOFF_SECONDS = 30L
        private const val PERIODIC_INTERVAL_HOURS = 4L

        /** One-shot sync, used when a carrier SYNC SMS arrives. */
        fun enqueueAutoSync(context: Context, trigger: String) {
            val request = OneTimeWorkRequestBuilder<VvmSyncWorker>()
                .setConstraints(networkConstraints())
                .setInputData(syncInputData(notifyOnNew = true, trigger = trigger))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_AUTO_SYNC, ExistingWorkPolicy.REPLACE, request)
        }

        /** Safety-net sync for carriers whose SYNC SMS never arrives. KEEP so an
         *  already-scheduled request is not rescheduled on every launch. */
        fun ensurePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<VvmSyncWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .setInputData(syncInputData(notifyOnNew = true, trigger = "periodic"))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(WORK_PERIODIC_SYNC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun networkConstraints(): Constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        private fun syncInputData(notifyOnNew: Boolean, trigger: String): Data =
            workDataOf(
                KEY_NOTIFY_ON_NEW to notifyOnNew,
                KEY_TRIGGER_LABEL to trigger,
            )
    }
}
