package dev.satooru.castles.go

import android.app.Application
import dev.satooru.castles.go.notify.GeofenceRefreshWorker
import dev.satooru.castles.go.notify.NearbyNotifier
import dev.satooru.castles.go.sync.SyncWorker

class CastleGoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.schedule(this)
        NearbyNotifier.ensureChannel(this)
        // 背景の通知の Geofence を，最後の現在地で登録し直す．
        GeofenceRefreshWorker.enqueue(this)
    }
}
