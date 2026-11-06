package com.realfamousbae.standby.dock.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.realfamousbae.standby.dock.graph
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Плитка в шторке: включает и выключает StandBy так же, как тумблер в настройках. */
class StandbyTileService : TileService() {

    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = graph.appScope.launch {
            graph.settingsRepository.settings.collect { render(it.enabled) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        graph.appScope.launch {
            val on = !graph.settingsRepository.settings.first().enabled
            // Если система не даст запустить службу из фона, ServiceStarter запишет это в
            // журнал; тумблер уже сохранён, и служба поднимется следующим разрешённым путём.
            graph.runState.setEnabled(on, reason = "плитка")
        }
    }

    private fun render(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (enabled) "Включён" else "Выключен"
        tile.updateTile()
    }
}
