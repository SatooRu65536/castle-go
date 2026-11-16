package dev.satooru.castles.go.ui.visit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.satooru.castles.go.ui.common.AppCard
import dev.satooru.castles.go.ui.common.AppIconButton
import dev.satooru.castles.go.ui.common.Labels
import dev.satooru.castles.go.ui.theme.PrimaryMuted

/** 訪問記録の一覧．100名城・続100名城の進捗と，訪問した城を新しい順に表示する． */
@Composable
fun VisitsScreen(viewModel: VisitsViewModel, onOpenCastle: (String) -> Unit, onBack: () -> Unit) {
    val visits by viewModel.visits.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(PrimaryMuted)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") {
                Text(
                    "訪問記録",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 52.dp, top = 6.dp, bottom = 4.dp),
                )
            }
            item(key = "progress") {
                AppCard(title = "名城の進捗") {
                    Labels.meijoSelections.forEach { (key, label) ->
                        val row = progress.firstOrNull { it.selection == key }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label)
                            Text("${row?.visited ?: 0} / ${row?.total ?: 0}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (visits.isEmpty()) {
                item(key = "empty") {
                    AppCard { Text("訪問した城はまだありません．城の詳細から「訪問済みにする」を押すと記録されます") }
                }
            } else {
                items(visits, key = { it.castleId }) { v ->
                    AppCard(Modifier.clickable { onOpenCastle(v.castleId) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(v.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(VisitFormat.date(v.visitedAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val place = if (v.country == "JP") Labels.prefecture(v.prefecture) else Labels.country(v.country)
                            if (place != null) {
                                Text(place, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (v.memo.isNotBlank()) {
                                Text(v.memo, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
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
