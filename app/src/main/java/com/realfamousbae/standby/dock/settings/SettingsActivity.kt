package com.realfamousbae.standby.dock.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.hardware.SensorManager
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.realfamousbae.standby.dock.core.prefs.StandbySettings
import com.realfamousbae.standby.dock.core.trigger.OrientationClassifier
import com.realfamousbae.standby.dock.core.trigger.TriggerEvent
import com.realfamousbae.standby.dock.sensors.GravitySensor
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.realfamousbae.standby.dock.graph
import com.realfamousbae.standby.dock.service.ServiceStarter
import com.realfamousbae.standby.dock.service.StandbyService

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

        TriggerSettingsSection()

        Text("Тесты", style = MaterialTheme.typography.titleMedium)
        Button(
            // Через службу: показ и закрытие режима решает только она (TriggerEngine).
            onClick = { context.graph.controller.send(TriggerEvent.ManualShow) },
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


/** Настройки триггера и живой угол датчика — для калибровки под свою подставку. */
@Composable
private fun TriggerSettingsSection() {
    val context = LocalContext.current
    val graph = context.graph
    val settings by graph.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Пока экран открыт, слушаем акселерометр сами (служба его в это время не держит).
    val gravitySensor = remember { GravitySensor(context.getSystemService(SensorManager::class.java)) }
    val tilt by remember {
        gravitySensor.gravity().map { OrientationClassifier.tiltFromVerticalDegrees(it) }
    }.collectAsStateWithLifecycle(initialValue = null)

    // Ползунки двигаем локально и сохраняем при отпускании, чтобы не писать DataStore 60 раз в секунду.
    var tiltDraft by remember(settings.maxTiltDegrees) { mutableFloatStateOf(settings.maxTiltDegrees) }
    var batteryDraft by remember(settings.minBatteryPercentOffCharger) {
        mutableFloatStateOf(settings.minBatteryPercentOffCharger.toFloat())
    }

    Text("Включение", style = MaterialTheme.typography.titleMedium)
    val current = tilt
    val onSide = current != null && current < tiltDraft
    Text(
        if (current == null) "Наклон: —" else "Наклон от вертикали сейчас: ${current.roundToInt()}° " +
            if (onSide) "— на боку ✅" else "— не на боку",
    )
    Text("Порог наклона: ${tiltDraft.roundToInt()}°")
    Slider(
        value = tiltDraft,
        onValueChange = { tiltDraft = it },
        onValueChangeFinished = { scope.launch { graph.settingsRepository.setMaxTilt(tiltDraft) } },
        valueRange = StandbySettings.TILT_RANGE_MIN..StandbySettings.TILT_RANGE_MAX,
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Только на зарядке", modifier = Modifier.weight(1f).padding(top = 12.dp))
        Switch(
            checked = settings.requireCharging,
            onCheckedChange = { scope.launch { graph.settingsRepository.setRequireCharging(it) } },
        )
    }
    if (!settings.requireCharging) {
        Text("Без зарядки — только если заряд не ниже ${batteryDraft.roundToInt()}%")
        Slider(
            value = batteryDraft,
            onValueChange = { batteryDraft = it },
            onValueChangeFinished = {
                scope.launch { graph.settingsRepository.setMinBattery(batteryDraft.roundToInt()) }
            },
            valueRange = StandbySettings.BATTERY_RANGE_MIN.toFloat()..StandbySettings.BATTERY_RANGE_MAX.toFloat(),
        )
    }
}
