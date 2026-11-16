package dev.satooru.castles.go.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.satooru.castles.go.ui.theme.PrimaryMuted
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.satooru.castles.go.ui.common.rememberContainer
import dev.satooru.castles.go.ui.detail.DetailScreen
import dev.satooru.castles.go.ui.detail.DetailViewModel
import dev.satooru.castles.go.ui.map.MapScreen
import dev.satooru.castles.go.ui.map.MapViewModel
import dev.satooru.castles.go.ui.settings.SettingsScreen
import dev.satooru.castles.go.ui.visit.VisitsScreen
import dev.satooru.castles.go.ui.visit.VisitsViewModel

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val VISITS = "visits"
    const val DETAIL = "detail/{id}"
    fun detail(id: String) = "detail/$id"
}

@Composable
fun CastleGoNavHost(openCastleId: String? = null, onCastleOpened: () -> Unit = {}) {
    val nav = rememberNavController()
    val container = rememberContainer()

    // 前面表示中は，現在地の更新ごとに近づいた城を通知する．背景の Geofence の登録も，大きく動いたときに作り直す．
    val location by container.locationRepository.location.collectAsStateWithLifecycle()
    LaunchedEffect(location) {
        val here = location ?: return@LaunchedEffect
        runCatching {
            container.nearbyNotifier.onAppOpened(here)
            container.nearbyNotifier.notifyIfNear(here)
            container.geofenceManager.refreshIfMoved(here)
        }
    }
    // 通知から開かれたときは，その城の詳細を表示する．
    LaunchedEffect(openCastleId) {
        if (openCastleId != null) {
            nav.navigate(Routes.detail(openCastleId))
            onCastleOpened()
        }
    }
    // 地図は常に表示したままにし，詳細・設定は上に重ねる．
    // 別の画面へ遷移するたびに地図を作り直すと，タイルの再読み込みでちらつくため．
    val mapViewModel: MapViewModel = viewModel(factory = viewModelFactory { initializer { MapViewModel(container) } })
    Box(Modifier.fillMaxSize()) {
        MapScreen(
            mapViewModel,
            onOpenCastle = { nav.navigate(Routes.detail(it)) },
            onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            onOpenVisits = { nav.navigate(Routes.VISITS) },
        )
        NavHost(
            nav,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(150)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(150)) },
            popExitTransition = { fadeOut(tween(150)) },
        ) {
            // 地図そのものは上で表示している．ここは何も重ねない．
            composable(Routes.HOME) {}
            composable(Routes.SETTINGS) {
                OverlayScreen { SettingsScreen(onBack = { nav.popBackStack() }) }
            }
            composable(Routes.VISITS) {
                val vm: VisitsViewModel = viewModel(factory = viewModelFactory { initializer { VisitsViewModel(container) } })
                OverlayScreen {
                    VisitsScreen(vm, onOpenCastle = { nav.navigate(Routes.detail(it)) }, onBack = { nav.popBackStack() })
                }
            }
            composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val vm: DetailViewModel = viewModel(
                    key = "detail-$id",
                    factory = viewModelFactory { initializer { DetailViewModel(id, container) } },
                )
                OverlayScreen { DetailScreen(vm, onBack = { nav.popBackStack() }) }
            }
        }
    }
}

/** 地図の上に重ねる画面．背景を塗り，下の地図へ操作が届かないようにする（Surface は操作を受け止める）． */
@Composable
private fun OverlayScreen(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxSize(),
        color = PrimaryMuted,
        // 背景色から文字色が自動で選ばれ，灰色になるのを避ける．
        contentColor = MaterialTheme.colorScheme.onSurface,
        content = content,
    )
}
