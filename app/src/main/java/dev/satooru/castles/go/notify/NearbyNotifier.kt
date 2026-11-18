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
import androidx.car.app.notification.CarPendingIntent
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
import dev.satooru.castles.go.settings.AppSettings
import dev.satooru.castles.go.ui.common.Labels

/**
 * 城へ近づいたときの通知．次の 3 つの経路から呼ばれ，判定と重複の防止を共有する．
 * - アプリの前面表示中（[notifyIfNear]）
 * - Android Auto の接続中（[notifyIfNear]）
 * - アプリを閉じている間の Geofencing（[notifyEntered]）
 *
 * 同じ城は，一度範囲の外へ出てから設定の間隔（既定 24 時間）が経つまで再通知せず，通知全体は 10 分（同じ城の間隔がそれより短いときはその間隔）に 1 回までに抑える（履歴は Room の `notified_castle`）．
 */
class NearbyNotifier(private val context: Context, private val container: AppContainer) {
    private val notifiedDao = container.database.notifiedCastleDao()
    private val prefs = context.getSharedPreferences("nearby_suppressed", Context.MODE_PRIVATE)
    private var appOpenHandled = false

    /**
     * Android Auto の周辺画面が前面にあるときに，通知の代わりに呼ぶ処理．
     * 設定されている間は，システムの通知（ヘッズアップ）を出さずにこれへ渡す．
     */
    @Volatile
    var inCarAppHandler: ((castle: CastleEntity, label: String) -> Unit)? = null

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
        if (!canNotify(now, settings)) return
        val already = notifiedDao.notifiedSince(now - repeatMillis(settings)).toSet() + suppressedIds()
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
        if (!canNotify(now, settings)) return
        val already = notifiedDao.notifiedSince(now - repeatMillis(settings)).toSet() + suppressedIds()
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

    private suspend fun canNotify(now: Long, settings: AppSettings): Boolean {
        if (!hasNotificationPermission()) return false
        notifiedDao.deleteOlderThan(now - HISTORY_MILLIS)
        val last = notifiedDao.latestNotifiedAt()
        return last == null || now - last >= minOf(MIN_INTERVAL_MILLIS, repeatMillis(settings))
    }

    private suspend fun post(castle: CastleEntity, distanceMeters: Double?, now: Long) {
        // 「名古屋城(規模 6) 1.5km」．距離が分からないときは省く．
        val label = "${castle.name}(規模 ${castle.scale})" +
            distanceMeters?.let { " " + Labels.distance(it).replace(" ", "") }.orEmpty()
        val inCarApp = inCarAppHandler
        if (inCarApp != null) {
            inCarApp(castle, label)
            notifiedDao.upsert(NotifiedCastleEntity(castle.id, now))
            suppress(listOf(castle.id))
            return
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openApp = PendingIntent.getActivity(
            context, castle.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_CASTLE_ID, castle.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags,
        )
        // 車載側の通知は，ホストが必要な extras を足せる CarPendingIntent で開く．
        val openCar = CarPendingIntent.getCarApp(context, castle.id.hashCode(), CarIntents.openCastle(context, castle.id), 0)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_castle)
            .setContentTitle(label)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            // 車載のヘッズアップの表示時間はホストが決める．通知は 30 秒で取り下げる．
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MILLIS)
            .extend(
                CarAppExtender.Builder()
                    .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setContentTitle(label)
                    .setSmallIcon(R.drawable.ic_stat_castle)
                    .setContentIntent(openCar)
                    .addAction(R.drawable.ic_stat_castle, "詳細を開く", openCar)
                    .build(),
            )
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        notifiedDao.upsert(NotifiedCastleEntity(castle.id, now))
        // 通知した城は，範囲の外へ出るまで再通知しない（位置の更新のたびに通知しない）．
        suppress(listOf(castle.id))
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
        private const val NOTIFICATION_TIMEOUT_MILLIS = 30_000L
        private const val KEY_SUPPRESSED = "ids"
        private const val MIN_INTERVAL_MILLIS = 10L * 60 * 1000
        /** 履歴の保持期間．設定で選べる最長の間隔より長くする． */
        private const val HISTORY_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** 設定で選べる「同じ城を再び通知する間隔」（分，表示名） */
        val REPEAT_OPTIONS: List<Pair<Int, String>> = listOf(
            0 to "なし", 5 to "5 分", 10 to "10 分", 30 to "30 分", 60 to "1 時間", 6 * 60 to "6 時間", 24 * 60 to "24 時間", 7 * 24 * 60 to "7 日",
        )

        private fun repeatMillis(settings: AppSettings): Long = settings.carNotifyRepeatMinutes * 60_000L

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "近づいた城", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }
}
