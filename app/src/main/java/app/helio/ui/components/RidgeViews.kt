package app.helio.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.data.DaySummary
import app.helio.logic.Insights
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.body
import app.helio.ui.theme.cap
import app.helio.ui.theme.card
import app.helio.ui.theme.num
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

val SleepPurple = Color(0xFFB7A3F5)
private val Good = HelioColors.RecGreen
private val Warn = HelioColors.RecYellow

/**
 * Seven days as rings, oldest left: the date inside, the day's score as the arc. The week shown is the
 * last seven days, or the week ending on an older picked day. Tap a day to show it.
 */
@Composable
fun WeekStrip(days: List<DaySummary>, selected: Int, onSelect: (Int) -> Unit, value: (DaySummary) -> Float?, color: (DaySummary) -> Color) {
    if (days.isEmpty()) return
    val end = if (selected >= days.lastIndex - 6) days.lastIndex else selected
    val from = (end - 6).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        (from..end).forEach { i ->
            val d = days[i]
            val on = i == selected
            val v = value(d)
            val f by animateFloatAsState(v ?: 0f, tween(600), label = "ring")
            Column(
                Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Color(0x1FFFFFFF) else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), style = cap(11.sp, if (on) Color.White else HelioColors.TextMuted))
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    val c = color(d)
                    Canvas(Modifier.size(36.dp)) {
                        val w = 3.2.dp.toPx()
                        val inset = w / 2
                        val sz = Size(size.width - w, size.height - w)
                        drawArc(Color(0x1FFFFFFF), 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
                        if (v != null) drawArc(c, -90f, 360f * f.coerceIn(0f, 1f), false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Text("${d.date.dayOfMonth}", style = num(13.sp, FontWeight.SemiBold, if (v == null) HelioColors.TextMuted else Color.White))
                }
            }
        }
    }
}

/** The day in a line and a paragraph: recovery band · load, then the day sentence. */
@Composable
fun DaySummaryCard(days: List<DaySummary>, i: Int, onOpenRecovery: () -> Unit) {
    val d = days[i]
    val text = Insights.daySentence(days, i, d.date == LocalDate.now())
    if (text.isBlank() && d.recovery == null) return
    val load = Insights.loadRange(days, i)
    Column(Modifier.fillMaxWidth().card().clickable(onClick = onOpenRecovery).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            d.recovery?.let { Box(Modifier.size(9.dp).background(HelioColors.recovery(it), CircleShape)); Spacer(Modifier.width(9.dp)) }
            Text(
                (d.recovery?.let { Insights.recoveryWord(it) } ?: "No recovery yet") + (load?.let { " · load ${it.words.lowercase()}" } ?: ""),
                style = body(14.sp, Color.White, FontWeight.SemiBold), modifier = Modifier.weight(1f),
            )
            Text("›", style = num(20.sp, color = HelioColors.TextMuted))
        }
        if (text.isNotBlank()) Text(text, style = body(13.sp, HelioColors.TextSoft))
    }
}

/** A coloured note under a figure: "● −2 vs usual". */
private data class Note(val text: String, val color: Color)

private fun change(delta: Double?, unit: String, higherBetter: Boolean?, meaningful: Double, digits: Int = 0, label: String = "vs usual"): Note? {
    delta ?: return null
    val txt = when {
        abs(delta) < meaningful / 2 -> "±0"
        else -> (if (delta > 0) "+" else "−") + String.format("%.${digits}f", abs(delta))
    } + "$unit $label"
    val color = when {
        abs(delta) < meaningful || higherBetter == null -> Good
        (delta > 0) == higherBetter -> Good
        else -> Warn
    }
    return Note(txt, color)
}

/**
 * One vital: label, value and unit, its change against your usual, and a small vertical gauge on the
 * right showing where tonight sits around your usual (middle).
 */
@Composable
private fun VitalTile(label: String, value: String, unit: String, note: Note?, pos: Float?, modifier: Modifier) {
    Row(modifier.card(16.dp).padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = body(12.sp, HelioColors.TextMuted))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = num(24.sp, FontWeight.SemiBold))
                if (unit.isNotEmpty()) Text(" $unit", style = body(12.sp, HelioColors.TextMuted), modifier = Modifier.padding(bottom = 3.dp))
            }
            if (note != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(note.color, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(note.text, style = body(12.sp, note.color, FontWeight.Medium))
            } else Text("no usual yet", style = body(12.sp, HelioColors.TextDim))
        }
        Box(Modifier.width(6.dp).height(40.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x14FFFFFF))) {
            if (pos != null && note != null) {
                val p = pos.coerceIn(0f, 1f)
                Box(Modifier.padding(top = (34 * (1 - p)).dp).width(6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(note.color))
            }
        }
    }
}

/** Position on the little gauge: usual in the middle, ±[span] (as a share of usual) at the ends. */
private fun gauge(v: Insights.VsUsual?, span: Double, higherBetter: Boolean = true): Float? {
    val u = v?.usual?.takeIf { it != 0.0 } ?: return null
    val rel = (v.value - u) / abs(u) / span
    return (0.5 + (if (higherBetter) rel else -rel) / 2).toFloat()
}

/** Last night's vitals, each against your usual (the 30 days before). */
@Composable
fun LastNightTiles(days: List<DaySummary>, i: Int) {
    val d = days[i]
    val hrv = Insights.vsUsual(days, i) { it.hrv?.toDouble() }
    val rhr = Insights.vsUsual(days, i) { it.restingHr?.toDouble() }
    val rr = Insights.vsUsual(days, i) { it.respRate }
    val spo2 = Insights.vsUsual(days, i) { it.extras?.spo2Night }
    val temp = Insights.vsUsual(days, i) { it.extras?.skinTempNight }
    val need = d.extras?.sleepNeedMin
    val asleep = d.sleep?.minutesAsleep
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("LAST NIGHT", style = cap(12.sp, Color.White), modifier = Modifier.weight(1f))
            Text("VS YOUR USUAL", style = cap(10.sp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VitalTile("HRV", hrv?.value?.roundToInt()?.toString() ?: "—", "ms", change(hrv?.delta, "", true, 2.0), gauge(hrv, 0.25), Modifier.weight(1f))
            VitalTile("Resting HR", rhr?.value?.roundToInt()?.toString() ?: "—", "bpm", change(rhr?.delta, "", false, 1.0), gauge(rhr, 0.12, higherBetter = false), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VitalTile("Breathing rate", rr?.value?.let { String.format("%.1f", it) } ?: "—", "rpm", change(rr?.delta, "", false, 0.4, 1), gauge(rr, 0.08, higherBetter = false), Modifier.weight(1f))
            VitalTile("SpO₂", spo2?.value?.roundToInt()?.toString() ?: "—", "%", change(spo2?.delta, "", true, 1.0), gauge(spo2, 0.03), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VitalTile(
                "Asleep", asleep?.let { Insights.hm(it) } ?: "—", "",
                if (asleep != null && need != null) change((asleep - need).toDouble(), " min", true, 10.0, label = "vs need") else null,
                if (asleep != null && need != null) (0.5f + (asleep - need) / 240f) else null, Modifier.weight(1f),
            )
            VitalTile("Skin temp", temp?.value?.let { String.format("%.1f", it) } ?: "—", "°C",
                temp?.delta?.let { dl -> Note((if (abs(dl) < 0.05) "±0.0" else (if (dl > 0) "+" else "−") + String.format("%.1f", abs(dl))) + " vs usual", if (abs(dl) < 0.5) Good else Warn) },
                temp?.delta?.let { (0.5 + it / 1.6).toFloat() }, Modifier.weight(1f))
        }
    }
}

/** Recovery's "+6 vs week" and the sentence on what went into it. */
@Composable
fun RecoveryInsight(days: List<DaySummary>, i: Int) {
    val d = days[i]
    val r = d.recovery ?: return
    val before = Insights.recoveryWeekBefore(days, i)
    val diff = before?.let { r - it.roundToInt() }
    val text = Insights.recoverySentence(d)
    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(HelioColors.recovery(r), CircleShape))
            Spacer(Modifier.width(9.dp))
            Text(Insights.recoveryWord(r) + (diff?.let { ", ${if (it >= 0) "+" else "−"}${abs(it)} vs your week" } ?: ""), style = body(14.sp, Color.White, FontWeight.SemiBold))
        }
        if (text.isNotBlank()) Text(text, style = body(13.sp, HelioColors.TextSoft))
    }
}

/** Sleep top: the strap's score ring, time in bed and asleep vs need, the headline and the night in words, sleep health. */
@Composable
fun SleepOverview(d: DaySummary, ringSize: Dp = 170.dp) {
    val s = d.sleep ?: return
    val score = d.extras?.deviceSleepScore
    val need = d.extras?.sleepNeedMin
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (score != null) {
            val f by animateFloatAsState(score / 100f, tween(700), label = "score")
            Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(ringSize)) {
                    val w = 12.dp.toPx()
                    val sz = Size(size.width - w, size.height - w)
                    drawArc(Color(0x26B7A3F5), 0f, 360f, false, Offset(w / 2, w / 2), sz, style = Stroke(w))
                    drawArc(SleepPurple, -90f, 360f * f, false, Offset(w / 2, w / 2), sz, style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$score", style = num(52.sp, FontWeight.SemiBold))
                    Text("${Insights.scoreWord(score)} · strap score", style = body(12.sp, HelioColors.TextMuted))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).card(16.dp).padding(14.dp)) {
                Text("Time in bed", style = body(12.sp, HelioColors.TextMuted))
                Text(Insights.hm((s.endMs - s.startMs) / 60_000), style = num(24.sp, FontWeight.SemiBold))
            }
            Column(Modifier.weight(1f).card(16.dp).padding(14.dp)) {
                Text("Time asleep", style = body(12.sp, HelioColors.TextMuted))
                Text(Insights.hm(s.minutesAsleep), style = num(24.sp, FontWeight.SemiBold))
                need?.let {
                    val gap = s.minutesAsleep - it
                    val c = if (gap >= -10) Good else Warn
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(c, CircleShape)); Spacer(Modifier.width(6.dp))
                        Text("${if (gap >= 0) "+" else "−"}${abs(gap)} min vs need", style = body(12.sp, c, FontWeight.Medium))
                    }
                }
            }
        }
        val (title, why) = Insights.headline(d)
        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(Color(0x33B7A3F5), CircleShape), contentAlignment = Alignment.Center) { Text("✦", style = body(13.sp, SleepPurple)) }
                Spacer(Modifier.width(10.dp))
                Text(title, style = body(15.sp, Color.White, FontWeight.SemiBold))
            }
            Text(listOfNotNull(why, Insights.nightSentence(d)).joinToString(" "), style = body(13.sp, HelioColors.TextSoft))
        }
        Insights.sleepHealth(d)?.let { SleepHealthGrid(it) }
    }
}

@Composable
private fun SleepHealthGrid(h: Insights.SleepHealth) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("SLEEP HEALTH", style = cap(12.sp, Color.White), modifier = Modifier.weight(1f))
            Text("${h.met} OF ${h.of} MET", style = cap(10.sp))
        }
        val cells = listOf(
            Triple("Duration · 7–9 h", Insights.hm(h.asleepMin), h.durationOk),
            Triple("Efficiency · ≥ 85%", "${h.efficiencyPct.roundToInt()}%", h.efficiencyOk),
            Triple("Timing · midpoint 02–04", String.format("%02d:%02d", h.midpoint.hour, h.midpoint.minute), h.timingOk),
            Triple("Regularity · SRI ≥ 70", h.sri?.roundToInt()?.toString() ?: "—", h.regularityOk),
        )
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, v, ok) ->
                    Column(Modifier.weight(1f).card(16.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(when (ok) { true -> "Met"; false -> "Not met"; null -> "Not yet" }, style = body(14.sp, Color.White, FontWeight.SemiBold))
                        Text(label, style = body(11.sp, HelioColors.TextMuted))
                        Text(v, style = num(16.sp, FontWeight.SemiBold))
                        Box(Modifier.padding(top = 4.dp).width(56.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(when (ok) { true -> Good; false -> Warn; null -> Color(0x26FFFFFF) }))
                    }
                }
            }
        }
    }
}

/** Biological age from your habits: the range, the gap to your age, and what moves it. */
@Composable
fun HabitsAgeCard(h: Insights.HabitsAge) {
    val diff = (h.age - h.chronological).roundToInt()
    val tone = if (h.age - h.chronological < 0.5) Good else Warn
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().card().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("BIOLOGICAL AGE FROM HABITS" + if (h.early) " · EARLY ESTIMATE" else "", style = cap(10.sp))
            Text("${h.low}–${h.high}", style = num(48.sp, FontWeight.SemiBold))
            Text(
                when { diff == 0 -> "About your age, ${h.chronological.toInt()}"; diff < 0 -> "About ${-diff} ${if (diff == -1) "year" else "years"} younger than ${h.chronological.toInt()}"; else -> "About $diff ${if (diff == 1) "year" else "years"} older than ${h.chronological.toInt()}" },
                style = body(13.sp, tone, FontWeight.SemiBold),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(tone.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Text(if (h.early) "From the weeks there are so far; it firms up over six months of wear." else "From your last six months, against the typical person your age and sex.",
                style = body(12.sp, HelioColors.TextMuted))
        }
        Text("WHAT MOVES IT", style = cap(12.sp, Color.White))
        Column(Modifier.fillMaxWidth().card().padding(horizontal = 14.dp, vertical = 6.dp)) {
            h.habits.forEachIndexed { k, hb ->
                if (k > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(HelioColors.Divider))
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(hb.title, style = body(14.sp, Color.White, FontWeight.SemiBold))
                    Text(Insights.pull(hb.shownYears), style = body(13.sp, when { abs(hb.shownYears) < 0.25 -> HelioColors.TextMuted; hb.shownYears < 0 -> Good; else -> Warn }))
                    Row(Modifier.fillMaxWidth()) { Text(hb.figureLabel, style = body(13.sp), modifier = Modifier.weight(1f)); Text(hb.figure, style = num(13.sp, FontWeight.SemiBold)) }
                    hb.aim?.let { Row(Modifier.fillMaxWidth()) { Text("Aim for", style = body(13.sp), modifier = Modifier.weight(1f)); Text(it, style = num(13.sp, FontWeight.SemiBold)) } }
                }
            }
            if (h.fitnessCapped) Text("Resting heart rate, steps and fitness together move it by 3 years at most.", style = body(11.sp, HelioColors.TextMuted), modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(
            "Each habit is scored against the typical person your age and sex, by how much it moves the risk of dying in large studies, turned into years and shrunk by 40 %. " +
                "An estimate for insight, not a medical measurement, so it is shown as a range. Method: Ridge (after OpenStrap).",
            style = body(11.sp, HelioColors.TextMuted),
        )
    }
}
