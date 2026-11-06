package ru.samokontrol.diary.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.samokontrol.diary.data.MeasurementContext
import ru.samokontrol.diary.domain.Glucose
import ru.samokontrol.diary.domain.GlucoseUnit
import ru.samokontrol.diary.domain.SpokenGlucoseParser
import ru.samokontrol.diary.voice.SpeechError
import ru.samokontrol.diary.voice.SpeechGlucoseRecognizer
import ru.samokontrol.diary.voice.VoiceEntryRequests

sealed interface VoiceStage {
    data object Idle : VoiceStage

    data class Listening(val partial: String, val onDevice: Boolean) : VoiceStage

    data class Confirm(
        val value: String,
        val recognized: String,
        val measuredAt: Long,
    ) : VoiceStage

    data class Failure(
        val title: String,
        val message: String,
        val recognized: String?,
        val canRetry: Boolean,
    ) : VoiceStage
}

/**
 * Голосовая запись: разрешение → распознавание → подтверждение → сохранение.
 *
 * @param startKey изменение ключа запускает новую запись (кнопка в приложении).
 */
@Composable
fun VoiceEntryFlow(
    unit: GlucoseUnit,
    startKey: Int,
    onSave: (mgdl: Int, context: MeasurementContext, note: String, measuredAt: Long) -> Unit,
    onManualEntry: () -> Unit,
) {
    val context = LocalContext.current
    val recognizer = remember { SpeechGlucoseRecognizer(context) }
    val currentUnit by rememberUpdatedState(unit)
    val currentOnSave by rememberUpdatedState(onSave)

    var stage by remember { mutableStateOf<VoiceStage>(VoiceStage.Idle) }
    var measuredAt by remember { mutableLongStateOf(0L) }
    var pendingPermission by remember { mutableStateOf(false) }

    fun startListening() {
        measuredAt = System.currentTimeMillis()
        if (!SpeechGlucoseRecognizer.isAvailable(context)) {
            stage = VoiceStage.Failure(
                title = "Распознавание недоступно",
                message = "На телефоне нет службы распознавания речи. " +
                    "Можно установить русский пакет в настройках Google или ввести значение вручную.",
                recognized = null,
                canRetry = false,
            )
            return
        }
        val onDevice = recognizer.isOnDevice
        stage = VoiceStage.Listening(partial = "", onDevice = onDevice)
        recognizer.start(
            onPartial = { text -> stage = VoiceStage.Listening(text, onDevice) },
            onResult = { text ->
                val parsed = SpokenGlucoseParser.parse(text, currentUnit)
                stage = if (parsed == null) {
                    VoiceStage.Failure(
                        title = "Не понял значение",
                        message = "Скажите, например, «пять и шесть», или введите значение вручную.",
                        recognized = text,
                        canRetry = true,
                    )
                } else {
                    VoiceStage.Confirm(
                        value = Glucose.format(parsed, currentUnit),
                        recognized = text,
                        measuredAt = measuredAt,
                    )
                }
            },
            onError = { error -> stage = errorStage(error) },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (pendingPermission) {
            pendingPermission = false
            if (granted) {
                startListening()
            } else {
                stage = VoiceStage.Failure(
                    title = "Нет доступа к микрофону",
                    message = "Разрешите доступ к микрофону в настройках приложения " +
                        "или введите значение вручную.",
                    recognized = null,
                    canRetry = false,
                )
            }
        }
    }

    fun begin() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            pendingPermission = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(startKey) {
        if (startKey > 0) begin()
    }

    LaunchedEffect(Unit) {
        VoiceEntryRequests.requests.collect {
            VoiceEntryRequests.consumed()
            begin()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                recognizer.cancel()
                if (stage is VoiceStage.Listening) stage = VoiceStage.Idle
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recognizer.cancel()
        }
    }

    when (val current = stage) {
        VoiceStage.Idle -> Unit

        is VoiceStage.Listening -> VoiceListeningDialog(
            partialText = current.partial,
            onDevice = current.onDevice,
            onDone = { recognizer.stop() },
            onCancel = {
                recognizer.cancel()
                stage = VoiceStage.Idle
            },
        )

        is VoiceStage.Confirm -> VoiceConfirmDialog(
            unit = unit,
            initialValue = current.value,
            recognizedText = current.recognized,
            measuredAt = current.measuredAt,
            onDismiss = { stage = VoiceStage.Idle },
            onConfirm = { mgdl, measurementContext, note ->
                currentOnSave(mgdl, measurementContext, note, current.measuredAt)
                stage = VoiceStage.Idle
            },
        )

        is VoiceStage.Failure -> VoiceFailureDialog(
            title = current.title,
            message = current.message,
            recognizedText = current.recognized,
            canRetry = current.canRetry,
            onRetry = { begin() },
            onManual = {
                stage = VoiceStage.Idle
                onManualEntry()
            },
            onDismiss = { stage = VoiceStage.Idle },
        )
    }
}

private fun errorStage(error: SpeechError): VoiceStage = when (error) {
    SpeechError.NothingHeard -> VoiceStage.Failure(
        title = "Не расслышал",
        message = "Попробуйте ещё раз, ближе к микрофону и без лишнего шума.",
        recognized = null,
        canRetry = true,
    )

    SpeechError.NoPermission -> VoiceStage.Failure(
        title = "Нет доступа к микрофону",
        message = "Разрешите доступ к микрофону в настройках приложения.",
        recognized = null,
        canRetry = false,
    )

    SpeechError.LanguageUnavailable -> VoiceStage.Failure(
        title = "Русский язык не поддерживается",
        message = "Установите русский пакет распознавания речи в настройках системы " +
            "или введите значение вручную.",
        recognized = null,
        canRetry = true,
    )

    SpeechError.Unavailable -> VoiceStage.Failure(
        title = "Распознавание недоступно",
        message = "Служба распознавания сейчас не отвечает. Попробуйте ещё раз " +
            "или введите значение вручную.",
        recognized = null,
        canRetry = true,
    )

    SpeechError.Timeout -> VoiceStage.Failure(
        title = "Распознавание не ответило",
        message = "Служба распознавания не прислала результат. Попробуйте ещё раз " +
            "или введите значение вручную.",
        recognized = null,
        canRetry = true,
    )
}