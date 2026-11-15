package dev.satooru.castles.go.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.data.remote.PhotoUrl
import dev.satooru.castles.go.data.repository.CastleDetail
import dev.satooru.castles.go.data.repository.DistanceCalculator
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.ui.common.Labels
import dev.satooru.castles.go.ui.common.SectionTitle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: DetailViewModel, onBack: () -> Unit) {
    val castle by viewModel.castle.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(castle?.name ?: "城の詳細") },
                navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            val c = castle
            if (c == null) {
                Text("この城は端末にありません", Modifier.padding(16.dp))
                return@Column
            }
            c.primaryPhotoId?.let {
                AsyncImage(
                    model = PhotoUrl.of(it, 1024),
                    contentDescription = c.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.name, style = MaterialTheme.typography.headlineSmall)
                if (c.nameKana.isNotEmpty()) Text(c.nameKana, style = MaterialTheme.typography.bodyMedium)
                location?.let {
                    Text(
                        "現在地から ${Labels.distance(DistanceCalculator.meters(it, GeoPoint(c.lat, c.lng)))}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                BasicInfo(c)
                if (tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { AssistChip(onClick = {}, label = { Text(it.name) }) }
                    }
                }
                HorizontalDivider()
                when (val s = state) {
                    DetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    DetailUiState.Gone -> Text("この城は削除されました")
                    DetailUiState.Failed -> {
                        Text("詳細を取得できませんでした．通信を確認してください")
                        Button(onClick = viewModel::load) { Text("再試行") }
                    }
                    is DetailUiState.Loaded -> DetailContent(s.detail)
                }
            }
        }
    }
}

@Composable
private fun BasicInfo(c: CastleEntity) {
    val rows = listOfNotNull(
        Labels.prefecture(c.prefecture)?.let { "都道府県" to it },
        "規模" to "${c.scale}",
        Labels.castleKind(c.castleKind)?.let { "城の種別" to it },
        Labels.locationType(c.locationType)?.let { "立地" to it },
        Labels.tenshuStatus(c.tenshuStatus)?.let { "天守" to it },
        Labels.meijoSelection(c.meijoSelection)?.let { sel -> "名城の選定" to (sel + (c.meijoNumber?.let { " No.$it" } ?: "")) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { (k, v) ->
            Row { Text(k, Modifier.padding(end = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(v) }
        }
    }
}

@Composable
private fun DetailContent(detail: CastleDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (detail.body.isNotBlank()) {
            SectionTitle("解説")
            Text(detail.body, style = MaterialTheme.typography.bodyMedium)
        }
        val attrs = detail.attributes.mapNotNull { (key, value) -> attributeText(value)?.let { key to it } }
        if (attrs.isNotEmpty()) {
            SectionTitle("属性")
            attrs.forEach { (k, v) ->
                Row { Text(k, Modifier.padding(end = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(v) }
            }
        }
    }
}

/** `{ "state": "known", "value": ... }` を文字列にする．不明は「不明」，読めない形は NULL． */
internal fun attributeText(element: JsonElement): String? {
    val obj = element as? JsonObject ?: return null
    return when ((obj["state"] as? JsonPrimitive)?.contentOrNull) {
        "unknown" -> "不明"
        "known" -> obj["value"]?.let(::valueText)
        else -> null
    }
}

private fun valueText(v: JsonElement): String? = when (v) {
    is JsonNull -> null
    is JsonPrimitive -> v.contentOrNull
    is JsonArray -> v.mapNotNull(::valueText).joinToString("，")
    is JsonObject -> (v["label"] as? JsonPrimitive)?.contentOrNull
        ?: v.values.mapNotNull(::valueText).joinToString(" ")
}
