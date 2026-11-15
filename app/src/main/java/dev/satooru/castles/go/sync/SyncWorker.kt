package dev.satooru.castles.go.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.satooru.castles.go.CastleGoApplication
import dev.satooru.castles.go.data.repository.SyncResult
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CastleGoApplication).container
        return when (val result = container.syncRepository.sync()) {
            is SyncResult.Success -> Result.success()
            // 車載の接続中で見送った場合は，後で再試行する．
            SyncResult.Skipped -> Result.retry()
            is SyncResult.Failure -> if (result.retryable) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val NAME = "castle-sync"

        /** 1 日 1 回，Wi-Fi・充電時に差分同期する． */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresCharging(true)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
