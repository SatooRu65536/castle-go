package dev.satooru.castles.go.ui.visit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.satooru.castles.go.data.local.VisitEntity
import dev.satooru.castles.go.ui.common.AppCard
import dev.satooru.castles.go.ui.common.PrimaryButton
import java.util.TimeZone

/** 詳細画面の訪問記録．未訪問なら「訪問済みにする」，訪問済みなら訪問日とメモを編集できる． */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitCard(
    visit: VisitEntity?,
    onMark: () -> Unit,
    onUnmark: () -> Unit,
    onChange: (VisitEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier, title = "訪問記録") {
        if (visit == null) {
            Text("まだ訪問していません")
            PrimaryButton("訪問済みにする", onClick = onMark)
            return@AppCard
        }
        var showPicker by remember { mutableStateOf(false) }
        // メモは入力のたびに保存する．別の訪問記録に切り替わったときは入力欄を作り直す．
        var memo by remember(visit.castleId) { mutableStateOf(visit.memo) }

        Text("訪問日: ${VisitFormat.date(visit.visitedAt)}")
        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it; onChange(visit.copy(memo = it)) },
            label = { Text("メモ") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("訪問日を変更", onClick = { showPicker = true }, outlined = true)
            PrimaryButton("訪問済みを取り消す", onClick = onUnmark, outlined = true)
        }

        if (showPicker) {
            // DatePicker は UTC の 0 時で日付を扱う．端末のタイムゾーンの日付に合わせて変換する．
            val offset = TimeZone.getDefault().getOffset(visit.visitedAt)
            val state = rememberDatePickerState(initialSelectedDateMillis = visit.visitedAt + offset)
            DatePickerDialog(
                onDismissRequest = { showPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { utc ->
                            // 選んだ日の，端末のタイムゾーンでの 0 時にする．
                            onChange(visit.copy(visitedAt = utc - TimeZone.getDefault().getOffset(utc)))
                        }
                        showPicker = false
                    }) { Text("決定") }
                },
                dismissButton = { TextButton(onClick = { showPicker = false }) { Text("キャンセル") } },
            ) { DatePicker(state) }
        }
    }
}

private fun Modifier.fillMaxWidth(): Modifier = this.then(Modifier.fillMaxWidth())
