package dev.satooru.castles.go.ui.common

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.satooru.castles.go.CastleGoApplication
import dev.satooru.castles.go.location.LocationRepository

@Composable
fun rememberContainer() = (LocalContext.current.applicationContext as CastleGoApplication).container

/** 位置情報の権限がないときだけ表示する案内．許可すると [LocationRepository] が取得を始める． */
@Composable
fun LocationPermissionBanner(location: LocationRepository, modifier: Modifier = Modifier) {
    var granted by remember { mutableStateOf(location.hasPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = location.hasPermission()
        location.onPermissionChanged()
    }
    if (granted) return
    AppCard(modifier) {
        Text("現在地を使うと，近い城を距離つきで表示できます", style = MaterialTheme.typography.bodyMedium)
        PrimaryButton("位置情報を許可", onClick = {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        })
    }
}

/** 値が NULL のとき「指定なし」を表すドロップダウン． */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    noneLabel: String? = "指定なし",
) {
    var expanded by remember { mutableStateOf(false) }
    val text = options.firstOrNull { it.first == selected }?.second ?: (noneLabel ?: "")
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            if (noneLabel != null) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onSelect(null); expanded = false })
            }
            options.forEach { (value, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}
