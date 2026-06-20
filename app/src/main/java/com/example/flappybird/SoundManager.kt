package com.example.flappybird

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundManager(context: Context) {
    private var soundPool: SoundPool? = null
    private var flapSound: Int = 0
    private var scoreSound: Int = 0
    private var dieSound: Int = 0

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        // We load the sounds from R.raw.
        // If the files don't exist yet, this will show an error until you add them.
        try {
            val res = context.resources
            val flapId = res.getIdentifier("flap", "raw", context.packageName)
            val scoreId = res.getIdentifier("score", "raw", context.packageName)
            val dieId = res.getIdentifier("die", "raw", context.packageName)

            if (flapId != 0) flapSound = soundPool?.load(context, flapId, 1) ?: 0
            if (scoreId != 0) scoreSound = soundPool?.load(context, scoreId, 1) ?: 0
            if (dieId != 0) dieSound = soundPool?.load(context, dieId, 1) ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playFlap() {
        if (flapSound != 0) soundPool?.play(flapSound, 1f, 1f, 0, 0, 1f)
    }

    fun playScore() {
        if (scoreSound != 0) soundPool?.play(scoreSound, 1f, 1f, 0, 0, 1f)
    }

    fun playDie() {
        if (dieSound != 0) soundPool?.play(dieSound, 1f, 1f, 0, 0, 1f)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
