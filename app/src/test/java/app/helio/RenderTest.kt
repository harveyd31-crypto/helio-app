package app.helio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.helio.data.DaySummary
import app.helio.data.LinkStatus
import app.helio.data.LiveState
import app.helio.data.Profile
import app.helio.data.SampleData
import app.helio.data.SessionKind
import app.helio.data.WorkoutMath
import app.helio.data.WorkoutType
import app.helio.data.Workouts
import app.helio.data.toWorkout
import app.helio.logic.BodyAge
import app.helio.logic.Insights
import app.helio.logic.Vo2Max
import app.helio.ui.components.AgeDetail
import app.helio.ui.components.SportProgressCard
import app.helio.ui.components.WorkoutDetail
import app.helio.ui.screens.FoldMode
import app.helio.ui.screens.HomeScreen
import app.helio.ui.screens.RoundsScreen
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.HelioTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the real screens with the sample data, at Fold 8 sizes, into app/build/renders. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderTest {
    @get:Rule val rule = createComposeRule()

    private val days: List<DaySummary> = SampleData.days()
    private val profile = Profile(birthYear = 1991, male = true, heightCm = 180, weightKg = 78.0)
    private val hrMax = 190.0

    private fun shot(name: String, tab: String? = null, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { HelioTheme { content() } }
        rule.mainClock.advanceTimeBy(2500)
        if (tab != null) {
            rule.onAllNodesWithText(tab, useUnmergedTree = true).onFirst().performClick()
            rule.mainClock.advanceTimeBy(2500)
        }
        rule.onRoot().captureRoboImage("build/renders/$name.png")
    }

    @Composable
    private fun Home(fold: FoldMode) {
        HomeScreen(
            days = days, selected = days.lastIndex, onSelect = {}, live = LiveState(status = LinkStatus.Live, hr = 64, stress = 1.1),
            source = app.helio.data.DataSource.Sample, fold = fold, profile = profile, onEditProfile = {},
            tests = emptyList(), testState = app.helio.ble.TestState(), onStartTest = {}, onCancelTest = {}, onSimulateTest = {},
            workouts = emptyList(), activeWorkout = null, onStartWorkout = {}, onEndWorkout = { null }, onDiscardWorkout = {},
            onDeleteWorkout = {}, onOpenLive = {}, liveContent = {},
        )
    }

    @Config(qualifiers = "w400dp-h2300dp-xhdpi") @Test fun cover1_overview() = shot("cover1_overview") { Home(FoldMode.Cover) }
    @Config(qualifiers = "w400dp-h2300dp-xhdpi") @Test fun cover2_sleep() = shot("cover2_sleep", "SLEEP") { Home(FoldMode.Cover) }
    @Config(qualifiers = "w400dp-h2000dp-xhdpi") @Test fun cover3_recovery() = shot("cover3_recovery", "RECOVERY") { Home(FoldMode.Cover) }
    @Config(qualifiers = "w400dp-h2300dp-xhdpi") @Test fun cover4_strain() = shot("cover4_strain", "STRAIN") { Home(FoldMode.Cover) }
    @Config(qualifiers = "w880dp-h1700dp-xhdpi") @Test fun open1_overview() = shot("open1_overview") { Home(FoldMode.Open) }
    @Config(qualifiers = "w880dp-h1700dp-xhdpi") @Test fun open2_sleep() = shot("open2_sleep", "SLEEP") { Home(FoldMode.Open) }

    private fun detail(name: String, content: @Composable () -> Unit) = shot(name) {
        Box(Modifier.fillMaxSize().background(HelioColors.background)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) { content() }
        }
    }

    @Config(qualifiers = "w400dp-h2300dp-xhdpi") @Test fun detail1_age() = detail("detail1_age") {
        val d = days.last()
        val vo2 = Vo2Max.compute(days, profile, d.date)
        val habits = Insights.habitsAge(days.takeLast(180), 35, true, 60.0, vo2.value)
        AgeDetail(BodyAge.compute(days, d.date, 35, true, vo2.value), sample = false, habits = habits) {}
    }

    private fun session(type: WorkoutType) = days.flatMap { it.activities }.last { a -> a.toWorkout().type == type }.toWorkout()

    @Config(qualifiers = "w400dp-h3000dp-xhdpi") @Test fun detail2_muaythai() = detail("detail2_muaythai") {
        val w = session(WorkoutType.MuayThai)
        val all = days.flatMap { it.activities }.map { it.toWorkout() }
        WorkoutDetail(w, WorkoutMath.stats(w, hrMax, 52.0, profile), emptyList(), null, hrMax, SessionKind.Sparring, {},
            progress = { SportProgressCard(WorkoutType.MuayThai, all, { Workouts.kindOf(it) }, hrMax, SessionKind.Sparring) }) {}
    }

    @Config(qualifiers = "w400dp-h3000dp-xhdpi") @Test fun detail3_tennis() = detail("detail3_tennis") {
        val w = session(WorkoutType.Tennis)
        val all = days.flatMap { it.activities }.map { it.toWorkout() }
        WorkoutDetail(w, WorkoutMath.stats(w, hrMax, 52.0, profile), emptyList(), null, hrMax, SessionKind.Match, {},
            progress = { SportProgressCard(WorkoutType.Tennis, all, { Workouts.kindOf(it) }, hrMax) }) {}
    }

    @Config(qualifiers = "w880dp-h840dp-xhdpi") @Test fun fold1_roundtimer() = shot("fold1_roundtimer") {
        val w = session(WorkoutType.MuayThai)
        RoundsScreen(
            live = LiveState(status = LinkStatus.Live, hr = 168),
            active = Workouts.Active(WorkoutType.MuayThai, w.start, w.hr.take(140), w.rounds.take(2)),
            hrMax = hrMax, topHeight = 410.dp, onClose = {}, onEnd = {},
        )
    }
}
