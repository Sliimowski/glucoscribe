package ru.samokontrol.diary.voice

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Мостик от внешних триггеров (плитка, виджет, ярлык) к экрану приложения.
 *
 * replay = 1 нужен для холодного старта: MainActivity успевает отправить
 * запрос раньше, чем Compose подпишется на поток. После обработки запрос
 * сбрасывается, чтобы он не повторился при пересоздании экрана.
 */
object VoiceEntryRequests {

    private val _requests = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val requests = _requests.asSharedFlow()

    fun request() {
        _requests.tryEmit(Unit)
    }

    fun consumed() {
        _requests.resetReplayCache()
    }
}