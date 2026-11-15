package dev.satooru.castles.go.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import dev.satooru.castles.go.BuildConfig
import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.data.repository.DistanceCalculator
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.ui.common.AppCard
import dev.satooru.castles.go.ui.common.AppIconButton
import dev.satooru.castles.go.ui.common.CastleDistanceChip
import dev.satooru.castles.go.ui.common.Labels
import dev.satooru.castles.go.ui.common.LocationPermissionBanner
import dev.satooru.castles.go.ui.common.PrimaryButton
import dev.satooru.castles.go.ui.common.rememberContainer
import kotlinx.coroutines.launch

private val JAPAN_CENTER = LatLng(36.2, 138.2)

/**
 * 最初に表示する画面．全画面の地図に，現在地に近い城 3 件（距離つき）を左上に重ねる．
 * 距離のチップを押すとその城へ移動し，マーカーを選ぶと概要を表示して詳細へ進める．
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onOpenCastle: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = rememberContainer()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val castles by viewModel.castles.collectAsStateWithLifecycle()
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val syncFailed by viewModel.syncFailed.collectAsStateWithLifecycle()
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(JAPAN_CENTER, 5f) }
    val scope = rememberCoroutineScope()
    var movedToUser by remember { mutableStateOf(false) }

    // 最初に現在地が取れたとき，現在地へ移動する．
    LaunchedEffect(location) {
        val here = location
        if (here != null && !movedToUser) {
            movedToUser = true
            camera.move(CameraUpdateFactory.newLatLngZoom(LatLng(here.lat, here.lng), 10f))
        }
    }
    // カメラの移動が止まったら，表示範囲の城を読み直す．
    LaunchedEffect(camera.isMoving) {
        if (camera.isMoving) return@LaunchedEffect
        val bounds = camera.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        viewModel.onViewportChanged(
            MapViewport(
                bounds.southwest.latitude, bounds.northeast.latitude,
                bounds.southwest.longitude, bounds.northeast.longitude,
                MapViewModel.minScaleFor(camera.position.zoom),
            ),
        )
    }

    Box(modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(isMyLocationEnabled = container.locationRepository.hasPermission() && location != null),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
            ),
            onMapClick = { viewModel.select(null) },
        ) {
            castles.forEach { castle ->
                Marker(
                    state = MarkerState(LatLng(castle.lat, castle.lng)),
                    title = castle.name,
                    onClick = { viewModel.select(castle); false },
                )
            }
        }

        // 左上: 近い城 3 件（距離つき）
        Column(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            nearby.forEach { n ->
                CastleDistanceChip(
                    name = n.castle.name,
                    distance = Labels.distance(n.distanceMeters),
                    onClick = {
                        viewModel.select(n.castle)
                        scope.launch {
                            camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(n.castle.lat, n.castle.lng), 13f))
                        }
                    },
                )
            }
            if (syncing) {
                CastleDistanceChip(name = "城のデータを更新しています", distance = "", onClick = {})
            } else if (syncFailed) {
                CastleDistanceChip(name = "同期に失敗しました．設定から再度更新できます", distance = "", onClick = onOpenSettings)
            }
        }

        // 右上: 設定・現在地へ
        Column(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIconButton(Icons.Filled.Settings, "設定", onOpenSettings)
            if (location != null) {
                AppIconButton(Icons.Filled.MyLocation, "現在地へ移動", {
                    location?.let { here ->
                        scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(here.lat, here.lng), 14f)) }
                    }
                })
            }
        }

        if (!BuildConfig.HAS_MAPS_API_KEY) {
            AppCard(Modifier.align(Alignment.Center).padding(24.dp)) {
                Text("地図の API キーが未設定です．local.properties に MAPS_API_KEY を設定して再ビルドしてください", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LocationPermissionBanner(container.locationRepository)
            selected?.let { castle -> SelectedCastleCard(castle, location, onOpenCastle) }
        }
    }
}

@Composable
private fun SelectedCastleCard(castle: CastleEntity, location: GeoPoint?, onOpenCastle: (String) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(castle.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    location?.let { Labels.distance(DistanceCalculator.meters(it, GeoPoint(castle.lat, castle.lng))) },
                    Labels.prefecture(castle.prefecture),
                    Labels.tenshuStatus(castle.tenshuStatus)?.let { "天守: $it" },
                ).joinToString(" ・ "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PrimaryButton("詳細を見る", onClick = { onOpenCastle(castle.id) })
    }
}
