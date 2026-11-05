package com.realfamousbae.standby.dock.settings

import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.realfamousbae.standby.dock.core.prefs.ScreenLayout
import com.realfamousbae.standby.dock.graph
import com.realfamousbae.standby.dock.service.ServiceStarter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Настройки: сверху — то, чем пользуются (раскладка и виджет, уведомления, включение),
 * внизу — разрешения, тесты и журнал диагностики.
 */
class SettingsActivity : ComponentActivity() {

    private val widgetHost get() = graph.widgetHost

    /** id виджета, который сейчас привязывается/настраивается. */
    private var pendingWidgetId: Int? = null

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val id = pendingWidgetId ?: return@registerForActivityResult
        if (it.resultCode == RESULT_OK) configureOrSave(id) else cancelPending()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Из видимой Activity запуск FGS разрешён всегда.
        ServiceStarter.start(this, reason = "открыто приложение")
        pendingWidgetId = savedInstanceState?.getInt(KEY_PENDING, NO_ID)?.takeIf { it != NO_ID }
        if (pendingWidgetId == null) {
            // Убрать id, оставшиеся от выбора, прерванного вместе с процессом.
            lifecycleScope.launch {
                val saved = graph.settingsRepository.settings.first().widgetId
                // Пользователь мог уже начать выбор, пока читались настройки.
                widgetHost.deleteAllExcept(setOfNotNull(saved, pendingWidgetId))
            }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { SettingsScreen(onPickWidget = ::pickWidget) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingWidgetId?.let { outState.putInt(KEY_PENDING, it) }
    }

    /** Шаг 1: выделить id и привязать (с системным диалогом согласия, если нужно). */
    private fun pickWidget(provider: AppWidgetProviderInfo) {
        cancelPending()
        val id = widgetHost.allocate()
        pendingWidgetId = id
        if (widgetHost.bindIfAllowed(id, provider.provider)) {
            configureOrSave(id)
        } else {
            bindWidget.launch(widgetHost.bindIntent(id, provider.provider))
        }
    }

    /** Шаг 2: экран настройки виджета, если он есть; иначе сразу сохранить. */
    private fun configureOrSave(id: Int) {
        val started = runCatching { widgetHost.startConfigureIfNeeded(this, id, REQUEST_CONFIGURE) }
            .getOrElse {
                graph.diagLog.log("Виджет: экран настройки не открылся (${it.javaClass.simpleName})")
                false
            }
        if (!started) save(id)
    }

    @Deprecated("startAppWidgetConfigureActivityForResult отдаёт результат только сюда")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIGURE) return
        val id = pendingWidgetId ?: return
        if (resultCode == RESULT_OK) save(id) else cancelPending()
    }

    private fun save(id: Int) {
        pendingWidgetId = null
        lifecycleScope.launch {
            val previous = graph.settingsRepository.settings.first().widgetId
            graph.settingsRepository.setWidgetId(id)
            graph.settingsRepository.setLayout(ScreenLayout.ClockAndWidget)
            if (previous != null && previous != id) widgetHost.delete(previous)
            graph.diagLog.log("Виджет: выбран")
        }
    }

    private fun cancelPending() {
        pendingWidgetId?.let(widgetHost::delete)
        pendingWidgetId = null
    }

    private companion object {
        const val REQUEST_CONFIGURE = 4101
        const val KEY_PENDING = "pending_widget_id"
        const val NO_ID = -1
    }
}

@Composable
private fun SettingsScreen(onPickWidget: (AppWidgetProviderInfo) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("StandBy", style = MaterialTheme.typography.headlineSmall)
        LayoutSection(onPickWidget)
        SectionDivider()
        NotificationsSection()
        SectionDivider()
        TriggerSettingsSection()
        SectionDivider()
        DiagnosticsSection()
    }
}

@Composable
private fun SectionDivider() = HorizontalDivider(Modifier.padding(vertical = 8.dp))

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun StatusRow(
    title: String,
    ok: Boolean?,
    unknownText: String = "неизвестно",
    onFix: () -> Unit,
) {
    val mark = when (ok) {
        true -> "✅"
        false -> "❌"
        null -> "❔ $unknownText"
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$mark  $title", modifier = Modifier.weight(1f).padding(top = 10.dp))
        if (ok != true) OutlinedButton(onClick = onFix) { Text("Открыть") }
    }
}
