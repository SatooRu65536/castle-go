package dev.satooru.castles.go

import android.app.Application
import dev.satooru.castles.go.sync.SyncWorker

class CastleGoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.schedule(this)
    }
}
