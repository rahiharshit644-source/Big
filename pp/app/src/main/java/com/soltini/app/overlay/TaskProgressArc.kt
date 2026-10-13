package com.soltini.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Thin progress ring drawn around the floating orb while a phone task runs.
 * Cyan = working, amber (full ring) = the agent is waiting for you, hidden when no task.
 */
class TaskProgressArc(private val density: Float) {

    @Volatile var percent: Int = -1       // -1 = hidden
    @Volatile var waiting: Boolean = false

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(60, 255, 255, 255)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    fun draw(canvas: Canvas, width: Int, height: Int) {
        val p = percent
        if (p < 0 || width == 0 || height == 0) return
        val stroke = 3.5f * density
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        val inset = stroke / 2f + density
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(rect, 0f, 360f, false, track)
        arc.color = if (waiting) Color.parseColor("#FFB300") else Color.parseColor("#4DD0E1")
        val sweep = if (waiting) 360f else 360f * p.coerceIn(2, 100) / 100f
        canvas.drawArc(rect, -90f, sweep, false, arc)
    }
}
