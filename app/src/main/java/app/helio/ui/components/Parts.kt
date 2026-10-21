package app.helio.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.data.Activity
import app.helio.data.DaySummary
import app.helio.data.LinkStatus
import app.helio.data.WeekGoal
import app.helio.data.toWorkout
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.cap
import app.helio.ui.theme.card
import app.helio.ui.theme.num
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

fun dayLabel(d: LocalDate): String = if (d == LocalDate.now()) "TODAY" else d.format(dayFmt).uppercase()

@Composable
fun TopBar(
    date: LocalDate,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    link: LinkStatus,
    battery: Int?,
    onProfile: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).border(1.5.dp, Color.White, CircleShape).clickable(onClick = onProfile),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.height(36.dp).background(Color(0x14FFFFFF), RoundedCornerShape(50)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, enabled = canPrev, modifier = Modifier.size(40.dp, 36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day", tint = if (canPrev) Color.White else HelioColors.TextDim)
            }
            AnimatedContent(date, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "date") { d ->
                Text(dayLabel(d), style = cap(13.sp, Color.White), textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 110.dp))
            }
            IconButton(onClick = onNext, enabled = canNext, modifier = Modifier.size(40.dp, 36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day", tint = if (canNext) Color.White else HelioColors.TextDim)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val live = link == LinkStatus.Live
            Text(battery?.let { "$it%" } ?: "", style = num(13.sp, FontWeight.SemiBold, if (live) HelioColors.RecGreen else HelioColors.TextMuted))
            StrapGlyph(live)
        }
    }
}

@Composable
private fun StrapGlyph(connected: Boolean) {
    Canvas(Modifier.size(16.dp, 24.dp)) {
        val u = size.width / 20f
        drawRoundRect(Color.White, Offset(4 * u, 3 * u), Size(11 * u, 22 * u), androidx.compose.ui.geometry.CornerRadius(4 * u), style = Stroke(1.8f * u))
        drawCircle(if (connected) HelioColors.RecGreen else HelioColors.TextDim, 3 * u, Offset(16 * u, 5 * u))
    }
}

@Composable
fun Tabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit, spread: Boolean) {
    Row(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(HelioColors.Divider, Offset(0f, size.height), Offset(size.width, size.height), 1f)
        }.padding(horizontal = 4.dp),
        horizontalArrangement = if (spread) Arrangement.SpaceBetween else Arrangement.spacedBy(36.dp),
    ) {
        items.forEachIndexed { i, t ->
            val on = i == selected
            Column(Modifier.clickable { onSelect(i) }.padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t.uppercase(), style = cap(12.sp, if (on) Color.White else HelioColors.TextMuted))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.height(2.dp).width(if (on) 64.dp else 0.dp).background(Color.White))
            }
        }
    }
}

@Composable
fun Hero(day: DaySummary, ringSize: Dp, sideWidth: Dp) {
    val rc = HelioColors.recovery(day.recovery)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Column(Modifier.width(sideWidth), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric(day.recovery?.let { "$it" } ?: "—", "%", "RECOVERY", rc, rc, big = true, alignEnd = true)
            Metric(day.hrv?.toString() ?: "—", "", "HRV", HelioColors.TextSoft, HelioColors.TextMuted, big = false, alignEnd = true)
        }
        DualRing(day.strain, day.strainTarget, day.recovery, ringSize)
        Column(Modifier.width(sideWidth), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric(day.strain?.let { String.format("%.1f", it) } ?: "—", "", "STRAIN", HelioColors.Strain, HelioColors.Strain, big = true, alignEnd = false)
            Metric(day.sleepPerformance?.toString() ?: "—", "%", "SLEEP", HelioColors.TextSoft, HelioColors.TextMuted, big = false, alignEnd = false)
        }
    }
}

@Composable
private fun Metric(value: String, unit: String, label: String, valueColor: Color, labelColor: Color, big: Boolean, alignEnd: Boolean) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = num(if (big) 27.sp else 22.sp, if (big) FontWeight.SemiBold else FontWeight.Medium, valueColor))
            if (unit.isNotEmpty()) Text(unit, style = num(if (big) 15.sp else 13.sp, FontWeight.SemiBold, valueColor), modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(label, style = cap(10.sp, labelColor))
    }
}

@Composable
fun WeeklyGoalCard(goal: WeekGoal, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().card().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        WeekRing(goal.cumulative, goal.goal, goal.scale, goal.pace, 98.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("WEEKLY STRAIN GOAL", style = cap(11.sp, Color.White), modifier = Modifier.weight(1f))
                Text("DAY ${goal.dayIndex + 1}/7", style = cap(10.sp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val ahead = goal.vsPace
                SmallStat((if (ahead >= 0) "+" else "") + String.format("%.1f", ahead), "vs pace", if (ahead >= 0) HelioColors.RecGreen else HelioColors.RecYellow)
                SmallStat(String.format("%.1f", goal.left), "left", Color.White)
                SmallStat(String.format("%.1f", goal.perDayLeft), "per day", HelioColors.Strain)
            }
            WeekBars(goal)
        }
    }
}

@Composable
private fun SmallStat(value: String, label: String, color: Color) {
    Column {
        Text(value, style = num(17.sp, FontWeight.SemiBold, color))
        Text(label, style = app.helio.ui.theme.body(11.sp, HelioColors.TextMuted))
    }
}

@Composable
private fun WeekBars(goal: WeekGoal) {
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        letters.forEachIndexed { k, l ->
            val v = goal.daily[k]
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val h = (((v ?: goal.perDayLeft) / 21.0) * 26 + 3).dp
                if (v != null) {
                    Box(Modifier.fillMaxWidth().height(h).background(if (k == goal.dayIndex) HelioColors.StrainLight else HelioColors.StrainBadge, RoundedCornerShape(4.dp)))
                } else {
                    Canvas(Modifier.fillMaxWidth().height(h)) {
                        drawRoundRect(
                            Color(0xFF46525C), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(l, style = cap(10.sp, when { k == goal.dayIndex -> Color.White; v == null -> HelioColors.TextDim; else -> HelioColors.TextMuted }))
            }
        }
    }
}

@Composable
fun ActivityRow(a: Activity, onClick: () -> Unit = {}) = BadgeRow(
    badgeColor = HelioColors.StrainBadge, glyph = { SportGlyph(a.toWorkout().type) }, badgeText = String.format("%.1f", a.strain),
    title = a.name, top = a.end.format(timeFmt), bottom = a.start.format(timeFmt), edge = HelioColors.StrainBadge, onClick = onClick,
)

@Composable
fun SleepRow(day: DaySummary, onClick: () -> Unit = {}) {
    val s = day.sleep ?: return
    BadgeRow(
        badgeColor = HelioColors.SleepBadge, glyph = { MoonGlyph() },
        badgeText = String.format("%d:%02d", s.minutesAsleep / 60, s.minutesAsleep % 60),
        title = "Sleep", top = s.wake.format(timeFmt), bottom = "${s.bedDayLabel} ${s.bed.format(timeFmt)}", edge = Color(0xFFC9D3DD), onClick = onClick,
    )
}

@Composable
private fun BadgeRow(
    badgeColor: Color, glyph: @Composable () -> Unit, badgeText: String, title: String,
    top: String, bottom: String, edge: Color, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().card(14.dp).clickable(onClick = onClick).padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.size(96.dp, 40.dp).background(badgeColor, RoundedCornerShape(9.dp)),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            glyph()
            Spacer(Modifier.width(8.dp))
            Text(badgeText, style = num(18.sp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title.uppercase(), style = cap(13.sp, Color.White), modifier = Modifier.weight(1f))
        Column(
            Modifier.drawBehind { drawLine(edge, Offset(size.width, 0f), Offset(size.width, size.height), 2.dp.toPx()) }.padding(end = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(top, style = num(12.sp, FontWeight.Medium, HelioColors.TextMuted))
            Text(bottom, style = num(12.sp, FontWeight.Medium, HelioColors.TextMuted))
        }
    }
}

@Composable
fun StressRow(value: Double?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().card(14.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(52.dp, 30.dp)) {
            val r = size.width * 0.42f
            val c = Offset(size.width / 2, size.height * 0.95f)
            drawArc(
                androidx.compose.ui.graphics.Brush.horizontalGradient(*HelioColors.stressStops),
                180f, 180f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(size.height * 0.15f, cap = StrokeCap.Round),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text("STRESS MONITOR", style = cap(13.sp, Color.White), modifier = Modifier.weight(1f))
        Text(value?.let { String.format("%.1f", it) } ?: "—", style = num(20.sp))
        Spacer(Modifier.width(8.dp))
        Text(app.helio.logic.Stress.label(value), style = cap(10.sp, HelioColors.stress(value)))
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = Color.White) {
    Column(modifier.card(14.dp).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(value, style = num(19.sp, FontWeight.SemiBold, valueColor))
        Text(label.uppercase(), style = cap(10.sp))
    }
}

@Composable
fun RunGlyph(color: Color = Color.White) {
    Canvas(Modifier.size(18.dp)) {
        val u = size.width / 24f
        val st = Stroke(2 * u, cap = StrokeCap.Round)
        drawCircle(color, 2 * u, Offset(14 * u, 4 * u), style = st)
        val p = Path().apply {
            moveTo(8 * u, 21 * u); lineTo(11 * u, 15 * u); lineTo(14 * u, 18 * u); lineTo(14 * u, 21 * u)
            moveTo(6 * u, 12 * u); lineTo(9 * u, 8 * u); lineTo(13 * u, 9 * u); lineTo(16 * u, 13 * u); lineTo(19 * u, 13 * u)
        }
        drawPath(p, color, style = st)
    }
}

@Composable
fun MoonGlyph(color: Color = Color.White) {
    Canvas(Modifier.size(16.dp)) {
        drawCircle(color, size.width * 0.42f, center)
        drawCircle(HelioColors.SleepBadge, size.width * 0.36f, Offset(center.x + size.width * 0.2f, center.y - size.height * 0.16f))
    }
}
