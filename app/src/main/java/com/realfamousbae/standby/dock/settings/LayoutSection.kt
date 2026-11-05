package com.realfamousbae.standby.dock.settings

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.realfamousbae.standby.dock.core.prefs.ScreenLayout
import com.realfamousbae.standby.dock.graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Раскладка экрана режима и выбор системного виджета для правой карточки. */
@Composable
fun LayoutSection(onPickWidget: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val settings by graph.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pickerOpen by remember { mutableStateOf(false) }
    val widget = graph.widgetHost.info(settings.widgetId)

    SectionTitle("Экран режима")
    LayoutOption("Только часы", settings.layout == ScreenLayout.ClockOnly) {
        scope.launch { graph.settingsRepository.setLayout(ScreenLayout.ClockOnly) }
    }
    LayoutOption("Часы + виджет", settings.layout == ScreenLayout.ClockAndWidget) {
        if (widget == null) {
            pickerOpen = true
        } else {
            scope.launch { graph.settingsRepository.setLayout(ScreenLayout.ClockAndWidget) }
        }
    }

    when {
        widget != null -> Text(
            "Виджет: ${graph.widgetHost.label(widget)} (${graph.widgetHost.appLabel(widget)})",
            modifier = Modifier.padding(start = 12.dp),
        )
        settings.widgetId != null -> Text(
            "Выбранный виджет больше недоступен (приложение удалено?) — выберите заново. " +
                "Пока показываются только часы.",
            color = MaterialTheme.colorScheme.error,
        )
    }
    OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (widget == null) "Выбрать виджет" else "Сменить виджет")
    }

    if (pickerOpen) {
        WidgetPickerDialog(
            onDismiss = { pickerOpen = false },
            onPick = {
                pickerOpen = false
                onPickWidget(it)
            },
        )
    }
}

@Composable
private fun LayoutOption(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title)
    }
}

private class WidgetEntry(val info: AppWidgetProviderInfo, val label: String, val app: String, val icon: ImageBitmap?)

@Composable
private fun WidgetPickerDialog(onDismiss: () -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val host = context.graph.widgetHost
    val density = LocalResources.current.displayMetrics.densityDpi
    val entries by produceState<List<WidgetEntry>?>(null) {
        value = withContext(Dispatchers.Default) {
            host.providers().map { info ->
                val icon = runCatching { info.loadIcon(context, density)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
                WidgetEntry(info, host.label(info), host.appLabel(info), icon)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text("Системный виджет") },
        text = {
            val list = entries
            when {
                list == null -> Text("Загружаю…")
                list.isEmpty() -> Text("Виджетов не найдено.")
                else -> LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(list, key = { "${it.info.provider.flattenToString()}@${it.info.profile.hashCode()}" }) { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(entry.info) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            entry.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) }
                            Column {
                                Text(entry.label)
                                Text(entry.app, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
    )
}
