package app.helio.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import app.helio.R
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the home-screen widget as one transparent image, exactly as designed on the canvas:
 * week by day (colour climbing from blue through teal and green to gold as the goal fills), today's
 * strain inside a recovery-coloured budget, recovery and sleep. Narrow (cover screen): minimal.
 * Wide (open screen): the same in the corner, with the details.
 */
object WidgetRender {
    private val STOPS = listOf(0.0 to intArrayOf(46, 111, 216), 0.5 to intArrayOf(79, 180, 255), 0.8 to intArrayOf(63, 215, 196), 1.0 to intArrayOf(47, 213, 102), 1.08 to intArrayOf(242, 201, 76))

    /** The week's colour at [f] of the goal. */
    fun weekColor(f: Double): Int {
        val x = f.coerceIn(0.0, 1.25)
        for (i in 0 until STOPS.size - 1) {
            val (a, ca) = STOPS[i]; val (b, cb) = STOPS[i + 1]
            if (x <= b) {
                val t = ((x - a) / (b - a)).toFloat()
                return Color.rgb((ca[0] + (cb[0] - ca[0]) * t).toInt(), (ca[1] + (cb[1] - ca[1]) * t).toInt(), (ca[2] + (cb[2] - ca[2]) * t).toInt())
            }
        }
        return Color.rgb(242, 201, 76)
    }

    private fun recColor(r: Int?) = when { r == null -> Color.rgb(142, 153, 164); r >= 67 -> Color.rgb(47, 213, 102); r >= 34 -> Color.rgb(242, 210, 46); else -> Color.rgb(232, 56, 79) }
    private val BLUE = Color.rgb(79, 180, 255)
    private val SLEEP = Color.rgb(159, 180, 204)
    private val STAGE = mapOf(0 to Color.rgb(233, 237, 241), 1 to Color.rgb(142, 157, 255), 2 to Color.rgb(91, 155, 201), 3 to Color.rgb(45, 92, 166))
    private fun white(a: Float) = Color.argb((a * 255).toInt(), 255, 255, 255)

    fun render(ctx: Context, s: WidgetStore.Snapshot, wDp: Float, hDp: Float, density: Float): Bitmap {
        // Keep the image well under the widget memory limit.
        val scale = min(density, sqrtLimit(wDp, hDp))
        val bmp = Bitmap.createBitmap(max(1, (wDp * scale).toInt()), max(1, (hDp * scale).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        val p = Painter(ctx)
        val detailed = wDp >= 270f && hDp >= 170f
        if (detailed) p.detailed(c, s, min(wDp - 8f, 330f)) else p.minimal(c, s, min(wDp - 8f, 200f))
        return bmp
    }

    private fun sqrtLimit(w: Float, h: Float): Float = kotlin.math.sqrt(1_200_000f / (w * h))

    private class Painter(ctx: Context) {
        val tight: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_tight_700) ?: Typeface.DEFAULT_BOLD
        val tightMid: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_tight_600) ?: Typeface.DEFAULT_BOLD
        val inter: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_600) ?: Typeface.DEFAULT
        val interReg: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_500) ?: Typeface.DEFAULT
        val shadow = Color.argb(110, 0, 0, 0)

        fun text(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, face: Typeface, right: Boolean = false, spacing: Float = 0f): Float {
            val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = color; typeface = face; letterSpacing = spacing
                setShadowLayer(3f, 0f, 1f, shadow)
                if (right) textAlign = Paint.Align.RIGHT
            }
            c.drawText(t, x, y, pt)
            return pt.measureText(t)
        }

        fun cap(c: Canvas, t: String, x: Float, y: Float, size: Float = 8.5f, color: Int = white(0.74f), right: Boolean = false) =
            text(c, t.uppercase(), x, y, size, color, inter, right, 0.08f)

        fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, paint: Paint) = c.drawRoundRect(RectF(l, t, r, b), rad, rad, paint)

        fun weekBar(c: Canvas, s: WidgetStore.Snapshot, x: Float, y: Float, w: Float, h: Float, labels: Boolean) {
            val wk = s.week ?: return
            val scale = wk.goal * 1.25
            rr(c, x, y, x + w, y + h, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white(0.13f) })
            var cum = 0.0; var px = x
            wk.daily.forEachIndexed { i, d ->
                if (d == null || d <= 0) return@forEachIndexed
                val a = cum / wk.goal; cum += d; val b = cum / wk.goal
                val sw = (d / scale * w).toFloat()
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(px, 0f, px + sw, 0f, weekColor(a), weekColor(b), Shader.TileMode.CLAMP)
                    if (i == wk.dayIndex) setShadowLayer(6f, 0f, 0f, weekColor(b))
                }
                rr(c, px, y, min(px + sw - 2f, x + w), y + h, 3f, paint)
                if (labels) {
                    val letter = "MTWTFSS"[i].toString()
                    val full = "$letter ${String.format("%.1f", d)}"
                    val m = Paint().apply { textSize = 8.5f; typeface = tightMid }
                    val shown = when { m.measureText(full) <= sw - 3f -> full; m.measureText(letter) <= sw - 2f -> letter; else -> null }
                    shown?.let { text(c, it, px, y + h + 11f, 8.5f, if (i == wk.dayIndex) Color.WHITE else white(0.62f), tightMid) }
                }
                px += sw
            }
            val gx = x + (wk.goal / scale * w).toFloat()
            rr(c, gx - 1f, y - 3f, gx + 1f, y + h + 3f, 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            val ax = x + (min(wk.pace, scale) / scale * w).toFloat()
            c.drawPath(Path().apply { moveTo(ax - 3f, y + h + 6.5f); lineTo(ax + 3f, y + h + 6.5f); lineTo(ax, y + h + 2.5f); close() },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white(0.85f) })
        }

        fun strainBar(c: Canvas, s: WidgetStore.Snapshot, x: Float, y: Float, w: Float, h: Float) {
            val rc = recColor(s.recovery)
            rr(c, x, y, x + w, y + h, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white(0.10f) })
            s.target?.let { t ->
                rr(c, x + 0.5f, y + 0.5f, x + (t / 21.0 * w).toFloat() - 0.5f, y + h - 0.5f, h / 2,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = rc })
            }
            s.strain?.let { st ->
                val fw = (st / 21.0 * w).toFloat().coerceAtLeast(h)
                rr(c, x + 2f, y + 2f, x + fw - 2f, y + h - 2f, (h - 4f) / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE; setShadowLayer(5f, 0f, 0f, BLUE) })
            }
        }

        fun dot(c: Canvas, x: Float, y: Float, r: Float, color: Int) = c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })

        /** Cover screen: week, strain, recovery and sleep. Nothing else. */
        fun minimal(c: Canvas, s: WidgetStore.Snapshot, w: Float) {
            val x = 4f
            var y = 18f
            cap(c, "Week", x, y)
            val f = s.week?.let { it.cumulative / it.goal } ?: 0.0
            text(c, s.week?.let { String.format("%.1f", it.cumulative) } ?: "—", x + w, y + 1f, 20f, weekColor(f), tight, right = true)
            y += 7f
            weekBar(c, s, x, y, w, 10f, labels = false)
            y += 10f + 24f
            cap(c, "Strain", x, y)
            text(c, s.strain?.let { String.format("%.1f", it) } ?: "—", x + w, y + 1f, 20f, Color.WHITE, tight, right = true)
            y += 7f
            strainBar(c, s, x, y, w, 10f)
            y += 10f + 18f
            var cx = x
            dot(c, cx + 3f, y - 4f, 3f, recColor(s.recovery)); cx += 10f
            cx += text(c, s.recovery?.toString() ?: "—", cx, y, 13f, Color.WHITE, tightMid) + 4f
            cx += cap(c, "Rec", cx, y) + 14f
            dot(c, cx + 3f, y - 4f, 3f, SLEEP); cx += 10f
            cx += text(c, s.sleepScore?.toString() ?: "—", cx, y, 13f, Color.WHITE, tightMid) + 4f
            cap(c, "Sleep", cx, y)
        }

        private fun head(c: Canvas, label: String, info: String, value: String, valueColor: Int, x: Float, y: Float, w: Float) {
            cap(c, label, x, y)
            val vw = text(c, value, x + w, y + 1f, 18f, valueColor, tight, right = true)
            text(c, info, x + w - vw - 8f, y, 9.5f, white(0.74f), interReg, right = true)
        }

        /** Open screen: the same in the top-left quarter, with the details. */
        fun detailed(c: Canvas, s: WidgetStore.Snapshot, w: Float) {
            val x = 4f
            var y = 18f
            val wk = s.week
            val f = wk?.let { it.cumulative / it.goal } ?: 0.0
            val info = wk?.let { g ->
                val d = g.cumulative - g.pace
                "${(f * 100).toInt()}% of ${String.format("%.0f", g.goal)} · ${String.format("%.1f", kotlin.math.abs(d))} ${if (d >= 0) "ahead" else "behind"}"
            } ?: ""
            head(c, "Week", info, wk?.let { String.format("%.1f", it.cumulative) } ?: "—", weekColor(f), x, y, w)
            y += 6f
            weekBar(c, s, x, y, w, 10f, labels = true)
            y += 10f + 12f + 20f

            val sInfo = listOfNotNull(s.target?.let { "target ${String.format("%.1f", it)}" }, s.activity).joinToString(" · ")
            head(c, "Strain", sInfo, s.strain?.let { String.format("%.1f", it) } ?: "—", Color.WHITE, x, y, w)
            y += 6f
            strainBar(c, s, x, y, w, 8f)
            y += 8f + 22f

            val sl = listOfNotNull(s.asleepMin?.let { "${it / 60}:${String.format("%02d", it % 60)}" }, s.bedWake).joinToString(" · ")
            head(c, "Sleep", sl, s.sleepScore?.toString() ?: "—", Color.WHITE, x, y, w)
            y += 6f
            val st = s.stages
            if (st.isNotEmpty() && s.sleepEnd > s.sleepStart) {
                val span = (s.sleepEnd - s.sleepStart).toFloat()
                st.forEach { (a, b, lvl) ->
                    val l = x + (a - s.sleepStart) / span * w; val r = x + (b - s.sleepStart) / span * w
                    rr(c, l, y + lvl * 6f, max(l + 1f, r - 1f), y + lvl * 6f + 5f, 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = STAGE.getValue(lvl) })
                }
                y += 24f + 12f
                var lx = x
                listOf(Triple("Deep", s.deepMin, 3), Triple("REM", s.remMin, 1), Triple("Light", s.lightMin, 2)).forEach { (n, m, lvl) ->
                    dot(c, lx + 2.5f, y - 3.5f, 2.5f, STAGE.getValue(lvl)); lx += 8f
                    lx += cap(c, n, lx, y) + 4f
                    lx += text(c, m?.let { "${it / 60}:${String.format("%02d", it % 60)}" } ?: "—", lx, y, 10f, Color.WHITE, tightMid) + 12f
                }
            } else y += 12f
            y += 20f
            var cx = x
            dot(c, cx + 3f, y - 4f, 3f, recColor(s.recovery)); cx += 10f
            cx += text(c, s.recovery?.toString() ?: "—", cx, y, 13f, Color.WHITE, tight) + 4f
            cx += cap(c, "Rec", cx, y) + 14f
            listOf(s.hrv?.toString() to "HRV", s.rhr?.toString() to "RHR", s.steps?.let { String.format("%,d", it) } to "Steps").forEach { (v, l) ->
                cx += text(c, v ?: "—", cx, y, 11f, Color.WHITE, tightMid) + 4f
                cx += cap(c, l, cx, y) + 14f
            }
        }
    }
}
