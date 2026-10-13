package com.soltini.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.soltini.app.agent.TaskSnapshot
import com.soltini.app.agent.TaskState

/**
 * Floating progress card for the phone agent: goal, progress bar, step counter, what it is doing right now,
 * the plan, and Pause / Resume / Cancel (plus Yes / No when the agent is waiting for an answer).
 *
 * Built in code (no XML). Hidden from accessibility so the agent can never "click" its own HUD.
 */
class AgentHudView(context: Context) : LinearLayout(context) {

    var onPause: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onAnswer: ((String) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private val statusChip = TextView(context)
    private val goalText = TextView(context)
    private val bar = ProgressBarView(context)
    private val infoText = TextView(context)
    private val actionText = TextView(context)
    private val planText = TextView(context)
    private val questionText = TextView(context)
    private val answerRow = LinearLayout(context)
    private val controlRow = LinearLayout(context)
    private val pauseBtn: TextView
    private val cancelBtn: TextView

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(Color.parseColor("#EE12161C"))
            setStroke(dp(1), Color.parseColor("#33FFFFFF"))
        }
        elevation = dp(10).toFloat()
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

        // header
        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            text = "Myra  •  Task"; setTextColor(Color.WHITE); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        statusChip.apply { textSize = 11f; setTypeface(typeface, Typeface.BOLD); setPadding(dp(8), dp(2), dp(8), dp(2)) }
        header.addView(statusChip)
        header.addView(TextView(context).apply {
            text = "✕"; setTextColor(Color.parseColor("#B0BEC5")); textSize = 16f; setPadding(dp(12), 0, 0, 0)
            setOnClickListener { onClose?.invoke() }
        })
        addView(header)

        goalText.apply { setTextColor(Color.WHITE); textSize = 13f; maxLines = 2; setPadding(0, dp(6), 0, dp(8)) }
        addView(goalText)
        addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)))
        infoText.apply { setTextColor(Color.parseColor("#90A4AE")); textSize = 11f; setPadding(0, dp(6), 0, 0) }
        addView(infoText)
        actionText.apply {
            setTextColor(Color.parseColor("#B2EBF2")); textSize = 11f; typeface = Typeface.MONOSPACE
            maxLines = 2; setPadding(0, dp(6), 0, 0)
        }
        addView(actionText)
        planText.apply { setTextColor(Color.parseColor("#78909C")); textSize = 11f; maxLines = 5; setPadding(0, dp(6), 0, 0) }
        addView(planText)

        questionText.apply {
            setTextColor(Color.parseColor("#FFCA28")); textSize = 12.5f; setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(4))
        }
        addView(questionText)

        answerRow.orientation = HORIZONTAL
        answerRow.addView(button("Yes / Done", "#2E7D32") { onAnswer?.invoke("yes") }, btnParams())
        answerRow.addView(button("No", "#B71C1C") { onAnswer?.invoke("no") }, btnParams())
        addView(answerRow)

        controlRow.orientation = HORIZONTAL
        controlRow.setPadding(0, dp(10), 0, 0)
        pauseBtn = button("Pause", "#37474F") { }
        cancelBtn = button("Cancel task", "#B71C1C") { onCancel?.invoke() }
        controlRow.addView(pauseBtn, btnParams())
        controlRow.addView(cancelBtn, btnParams())
        addView(controlRow)
    }

    private fun btnParams() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }

    private fun button(label: String, color: String, click: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 12.5f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(8), dp(9), dp(8), dp(9))
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor(color)) }
        setOnClickListener { click() }
    }

    private fun stateColor(s: TaskState): Int = Color.parseColor(
        when (s) {
            TaskState.RUNNING -> "#4DD0E1"
            TaskState.PLANNING -> "#81D4FA"
            TaskState.WAITING_USER -> "#FFB300"
            TaskState.PAUSED -> "#BDBDBD"
            TaskState.COMPLETED -> "#66BB6A"
            TaskState.FAILED -> "#EF5350"
            TaskState.CANCELLED -> "#BDBDBD"
        }
    )

    /** Refreshes every widget from the latest snapshot (null = nothing has run yet). */
    fun update(s: TaskSnapshot?) {
        if (s == null) {
            statusChip.text = "IDLE"; statusChip.setTextColor(Color.parseColor("#B0BEC5"))
            goalText.text = "No phone task is running. Ask Myra to do something on your phone and tap the orb to watch it."
            bar.progress = 0f
            infoText.text = ""; actionText.text = ""; planText.text = ""
            questionText.visibility = GONE; answerRow.visibility = GONE; controlRow.visibility = GONE
            return
        }
        val active = s.state == TaskState.PLANNING || s.state == TaskState.RUNNING ||
            s.state == TaskState.WAITING_USER || s.state == TaskState.PAUSED

        statusChip.text = s.state.name.replace('_', ' ')
        statusChip.setTextColor(stateColor(s.state))
        goalText.text = s.goal
        bar.color = stateColor(s.state)
        bar.progress = s.progressPercent / 100f
        infoText.text = buildString {
            append("Step ${s.step} of ${s.maxSteps}  •  ${s.progressPercent}%")
            if (s.processedItems > 0) append("  •  items ${s.processedItems}")
            if (s.recoveries > 0) append("  •  recoveries ${s.recoveries}")
        }
        actionText.text = if (s.lastAction.isNotBlank()) "▶ ${s.lastAction}" else ""
        planText.text = if (s.plan.isNotEmpty()) s.plan.mapIndexed { i, p -> "${i + 1}) $p" }.joinToString("\n") else ""

        val q = s.pendingQuestion
        questionText.visibility = if (q != null) VISIBLE else GONE
        answerRow.visibility = if (q != null) VISIBLE else GONE
        if (q != null) questionText.text = q

        controlRow.visibility = if (active) VISIBLE else GONE
        val paused = s.state == TaskState.PAUSED
        pauseBtn.text = if (paused) "Resume" else "Pause"
        pauseBtn.setOnClickListener { if (paused) onResume?.invoke() else onPause?.invoke() }

        if (!active) {
            // finished: show how it ended
            actionText.text = if (s.lastResult.isNotBlank()) "Last: ${s.lastResult.take(90)}" else actionText.text
        }
    }

    private class ProgressBarView(context: Context) : View(context) {
        var progress = 0f
            set(v) { field = v.coerceIn(0f, 1f); invalidate() }
        var color: Int = Color.parseColor("#4DD0E1")
            set(v) { field = v; invalidate() }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val h = height.toFloat()
            p.color = Color.parseColor("#26FFFFFF")
            r.set(0f, 0f, width.toFloat(), h)
            c.drawRoundRect(r, h / 2, h / 2, p)
            p.color = color
            r.set(0f, 0f, width * progress, h)
            if (r.width() > 0f) c.drawRoundRect(r, h / 2, h / 2, p)
        }
    }
}
