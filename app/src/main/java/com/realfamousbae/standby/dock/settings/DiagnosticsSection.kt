package com.realfamousbae.standby.dock.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.realfamousbae.standby.dock.core.trigger.TriggerEvent
import com.realfamousbae.standby.dock.graph
import com.realfamousbae.standby.dock.service.ServiceStarter
import com.realfamousbae.standby.dock.service.StandbyService

/**
 * Разрешения (нужны всем) и свёрнутый по умолчанию блок «Для разработчиков» с ручными
 * тестами и журналом.
 */
@Composable
fun DiagnosticsSection() {
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
    var devOpen by rememberSaveable { mutableStateOf(false) }

    SectionTitle("Разрешения")
    StatusRow("Поверх других окон", overlay) {
        context.startActivity(SystemChecks.overlaySettings(context))
    }
    StatusRow("Без оптимизации батареи", battery) {
        context.startActivity(SystemChecks.requestIgnoreBatteryOptimizations(context))
    }
    StatusRow("Уведомление службы", notifications) {
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

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { devOpen = !devOpen }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Для разработчиков", style = MaterialTheme.typography.titleMedium)
        Text(if (devOpen) "▾" else "▸", style = MaterialTheme.typography.titleMedium)
    }
    AnimatedVisibility(devOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Своя область перерисовки: чтение `refresh` здесь обновляет журнал по кнопкам.
            @Suppress("UNUSED_EXPRESSION") refresh
            SectionTitle("Тесты")
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
                SectionTitle("Журнал")
                OutlinedButton(onClick = { refresh++ }) { Text("Обновить") }
                OutlinedButton(onClick = { context.graph.diagLog.clear(); refresh++ }) { Text("Очистить") }
            }
            context.graph.diagLog.lines().takeLast(80).reversed().forEach {
                Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}
