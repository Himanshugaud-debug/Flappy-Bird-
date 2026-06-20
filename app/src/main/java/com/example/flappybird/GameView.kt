package com.example.flappybird

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.sin
import kotlin.random.Random

enum class GameState {
    READY,    // waiting for first tap
    PLAYING,
    GAME_OVER
}

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var gameThread: Thread? = null
    @Volatile private var running = false

    private var screenWidth = 0
    private var screenHeight = 0

    private lateinit var bird: Bird
    private val pipes = mutableListOf<Pipe>()
    private val scoreManager: ScoreManager = ScoreManager(context)
    private val soundManager: SoundManager = SoundManager(context)

    private var state = GameState.READY
    private var score = 0
    private var highScore = scoreManager.getHighScore()

    // Scrolling world
    private var groundScrollX = 0f
    private var cloudScrollX = 0f
    private var groundHeight = 0f

    private val pipeSpeed = 260f          // px/s
    private val pipeGapHeight = 215f
    private val pipeSpacing = 510f        // horizontal distance between pipe pairs
    private var distanceSinceLastPipe = 0f

    private var lastFrameTimeNanos = 0L

    // --- Paints ---
    private val skyPaint = Paint().apply { color = Color.parseColor("#70C5CE") }
    private val groundPaint = Paint().apply { color = Color.parseColor("#DED895") }
    private val groundStripePaint = Paint().apply { color = Color.parseColor("#C8BE6E") }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 255, 255) }
    private val scoreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 90f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        setShadowLayer(6f, 0f, 4f, Color.argb(150, 0, 0, 0))
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 80f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        setShadowLayer(6f, 0f, 4f, Color.argb(150, 0, 0, 0))
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        setShadowLayer(4f, 0f, 3f, Color.argb(150, 0, 0, 0))
    }
    private val creditTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 30f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DED895")
        style = Paint.Style.FILL
    }
    private val panelOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8C7B3E")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val panelTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5B4A1E")
        textSize = 64f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val panelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5B4A1E")
        textSize = 46f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val flashPaint = Paint().apply { color = Color.WHITE }
    private var flashAlpha = 0
    private var elapsedTime = 0f

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ---------- SurfaceHolder.Callback ----------

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width
        screenHeight = height
        groundHeight = screenHeight * 0.3f
        bird = Bird(screenWidth, screenHeight)
        resume()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        screenWidth = w
        screenHeight = h
        groundHeight = screenHeight * 0.3f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pause()
        soundManager.release()
    }

    // ---------- Lifecycle ----------

    fun resume() {
        if (running) return
        running = true
        gameThread = Thread(this, "GameThread")
        lastFrameTimeNanos = System.nanoTime()
        gameThread?.start()
    }

    fun pause() {
        running = false
        try {
            gameThread?.join()
        } catch (e: InterruptedException) {
            // ignore
        }
        gameThread = null
    }

    // ---------- Game loop ----------

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            var deltaTime = (now - lastFrameTimeNanos) / 1_000_000_000f
            lastFrameTimeNanos = now
            // Clamp delta to avoid huge jumps after pause/lag
            if (deltaTime > 0.05f) deltaTime = 0.05f

            update(deltaTime)
            draw()

            // Cap roughly to 60fps if device is faster than expected
            val frameDuration = (System.nanoTime() - now) / 1_000_000
            val sleepTime = 16 - frameDuration
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime)
                } catch (e: InterruptedException) {
                    // ignore
                }
            }
        }
    }

    private fun update(deltaTime: Float) {
        if (!::bird.isInitialized) return
        elapsedTime += deltaTime

        if (flashAlpha > 0) {
            flashAlpha = (flashAlpha - 10).coerceAtLeast(0)
        }

        // Background always scrolls gently, even on the ready screen
        cloudScrollX -= 12f * deltaTime
        if (cloudScrollX < -screenWidth) cloudScrollX = 0f

        if (state == GameState.READY) {
            // Idle bobbing animation for the bird
            bird.y = screenHeight * 0.4f + sin(elapsedTime * 3f) * 12f
            return
        }

        if (state != GameState.PLAYING) return

        groundScrollX -= pipeSpeed * deltaTime
        if (groundScrollX < -80f) groundScrollX += 80f

        bird.update(deltaTime)

        // Spawn pipes
        distanceSinceLastPipe += pipeSpeed * deltaTime
        if (distanceSinceLastPipe >= pipeSpacing) {
            distanceSinceLastPipe = 0f
            spawnPipe()
        }

        // Update pipes & scoring
        val iterator = pipes.iterator()
        while (iterator.hasNext()) {
            val pipe = iterator.next()
            pipe.update(deltaTime, pipeSpeed)
            if (!pipe.scored && pipe.x + Pipe.WIDTH < bird.x) {
                pipe.scored = true
                score++
                soundManager.playScore()
            }
            if (pipe.isOffScreen()) {
                iterator.remove()
            }
        }

        checkCollisions()
    }

    private fun spawnPipe() {
        val margin = groundHeight + pipeGapHeight / 2f + 40f
        val maxCenter = screenHeight - margin
        val minCenter = pipeGapHeight / 2f + 80f
        val centerY = if (maxCenter > minCenter) {
            Random.nextFloat() * (maxCenter - minCenter) + minCenter
        } else {
            screenHeight / 2f
        }
        pipes.add(Pipe(screenWidth.toFloat(), centerY, pipeGapHeight, screenHeight))
    }

    private fun checkCollisions() {
        // Ground / ceiling collision
        if (bird.y + bird.getHitboxRadius() >= screenHeight - groundHeight) {
            bird.y = screenHeight - groundHeight - bird.getHitboxRadius()
            triggerGameOver()
            return
        }
        if (bird.y - bird.getHitboxRadius() <= 0) {
            bird.y = bird.getHitboxRadius()
            bird.velocityY = 0f
        }

        // Pipe collision (circle vs rect, approximate)
        for (pipe in pipes) {
            if (circleIntersectsRect(bird.x, bird.y, bird.getHitboxRadius(), pipe.topRect) ||
                circleIntersectsRect(bird.x, bird.y, bird.getHitboxRadius(), pipe.bottomRect)
            ) {
                triggerGameOver()
                return
            }
        }
    }

    private fun circleIntersectsRect(cx: Float, cy: Float, radius: Float, rect: RectF): Boolean {
        val closestX = cx.coerceIn(rect.left, rect.right)
        val closestY = cy.coerceIn(rect.top, rect.bottom)
        val dx = cx - closestX
        val dy = cy - closestY
        return (dx * dx + dy * dy) <= radius * radius
    }

    private fun triggerGameOver() {
        if (state == GameState.GAME_OVER) return
        state = GameState.GAME_OVER
        soundManager.playDie()
        flashAlpha = 200
        scoreManager.saveHighScoreIfBetter(score)
        highScore = scoreManager.getHighScore()
    }

    private fun resetGame() {
        bird.reset()
        pipes.clear()
        score = 0
        distanceSinceLastPipe = 0f
        state = GameState.READY
    }

    // ---------- Drawing ----------

    private fun draw() {
        if (!holder.surface.isValid || !::bird.isInitialized) return
        val canvas = holder.lockCanvas() ?: return
        try {
            drawSky(canvas)
            drawClouds(canvas)
            for (pipe in pipes) pipe.draw(canvas)
            drawGround(canvas)
            bird.draw(canvas)
            drawUi(canvas)
            if (flashAlpha > 0) {
                flashPaint.alpha = flashAlpha
                canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), flashPaint)
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawSky(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), skyPaint)
    }

    private fun drawClouds(canvas: Canvas) {
        val y = screenHeight * 0.18f
        var startX = cloudScrollX
        while (startX < screenWidth) {
            drawCloud(canvas, startX + 60f, y)
            drawCloud(canvas, startX + 320f, y + 80f)
            startX += screenWidth.toFloat()
        }
    }

    private fun drawCloud(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, 35f, cloudPaint)
        canvas.drawCircle(x + 30f, y - 10f, 28f, cloudPaint)
        canvas.drawCircle(x + 55f, y, 32f, cloudPaint)
    }

    private fun drawGround(canvas: Canvas) {
        val top = screenHeight - groundHeight
        canvas.drawRect(0f, top, screenWidth.toFloat(), screenHeight.toFloat(), groundPaint)
        // Scrolling stripe pattern for a sense of motion
        var x = groundScrollX
        while (x < screenWidth) {
            canvas.drawRect(x, top, x + 40f, top + 14f, groundStripePaint)
            x += 80f
        }
    }

    private fun drawUi(canvas: Canvas) {
        when (state) {
            GameState.READY -> {
                canvas.drawText("Flappy Bird", screenWidth / 2f, screenHeight * 0.18f, titlePaint)
                canvas.drawText("Tap to start", screenWidth / 2f, screenHeight * 0.18f + 60f, subTextPaint)
                canvas.drawText("Thanks to Himanshu to develop me", screenWidth / 2f, screenHeight * 0.18f + 880f, creditTextPaint)

                if (highScore > 0) {
                    canvas.drawText("Best: $highScore", screenWidth / 2f, screenHeight * 0.18f + 110f, subTextPaint)
                }
            }
            GameState.PLAYING -> {
                canvas.drawText(score.toString(), screenWidth / 2f, screenHeight * 0.12f, scoreTextPaint)
            }
            GameState.GAME_OVER -> {
                canvas.drawText(score.toString(), screenWidth / 2f, screenHeight * 0.12f, scoreTextPaint)
                drawGameOverPanel(canvas)
            }
        }
    }

    private fun drawGameOverPanel(canvas: Canvas) {
        val panelWidth = screenWidth * 0.75f
        val panelHeight = screenHeight * 0.3f
        val left = (screenWidth - panelWidth) / 2f
        val top = (screenHeight - panelHeight) / 2f
        val rect = RectF(left, top, left + panelWidth, top + panelHeight)

        canvas.drawRoundRect(rect, 24f, 24f, panelPaint)
        canvas.drawRoundRect(rect, 24f, 24f, panelOutlinePaint)

        canvas.drawText("Game Over", screenWidth / 2f, top + 70f, panelTitlePaint)
        canvas.drawText("Score: $score", screenWidth / 2f, top + 140f, panelTextPaint)
        canvas.drawText("Best: $highScore", screenWidth / 2f, top + 195f, panelTextPaint)
        canvas.drawText("Tap to retry", screenWidth / 2f, top + panelHeight - 30f, panelTextPaint)
    }

    // ---------- Input ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            performClick()
            when (state) {
                GameState.READY -> {
                    if (::bird.isInitialized) {
                        state = GameState.PLAYING
                        bird.flap()
                        soundManager.playFlap()
                    }
                }
                GameState.PLAYING -> {
                    bird.flap()
                    soundManager.playFlap()
                }
                GameState.GAME_OVER -> {
                    resetGame()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
