package dev.satooru.castles.go.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.satooru.castles.go.data.repository.DistanceCalculator
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.ui.common.Labels
import dev.satooru.castles.go.ui.common.LocationPermissionBanner
import dev.satooru.castles.go.ui.common.rememberContainer

private val JAPAN_CENTER = LatLng(36.2, 138.2)

/**
 * 現在地と城のマーカー．表示範囲の城のみを描き，ズームに応じて規模で間引く．
 * マーカーを選ぶと概要を表示し，詳細へ進める．
 */
@Composable
fun MapScreen(viewModel: MapViewModel, onOpenCastle: (String) -> Unit, modifier: Modifier = Modifier) {
    val container = rememberContainer()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val castles by viewModel.castles.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(JAPAN_CENTER, 5f) }
    var movedToUser by remember { mutableStateOf(false) }

    // 最初に現在地が取れたとき，周辺（徒歩圏より広い）に移動する．
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
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = location != null),
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
        LocationPermissionBanner(container.locationRepository, Modifier.align(Alignment.TopCenter))
        selected?.let { castle ->
            Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(castle.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            Labels.prefecture(castle.prefecture),
                            Labels.tenshuStatus(castle.tenshuStatus)?.let { "天守: $it" },
                            location?.let { Labels.distance(DistanceCalculator.meters(it, GeoPoint(castle.lat, castle.lng))) },
                        ).joinToString(" ・ "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { onOpenCastle(castle.id) }) { Text("詳細") }
                }
            }
        }
    }
}
