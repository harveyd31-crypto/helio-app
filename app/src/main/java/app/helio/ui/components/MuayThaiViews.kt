package app.helio.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.data.MuayThaiKind
import app.helio.data.Workout
import app.helio.logic.MuayThaiProgress
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.body
import app.helio.ui.theme.cap
import app.helio.ui.theme.card
import app.helio.ui.theme.num

/** One tap after a Muay Thai session: what was it? Shown once, at the top of the session detail. */
@Composable
fun KindPrompt(onPick: (MuayThaiKind) -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("WHAT WAS THIS SESSION?", style = cap(12.sp, Color.White))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MuayThaiKind.entries.forEach { k ->
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
fun KindBadge(k: MuayThaiKind, onChange: () -> Unit) {
    Text(
        k.label.uppercase() + "  ✎", style = cap(11.sp, Color.White),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x1FFFFFFF)).clickable(onClick = onChange).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * Muay Thai progress by month or quarter: rest recovery (conditioning), time in the red (intensity),
 * fade (holding output to the last round), strain and number of sessions, per kind of session.
 */
@Composable
fun MuayThaiProgressCard(all: List<Workout>, kindOf: (Workout) -> MuayThaiKind?, hrMax: Double, initialKind: MuayThaiKind? = null) {
    var kind by remember(initialKind) { mutableStateOf(initialKind) }
    var period by remember { mutableStateOf(MuayThaiProgress.Period.Month) }
    val b = remember(all, kind, period, hrMax) { MuayThaiProgress.buckets(all, kind, kindOf, hrMax, period) }

    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("MUAY THAI PROGRESS", style = cap(13.sp, Color.White), modifier = Modifier.weight(1f))
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF))) {
                MuayThaiProgress.Period.entries.forEach { p ->
                    Text(
                        if (p == MuayThaiProgress.Period.Month) "MONTH" else "QUARTER",
                        style = cap(10.sp, if (p == period) Color.Black else Color.White),
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (p == period) Color.White else Color.Transparent)
                            .clickable { period = p }.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<MuayThaiKind?>(null) + MuayThaiKind.entries).forEach { k ->
                val on = k == kind
                Text(
                    (k?.label ?: "All").uppercase(), style = cap(11.sp, if (on) Color.Black else Color.White),
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Color.White else Color(0x14FFFFFF))
                        .clickable { kind = k }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        if (b.all { it.sessions == 0 }) {
            Text("No ${kind?.label?.lowercase() ?: "Muay Thai"} sessions in this period yet.", style = body(13.sp, HelioColors.TextMuted))
        } else {
            Metric("REST RECOVERY", "bpm drop in the first minute of rest", b, { it.recovery?.toDouble() }, { "−${it.toInt()}" }, higherIsBetter = true)
            Metric("TIME IN THE RED", "minutes at 90 %+ per session", b, { it.redMin }, { String.format("%.1f", it) }, higherIsBetter = null)
            Metric("FADE", "last round vs best round", b, { it.fade?.toDouble() }, { "${if (it >= 0) "+" else ""}${it.toInt()}%" }, higherIsBetter = true)
            Metric("STRAIN", "average per session", b, { it.strain }, { String.format("%.1f", it) }, higherIsBetter = null)
            Metric("SESSIONS", "", b, { it.sessions.toDouble().takeIf { s -> s > 0 } }, { it.toInt().toString() }, higherIsBetter = null)
        }
    }
}

@Composable
private fun Metric(
    title: String, sub: String, b: List<MuayThaiProgress.Bucket>,
    value: (MuayThaiProgress.Bucket) -> Double?, fmt: (Double) -> String, higherIsBetter: Boolean?,
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
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    val f = if (v == null) 0f else if (hi - lo < 1e-6) 0.6f else (0.25 + 0.75 * (v - lo) / (hi - lo)).toFloat()
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(f.coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(3.dp)).background(if (v == null) Color(0x0FFFFFFF) else if (i == latest.index) HelioColors.Strain else Color(0x40FFFFFF)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            b.forEach { Text(it.label, style = cap(9.sp), modifier = Modifier.weight(1f)) }
        }
    }
}
