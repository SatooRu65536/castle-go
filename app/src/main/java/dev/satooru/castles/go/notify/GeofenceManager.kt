package dev.satooru.castles.go.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dev.satooru.castles.go.AppContainer
import dev.satooru.castles.go.data.repository.CastleFilter
import dev.satooru.castles.go.data.repository.DistanceCalculator
import dev.satooru.castles.go.data.repository.GeoPoint
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/**
 * アプリを閉じている間の「近づいた通知」．城の周囲に Geofence（円）を登録し，入ったときに通知する．
 *
 * Geofence は 1 アプリ 100 件までのため，現在地に近い城（条件に合うもの）を最大 [MAX_CASTLE_FENCES] 件だけ登録する．
 * あわせて現在地を中心とする「再登録用」の円を置き，その外へ出たら近い城を選び直す．
 * 再起動やアプリの更新で登録は消えるため，その後に作り直す．
 */
class GeofenceManager(private val context: Context, private val container: AppContainer) {
    private val client = LocationServices.getGeofencingClient(context)
    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val prefs = context.getSharedPreferences("geofence", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    /** 背景の位置情報の権限（Android 10 以上は「常に許可」）が許可されているか． */
    fun hasBackgroundPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    private fun isEnabled(): Boolean {
        val s = container.settingsRepository.settings.value
        return s.carNotifyEnabled && s.backgroundNotifyEnabled && hasBackgroundPermission()
    }

    /** 前回の登録の中心から 1 km 以上動いたとき，または未登録のときに登録し直す． */
    suspend fun refreshIfMoved(here: GeoPoint) {
        if (!isEnabled()) return
        val last = lastCenter()
        if (last == null || DistanceCalculator.meters(last, here) >= REREGISTER_METERS) refresh(here)
    }

    /** 設定が変わったとき・起動時・Geofence の外へ出たときに，登録を作り直す．[center] が NULL なら最後の現在地を使う． */
    @SuppressLint("MissingPermission")
    suspend fun refresh(center: GeoPoint? = null) = mutex.withLock {
        if (!isEnabled()) {
            clearInternal()
            return@withLock
        }
        val origin = center ?: lastKnownLocation() ?: return@withLock
        val settings = container.settingsRepository.settings.value
        val castles = container.castleRepository.nearby(
            origin = origin,
            filter = CastleFilter(minScale = settings.carNotifyMinScale, unvisitedOnly = settings.carNotifyUnvisitedOnly),
            limit = MAX_CASTLE_FENCES,
            radiusKm = null,
        )
        val radius = max(MIN_RADIUS_METERS, (settings.carNotifyDistanceKm * 1000).toFloat())
        val fences = castles.map {
            Geofence.Builder()
                .setRequestId(it.castle.id)
                .setCircularRegion(it.castle.lat, it.castle.lng, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        } + Geofence.Builder()
            .setRequestId(REFRESH_ID)
            .setCircularRegion(origin.lat, origin.lng, REFRESH_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(fences)
            .build()
        try {
            client.removeGeofences(pendingIntent()).awaitTask()
            client.addGeofences(request, pendingIntent()).awaitTask()
            prefs.edit().putFloat(KEY_LAT, origin.lat.toFloat()).putFloat(KEY_LNG, origin.lng.toFloat()).apply()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Geofence の登録に失敗: $e", e)
        }
    }

    suspend fun clear() = mutex.withLock { clearInternal() }

    private suspend fun clearInternal() {
        try {
            client.removeGeofences(pendingIntent()).awaitTask()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Geofence の解除に失敗: $e", e)
        }
        prefs.edit().clear().apply()
    }

    private fun lastCenter(): GeoPoint? =
        if (prefs.contains(KEY_LAT)) GeoPoint(prefs.getFloat(KEY_LAT, 0f).toDouble(), prefs.getFloat(KEY_LNG, 0f).toDouble()) else null

    @SuppressLint("MissingPermission")
    private suspend fun lastKnownLocation(): GeoPoint? = try {
        fused.lastLocation.awaitTask()?.let { GeoPoint(it.latitude, it.longitude) }
    } catch (e: SecurityException) {
        null
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, GeofenceReceiver::class.java),
        // Geofencing は，位置の情報を Intent に足して送るため変更できる PendingIntent が要る．
        PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0,
    )

    companion object {
        const val REFRESH_ID = "__refresh__"
        private const val TAG = "GeofenceManager"
        private const val MAX_CASTLE_FENCES = 90
        private const val MIN_RADIUS_METERS = 100f
        private const val REFRESH_RADIUS_METERS = 2000f
        private const val REREGISTER_METERS = 1000.0
        private const val KEY_LAT = "lat"
        private const val KEY_LNG = "lng"
    }
}
