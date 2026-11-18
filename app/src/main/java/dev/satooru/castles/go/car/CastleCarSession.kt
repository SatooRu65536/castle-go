package dev.satooru.castles.go.car

import android.content.Intent
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import dev.satooru.castles.go.CastleGoApplication
import kotlinx.coroutines.launch

class CastleCarSession : Session() {
    private var root: NearbyCarScreen? = null
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

            override fun onStart(owner: LifecycleOwner) {
                // 車載のアプリが前面にあるときは，システムの通知を出さず，地図の上のボタンで知らせる．
                container.nearbyNotifier.inCarAppHandler = { castle, label ->
                    owner.lifecycleScope.launch { showAlert(castle.id, label) }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                container.nearbyNotifier.inCarAppHandler = null
            }

            override fun onDestroy(owner: LifecycleOwner) {
                container.nearbyNotifier.inCarAppHandler = null
                container.carConnected.decrementAndGet()
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen {
        val root = NearbyCarScreen(carContext, container, ::openDetail).also { this.root = it }
        // 通知から起動された場合は，ルート画面を先に積み，返す画面（最上部）を城の基本情報にする．
        // 先に積んだ画面が最下層になるため，逆にすると詳細が根になり，戻る操作が効かない．
        val castleId = CarIntents.castleId(intent) ?: return root
        carContext.getCarService(ScreenManager::class.java).push(root)
        return CastleCarDetailScreen(carContext, container, castleId)
    }

    override fun onNewIntent(intent: Intent) {
        val castleId = CarIntents.castleId(intent) ?: return
        openDetail(castleId)
    }

    /** 通知のたびに積み増さない．ホストの枠は通知から開くと戻るため，ルートの数え方も戻す． */
    private fun openDetail(castleId: String) {
        val screens = carContext.getCarService(ScreenManager::class.java)
        screens.popToRoot()
        (screens.top as? NearbyCarScreen)?.resetQuota()
        pushDetail(castleId)
    }

    /** 周辺画面が最上部なら地図の上のボタンで，詳細などを見ているときは小さなトーストで知らせる． */
    private fun showAlert(castleId: String, label: String) {
        val screens = carContext.getCarService(ScreenManager::class.java)
        val r = root
        if (r != null && screens.top === r) r.showAlert(castleId, label)
        else CarToast.makeText(carContext, label, CarToast.LENGTH_LONG).show()
    }

    private fun pushDetail(castleId: String) {
        carContext.getCarService(ScreenManager::class.java)
            .push(CastleCarDetailScreen(carContext, container, castleId))
    }
}
