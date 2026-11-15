package dev.satooru.castles.go.ui.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.satooru.castles.go.settings.NearbyMode
import dev.satooru.castles.go.ui.common.CastleRow
import dev.satooru.castles.go.ui.common.LocationPermissionBanner
import dev.satooru.castles.go.ui.common.rememberContainer

/** 現在地の周辺の城．設定の「周辺の表示」（上位 N 件，または半径 N km 以内）に従う． */
@Composable
fun NearbyScreen(viewModel: NearbyViewModel, onOpenCastle: (String) -> Unit, modifier: Modifier = Modifier) {
    val container = rememberContainer()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        LocationPermissionBanner(container.locationRepository)
        Text(
            when (settings.nearbyMode) {
                NearbyMode.TOP_N -> "${settings.travelMode.label} ・ 近い順の上位 ${settings.topN} 件"
                NearbyMode.RADIUS -> "${settings.travelMode.label} ・ 半径 ${formatKm(settings.effectiveRadiusKm)} km 以内"
            },
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter.meijoSelection == "top100",
                onClick = { viewModel.filter.value = filter.copy(meijoSelection = if (filter.meijoSelection == "top100") null else "top100") },
                label = { Text("100名城") },
            )
            FilterChip(
                selected = filter.meijoSelection == "continued_top100",
                onClick = { viewModel.filter.value = filter.copy(meijoSelection = if (filter.meijoSelection == "continued_top100") null else "continued_top100") },
                label = { Text("続100名城") },
            )
            FilterChip(
                selected = filter.unvisitedOnly,
                onClick = { viewModel.filter.value = filter.copy(unvisitedOnly = !filter.unvisitedOnly) },
                label = { Text("未訪問") },
            )
            FilterChip(
                selected = filter.minScale >= 4,
                onClick = { viewModel.filter.value = filter.copy(minScale = if (filter.minScale >= 4) 1 else 4) },
                label = { Text("規模4以上") },
            )
        }
        if (location == null) {
            Text("現在地を取得しています．位置情報を許可してください", Modifier.padding(16.dp))
        } else if (items.isEmpty()) {
            Text("周辺に城がありません", Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.castle.id }) { n ->
                CastleRow(n.castle, n.distanceMeters, onClick = { onOpenCastle(n.castle.id) })
            }
        }
    }
}

internal fun formatKm(km: Double): String =
    if (km == km.toInt().toDouble()) km.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", km)
