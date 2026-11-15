package dev.satooru.castles.go.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
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
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import dev.satooru.castles.go.BuildConfig
import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.data.repository.DistanceCalculator
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.navigation.ExternalNavigation
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
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    // 詳細画面から戻ったときは，離れたときのカメラの位置から再開する．
    val camera = rememberCameraPositionState {
        position = viewModel.savedCamera ?: CameraPosition.fromLatLngZoom(JAPAN_CENTER, 5f)
    }
    DisposableEffect(camera) { onDispose { viewModel.savedCamera = camera.position } }
    val scope = rememberCoroutineScope()
    // 現在地を中心にしている間は true．現在地が変わるたびに中心を追従させ，ユーザーが地図を操作したら false にする．
    var following by remember { mutableStateOf(viewModel.following) }
    LaunchedEffect(following) { viewModel.following = following }
    // 別の画面へ移る前に追従を止める（戻ったときに現在地へ引き戻さない）．
    val openCastle: (String) -> Unit = { id -> following = false; viewModel.following = false; onOpenCastle(id) }
    val openSettings: () -> Unit = { following = false; viewModel.following = false; onOpenSettings() }

    // 最初に現在地が取れたとき，現在地へ移動する（一度だけ．戻ったときは繰り返さない）．
    LaunchedEffect(location) {
        val here = location
        if (here != null && !viewModel.movedToUser) {
            viewModel.movedToUser = true
            camera.move(CameraUpdateFactory.newLatLngZoom(LatLng(here.lat, here.lng), 10f))
            following = true
        }
    }
    LaunchedEffect(camera.isMoving) {
        if (camera.isMoving && camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) following = false
    }
    LaunchedEffect(location, following) {
        val here = location
        if (following && here != null) camera.animate(CameraUpdateFactory.newLatLng(LatLng(here.lat, here.lng)))
    }
    var mapLoaded by remember { mutableStateOf(false) }
    // カメラの移動が止まったとき（`move` のように移動中にならない更新も含む）と，地図の読み込み後に，表示範囲の城を読み直す．
    LaunchedEffect(camera.isMoving, camera.position, mapLoaded) {
        if (camera.isMoving || !mapLoaded) return@LaunchedEffect
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
            onMapLoaded = { mapLoaded = true },
            onMapClick = { viewModel.select(null) },
        ) {
            val context = LocalContext.current
            val renderer = remember { CastlePinRenderer(context) }
            val showAllLabels by remember { derivedStateOf { camera.position.zoom >= MapViewModel.ALL_LABELS_MIN_ZOOM } }
            castles.forEach { castle ->
                key(castle.id) {
                    val isSelected = castle.id == selected?.id
                    // 縮小時は規模の大きい城と選択中の城だけに城名を出す（castle-wiki と同じ）．
                    val label = castle.name.takeIf {
                        showAllLabels || isSelected || castle.scale >= MapViewModel.LABELED_MIN_SCALE
                    }
                    val pin = renderer.image(castle.scale, isSelected, label)
                    Marker(
                        state = rememberMarkerState(position = LatLng(castle.lat, castle.lng)),
                        icon = pin.descriptor,
                        anchor = Offset(pin.anchorU, 0.5f),
                        // 規模の大きい城を手前に重ねる．
                        zIndex = castle.scale + if (isSelected) 10f else 0f,
                        onClick = { viewModel.select(castle); true },
                    )
                }
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
                        following = false
                        scope.launch {
                            camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(n.castle.lat, n.castle.lng), 13f))
                        }
                    },
                )
            }
            if (syncing) {
                CastleDistanceChip(name = "城のデータを更新しています", distance = "", onClick = {})
            } else if (syncError != null) {
                CastleDistanceChip(name = "同期に失敗: $syncError", distance = "", onClick = openSettings)
            }
        }

        // 右上: 設定・現在地へ
        Column(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIconButton(Icons.Filled.Settings, "設定", openSettings)
            if (location != null) {
                AppIconButton(Icons.Filled.MyLocation, "現在地へ移動", {
                    location?.let { here ->
                        scope.launch {
                            following = false
                            camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(here.lat, here.lng), 14f))
                            following = true
                        }
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
            selected?.let { castle -> SelectedCastleCard(castle, location, openCastle) }
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
        val context = LocalContext.current
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("詳細を見る", onClick = { onOpenCastle(castle.id) })
            // 移動手段は Google マップ側で選ぶ．
            PrimaryButton("ここに行く", outlined = true, onClick = {
                ExternalNavigation.start(context, castle.lat, castle.lng)
            })
        }
    }
}
