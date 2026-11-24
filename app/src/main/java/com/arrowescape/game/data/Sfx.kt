package com.arrowescape.game.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.arrowescape.game.R

enum class Sound { EXIT, BLOCK, WIN }

/** Short effects via SoundPool. Placeholder sounds live in res/raw; see README to replace them. */
class Sfx(context: Context) {
    var enabled = true

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()

    private val ids = mapOf(
        Sound.EXIT to R.raw.exit, Sound.BLOCK to R.raw.block, Sound.WIN to R.raw.win,
    ).mapValues { pool.load(context, it.value, 1) }

    fun play(s: Sound) {
        if (enabled) pool.play(ids.getValue(s), 1f, 1f, 1, 0, 1f)
    }
}
