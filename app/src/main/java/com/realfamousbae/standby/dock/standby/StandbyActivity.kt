package com.realfamousbae.standby.dock.standby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.realfamousbae.standby.dock.graph

/**
 * Полноэкранный режим StandBy поверх экрана блокировки.
 *
 * Флаги showWhenLocked/turnScreenOn продублированы в манифесте и в коде: атрибуты
 * манифеста срабатывают уже при создании окна, вызовы в коде — гарантия на всех прошивках.
 * Выход — finish(): система возвращает экран блокировки и гасит экран по своему таймауту.
 */
class StandbyActivity : ComponentActivity() {

    private val powerEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            graph.diagLog.log("Режим: зарядка отключена — выход")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Яркость окна независимо от системной; 0.01 — проверка минимума (spike, п. 6).
        window.attributes = window.attributes.apply { screenBrightness = SPIKE_BRIGHTNESS }
        hideSystemBars()

        ContextCompat.registerReceiver(
            this, powerEvents, IntentFilter(Intent.ACTION_POWER_DISCONNECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        graph.diagLog.log("Режим: показан")

        setContent {
            StandbySpikeScreen(onExit = { finish() })
        }
    }

    override fun onDestroy() {
        unregisterReceiver(powerEvents)
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
        private const val SPIKE_BRIGHTNESS = 0.01f

        fun intent(context: Context): Intent =
            Intent(context, StandbyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
