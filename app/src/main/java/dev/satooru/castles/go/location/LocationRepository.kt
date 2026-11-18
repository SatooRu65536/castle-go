package dev.satooru.castles.go.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 現在地．購読されている間（前面表示中・車載の接続中）のみ取得する．
 * 更新間隔は移動手段に応じて変える．
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationRepository(
    private val context: Context,
    scope: CoroutineScope,
    settings: SettingsRepository,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val permissionTick = MutableStateFlow(0)

    private val _heading = MutableStateFlow<Float?>(null)

    /** 進行方向（真北から時計回りの度）．移動していて方位が得られたときのみ更新し，得られないときは最後の値を保つ． */
    val heading: StateFlow<Float?> = _heading

    val location: StateFlow<GeoPoint?> = combine(
        permissionTick,
        settings.settings.map { it.travelMode.locationIntervalMillis }.distinctUntilChanged(),
    ) { _, interval -> interval }
        .flatMapLatest { interval -> if (hasPermission()) updates(interval) else emptyFlow() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** 権限の許可の結果が変わったときに呼ぶ． */
    fun onPermissionChanged() = permissionTick.update { it + 1 }

    private fun updateHeading(loc: Location) {
        if (loc.hasBearing() && loc.speed >= MIN_HEADING_SPEED) _heading.value = loc.bearing
    }

    @SuppressLint("MissingPermission")
    private fun updates(intervalMillis: Long): Flow<GeoPoint> = callbackFlow {
        try {
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    updateHeading(loc)
                    trySend(GeoPoint(loc.latitude, loc.longitude))
                }
            }
        } catch (e: SecurityException) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMillis)
            .setMinUpdateDistanceMeters(50f)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    updateHeading(it)
                    trySend(GeoPoint(it.latitude, it.longitude))
                }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // 権限が外れた．購読を終える（再度許可されれば onPermissionChanged で作り直す）．
            close()
            return@callbackFlow
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private companion object {
        /** これより遅いと方位が不安定なため，進行方向に使わない（m/s） */
        const val MIN_HEADING_SPEED = 1f
    }
}
