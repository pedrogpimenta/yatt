package org.yatt.app.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.yatt.app.YattApp

/**
 * Reconciles timer state after push delivery so dropped or delayed messages do not
 * leave the notification stuck until the app is opened again.
 */
class TimerStateSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reason = inputData.getString(KEY_REASON) ?: "unspecified"
        val app = applicationContext as? YattApp
        if (app == null || !app.isContainerReady) {
            Log.w(TAG, "Timer state sync skipped: app container not ready ($reason)")
            return Result.retry()
        }

        return try {
            app.container.timerRepository.refreshTimersAndSyncNotification(
                allowForegroundServiceStart = false
            )
            Log.d(TAG, "Timer state sync completed ($reason)")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Timer state sync failed ($reason)", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "YattTimerSync"
        private const val UNIQUE_WORK_NAME = "timer_state_sync"
        private const val KEY_REASON = "reason"

        fun enqueueImmediate(context: Context, reason: String) {
            val request = OneTimeWorkRequestBuilder<TimerStateSyncWorker>()
                .setInputData(workDataOf(KEY_REASON to reason))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
