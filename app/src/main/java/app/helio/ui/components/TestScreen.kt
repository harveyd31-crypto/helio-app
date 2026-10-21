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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.ble.TestState
import app.helio.data.LinkStatus
import app.helio.data.TestResult
import app.helio.logic.RunTest
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.body
import app.helio.ui.theme.cap
import app.helio.ui.theme.card
import app.helio.ui.theme.num

/** The guided VO2 test: checklist before, big timer during, result after. */
@Composable
fun TestScreen(
    state: TestState,
    link: LinkStatus,
    hr: Int?,
    last: TestResult?,
    canSimulate: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onSimulate: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailHeader("VO2 RUNNING TEST", onClose)
        if (state.running) Running(state, onCancel)
        else {
            if (last != null) Result(last)
            Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("HOW IT WORKS", style = cap(11.sp, Color.White))
                RunTest.phases.forEach { p ->
                    Row {
                        Text(String.format("%d:%02d", p.startS / 60, p.startS % 60), style = num(13.sp, FontWeight.Medium, HelioColors.TextMuted), modifier = Modifier.padding(end = 10.dp))
                        Text(p.name.uppercase(), style = cap(12.sp, if (p.startS == 0) HelioColors.TextMuted else HelioColors.Strain), modifier = Modifier.padding(end = 10.dp))
                        Text(p.cue.substringAfter(". ").ifEmpty { p.cue }, style = body(12.sp))
                    }
                }
                Text("Flat route, open sky, phone in your pocket. Voice and vibration tell you when to change pace. Once a month is enough.", style = body(12.sp, HelioColors.TextMuted))
            }
            val ready = link == LinkStatus.Live && hr != null
            Check("Helio heart rate streaming", ready, if (ready) "$hr bpm" else "Connect in the Live tab first")
            Check("Location permission", true, "Asked when you start")
            Box(
                Modifier.fillMaxWidth().height(54.dp).background(if (ready) HelioColors.StrainBadge else Color(0x14FFFFFF), RoundedCornerShape(27.dp))
                    .clickable(enabled = ready, onClick = onStart),
                contentAlignment = Alignment.Center,
            ) { Text("START 12-MIN TEST", style = cap(14.sp, if (ready) Color.White else HelioColors.TextDim)) }
            if (canSimulate) {
                Text("Simulate a test (sample data)", style = body(13.sp, HelioColors.Strain, FontWeight.Medium), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onSimulate).padding(8.dp))
            }
        }
    }
}

@Composable
private fun Check(label: String, ok: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth().card(14.dp).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(if (ok) HelioColors.RecGreen else HelioColors.RecYellow, RoundedCornerShape(5.dp)))
        Text(label, style = body(14.sp, Color.White), modifier = Modifier.weight(1f).padding(start = 10.dp))
        Text(detail, style = body(12.sp, HelioColors.TextMuted))
    }
}

@Composable
private fun Running(s: TestState, onCancel: () -> Unit) {
    val ph = RunTest.phases[s.phase]
    val phaseLeft = (ph.endS - s.elapsedS).coerceAtLeast(0)
    val prog by animateFloatAsState(s.elapsedS / RunTest.TOTAL_S.toFloat(), tween(900, easing = FastOutSlowInEasing), label = "p")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp)) {
                val sw = 12.dp.toPx(); val r = size.minDimension / 2 - sw
                drawArc(HelioColors.Track, -90f, 360f, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(sw))
                drawArc(HelioColors.StrainBadge, -90f, 360f * prog, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ph.name.uppercase(), style = cap(13.sp, HelioColors.Strain))
                Text(String.format("%d:%02d", phaseLeft / 60, phaseLeft % 60), style = num(52.sp))
                Text("left in this pace", style = body(12.sp, HelioColors.TextMuted))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(s.hr?.toString() ?: "—", "Heart rate", Modifier.weight(1f))
            StatTile(s.speedMps?.takeIf { it > 0.5 }?.let { val p = 1000 / it; String.format("%d:%02d", (p / 60).toInt(), (p % 60).toInt()) } ?: "—", "Pace /km", Modifier.weight(1f))
            StatTile(String.format("%.2f", s.distanceM / 1000), "km", Modifier.weight(1f))
        }
        Text(s.gpsAccuracy?.let { "GPS ±${it.toInt()} m" } ?: "Waiting for GPS…", style = body(12.sp, if ((s.gpsAccuracy ?: 99f) <= 15f) HelioColors.RecGreen else HelioColors.RecYellow))
        Text("Cancel test", style = body(13.sp, HelioColors.RecRed, FontWeight.Medium), modifier = Modifier.clickable(onClick = onCancel).padding(8.dp))
    }
}

@Composable
private fun Result(r: TestResult) {
    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("LAST TEST", style = cap(11.sp, Color.White))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(r.vo2max?.let { String.format("%.1f", it) } ?: "—", style = num(44.sp, color = if (r.valid) Color.White else HelioColors.TextMuted))
            Text(if (r.valid) "  ml/kg/min · counted ×2" else "  not counted", style = body(13.sp, if (r.valid) HelioColors.RecGreen else HelioColors.RecYellow), modifier = Modifier.padding(bottom = 8.dp))
        }
        r.stages.forEachIndexed { i, (v, h, o) ->
            val p = 1000 / v
            Text(String.format("%s · %d:%02d /km · %.0f bpm · %.1f ml/kg/min", listOf("Easy", "Steady", "Hard")[i], (p / 60).toInt(), (p % 60).toInt(), h, o), style = body(12.sp))
        }
        Text(String.format("%.2f km · line fit %.2f", r.distanceM / 1000, r.r2), style = body(12.sp, HelioColors.TextMuted))
        r.problems.forEach { Text("• $it", style = body(12.sp, HelioColors.RecYellow)) }
    }
}
