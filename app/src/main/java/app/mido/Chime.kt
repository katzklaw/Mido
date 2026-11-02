package app.mido

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import app.mido.settings.AppSettings

/** The little "ding" when a task is checked off. Quiet when the phone is on silent or vibrate. */
object Chime {
    fun playDone(context: Context) {
        val app = context.applicationContext
        if (!AppSettings(app).chimeEnabled) return
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val player = MediaPlayer.create(app, R.raw.done_chime, attrs, AudioManager.AUDIO_SESSION_ID_GENERATE)
                ?: return
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mp, _, _ -> mp.release(); true }
            player.start()
        }
    }
}
