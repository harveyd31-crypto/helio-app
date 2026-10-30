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
 * Draws the home-screen widget (and the lock-screen notification) as one image, exactly as
 * designed: week by day (colour climbing from blue through teal and green to gold as the goal
 * fills), today's strain inside a recovery-coloured budget, recovery and sleep. Narrow (cover
 * screen): minimal. Wide (open screen): the details too. The layout is drawn at a base size and
 * scaled to fill the widget, so text grows with the widget.
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

    // Base layouts (dp): content is drawn at this size, then scaled to the widget.
    private const val MIN_W = 200f; private const val MIN_H = 116f
    private const val DET_W = 330f; private const val DET_H = 262f
    private const val STRIP_W = 340f; private const val STRIP_H = 46f

    /** The widget: transparent, minimal when narrow, detailed when wide. */
    fun render(ctx: Context, s: WidgetStore.Snapshot, wDp: Float, hDp: Float, density: Float, card: Boolean = false): Bitmap {
        init(ctx)
        val detailed = wDp >= 260f && hDp >= 175f
        val bw = if (detailed) DET_W else MIN_W
        val bh = if (detailed) DET_H else MIN_H
        val pad = if (card) 14f else 4f
        // Fill the width, keep the proportions, never overflow the height.
        val k = min((wDp - 2 * pad) / bw, (hDp - 2 * pad) / bh).coerceIn(0.6f, 2.2f)
        return draw(wDp, hDp, density, card) { c, p ->
            c.translate(pad, pad)
            c.scale(k, k)
            // Stretch the rows across the full width the scale leaves (bars get longer, text stays the same size).
            val fullW = ((wDp - 2 * pad) / k).coerceAtLeast(bw)
            if (detailed) p.detailed(c, s, fullW) else p.minimal(c, s, fullW)
        }
    }

    /** A one-line summary for the notification on the lock screen: recovery, strain, sleep and the week. */
    fun strip(ctx: Context, s: WidgetStore.Snapshot, density: Float): Bitmap = init(ctx).let {
        draw(STRIP_W, STRIP_H, density, card = true) { c, p -> p.strip(c, s, STRIP_W, STRIP_H) }
    }

    private fun draw(wDp: Float, hDp: Float, density: Float, card: Boolean, body: (Canvas, Painter) -> Unit): Bitmap {
        val scale = min(density, kotlin.math.sqrt(1_200_000f / (wDp * hDp)))
        val bmp = Bitmap.createBitmap(max(1, (wDp * scale).toInt()), max(1, (hDp * scale).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        if (card) c.drawRoundRect(RectF(0f, 0f, wDp, hDp), 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(23, 30, 35) })
        body(c, painter ?: Painter(ctxHolder!!).also { painter = it })
        return bmp
    }

    private var painter: Painter? = null
    private var ctxHolder: Context? = null

    /** Fonts load once per process. */
    fun init(ctx: Context) { if (ctxHolder == null) ctxHolder = ctx.applicationContext }

    private class Painter(ctx: Context) {
        val tight: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_tight_700) ?: Typeface.DEFAULT_BOLD
        val tightMid: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_tight_600) ?: Typeface.DEFAULT_BOLD
        val inter: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_600) ?: Typeface.DEFAULT
        val interReg: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_500) ?: Typeface.DEFAULT
        val shadow = Color.argb(110, 0, 0, 0)

        fun text(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, face: Typeface, right: Boolean = false, spacing: Float = 0f, center: Boolean = false): Float {
            val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = color; typeface = face; letterSpacing = spacing
                setShadowLayer(3f, 0f, 1f, shadow)
                textAlign = when { right -> Paint.Align.RIGHT; center -> Paint.Align.CENTER; else -> Paint.Align.LEFT }
            }
            c.drawText(t, x, y, pt)
            return pt.measureText(t)
        }

        fun cap(c: Canvas, t: String, x: Float, y: Float, size: Float = 9f, color: Int = white(0.74f), right: Boolean = false) =
            text(c, t.uppercase(), x, y, size, color, inter, right, 0.08f)

        fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, paint: Paint) = c.drawRoundRect(RectF(l, t, r, b), rad, rad, paint)
        fun dot(c: Canvas, x: Float, y: Float, r: Float, color: Int) = c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        fun hm(m: Int) = "${m / 60}:${String.format("%02d", m % 60)}"

        /** The week as one bar: each day's strain a segment, coloured by how far into the goal it took you. */
        fun weekBar(c: Canvas, s: WidgetStore.Snapshot, x: Float, y: Float, w: Float, h: Float) {
            val wk = s.week ?: return
            val scale = wk.goal * 1.25
            rr(c, x, y, x + w, y + h, h / 3, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white(0.13f) })
            var cum = 0.0; var px = x
            wk.daily.forEachIndexed { i, d ->
                if (d == null || d <= 0) return@forEachIndexed
                val a = cum / wk.goal; cum += d; val b = cum / wk.goal
                val sw = (d / scale * w).toFloat()
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(px, 0f, px + sw, 0f, weekColor(a), weekColor(b), Shader.TileMode.CLAMP)
                    if (i == wk.dayIndex) setShadowLayer(6f, 0f, 0f, weekColor(b))
                }
                rr(c, px, y, max(px + 2f, min(px + sw - 2f, x + w)), y + h, h / 3, paint)
                px += sw
            }
            val gx = x + (wk.goal / scale * w).toFloat()
            rr(c, gx - 1.2f, y - 3f, gx + 1.2f, y + h + 3f, 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            val ax = x + (min(wk.pace, scale) / scale * w).toFloat()
            c.drawPath(Path().apply { moveTo(ax - 3.5f, y + h + 7.5f); lineTo(ax + 3.5f, y + h + 7.5f); lineTo(ax, y + h + 3f); close() },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white(0.85f) })
        }

        /** Seven equal slots under the bar: each day's letter and strain, today in white, days ahead dimmed. */
        fun weekDays(c: Canvas, s: WidgetStore.Snapshot, x: Float, y: Float, w: Float) {
            val wk = s.week ?: return
            val slot = w / 7f
            for (i in 0 until 7) {
                val cx = x + slot * i + slot / 2
                val today = i == wk.dayIndex
                val ahead = i > wk.dayIndex
                val col = when { today -> Color.WHITE; ahead -> white(0.30f); else -> white(0.66f) }
                text(c, "MTWTFSS"[i].toString(), cx, y, 9f, col, inter, center = true)
                val v = wk.daily.getOrNull(i)
                text(c, if (ahead || v == null) "–" else String.format("%.1f", v), cx, y + 13f, 11f, if (today) Color.WHITE else col, tightMid, center = true)
            }
        }

        fun strainBar(c: Canvas, s: WidgetStore.Snapshot, x: Float, y: Float, w: Float, h: Float) {
            val rc = recColor(s.recovery)
            rr(c, x, y, x + w, y + h, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white(0.10f) })
            s.target?.let { t ->
                rr(c, x + 0.6f, y + 0.6f, x + (t / 21.0 * w).toFloat() - 0.6f, y + h - 0.6f, h / 2,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = rc })
            }
            s.strain?.takeIf { it > 0 }?.let { st ->
                val fw = (st / 21.0 * w).toFloat().coerceAtLeast(h)
                rr(c, x + 2f, y + 2f, x + fw - 2f, y + h - 2f, (h - 4f) / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE; setShadowLayer(5f, 0f, 0f, BLUE) })
            }
        }

        private fun recSleep(c: Canvas, s: WidgetStore.Snapshot, x: Float, y: Float, big: Float, small: Float): Float {
            var cx = x
            dot(c, cx + 3.5f, y - big * 0.33f, 3.5f, recColor(s.recovery)); cx += 11f
            cx += text(c, s.recovery?.toString() ?: "—", cx, y, big, Color.WHITE, tight) + 4f
            cx += cap(c, "Rec", cx, y, small) + 14f
            dot(c, cx + 3.5f, y - big * 0.33f, 3.5f, SLEEP); cx += 11f
            cx += text(c, s.sleepScore?.toString() ?: "—", cx, y, big, Color.WHITE, tight) + 4f
            cx += cap(c, "Sleep", cx, y, small) + 14f
            return cx
        }

        /** Cover screen: week, strain, recovery and sleep. Nothing else. */
        fun minimal(c: Canvas, s: WidgetStore.Snapshot, w: Float) {
            var y = 16f
            cap(c, "Week", 0f, y)
            val f = s.week?.let { it.cumulative / it.goal } ?: 0.0
            text(c, s.week?.let { String.format("%.1f", it.cumulative) } ?: "—", w, y + 2f, 21f, weekColor(f), tight, right = true)
            y += 7f
            weekBar(c, s, 0f, y, w, 11f)
            y += 11f + 25f
            cap(c, "Strain", 0f, y)
            text(c, s.strain?.let { String.format("%.1f", it) } ?: "—", w, y + 2f, 21f, Color.WHITE, tight, right = true)
            y += 7f
            strainBar(c, s, 0f, y, w, 11f)
            y += 11f + 22f
            recSleep(c, s, 0f, y, 15f, 9f)
        }

        private fun head(c: Canvas, label: String, info: String, value: String, valueColor: Int, y: Float, w: Float) {
            cap(c, label, 0f, y, 10f)
            val vw = text(c, value, w, y + 2f, 23f, valueColor, tight, right = true)
            text(c, info, w - vw - 9f, y, 11f, white(0.76f), interReg, right = true)
        }

        /** Open screen: the same, with the details. */
        fun detailed(c: Canvas, s: WidgetStore.Snapshot, w: Float) {
            var y = 18f
            val wk = s.week
            val f = wk?.let { it.cumulative / it.goal } ?: 0.0
            val info = wk?.let { g ->
                val d = g.cumulative - g.pace
                "${(f * 100).toInt()}% of ${String.format("%.0f", g.goal)} · ${String.format("%.1f", kotlin.math.abs(d))} ${if (d >= 0) "ahead" else "behind"}"
            } ?: ""
            head(c, "Week", info, wk?.let { String.format("%.1f", it.cumulative) } ?: "—", weekColor(f), y, w)
            y += 8f
            weekBar(c, s, 0f, y, w, 12f)
            y += 12f + 20f
            weekDays(c, s, 0f, y, w)
            y += 13f + 28f

            val sInfo = listOfNotNull(s.target?.let { "target ${String.format("%.1f", it)}" }, s.activity).joinToString(" · ")
            head(c, "Strain", sInfo, s.strain?.let { String.format("%.1f", it) } ?: "—", Color.WHITE, y, w)
            y += 8f
            strainBar(c, s, 0f, y, w, 10f)
            y += 10f + 28f

            val sl = listOfNotNull(s.asleepMin?.let { hm(it) }, s.bedWake).joinToString(" · ")
            head(c, "Sleep", sl, s.sleepScore?.toString() ?: "—", Color.WHITE, y, w)
            y += 9f
            val st = s.stages
            if (st.isNotEmpty() && s.sleepEnd > s.sleepStart) {
                val span = (s.sleepEnd - s.sleepStart).toFloat()
                st.forEach { (a, b, lvl) ->
                    val l = (a - s.sleepStart) / span * w; val r = (b - s.sleepStart) / span * w
                    rr(c, l, y + lvl * 7f, max(l + 1.2f, r - 1f), y + lvl * 7f + 6f, 1.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = STAGE.getValue(lvl) })
                }
                y += 28f + 16f
                var lx = 0f
                listOf(Triple("Deep", s.deepMin, 3), Triple("REM", s.remMin, 1), Triple("Light", s.lightMin, 2)).forEach { (n, m, lvl) ->
                    dot(c, lx + 3f, y - 4f, 3f, STAGE.getValue(lvl)); lx += 9f
                    lx += cap(c, n, lx, y, 9.5f) + 5f
                    lx += text(c, m?.let { hm(it) } ?: "—", lx, y, 12f, Color.WHITE, tightMid) + 14f
                }
            } else y += 14f
            y += 27f
            var cx = 0f
            dot(c, cx + 3.5f, y - 5f, 3.5f, recColor(s.recovery)); cx += 11f
            cx += text(c, s.recovery?.toString() ?: "—", cx, y, 16f, Color.WHITE, tight) + 4f
            cx += cap(c, "Rec", cx, y, 9.5f) + 16f
            listOf(s.hrv?.toString() to "HRV", s.rhr?.toString() to "RHR", s.steps?.let { String.format("%,d", it) } to "Steps").forEach { (v, l) ->
                cx += text(c, v ?: "—", cx, y, 13.5f, Color.WHITE, tightMid) + 4f
                cx += cap(c, l, cx, y, 9.5f) + 16f
            }
        }

        /** Lock-screen line: recovery, strain, sleep on the left; the week on the right. */
        fun strip(c: Canvas, s: WidgetStore.Snapshot, w: Float, h: Float) {
            val y = h / 2 + 6f
            var cx = 14f
            cx = recSleep(c, s, cx, y, 17f, 9f)
            cx += text(c, s.strain?.let { String.format("%.1f", it) } ?: "—", cx, y, 17f, BLUE, tight) + 4f
            cx += cap(c, "Strain", cx, y, 9f) + 12f
            val wk = s.week ?: return
            val f = wk.cumulative / wk.goal
            val bx = max(cx, w - 104f)
            val num = String.format("%.1f", wk.cumulative)
            text(c, num, w - 14f, y - 6f, 13f, weekColor(f), tight, right = true)
            cap(c, "Week", w - 14f - Paint().apply { textSize = 13f; typeface = tight }.measureText(num) - 6f, y - 6f, 8.5f, right = true)
            weekBar(c, s, bx, y + 1f, w - 14f - bx, 6f)
        }
    }
}
