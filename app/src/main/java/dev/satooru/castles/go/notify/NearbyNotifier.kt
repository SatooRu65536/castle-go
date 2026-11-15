package dev.satooru.castles.go.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.car.app.notification.CarAppExtender
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.satooru.castles.go.AppContainer
import dev.satooru.castles.go.MainActivity
import dev.satooru.castles.go.R
import dev.satooru.castles.go.car.CarIntents
import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.data.local.NotifiedCastleEntity
import dev.satooru.castles.go.data.repository.CastleFilter
import dev.satooru.castles.go.data.repository.DistanceCalculator
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.ui.common.Labels

/**
 * 城へ近づいたときの通知．次の 3 つの経路から呼ばれ，判定と重複の防止を共有する．
 * - アプリの前面表示中（[notifyIfNear]）
 * - Android Auto の接続中（[notifyIfNear]）
 * - アプリを閉じている間の Geofencing（[notifyEntered]）
 *
 * 同じ城は 24 時間，通知全体は 10 分に 1 回までに抑える（履歴は Room の `notified_castle`）．
 */
class NearbyNotifier(private val context: Context, private val container: AppContainer) {
    private val notifiedDao = container.database.notifiedCastleDao()
    private val prefs = context.getSharedPreferences("nearby_suppressed", Context.MODE_PRIVATE)
    private var appOpenHandled = false

    /** 通知しない城の ID．範囲の外へ出たら外す． */
    private fun suppressedIds(): Set<String> = prefs.getStringSet(KEY_SUPPRESSED, emptySet()).orEmpty().toSet()

    /** すでに範囲内にいる城を，範囲の外へ出るまで通知しない対象にする． */
    fun suppress(ids: Collection<String>) {
        if (ids.isEmpty()) return
        prefs.edit().putStringSet(KEY_SUPPRESSED, suppressedIds() + ids).apply()
    }

    /** 範囲の外へ出た城を，通知の対象に戻す． */
    fun release(ids: Collection<String>) {
        val current = suppressedIds()
        if (current.none { it in ids }) return
        prefs.edit().putStringSet(KEY_SUPPRESSED, current - ids.toSet()).apply()
    }

    /** アプリを開いた時点（プロセスごとに最初の現在地）で範囲内にある城を，範囲の外へ出るまで通知しない． */
    suspend fun onAppOpened(here: GeoPoint) {
        if (appOpenHandled) return
        appOpenHandled = true
        val inRange = inRangeIds(here)
        suppress(inRange)
    }

    private suspend fun inRangeIds(here: GeoPoint): Set<String> {
        val settings = container.settingsRepository.settings.value
        return container.castleRepository.nearby(
            origin = here,
            filter = CastleFilter(),
            limit = 200,
            radiusKm = settings.carNotifyDistanceKm,
        ).map { it.castle.id }.toSet()
    }

    /** 現在地から通知する距離以内に，条件に合う城があれば通知する． */
    suspend fun notifyIfNear(here: GeoPoint) {
        val settings = container.settingsRepository.settings.value
        if (!settings.carNotifyEnabled) return
        val now = System.currentTimeMillis()
        // 範囲の外へ出た城は，通知の対象に戻す．
        val suppressed = suppressedIds()
        if (suppressed.isNotEmpty()) {
            val inRange = inRangeIds(here)
            release(suppressed.filter { it !in inRange })
        }
        if (!canNotify(now)) return
        val already = notifiedDao.notifiedSince(now - SAME_CASTLE_MILLIS).toSet() + suppressedIds()
        val target = container.castleRepository.nearby(
            origin = here,
            filter = CastleFilter(minScale = settings.carNotifyMinScale, unvisitedOnly = settings.carNotifyUnvisitedOnly),
            limit = 10,
            radiusKm = settings.carNotifyDistanceKm,
        ).firstOrNull { it.castle.id !in already } ?: return
        post(target.castle, target.distanceMeters, now)
    }

    /** Geofencing が「城の周囲に入った」と伝えた城のうち，条件に合う最初の 1 件を通知する． */
    suspend fun notifyEntered(castleIds: List<String>, here: GeoPoint?) {
        val settings = container.settingsRepository.settings.value
        if (!settings.carNotifyEnabled || !settings.backgroundNotifyEnabled) return
        val now = System.currentTimeMillis()
        if (!canNotify(now)) return
        val already = notifiedDao.notifiedSince(now - SAME_CASTLE_MILLIS).toSet() + suppressedIds()
        for (id in castleIds) {
            if (id in already) continue
            val castle = container.castleRepository.findCastle(id) ?: continue
            if (castle.deletedAt != null || castle.scale < settings.carNotifyMinScale) continue
            if (settings.carNotifyUnvisitedOnly && container.database.visitDao().countOf(id) > 0) continue
            val distance = here?.let { DistanceCalculator.meters(it, GeoPoint(castle.lat, castle.lng)) }
            post(castle, distance, now)
            return
        }
    }

    private suspend fun canNotify(now: Long): Boolean {
        if (!hasNotificationPermission()) return false
        notifiedDao.deleteOlderThan(now - SAME_CASTLE_MILLIS)
        val last = notifiedDao.latestNotifiedAt()
        return last == null || now - last >= MIN_INTERVAL_MILLIS
    }

    private suspend fun post(castle: CastleEntity, distanceMeters: Double?, now: Long) {
        val text = listOfNotNull(
            distanceMeters?.let { Labels.distance(it) },
            Labels.tenshuStatus(castle.tenshuStatus)?.let { "天守: $it" },
        ).joinToString(" ・ ")
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openApp = PendingIntent.getActivity(
            context, castle.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_CASTLE_ID, castle.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags,
        )
        val openCar = PendingIntent.getService(context, castle.id.hashCode(), CarIntents.openCastle(context, castle.id), flags)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${castle.name} が近くにあります")
            .setContentText(text)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .extend(
                CarAppExtender.Builder()
                    .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setContentTitle(castle.name)
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(openCar)
                    .build(),
            )
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        notifiedDao.upsert(NotifiedCastleEntity(castle.id, now))
    }

    private fun hasNotificationPermission(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    companion object {
        const val CHANNEL_ID = "nearby_castle"
        private const val NOTIFICATION_ID = 1001
        private const val KEY_SUPPRESSED = "ids"
        private const val MIN_INTERVAL_MILLIS = 10L * 60 * 1000
        private const val SAME_CASTLE_MILLIS = 24L * 60 * 60 * 1000

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "近づいた城", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }
}
