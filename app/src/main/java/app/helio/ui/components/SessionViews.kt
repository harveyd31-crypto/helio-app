package app.helio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.data.SessionKind
import app.helio.data.Workout
import app.helio.data.WorkoutMath
import app.helio.data.WorkoutType
import app.helio.logic.SportProgress
import app.helio.logic.TennisAnalysis
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.body
import app.helio.ui.theme.cap
import app.helio.ui.theme.card
import app.helio.ui.theme.num

/** One tap after a session: what was it? Shown at the top of the session detail until answered. */
@Composable
fun KindPrompt(sport: WorkoutType, onPick: (SessionKind) -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("WHAT WAS THIS SESSION?", style = cap(12.sp, Color.White))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SessionKind.forSport(sport).forEach { k ->
                Box(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).background(HelioColors.StrainBadge).clickable { onPick(k) },
                    contentAlignment = Alignment.Center,
                ) { Text(k.label.uppercase(), style = cap(12.sp, Color.White)) }
            }
        }
        Text("Used to compare like with like in your monthly and quarterly progress.", style = body(12.sp, HelioColors.TextMuted))
    }
}

@Composable
fun KindBadge(k: SessionKind, onChange: () -> Unit) {
    Text(
        k.label.uppercase() + "  ✎", style = cap(11.sp, Color.White),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x1FFFFFFF)).clickable(onClick = onChange).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Progress by month or quarter for one sport, filterable by kind of session. */
@Composable
fun SportProgressCard(sport: WorkoutType, all: List<Workout>, kindOf: (Workout) -> SessionKind?, hrMax: Double, initialKind: SessionKind? = null) {
    var kind by remember(initialKind) { mutableStateOf(initialKind) }
    var period by remember { mutableStateOf(SportProgress.Period.Month) }
    val specs = SportProgress.specsFor(sport)
    val b = remember(all, kind, period, hrMax, sport) { SportProgress.buckets(all, sport, kind, kindOf, period, SportProgress.measureFor(sport, hrMax)) }

    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${sport.label.uppercase()} PROGRESS", style = cap(13.sp, Color.White), modifier = Modifier.weight(1f))
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF))) {
                SportProgress.Period.entries.forEach { p ->
                    Text(
                        if (p == SportProgress.Period.Month) "MONTH" else "QUARTER",
                        style = cap(10.sp, if (p == period) Color.Black else Color.White),
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (p == period) Color.White else Color.Transparent)
                            .clickable { period = p }.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<SessionKind?>(null) + SessionKind.forSport(sport)).forEach { k ->
                val on = k == kind
                Text(
                    (k?.label ?: "All").uppercase(), style = cap(11.sp, if (on) Color.Black else Color.White),
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Color.White else Color(0x14FFFFFF))
                        .clickable { kind = k }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        if (b.all { it.sessions == 0 }) {
            Text("No ${kind?.label?.lowercase() ?: sport.label.lowercase()} sessions in this period yet.", style = body(13.sp, HelioColors.TextMuted))
        } else {
            specs.forEach { sp -> Metric(sp.title, sp.sub, b, { it.values[sp.key] }, sp.fmt, sp.higherIsBetter) }
            Metric("SESSIONS", "", b, { it.sessions.toDouble().takeIf { s -> s > 0 } }, { it.toInt().toString() }, null)
        }
    }
}

@Composable
private fun Metric(
    title: String, sub: String, b: List<SportProgress.Bucket>,
    value: (SportProgress.Bucket) -> Double?, fmt: (Double) -> String, higherIsBetter: Boolean?,
) {
    val vals = b.map(value)
    val present = vals.withIndex().filter { it.value != null }
    if (present.isEmpty()) return
    val latest = present.last()
    val first = present.first()
    val lo = present.minOf { it.value!! }; val hi = present.maxOf { it.value!! }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(title, style = cap(11.sp, Color.White))
                if (sub.isNotEmpty()) Text(sub, style = body(11.sp, HelioColors.TextMuted))
            }
            Text(fmt(latest.value!!), style = num(22.sp, FontWeight.Bold))
            if (present.size >= 2 && latest.index != first.index) {
                val d = latest.value!! - first.value!!
                val good = higherIsBetter?.let { if (it) d > 0 else d < 0 }
                val color = when { kotlin.math.abs(d) < 1e-6 || good == null -> HelioColors.TextSoft; good -> HelioColors.RecGreen; else -> HelioColors.RecYellow }
                Spacer(Modifier.width(8.dp))
                Text(
                    (if (d >= 0) "▲ " else "▼ ") + fmt(kotlin.math.abs(d)).trimStart('−', '+') + " vs ${b[first.index].label}",
                    style = num(11.sp, FontWeight.SemiBold, color),
                    modifier = Modifier.padding(bottom = 4.dp).background(Color(0x14FFFFFF), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            vals.forEachIndexed { i, v ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    val f = if (v == null) 0f else if (hi - lo < 1e-6) 0.6f else (0.25 + 0.75 * (v - lo) / (hi - lo)).toFloat()
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(f.coerceAtLeast(0.04f)).clip(RoundedCornerShape(3.dp))
                            .background(if (v == null) Color(0x0FFFFFFF) else if (i == latest.index) HelioColors.Strain else Color(0x40FFFFFF)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            b.forEach { Text(it.label, style = cap(9.sp), modifier = Modifier.weight(1f)) }
        }
    }
}

/** Tennis session breakdown: hard efforts, recovery between points, match intensity, holding up, 15-min blocks. */
@Composable
fun TennisCard(r: TennisAnalysis.Result, hrMax: Double) {
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SESSION ANALYSIS", style = cap(13.sp, Color.White), modifier = Modifier.weight(1f))
            Text("avg ${r.avgPctMax}% of max", style = cap(11.sp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("${r.efforts.size}", "Hard efforts · ${r.perHour.toInt()}/h", Color.White, Modifier.weight(1f))
            Tile(r.recovery30?.let { "−$it bpm" } ?: "—", "Recovery in 30 s", r.recovery30?.let { if (it >= 15) HelioColors.RecGreen else if (it >= 10) Color.White else HelioColors.RecYellow } ?: Color.White, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("${r.matchPct}%", "Match intensity (80 %+)", zoneColors[4], Modifier.weight(1f))
            val held = (r.holdPct ?: 0) >= -3
            Tile(
                r.holdPct?.let { if (held) "HELD" else "DROPPED" } ?: "—",
                r.holdPct?.let { "Last third ${if (it >= 0) "+" else ""}$it% vs first" } ?: "Session too short",
                if (r.holdPct == null) Color.White else if (held) HelioColors.RecGreen else HelioColors.RecYellow, Modifier.weight(1f),
            )
        }
        Text("Longest hard spell ${r.longestHardS / 60}:${String.format("%02d", r.longestHardS % 60)}", style = body(12.sp, HelioColors.TextMuted))
        if (r.blocks.size >= 2) {
            Text("EVERY 15 MINUTES", style = cap(11.sp, Color.White))
            val floors = WorkoutMath.floors(hrMax)
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                val n = r.blocks.size
                val gap = 6.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                val lo = 0.5 * hrMax; val hi = hrMax
                r.blocks.forEachIndexed { i, b ->
                    val x = i * (w + gap)
                    fun y(v: Double) = size.height - ((v - lo) / (hi - lo)).toFloat().coerceIn(0.03f, 1f) * size.height
                    val z = floors.count { b.avgHr >= it }
                    drawRoundRect(zoneColors[z], Offset(x, y(b.avgHr.toDouble())), Size(w, size.height - y(b.avgHr.toDouble())), CornerRadius(4.dp.toPx()))
                    drawLine(Color.White, Offset(x, y(b.peakHr.toDouble())), Offset(x + w, y(b.peakHr.toDouble())), 2.dp.toPx())
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.blocks.forEach { b -> Text("${b.startMin}′ · ${b.hardPct}%", style = cap(9.sp), modifier = Modifier.weight(1f)) }
            }
            Text("Bar = average heart rate, line = peak, % = time at match intensity.", style = body(11.sp, HelioColors.TextMuted))
        }
        Text("Hard efforts are your intense rallies and games, found from heart-rate surges. Faster recovery between them means better tennis fitness.", style = body(12.sp, HelioColors.TextMuted))
    }
}

@Composable
private fun Tile(v: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier.background(Color(0xFF2A2F35), RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(v, style = num(20.sp, FontWeight.Bold, color))
        Text(label, style = body(12.sp, HelioColors.TextMuted))
    }
}
