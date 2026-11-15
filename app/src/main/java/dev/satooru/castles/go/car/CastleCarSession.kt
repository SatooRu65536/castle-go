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
                container.carConnected.incrementAndGet()
                // 接続中は，現在地の更新ごとに近づいた城を通知する．
                owner.lifecycleScope.launch {
                    container.locationRepository.location.collect { here ->
                        if (here != null) {
                            try {
                                container.nearbyNotifier.notifyIfNear(here)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // 通知の失敗で車載の画面を落とさない．
                            }
                        }
                    }
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                container.carConnected.decrementAndGet()
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
