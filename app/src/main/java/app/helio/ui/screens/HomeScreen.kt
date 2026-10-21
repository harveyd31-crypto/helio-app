package app.helio.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.data.DataSource
import app.helio.data.DaySummary
import app.helio.data.LiveState
import app.helio.data.Profile
import app.helio.data.WeekGoal
import app.helio.logic.BodyAge
import app.helio.logic.Vo2Max
import app.helio.logic.WeeklyGoal
import app.helio.ui.components.ActivityRow
import app.helio.ui.components.AgeDetail
import app.helio.ui.components.AgeRow
import app.helio.ui.components.Hero
import app.helio.ui.components.RingsHeader
import app.helio.ui.components.SleepRow
import app.helio.ui.components.StatTile
import app.helio.ui.components.StrainRecoveryChart
import app.helio.ui.components.StressChart
import app.helio.ui.components.StressGauge
import app.helio.ui.components.StressRow
import app.helio.ui.components.Tabs
import app.helio.ui.components.TemperatureRow
import app.helio.ui.components.TopBar
import app.helio.ui.components.Vo2Detail
import app.helio.ui.components.Vo2Row
import app.helio.ui.components.WeeklyGoalCard
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.body
import app.helio.ui.theme.cap
import app.helio.ui.theme.card
import app.helio.ui.theme.num
import java.time.format.DateTimeFormatter

enum class FoldMode { Cover, Open, Tabletop }
enum class Detail { Vo2, Age }

private val popSpring = spring<IntSize>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

/**
 * Overview. Closed (cover screen): the three rings shrink into a sticky row as you scroll, and VO2 max /
 * estimated age open full screen. Open: the same column on the left, the stress monitor and the 7-day
 * chart pop in on the right, and VO2 max / age details open in that right pane.
 */
@Composable
fun HomeScreen(
    days: List<DaySummary>,
    selected: Int,
    onSelect: (Int) -> Unit,
    live: LiveState,
    source: DataSource,
    fold: FoldMode,
    profile: Profile,
    onEditProfile: () -> Unit,
    onOpenLive: () -> Unit,
    liveContent: @Composable () -> Unit,
) {
    val expanded = fold == FoldMode.Open
    val day = days[selected]
    val goal = remember(days, selected) { WeeklyGoal.compute(days, day.date) }
    val vo2 = remember(days, selected, profile) { Vo2Max.compute(days, profile, day.date) }
    val sample = source == DataSource.Sample
    val bodyAge = remember(days, selected, profile, vo2.value) {
        val age = profile.age ?: if (sample) 40 else null
        val male = profile.male ?: if (sample) true else null
        if (age != null && male != null) BodyAge.compute(days, day.date, age, male, vo2.value) else null
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    val tabs = if (expanded) listOf("Overview", "Sleep", "Recovery", "Strain", "Live") else listOf("Overview", "Sleep", "Recovery", "Strain")
    if (!expanded && tab == 4) tab = 0
    BackHandler(enabled = detail != null) { detail = null }

    val detailContent: @Composable (Detail) -> Unit = { d ->
        when (d) {
            Detail.Vo2 -> Vo2Detail(vo2, profileMissing = !profile.complete) { detail = null }
            Detail.Age -> AgeDetail(bodyAge, sample = sample && !profile.complete) { detail = null }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(HelioColors.background)) {
        val sidePad = if (expanded) 22.dp else 14.dp
        val leftTarget = if (expanded) 380.dp else maxWidth - sidePad * 2
        val leftWidth by animateDpAsState(leftTarget, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), label = "left")

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = sidePad, vertical = 8.dp)) {
            TopBar(
                date = day.date,
                canPrev = selected > 0, canNext = selected < days.lastIndex,
                onPrev = { onSelect(selected - 1) }, onNext = { onSelect(selected + 1) },
                link = live.status, battery = null, onProfile = onEditProfile,
            )
            Tabs(tabs, tab, { if (it == 4 && !expanded) onOpenLive() else tab = it }, spread = !expanded)
            if (sample) {
                Text("Sample data · allow Health Connect to see yours", style = body(11.sp, HelioColors.TextDim), modifier = Modifier.padding(top = 6.dp))
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
                if (t == 4) liveContent() else Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(leftWidth).fillMaxHeight().then(swipe)) {
                        val scroll = rememberScrollState()
                        if (t == 0) {
                            val range = with(LocalDensity.current) { 150.dp.toPx() }
                            val collapse = (scroll.value / range).coerceIn(0f, 1f)
                            RingsHeader(day, collapse, big = if (expanded) 100.dp else 96.dp) { tab = it }
                        }
                        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = 6.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            when (t) {
                                0 -> OverviewList(day, goal, expanded, live, vo2, bodyAge, onOpenLive) { detail = it }
                                1 -> SleepTab(day)
                                2 -> RecoveryTab(day)
                                3 -> StrainTab(day, goal)
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandHorizontally(popSpring, expandFrom = Alignment.Start) +
                            slideInHorizontally(tween(520, easing = FastOutSlowInEasing)) { it / 3 } +
                            fadeIn(tween(420, delayMillis = 120)) + scaleIn(tween(520), initialScale = 0.96f),
                        exit = shrinkHorizontally(tween(260)) + fadeOut(tween(180)),
                    ) {
                        AnimatedContent(
                            detail,
                            transitionSpec = { (fadeIn(tween(300)) + slideInHorizontally(tween(380)) { it / 6 }) togetherWith fadeOut(tween(160)) },
                            label = "pane",
                        ) { d ->
                            Column(
                                Modifier.padding(start = 20.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (d != null) detailContent(d) else {
                                    StressCard(day, live)
                                    Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                        Text("STRAIN & RECOVERY", style = cap(11.sp, Color.White))
                                        Spacer(Modifier.height(6.dp))
                                        val from = (selected - 6).coerceAtLeast(0)
                                        StrainRecoveryChart(days.subList(from, selected + 1), selected - from, Modifier.fillMaxWidth().height(220.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Closed phone: details slide up full screen
        AnimatedVisibility(
            visible = !expanded && detail != null,
            enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(280)),
            exit = slideOutVertically(tween(260)) { it / 5 } + fadeOut(tween(200)),
        ) {
            val last = remember { mutableStateOf(Detail.Vo2) }
            detail?.let { last.value = it }
            Box(Modifier.fillMaxSize().background(HelioColors.background)) {
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                ) { detailContent(last.value) }
            }
        }
    }
}

@Composable
private fun OverviewList(
    day: DaySummary, goal: WeekGoal, expanded: Boolean, live: LiveState,
    vo2: Vo2Max.Result, bodyAge: BodyAge.Result?, onOpenLive: () -> Unit, onDetail: (Detail) -> Unit,
) {
    WeeklyGoalCard(goal)
    day.activities.forEach { ActivityRow(it) }
    SleepRow(day)
    AnimatedVisibility(!expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        StressRow(live.stress ?: day.stressNow, onOpenLive)
    }
    TemperatureRow(day.skinTempDelta)
    Vo2Row(vo2) { onDetail(Detail.Vo2) }
    AgeRow(bodyAge) { onDetail(Detail.Age) }
    AnimatedVisibility(
        expanded,
        enter = expandVertically(tween(420, delayMillis = 80)) + fadeIn(tween(380, delayMillis = 160)),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(day.restingHr?.toString() ?: "—", "Resting HR", Modifier.weight(1f))
            StatTile(day.hrv?.let { "$it ms" } ?: "—", "HRV", Modifier.weight(1f))
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
    Text("Sleep stages and the sleep planner come in a later version.", style = body(13.sp, HelioColors.TextMuted))
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
private fun StrainTab(day: DaySummary, goal: WeekGoal) {
    WeeklyGoalCard(goal)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(day.strain?.let { String.format("%.1f", it) } ?: "—", "Day strain", Modifier.weight(1f), HelioColors.Strain)
        StatTile(day.strainTarget?.let { String.format("%.1f", it) } ?: "—", "Target", Modifier.weight(1f))
    }
    day.activities.forEach { ActivityRow(it) }
}
