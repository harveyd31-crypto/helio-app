package app.helio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.data.DaySummary
import app.helio.data.SleepInfo
import app.helio.data.Stage
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.body
import app.helio.ui.theme.cap
import app.helio.ui.theme.card
import app.helio.ui.theme.num
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val stageColor = mapOf(
    Stage.Awake to Color(0xFFE07A4F),
    Stage.Rem to Color(0xFF2F8F6E),
    Stage.Light to Color(0xFF4A6FB5),
    Stage.Deep to Color(0xFF6A4FC2),
)
private val stageName = mapOf(Stage.Awake to "Awake", Stage.Rem to "REM", Stage.Light to "Light", Stage.Deep to "Deep")
private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
private fun t(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(hhmm)
private fun dur(min: Int) = if (min >= 60) String.format("%dh %02dm", min / 60, min % 60) else "${min}m"

/** Sleep details: the night's hypnogram with heart rate and breathing, stage totals, and the week. */
@Composable
fun SleepDetail(days: List<DaySummary>, selected: Int, onClose: (() -> Unit)?) {
    val day = days[selected]
    val s = day.sleep
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onClose != null) DetailHeader("SLEEP", onClose)
        if (s == null) {
            Text("No sleep recorded for this night.", style = body(14.sp))
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(dur(s.minutesAsleep), style = num(40.sp))
            Spacer(Modifier.width(10.dp))
            Text("${day.sleepPerformance ?: "—"}% of need · ${s.bed.format(hhmm)} – ${s.wake.format(hhmm)}", style = body(13.sp, HelioColors.TextMuted), modifier = Modifier.padding(bottom = 7.dp))
        }
        if (s.stages.isNotEmpty()) {
            Hypnogram(s)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Stage.Awake, Stage.Rem, Stage.Light, Stage.Deep).forEach { st ->
                    val m = s.minutesIn(st)
                    val total = s.stages.sumOf { it.end - it.start } / 60_000.0
                    Column(Modifier.weight(1f).card(14.dp).padding(horizontal = 10.dp, vertical = 9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(stageColor.getValue(st), CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(stageName.getValue(st).uppercase(), style = cap(10.sp))
                        }
                        Text(dur(m), style = num(17.sp))
                        Text(if (total > 0) "${(m / total * 100).toInt()}%" else "", style = body(11.sp, HelioColors.TextMuted))
                    }
                }
            }
        } else {
            Text("No sleep stages for this night: only the total is available.", style = body(13.sp, HelioColors.TextMuted))
        }
        SleepExtras(day.extras)

        val from = (selected - 6).coerceAtLeast(0)
        val week = days.subList(from, selected + 1)
        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SLEEP DURATION · 7 NIGHTS", style = cap(11.sp, Color.White))
            DurationBars(week, selected - from)
        }
        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SLEEP REGULARITY", style = cap(11.sp, Color.White))
            RegularityBars(week, selected - from)
        }
    }
}

@Composable
private fun Hypnogram(s: SleepInfo) {
    var scrub by remember(s) { mutableStateOf<Long?>(null) }
    val tm = rememberTextMeasurer()
    val span = (s.endMs - s.startMs).coerceAtLeast(1)
    val at = scrub
    val block = at?.let { m -> s.stages.firstOrNull { m >= it.start && m < it.end } }
    val hrAt = at?.let { m -> s.hr.minByOrNull { kotlin.math.abs(it.first - m) }?.second }
    val respAt = at?.let { m -> s.resp.minByOrNull { kotlin.math.abs(it.first - m) }?.second }

    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(block?.let { "${stageName[it.stage]}  ${t(it.start)}–${t(it.end)}" } ?: "Tap or drag the graph", style = body(12.sp, HelioColors.TextMuted))
                Text(block?.let { dur(((it.end - it.start) / 60_000).toInt()) } ?: "—", style = num(18.sp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(HelioColors.RecRed, CircleShape)); Spacer(Modifier.width(5.dp)); Text("Heart rate", style = body(12.sp, HelioColors.TextMuted)) }
                Text(hrAt?.let { "$it bpm" } ?: "—", style = num(16.sp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(HelioColors.StressBlue, CircleShape)); Spacer(Modifier.width(5.dp)); Text("Breathing", style = body(12.sp, HelioColors.TextMuted)) }
                Text(respAt?.let { String.format("%.0f /min", it) } ?: "—", style = num(16.sp))
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(200.dp)
                .pointerInput(s) { detectTapGestures { o -> scrub = s.startMs + ((o.x - 52.dp.toPx()) / (size.width - 52.dp.toPx()) * span).toLong().coerceIn(0, span) } }
                .pointerInput(s) { detectDragGestures { c, _ -> scrub = s.startMs + ((c.position.x - 52.dp.toPx()) / (size.width - 52.dp.toPx()) * span).toLong().coerceIn(0, span) } },
        ) {
            val left = 52.dp.toPx()
            val w = size.width - left
            val rowH = (size.height - 18.dp.toPx()) / 4
            fun x(ms: Long) = left + (ms - s.startMs).toFloat() / span * w
            val rows = listOf(Stage.Awake, Stage.Rem, Stage.Light, Stage.Deep)
            rows.forEachIndexed { i, st ->
                drawLine(HelioColors.Divider, Offset(left, (i + 1) * rowH), Offset(size.width, (i + 1) * rowH), 1f)
                drawText(tm, stageName.getValue(st), Offset(0f, i * rowH + rowH / 2 - 8.dp.toPx()), body(11.sp, HelioColors.TextMuted))
            }
            var prev: Pair<Float, Int>? = null
            s.stages.sortedBy { it.start }.forEach { b ->
                val row = rows.indexOf(b.stage)
                val x0 = x(b.start); val x1 = x(b.end)
                prev?.let { (px, pr) -> if (pr != row) drawLine(Color(0x55FFFFFF), Offset(x0, (minOf(pr, row) + 0.5f) * rowH), Offset(x0, (maxOf(pr, row) + 0.5f) * rowH), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))) }
                drawRoundRect(stageColor.getValue(b.stage), Offset(x0, row * rowH + rowH * 0.12f), Size((x1 - x0).coerceAtLeast(1f), rowH * 0.76f), CornerRadius(3f))
                prev = x1 to row
            }
            fun line(vals: List<Pair<Long, Double>>, top: Float, bottom: Float, c: Color) {
                if (vals.size < 2) return
                val lo = vals.minOf { it.second } - 1; val hi = vals.maxOf { it.second } + 1
                val p = Path()
                vals.forEachIndexed { i, (m, v) -> val y = bottom - ((v - lo) / (hi - lo)).toFloat() * (bottom - top); if (i == 0) p.moveTo(x(m), y) else p.lineTo(x(m), y) }
                drawPath(p, c, style = Stroke(1.8.dp.toPx(), join = StrokeJoin.Round))
            }
            line(s.hr.map { it.first to it.second.toDouble() }, 4f, rowH * 1.1f, HelioColors.RecRed)
            line(s.resp, rowH * 3.15f, rowH * 4f - 4f, HelioColors.StressBlue)
            drawText(tm, t(s.startMs), Offset(left, size.height - 14.dp.toPx()), body(10.sp, HelioColors.TextMuted))
            val endLabel = tm.measure(t(s.endMs), body(10.sp, HelioColors.TextMuted))
            drawText(endLabel, topLeft = Offset(size.width - endLabel.size.width, size.height - 14.dp.toPx()))
            at?.let { m -> drawLine(Color.White, Offset(x(m), 0f), Offset(x(m), rowH * 4), 1.5f) }
        }
    }
}

@Composable
private fun DurationBars(week: List<DaySummary>, sel: Int) {
    val tm = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val left = 34.dp.toPx(); val bottom = size.height - 20.dp.toPx(); val top = 6.dp.toPx()
        val maxH = 10 * 60f
        fun y(min: Float) = bottom - min / maxH * (bottom - top)
        listOf(0, 5, 10).forEach { h ->
            drawLine(HelioColors.Divider, Offset(left, y(h * 60f)), Offset(size.width, y(h * 60f)), 1f)
            drawText(tm, "$h:00", Offset(0f, y(h * 60f) - 7.dp.toPx()), body(10.sp, HelioColors.TextMuted))
        }
        val slot = (size.width - left) / 7
        week.forEachIndexed { i, d ->
            val cx = left + slot * (i + 0.5f)
            val bw = slot * 0.45f
            if (i == sel) drawRoundRect(Color(0x12FFFFFF), Offset(cx - slot * 0.42f, top), Size(slot * 0.84f, size.height - top), CornerRadius(8f))
            val s = d.sleep
            if (s != null) {
                var acc = 0f
                val parts = if (s.stages.isEmpty()) listOf(Stage.Light to s.minutesAsleep) else listOf(Stage.Deep, Stage.Light, Stage.Rem, Stage.Awake).map { it to s.minutesIn(it) }
                parts.forEach { (st, m) ->
                    if (m > 0) drawRect(stageColor.getValue(st), Offset(cx - bw / 2, y(acc + m)), Size(bw, y(acc) - y(acc + m)))
                    acc += m
                }
            }
            val l = tm.measure(d.date.dayOfWeek.name.take(1), cap(11.sp, if (i == sel) Color.White else HelioColors.TextMuted))
            drawText(l, topLeft = Offset(cx - l.size.width / 2, size.height - l.size.height))
        }
    }
}

@Composable
private fun RegularityBars(week: List<DaySummary>, sel: Int) {
    val tm = rememberTextMeasurer()
    // Minutes after 18:00 the evening before
    fun bedMin(s: SleepInfo) = (s.bed.toSecondOfDay() / 60 + (if (s.bed.hour >= 12) -18 * 60 else 6 * 60)).toFloat()
    fun wakeMin(s: SleepInfo) = (s.wake.toSecondOfDay() / 60 + 6 * 60).toFloat()
    val nights = week.mapNotNull { it.sleep }
    if (nights.isEmpty()) return
    val lo = (nights.minOf { bedMin(it) } - 60).coerceAtLeast(0f)
    val hi = nights.maxOf { wakeMin(it) } + 60
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val bottom = size.height - 20.dp.toPx(); val top = 18.dp.toPx()
        fun y(m: Float) = bottom - (m - lo) / (hi - lo) * (bottom - top)
        val slot = size.width / 7
        week.forEachIndexed { i, d ->
            val cx = slot * (i + 0.5f)
            if (i == sel) drawRoundRect(Color(0x12FFFFFF), Offset(cx - slot * 0.42f, 0f), Size(slot * 0.84f, size.height), CornerRadius(8f))
            d.sleep?.let { s ->
                val y0 = y(wakeMin(s)); val y1 = y(bedMin(s))
                drawRoundRect(Color(0xFF6E86E6), Offset(cx - slot * 0.2f, y0), Size(slot * 0.4f, y1 - y0), CornerRadius(8f))
                val a = tm.measure(s.wake.format(hhmm), num(11.sp, FontWeight.Medium, Color(0xFF8EA2F0)))
                drawText(a, topLeft = Offset(cx - a.size.width / 2, y0 - a.size.height - 2))
                val b = tm.measure(s.bed.format(hhmm), num(11.sp, FontWeight.Medium, Color(0xFF8EA2F0)))
                drawText(b, topLeft = Offset(cx - b.size.width / 2, y1 + 2))
            }
            val l = tm.measure(d.date.dayOfWeek.name.take(1), cap(11.sp, if (i == sel) Color.White else HelioColors.TextMuted))
            drawText(l, topLeft = Offset(cx - l.size.width / 2, size.height - l.size.height))
        }
    }
}
