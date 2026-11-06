package ru.samokontrol.diary.voice

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import ru.samokontrol.diary.R

/**
 * Закрепление виджета 1×1 «Записать» на рабочем столе.
 *
 * В отличие от pinned-shortcut, виджет рисует линейную иконку прямо на обоях,
 * без системного круга и бейджа приложения.
 */
object VoiceWidgets {

    fun pinIcon(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) {
            Toast.makeText(context, R.string.pin_widget_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        val requested = manager.requestPinAppWidget(
            ComponentName(context, VoiceIconWidgetProvider::class.java),
            null,
            null,
        )
        if (!requested) {
            Toast.makeText(context, R.string.pin_widget_failed, Toast.LENGTH_LONG).show()
        }
    }
}
