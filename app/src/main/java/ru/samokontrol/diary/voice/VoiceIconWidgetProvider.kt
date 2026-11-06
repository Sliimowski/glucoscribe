package ru.samokontrol.diary.voice

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import ru.samokontrol.diary.MainActivity
import ru.samokontrol.diary.R

/**
 * Виджет 1×1 «Записать»: линейная иконка прямо на обоях, без системного круга
 * и бейджа. Один тап — сразу голосовой ввод.
 */
class VoiceIconWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, buildViews(context))
        }
    }

    private fun buildViews(context: Context): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_record_icon).apply {
            setOnClickPendingIntent(R.id.widget_icon_root, launchIntent(context))
        }

    private fun launchIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            MainActivity.voiceEntryIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}