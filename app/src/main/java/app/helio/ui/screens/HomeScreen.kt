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
import androidx.compose.runtime.collectAsState
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
import app.helio.data.toWorkout
import dev.chrisbanes.haze.hazeSource
import app.helio.logic.BodyAge
import app.helio.logic.Vo2Max
import app.helio.logic.WeeklyGoal
import app.helio.ui.components.ActivityFab
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
enum class Detail { Vo2, Age, Sleep, Test, Picker, Recording, Workout }

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
    tests: List<app.helio.data.TestResult>,
    testState: app.helio.ble.TestState,
    onStartTest: () -> Unit,
    onCancelTest: () -> Unit,
    onSimulateTest: () -> Unit,
    workouts: List<app.helio.data.Workout>,
    activeWorkout: app.helio.data.Workouts.Active?,
    onStartWorkout: (app.helio.data.WorkoutType) -> Unit,
    onEndWorkout: () -> app.helio.data.Workout?,
    onDiscardWorkout: () -> Unit,
    onDeleteWorkout: (app.helio.data.Workout) -> Unit,
    onOpenLive: () -> Unit,
    onOpenRounds: () -> Unit = {},
    liveContent: @Composable () -> Unit,
) {
    val expanded = fold == FoldMode.Open
    val day = days[selected]
    val goal = remember(days, selected) { WeeklyGoal.compute(days, day.date) }
    val vo2 = remember(days, selected, profile, tests) { Vo2Max.compute(days, profile, day.date, tests) }
    val sample = source == DataSource.Sample
    val bodyAge = remember(days, selected, profile, vo2.value) {
        val age = profile.age ?: if (sample) 40 else null
        val male = profile.male ?: if (sample) true else null
        if (age != null && male != null) BodyAge.compute(days, day.date, age, male, vo2.value) else null
    }
    val habitsAge = remember(days, selected, profile, vo2.value, workouts) {
        val upTo = days.subList(0, selected + 1)
        val since = day.date.minusDays(28)
        val strength = upTo.filter { it.date > since }.flatMap { it.activities }
            .filter { it.name.contains("cross", true) || it.name.contains("strength", true) }.sumOf { (it.endMs - it.startMs) / 60_000.0 } +
            workouts.filter { it.type == app.helio.data.WorkoutType.CrossFit && java.time.Instant.ofEpochMilli(it.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate() > since }
                .sumOf { (it.end - it.start) / 60_000.0 }
        app.helio.logic.Insights.habitsAge(
            upTo.takeLast(180), profile.age ?: if (sample) 40 else null, profile.male ?: if (sample) true else null,
            strength / 4.0, vo2.value,
        )
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    val tabs = if (expanded) listOf("Overview", "Sleep", "Recovery", "Strain", "Live") else listOf("Overview", "Sleep", "Recovery", "Strain")
    if (!expanded && tab == 4) tab = 0
    if (testState.running && detail != Detail.Test) detail = Detail.Test
    if (activeWorkout != null && detail != Detail.Recording) detail = Detail.Recording
    var openWorkout by remember { mutableStateOf<app.helio.data.Workout?>(null) }
    val kinds by app.helio.data.Workouts.tags.collectAsState()
    val finished by app.helio.data.Workouts.finished.collectAsState()
    androidx.compose.runtime.LaunchedEffect(finished) {
        finished?.let { openWorkout = it; detail = Detail.Workout; app.helio.data.Workouts.consumeFinished() }
    }
    val allWorkouts = remember(days, workouts) {
        workouts + days.flatMap { d -> d.activities.filter { it.hr.size > 10 }.map { a -> a.toWorkout() } }
    }
    val rest = (day.restingHr ?: 60).toDouble()
    fun statsOf(w: app.helio.data.Workout) = app.helio.data.WorkoutMath.stats(w, vo2.hrMax, rest, profile)
    val zone = java.time.ZoneId.systemDefault()
    val dayWorkouts = workouts.filter { java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == day.date }
        .map { it to statsOf(it) }
    BackHandler(enabled = detail != null && !testState.running) { detail = null }

    val detailContent: @Composable (Detail) -> Unit = { d ->
        when (d) {
            Detail.Vo2 -> Vo2Detail(vo2, profileMissing = !profile.complete, onOpenTest = { detail = Detail.Test }) { detail = null }
            Detail.Age -> AgeDetail(bodyAge, sample = sample && !profile.complete, habits = habitsAge) { detail = null }
            Detail.Sleep -> app.helio.ui.components.SleepDetail(days, selected) { detail = null }
            Detail.Picker -> app.helio.ui.components.ActivityPicker(live.status, onPick = { onStartWorkout(it); detail = Detail.Recording }) { detail = null }
            Detail.Recording -> activeWorkout?.let { a ->
                app.helio.ui.components.RecordingView(
                    a, live.hr, live.status, vo2.hrMax, rest, profile,
                    onEnd = { val w = onEndWorkout(); openWorkout = w; detail = if (w != null) Detail.Workout else null },
                    onDiscard = { onDiscardWorkout(); detail = null },
                    onRounds = onOpenRounds,
                )
            } ?: Text("Activity finished.", style = body(14.sp))
            Detail.Workout -> openWorkout?.let { w ->
                val history = allWorkouts.filter { it.type == w.type && it.start < w.start && it.start > w.start - 30L * 86_400_000 }.map { statsOf(it) }
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val kind = app.helio.data.Workouts.kindOf(w, kinds)
                app.helio.ui.components.WorkoutDetail(
                    w, statsOf(w), history, onDelete = if (w in workouts) ({ onDeleteWorkout(w); detail = null }) else null, hrMax = vo2.hrMax,
                    kind = kind, onKind = { app.helio.data.Workouts.setKind(ctx, w, it) },
                    progress = if (app.helio.data.SessionKind.forSport(w.type).isNotEmpty()) ({
                        app.helio.ui.components.SportProgressCard(w.type, allWorkouts, { app.helio.data.Workouts.kindOf(it, kinds) }, vo2.hrMax, kind)
                    }) else null,
                ) { detail = null }
            } ?: Text("No activity selected.", style = body(14.sp))
            Detail.Test -> app.helio.ui.components.TestScreen(
                state = testState, link = live.status, hr = live.hr, last = tests.lastOrNull(), canSimulate = sample,
                onStart = onStartTest, onCancel = onCancelTest, onSimulate = onSimulateTest,
            ) { detail = Detail.Vo2 }
        }
    }

    val hazeState = remember { dev.chrisbanes.haze.HazeState() }
    BoxWithConstraints(Modifier.fillMaxSize().background(HelioColors.background)) {
        val sidePad = if (expanded) 22.dp else 14.dp
        val leftTarget = if (expanded) 380.dp else maxWidth - sidePad * 2
        val leftWidth by animateDpAsState(leftTarget, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), label = "left")

        Column(Modifier.fillMaxSize().hazeSource(hazeState).safeDrawingPadding().padding(horizontal = sidePad, vertical = 8.dp)) {
            TopBar(
                date = day.date,
                canPrev = selected > 0, canNext = selected < days.lastIndex,
                onPrev = { onSelect(selected - 1) }, onNext = { onSelect(selected + 1) },
                link = live.status, battery = null, onProfile = onEditProfile,
            )
            Tabs(tabs, tab, { if (it == 4 && !expanded) onOpenLive() else tab = it }, spread = !expanded)
            val strap by app.helio.strap.StrapManager.status.collectAsState()
            when (source) {
                DataSource.Sample -> app.helio.ui.components.SourceLine("Sample data · pair the Helio in Profile, or allow Health Connect")
                DataSource.HealthConnect -> app.helio.ui.components.SourceLine("From Health Connect (Zepp)")
                DataSource.Strap -> app.helio.ui.components.SourceLine(
                    strap.error ?: strap.progress?.let { "Syncing with Helio · $it" }
                        ?: ("Synced from Helio" + (strap.lastSync?.let { " · " + java.time.LocalTime.ofInstant(it, java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) } ?: "") +
                            (strap.battery?.let { " · strap $it%" } ?: "")),
                    if (strap.error != null) HelioColors.RecYellow else HelioColors.TextDim,
                )
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
                        when (t) {
                            0, 2 -> app.helio.ui.components.WeekStrip(days, selected, onSelect, { it.recovery?.let { r -> r / 100f } }, { HelioColors.recovery(it.recovery) })
                            1 -> app.helio.ui.components.WeekStrip(days, selected, onSelect,
                                { d -> (d.extras?.deviceSleepScore ?: d.sleepPerformance)?.let { it / 100f } }, { app.helio.ui.components.SleepPurple })
                            3 -> app.helio.ui.components.WeekStrip(days, selected, onSelect, { d -> d.strain?.let { (it / 21).toFloat() } }, { HelioColors.Strain })
                        }
                        if (t == 0) {
                            val range = with(LocalDensity.current) { 150.dp.toPx() }
                            val collapse = (scroll.value / range).coerceIn(0f, 1f)
                            RingsHeader(day, collapse, big = if (expanded) 100.dp else 96.dp) { tab = it }
                        }
                        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = 6.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            when (t) {
                                0 -> {
                                    app.helio.ui.components.DaySummaryCard(days, selected) { tab = 2 }
                                    app.helio.ui.components.LastNightTiles(days, selected)
                                    OverviewList(day, goal, expanded, live, vo2, bodyAge, onOpenLive, dayWorkouts,
                                        onOpenWorkout = { openWorkout = it; detail = Detail.Workout }) { detail = it }
                                }
                                1 -> {
                                    app.helio.ui.components.SleepOverview(day)
                                    app.helio.ui.components.SleepDetail(days, selected, null)
                                }
                                2 -> {
                                    RecoveryTab(day)
                                    app.helio.ui.components.RecoveryInsight(days, selected)
                                    app.helio.ui.components.LastNightTiles(days, selected)
                                }
                                3 -> {
                                    StrainDetailTab(days, selected, goal, dayWorkouts, vo2.hrMax) { openWorkout = it; detail = Detail.Workout }
                                    listOf(app.helio.data.WorkoutType.MuayThai, app.helio.data.WorkoutType.Tennis).forEach { sport ->
                                        if (allWorkouts.any { it.type == sport }) {
                                            app.helio.ui.components.SportProgressCard(sport, allWorkouts, { app.helio.data.Workouts.kindOf(it, kinds) }, vo2.hrMax)
                                        }
                                    }
                                }
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

        // Floating Start Activity, bottom right, on the overview
        if (detail == null && tab == 0 && activeWorkout == null && !testState.running) {
            ActivityFab(hazeState) { t -> onStartWorkout(t); detail = Detail.Recording }
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
    vo2: Vo2Max.Result, bodyAge: BodyAge.Result?, onOpenLive: () -> Unit,
    dayWorkouts: List<Pair<app.helio.data.Workout, app.helio.data.WorkoutStats>>,
    onOpenWorkout: (app.helio.data.Workout) -> Unit,
    onDetail: (Detail) -> Unit,
) {
    app.helio.ui.components.IllnessBanner(day.extras)
    WeeklyGoalCard(goal)
    app.helio.ui.components.DailyTiles(day.extras)
    day.activities.forEach { a -> ActivityRow(a) { if (a.hr.size > 10) onOpenWorkout(a.toWorkout()) } }
    val zone = java.time.ZoneId.systemDefault()
    dayWorkouts.forEach { (w, st) ->
        ActivityRow(
            app.helio.data.Activity(
                w.type.label, (st.strain * 10).toInt() / 10.0,
                java.time.Instant.ofEpochMilli(w.start).atZone(zone).toLocalTime(),
                java.time.Instant.ofEpochMilli(w.end).atZone(zone).toLocalTime(),
            ),
        ) { onOpenWorkout(w) }
    }
    SleepRow(day) { onDetail(Detail.Sleep) }
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
    app.helio.ui.components.IllnessBanner(day.extras)
    app.helio.ui.components.RecoveryParts(day.extras)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(day.hrv?.let { "$it ms" } ?: "—", "HRV", Modifier.weight(1f))
        StatTile(day.restingHr?.toString() ?: "—", "Resting HR", Modifier.weight(1f))
        StatTile(day.respRate?.let { String.format("%.1f", it) } ?: "—", "Resp. rate", Modifier.weight(1f))
    }
}

@Composable
private fun StrainDetailTab(
    days: List<DaySummary>, selected: Int, goal: WeekGoal,
    dayWorkouts: List<Pair<app.helio.data.Workout, app.helio.data.WorkoutStats>>, hrMax: Double,
    onOpen: (app.helio.data.Workout) -> Unit,
) {
    val day = days[selected]
    val strain = day.strain ?: 0.0
    val target = day.strainTarget
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DAY STRAIN", style = cap(11.sp, Color.White))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(String.format("%.1f", strain), style = num(48.sp, androidx.compose.ui.text.font.FontWeight.Bold, HelioColors.Strain))
            Text(target?.let { String.format("  target %.1f", it) } ?: "", style = body(14.sp, HelioColors.TextMuted), modifier = Modifier.padding(bottom = 9.dp))
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val h = size.height
            drawRoundRect(HelioColors.Track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
            drawRoundRect(
                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(HelioColors.StrainDeep, HelioColors.StrainLight)),
                size = androidx.compose.ui.geometry.Size((strain / 21.0).toFloat().coerceIn(0f, 1f) * size.width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2),
            )
            target?.let { val x = (it / 21.0).toFloat() * size.width; drawLine(Color.White, androidx.compose.ui.geometry.Offset(x, -4f), androidx.compose.ui.geometry.Offset(x, h + 4f), 3f) }
        }
        day.extras?.cardioLoad?.let { load ->
            Text(String.format("Cardio load %.0f", load) + (day.extras.typicalLoad?.let { String.format(" · your typical %.0f", it) } ?: "") +
                ". Strain is placed against your own heavy days (90-day high), so 21 means hard for you.", style = body(12.sp, HelioColors.TextMuted))
        }
        day.extras?.strainWithheld?.let { Text(it, style = body(12.sp, HelioColors.RecYellow)) }
        Text(
            when {
                target == null -> "Strain is 0–21, from time spent at raised heart rate."
                strain < target - 2 -> String.format("%.1f below today's target: room for a session.", target - strain)
                strain > target + 2 -> String.format("%.1f above today's target: prioritise recovery tonight.", strain - target)
                else -> "On target for today's recovery."
            },
            style = body(12.sp, HelioColors.TextMuted),
        )
    }

    Text("ACTIVITIES", style = cap(11.sp, Color.White))
    val items = day.activities.map { it to (if (it.hr.size > 10) it.toWorkout() else null) } +
        dayWorkouts.map { (w, st) ->
            val z = java.time.ZoneId.systemDefault()
            app.helio.data.Activity(w.type.label, (st.strain * 10).toInt() / 10.0,
                java.time.Instant.ofEpochMilli(w.start).atZone(z).toLocalTime(), java.time.Instant.ofEpochMilli(w.end).atZone(z).toLocalTime()) to w
        }
    if (items.isEmpty()) Text("No activities this day.", style = body(13.sp, HelioColors.TextMuted))
    items.forEach { (a, w) -> ActivityRow(a) { w?.let(onOpen) } }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(day.moderateMin?.let { "$it min" } ?: "—", "Moderate 60–80%", Modifier.weight(1f))
        StatTile(day.vigorousMin?.let { "$it min" } ?: "—", "Vigorous 80%+", Modifier.weight(1f))
        StatTile(day.maxHr?.toString() ?: "—", "Max HR", Modifier.weight(1f))
    }

    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("LAST 7 DAYS · STRAIN VS TARGET", style = cap(11.sp, Color.White))
        val from = (selected - 6).coerceAtLeast(0)
        val week = days.subList(from, selected + 1)
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            week.forEachIndexed { i, d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.strain?.let { String.format("%.1f", it) } ?: "", style = num(11.sp, androidx.compose.ui.text.font.FontWeight.SemiBold, HelioColors.Strain))
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(96.dp)) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            val hBar = ((d.strain ?: 0.0) / 21.0).toFloat() * size.height
                            drawRoundRect(if (i == week.lastIndex) HelioColors.StrainLight else HelioColors.StrainBadge,
                                androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height - hBar), androidx.compose.ui.geometry.Size(size.width * 0.6f, hBar),
                                androidx.compose.ui.geometry.CornerRadius(5f))
                            d.strainTarget?.let { t -> val y = size.height - (t / 21.0).toFloat() * size.height; drawLine(Color.White, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 2.5f) }
                        }
                    }
                    Text(d.date.dayOfWeek.name.take(1), style = cap(10.sp, if (i == week.lastIndex) Color.White else HelioColors.TextMuted))
                }
            }
        }
        Text("Bars: day strain · white line: that day's target", style = body(11.sp, HelioColors.TextMuted))
    }
    WeeklyGoalCard(goal)
}
