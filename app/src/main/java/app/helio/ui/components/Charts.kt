package app.helio.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.data.Band
import app.helio.data.DaySummary
import app.helio.data.StressPoint
import app.helio.logic.Stress
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.cap
import app.helio.ui.theme.num
import kotlin.math.cos
import kotlin.math.sin

private val ringAnim = tween<Float>(durationMillis = 750, easing = FastOutSlowInEasing)

private fun DrawScope.tick(center: Offset, angleDeg: Float, r1: Float, r2: Float, color: Color, width: Float) {
    val a = Math.toRadians(angleDeg.toDouble())
    drawLine(
        color,
        Offset(center.x + r1 * cos(a).toFloat(), center.y + r1 * sin(a).toFloat()),
        Offset(center.x + r2 * cos(a).toFloat(), center.y + r2 * sin(a).toFloat()),
        strokeWidth = width,
        cap = StrokeCap.Round,
    )
}

/** Strain (outer, blue gradient, white target tick) around recovery (inner, traffic-light colour). */
@Composable
fun DualRing(strain: Double?, target: Double?, recovery: Int?, size: Dp, modifier: Modifier = Modifier) {
    val strainF by animateFloatAsState(((strain ?: 0.0) / 21.0).toFloat().coerceIn(0f, 1f), ringAnim, label = "strain")
    val recF by animateFloatAsState(((recovery ?: 0) / 100f).coerceIn(0f, 1f), ringAnim, label = "rec")
    val targetF by animateFloatAsState(((target ?: 0.0) / 21.0).toFloat().coerceIn(0f, 1f), ringAnim, label = "target")
    val recColor = HelioColors.recovery(recovery)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val outerW = s * 0.045f
        val innerW = s * 0.055f
        val rOuter = s / 2 - outerW
        val rInner = rOuter - outerW / 2 - s * 0.05f - innerW / 2
        val c = center
        fun box(r: Float) = Pair(Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
        val (o1, o2) = box(rOuter)
        drawArc(HelioColors.Track, -90f, 360f, false, o1, o2, style = Stroke(outerW))
        drawArc(
            Brush.verticalGradient(listOf(HelioColors.StrainDeep, HelioColors.StrainLight)),
            -90f, 360f * strainF, false, o1, o2, style = Stroke(outerW, cap = StrokeCap.Round),
        )
        if (target != null) tick(c, -90f + 360f * targetF, rOuter - outerW, rOuter + outerW, Color.White, s * 0.0125f)
        val (i1, i2) = box(rInner)
        drawArc(HelioColors.Track, -90f, 360f, false, i1, i2, style = Stroke(innerW))
        drawArc(recColor, -90f, 360f * recF, false, i1, i2, style = Stroke(innerW, cap = StrokeCap.Round))
    }
}

/** Weekly goal ring: progress, a white tick at the goal, a hollow dot at today's pace. */
@Composable
fun WeekRing(cumulative: Double, goal: Double, scale: Double, pace: Double, size: Dp, modifier: Modifier = Modifier) {
    val progF by animateFloatAsState((cumulative / scale).toFloat().coerceIn(0f, 1f), ringAnim, label = "week")
    val paceF by animateFloatAsState((pace / scale).toFloat().coerceIn(0f, 1f), ringAnim, label = "pace")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            val w = s * 0.072f
            val r = s / 2 - w
            val tl = Offset(center.x - r, center.y - r)
            val sz = Size(r * 2, r * 2)
            drawArc(HelioColors.Track, -90f, 360f, false, tl, sz, style = Stroke(w))
            drawArc(
                Brush.verticalGradient(listOf(HelioColors.StrainDeep, HelioColors.StrainLight)),
                -90f, 360f * progF, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round),
            )
            tick(center, -90f + 360f * (goal / scale).toFloat(), r - w * 0.9f, r + w * 0.9f, Color.White, s * 0.022f)
            val a = Math.toRadians((-90f + 360f * paceF).toDouble())
            val p = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
            drawCircle(HelioColors.BgMid, radius = w * 0.42f, center = p)
            drawCircle(Color.White, radius = w * 0.42f, center = p, style = Stroke(s * 0.014f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(String.format("%.1f", cumulative), style = num((size.value * 0.22f).sp))
            Text("of ${goal.toInt()}", style = cap((size.value * 0.08f).sp, HelioColors.TextMuted, FontWeight.Medium))
        }
    }
}

/** Semicircle-ish stress gauge 0–3 (240° sweep) with a white marker at the value. */
@Composable
fun StressGauge(value: Double?, width: Dp, modifier: Modifier = Modifier) {
    val vF by animateFloatAsState(((value ?: 0.0) / 3.0).toFloat().coerceIn(0f, 1f), ringAnim, label = "gauge")
    val height = width * 0.58f
    Box(modifier.size(width, height), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.size(width, height)) {
            val w = this.size.width
            val r = w * 0.31f
            val c = Offset(w / 2, r + w * 0.06f)
            val stroke = w * 0.028f
            drawArc(
                Brush.horizontalGradient(*HelioColors.stressStops, startX = c.x - r, endX = c.x + r),
                150f, 240f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (value != null) tick(c, 150f + 240f * vF, r - stroke * 1.2f, r + stroke * 1.2f, Color.White, stroke * 0.9f)
        }
        Column(Modifier.fillMaxWidth().padding(top = width * 0.14f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value?.let { String.format("%.1f", it) } ?: "—", style = num((width.value * 0.19f).sp))
            Text(Stress.label(value), style = cap((width.value * 0.05f).sp, HelioColors.stress(value)))
        }
    }
}

/** The day's stress line with sleep and workout bands; line colour follows the stress level. */
@Composable
fun StressChart(points: List<StressPoint>, sleep: Band?, workouts: List<Band>, modifier: Modifier = Modifier, showNow: Boolean = true) {
    val tm = rememberTextMeasurer()
    val labelStyle = cap(10.sp, HelioColors.TextMuted)
    Canvas(modifier) {
        val left = 24.dp.toPx()
        val top = 6.dp.toPx()
        val w = size.width - left
        val h = size.height - top - 2.dp.toPx()
        fun y(v: Double) = top + h - (v / 3.0 * h).toFloat()
        listOf(3.0, 2.0, 1.0, 0.0).forEach { v ->
            drawLine(HelioColors.Divider, Offset(left, y(v)), Offset(size.width, y(v)), 1f)
            drawText(tm, String.format("%.1f", v), Offset(0f, y(v) - 7.dp.toPx()), labelStyle)
        }
        sleep?.let {
            drawRect(Color(0x733A4856), Offset(left + it.start * w, top), Size((it.end - it.start) * w, h))
            drawRect(HelioColors.SleepBadge, Offset(left + it.start * w, top - 3f), Size((it.end - it.start) * w, 3f))
        }
        workouts.forEach {
            drawRect(HelioColors.StrainBadge.copy(alpha = 0.16f), Offset(left + it.start * w, top), Size((it.end - it.start) * w, h))
            drawRect(HelioColors.StrainBadge, Offset(left + it.start * w, top - 3f), Size((it.end - it.start) * w, 3f))
        }
        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = left + p.t * w
                if (i == 0) path.moveTo(x, y(p.v)) else path.lineTo(x, y(p.v))
            }
            drawPath(
                path,
                Brush.verticalGradient(
                    0f to HelioColors.StressOrange, 0.25f to HelioColors.StressYellow,
                    0.55f to HelioColors.StressGreen, 1f to HelioColors.StressBlue,
                    startY = top, endY = top + h,
                ),
                style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round),
            )
            if (showNow) {
                val lx = left + points.last().t * w
                drawLine(Color.White, Offset(lx, top), Offset(lx, top + h), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
        }
    }
}

/** Seven days of strain (blue) and recovery (coloured dots), selected day highlighted. */
@Composable
fun StrainRecoveryChart(days: List<DaySummary>, selected: Int, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        if (days.isEmpty()) return@Canvas
        val padX = 26.dp.toPx()
        val top = 22.dp.toPx()
        val bottom = size.height - 30.dp.toPx()
        val step = (size.width - padX * 2) / (days.size - 1).coerceAtLeast(1)
        fun x(i: Int) = padX + i * step
        fun sy(s: Double) = bottom - (s / 21.0 * (bottom - top)).toFloat()
        fun ry(r: Int) = bottom - (r / 100f * (bottom - top))

        if (selected in days.indices) {
            drawRoundRect(Color(0x12FFFFFF), Offset(x(selected) - 23.dp.toPx(), 0f), Size(46.dp.toPx(), size.height), CornerRadius(8.dp.toPx()))
        }
        listOf(0.0, 7.0, 14.0, 21.0).forEach { drawLine(HelioColors.Divider, Offset(padX - 10, sy(it)), Offset(size.width - padX + 10, sy(it)), 1f) }

        fun line(values: List<Float?>, color: Color) {
            val p = Path(); var started = false
            values.forEachIndexed { i, v -> if (v != null) { if (!started) { p.moveTo(x(i), v); started = true } else p.lineTo(x(i), v) } }
            drawPath(p, color, style = Stroke(2.dp.toPx()))
        }
        line(days.map { d -> d.recovery?.let { ry(it) } }, Color(0xFF7D8790))
        line(days.map { d -> d.strain?.let { sy(it) } }, HelioColors.StrainBadge)

        val lbl = num(13.sp, FontWeight.SemiBold)
        days.forEachIndexed { i, d ->
            val s = d.strain?.let { sy(it) }
            val r = d.recovery?.let { ry(it) }
            val strainAbove = s != null && r != null && s <= r
            r?.let {
                val c = HelioColors.recovery(d.recovery)
                drawCircle(HelioColors.BgMid, 6.dp.toPx(), Offset(x(i), it))
                drawCircle(c, 6.dp.toPx(), Offset(x(i), it), style = Stroke(2.5.dp.toPx()))
                val t = tm.measure("${d.recovery}%", lbl.copy(color = c))
                drawText(t, topLeft = Offset(x(i) - t.size.width / 2, if (strainAbove) it + 8.dp.toPx() else it - 8.dp.toPx() - t.size.height))
            }
            s?.let {
                drawCircle(HelioColors.BgMid, 6.dp.toPx(), Offset(x(i), it))
                drawCircle(HelioColors.Strain, 6.dp.toPx(), Offset(x(i), it), style = Stroke(2.5.dp.toPx()))
                val t = tm.measure(String.format("%.1f", d.strain), lbl.copy(color = HelioColors.Strain))
                drawText(t, topLeft = Offset(x(i) - t.size.width / 2, if (strainAbove) it - 8.dp.toPx() - t.size.height else it + 8.dp.toPx()))
            }
            val dl = tm.measure(
                d.date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } + "\n" + d.date.dayOfMonth,
                cap(11.sp, if (i == selected) Color.White else HelioColors.TextMuted).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            )
            drawText(dl, topLeft = Offset(x(i) - dl.size.width / 2, size.height - dl.size.height))
        }
    }
}

/** Live line for the half-folded screen: last 10 minutes of stress. */
@Composable
fun LiveStressLine(history: List<Pair<Long, Double>>, windowMs: Long, now: Long, modifier: Modifier = Modifier) {
    val pts = history.map { (t, v) -> StressPoint((1f - (now - t).toFloat() / windowMs).coerceIn(0f, 1f), v) }
    StressChart(pts, null, emptyList(), modifier, showNow = false)
}
