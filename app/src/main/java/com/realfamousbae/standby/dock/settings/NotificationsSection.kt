package com.realfamousbae.standby.dock.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.realfamousbae.standby.dock.graph
import com.realfamousbae.standby.dock.notifications.StandbyNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Баннеры уведомлений: доступ, выключатель, от каких приложений показывать. */
@Composable
fun NotificationsSection() {
    val context = LocalContext.current
    val graph = context.graph
    val settings by graph.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    var appsOpen by remember { mutableStateOf(false) }

    @Suppress("UNUSED_EXPRESSION") refresh
    val granted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    SectionTitle("Уведомления")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Баннеры поверх режима", modifier = Modifier.weight(1f).padding(top = 12.dp))
        Switch(
            checked = settings.notificationBannersEnabled,
            onCheckedChange = { scope.launch { graph.settingsRepository.setBannersEnabled(it) } },
        )
    }
    if (!settings.notificationBannersEnabled) return

    StatusRow("Доступ к уведомлениям", granted) { openListenerSettings(context) }
    if (!granted) {
        Text(
            "Если переключатель доступа неактивен («Ограниченный параметр»): Карточка приложения → " +
                "⋮ в правом верхнем углу → «Разрешить ограниченные настройки», затем вернитесь сюда.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = { context.startActivity(SystemChecks.appDetails(context)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Карточка приложения") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Скрывать системные всплывающие уведомления", modifier = Modifier.weight(1f).padding(top = 12.dp))
        Switch(
            checked = settings.hideSystemHeadsUp,
            onCheckedChange = { scope.launch { graph.settingsRepository.setHideSystemHeadsUp(it) } },
        )
    }
    if (settings.hideSystemHeadsUp) {
        Text(
            "Пока режим на экране, включается режим «StandBy» из «Не беспокоить»: уведомления " +
                "приходят без звука и показываются только нашими баннерами. Звонки и будильники — " +
                "как обычно.",
            style = MaterialTheme.typography.bodySmall,
        )
        StatusRow("Доступ к «Не беспокоить»", graph.quietMode.hasAccess()) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }
    Text(
        "Беззвучные и постоянные уведомления не показываются. Текст скрывается, если так " +
            "настроено для экрана блокировки.",
        style = MaterialTheme.typography.bodySmall,
    )
    val blocked = settings.notificationBlockedPackages.size
    OutlinedButton(onClick = { appsOpen = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (blocked == 0) "Приложения: все" else "Приложения: скрыто $blocked")
    }
    if (appsOpen) AppsDialog(onDismiss = { appsOpen = false })
}

private fun openListenerSettings(context: Context) {
    val component = StandbyNotificationListener.component(context).flattenToString()
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
    try {
        context.startActivity(detail)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

private class AppEntry(val packageName: String, val label: String, val icon: ImageBitmap?)

/** Приложения с ярлыком в лаунчере; снятая галочка — баннеры от приложения не показываются. */
@Composable
private fun AppsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val settings by graph.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.Default) { launcherApps(context) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        title = { Text("Баннеры от приложений") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val list = apps?.filter { it.label.contains(query.trim(), ignoreCase = true) }
                if (list == null) {
                    Text("Загружаю…")
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(list, key = { it.packageName }) { app ->
                            val enabled = app.packageName !in settings.notificationBlockedPackages
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .toggleable(value = enabled) {
                                        scope.launch { graph.settingsRepository.setPackageBlocked(app.packageName, !it) }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(32.dp)) }
                                Text(app.label, modifier = Modifier.weight(1f))
                                Checkbox(checked = enabled, onCheckedChange = null)
                            }
                        }
                    }
                }
            }
        },
    )
}

private fun launcherApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map { app ->
            AppEntry(
                packageName = app.packageName,
                label = pm.getApplicationLabel(app).toString(),
                icon = runCatching { pm.getApplicationIcon(app).toBitmap(64, 64).asImageBitmap() }.getOrNull(),
            )
        }
        .sortedBy { it.label.lowercase() }
}
