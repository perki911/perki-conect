package com.perki.conect

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class GaugeView(c: Context) : View(c) {
    var value = 0.0
        set(x) { field = x; postInvalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun fr(v: Double) = (Math.log(1 + v) / Math.log(1001.0)).coerceIn(0.0, 1.0)

    override fun onMeasure(w: Int, h: Int) {
        val s = MeasureSpec.getSize(w)
        setMeasuredDimension(s, (s * 0.84).toInt())
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val cx = w / 2; val cy = w * 0.42f; val r = w * 0.34f
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.045f; p.strokeCap = Paint.Cap.ROUND
        p.color = Color.parseColor("#D3DDEA"); cv.drawArc(rect, 135f, 270f, false, p)
        p.color = Color.parseColor("#378ADD"); cv.drawArc(rect, 135f, (270 * fr(value)).toFloat().coerceAtLeast(1f), false, p)
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.textSize = w * 0.035f; p.color = Color.GRAY
        listOf(0, 10, 50, 100, 250, 500, 1000).forEach { t ->
            val a = Math.toRadians(135 + 270 * fr(t.toDouble()))
            cv.drawText("$t", cx + (r - w * 0.085f) * cos(a).toFloat(), cy + (r - w * 0.085f) * sin(a).toFloat() + w * 0.012f, p)
        }
        val a = Math.toRadians(135 + 270 * fr(value))
        p.color = Color.parseColor("#0C447C"); p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.012f
        cv.drawLine(cx, cy, cx + (r - w * 0.13f) * cos(a).toFloat(), cy + (r - w * 0.13f) * sin(a).toFloat(), p)
        p.style = Paint.Style.FILL; cv.drawCircle(cx, cy, w * 0.025f, p)
        p.textSize = w * 0.11f; cv.drawText("%.1f".format(value), cx, cy + r * 0.95f, p)
        p.textSize = w * 0.04f; p.color = Color.GRAY; cv.drawText("Mbps", cx, cy + r * 0.95f + w * 0.055f, p)
    }
}
