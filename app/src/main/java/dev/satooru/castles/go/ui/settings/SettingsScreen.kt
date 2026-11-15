package dev.satooru.castles.go.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.satooru.castles.go.data.repository.SyncResult
import dev.satooru.castles.go.settings.NearbyMode
import dev.satooru.castles.go.settings.TravelMode
import dev.satooru.castles.go.ui.common.AppCard
import dev.satooru.castles.go.ui.common.AppIconButton
import dev.satooru.castles.go.ui.common.DropdownField
import dev.satooru.castles.go.ui.common.PrimaryButton
import dev.satooru.castles.go.ui.common.rememberContainer
import dev.satooru.castles.go.ui.theme.PrimaryMuted
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = rememberContainer()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val syncState by container.syncRepository.observeState().collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val update = container.settingsRepository::update
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Box(modifier.fillMaxSize().background(PrimaryMuted)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "設定",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 52.dp, top = 6.dp, bottom = 4.dp),
            )

            AppCard(title = "移動手段") {
                DropdownField(
                    "既定の移動手段", TravelMode.entries.map { it to it.label }, settings.travelMode,
                    { m -> if (m != null) update { it.copy(travelMode = m) } }, noneLabel = null,
                )
            }

            AppCard(title = "周辺の表示（Android Auto と共通）") {
                DropdownField(
                    "方式", NearbyMode.entries.map { it to it.label }, settings.nearbyMode,
                    { m -> if (m != null) update { it.copy(nearbyMode = m) } }, noneLabel = null,
                )
                if (settings.nearbyMode == NearbyMode.TOP_N) {
                    DropdownField(
                        "件数 N（Android Auto）", listOf(10, 20, 30, 50, 100).map { it to "$it 件" }, settings.topN,
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
            }

            AppCard(title = "Android Auto の近づいた通知") {
                SwitchRow("通知する", settings.carNotifyEnabled) { v -> update { it.copy(carNotifyEnabled = v) } }
                if (settings.carNotifyEnabled) {
                    DistanceField(
                        value = settings.carNotifyDistanceKm,
                        onValidChange = { d -> update { it.copy(carNotifyDistanceKm = d) } },
                    )
                    DropdownField(
                        "規模の下限", (1..6).map { it to "$it 以上" }, settings.carNotifyMinScale,
                        { s -> if (s != null) update { it.copy(carNotifyMinScale = s) } }, noneLabel = null,
                    )
                    SwitchRow("未訪問の城のみ", settings.carNotifyUnvisitedOnly) { v ->
                        update { it.copy(carNotifyUnvisitedOnly = v) }
                    }
                    if (Build.VERSION.SDK_INT >= 33) {
                        PrimaryButton("通知の権限を許可", outlined = true, onClick = {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        })
                    }
                }
            }

            AppCard(title = "同期") {
                Text(
                    "最終同期: " + (syncState?.lastSyncedAt?.let {
                        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date(it))
                    } ?: "未同期"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("手動で更新", onClick = {
                        scope.launch {
                            status = "更新しています"
                            status = when (val r = container.syncRepository.sync()) {
                                is SyncResult.Success -> "更新しました（${r.updatedCount} 件）"
                                SyncResult.Skipped -> "Android Auto の接続中は更新しません"
                                is SyncResult.Failure -> "更新に失敗しました: ${r.summary}"
                            }
                        }
                    })
                    PrimaryButton("キャッシュを削除", outlined = true, onClick = {
                        scope.launch {
                            container.castleRepository.clearDetailCache()
                            status = "詳細のキャッシュを削除しました"
                        }
                    })
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        AppIconButton(
            Icons.AutoMirrored.Filled.ArrowBack, "戻る", onBack,
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 12.dp),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color.Black,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD1D5DB),
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/**
 * 通知する距離の手入力．0.1 km 単位で 0.1〜10 km まで．範囲外・不正な入力は保存せず，エラーを表示する．
 */
@Composable
private fun DistanceField(value: Double, onValidChange: (Double) -> Unit) {
    var text by remember { mutableStateOf(formatKm(value)) }
    val parsed = parseDistanceKm(text)
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            parseDistanceKm(input)?.let(onValidChange)
        },
        label = { Text("通知する距離（km）") },
        singleLine = true,
        isError = parsed == null,
        supportingText = { Text(if (parsed == null) "0.1〜10 の範囲で，0.1 刻みで入力してください" else "0.1 km 単位，10 km まで") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 0.1 km 単位に丸めた距離．0.1〜10 の範囲外や数値でないときは NULL． */
internal fun parseDistanceKm(input: String): Double? {
    val v = input.trim().toDoubleOrNull() ?: return null
    if (v.isNaN() || v < 0.05 || v > 10.0) return null
    val rounded = Math.round(v * 10) / 10.0
    return rounded.takeIf { it in 0.1..10.0 }
}

private fun formatKm(km: Double): String =
    if (km == km.toInt().toDouble()) km.toInt().toString() else String.format(Locale.ROOT, "%.1f", km)
