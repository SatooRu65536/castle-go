package dev.satooru.castles.go.ui

import androidx.compose.runtime.Composable
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

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{id}"
    fun detail(id: String) = "detail/$id"
}

@Composable
fun CastleGoNavHost() {
    val nav = rememberNavController()
    val container = rememberContainer()
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onOpenCastle = { nav.navigate(Routes.detail(it)) })
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
