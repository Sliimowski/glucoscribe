package ru.samokontrol.diary

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.samokontrol.diary.ui.DiaryRoot
import ru.samokontrol.diary.ui.theme.SamokontrolTheme
import ru.samokontrol.diary.voice.VoiceEntryRequests

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SamokontrolTheme {
                DiaryRoot()
            }
        }
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val voiceEntry = intent?.action == ACTION_VOICE_ENTRY ||
            intent?.getBooleanExtra(EXTRA_VOICE_ENTRY, false) == true
        if (voiceEntry) {
            VoiceEntryRequests.request()
        }
    }

    companion object {
        const val EXTRA_VOICE_ENTRY = "voice_entry"
        const val ACTION_VOICE_ENTRY = "ru.samokontrol.diary.action.VOICE_ENTRY"

        fun voiceEntryIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_VOICE_ENTRY
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_VOICE_ENTRY, true)
            }
    }
}