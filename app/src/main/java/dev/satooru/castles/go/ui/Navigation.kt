package dev.satooru.castles.go.ui

import androidx.compose.runtime.Composable
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

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
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
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val vm: MapViewModel = viewModel(factory = viewModelFactory { initializer { MapViewModel(container) } })
            MapScreen(
                vm,
                onOpenCastle = { nav.navigate(Routes.detail(it)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: DetailViewModel = viewModel(
                key = "detail-$id",
                factory = viewModelFactory { initializer { DetailViewModel(id, container) } },
            )
            DetailScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
