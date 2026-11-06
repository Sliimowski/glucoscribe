package ru.samokontrol.diary.voice

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import ru.samokontrol.diary.MainActivity
import ru.samokontrol.diary.R

/**
 * Плитка быстрых настроек: «Записать сахар».
 * Пользователь добавляет её сам через редактирование шторки.
 */
class VoiceTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.voice_entry_title)
            icon = Icon.createWithResource(this@VoiceTileService, R.drawable.ic_mic)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = MainActivity.voiceEntryIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}