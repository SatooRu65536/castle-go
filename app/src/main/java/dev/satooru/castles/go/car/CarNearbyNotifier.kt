package dev.satooru.castles.go.car

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.car.app.notification.CarAppExtender
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.satooru.castles.go.AppContainer
import dev.satooru.castles.go.R
import dev.satooru.castles.go.data.local.NotifiedCastleEntity
import dev.satooru.castles.go.data.repository.CastleFilter
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.ui.common.Labels
import kotlinx.coroutines.CancellationException

/**
 * 接続中に城へ近づいたら車載に通知する．ジオフェンスは使わず，現在地の更新ごとに端末の `castle` から距離を計算する．
 * 同じ城は 24 時間，通知全体は 10 分に 1 回までに抑える．
 */
class CarNearbyNotifier(private val context: Context, private val container: AppContainer) {
    private var lastNotifiedAt = 0L

    suspend fun run() {
        createChannel()
        container.locationRepository.location.collect { here ->
            if (here != null) {
                try {
                    check(here)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 通知の失敗で車載の画面を落とさない．
                }
            }
        }
    }

    private suspend fun check(here: GeoPoint) {
        val settings = container.settingsRepository.settings.value
        if (!settings.carNotifyEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastNotifiedAt < MIN_INTERVAL_MILLIS) return
        if (!canNotify()) return

        val dao = container.database.notifiedCastleDao()
        dao.deleteOlderThan(now - SAME_CASTLE_MILLIS)
        val already = dao.notifiedSince(now - SAME_CASTLE_MILLIS).toSet()
        val target = container.castleRepository.nearby(
            origin = here,
            filter = CastleFilter(minScale = settings.carNotifyMinScale, unvisitedOnly = settings.carNotifyUnvisitedOnly),
            limit = 10,
            radiusKm = settings.carNotifyDistanceKm,
        ).firstOrNull { it.castle.id !in already } ?: return

        val c = target.castle
        val text = listOfNotNull(
            Labels.distance(target.distanceMeters),
            Labels.tenshuStatus(c.tenshuStatus)?.let { "天守: $it" },
        ).joinToString(" ・ ")
        val open = PendingIntent.getService(
            context, c.id.hashCode(), CarIntents.openCastle(context, c.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(c.name)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .extend(
                CarAppExtender.Builder()
                    .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setContentTitle(c.name)
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(open)
                    .build(),
            )
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        dao.upsert(NotifiedCastleEntity(c.id, now))
        lastNotifiedAt = now
    }

    private fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "近づいた城", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private companion object {
        const val CHANNEL_ID = "car_nearby"
        const val NOTIFICATION_ID = 1001
        const val MIN_INTERVAL_MILLIS = 10L * 60 * 1000
        const val SAME_CASTLE_MILLIS = 24L * 60 * 60 * 1000
    }
}
