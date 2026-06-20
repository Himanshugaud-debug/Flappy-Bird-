package com.example.flappybird

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Represents the player-controlled bird: position, velocity, rotation and rendering.
 */
class Bird(private val screenWidth: Int, private val screenHeight: Int) {

    companion object {
        const val RADIUS = 28f
        private const val GRAVITY = 1500f          // px/s^2
        private const val FLAP_VELOCITY = -560f     // px/s (upward, negative y)
        private const val MAX_FALL_SPEED = 1100f
        private const val MAX_ROTATION = 90f
        private const val MIN_ROTATION = -30f
    }

    var x: Float = screenWidth * 0.3f
    var y: Float = screenHeight * 0.4f
    var velocityY: Float = 0f
    var rotation: Float = 0f

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD93B")
        style = Paint.Style.FILL
    }
    private val beakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF8C00")
        style = Paint.Style.FILL
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2B705")
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3D2B00")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private var wingFlapTime = 0f
    private var wingUp = true

    fun reset() {
        x = screenWidth * 0.3f
        y = screenHeight * 0.4f
        velocityY = 0f
        rotation = 0f
        wingFlapTime = 0f
    }

    fun flap() {
        velocityY = FLAP_VELOCITY
    }

    /** Update physics. deltaTime is in seconds. */
    fun update(deltaTime: Float) {
        velocityY += GRAVITY * deltaTime
        if (velocityY > MAX_FALL_SPEED) velocityY = MAX_FALL_SPEED
        y += velocityY * deltaTime

        // Rotation follows velocity: nose-down when falling, nose-up when flapping
        val targetRotation = (velocityY / MAX_FALL_SPEED) * MAX_ROTATION
        rotation = targetRotation.coerceIn(MIN_ROTATION, MAX_ROTATION)

        // Wing flap animation timer
        wingFlapTime += deltaTime
        if (wingFlapTime > 0.12f) {
            wingFlapTime = 0f
            wingUp = !wingUp
        }
    }

    fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)

        // Body
        canvas.drawCircle(0f, 0f, RADIUS, bodyPaint)
        canvas.drawCircle(0f, 0f, RADIUS, outlinePaint)

        // Wing
        val wingOffsetY = if (wingUp) -6f else 6f
        canvas.drawOval(
            RectF(-RADIUS * 0.6f, wingOffsetY - 10f, RADIUS * 0.3f, wingOffsetY + 10f),
            wingPaint
        )

        // Eye
        canvas.drawCircle(RADIUS * 0.35f, -RADIUS * 0.3f, 8f, eyePaint)
        canvas.drawCircle(RADIUS * 0.4f, -RADIUS * 0.3f, 4f, pupilPaint)

        // Beak
        val beakPath = android.graphics.Path()
        beakPath.moveTo(RADIUS * 0.7f, 0f)
        beakPath.lineTo(RADIUS * 1.4f, -6f)
        beakPath.lineTo(RADIUS * 1.4f, 8f)
        beakPath.close()
        canvas.drawPath(beakPath, beakPaint)

        canvas.restore()
    }

    /** Bounding circle for collision purposes, slightly shrunk for forgiving hitbox. */
    fun getHitboxRadius(): Float = RADIUS * 0.75f
}
