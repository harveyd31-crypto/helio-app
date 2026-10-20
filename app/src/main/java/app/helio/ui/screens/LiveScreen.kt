package app.helio.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.data.LinkStatus
import app.helio.data.LiveState
import app.helio.ui.components.LiveStressLine
import app.helio.ui.components.StressGauge
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.body
import app.helio.ui.theme.cap
import app.helio.ui.theme.card
import app.helio.ui.theme.num
import kotlinx.coroutines.delay

/**
 * Live mode. Half-folded, the top half (above the hinge) shows the live gauge and the last
 * 10 minutes; the bottom half has session numbers, the breathing guide and a vertical control bar.
 */
@Composable
fun LiveScreen(
    live: LiveState,
    topHeight: Dp?,
    onClose: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSync: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    var breathing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(HelioColors.Bg)) {
        Column(
            Modifier.fillMaxWidth().then(if (topHeight != null) Modifier.height(topHeight) else Modifier.weight(1f))
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(HelioColors.BgTop, HelioColors.BgMid)))
                .padding(horizontal = 22.dp, vertical = 10.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close live mode", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(7.dp).background(if (live.status == LinkStatus.Live) HelioColors.Live else HelioColors.TextDim, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("LIVE STRESS MONITOR", style = cap(13.sp, Color.White))
                Spacer(Modifier.weight(1f))
                Text(statusText(live), style = body(11.sp, HelioColors.TextMuted))
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Column(Modifier.width(240.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    StressGauge(live.stress, 230.dp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        Big(live.hr?.toString() ?: "—", "BPM", Color.White)
                        val diff = live.hr?.let { it - live.restingBaseline.toInt() }
                        Big(diff?.let { (if (it >= 0) "+" else "") + it } ?: "—", "VS RESTING", if ((diff ?: 0) > 5) HelioColors.RecYellow else Color.White)
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    LiveStressLine(live.stressHistory, 10 * 60_000L, now, Modifier.fillMaxWidth().weight(1f))
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("−10 MIN", style = cap(10.sp)); Text("NOW", style = cap(10.sp, Color.White))
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(12.dp).background(Color(0xFF050607)))
        Row(Modifier.fillMaxWidth().weight(1f).padding(start = 22.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val mins = live.sessionStart?.let { (now - it) / 60000 }
                    Tile(mins?.let { String.format("%d:%02d", it / 60, it % 60) } ?: "—", "SESSION", Modifier.weight(1f))
                    val delta = if (live.stress != null && live.stressAtStart != null) live.stress - live.stressAtStart else null
                    Tile(delta?.let { String.format("%+.1f", it) } ?: "—", "STRESS SINCE START", Modifier.weight(1f), if ((delta ?: 0.0) <= 0) HelioColors.StressGreen else HelioColors.StressOrange)
                    Tile(String.format("%.0f", live.restingBaseline), "RESTING BASELINE", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().weight(1f).card().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    BreathingCircle(breathing)
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("BREATHE · 4 IN · 6 OUT", style = cap(11.sp, HelioColors.StressGreen))
                        Text(if (breathing) "Follow the circle" else "Tap the green button", style = num(30.sp))
                        Text(
                            if (live.status == LinkStatus.Live) "Stress is computed every 5 seconds from your heart rate against your resting baseline."
                            else "Connect your Helio Strap to see live heart rate and stress.",
                            style = body(14.sp),
                        )
                    }
                }
            }
            Column(
                Modifier.width(76.dp).fillMaxHeight().background(Color(0x0FFFFFFF), RoundedCornerShape(38.dp))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(38.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RailButton("Breathe", HelioColors.StressGreen, Color(0xFF0B1A10), Icons.Filled.Star) { breathing = !breathing }
                val connected = live.status == LinkStatus.Live || live.status == LinkStatus.Connecting || live.status == LinkStatus.Scanning
                RailButton(if (connected) "Disconnect Helio" else "Connect Helio", if (connected) Color(0x14FFFFFF) else HelioColors.StrainBadge, Color.White, Icons.Filled.Favorite) {
                    if (connected) onDisconnect() else onConnect()
                }
                RailButton("Sync now", Color(0x14FFFFFF), Color.White, Icons.Filled.Refresh, onSync)
            }
        }
    }
}

private fun statusText(l: LiveState) = when (l.status) {
    LinkStatus.Off -> "Not connected"
    LinkStatus.Scanning -> "Looking for Helio…"
    LinkStatus.Connecting -> "Connecting…"
    LinkStatus.Live -> l.deviceName ?: "Helio"
    LinkStatus.Lost -> "Signal lost · retrying"
}

@Composable
private fun Big(v: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, style = num(26.sp, FontWeight.SemiBold, color))
        Text(label, style = cap(10.sp))
    }
}

@Composable
private fun Tile(v: String, label: String, modifier: Modifier, color: Color = Color.White) {
    Column(modifier.card(14.dp).padding(horizontal = 14.dp, vertical = 11.dp)) {
        Text(v, style = num(24.sp, FontWeight.SemiBold, color))
        Text(label, style = cap(10.sp))
    }
}

@Composable
private fun RailButton(label: String, bg: Color, fg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp).background(bg, CircleShape)) {
        Icon(icon, contentDescription = label, tint = fg)
    }
}

@Composable
private fun BreathingCircle(active: Boolean) {
    val t = rememberInfiniteTransition(label = "breath")
    val scale by t.animateFloat(
        initialValue = 0.55f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 10_000
                0.55f at 0 using LinearEasing
                1.0f at 4_000 using LinearEasing
                0.55f at 10_000
            },
            RepeatMode.Restart,
        ),
        label = "scale",
    )
    val s = if (active) scale else 0.7f
    Canvas(Modifier.size(132.dp)) {
        val r = size.minDimension / 2
        drawCircle(Color(0x14FFFFFF), r - 4, style = Stroke(5f))
        drawCircle(HelioColors.StressGreen.copy(alpha = 0.14f), r * s)
        drawCircle(HelioColors.StressGreen.copy(alpha = 0.28f), r * s * 0.62f)
    }
}
