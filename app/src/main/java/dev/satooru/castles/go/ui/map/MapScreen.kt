package dev.satooru.castles.go.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import dev.satooru.castles.go.data.local.ScaleMask
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.ui.platform.LocalDensity
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
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.Circle
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

/** 地図の表示できる範囲．日本（与那国島から択捉島まで）と韓国が収まる（castle-wiki の `MAX_BOUNDS` と同じ）． */
private val JAPAN_KOREA_BOUNDS = LatLngBounds(LatLng(24.0, 122.0), LatLng(46.0, 154.0))

/** 縮小の下限．日本と韓国の範囲の横幅（32°）が画面に収まる大きさ． */
private const val MIN_ZOOM = 4f

// 検知範囲の円．青の薄い塗りと，やや濃い縁．
private val DETECTION_FILL = androidx.compose.ui.graphics.Color(0x0D007AFF)
private val DETECTION_STROKE = androidx.compose.ui.graphics.Color(0x55007AFF)
private const val DETECTION_STROKE_WIDTH_PX = 3f

/** 選択中のピンは，通常のピン（zIndex は規模の 1〜6）より手前に置く． */
private const val SELECTED_Z_INDEX = 100f

/** まとめたマーカーは，通常のピン（zIndex は規模の 1〜6）より手前，選択中のピンより奥． */
private const val CLUSTER_Z_INDEX = 20f

/** まとめたマーカーを押したときに拡大するズームの幅． */
private const val CLUSTER_ZOOM_STEP = 2f

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
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
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
    var showScaleFilter by remember { mutableStateOf(false) }
    // 動きが止まったときのカメラの位置．重なる城をまとめ直す契機にする（動いている間は組み直さない）．
    var settledPosition by remember { mutableStateOf(camera.position) }
    // カメラの移動が止まったとき（`move` のように移動中にならない更新も含む）と，地図の読み込み後に，表示範囲の城を読み直す．
    LaunchedEffect(camera.isMoving, camera.position, mapLoaded) {
        if (camera.isMoving) return@LaunchedEffect
        settledPosition = camera.position
        // 地図の中心を，左上の最寄りの城の基準にする．
        camera.position.target.let { viewModel.onCenterChanged(GeoPoint(it.latitude, it.longitude)) }
        if (!mapLoaded) return@LaunchedEffect
        val bounds = camera.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        viewModel.onViewportChanged(
            MapViewport(
                bounds.southwest.latitude, bounds.northeast.latitude,
                bounds.southwest.longitude, bounds.northeast.longitude,
            ),
        )
    }

    Box(modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(
                isMyLocationEnabled = container.locationRepository.hasPermission() && location != null,
                // カメラの中心が，日本と韓国の範囲の外へ出ないようにし，世界全体まで縮小できないようにする．
                latLngBoundsForCameraTarget = JAPAN_KOREA_BOUNDS,
                minZoomPreference = MIN_ZOOM,
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                // 画面の位置を 1 次変換で求めるため，傾き（tilt）は使わない．
                tiltGesturesEnabled = false,
            ),
            onMapLoaded = { mapLoaded = true },
            onMapClick = { viewModel.select(null) },
        ) {
            val context = LocalContext.current
            val renderer = remember { CastlePinRenderer(context) }
            val showAllLabels by remember { derivedStateOf { camera.position.zoom >= MapViewModel.ALL_LABELS_MIN_ZOOM } }
            // 通知の検知範囲．現在地を中心に，通知する距離の円を描く（この円に城が入ると通知する）．
            val here = location
            if (settings.carNotifyEnabled && here != null) {
                Circle(
                    center = LatLng(here.lat, here.lng),
                    radius = settings.carNotifyDistanceKm * 1000,
                    fillColor = DETECTION_FILL,
                    strokeColor = DETECTION_STROKE,
                    strokeWidth = DETECTION_STROKE_WIDTH_PX,
                    zIndex = 0f,
                )
            }
            // 画面上で重なるピンは 1 つにまとめる．動きが止まったとき・表示する城が変わったとき・選択が変わったときだけ組み直す．
            val density = LocalDensity.current.density
            val items = remember(castles, settledPosition, mapLoaded, selected?.id) {
                // 実際の地図の変換は 3 点だけ呼び，あとは 1 次変換で計算する（城が数千件でも遅くならない）．
                val projection = if (mapLoaded) camera.projection else null
                val projector = projection?.let { p ->
                    ScreenProjector.fromSamples(
                        camera.position.target.latitude, camera.position.target.longitude, camera.position.zoom, density,
                    ) { lat, lng -> p.toScreenLocation(LatLng(lat, lng)).let { it.x.toDouble() to it.y.toDouble() } }
                }
                clusterCastles(
                    castles,
                    position = { c -> projector?.toScreen(c.lat, c.lng) },
                    radiusPx = { c -> (renderer.radiusDp(c.scale) + renderer.strokeDp) * density },
                    keepSingleId = selected?.id,
                )
            }
            items.forEach { item ->
                when (item) {
                    is MapItem.Single -> key(item.castle.id) {
                        val castle = item.castle
                        // 通常のピン．選択しても画像を差し替えない（表示中のマーカーの画像を差し替えると，一瞬消えてちらつくことがあるため）．
                        // 縮小時は規模の大きい城だけに城名を出す（castle-wiki と同じ）．
                        val label = castle.name.takeIf { showAllLabels || castle.scale >= MapViewModel.LABELED_MIN_SCALE }
                        val pin = renderer.image(castle.scale, false, label)
                        Marker(
                            state = rememberMarkerState(position = LatLng(castle.lat, castle.lng)),
                            icon = pin.descriptor,
                            anchor = Offset(pin.anchorU, 0.5f),
                            // 規模の大きい城を手前に重ねる．
                            zIndex = castle.scale.toFloat(),
                            onClick = {
                                viewModel.select(castle)
                                // 選んだ城を画面の中央に置く（ズームは変えない）．
                                following = false
                                scope.launch { camera.animate(CameraUpdateFactory.newLatLng(LatLng(castle.lat, castle.lng))) }
                                true
                            },
                        )
                    }
                    is MapItem.Cluster -> key(item.key) {
                        // まとめたマーカー．件数を表示し，押すとその位置を中心に拡大する．
                        val pin = renderer.clusterImage(item.count)
                        Marker(
                            state = rememberMarkerState(key = item.key, position = LatLng(item.lat, item.lng)),
                            icon = pin.descriptor,
                            anchor = Offset(0.5f, 0.5f),
                            zIndex = CLUSTER_Z_INDEX,
                            onClick = {
                                following = false
                                scope.launch {
                                    camera.animate(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(item.lat, item.lng), camera.position.zoom + CLUSTER_ZOOM_STEP),
                                    )
                                }
                                true
                            },
                        )
                    }
                }
            }
            // 選択中のピン．通常のピンの上に別のマーカーとして重ね，赤い円と城名を出す．
            selected?.let { castle ->
                key("selected-${castle.id}") {
                    val pin = renderer.image(castle.scale, true, castle.name)
                    Marker(
                        state = rememberMarkerState(position = LatLng(castle.lat, castle.lng)),
                        icon = pin.descriptor,
                        anchor = Offset(pin.anchorU, 0.5f),
                        zIndex = SELECTED_Z_INDEX,
                        onClick = { true },
                    )
                }
            }
        }

        // 画面の中央の十字．左上の最寄りの城の基準を示す．現在地を追従している間は出さない．
        if (!following) CenterCrosshair(Modifier.align(Alignment.Center))

        // 左上: 地図の中心に近い城 3 件（距離つき）
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
            AppIconButton(Icons.Filled.FilterList, "表示する規模", { showScaleFilter = !showScaleFilter })
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

        // 表示する規模を選ぶパネル．右上のボタンの左に出す．
        if (showScaleFilter) {
            ScaleFilterPanel(
                mask = settings.visibleScaleMask,
                onToggle = { scale ->
                    container.settingsRepository.update { it.copy(visibleScaleMask = ScaleMask.toggle(it.visibleScaleMask, scale)) }
                },
                onSelectAll = { container.settingsRepository.update { it.copy(visibleScaleMask = ScaleMask.ALL) } },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 72.dp),
            )
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
                    Labels.area(castle.country, castle.prefecture, castle.region),
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

/** 画面の中央を示す小さな十字．地図の上でも見えるよう，白い縁取りをつける． */
@Composable
private fun CenterCrosshair(modifier: Modifier = Modifier) {
    Canvas(modifier.size(16.dp)) {
        val half = size.width / 2
        val center = Offset(half, half)
        fun cross(color: androidx.compose.ui.graphics.Color, width: Float) {
            drawLine(color, Offset(center.x - half, center.y), Offset(center.x + half, center.y), strokeWidth = width, cap = StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y - half), Offset(center.x, center.y + half), strokeWidth = width, cap = StrokeCap.Round)
        }
        cross(androidx.compose.ui.graphics.Color.White, 4.dp.toPx())
        cross(androidx.compose.ui.graphics.Color(0xFF1C1917), 1.5.dp.toPx())
    }
}

/** 地図に表示する規模（1〜6）を選ぶパネル．規模ごとに表示・非表示を切り替える．すべてを非表示にはできない． */
@Composable
private fun ScaleFilterPanel(
    mask: Int,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.width(264.dp), title = "表示する規模") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..6).forEach { scale ->
                val on = ScaleMask.contains(mask, scale)
                Box(
                    Modifier.size(34.dp)
                        .background(if (on) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, androidx.compose.ui.graphics.Color(0xFFD1D5DB), RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggle(scale) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$scale",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black,
                    )
                }
            }
        }
        if (mask != ScaleMask.ALL) PrimaryButton("すべて表示", outlined = true, onClick = onSelectAll)
    }
}
