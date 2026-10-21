package app.helio.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import app.helio.data.DaySummary
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.cap
import app.helio.ui.theme.num
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Sleep, Recovery and Strain as three rings. `collapse` 0 → big rings with the value inside and the
 * label underneath; 1 → small rings with the label beside them (the sticky header while scrolling).
 */
@Composable
fun RingsHeader(day: DaySummary, collapse: Float, big: Dp, onTap: (Int) -> Unit) {
    val small = 22.dp
    val size = lerp(big, small, collapse)
    Row(
        Modifier.fillMaxWidth().padding(vertical = lerp(10.dp, 6.dp, collapse)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RingItem(
            frac = (day.sleepPerformance ?: 0) / 100f, brush = Brush.linearGradient(listOf(Color(0xFF8FA9C7), Color(0xFFB9CCE2))),
            value = day.sleepPerformance?.let { "$it%" } ?: "--%", label = "SLEEP", size = size, collapse = collapse,
            onClick = { onTap(1) },
        )
        RingItem(
            frac = (day.recovery ?: 0) / 100f, brush = androidx.compose.ui.graphics.SolidColor(HelioColors.recovery(day.recovery)),
            value = day.recovery?.let { "$it%" } ?: "--%", label = "RECOVERY", size = size, collapse = collapse,
            valueColor = HelioColors.recovery(day.recovery), onClick = { onTap(2) },
        )
        RingItem(
            frac = ((day.strain ?: 0.0) / 21.0).toFloat(), brush = Brush.verticalGradient(listOf(HelioColors.StrainDeep, HelioColors.StrainLight)),
            value = day.strain?.let { String.format("%.1f", it) } ?: "--", label = "STRAIN", size = size, collapse = collapse,
            tick = day.strainTarget?.let { (it / 21.0).toFloat() }, valueColor = HelioColors.Strain, onClick = { onTap(3) },
        )
    }
}

@Composable
private fun RingItem(
    frac: Float, brush: Brush, value: String, label: String, size: Dp, collapse: Float, onClick: () -> Unit,
    tick: Float? = null, valueColor: Color = Color.White,
) {
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), tween(750, easing = FastOutSlowInEasing), label = "ring")
    Layout(
        modifier = Modifier.clickable(onClick = onClick),
        content = {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(size)) {
                    val sw = (this.size.minDimension * 0.075f).coerceAtLeast(2.5.dp.toPx())
                    val r = this.size.minDimension / 2 - sw / 2
                    val tl = Offset(center.x - r, center.y - r)
                    val sz = Size(r * 2, r * 2)
                    drawArc(HelioColors.Track, -90f, 360f, false, tl, sz, style = Stroke(sw))
                    drawArc(brush, -90f, 360f * f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                    if (tick != null && collapse < 0.6f) {
                        val a = Math.toRadians((-90.0 + 360.0 * tick))
                        drawLine(
                            Color.White,
                            Offset(center.x + (r - sw) * cos(a).toFloat(), center.y + (r - sw) * sin(a).toFloat()),
                            Offset(center.x + (r + sw) * cos(a).toFloat(), center.y + (r + sw) * sin(a).toFloat()),
                            strokeWidth = sw * 0.45f, cap = StrokeCap.Round,
                        )
                    }
                }
                Text(value, style = num((size.value * 0.24f).coerceAtLeast(6f).sp, color = valueColor), modifier = Modifier.alpha((1f - collapse * 2.2f).coerceIn(0f, 1f)))
            }
            Text("$label ›", style = cap(lerp(12.dp, 11.dp, collapse).value.sp, Color.White))
        },
    ) { ms, _ ->
        val ring = ms[0].measure(Constraints())
        val lab = ms[1].measure(Constraints())
        val gap = 7.dp.roundToPx()
        val wE = max(ring.width, lab.width); val hE = ring.height + gap + lab.height
        val wC = ring.width + gap + lab.width; val hC = max(ring.height, lab.height)
        fun l(a: Int, b: Int) = (a + (b - a) * collapse).roundToInt()
        layout(l(wE, wC), l(hE, hC)) {
            ring.place(l((wE - ring.width) / 2, 0), l(0, (hC - ring.height) / 2))
            lab.place(l((wE - lab.width) / 2, ring.width + gap), l(ring.height + gap, (hC - lab.height) / 2))
        }
    }
}
