package com.example.flappybird

import android.content.Context

class ScoreManager(context: Context) {
    private val prefs = context.getSharedPreferences("flappy_bird_prefs", Context.MODE_PRIVATE)

    fun getHighScore(): Int = prefs.getInt(KEY_HIGH_SCORE, 0)

    fun saveHighScoreIfBetter(score: Int): Boolean {
        val current = getHighScore()
        if (score > current) {
            prefs.edit().putInt(KEY_HIGH_SCORE, score).apply()
            return true
        }
        return false
    }

    companion object {
        private const val KEY_HIGH_SCORE = "high_score"
    }
}
