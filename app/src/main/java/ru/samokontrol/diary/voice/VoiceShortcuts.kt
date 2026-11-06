package ru.samokontrol.diary.voice

import android.content.Context
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import ru.samokontrol.diary.MainActivity
import ru.samokontrol.diary.R

/** Ярлык «Записать сахар» на рабочем столе: один тап — сразу голосовой ввод. */
object VoiceShortcuts {

    private const val SHORTCUT_ID = "record_sugar"

    /**
     * Просит лаунчер закрепить ярлык. Система показывает диалог подтверждения;
     * если лаунчер не поддерживает закрепление — сообщаем об этом.
     */
    fun pin(context: Context) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, R.string.pin_shortcut_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.shortcut_sugar_short))
            .setLongLabel(context.getString(R.string.shortcut_sugar_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_sugar))
            .setIntent(MainActivity.voiceEntryIntent(context))
            .build()
        if (!ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)) {
            Toast.makeText(context, R.string.pin_shortcut_failed, Toast.LENGTH_LONG).show()
        }
    }
}