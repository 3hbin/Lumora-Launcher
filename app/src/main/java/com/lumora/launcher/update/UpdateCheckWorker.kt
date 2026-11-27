package com.lumora.launcher.update

import android.content.Context
import androidx.work.*
import com.lumora.launcher.LumoraApp
import java.util.concurrent.TimeUnit

/**
 * Periodic background check (once per day when enabled).
 * Does not download – only queries the Releases API and updates in-memory state.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = (applicationContext as? LumoraApp)?.updateRepository
                ?: UpdateRepository(applicationContext)
            if (repo.shouldAutoCheck()) {
                repo.checkForUpdate()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK = "lumora_update_check"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }
}
