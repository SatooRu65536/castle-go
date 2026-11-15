package dev.satooru.castles.go.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import dev.satooru.castles.go.ui.common.AppCard
import dev.satooru.castles.go.ui.common.MarkdownText
import dev.satooru.castles.go.ui.theme.Ally
import dev.satooru.castles.go.ui.common.AppIconButton
import dev.satooru.castles.go.ui.common.PrimaryButton
import dev.satooru.castles.go.ui.theme.AllyMuted
import dev.satooru.castles.go.ui.theme.PrimaryMuted
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.satooru.castles.go.navigation.ExternalNavigation
import dev.satooru.castles.go.settings.TravelMode
import dev.satooru.castles.go.ui.common.DropdownField
import dev.satooru.castles.go.ui.common.rememberContainer
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
fun DetailScreen(viewModel: DetailViewModel, onBack: () -> Unit) {
    val castle by viewModel.castle.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(PrimaryMuted)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val c = castle
            if (c == null) {
                Text("この城は端末にありません", Modifier.statusBarsPadding().padding(start = 72.dp, top = 24.dp))
            } else {
                if (c.primaryPhotoId != null) {
                    AsyncImage(
                        model = PhotoUrl.of(c.primaryPhotoId, 1024),
                        contentDescription = c.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                    )
                } else {
                    Box(Modifier.statusBarsPadding().height(56.dp))
                }
                Column(
                    Modifier.padding(16.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppCard {
                        Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        if (c.nameKana.isNotEmpty()) {
                            Text(c.nameKana, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        location?.let {
                            Text(
                                "現在地から ${Labels.distance(DistanceCalculator.meters(it, GeoPoint(c.lat, c.lng)))}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        BasicInfo(c)
                        if (tags.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                tags.forEach { TagChip(it.name) }
                            }
                        }
                    }
                    NavigationCard(c)
                    when (val st = state) {
                        DetailUiState.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                        DetailUiState.Gone -> AppCard { Text("この城は削除されました") }
                        DetailUiState.Failed -> AppCard {
                            Text("詳細を取得できませんでした．通信を確認してください")
                            PrimaryButton("再試行", onClick = viewModel::load)
                        }
                        is DetailUiState.Loaded -> DetailContent(st.detail)
                    }
                }
            }
        }
        AppIconButton(
            Icons.AutoMirrored.Filled.ArrowBack, "戻る", onBack,
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 12.dp),
        )
    }
}

/** 外部のナビアプリへ目的地を引き継ぐ．移動手段の初期値は設定の既定の移動手段． */
@Composable
private fun NavigationCard(c: CastleEntity) {
    val context = LocalContext.current
    val container = rememberContainer()
    var mode by remember { mutableStateOf(container.settingsRepository.settings.value.travelMode) }
    AppCard(title = "ナビ") {
        DropdownField(
            "移動手段", TravelMode.entries.map { it to it.label }, mode,
            { m -> if (m != null) mode = m }, noneLabel = null,
        )
        PrimaryButton("ナビを開く", onClick = { ExternalNavigation.start(context, c.lat, c.lng, mode) })
    }
}

@Composable
private fun TagChip(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(AllyMuted, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
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
    if (detail.body.isNotBlank()) {
        AppCard(title = "解説") { MarkdownText(detail.body) }
    }
    AttributeFormatter.sections(detail.attributes).forEach { section ->
        AppCard(title = section.title) {
            section.rows.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    row.lines.forEach { line -> AttributeLineText(line) }
                }
            }
        }
    }
}

@Composable
private fun AttributeLineText(line: AttributeLine) {
    if (line.url == null) {
        Text(line.text, style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(
            buildAnnotatedString {
                withLink(
                    LinkAnnotation.Url(line.url, TextLinkStyles(SpanStyle(color = Ally, textDecoration = TextDecoration.Underline))),
                ) { append(line.text) }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
