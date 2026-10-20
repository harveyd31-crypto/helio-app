package app.helio.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.data.DataSource
import app.helio.data.DaySummary
import app.helio.data.LiveState
import app.helio.logic.WeeklyGoal
import app.helio.ui.components.ActivityRow
import app.helio.ui.components.Hero
import app.helio.ui.components.SleepRow
import app.helio.ui.components.StatTile
import app.helio.ui.components.StrainRecoveryChart
import app.helio.ui.components.StressChart
import app.helio.ui.components.StressGauge
import app.helio.ui.components.StressRow
import app.helio.ui.components.Tabs
import app.helio.ui.components.TopBar
import app.helio.ui.components.WeeklyGoalCard
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.body
import app.helio.ui.theme.cap
import app.helio.ui.theme.card
import app.helio.ui.theme.num
import java.time.format.DateTimeFormatter

enum class FoldMode { Cover, Open, Tabletop }

private val popSpring = spring<androidx.compose.ui.unit.IntSize>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

/**
 * Overview. Closed (cover screen) it is one column. Opening the phone keeps the same left column
 * and pops the stress monitor and the 7-day chart in from the right, while the extra vitals row
 * grows in under the activities.
 */
@Composable
fun HomeScreen(
    days: List<DaySummary>,
    selected: Int,
    onSelect: (Int) -> Unit,
    live: LiveState,
    source: DataSource,
    fold: FoldMode,
    onOpenLive: () -> Unit,
    liveContent: @Composable () -> Unit,
) {
    val expanded = fold == FoldMode.Open
    val day = days[selected]
    val goal = remember(days, selected) { WeeklyGoal.compute(days, day.date) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = if (expanded) listOf("Overview", "Sleep", "Recovery", "Strain", "Live") else listOf("Overview", "Sleep", "Recovery", "Strain")
    if (!expanded && tab == 4) tab = 0

    BoxWithConstraints(Modifier.fillMaxSize().background(HelioColors.background).safeDrawingPadding()) {
        val sidePad = if (expanded) 22.dp else 14.dp
        val leftTarget = if (expanded) 380.dp else maxWidth - sidePad * 2
        val leftWidth by animateDpAsState(leftTarget, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), label = "left")

        Column(Modifier.fillMaxSize().padding(horizontal = sidePad, vertical = 8.dp)) {
            TopBar(
                date = day.date,
                canPrev = selected > 0, canNext = selected < days.lastIndex,
                onPrev = { onSelect(selected - 1) }, onNext = { onSelect(selected + 1) },
                link = live.status, battery = null, onProfile = {},
            )
            Tabs(tabs, tab, { if (it == 4 && !expanded) onOpenLive() else tab = it }, spread = !expanded)
            Spacer(Modifier.height(10.dp))

            if (source == DataSource.Sample) {
                Text("Sample data · connect Health Connect in settings to see yours", style = body(11.sp, HelioColors.TextDim), modifier = Modifier.padding(bottom = 6.dp))
            }

            var dragX by remember { mutableFloatStateOf(0f) }
            val swipe = Modifier.pointerInput(selected, days.size) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragX > 120 && selected > 0) onSelect(selected - 1)
                        if (dragX < -120 && selected < days.lastIndex) onSelect(selected + 1)
                        dragX = 0f
                    },
                ) { _, d -> dragX += d }
            }

            AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) }, label = "tab", modifier = Modifier.weight(1f)) { t ->
                when (t) {
                    4 -> liveContent()
                    else -> Row(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.width(leftWidth).fillMaxHeight().verticalScroll(rememberScrollState()).then(swipe),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            when (t) {
                                0 -> OverviewLeft(day, goal, expanded, live, onOpenLive)
                                1 -> SleepTab(day)
                                2 -> RecoveryTab(day)
                                3 -> StrainTab(day, goal)
                            }
                        }
                        AnimatedVisibility(
                            visible = expanded,
                            enter = expandHorizontally(popSpring, expandFrom = Alignment.Start) +
                                slideInHorizontally(tween(520, easing = FastOutSlowInEasing)) { it / 3 } +
                                fadeIn(tween(420, delayMillis = 120)) + scaleIn(tween(520), initialScale = 0.96f),
                            exit = shrinkHorizontally(tween(260)) + fadeOut(tween(180)),
                        ) {
                            Column(Modifier.padding(start = 20.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                StressCard(day, live)
                                Column(Modifier.fillMaxWidth().weight(1f).card().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Text("STRAIN & RECOVERY", style = cap(11.sp, Color.White))
                                    Spacer(Modifier.height(6.dp))
                                    val from = (selected - 6).coerceAtLeast(0)
                                    StrainRecoveryChart(days.subList(from, selected + 1), selected - from, Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewLeft(day: DaySummary, goal: app.helio.data.WeekGoal, expanded: Boolean, live: LiveState, onOpenLive: () -> Unit) {
    Hero(day, ringSize = if (expanded) 172.dp else 168.dp, sideWidth = 88.dp)
    WeeklyGoalCard(goal)
    day.activities.forEach { ActivityRow(it) }
    SleepRow(day)
    AnimatedVisibility(!expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        StressRow(live.stress ?: day.stressNow, onOpenLive)
    }
    AnimatedVisibility(
        expanded,
        enter = expandVertically(tween(420, delayMillis = 80)) + fadeIn(tween(380, delayMillis = 160)),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(day.restingHr?.toString() ?: "—", "Resting HR", Modifier.weight(1f))
            StatTile(day.skinTempDelta?.let { String.format("%+.1f°", it) } ?: "—", "Skin temp", Modifier.weight(1f))
            StatTile(day.respRate?.let { String.format("%.1f", it) } ?: "—", "Resp. rate", Modifier.weight(1f))
        }
    }
}

private val hm = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun StressCard(day: DaySummary, live: LiveState) {
    val value = live.stress ?: day.stressNow
    Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("STRESS MONITOR", style = cap(11.sp, Color.White), modifier = Modifier.weight(1f))
            Text(if (live.stress != null) "Live" else day.stressUpdated?.let { "Updated ${it.format(hm)}" } ?: "", style = body(11.sp, HelioColors.TextMuted))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { StressGauge(value, 190.dp) }
        StressChart(day.stressSeries, day.sleepBand, day.workoutBands, Modifier.fillMaxWidth().height(104.dp))
        Row(Modifier.fillMaxWidth().padding(start = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(day.chartStart, day.chartStart.plusHours(6), day.chartStart.plusHours(12), day.chartEnd).forEach {
                Text(it.format(hm), style = num(11.sp, androidx.compose.ui.text.font.FontWeight.Medium, HelioColors.TextMuted))
            }
        }
    }
}

@Composable
private fun SleepTab(day: DaySummary) {
    SleepRow(day)
    val s = day.sleep
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(day.sleepPerformance?.let { "$it%" } ?: "—", "Performance", Modifier.weight(1f))
        StatTile(s?.let { String.format("%d:%02d", it.minutesAsleep / 60, it.minutesAsleep % 60) } ?: "—", "Asleep", Modifier.weight(1f))
        StatTile("8:00", "Need", Modifier.weight(1f))
    }
    Text("Sleep stages and the sleep planner come in the next version.", style = body(13.sp, HelioColors.TextMuted))
}

@Composable
private fun RecoveryTab(day: DaySummary) {
    Hero(day, 168.dp, 88.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(day.hrv?.let { "$it ms" } ?: "—", "HRV", Modifier.weight(1f))
        StatTile(day.restingHr?.toString() ?: "—", "Resting HR", Modifier.weight(1f))
        StatTile(day.respRate?.let { String.format("%.1f", it) } ?: "—", "Resp. rate", Modifier.weight(1f))
    }
}

@Composable
private fun StrainTab(day: DaySummary, goal: app.helio.data.WeekGoal) {
    WeeklyGoalCard(goal)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(day.strain?.let { String.format("%.1f", it) } ?: "—", "Day strain", Modifier.weight(1f), HelioColors.Strain)
        StatTile(day.strainTarget?.let { String.format("%.1f", it) } ?: "—", "Target", Modifier.weight(1f))
    }
    day.activities.forEach { ActivityRow(it) }
}
