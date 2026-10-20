package app.helio.data

import java.time.LocalDate
import java.time.LocalTime

/** One point on the day's stress line: `t` is 0..1 across the chart window, `v` is stress 0..3. */
data class StressPoint(val t: Float, val v: Double)

/** A shaded band on the stress chart (sleep or a workout), 0..1 across the chart window. */
data class Band(val start: Float, val end: Float)

data class Activity(
    val name: String,
    val strain: Double,
    val start: LocalTime,
    val end: LocalTime,
)

data class SleepInfo(
    val minutesAsleep: Int,
    val bed: LocalTime,
    val wake: LocalTime,
    val bedDayLabel: String,
)

data class DaySummary(
    val date: LocalDate,
    val recovery: Int?,
    val hrv: Int?,
    val restingHr: Int?,
    val strain: Double?,
    val strainTarget: Double?,
    val sleepPerformance: Int?,
    val sleep: SleepInfo?,
    val respRate: Double?,
    val skinTempDelta: Double?,
    val stressNow: Double?,
    val stressUpdated: LocalTime?,
    val stressSeries: List<StressPoint>,
    val chartStart: LocalTime,
    val chartEnd: LocalTime,
    val sleepBand: Band?,
    val workoutBands: List<Band>,
    val activities: List<Activity>,
)

data class WeekGoal(
    val goal: Double,
    val scale: Double,
    val cumulative: Double,
    val pace: Double,
    val dayIndex: Int,
    /** Monday..Sunday: actual strain for days so far, null for days still ahead. */
    val daily: List<Double?>,
    val perDayLeft: Double,
) {
    val left: Double get() = (goal - cumulative).coerceAtLeast(0.0)
    val vsPace: Double get() = cumulative - pace
}

enum class LinkStatus { Off, Scanning, Connecting, Live, Lost }

data class LiveState(
    val status: LinkStatus = LinkStatus.Off,
    val deviceName: String? = null,
    val hr: Int? = null,
    val stress: Double? = null,
    val restingBaseline: Double = 62.0,
    /** Last 10 minutes, oldest first: (epoch millis, bpm). */
    val hrHistory: List<Pair<Long, Int>> = emptyList(),
    /** Last 10 minutes of stress, one value every ~5 s: (epoch millis, 0..3). */
    val stressHistory: List<Pair<Long, Double>> = emptyList(),
    val sessionStart: Long? = null,
    val stressAtStart: Double? = null,
)

enum class DataSource { Sample, HealthConnect }
