package com.realfamousbae.standby.dock.standby

import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.realfamousbae.standby.dock.core.trigger.TriggerEvent
import com.realfamousbae.standby.dock.graph
import com.realfamousbae.standby.dock.sensors.LightSensor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Полноэкранный режим StandBy поверх экрана блокировки.
 *
 * Activity ничего не решает сама: показывать ли её, решает служба (TriggerEngine), а
 * Activity закрывается, как только `controller.visible` стал false. Уход с экрана по любой
 * причине (тап, кнопка «назад») сообщается службе как [TriggerEvent.UserExit].
 *
 * Флаги showWhenLocked/turnScreenOn продублированы в манифесте и в коде: атрибуты
 * манифеста срабатывают уже при создании окна, вызовы в коде — гарантия на всех прошивках.
 */
class StandbyActivity : ComponentActivity() {

    private val controller get() = graph.controller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        // Сразу минимальная яркость: иначе до первого показания датчика света (~0.25 с)
        // экран вспыхнет на системной яркости — в тёмной спальне это слепит.
        window.attributes = window.attributes.apply { screenBrightness = graph.settings.value.brightnessMin }

        // Яркость окна — независимо от системной, по датчику освещённости.
        val brightness = BrightnessController(
            window = window,
            lightSensor = LightSensor(getSystemService(SensorManager::class.java)),
            settings = graph.settings,
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { brightness.run() }
        }
        // Служба решила скрыть режим — закрываемся.
        lifecycleScope.launch {
            controller.visible.first { !it }
            finish()
        }
        graph.diagLog.log("Режим: показан")

        setContent {
            StandbySpikeScreen(onExit = { finish() })
        }
    }

    override fun onDestroy() {
        // Закрыли не по решению службы (тап, «назад», система) — сообщаем, чтобы режим
        // не вернулся сразу после погасания экрана.
        if (!isChangingConfigurations && controller.visible.value) {
            controller.setVisible(false)
            controller.send(TriggerEvent.UserExit)
        }
        graph.diagLog.log("Режим: закрыт")
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, StandbyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
