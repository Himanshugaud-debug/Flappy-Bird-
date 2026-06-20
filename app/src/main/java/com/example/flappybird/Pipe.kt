package com.example.flappybird

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * A single pipe obstacle: a top pipe and bottom pipe with a gap between them.
 * Moves leftward each frame; tracks whether the bird has already passed it (for scoring).
 */
class Pipe(
    var x: Float,
    val gapCenterY: Float,
    val gapHeight: Float,
    private val screenHeight: Int
) {
    companion object {
        const val WIDTH = 100f
        const val CAP_HEIGHT = 36f
        const val CAP_OVERHANG = 10f
    }

    var scored = false

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#74BF2E")
        style = Paint.Style.FILL
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8FE03E")
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4C7A1E")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    val topRect: RectF
        get() = RectF(x, 0f, x + WIDTH, gapCenterY - gapHeight / 2f)

    val bottomRect: RectF
        get() = RectF(x, gapCenterY + gapHeight / 2f, x + WIDTH, screenHeight.toFloat())

    fun update(deltaTime: Float, speed: Float) {
        x -= speed * deltaTime
    }

    fun isOffScreen(): Boolean = x + WIDTH < 0

    fun draw(canvas: Canvas) {
        val top = topRect
        val bottom = bottomRect

        // Top pipe body
        canvas.drawRect(top, bodyPaint)
        canvas.drawRect(top, outlinePaint)
        // Top pipe cap (overhangs slightly, sits at the gap edge)
        val topCap = RectF(
            top.left - CAP_OVERHANG,
            top.bottom - CAP_HEIGHT,
            top.right + CAP_OVERHANG,
            top.bottom
        )
        canvas.drawRect(topCap, capPaint)
        canvas.drawRect(topCap, outlinePaint)

        // Bottom pipe body
        canvas.drawRect(bottom, bodyPaint)
        canvas.drawRect(bottom, outlinePaint)
        // Bottom pipe cap
        val bottomCap = RectF(
            bottom.left - CAP_OVERHANG,
            bottom.top,
            bottom.right + CAP_OVERHANG,
            bottom.top + CAP_HEIGHT
        )
        canvas.drawRect(bottomCap, capPaint)
        canvas.drawRect(bottomCap, outlinePaint)
    }
}
