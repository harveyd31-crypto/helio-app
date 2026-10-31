package app.helio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.data.LinkStatus
import app.helio.data.Profile
import app.helio.data.Workout
import app.helio.data.WorkoutMath
import app.helio.data.WorkoutStats
import app.helio.data.WorkoutType
import app.helio.data.SessionKind
import app.helio.data.Workouts
import app.helio.logic.Strain
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.body
import app.helio.ui.theme.cap
import app.helio.ui.theme.card
import app.helio.ui.theme.num
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val zoneColors = listOf(Color(0xFFF2F4F6), Color(0xFFB4C3D0), Color(0xFF5B9BC9), Color(0xFF7CC4A0), Color(0xFFF0B060), Color(0xFFE0603A))
private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val md = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
fun hms(s: Long) = String.format("%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)

@Composable
fun TypeBadge(t: WorkoutType, size: Int = 40) {
    Box(Modifier.size(size.dp).background(HelioColors.StrainBadge, CircleShape), contentAlignment = Alignment.Center) {
        SportGlyph(t)
    }
}

/** Under the activities on the overview: start a new one. */
@Composable
fun StartActivityButton(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("+  START ACTIVITY", style = cap(12.sp, Color.White)) }
}

@Composable
fun ActivityPicker(link: LinkStatus, onPick: (WorkoutType) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailHeader("START ACTIVITY", onClose)
        listOf(WorkoutType.MuayThai, WorkoutType.Running, WorkoutType.Tennis, WorkoutType.CrossFit, WorkoutType.Other).forEach { t ->
            Row(Modifier.fillMaxWidth().card(14.dp).clickable { onPick(t) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(t)
                Spacer(Modifier.width(14.dp))
                Text(t.label.uppercase(), style = cap(14.sp, Color.White), modifier = Modifier.weight(1f))
                Text("Start ›", style = body(13.sp, HelioColors.Strain, FontWeight.Medium))
            }
        }
        Text(
            if (link == LinkStatus.Live) "Recording uses the Helio's live heart rate. You can lock the phone."
            else "The Helio will connect when you start. Keep the phone within about 10 m.",
            style = body(12.sp, HelioColors.TextMuted),
        )
    }
}

/** While recording: elapsed time, heart rate and zone, live strain and calories. */
@Composable
fun RecordingView(active: Workouts.Active, hr: Int?, link: LinkStatus, hrMax: Double, rest: Double, profile: Profile, onEnd: () -> Unit, onDiscard: () -> Unit, onRounds: (() -> Unit)? = null) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val floors = WorkoutMath.floors(hrMax)
    val zone = hr?.let { b -> floors.count { b >= it } } ?: 0
    val strain = Strain.fromHeartRate(active.hr, rest, hrMax)
    var kcal = 0.0
    for (i in 1 until active.hr.size) kcal += WorkoutMath.kcalPerMin(active.hr[i].second.toDouble(), profile) * ((active.hr[i].first - active.hr[i - 1].first) / 60000.0)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TypeBadge(active.type, 34)
            Spacer(Modifier.width(10.dp))
            Text(active.type.label.uppercase(), style = cap(14.sp, Color.White), modifier = Modifier.weight(1f))
            Box(Modifier.size(8.dp).background(if (link == LinkStatus.Live) HelioColors.Live else HelioColors.TextDim, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(if (link == LinkStatus.Live) "RECORDING" else "WAITING FOR HELIO", style = cap(11.sp, HelioColors.TextMuted))
        }
        Text(hms((now - active.start) / 1000), style = num(60.sp))
        if (link != LinkStatus.Live) Text(
            "Phone out of range: the strap keeps measuring, and the gap fills in from it at the next sync.",
            style = body(12.sp, HelioColors.TextMuted),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(hr?.toString() ?: "—", style = num(48.sp, color = zoneColors[zone]))
            Text("  bpm · zone $zone", style = body(14.sp, HelioColors.TextMuted), modifier = Modifier.padding(bottom = 10.dp))
        }
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { z -> Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (z <= zone) zoneColors[z] else Color(0x14FFFFFF))) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(String.format("%.1f", strain), "Strain", Modifier.weight(1f), HelioColors.Strain)
            StatTile("${kcal.toInt()}", "Calories", Modifier.weight(1f))
            StatTile(active.hr.maxOfOrNull { it.second }?.toString() ?: "—", "Max HR", Modifier.weight(1f))
        }
        if (onRounds != null) {
            Box(
                Modifier.fillMaxWidth().height(54.dp).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(27.dp)).clickable(onClick = onRounds),
                contentAlignment = Alignment.Center,
            ) { Text(if (active.rounds.isEmpty()) "ROUND TIMER" else "ROUND TIMER · ${active.rounds.size} DONE", style = cap(14.sp, Color.White)) }
            Text("Tip: fold the phone half-open on a bench and the round timer opens by itself.", style = body(12.sp, HelioColors.TextMuted))
        }
        Box(
            Modifier.fillMaxWidth().height(54.dp).background(HelioColors.RecRed, RoundedCornerShape(27.dp)).clickable(onClick = onEnd),
            contentAlignment = Alignment.Center,
        ) { Text("END ACTIVITY", style = cap(14.sp, Color.White)) }
        Text("Discard", style = body(13.sp, HelioColors.TextMuted), modifier = Modifier.clickable(onClick = onDiscard).padding(6.dp))
    }
}

/** Finished activity, in the style of the screenshots: strain, HR chart, zones with typical range, key stats vs 30 days. */
@Composable
fun WorkoutDetail(
    w: Workout, s: WorkoutStats, history: List<WorkoutStats>, onDelete: (() -> Unit)?, hrMax: Double = 190.0,
    kind: SessionKind? = null, onKind: ((SessionKind) -> Unit)? = null,
    progress: (@Composable () -> Unit)? = null,
    onClose: () -> Unit,
) {
    val avg = if (history.isEmpty()) null else history
    var changing by remember(w.start) { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", style = num(30.sp, FontWeight.Medium), modifier = Modifier.clickable(onClick = onClose).padding(end = 12.dp))
            TypeBadge(w.type)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(w.type.label.uppercase(), style = cap(15.sp, Color.White))
                Text("${local(w.start).format(md)} ${local(w.start).format(hm)} to ${local(w.end).format(hm)}", style = body(13.sp, HelioColors.TextMuted))
            }
            if (kind != null && onKind != null && !changing) KindBadge(kind) { changing = true }
        }
        if (SessionKind.forSport(w.type).isNotEmpty() && onKind != null && (kind == null || changing)) KindPrompt(w.type) { onKind(it); changing = false }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            BigStat(String.format("%.1f", s.strain), "ACTIVITY STRAIN", HelioColors.Strain, avg?.map { it.strain }?.average()?.let { String.format("%.1f", it) })
            BigStat(String.format("%,d", s.calories), "CALORIES", Color.White, avg?.map { it.calories }?.average()?.let { String.format("%,d", it.toInt()) })
        }
        HrChart(w)
        app.helio.logic.RoundAnalysis.analyse(w, hrMax)?.let { RoundsCard(it, hrMax) }
        if (w.type == WorkoutType.Tennis) app.helio.logic.TennisAnalysis.analyse(w, hrMax)?.let { TennisCard(it, hrMax) }
        Zones(s, history)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("KEY STATISTICS", style = cap(13.sp, Color.White))
            Text("VS. 30 DAY AVERAGE", style = cap(11.sp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeyStat("CALORIES", "${s.calories}", "cals", avg?.let { "${it.map { h -> h.calories }.average().toInt()}cals" })
            KeyStat("AVG HR", "${s.avgHr}", "bpm", avg?.let { "${it.map { h -> h.avgHr }.average().toInt()}bpm" })
            KeyStat("MAX HR", "${s.maxHr}", "bpm", avg?.let { "${it.map { h -> h.maxHr }.average().toInt()}bpm" })
            KeyStat("DURATION", hms(s.durationS), "", avg?.let { hms(it.map { h -> h.durationS }.average().toLong()) })
        }
        progress?.invoke()
        if (onDelete != null) Text("Delete activity", style = body(13.sp, HelioColors.RecRed), modifier = Modifier.clickable(onClick = onDelete).padding(vertical = 6.dp))
    }
}

/** Round by round: intensity, time in the red, recovery in the rest, and whether you held it to the end. */
@Composable
private fun RoundsCard(r: app.helio.logic.RoundAnalysis.Result, hrMax: Double) {
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ROUNDS", style = cap(13.sp, Color.White), modifier = Modifier.weight(1f))
            Text("${r.rounds.size} rounds · best R${r.bestRound}", style = cap(11.sp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            r.fadePct?.let { f ->
                val held = f >= -3
                Column(Modifier.weight(1f).background(Color(0xFF2A2F35), RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text(if (held) "HELD" else "FADED", style = num(20.sp, FontWeight.Bold, if (held) HelioColors.RecGreen else HelioColors.RecYellow))
                    Text("Last round ${if (f >= 0) "+" else ""}$f% vs best", style = body(12.sp, HelioColors.TextMuted))
                }
            }
            r.avgDrop?.let { d ->
                Column(Modifier.weight(1f).background(Color(0xFF2A2F35), RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text("−$d bpm", style = num(20.sp, FontWeight.Bold, if (d >= 30) HelioColors.RecGreen else if (d >= 20) Color.White else HelioColors.RecYellow))
                    Text("Avg recovery, first 60 s of rest", style = body(12.sp, HelioColors.TextMuted))
                }
            }
            val red = r.rounds.sumOf { it.redS }
            Column(Modifier.weight(1f).background(Color(0xFF2A2F35), RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text(String.format("%d:%02d", red / 60, red % 60), style = num(20.sp, FontWeight.Bold, zoneColors[5]))
                Text("In the red (90 %+)", style = body(12.sp, HelioColors.TextMuted))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("", "AVG", "PEAK", "RED", "REST ↓").forEachIndexed { i, h -> Text(h, style = cap(10.sp), modifier = Modifier.weight(if (i == 0) 1.6f else 1f)) }
        }
        r.rounds.forEach { rd ->
            val z = WorkoutMath.floors(hrMax).count { rd.avgHr >= it }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1.6f), verticalAlignment = Alignment.CenterVertically) {
                    Text("R${rd.index}", style = num(14.sp, FontWeight.Bold), modifier = Modifier.width(30.dp))
                    val f = ((rd.avgHr / hrMax - 0.5) / 0.5).toFloat().coerceIn(0.05f, 1f)
                    Box(Modifier.weight(1f).padding(end = 10.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x14FFFFFF))) {
                        Box(Modifier.fillMaxWidth(f).height(8.dp).background(zoneColors[z]))
                    }
                }
                Text("${rd.avgHr}", style = num(14.sp), modifier = Modifier.weight(1f))
                Text("${rd.peakHr}", style = num(14.sp, color = HelioColors.TextSoft), modifier = Modifier.weight(1f))
                Text(String.format("%d:%02d", rd.redS / 60, rd.redS % 60), style = num(14.sp, color = HelioColors.TextSoft), modifier = Modifier.weight(1f))
                Text(rd.drop60?.let { "−$it" } ?: "—", style = num(14.sp, color = HelioColors.TextSoft), modifier = Modifier.weight(1f))
            }
        }
        Text("Rest ↓ is how far your heart rate falls in the first minute of rest. It drops less as you tire, and more as your conditioning improves.", style = body(12.sp, HelioColors.TextMuted))
    }
}

@Composable
private fun BigStat(v: String, label: String, color: Color, avg: String?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v, style = num(46.sp, FontWeight.Bold, color))
            if (avg != null) {
                Spacer(Modifier.width(8.dp))
                Text("▲ $avg", style = num(13.sp, FontWeight.SemiBold, HelioColors.TextSoft), modifier = Modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        Text(label, style = cap(12.sp))
    }
}

@Composable
private fun HrChart(w: Workout) {
    val tm = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        if (w.hr.size < 2) return@Canvas
        val left = 30.dp.toPx(); val bottom = size.height - 22.dp.toPx(); val top = 6.dp.toPx()
        val lo = 75.0; val hi = maxOf(175.0, (w.hr.maxOf { it.second } + 5).toDouble())
        fun x(t: Long) = left + (t - w.start).toFloat() / (w.end - w.start) * (size.width - left)
        fun y(b: Double) = bottom - ((b - lo) / (hi - lo)).toFloat() * (bottom - top)
        listOf(75, 100, 125, 150, 175).forEach { g ->
            drawLine(HelioColors.Divider, Offset(left, y(g.toDouble())), Offset(size.width, y(g.toDouble())), 1f)
            drawText(tm, "$g", Offset(0f, y(g.toDouble()) - 7.dp.toPx()), cap(10.sp))
        }
        w.rounds.forEach { (a, b) ->
            drawRect(Color(0x1FE8384F), Offset(x(a), top), Size(x(b) - x(a), bottom - top))
        }
        val line = Path(); val area = Path()
        w.hr.forEachIndexed { i, (t, b) ->
            val px = x(t); val py = y(b.toDouble().coerceAtLeast(lo))
            if (i == 0) { line.moveTo(px, py); area.moveTo(px, bottom); area.lineTo(px, py) } else { line.lineTo(px, py); area.lineTo(px, py) }
        }
        area.lineTo(x(w.hr.last().first), bottom); area.close()
        drawPath(area, Brush.verticalGradient(listOf(Color(0x552E8FE6), Color(0x112E8FE6)), startY = top, endY = bottom))
        drawPath(line, HelioColors.Strain, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
        listOf(w.start, w.end).forEach { t ->
            drawLine(Color(0x99FFFFFF), Offset(x(t), top), Offset(x(t), bottom), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
            drawCircle(Color.White, 3.dp.toPx(), Offset(x(t), bottom))
        }
        drawText(tm, local(w.start).format(hm), Offset(left, size.height - 16.dp.toPx()), num(12.sp))
        val e = tm.measure(local(w.end).format(hm), num(12.sp))
        drawText(e, topLeft = Offset(size.width - e.size.width, size.height - 16.dp.toPx()))
    }
}

@Composable
private fun Zones(s: WorkoutStats, history: List<WorkoutStats>) {
    val total = s.zoneSeconds.sum().coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(18.dp, 14.dp)) {
                drawRect(Color(0x33FFFFFF))
                drawLine(Color.White, Offset(0f, 0f), Offset(0f, size.height), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)))
                drawLine(Color.White, Offset(size.width, 0f), Offset(size.width, size.height), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)))
            }
            Text("  TYPICAL RANGE", style = cap(12.sp), modifier = Modifier.weight(1f))
            Text("DURATION ", style = cap(12.sp)); Text(hms(s.durationS), style = num(14.sp))
        }
        (5 downTo 0).forEach { z ->
            val sec = s.zoneSeconds[z]
            val pct = sec.toFloat() / total
            val range = when (z) {
                0 -> "<${s.zoneFloors[0]} BPM"
                5 -> "${s.zoneFloors[4]}+ BPM"
                else -> "${s.zoneFloors[z - 1]}-${s.zoneFloors[z] - 1} BPM"
            }
            val hp = history.map { h -> h.zoneSeconds[z].toFloat() / h.zoneSeconds.sum().coerceAtLeast(1) }
            val typical = if (hp.size >= 2) hp.min() to hp.max() else null
            val dim = sec == 0L
            Column(Modifier.fillMaxWidth().background(Color(0xFF2A2F35), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ZONE $z  ", style = cap(13.sp, if (dim) HelioColors.TextMuted else Color.White))
                    Text("$range  ", style = cap(12.sp))
                    Text("${(pct * 100).toInt()}%", style = num(13.sp, FontWeight.SemiBold, zoneColors[z]))
                    Spacer(Modifier.weight(1f))
                    Text(String.format("%d:%02d", sec / 3600, sec % 3600 / 60), style = num(20.sp, FontWeight.Bold, if (dim) HelioColors.TextMuted else Color.White))
                    Text(String.format(":%02d", sec % 60), style = num(13.sp, FontWeight.SemiBold, HelioColors.TextMuted))
                }
                if (!dim || typical != null) {
                    Spacer(Modifier.height(8.dp))
                    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                        val h = size.height
                        // hatched track
                        var hx = 0f
                        while (hx < size.width + h) { drawLine(Color(0x1FFFFFFF), Offset(hx, h), Offset(hx + h * 0.8f, 0f), 3f); hx += 9f }
                        if (pct > 0) drawRoundRect(zoneColors[z], Offset(0f, 0f), Size((pct * size.width).coerceAtLeast(h * 0.6f), h), CornerRadius(h / 2))
                        typical?.let { (a, b) ->
                            val x0 = a * size.width; val x1 = (b * size.width).coerceAtLeast(x0 + 6f)
                            drawRect(Color(0x26FFFFFF), Offset(x0, -3f), Size(x1 - x0, h + 6f))
                            listOf(x0, x1).forEach { xx -> drawLine(Color.White, Offset(xx, -3f), Offset(xx, h + 3f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyStat(label: String, value: String, unit: String, avg: String?) {
    Column(Modifier.width(170.dp).background(Color(0xFF2A2F35), RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = cap(12.sp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = num(34.sp, FontWeight.Bold))
            if (unit.isNotEmpty()) Text(" $unit", style = body(15.sp, Color.White), modifier = Modifier.padding(bottom = 5.dp))
        }
        if (avg != null) Text("▲ $avg", style = num(13.sp, FontWeight.SemiBold, HelioColors.TextSoft), modifier = Modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
