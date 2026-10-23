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
    val startMs: Long = 0,
    val endMs: Long = 0,
    /** Heart rate during the activity, about every 5 s: (epoch millis, bpm). */
    val hr: List<Pair<Long, Int>> = emptyList(),
    /** Work rounds (start, end epoch millis) when the round timer was used. */
    val rounds: List<Pair<Long, Long>> = emptyList(),
    /** Muay Thai session kind (sample data only; real ones are tagged in [app.helio.data.Workouts]). */
    val tag: String? = null,
)

enum class Stage { Awake, Rem, Light, Deep }

/** One block of the hypnogram, epoch millis. */
data class StageBlock(val start: Long, val end: Long, val stage: Stage)

data class SleepInfo(
    val minutesAsleep: Int,
    val bed: LocalTime,
    val wake: LocalTime,
    val bedDayLabel: String,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val stages: List<StageBlock> = emptyList(),
    /** Heart rate and breathing rate during the night: (epoch millis, value). */
    val hr: List<Pair<Long, Int>> = emptyList(),
    val resp: List<Pair<Long, Double>> = emptyList(),
) {
    fun minutesIn(s: Stage): Int = (stages.filter { it.stage == s }.sumOf { it.end - it.start } / 60_000).toInt()
}

/** A finished guided VO2 test. */
data class TestResult(
    val dateEpochDay: Long,
    val vo2max: Double?,
    val r2: Double,
    /** Per stage: speed m/s, heart rate, oxygen cost ml/kg/min. */
    val stages: List<Triple<Double, Double, Double>>,
    val distanceM: Double,
    val problems: List<String>,
) {
    val valid: Boolean get() = vo2max != null && problems.isEmpty()
}

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
    val steps: Int? = null,
    /** Minutes at 60–80 % of max HR (moderate) and above 80 % (vigorous). */
    val moderateMin: Int? = null,
    val vigorousMin: Int? = null,
    val strengthMin: Int? = null,
    /** Highest heart rate of the day (second-highest minute, to ignore spikes). */
    val maxHr: Int? = null,
    val runs: List<RunEffort> = emptyList(),
    /** Extra numbers available when the strap syncs directly (Ridge's science). */
    val extras: StrapExtras? = null,
)

data class RecoveryPart(val key: String, val sub: Int, val weight: Double, val value: Double, val baseline: Double?)

/** What the direct strap sync adds on top of the basics. Null fields = not measured / withheld. */
data class StrapExtras(
    val charge: Int? = null,
    val chargeAtWake: Int? = null,
    val chargeHigh: Int? = null,
    val chargeLow: Int? = null,
    val chargeRecharged: Double? = null,
    val chargeActivity: Double? = null,
    val chargeStress: Double? = null,
    val chargeLiving: Double? = null,
    /** Charge every 10 minutes: (epoch millis, 0–100). */
    val chargeCurve: List<Pair<Long, Double>> = emptyList(),
    val recoveryParts: List<RecoveryPart> = emptyList(),
    val recoveryWithheld: String? = null,
    val sleepNeedMin: Int? = null,
    val sleepDebtMin: Int? = null,
    val sleepRegularity: Double? = null,
    val sleepEfficiency: Double? = null,
    val deviceSleepScore: Int? = null,
    val breathingPerHour: Double? = null,
    val breathingUsual: Double? = null,
    val spo2Night: Double? = null,
    val spo2Min: Double? = null,
    val steps: Int? = null,
    val stepsUsualByNow: Int? = null,
    val activeCalories: Int? = null,
    val totalCalories: Int? = null,
    val distanceKm: Double? = null,
    val pai: Int? = null,
    val paiToday: Int? = null,
    val strapStress: Int? = null,
    val strainWithheld: String? = null,
    val illness: String? = null,
    val illnessSevere: Boolean = false,
    val cardioLoad: Double? = null,
    val typicalLoad: Double? = null,
)

/** A steady run: enough to place one point on your heart-rate-vs-speed line. */
data class RunEffort(
    val minutes: Double,
    val speedMps: Double,
    val avgHr: Double,
    val gradePct: Double = 0.0,
    /** True for a guided test run recorded by the app (weighted more). */
    val test: Boolean = false,
)

data class Profile(
    val birthYear: Int? = null,
    val male: Boolean? = null,
    val heightCm: Int? = null,
    val weightKg: Double? = null,
) {
    val age: Int? get() = birthYear?.let { LocalDate.now().year - it }
    val complete: Boolean get() = birthYear != null && male != null
}

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

enum class DataSource { Sample, HealthConnect, Strap }
