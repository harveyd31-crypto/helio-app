package app.helio.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.data.DaySummary
import app.helio.data.StrapExtras
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.body
import app.helio.ui.theme.cap
import app.helio.ui.theme.card
import app.helio.ui.theme.num
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val ChargeGreen = Color(0xFF3DDC97)
private fun chargeColor(v: Int?) = when { v == null -> HelioColors.TextMuted; v >= 60 -> ChargeGreen; v >= 30 -> HelioColors.RecYellow; else -> HelioColors.StressOrange }

/** Illness early warning (breathing rate and skin temperature at night well above your usual). */
@Composable
fun IllnessBanner(x: StrapExtras?) {
    val text = x?.illness ?: return
    Row(
        Modifier.fillMaxWidth().background(if (x.illnessSevere) Color(0x33E8384F) else Color(0x26F2D22E), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("!", style = num(18.sp, FontWeight.Bold, if (x.illnessSevere) HelioColors.RecRed else HelioColors.RecYellow), modifier = Modifier.padding(end = 10.dp))
        Column {
            Text("BODY SIGNALS OFF YOUR USUAL", style = cap(11.sp, Color.White))
            Text(text, style = body(13.sp))
        }
    }
}

/** Charge (body battery) row for the overview: value, a mini curve, what filled and drained it. */
@Composable
fun ChargeRow(x: StrapExtras?, onClick: () -> Unit) {
    val v = x?.charge ?: return
    Row(Modifier.fillMaxWidth().card(14.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BatteryGlyph(v)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("CHARGE", style = cap(13.sp, Color.White))
            Text(listOfNotNull(x.chargeAtWake?.let { "$it at wake" }, x.chargeRecharged?.let { "+${it.toInt()} sleep" }).joinToString(" · "), style = body(11.sp, HelioColors.TextMuted))
        }
        MiniCurve(x.chargeCurve, Modifier.size(70.dp, 28.dp))
        Spacer(Modifier.width(10.dp))
        Text("$v", style = num(22.sp, color = chargeColor(v)))
    }
}

@Composable
private fun BatteryGlyph(v: Int) {
    val f by animateFloatAsState(v / 100f, tween(800, easing = FastOutSlowInEasing), label = "bat")
    Canvas(Modifier.size(22.dp, 34.dp)) {
        val u = size.width / 22f
        drawRoundRect(Color.White, Offset(7 * u, 0f), Size(8 * u, 3 * u), CornerRadius(1.5f * u))
        drawRoundRect(Color(0x66FFFFFF), Offset(1 * u, 3 * u), Size(20 * u, size.height - 3 * u), CornerRadius(4 * u), style = Stroke(1.8f * u))
        val inner = size.height - 7 * u
        drawRoundRect(chargeColor(v), Offset(3.5f * u, 5 * u + inner * (1 - f)), Size(15 * u, inner * f), CornerRadius(2.5f * u))
    }
}

@Composable
private fun MiniCurve(pts: List<Pair<Long, Double>>, modifier: Modifier) {
    Canvas(modifier) {
        if (pts.size < 2) return@Canvas
        val t0 = pts.first().first; val t1 = pts.last().first
        val p = Path()
        pts.forEachIndexed { i, (t, v) ->
            val x = (t - t0).toFloat() / (t1 - t0).coerceAtLeast(1) * size.width
            val y = size.height - (v / 100.0).toFloat() * size.height
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, ChargeGreen, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Charge in detail: the day's curve and what filled and drained it. */
@Composable
fun ChargeDetail(day: DaySummary, onClose: () -> Unit) {
    val x = day.extras
    val tm = rememberTextMeasurer()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailHeader("CHARGE", onClose)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(x?.charge?.toString() ?: "—", style = num(56.sp, color = chargeColor(x?.charge)))
            Text("  / 100", style = body(14.sp, HelioColors.TextMuted), modifier = Modifier.padding(bottom = 10.dp))
        }
        Text("A body battery: sleep fills it, activity, stress and the waking day drain it. Every constant is a design choice, not a measurement (Ridge, after OpenStrap).", style = body(12.sp, HelioColors.TextMuted))
        if (x != null && x.chargeCurve.size > 1) {
            Column(Modifier.fillMaxWidth().card().padding(14.dp)) {
                Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                    val left = 26.dp.toPx(); val bottom = size.height - 16.dp.toPx()
                    val t0 = x.chargeCurve.first().first; val t1 = x.chargeCurve.last().first
                    fun px(t: Long) = left + (t - t0).toFloat() / (t1 - t0).coerceAtLeast(1) * (size.width - left)
                    fun py(v: Double) = bottom - (v / 100.0).toFloat() * bottom
                    listOf(0, 50, 100).forEach { g ->
                        drawLine(HelioColors.Divider, Offset(left, py(g.toDouble())), Offset(size.width, py(g.toDouble())), 1f)
                        drawText(tm, "$g", Offset(0f, py(g.toDouble()) - 7.dp.toPx()), cap(10.sp))
                    }
                    day.sleep?.let { s -> if (s.endMs > t0) drawRect(Color(0x333A4856), Offset(px(maxOf(s.startMs, t0)), 0f), Size(px(s.endMs) - px(maxOf(s.startMs, t0)), bottom)) }
                    val area = Path(); val line = Path()
                    x.chargeCurve.forEachIndexed { i, (t, v) ->
                        if (i == 0) { line.moveTo(px(t), py(v)); area.moveTo(px(t), bottom); area.lineTo(px(t), py(v)) } else { line.lineTo(px(t), py(v)); area.lineTo(px(t), py(v)) }
                    }
                    area.lineTo(px(t1), bottom); area.close()
                    drawPath(area, Brush.verticalGradient(listOf(ChargeGreen.copy(alpha = 0.35f), ChargeGreen.copy(alpha = 0.03f))))
                    drawPath(line, ChargeGreen, style = Stroke(2.dp.toPx()))
                    val l0 = tm.measure(Instant.ofEpochMilli(t0).atZone(ZoneId.systemDefault()).format(hm), cap(10.sp))
                    drawText(l0, topLeft = Offset(left, size.height - l0.size.height))
                    val l1 = tm.measure(Instant.ofEpochMilli(t1).atZone(ZoneId.systemDefault()).format(hm), cap(10.sp))
                    drawText(l1, topLeft = Offset(size.width - l1.size.width, size.height - l1.size.height))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(x?.chargeAtWake?.toString() ?: "—", "At wake", Modifier.weight(1f))
            StatTile(x?.chargeHigh?.toString() ?: "—", "High", Modifier.weight(1f))
            StatTile(x?.chargeLow?.toString() ?: "—", "Low", Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("FILLED AND DRAINED", style = cap(11.sp, Color.White))
            Flow("Sleep", x?.chargeRecharged, ChargeGreen, true)
            Flow("Activity", x?.chargeActivity, HelioColors.Strain, false)
            Flow("Stress", x?.chargeStress, HelioColors.StressOrange, false)
            Flow("Living (being awake)", x?.chargeLiving, HelioColors.TextMuted, false)
        }
    }
}

@Composable
private fun Flow(label: String, v: Double?, color: Color, plus: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = body(13.sp, Color.White), modifier = Modifier.width(150.dp))
        val f by animateFloatAsState(((v ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f), tween(800), label = "flow")
        Box(Modifier.weight(1f).height(8.dp).background(Color(0x14FFFFFF), RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxWidth(f).height(8.dp).background(color, RoundedCornerShape(4.dp)))
        }
        Text(v?.let { (if (plus) "+" else "−") + it.toInt() } ?: "—", style = num(14.sp, color = color), modifier = Modifier.width(48.dp).padding(start = 8.dp))
    }
}

/** Steps, calories and PAI for the day. */
@Composable
fun DailyTiles(x: StrapExtras?) {
    if (x == null || (x.steps == null && x.totalCalories == null && x.pai == null)) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(x.steps?.let { String.format("%,d", it) } ?: "—", x.stepsUsualByNow?.let { "Steps · usual ${String.format("%,d", it)}" } ?: "Steps", Modifier.weight(1.2f))
        StatTile(x.totalCalories?.let { String.format("%,d", it) } ?: "—", x.activeCalories?.let { "kcal · $it active" } ?: "kcal", Modifier.weight(1f))
        StatTile(x.pai?.toString() ?: "—", "PAI 7-day / 100", Modifier.weight(0.9f), if ((x.pai ?: 0) >= 100) HelioColors.RecGreen else Color.White)
    }
}

/** The recovery score's parts: each sub-score, its weight, and today's value against your usual. */
@Composable
fun RecoveryParts(x: StrapExtras?) {
    if (x == null) return
    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("WHAT MADE TODAY'S RECOVERY", style = cap(11.sp, Color.White))
        if (x.recoveryParts.isEmpty()) Text(x.recoveryWithheld ?: "Not enough history yet.", style = body(13.sp, HelioColors.TextMuted))
        x.recoveryParts.forEach { p ->
            val (label, unit) = when (p.key) {
                "hrv" -> "HRV" to "ms"; "rhr" -> "Resting heart rate" to "bpm"; "rr" -> "Breathing rate" to "/min"; "sleep" -> "Sleep" to "min"; else -> p.key to ""
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(150.dp)) {
                    Text("$label · ${(p.weight * 100).toInt()}%", style = body(13.sp, Color.White, FontWeight.Medium))
                    Text(String.format("%.0f %s", p.value, unit) + (p.baseline?.let { String.format(" · usual %.0f", it) } ?: ""), style = body(11.sp, HelioColors.TextMuted))
                }
                val f by animateFloatAsState(p.sub / 100f, tween(800), label = "sub")
                Box(Modifier.weight(1f).height(8.dp).background(Color(0x14FFFFFF), RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth(f).height(8.dp).background(HelioColors.recovery(p.sub), RoundedCornerShape(4.dp)))
                }
                Text("${p.sub}", style = num(14.sp, color = HelioColors.recovery(p.sub)), modifier = Modifier.width(40.dp).padding(start = 8.dp))
            }
        }
        Text("Each part vs your last 42 days (median and robust spread). Weights: HRV 42 %, resting HR 28 %, sleep 20 %, breathing 10 %.", style = body(11.sp, HelioColors.TextMuted))
    }
}

/** Sleep need, debt, regularity, efficiency, the strap's score, breathing disturbances and SpO2. */
@Composable
fun SleepExtras(x: StrapExtras?) {
    if (x == null) return
    fun hm(m: Int?) = m?.let { String.format("%d:%02d", it / 60, it % 60) } ?: "—"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(hm(x.sleepNeedMin), "Need", Modifier.weight(1f))
        StatTile(hm(x.sleepDebtMin), "Debt (14 nights)", Modifier.weight(1f), if ((x.sleepDebtMin ?: 0) > 120) HelioColors.RecYellow else Color.White)
        StatTile(x.sleepEfficiency?.let { "${it.toInt()}%" } ?: "—", "Efficiency", Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(x.sleepRegularity?.let { "${it.toInt()}" } ?: "—", "Regularity /100", Modifier.weight(1f))
        StatTile(x.deviceSleepScore?.toString() ?: "—", "Strap's score", Modifier.weight(1f))
        StatTile(x.spo2Night?.let { "${it.toInt()}%" } ?: "—", x.spo2Min?.let { "SpO₂ · low ${it.toInt()}%" } ?: "SpO₂", Modifier.weight(1f))
    }
    if (x.breathingPerHour != null) {
        Column(Modifier.fillMaxWidth().card(14.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(String.format("%.1f", x.breathingPerHour), style = num(22.sp))
                Text("  oxygen drops per hour" + (x.breathingUsual?.let { String.format(" · usual %.1f", it) } ?: ""), style = body(12.sp, HelioColors.TextMuted), modifier = Modifier.padding(bottom = 3.dp))
            }
            Text("The strap's own breathing-disturbance count for the night. Under 5 an hour is the usual range; not a diagnosis.", style = body(11.sp, HelioColors.TextMuted))
        }
    }
}

/** Where the numbers come from and how fresh they are. */
@Composable
fun SourceLine(text: String, color: Color = HelioColors.TextDim) {
    Text(text, style = body(11.sp, color), modifier = Modifier.padding(top = 6.dp))
}
