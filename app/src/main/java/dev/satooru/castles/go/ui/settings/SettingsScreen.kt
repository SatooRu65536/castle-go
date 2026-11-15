package dev.satooru.castles.go.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.satooru.castles.go.data.repository.SyncResult
import dev.satooru.castles.go.settings.NearbyMode
import dev.satooru.castles.go.settings.TravelMode
import dev.satooru.castles.go.ui.common.DropdownField
import dev.satooru.castles.go.ui.common.SectionTitle
import dev.satooru.castles.go.ui.common.rememberContainer
import dev.satooru.castles.go.ui.nearby.formatKm
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val container = rememberContainer()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val syncState by container.syncRepository.observeState().collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val update = container.settingsRepository::update
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("移動手段")
        DropdownField(
            "既定の移動手段", TravelMode.entries.map { it to it.label }, settings.travelMode,
            { m -> if (m != null) update { it.copy(travelMode = m) } }, noneLabel = null,
        )

        SectionTitle("周辺の表示（スマートフォン・Android Auto 共通）")
        DropdownField(
            "方式", NearbyMode.entries.map { it to it.label }, settings.nearbyMode,
            { m -> if (m != null) update { it.copy(nearbyMode = m) } }, noneLabel = null,
        )
        if (settings.nearbyMode == NearbyMode.TOP_N) {
            DropdownField(
                "件数 N", listOf(10, 20, 30, 50, 100).map { it to "$it 件" }, settings.topN,
                { n -> if (n != null) update { it.copy(topN = n) } }, noneLabel = null,
            )
        } else {
            DropdownField(
                "半径 N km",
                listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0).map { it to "${formatKm(it)} km" },
                settings.radiusKm,
                { r -> update { it.copy(radiusKm = r) } },
                noneLabel = "移動手段の既定値（${formatKm(settings.travelMode.defaultRadiusKm)} km）",
            )
        }

        SectionTitle("Android Auto の近づいた通知")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("通知する")
            Switch(settings.carNotifyEnabled, { v -> update { it.copy(carNotifyEnabled = v) } })
        }
        if (settings.carNotifyEnabled) {
            DropdownField(
                "通知する距離", listOf(0.5, 1.0, 2.0, 5.0).map { it to "${formatKm(it)} km" }, settings.carNotifyDistanceKm,
                { d -> if (d != null) update { it.copy(carNotifyDistanceKm = d) } }, noneLabel = null,
            )
            DropdownField(
                "規模の下限", (1..6).map { it to "$it 以上" }, settings.carNotifyMinScale,
                { s -> if (s != null) update { it.copy(carNotifyMinScale = s) } }, noneLabel = null,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("未訪問の城のみ")
                Switch(settings.carNotifyUnvisitedOnly, { v -> update { it.copy(carNotifyUnvisitedOnly = v) } })
            }
            if (Build.VERSION.SDK_INT >= 33) {
                OutlinedButton(onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("通知の権限を許可")
                }
            }
        }

        SectionTitle("同期")
        Text(
            "最終同期: " + (syncState?.lastSyncedAt?.let {
                SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date(it))
            } ?: "未同期"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    status = "更新しています"
                    status = when (val r = container.syncRepository.sync()) {
                        is SyncResult.Success -> "更新しました（${r.updatedCount} 件）"
                        SyncResult.Skipped -> "Android Auto の接続中は更新しません"
                        is SyncResult.Failure -> "更新に失敗しました"
                    }
                }
            }) { Text("手動で更新") }
            OutlinedButton(onClick = {
                scope.launch {
                    container.castleRepository.clearDetailCache()
                    status = "詳細のキャッシュを削除しました"
                }
            }) { Text("キャッシュを削除") }
        }
        status?.let { Text(it) }
    }
}
