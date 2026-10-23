package app.helio.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.data.LinkStatus
import app.helio.data.LiveState
import app.helio.data.RoundTimer
import app.helio.data.RoundTimer.Phase
import app.helio.data.WorkoutMath
import app.helio.data.Workouts
import app.helio.logic.Strain
import app.helio.ui.components.zoneColors
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.body
import app.helio.ui.theme.cap
import app.helio.ui.theme.card
import app.helio.ui.theme.num
import kotlinx.coroutines.delay

private fun mmss(ms: Long): String { val s = (ms + 999) / 1000; return String.format("%d:%02d", s / 60, s % 60) }

/**
 * Round timer during an activity. Half-folded: the clock, round and live heart rate stand up above the
 * hinge where you can read them from the bag; the controls lie flat below. Also opens full screen from
 * the recording view when the phone isn't folded. The screen stays on while it's shown.
 */
@Composable
fun RoundsScreen(
    live: LiveState,
    active: Workouts.Active,
    hrMax: Double,
    topHeight: Dp?,
    onClose: () -> Unit,
    onEnd: () -> Unit,
) {
    val ctx = LocalContext.current
    val cfg by RoundTimer.config.collectAsState()
    val st by RoundTimer.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(200); now = System.currentTimeMillis() } }
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    val left = st.leftMs(now)
    val target = when (st.phase) {
        Phase.Work -> if (left <= 10_000) HelioColors.RecYellow else HelioColors.RecRed
        Phase.Rest -> HelioColors.StressBlue
        Phase.Prep -> HelioColors.RecYellow
        Phase.Done -> HelioColors.RecGreen
        Phase.Idle -> Color.White
    }
    val phaseColor by animateColorAsState(target, tween(300), label = "phase")
    val floors = WorkoutMath.floors(hrMax)
    val zone = live.hr?.let { b -> floors.count { b >= it } } ?: 0

    Column(Modifier.fillMaxSize().background(HelioColors.Bg).then(if (topHeight == null) Modifier.safeDrawingPadding() else Modifier)) {
        // ── Upright half: what you read from across the room
        BoxWithConstraints(
            Modifier.fillMaxWidth().then(if (topHeight != null) Modifier.height(topHeight) else Modifier.weight(1.15f))
                .background(Brush.verticalGradient(listOf(HelioColors.BgTop, HelioColors.BgMid)))
                .padding(horizontal = 22.dp, vertical = 8.dp),
        ) {
            val wide = maxWidth >= 560.dp
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close round timer", tint = Color.White) }
                    Spacer(Modifier.weight(1f))
                    Text("ROUND TIMER · ${active.type.label.uppercase()}", style = cap(13.sp, Color.White))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(7.dp).background(if (live.status == LinkStatus.Live) HelioColors.Live else HelioColors.TextDim, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(if (live.status == LinkStatus.Live) "LIVE" else "NO HR", style = cap(11.sp))
                }
                if (wide) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Clock(st, cfg, left, phaseColor, Modifier.weight(1f), big = true)
                    Spacer(Modifier.width(20.dp))
                    HeartPanel(live.hr, zone, active, hrMax, Modifier.width(250.dp))
                } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    Clock(st, cfg, left, phaseColor, Modifier.fillMaxWidth(), big = false)
                    HeartPanel(live.hr, zone, active, hrMax, Modifier.fillMaxWidth())
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(12.dp).background(Color(0xFF050607)))
        // ── Flat half: controls
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 22.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!st.running) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundTimer.presets.forEach { (name, c) ->
                        val on = c.rounds == cfg.rounds && c.workS == cfg.workS && c.restS == cfg.restS
                        Text(
                            "${name.uppercase()}  ${c.rounds}×${mmss(c.workS * 1000L)}",
                            style = cap(11.sp, if (on) Color.Black else Color.White),
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Color.White else Color(0x14FFFFFF))
                                .clickable { RoundTimer.setConfig(ctx, c) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stepper("ROUNDS", "${cfg.rounds}", Modifier.weight(1f),
                        { RoundTimer.setConfig(ctx, cfg.copy(rounds = cfg.rounds - 1)) }, { RoundTimer.setConfig(ctx, cfg.copy(rounds = cfg.rounds + 1)) })
                    Stepper("ROUND", mmss(cfg.workS * 1000L), Modifier.weight(1f),
                        { RoundTimer.setConfig(ctx, cfg.copy(workS = cfg.workS - 15)) }, { RoundTimer.setConfig(ctx, cfg.copy(workS = cfg.workS + 15)) })
                    Stepper("REST", mmss(cfg.restS * 1000L), Modifier.weight(1f),
                        { RoundTimer.setConfig(ctx, cfg.copy(restS = cfg.restS - 15)) }, { RoundTimer.setConfig(ctx, cfg.copy(restS = cfg.restS + 15)) })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(if (st.phase == Phase.Done) "AGAIN" else "START ROUNDS", HelioColors.RecGreen, Color.Black, Modifier.weight(1f)) { RoundTimer.start(ctx) }
                    Pill("END ACTIVITY", Color(0x1FFFFFFF), Color.White, Modifier.width(150.dp), onEnd)
                }
                val total = cfg.rounds * cfg.workS + (cfg.rounds - 1).coerceAtLeast(0) * cfg.restS
                Text("Total ${mmss(total * 1000L)} · bell at each round, double clap 10 s before the end, long tone at the end.", style = body(12.sp, HelioColors.TextMuted))
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val strain = Strain.fromHeartRate(active.hr, live.restingBaseline, hrMax)
                    StatBox(String.format("%.1f", strain), "STRAIN", Modifier.weight(1f), HelioColors.Strain)
                    StatBox("${active.rounds.size}/${cfg.rounds}", "ROUNDS DONE", Modifier.weight(1f))
                    StatBox(active.hr.maxOfOrNull { it.second }?.toString() ?: "—", "MAX HR", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(if (st.paused) "RESUME" else "PAUSE", if (st.paused) HelioColors.RecGreen else Color.White, Color.Black, Modifier.weight(1.4f).fillMaxHeight()) {
                        if (st.paused) RoundTimer.resume() else RoundTimer.pause()
                    }
                    Pill(if (st.phase == Phase.Rest) "SKIP REST" else "NEXT", Color(0x1FFFFFFF), Color.White, Modifier.weight(1f).fillMaxHeight()) { RoundTimer.skip() }
                    Pill("STOP", Color(0x1FFFFFFF), HelioColors.RecRed, Modifier.weight(0.8f).fillMaxHeight()) { RoundTimer.closeOpenRound(); RoundTimer.reset() }
                }
            }
        }
    }
}

@Composable
private fun Clock(st: RoundTimer.State, cfg: RoundTimer.Config, left: Long, color: Color, modifier: Modifier, big: Boolean) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val label = when (st.phase) {
            Phase.Idle -> "${cfg.rounds} ROUNDS · ${mmss(cfg.workS * 1000L)} / ${mmss(cfg.restS * 1000L)}"
            Phase.Prep -> "GET READY"
            Phase.Work -> "ROUND ${st.round} OF ${cfg.rounds}"
            Phase.Rest -> "REST · NEXT ROUND ${st.round + 1}"
            Phase.Done -> "DONE · ${st.round} ROUNDS"
        }
        Text(label + if (st.paused) " · PAUSED" else "", style = cap(if (big) 15.sp else 13.sp, color))
        val shown = when (st.phase) { Phase.Idle -> cfg.workS * 1000L; Phase.Done -> 0L; else -> left }
        Text(mmss(shown), style = num(if (big) 128.sp else 92.sp, FontWeight.Bold, if (st.phase == Phase.Idle) Color.White else color))
        // phase progress
        val frac = if (st.running && st.phaseLengthMs > 0) 1f - left.toFloat() / st.phaseLengthMs else if (st.phase == Phase.Done) 1f else 0f
        Box(Modifier.fillMaxWidth(0.85f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x14FFFFFF))) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(color))
        }
        Spacer(Modifier.height(10.dp))
        // round dots
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..cfg.rounds.coerceAtMost(15)).forEach { r ->
                val done = r < st.round || st.phase == Phase.Done || (st.phase == Phase.Rest && r == st.round)
                val now = st.phase == Phase.Work && r == st.round
                Box(Modifier.size(if (now) 12.dp else 9.dp).background(if (done) Color.White else if (now) color else Color(0x26FFFFFF), CircleShape))
            }
        }
    }
}

@Composable
private fun HeartPanel(hr: Int?, zone: Int, active: Workouts.Active, hrMax: Double, modifier: Modifier) {
    Column(modifier.card(18.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(hr?.toString() ?: "—", style = num(64.sp, FontWeight.Bold, zoneColors[zone]))
            Text("  bpm", style = body(14.sp, HelioColors.TextMuted), modifier = Modifier.padding(bottom = 12.dp))
            Spacer(Modifier.weight(1f))
            Text("ZONE $zone", style = cap(13.sp, zoneColors[zone]), modifier = Modifier.padding(bottom = 14.dp))
        }
        Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { z -> Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (z <= zone) zoneColors[z] else Color(0x14FFFFFF))) }
        }
        if (active.rounds.isNotEmpty()) {
            Text("AVG HR PER ROUND", style = cap(10.sp))
            Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                active.rounds.takeLast(12).forEach { (a, b) ->
                    val avg = active.hr.filter { it.first in a..b }.map { it.second }.average().takeIf { !it.isNaN() } ?: 0.0
                    val f = ((avg / hrMax - 0.5) / 0.5).toFloat().coerceIn(0.08f, 1f)
                    val z = WorkoutMath.floors(hrMax).count { avg >= it }
                    Box(Modifier.weight(1f).fillMaxHeight(f).clip(RoundedCornerShape(3.dp)).background(zoneColors[z]))
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, modifier: Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(modifier.card(14.dp).padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = cap(10.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("−", style = num(26.sp, color = HelioColors.TextSoft), modifier = Modifier.clip(CircleShape).clickable(onClick = onMinus).padding(horizontal = 10.dp))
            Text(value, style = num(24.sp, FontWeight.Bold), modifier = Modifier.weight(1f, fill = false))
            Text("+", style = num(26.sp, color = HelioColors.TextSoft), modifier = Modifier.clip(CircleShape).clickable(onClick = onPlus).padding(horizontal = 10.dp))
        }
    }
}

@Composable
private fun Pill(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(54.dp).clip(RoundedCornerShape(27.dp)).background(bg).border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(27.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = cap(14.sp, fg)) }
}

@Composable
private fun StatBox(v: String, label: String, modifier: Modifier, color: Color = Color.White) {
    Column(modifier.card(14.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(v, style = num(24.sp, FontWeight.SemiBold, color))
        Text(label, style = cap(10.sp))
    }
}
