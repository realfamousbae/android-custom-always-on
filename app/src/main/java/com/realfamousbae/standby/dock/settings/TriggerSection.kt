package com.realfamousbae.standby.dock.settings

import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.realfamousbae.standby.dock.core.prefs.StandbySettings
import com.realfamousbae.standby.dock.core.trigger.OrientationClassifier
import com.realfamousbae.standby.dock.graph
import com.realfamousbae.standby.dock.sensors.GravitySensor
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Настройки триггера и живой угол датчика — для калибровки под свою подставку. */
@Composable
fun TriggerSettingsSection() {
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

    SectionTitle("Включение")
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
