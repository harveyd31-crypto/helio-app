package app.helio.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.logic.BodyAge
import app.helio.logic.Vo2Max
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.body
import app.helio.ui.theme.cap
import app.helio.ui.theme.card
import app.helio.ui.theme.num
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ── Temperature ─────────────────────────────────────────────────────────────

fun tempLabel(d: Double?) = when { d == null -> "—"; d < -0.5 -> "LOW"; d > 0.5 -> "HIGH"; else -> "NORMAL" }
fun tempColor(d: Double?) = when { d == null -> HelioColors.TextMuted; d < -0.5 -> HelioColors.StressBlue; d > 0.5 -> HelioColors.StressOrange; else -> HelioColors.StressGreen }
private val tempStops = arrayOf(0f to HelioColors.StressBlue, 0.33f to HelioColors.StressGreen, 0.67f to HelioColors.StressGreen, 1f to HelioColors.StressOrange)

/** Mini arc like the stress row, with a marker for skin temperature vs your baseline (−1.5…+1.5 °C). */
@Composable
private fun MiniArc(stops: Array<Pair<Float, Color>>, marker: Float?) {
    Canvas(Modifier.size(52.dp, 30.dp)) {
        val r = size.width * 0.42f
        val c = Offset(size.width / 2, size.height * 0.95f)
        val sw = size.height * 0.15f
        drawArc(Brush.horizontalGradient(*stops), 180f, 180f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
        if (marker != null) {
            val a = Math.toRadians(180.0 + 180.0 * marker.coerceIn(0f, 1f))
            drawLine(Color.White, Offset(c.x + (r - sw) * cos(a).toFloat(), c.y + (r - sw) * sin(a).toFloat()),
                Offset(c.x + (r + sw) * cos(a).toFloat(), c.y + (r + sw) * sin(a).toFloat()), sw * 0.8f, StrokeCap.Round)
        }
    }
}

@Composable
fun TemperatureRow(delta: Double?, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().card(14.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        MiniArc(tempStops, delta?.let { ((it + 1.5) / 3.0).toFloat() })
        Spacer(Modifier.width(12.dp))
        Text("SKIN TEMPERATURE", style = cap(13.sp, Color.White), modifier = Modifier.weight(1f))
        Text(delta?.let { String.format("%+.1f°", it) } ?: "—", style = num(20.sp))
        Spacer(Modifier.width(8.dp))
        Text(tempLabel(delta), style = cap(10.sp, tempColor(delta)))
    }
}

// ── VO2 max ─────────────────────────────────────────────────────────────────

@Composable
fun Vo2Row(r: Vo2Max.Result, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().card(14.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Sparkline(r.history.map { it.second }, Modifier.size(52.dp, 30.dp))
        Spacer(Modifier.width(12.dp))
        Text("VO2 MAX", style = cap(13.sp, Color.White), modifier = Modifier.weight(1f))
        Text(r.value?.let { String.format("%.1f", it) } ?: "—", style = num(20.sp))
        Spacer(Modifier.width(8.dp))
        Text((r.category ?: "").uppercase(), style = cap(10.sp, categoryColor(r.category)))
    }
}

fun categoryColor(c: String?) = when (c) {
    "Superior", "Excellent" -> HelioColors.RecGreen; "Good" -> HelioColors.StressGreen; "Fair" -> HelioColors.RecYellow
    "Poor" -> HelioColors.StressOrange; else -> HelioColors.TextMuted
}

@Composable
private fun Sparkline(values: List<Double>, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min() - 0.5; val hi = values.max() + 0.5
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = i / (values.size - 1f) * size.width
            val y = size.height - ((v - lo) / (hi - lo)).toFloat() * size.height
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, HelioColors.Strain, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

@Composable
fun Vo2Detail(r: Vo2Max.Result, profileMissing: Boolean, onOpenTest: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailHeader("VO2 MAX", onClose)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(r.value?.let { String.format("%.1f", it) } ?: "—", style = num(56.sp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.padding(bottom = 10.dp)) {
                Text("± ${r.plusMinus.toInt()} ml/kg/min", style = body(13.sp, HelioColors.TextMuted))
                Text(listOfNotNull(r.category?.uppercase(), r.percentile?.let { "TOP ${100 - it}%" }).joinToString(" · "), style = cap(11.sp, categoryColor(r.category)))
            }
        }
        if (profileMissing) Text("Add your birth year and sex in Profile for your age band and a better max heart rate.", style = body(13.sp, HelioColors.RecYellow))

        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("LAST 90 DAYS", style = cap(11.sp, Color.White))
            TrendChart(r.history.map { it.second }, r.plusMinus, Modifier.fillMaxWidth().height(130.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                r.history.firstOrNull()?.let { Text(it.first.format(shortDate), style = body(11.sp, HelioColors.TextMuted)) }
                r.history.lastOrNull()?.let { Text(it.first.format(shortDate), style = body(11.sp, HelioColors.TextMuted)) }
            }
        }

        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("HOW IT'S CALCULATED", style = cap(11.sp, Color.White))
            MethodLine("Runs", "Your pace and heart rate on steady runs, extended to your max heart rate (ACSM running equation).", "× 1.0", r.estimates.count { it.method == Vo2Max.Method.Run })
            MethodLine("Running tests", "Guided test runs recorded by the app: same maths, cleaner data.", "× 2.0", r.estimates.count { it.method == Vo2Max.Method.Test })
            MethodLine("Resting heart rate", "15.3 × max HR ÷ resting HR (Uth). Always available, least precise.", "× 0.3", r.estimates.count { it.method == Vo2Max.Method.Resting })
            Text("Averaged over 6 weeks; newer results count more.", style = body(12.sp, HelioColors.TextMuted))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("${r.hrMax.toInt()}", "Max HR", Modifier.weight(1f))
                StatTile(r.hrRest?.let { "${it.toInt()}" } ?: "—", "Resting HR", Modifier.weight(1f))
                StatTile("${r.runsUsed}", "Runs used", Modifier.weight(1f))
            }
            Text("Max HR: ${r.hrMaxSource}", style = body(12.sp, HelioColors.TextMuted))
        }

        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("RECENT ESTIMATES", style = cap(11.sp, Color.White))
            r.estimates.filter { it.method != Vo2Max.Method.Resting }.take(6).ifEmpty { r.estimates.take(3) }.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.date.format(shortDate), style = num(12.sp, FontWeight.Medium, HelioColors.TextMuted), modifier = Modifier.width(52.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.method.label, style = body(13.sp, Color.White, FontWeight.Medium))
                        Text(e.detail, style = body(11.sp, HelioColors.TextMuted))
                    }
                    Text(String.format("%.1f", e.value), style = num(16.sp))
                }
            }
        }

        Column(Modifier.fillMaxWidth().card().clickable(onClick = onOpenTest).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("GUIDED RUNNING TEST ›", style = cap(11.sp, HelioColors.Strain))
            Text("12 minutes on flat ground: warm-up, then easy, steady and comfortably hard. GPS pace + live Helio heart rate give your personal line. Counts double. Once a month is enough.", style = body(13.sp))
        }
    }
}

@Composable
private fun MethodLine(title: String, text: String, weight: String, n: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = body(14.sp, Color.White, FontWeight.Medium))
            Text(text, style = body(12.sp, HelioColors.TextMuted))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(weight, style = num(14.sp, color = HelioColors.Strain))
            Text("$n in 6 wk", style = body(11.sp, HelioColors.TextMuted))
        }
    }
}

@Composable
private fun TrendChart(values: List<Double>, band: Double, modifier: Modifier) {
    val tm = androidx.compose.ui.text.rememberTextMeasurer()
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = (values.min() - band).let { kotlin.math.floor(it / 5) * 5 }
        val hi = (values.max() + band).let { kotlin.math.ceil(it / 5) * 5 }
        val left = 26.dp.toPx()
        val w = size.width - left
        fun x(i: Int) = left + i / (values.size - 1f) * w
        fun y(v: Double) = size.height - ((v - lo) / (hi - lo)).toFloat() * size.height
        var g = lo
        while (g <= hi) {
            drawLine(HelioColors.Divider, Offset(left, y(g)), Offset(size.width, y(g)), 1f)
            drawText(tm, g.toInt().toString(), Offset(0f, y(g) - 7.dp.toPx()), cap(10.sp))
            g += 5
        }
        val bandPath = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v + band)) else lineTo(x(i), y(v + band)) }
            for (i in values.indices.reversed()) lineTo(x(i), y(values[i] - band))
            close()
        }
        drawPath(bandPath, HelioColors.Strain.copy(alpha = 0.12f))
        val line = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
        drawPath(line, HelioColors.Strain, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Color.White, 4.dp.toPx(), Offset(x(values.lastIndex), y(values.last())))
    }
}

@Composable
fun DetailHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = cap(13.sp, Color.White), modifier = Modifier.weight(1f))
        Text("Close", style = body(13.sp, HelioColors.Strain, FontWeight.Medium), modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
    }
}

// ── Estimated age ───────────────────────────────────────────────────────────

@Composable
fun AgeRow(r: BodyAge.Result?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().card(14.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        AgeBlob(Modifier.size(52.dp, 30.dp), particles = 18, animate = false)
        Spacer(Modifier.width(12.dp))
        Text("ESTIMATED AGE", style = cap(13.sp, Color.White), modifier = Modifier.weight(1f))
        Text(r?.let { String.format("%.1f", it.age) } ?: "—", style = num(20.sp))
        Spacer(Modifier.width(8.dp))
        Text(r?.let { ageDeltaText(it.delta, short = true) } ?: "", style = cap(10.sp, r?.let { ageColor(it.delta) } ?: HelioColors.TextMuted))
    }
}

fun ageColor(d: Double) = if (d <= 0) HelioColors.RecGreen else HelioColors.StressOrange
fun ageDeltaText(d: Double, short: Boolean = false): String {
    val y = String.format("%.1f", abs(d))
    return if (short) (if (d <= 0) "−$y YRS" else "+$y YRS") else (if (d <= 0) "$y years younger" else "$y years older")
}

@Composable
fun AgeDetail(r: BodyAge.Result?, sample: Boolean, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DetailHeader("ESTIMATED AGE", onClose)
        Box(contentAlignment = Alignment.Center) {
            AgeBlob(Modifier.size(250.dp), particles = 150, animate = true)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(r?.let { String.format("%.1f", it.age) } ?: "—", style = num(52.sp))
                Text("ESTIMATED AGE", style = cap(11.sp, Color.White))
                Spacer(Modifier.height(4.dp))
                Text(r?.let { ageDeltaText(it.delta) } ?: "Not enough data yet", style = body(14.sp, Color.White))
            }
        }
        if (r == null || r.present < 9 || sample) {
            Text(
                if (r == null) "Needs at least 5 of the 9 factors over 28 days, and your birth year and sex in Profile."
                else if (sample) "Sample data. Add your profile and wear the Helio to see yours."
                else "Calibrating: ${r.present} of 9 factors measured. Missing ones are filled with the average of the others.",
                style = body(13.sp, HelioColors.StressBlue), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Color(0x1A5AA9E6), RoundedCornerShape(12.dp)).padding(12.dp),
            )
        }
        if (r != null) {
            Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PACE OF AGING", style = cap(11.sp, Color.White))
                PaceScale(r.pace, Modifier.fillMaxWidth().height(58.dp))
                Text(String.format("You are aging %.2f× as fast as the calendar.", r.pace), style = body(12.sp, HelioColors.TextMuted))
            }
            Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("WHAT ADDS AND TAKES AWAY YEARS", style = cap(11.sp, Color.White))
                r.factors.sortedBy { it.years ?: 0.0 }.forEach { FactorLine(it) }
                Text("28-day averages. Each factor is compared with a reference for your age and sex; years = 10 × ln(hazard ratio).", style = body(11.sp, HelioColors.TextMuted))
            }
        }
    }
}

@Composable
private fun FactorLine(f: BodyAge.FactorResult) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(f.factor.label, style = body(13.sp, Color.White, FontWeight.Medium))
            Text(
                f.value?.let { (if (it >= 1000) String.format("%,.0f", it) else String.format("%.1f", it)) + " ${f.factor.unit} · ref " + (if (f.reference >= 1000) String.format("%,.0f", f.reference) else String.format("%.0f", f.reference)) }
                    ?: "Not measured",
                style = body(11.sp, HelioColors.TextMuted),
            )
        }
        val yrs = f.years
        val anim by animateFloatAsState(((yrs ?: 0.0) / 3.0).toFloat().coerceIn(-1f, 1f), tween(900, easing = FastOutSlowInEasing), label = "f")
        Canvas(Modifier.size(90.dp, 10.dp)) {
            val mid = size.width / 2
            drawLine(HelioColors.Divider, Offset(mid, 0f), Offset(mid, size.height), 1f)
            if (yrs != null) {
                val w = anim * mid
                drawRoundRect(
                    if (anim <= 0) HelioColors.RecGreen else HelioColors.StressOrange,
                    Offset(if (w < 0) mid + w else mid, 1f), Size(abs(w), size.height - 2f),
                    androidx.compose.ui.geometry.CornerRadius(4f),
                )
            }
        }
        Text(yrs?.let { String.format("%+.1f", it) } ?: "—", style = num(13.sp, color = yrs?.let { if (it <= 0) HelioColors.RecGreen else HelioColors.StressOrange } ?: HelioColors.TextMuted), textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun PaceScale(pace: Double, modifier: Modifier) {
    val tm = androidx.compose.ui.text.rememberTextMeasurer()
    val p by animateFloatAsState(((pace - 0.6) / 1.0).toFloat().coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing), label = "pace")
    Canvas(modifier) {
        val top = 22.dp.toPx(); val bottom = size.height - 16.dp.toPx()
        for (i in 0..50) {
            val x = i / 50f * size.width
            val major = i % 10 == 0
            drawLine(if (major) HelioColors.TextMuted else HelioColors.Divider, Offset(x, if (major) top else top + 6.dp.toPx()), Offset(x, bottom), if (major) 2f else 1.5f)
        }
        val mx = p * size.width
        drawLine(Color.White, Offset(mx, top - 4.dp.toPx()), Offset(mx, bottom + 2.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
        val lab = tm.measure(String.format("%.2fx", pace), num(16.sp))
        drawText(lab, topLeft = Offset((mx - lab.size.width / 2).coerceIn(0f, size.width - lab.size.width), 0f))
        listOf(0f to "0.6x", 0.4f to "1.0x", 1f to "1.6x").forEach { (fx, t) ->
            val m = tm.measure(t, cap(10.sp))
            drawText(m, topLeft = Offset((fx * size.width - m.size.width / 2).coerceIn(0f, size.width - m.size.width), size.height - m.size.height))
        }
    }
}

/**
 * A living, slowly breathing blob: a wobbling outline (sums of sines that loop seamlessly), deep blue
 * centre warming to gold at the bottom edge, and twinkling particles drifting near the rim.
 */
@Composable
fun AgeBlob(modifier: Modifier, particles: Int, animate: Boolean) {
    val phase = if (animate) {
        val t = rememberInfiniteTransition(label = "blob")
        t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    } else 0.7f
    val dots = remember(particles) {
        val rnd = Random(7)
        List(particles) { floatArrayOf(rnd.nextFloat() * 2 * PI.toFloat(), 1f - rnd.nextFloat() * rnd.nextFloat() * 0.5f, 0.6f + rnd.nextFloat() * 1.8f, (1 + rnd.nextInt(3)).toFloat(), rnd.nextFloat() * 6.28f) }
    }
    Canvas(modifier.clipToBounds()) {
        val c = center
        val base = size.minDimension / 2 * 0.92f
        fun radius(th: Float) = base * (1f + 0.045f * sin(3 * th + phase) + 0.035f * sin(5 * th - 2 * phase) + 0.022f * sin(7 * th + 3 * phase))
        val path = Path()
        val n = 120
        for (i in 0..n) {
            val th = i / n.toFloat() * 2 * PI.toFloat()
            val r = radius(th)
            val x = c.x + r * cos(th); val y = c.y + r * sin(th)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, Brush.radialGradient(0f to Color(0xFF04070A), 0.55f to Color(0xFF0A1520), 0.82f to Color(0xFF24547A), 1f to Color(0xFF79B4DE), center = c, radius = base * 1.08f))
        drawPath(path, Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0x99D9C27A), startY = c.y - base, endY = c.y + base))
        clipPath(path) {
            val dotUnit = size.minDimension / 250f
            dots.forEach { d ->
                val th = d[0] + 0.06f * sin(phase * d[3] + d[4])
                val r = radius(th) * d[1]
                val a = 0.25f + 0.75f * (0.5f + 0.5f * sin(phase * d[3] + d[4]))
                drawCircle(Color.White.copy(alpha = a * 0.9f), d[2] * dotUnit * 1.4f, Offset(c.x + r * cos(th), c.y + r * sin(th)))
            }
        }
    }
}
