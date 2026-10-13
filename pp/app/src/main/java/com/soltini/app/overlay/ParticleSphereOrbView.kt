package com.soltini.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Floating "Particle Sphere" orb — a 360° sphere made of thousands of tiny particles
 * (native port of the "untitled" CodePen: Three.js points on a sphere).
 *
 * Colour behaviour is intentionally identical to the classic wireframe orb:
 *  • a rainbow hue ramp wraps around the sphere (hue = longitude),
 *  • saturation / brightness change with [RingState]
 *    (SLEEPING = dim & near-grayscale, IDLE soft, LISTENING / THINKING richer, SPEAKING most vivid),
 *  • speed + pulse follow the state and the live voice [amplitude].
 *
 * Drawn with plain Canvas (no WebView / GL) and batched `drawPoints` calls, so it is cheap
 * enough to run inside the system overlay window.
 */
class ParticleSphereOrbView(context: Context) : View(context), OrbIndicator {

    companion object {
        private const val POINT_COUNT = 2600
        private const val HUE_BUCKETS = 24
        private const val DEPTH_BUCKETS = 3          // 0 = back, 1 = middle, 2 = front
        private const val CAMERA_DISTANCE = 3.2f
    }

    // ── Public contract ────────────────────────────────────────────────────────
    private val taskArc = TaskProgressArc(context.resources.displayMetrics.density)

    override fun setTaskProgress(percent: Int, waiting: Boolean) {
        taskArc.percent = percent
        taskArc.waiting = waiting
    }

    override var amplitude: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    override var state: RingState = RingState.IDLE
        set(value) {
            field = value
            pulseAnimator?.cancel()
            when (value) {
                RingState.IDLE -> {
                    saturation = 0.55f; brightness = 0.80f; rotationSpeed = 0.012f; pulseScale = 1f
                }
                RingState.SLEEPING -> {
                    saturation = 0.10f; brightness = 0.35f; rotationSpeed = 0.004f
                    startPulse(0.92f, 1.0f, 3000)
                }
                RingState.LISTENING -> {
                    saturation = 0.75f; brightness = 0.95f; rotationSpeed = 0.020f
                    startPulse(0.96f, 1.06f, 1500)
                }
                RingState.SPEAKING -> {
                    saturation = 0.85f; brightness = 1.00f; rotationSpeed = 0.034f
                    startPulse(0.96f, 1.10f, 1500)
                }
                RingState.THINKING -> {
                    saturation = 0.80f; brightness = 0.95f; rotationSpeed = 0.027f
                    startPulse(0.96f, 1.07f, 1500)
                }
            }
            rebuildColors()
            invalidate()
        }

    // ── Animation state ────────────────────────────────────────────────────────
    private var saturation = 0.55f
    private var brightness = 0.80f
    private var rotationSpeed = 0.012f
    private var pulseScale = 1f
    private var angleY = 0f
    private var wobble = 0f
    private var pulseAnimator: ValueAnimator? = null

    private fun startPulse(from: Float, to: Float, durationMs: Long) {
        pulseAnimator = ValueAnimator.ofFloat(from, to, from).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ── Geometry: Fibonacci sphere, grouped by hue bucket (hue = longitude) ────
    private val px = FloatArray(POINT_COUNT)
    private val py = FloatArray(POINT_COUNT)
    private val pz = FloatArray(POINT_COUNT)
    private val bucketStart = IntArray(HUE_BUCKETS + 1)
    private var fitScale = 1f

    // Per (hue, depth) bucket: reusable coordinate buffer + paint.
    private val buffers: Array<FloatArray>
    private val counts = IntArray(HUE_BUCKETS * DEPTH_BUCKETS)
    private val paints = Array(HUE_BUCKETS * DEPTH_BUCKETS) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }

    init {
        val golden = (PI * (3.0 - sqrt(5.0))).toFloat()
        val tmpX = FloatArray(POINT_COUNT)
        val tmpY = FloatArray(POINT_COUNT)
        val tmpZ = FloatArray(POINT_COUNT)
        val bucketOf = IntArray(POINT_COUNT)
        val perBucket = IntArray(HUE_BUCKETS)

        for (i in 0 until POINT_COUNT) {
            val y = 1f - 2f * (i + 0.5f) / POINT_COUNT
            val r = sqrt(1f - y * y)
            val theta = i * golden
            tmpX[i] = r * cos(theta)
            tmpY[i] = y
            tmpZ[i] = r * sin(theta)
            var lon = theta % (2f * PI.toFloat())
            if (lon < 0f) lon += 2f * PI.toFloat()
            val b = ((lon / (2f * PI.toFloat())) * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
            bucketOf[i] = b
            perBucket[b]++
        }
        for (b in 0 until HUE_BUCKETS) bucketStart[b + 1] = bucketStart[b] + perBucket[b]
        val fill = IntArray(HUE_BUCKETS)
        for (i in 0 until POINT_COUNT) {
            val b = bucketOf[i]
            val dst = bucketStart[b] + fill[b]++
            px[dst] = tmpX[i]; py[dst] = tmpY[i]; pz[dst] = tmpZ[i]
        }
        buffers = Array(HUE_BUCKETS * DEPTH_BUCKETS) { idx ->
            FloatArray(perBucket[idx / DEPTH_BUCKETS] * 2)
        }
        rebuildColors()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitScale = min(w, h) / 360f
        rebuildColors()
    }

    /** Same idea as the classic orb: rainbow hue ramp, saturation by state. */
    private fun rebuildColors() {
        val hsv = FloatArray(3)
        val depthAlpha = floatArrayOf(0.30f, 0.62f, 1.00f)
        val depthWidth = floatArrayOf(2.6f, 3.6f, 5.0f)
        for (h in 0 until HUE_BUCKETS) {
            hsv[0] = (h + 0.5f) / HUE_BUCKETS * 360f
            hsv[1] = saturation
            hsv[2] = 1f
            val base = Color.HSVToColor(hsv)
            for (d in 0 until DEPTH_BUCKETS) {
                val a = (255f * brightness * depthAlpha[d]).toInt().coerceIn(10, 255)
                val paint = paints[h * DEPTH_BUCKETS + d]
                paint.color = Color.argb(a, Color.red(base), Color.green(base), Color.blue(base))
                paint.strokeWidth = depthWidth[d] * fitScale.coerceAtLeast(0.5f)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            invalidate()
            return
        }
        val cx = w / 2f
        val cy = h / 2f
        val amp = amplitude.coerceIn(0f, 1f)
        val radius = min(w, h) * 0.36f * pulseScale * (1f + amp * 0.35f)

        val cosY = cos(angleY); val sinY = sin(angleY)
        val tilt = 0.38f + sin(wobble) * 0.08f
        val cosX = cos(tilt); val sinX = sin(tilt)

        java.util.Arrays.fill(counts, 0)

        for (hb in 0 until HUE_BUCKETS) {
            for (i in bucketStart[hb] until bucketStart[hb + 1]) {
                val x = px[i]; val y = py[i]; val z = pz[i]
                // rotate around Y, then tilt around X
                val x1 = x * cosY + z * sinY
                val z1 = -x * sinY + z * cosY
                val y2 = y * cosX - z1 * sinX
                val z2 = y * sinX + z1 * cosX
                val persp = CAMERA_DISTANCE / (CAMERA_DISTANCE - z2)
                val sx = cx + x1 * radius * persp
                val sy = cy + y2 * radius * persp
                val db = when {
                    z2 > 0.33f -> 2
                    z2 > -0.33f -> 1
                    else -> 0
                }
                val bi = hb * DEPTH_BUCKETS + db
                val buf = buffers[bi]
                val n = counts[bi]
                buf[n * 2] = sx
                buf[n * 2 + 1] = sy
                counts[bi] = n + 1
            }
        }

        // back -> front so near particles sit on top
        for (d in 0 until DEPTH_BUCKETS) {
            for (hb in 0 until HUE_BUCKETS) {
                val bi = hb * DEPTH_BUCKETS + d
                val n = counts[bi]
                if (n > 0) canvas.drawPoints(buffers[bi], 0, n * 2, paints[bi])
            }
        }

        taskArc.draw(canvas, width, height)
        angleY += rotationSpeed * (1f + amp)
        wobble += 0.006f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
    }
}
