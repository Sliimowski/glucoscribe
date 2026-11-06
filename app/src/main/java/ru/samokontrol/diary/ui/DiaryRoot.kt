package ru.samokontrol.diary.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.samokontrol.diary.R
import ru.samokontrol.diary.ui.screens.AddEntryDialog
import ru.samokontrol.diary.ui.screens.ChartScreen
import ru.samokontrol.diary.ui.screens.DiaryScreen
import ru.samokontrol.diary.ui.screens.ReportScreen
import ru.samokontrol.diary.ui.voice.VoiceEntryFlow
import ru.samokontrol.diary.voice.VoiceShortcuts

private data class Tab(val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryRoot(viewModel: DiaryViewModel = viewModel()) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    // Не rememberSaveable: после поворота экрана запись не должна начинаться заново.
    var voiceStartKey by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val entries by viewModel.entries.collectAsState()
    val unit by viewModel.unit.collectAsState()
    val tabs = remember {
        listOf(
            Tab("Дневник", Icons.Default.List),
            Tab("График", Icons.Default.DateRange),
            Tab("Отчёт", Icons.Default.Info),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tabs[selectedTab].title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { VoiceShortcuts.pin(context) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_pin_home),
                                contentDescription = stringResource(R.string.pin_shortcut_action),
                            )
                        }
                        IconButton(onClick = { voiceStartKey++ }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mic),
                                contentDescription = "Записать голосом",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить измерение")
                }
            }
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            0 -> DiaryScreen(
                entries = entries,
                unit = unit,
                onDelete = viewModel::delete,
                modifier = modifier,
            )
            1 -> ChartScreen(
                entries = entries,
                unit = unit,
                modifier = modifier,
            )
            else -> ReportScreen(
                entries = entries,
                unit = unit,
                statsFor = viewModel::statsFor,
                modifier = modifier,
            )
        }
    }

    VoiceEntryFlow(
        unit = unit,
        startKey = voiceStartKey,
        onSave = { mgdl, context, note, measuredAt ->
            viewModel.add(mgdl = mgdl, context = context, note = note, measuredAt = measuredAt)
        },
        onManualEntry = { showAddDialog = true },
    )

    if (showAddDialog) {
        AddEntryDialog(
            unit = unit,
            onDismiss = { showAddDialog = false },
            onConfirm = { mgdl, context, note ->
                viewModel.add(mgdl = mgdl, context = context, note = note)
                showAddDialog = false
            },
        )
    }
}