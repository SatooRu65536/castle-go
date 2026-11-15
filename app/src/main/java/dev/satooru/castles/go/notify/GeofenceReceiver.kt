package dev.satooru.castles.go.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dev.satooru.castles.go.CastleGoApplication
import dev.satooru.castles.go.data.repository.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Geofence の出入りを受け取る．城の周囲に入ったら通知し，再登録用の円の外へ出たら登録を作り直す． */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val ids = event.triggeringGeofences.orEmpty().map { it.requestId }
        val here = event.triggeringLocation?.let { GeoPoint(it.latitude, it.longitude) }
        val container = (context.applicationContext as CastleGoApplication).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER ->
                        container.nearbyNotifier.notifyEntered(ids.filter { it != GeofenceManager.REFRESH_ID }, here)
                    Geofence.GEOFENCE_TRANSITION_EXIT ->
                        if (GeofenceManager.REFRESH_ID in ids) container.geofenceManager.refresh(here)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** 再起動やアプリの更新で Geofence の登録は消えるため，作り直す． */
class GeofenceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GeofenceRefreshWorker.enqueue(context)
    }
}
