package dev.satooru.castles.go.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.satooru.castles.go.data.repository.CastleFilter
import dev.satooru.castles.go.data.repository.DistanceCalculator
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.ui.common.CastleRow
import dev.satooru.castles.go.ui.common.DropdownField
import dev.satooru.castles.go.ui.common.Labels
import dev.satooru.castles.go.ui.common.LocationPermissionBanner
import dev.satooru.castles.go.ui.common.rememberContainer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(viewModel: ListViewModel, onOpenCastle: (String) -> Unit, modifier: Modifier = Modifier) {
    val container = rememberContainer()
    val items = viewModel.paged.collectAsLazyPagingItems()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val message by viewModel.syncMessage.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var showFilter by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeSyncMessage() }
    }

    Scaffold(modifier, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("城名・読み仮名で検索") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showFilter = true }) {
                    Text(if (filter.isActive()) "絞り込み中" else "絞り込み")
                }
            }
            LocationPermissionBanner(container.locationRepository)
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "$total 件",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            PullToRefreshBox(isRefreshing = syncing, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                        val castle = items[index] ?: return@items
                        CastleRow(
                            castle = castle,
                            distanceMeters = location?.let { DistanceCalculator.meters(it, GeoPoint(castle.lat, castle.lng)) },
                            onClick = { onOpenCastle(castle.id) },
                        )
                    }
                }
                if (items.itemCount == 0 && total == 0) {
                    Text(
                        "城のデータがありません．下に引いて更新してください",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
            }
        }
    }

    if (showFilter) {
        FilterDialog(
            initial = filter,
            tags = tags.map { it.id to it.name },
            onDismiss = { showFilter = false },
            onApply = { viewModel.setFilter(it); showFilter = false },
        )
    }
}

private fun CastleFilter.isActive() = prefecture != null || minScale > 1 || castleKind != null ||
    tenshuStatus != null || locationType != null || meijoSelection != null || tagId != null || unvisitedOnly

@Composable
private fun FilterDialog(
    initial: CastleFilter,
    tags: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onApply: (CastleFilter) -> Unit,
) {
    var f by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("絞り込み") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField("都道府県", Labels.prefectures, f.prefecture, { f = f.copy(prefecture = it) })
                DropdownField(
                    "規模（以上）", (1..6).map { it to "$it" }, f.minScale.takeIf { it > 1 },
                    { f = f.copy(minScale = it ?: 1) }, noneLabel = "1（すべて）",
                )
                DropdownField("城の種別", Labels.castleKinds, f.castleKind, { f = f.copy(castleKind = it) })
                DropdownField("天守の状態", Labels.tenshuStatuses, f.tenshuStatus, { f = f.copy(tenshuStatus = it) })
                DropdownField("立地", Labels.locationTypes, f.locationType, { f = f.copy(locationType = it) })
                DropdownField("名城の選定", Labels.meijoSelections, f.meijoSelection, { f = f.copy(meijoSelection = it) })
                if (tags.isNotEmpty()) DropdownField("タグ", tags, f.tagId, { f = f.copy(tagId = it) })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("未訪問のみ")
                    Switch(f.unvisitedOnly, { f = f.copy(unvisitedOnly = it) })
                }
            }
        },
        confirmButton = { Button(onClick = { onApply(f) }) { Text("適用") } },
        dismissButton = {
            TextButton(onClick = { onApply(CastleFilter()) }) { Text("クリア") }
        },
    )
}
