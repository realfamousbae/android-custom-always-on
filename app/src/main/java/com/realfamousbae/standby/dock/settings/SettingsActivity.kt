package com.realfamousbae.standby.dock.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.realfamousbae.standby.dock.graph
import com.realfamousbae.standby.dock.service.ServiceStarter
import com.realfamousbae.standby.dock.service.StandbyService
import com.realfamousbae.standby.dock.standby.StandbyActivity

/**
 * Экран диагностики spike: статусы разрешений, кнопки-переходы в системные настройки,
 * ручные тесты и журнал. На этапе 5 превратится в мастер разрешений и настройки.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Из видимой Activity запуск FGS разрешён всегда.
        ServiceStarter.start(this, reason = "открыто приложение")
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { DiagnosticsScreen() }
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen() {
    val context = LocalContext.current
    // Пересчитываем статусы при каждом возврате на экран (например, из системных настроек).
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    // Чтение `refresh` подписывает этот блок на перерисовку.
    @Suppress("UNUSED_EXPRESSION") refresh
    val overlay = SystemChecks.canDrawOverlays(context)
    val battery = SystemChecks.isIgnoringBatteryOptimizations(context)
    val notifications = SystemChecks.canPostNotifications(context)
    val screensaver = SystemChecks.isScreensaverEnabled(context)
    val logLines = context.graph.diagLog.lines().takeLast(80).reversed()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("StandBy — диагностика", style = MaterialTheme.typography.headlineSmall)

        StatusRow("Поверх других окон", overlay) {
            context.startActivity(SystemChecks.overlaySettings(context))
        }
        StatusRow("Без оптимизации батареи", battery) {
            context.startActivity(SystemChecks.requestIgnoreBatteryOptimizations(context))
        }
        StatusRow("Уведомления", notifications) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        StatusRow(
            title = "Заставка Samsung выключена",
            ok = screensaver?.not(),
            unknownText = "не удалось прочитать",
        ) { context.startActivity(SystemChecks.screensaverSettings()) }

        OutlinedButton(
            onClick = { context.startActivity(SystemChecks.appDetails(context)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Карточка приложения (Батарея → Без ограничений)") }

        Text("Тесты", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { context.startActivity(StandbyActivity.intent(context)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Показать режим сейчас") }
        Button(
            onClick = {
                ServiceStarter.start(context, "кнопка теста", StandbyService.ACTION_TEST_LAUNCH)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Показать режим через 10 с (заблокируйте экран)") }
        OutlinedButton(
            onClick = {
                // Явная остановка: START_STICKY не сработает, поднять службу сможет только
                // KeepAliveWorker или BootReceiver (spike, п. 7).
                context.stopService(Intent(context, StandbyService::class.java))
                context.graph.diagLog.log("Тест: служба остановлена вручную")
                refresh++
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Остановить службу (тест подъёма)") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Журнал", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { refresh++ }) { Text("Обновить") }
            OutlinedButton(onClick = { context.graph.diagLog.clear(); refresh++ }) { Text("Очистить") }
        }
        logLines.forEach {
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatusRow(
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

