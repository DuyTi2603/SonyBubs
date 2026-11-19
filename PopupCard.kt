package com.example.sonybuds

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/** Vòng tròn pin có animation, kiểu HeyMelody */
class RingView(c: Context, private val label: String) : View(c) {
    private var shown = 0f
    private var target = -1
    var charging = false
    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7 * d; color = 0x22FFFFFF }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7 * d; strokeCap = Paint.Cap.ROUND }
    private val pct = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 19 * d; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val lab = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9AA0A6.toInt(); textSize = 12 * d; textAlign = Paint.Align.CENTER }
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF34D399.toInt(); textSize = 13 * d; textAlign = Paint.Align.CENTER }

    fun setLevel(v: Int, chg: Boolean) {
        charging = chg
        if (v == target) { invalidate(); return }
        val from = shown
        target = v
        ValueAnimator.ofFloat(from, v.coerceAtLeast(0).toFloat()).apply {
            duration = 700; interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
        }.start()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (118 * d).toInt())
    }

    override fun onDraw(cv: Canvas) {
        val size = 84 * d
        val cx = width / 2f
        val r = RectF(cx - size / 2 + 4 * d, 4 * d, cx + size / 2 - 4 * d, size - 4 * d)
        cv.drawArc(r, 0f, 360f, false, track)
        arc.color = when { charging -> 0xFF34D399.toInt(); target in 0..20 -> 0xFFF87171.toInt(); else -> 0xFF60A5FA.toInt() }
        cv.drawArc(r, -90f, 360f * shown / 100f, false, arc)
        val txt = if (target < 0) "--" else "${shown.toInt()}%"
        cv.drawText(txt, cx, r.centerY() + 7 * d, pct)
        if (charging) cv.drawText("⚡", cx, r.centerY() - 14 * d, bolt)
        cv.drawText(label, cx, size + 18 * d, lab)
    }
}

class PopupCard(c: Context, name: String) : LinearLayout(c) {
    val left = RingView(c, "Tai trái")
    val case = RingView(c, "Hộp sạc")
    val right = RingView(c, "Tai phải")

    init {
        val d = resources.displayMetrics.density
        orientation = VERTICAL
        setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (10 * d).toInt())
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xF21F2937.toInt(), 0xF2111827.toInt())).apply {
            cornerRadius = 32 * d
        }
        elevation = 16 * d

        val dot = View(c).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF34D399.toInt()) }
            layoutParams = LayoutParams((8 * d).toInt(), (8 * d).toInt()).apply { rightMargin = (8 * d).toInt() }
        }
        val title = TextView(c).apply { text = name; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }
        val sub = TextView(c).apply { text = "Đã kết nối"; textSize = 12f; setTextColor(0xFF34D399.toInt()) }
        val titles = LinearLayout(c).apply {
            orientation = VERTICAL; addView(title); addView(sub)
        }
        val head = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, (6 * d).toInt())
            addView(dot); addView(titles)
        }
        val row = LinearLayout(c).apply {
            orientation = HORIZONTAL
            for (v in listOf(left, case, right)) addView(v, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(head); addView(row)
    }

    fun update(b: Battery) {
        left.setLevel(b.left, b.leftCharging)
        right.setLevel(b.right, b.rightCharging)
        case.setLevel(b.case, b.caseCharging)
    }
}
