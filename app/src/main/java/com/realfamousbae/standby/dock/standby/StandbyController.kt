package com.realfamousbae.standby.dock.standby

import com.realfamousbae.standby.dock.core.trigger.TriggerEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Связь между службой (владеет TriggerEngine) и UI (Activity режима, экран настроек).
 * - Служба выставляет [visible]: Activity закрывается сама, как только он стал false.
 * - UI отправляет события пользователя в [userEvents], служба передаёт их в TriggerEngine.
 * Живёт в AppGraph, т. е. столько же, сколько процесс.
 */
class StandbyController {

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _userEvents = MutableSharedFlow<TriggerEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val userEvents: SharedFlow<TriggerEvent> = _userEvents.asSharedFlow()

    fun setVisible(value: Boolean) {
        _visible.value = value
    }

    fun send(event: TriggerEvent) {
        _userEvents.tryEmit(event)
    }
}
