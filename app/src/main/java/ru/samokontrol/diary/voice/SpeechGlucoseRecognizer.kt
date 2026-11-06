package ru.samokontrol.diary.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Обёртка над системным распознаванием речи.
 *
 * По возможности использует локальный распознаватель (Android 12+),
 * чтобы аудио не уходило в сеть. Если локального нет — системный,
 * о чём экран записи предупреждает пользователя.
 *
 * Системная служба не обязана присылать финальный колбэк: она может потерять
 * соединение или зависнуть. Поэтому каждая сессия ограничена по времени —
 * иначе экран «Слушаю…» остаётся навсегда.
 */
class SpeechGlucoseRecognizer(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var finished = true
    private var session = 0

    private var onResult: ((String) -> Unit)? = null
    private var onError: ((SpeechError) -> Unit)? = null
    private var timeout: Runnable? = null

    val isOnDevice: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (SpeechError) -> Unit,
    ) {
        cancel()
        finished = false
        val currentSession = ++session
        this.onResult = onResult
        this.onError = onError

        val speech = createRecognizer()
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() {
                if (isActive(currentSession)) {
                    scheduleTimeout(currentSession, SPEECH_TIMEOUT_MS)
                }
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                if (isActive(currentSession)) {
                    // Речь закончилась — ждём финальный результат не бесконечно.
                    scheduleTimeout(currentSession, RESULT_TIMEOUT_MS)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isActive(currentSession)) return
                scheduleTimeout(currentSession, SPEECH_TIMEOUT_MS)
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                if (!isActive(currentSession)) return
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isBlank()) {
                    finishWithError(currentSession, SpeechError.NothingHeard)
                } else {
                    finishWithResult(currentSession, text)
                }
            }

            override fun onError(error: Int) {
                if (!isActive(currentSession)) return
                finishWithError(currentSession, mapError(error))
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        scheduleTimeout(currentSession, START_TIMEOUT_MS)
        runCatching { speech.startListening(intent()) }
            .onFailure { finishWithError(currentSession, SpeechError.Unavailable) }
    }

    fun stop() {
        val speech = recognizer ?: return
        if (finished) return
        // Пользователь нажал «Готово»: даже если служба промолчит,
        // через несколько секунд покажем ошибку вместо вечного ожидания.
        scheduleTimeout(session, RESULT_TIMEOUT_MS)
        runCatching { speech.stopListening() }
    }

    fun cancel() {
        finished = true
        session++
        cancelTimeout()
        releaseRecognizer()
        onResult = null
        onError = null
    }

    private fun isActive(currentSession: Int): Boolean =
        !finished && currentSession == session

    private fun scheduleTimeout(currentSession: Int, delayMs: Long) {
        cancelTimeout()
        val runnable = Runnable {
            if (isActive(currentSession)) {
                finishWithError(currentSession, SpeechError.Timeout)
            }
        }
        timeout = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun cancelTimeout() {
        timeout?.let(handler::removeCallbacks)
        timeout = null
    }

    private fun finishWithResult(currentSession: Int, text: String) {
        if (!isActive(currentSession)) return
        val callback = onResult
        finish()
        callback?.invoke(text)
    }

    private fun finishWithError(currentSession: Int, error: SpeechError) {
        if (!isActive(currentSession)) return
        val callback = onError
        finish()
        callback?.invoke(error)
    }

    private fun finish() {
        finished = true
        cancelTimeout()
        releaseRecognizer()
        onResult = null
        onError = null
    }

    private fun releaseRecognizer() {
        val speech = recognizer ?: return
        recognizer = null
        runCatching { speech.cancel() }
        runCatching { speech.destroy() }
    }

    private fun createRecognizer(): SpeechRecognizer =
        if (isOnDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    private fun intent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

    private fun mapError(error: Int): SpeechError = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> SpeechError.NothingHeard

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.NoPermission

        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        -> SpeechError.LanguageUnavailable

        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_CLIENT,
        -> SpeechError.Unavailable

        else -> SpeechError.Unavailable
    }

    companion object {
        /** Сколько ждать первую речь. */
        private const val START_TIMEOUT_MS = 12_000L

        /** Сколько ждать продолжения/финала после начала речи. */
        private const val SPEECH_TIMEOUT_MS = 20_000L

        /** Сколько ждать финальный результат после конца речи или «Готово». */
        private const val RESULT_TIMEOUT_MS = 5_000L

        /** Есть ли вообще системное распознавание речи. */
        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)

        fun defaultLocaleTag(): String = Locale("ru", "RU").toLanguageTag()
    }
}

enum class SpeechError {
    NothingHeard,
    NoPermission,
    LanguageUnavailable,
    Unavailable,
    Timeout,
}