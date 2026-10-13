package com.soltini.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class RingState { IDLE, LISTENING, SPEAKING, THINKING, SLEEPING }

/**
 * Floating Iridescent Glass Orb Indicator View for the system overlay.
 *
 * Renders a soft, glossy 3D pastel glass sphere (pink-purple-blue gradient
 * with specular highlights, swirling pastel caustics, and a luminous glass rim)
 * matching the Myra AI Soft Light Glassmorphic design system.
 */
class RingIndicatorView(context: Context) : View(context) {

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val spherePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val causticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val highlightRect = RectF()

    /** Raw mic/speaker amplitude (0f..~1f). */
    var amplitude: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var state: RingState = RingState.IDLE
        set(value) {
            field = value
            pulseAnimator?.cancel()
            when (value) {
                RingState.IDLE -> {
                    pulseScale = 1f
                    rotationSpeed = 0.025f
                    invalidate()
                }
                RingState.SLEEPING -> {
                    rotationSpeed = 0.01f
                    pulseAnimator = ValueAnimator.ofFloat(0.92f, 1.0f, 0.92f).apply {
                        duration = 3000
                        repeatCount = ValueAnimator.INFINITE
                        interpolator = LinearInterpolator()
                        addUpdateListener {
                            pulseScale = it.animatedValue as Float
                            invalidate()
                        }
                        start()
                    }
                }
                RingState.LISTENING -> startPulse(speed = 0.045f, maxScale = 1.06f)
                RingState.SPEAKING -> startPulse(speed = 0.065f, maxScale = 1.10f)
                RingState.THINKING -> startPulse(speed = 0.055f, maxScale = 1.07f)
            }
        }

    private var pulseScale = 1f
    private var rotationSpeed = 0.025f
    private var phase = 0f
    private var pulseAnimator: ValueAnimator? = null

    private fun startPulse(speed: Float, maxScale: Float) {
        rotationSpeed = speed
        pulseAnimator = ValueAnimator.ofFloat(0.96f, maxScale, 0.96f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        if (cx <= 0f || cy <= 0f) {
            invalidate()
            return
        }

        val minDim = min(width, height).toFloat()
        val ampBoost = 1f + (amplitude.coerceIn(0f, 1f) * 0.35f)
        val orbRadius = (minDim * 0.32f) * pulseScale * ampBoost

        val isSleeping = state == RingState.SLEEPING
        val alphaMul = if (isSleeping) 0.55f else 1f

        val corePink = if (isSleeping) Color.argb(180, 216, 212, 230) else Color.argb(245, 232, 160, 224)    // #E8A0E0
        val midPurple = if (isSleeping) Color.argb(180, 184, 179, 200) else Color.argb(245, 185, 147, 240)   // #B993F0
        val rimBlue = if (isSleeping) Color.argb(170, 162, 158, 181) else Color.argb(240, 142, 197, 252)     // #8EC5FC
        val mintAccent = Color.argb((110 * alphaMul).toInt(), 169, 232, 201)                                  // #A9E8C9

        // 1. Soft pastel drop glow behind sphere
        glowPaint.shader = RadialGradient(
            cx,
            cy + orbRadius * 0.1f,
            orbRadius * 1.55f,
            intArrayOf(
                Color.argb((110 * alphaMul).toInt(), 185, 147, 240),
                Color.argb((55 * alphaMul).toInt(), 232, 160, 224),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy + orbRadius * 0.1f, orbRadius * 1.55f, glowPaint)

        // 2. Subtle outer ripple ring when active
        if (!isSleeping) {
            ripplePaint.strokeWidth = minDim * 0.014f
            ripplePaint.shader = LinearGradient(
                cx - orbRadius, cy - orbRadius,
                cx + orbRadius, cy + orbRadius,
                intArrayOf(corePink, midPurple, rimBlue),
                null,
                Shader.TileMode.CLAMP
            )
            ripplePaint.alpha = 120
            canvas.drawCircle(cx, cy, orbRadius * (1.16f + amplitude * 0.18f), ripplePaint)
        }

        // 3. Main 3D Iridescent Sphere Body
        val gradCenterX = cx - orbRadius * 0.26f + sin(phase) * (orbRadius * 0.08f)
        val gradCenterY = cy - orbRadius * 0.28f + cos(phase) * (orbRadius * 0.08f)
        spherePaint.shader = RadialGradient(
            gradCenterX,
            gradCenterY,
            orbRadius * 1.38f,
            intArrayOf(
                Color.argb((250 * alphaMul).toInt(), 255, 255, 255),
                corePink,
                midPurple,
                rimBlue,
                midPurple
            ),
            floatArrayOf(0f, 0.24f, 0.58f, 0.85f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, orbRadius, spherePaint)

        // 4. Swirling inner pastel mint/pink caustic wave
        val swirlX = cx + orbRadius * 0.24f * cos(phase)
        val swirlY = cy + orbRadius * 0.20f * sin(phase)
        causticPaint.shader = RadialGradient(
            swirlX,
            swirlY,
            orbRadius * 0.78f,
            intArrayOf(
                mintAccent,
                Color.argb((75 * alphaMul).toInt(), 232, 160, 224),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(swirlX, swirlY, orbRadius * 0.78f, causticPaint)

        // 5. Top-left curved glass specular highlight
        val hlLeft = cx - orbRadius * 0.52f
        val hlTop = cy - orbRadius * 0.76f
        val hlRight = hlLeft + orbRadius * 0.92f
        val hlBottom = hlTop + orbRadius * 0.50f
        highlightRect.set(hlLeft, hlTop, hlRight, hlBottom)
        highlightPaint.shader = LinearGradient(
            cx, hlTop, cx, hlBottom,
            Color.argb((225 * alphaMul).toInt(), 255, 255, 255),
            Color.argb((25 * alphaMul).toInt(), 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(highlightRect, highlightPaint)

        // 6. Luminous outer glass rim border
        rimPaint.strokeWidth = minDim * 0.018f
        rimPaint.shader = LinearGradient(
            cx - orbRadius, cy - orbRadius,
            cx + orbRadius, cy + orbRadius,
            intArrayOf(
                Color.argb((220 * alphaMul).toInt(), 255, 255, 255),
                corePink,
                Color.argb((180 * alphaMul).toInt(), 255, 255, 255)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, orbRadius, rimPaint)

        phase += rotationSpeed * (1f + amplitude.coerceIn(0f, 1f))
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
    }
}
