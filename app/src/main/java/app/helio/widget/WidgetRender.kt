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
    private const val MIN_W = 200f; private const val MIN_H = 128f
    private const val DET_W = 330f; private const val DET_H = 268f
    private const val STRIP_W = 340f; private const val STRIP_H = 46f

    /** The widget: transparent, minimal when narrow, detailed when wide. */
    fun render(ctx: Context, s: WidgetStore.Snapshot, wDp: Float, hDp: Float, density: Float, card: Boolean = false): Bitmap {
        init(ctx)
        val detailed = wDp >= 260f && hDp >= 175f
        val bw = if (detailed) DET_W else MIN_W
        val bh = if (detailed) DET_H else MIN_H
        val pad = if (card) 14f else 8f
        // Fill the width, keep the proportions, never overflow the height.
        val k = min((wDp - 2 * pad) / bw, (hDp - 2 * pad) / bh).coerceIn(0.6f, 2.2f)
        return draw(wDp, hDp, density, card) { c, p ->
            c.translate(pad, pad)
            c.scale(k, k)
            // Stretch the rows across the full width the scale leaves (bars get longer, text stays the same size).
            val fullW = ((wDp - 2 * pad) / k).coerceAtLeast(bw)
            // Spare height is shared between the sections, so the widget fills its frame.
            val spare = (((hDp - 2 * pad) / k) - bh).coerceIn(0f, bh * 0.6f)
            if (detailed) p.detailed(c, s, fullW, spare / 4f) else p.minimal(c, s, fullW, spare / 3f)
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

        /** Cover screen: training week, strain, recovery and sleep. Nothing else. */
        fun minimal(c: Canvas, s: WidgetStore.Snapshot, w: Float, gap: Float = 0f) {
            var y = 20f + gap / 2
            val wk = s.week
            val f = wk?.let { it.cumulative / it.goal } ?: 0.0
            row(c, "Training", wk?.let { String.format("%.0f", it.cumulative) } ?: "—", wk?.let { "/${String.format("%.0f", it.goal)}" }, weekColor(f), y, w, 22f)
            y += 8f
            weekBar(c, s, 0f, y, w, 14f)
            y += 14f + 30f + gap
            row(c, "Strain", s.strain?.let { String.format("%.1f", it) } ?: "—", null, Color.WHITE, y, w, 22f)
            y += 8f
            strainBar(c, s, 0f, y, w, 14f)
            y += 14f + 24f + gap
            recSleep(c, s, 0f, y, 17f, 9.5f)
        }

        /** One row's heading: a small label on the left, the big number (and a dim suffix) on the right. */
        private fun row(c: Canvas, label: String, value: String, suffix: String?, color: Int, y: Float, w: Float, size: Float = 28f) {
            cap(c, label, 0f, y, 11f)
            var right = w
            if (suffix != null) right -= text(c, suffix, w, y, size * 0.5f, white(0.6f), tightMid, right = true) + 2f
            text(c, value, right, y + 2f, size, color, tight, right = true)
        }

        /** A plain thick bar filled to [f] in [color]. */
        private fun fillBar(c: Canvas, x: Float, y: Float, w: Float, h: Float, f: Float, color: Int) {
            rr(c, x, y, x + w, y + h, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = white(0.10f) })
            if (f > 0f) rr(c, x, y, x + max(h, w * f.coerceIn(0f, 1f)), y + h, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; setShadowLayer(6f, 0f, 0f, color) })
        }

        /** Open screen: four rows, each a label, one big number and one thick bar. */
        fun detailed(c: Canvas, s: WidgetStore.Snapshot, w: Float, gap: Float = 0f) {
            var y = 24f + gap / 2
            val wk = s.week
            val f = wk?.let { it.cumulative / it.goal } ?: 0.0
            row(c, "Training", wk?.let { String.format("%.0f", it.cumulative) } ?: "—", wk?.let { "/${String.format("%.0f", it.goal)}" }, weekColor(f), y, w)
            y += 10f
            weekBar(c, s, 0f, y, w, 18f)
            y += 18f + 36f + gap

            row(c, "Strain", s.strain?.let { String.format("%.1f", it) } ?: "—", null, Color.WHITE, y, w)
            y += 10f
            strainBar(c, s, 0f, y, w, 16f)
            y += 16f + 36f + gap

            row(c, "Sleep", s.asleepMin?.let { hm(it) } ?: "—", s.sleepScore?.let { " · $it" }, Color.WHITE, y, w)
            y += 10f
            val st = s.stages
            if (st.isNotEmpty() && s.sleepEnd > s.sleepStart) {
                val span = (s.sleepEnd - s.sleepStart).toFloat()
                st.forEach { (a, b, lvl) ->
                    val l = (a - s.sleepStart) / span * w; val r = (b - s.sleepStart) / span * w
                    rr(c, l, y + lvl * 9f, max(l + 3f, r - 1f), y + lvl * 9f + 8f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = STAGE.getValue(lvl) })
                }
            } else fillBar(c, 0f, y, w, 16f, (s.asleepMin ?: 0) / 540f, SLEEP)
            y += 36f + 36f + gap

            val rec = s.recovery
            row(c, "Recovery", rec?.let { "$it%" } ?: "—", null, recColor(rec), y, w)
            y += 10f
            fillBar(c, 0f, y, w, 16f, (rec ?: 0) / 100f, recColor(rec))
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
