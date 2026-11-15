package dev.satooru.castles.go.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import dev.satooru.castles.go.CastleGoApplication
import kotlinx.coroutines.launch

class CastleCarSession : Session() {
    private val container get() = (carContext.applicationContext as CastleGoApplication).container

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                // 接続中は同期しない．
                container.carConnected.set(true)
                val notifier = CarNearbyNotifier(carContext.applicationContext, container)
                owner.lifecycleScope.launch { notifier.run() }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                container.carConnected.set(false)
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen {
        // 通知から起動された場合は，ルート画面の上に城の基本情報を積む．
        CarIntents.castleId(intent)?.let { pushDetail(it) }
        return NearbyCarScreen(carContext, container)
    }

    override fun onNewIntent(intent: Intent) {
        CarIntents.castleId(intent)?.let { pushDetail(it) }
    }

    private fun pushDetail(castleId: String) {
        carContext.getCarService(ScreenManager::class.java)
            .push(CastleCarDetailScreen(carContext, container, castleId))
    }
}
