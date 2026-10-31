package app.helio

import app.helio.data.Activity
import app.helio.data.DaySummary
import app.helio.data.Profile
import app.helio.data.SampleData
import app.helio.data.Sessions
import app.helio.data.Workout
import app.helio.data.WorkoutType
import app.helio.logic.Strain
import app.helio.logic.WeeklyGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId

/** Rules that must hold however the data arrives: one session counted once, one strain per session, sane scales. */
class AuditTest {
    private val zone = ZoneId.systemDefault()
    private val profile = Profile(birthYear = 1991, male = true, heightCm = 180, weightKg = 78.0)

    /** A heart-rate trace every 5 s: (minutes, bpm) blocks from [t0]. */
    private fun trace(t0: Long, vararg blocks: Pair<Double, Int>): List<Pair<Long, Int>> {
        var t = t0
        val out = mutableListOf<Pair<Long, Int>>()
        for ((min, bpm) in blocks) {
            val end = t + (min * 60_000).toLong()
            while (t < end) { out += t to bpm; t += 5000 }
        }
        return out
    }

    /** David's 31 Oct session, by zone: ~12.5 min zone 2, ~41.5 zone 3, ~26.7 zone 4, ~14.6 zone 5, plus warm-up. */
    private fun muayThai(t0: Long) = trace(t0, 7.5 to 100, 12.6 to 118, 41.5 to 137, 26.7 to 155, 14.6 to 172)

    private fun days(): List<DaySummary> = SampleData.days(count = 40).map { it.copy(activities = emptyList()) }

    @Test fun strapAndHelioRecordingOfOneSessionCountOnce() {
        val ds = days()
        val today = ds.last()
        val t0 = today.date.atTime(13, 35).atZone(zone).toInstant().toEpochMilli()
        val hr = muayThai(t0)
        val mine = Workout(t0, t0 + 103 * 60_000L, WorkoutType.MuayThai, hr)
        val strapCopy = Activity("Workout", 15.3, LocalTime.of(13, 36), LocalTime.of(15, 20), t0 + 60_000, t0 + 105 * 60_000L, hr)
        val withBoth = ds.dropLast(1) + today.copy(activities = listOf(strapCopy))
        val (norm, ctx) = Sessions.normalize(withBoth, listOf(mine), profile)
        assertTrue("the strap's copy is dropped", norm.last().activities.isEmpty())
        val g = WeeklyGoal.compute(norm, today.date, Sessions.strainByDay(listOf(mine), ctx))
        val once = Strain.fromHeartRate(hr, ctx.restOn(today.date), ctx.hrMax)
        assertEquals("the week holds the session once", once, g.daily.filterNotNull().sum(), 0.01)
    }

    @Test fun separateSessionsAreKept() {
        val ds = days()
        val today = ds.last()
        val t0 = today.date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val morning = Activity("Run", 8.0, LocalTime.of(9, 0), LocalTime.of(9, 40), t0, t0 + 40 * 60_000L, trace(t0, 40.0 to 150))
        val evening = Workout(t0 + 9 * 3_600_000L, t0 + 10 * 3_600_000L, WorkoutType.MuayThai, muayThai(t0 + 9 * 3_600_000L))
        val (norm, _) = Sessions.normalize(ds.dropLast(1) + today.copy(activities = listOf(morning)), listOf(evening), profile)
        assertEquals(1, norm.last().activities.size)
    }

    @Test fun moderateHardMuayThaiScoresAbout13To14() {
        val hr = muayThai(0)
        val s183 = Strain.fromHeartRate(hr, 55.0, 183.0)
        val s190 = Strain.fromHeartRate(hr, 55.0, 190.0)
        assertTrue("age-formula max: $s183", s183 in 13.0..15.0)
        assertTrue("observed max: $s190", s190 in 12.0..14.0)
        val easy = Strain.fromHeartRate(trace(0, 45.0 to 115), 55.0, 190.0)
        assertTrue("easy 45 min: $easy", easy in 2.0..6.0)
    }

    @Test fun oneSessionIsAboutAThirdOfTheWeekWithoutHistory() {
        val ds = days()
        val g = WeeklyGoal.compute(ds, ds.last().date, mapOf(ds.last().date to 13.6))
        assertTrue("goal ${g.goal}", g.goal >= 40.0)
        assertTrue("share ${g.cumulative / g.goal}", g.cumulative / g.goal < 0.4)
    }

    @Test fun aDayAtHomeIsLowStrain() {
        val home = trace(0, 16 * 60.0 to 75)
        val homeWalk = trace(0, 15 * 60.0 to 75, 60.0 to 108)
        val h = Strain.day(home, 54.0, 185.0)
        val w = Strain.day(homeWalk, 54.0, 185.0)
        assertTrue("home day $h", h < 3.0)
        assertTrue("with a walk $w", w in 2.0..9.0)
    }

    @Test fun sessionPeaksRaiseTheMaxHeartRate() {
        val ds = days()
        val mine = listOf(1, 4).map { back ->
            val d = ds[ds.size - back]
            val t0 = d.date.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
            Workout(t0, t0 + 3_600_000L, WorkoutType.MuayThai, trace(t0, 50.0 to 150, 10.0 to 196))
        }
        val (_, ctx) = Sessions.normalize(ds, mine, profile)
        assertTrue("max ${ctx.hrMax}", ctx.hrMax >= 195.0)
    }
}
