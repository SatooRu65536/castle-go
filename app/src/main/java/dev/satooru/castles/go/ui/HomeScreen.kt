package dev.satooru.castles.go.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.satooru.castles.go.ui.common.rememberContainer
import dev.satooru.castles.go.ui.list.ListScreen
import dev.satooru.castles.go.ui.list.ListViewModel

private enum class Tab(val label: String) {
    LIST("一覧"),
}

@Composable
fun HomeScreen(onOpenCastle: (String) -> Unit) {
    val container = rememberContainer()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val listVm: ListViewModel = viewModel(factory = viewModelFactory { initializer { ListViewModel(container) } })
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, tab ->
                    NavigationBarItem(selected = selected == i, onClick = { selected = i }, icon = {}, label = { Text(tab.label) })
                }
            }
        },
    ) { padding ->
        when (Tab.entries[selected]) {
            Tab.LIST -> ListScreen(listVm, onOpenCastle, Modifier.padding(padding))
        }
    }
}
