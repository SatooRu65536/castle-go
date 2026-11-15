package dev.satooru.castles.go.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.satooru.castles.go.CastleGoApplication

/** 最後の現在地を中心に，Geofence を登録し直す．起動時・再起動後・同期後に実行する． */
class GeofenceRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as CastleGoApplication).container.geofenceManager.refresh(null)
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "geofence-refresh", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<GeofenceRefreshWorker>().build(),
            )
        }
    }
}
