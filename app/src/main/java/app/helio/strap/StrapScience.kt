package app.helio.strap

import android.content.Context
import app.helio.data.Activity
import app.helio.data.Band
import app.helio.data.DaySummary
import app.helio.data.Profile
import app.helio.data.RecoveryPart
import app.helio.data.SleepInfo
import app.helio.data.Stage
import app.helio.data.StageBlock
import app.helio.data.StrapExtras
import app.helio.data.StressPoint
import app.helio.logic.Strain
import strap.science.Card
import strap.science.Science
import strap.science.WeightEntry
import strap.science.Zones
import strap.science.daySeries
import strap.science.daySummary
import strap.science.illnessCheck
import strap.science.rawFrom
import strap.science.rederive
import strap.science.shown
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Turns what the strap delivered (stored by [LocalStore]) into Helio's days, using Ridge's
 * science for recovery, strain, sleep and the rest. Helio's own extras (weekly goal,
 * VO2 max blend, estimated age, live stress) keep running on top of these days.
 */
object StrapScience {
    private val CHART_START: LocalTime = LocalTime.of(4, 30)
    private val CHART_END: LocalTime = LocalTime.of(23, 0)

    fun hasData(ctx: Context): Boolean = StrapManager.pairing(ctx) != null && !StrapManager.store(ctx).isEmpty()

    fun days(ctx: Context, profile: Profile, count: Int = 60): List<DaySummary> {
        val store = StrapManager.store(ctx)
        val zone = ZoneId.systemDefault()
        val zones = Zones(zone.id)
        val today = LocalDate.now(zone)
        val first = today.minusDays((count - 1).toLong())
        val sProfile = strap.science.Profile(
            heightCm = profile.heightCm?.toFloat(),
            sex = profile.male?.let { if (it) "male" else "female" },
            dob = profile.birthYear?.let { LocalDate.of(it, 7, 1) },
        )
        val weights = profile.weightKg?.let { listOf(WeightEntry(Instant.now().minusSeconds(86_400), it.toFloat())) } ?: emptyList()
        val data = store.scienceData(zones, sProfile, weights, zones.rawFrom(first), zones.start(today.plusDays(2)))
        val science = Science(data, store.derived)
        science.rederive(since = first)
        val now = Instant.now()

        return (0 until count).map { k ->
            val day = first.plusDays(k.toLong())
            val sum = science.daySummary(day, now)
            val series = science.daySeries(day, listOf("hr", "stress", "respiratory_rate"))
            val hr = series.series["hr"].orEmpty().map { it.at.toEpochMilli() to it.value.roundToInt() }
            val (dayStart, dayEnd) = zones.bounds(day)

            val chartStart = day.atTime(CHART_START).atZone(zone).toInstant().toEpochMilli()
            val chartEnd = day.atTime(CHART_END).atZone(zone).toInstant().toEpochMilli()
            fun frac(ms: Long) = ((ms - chartStart).toFloat() / (chartEnd - chartStart)).coerceIn(0f, 1f)

            // Strap stress 0–100 shown on Helio's 0–3 scale (and the raw value in extras)
            val stress = series.series["stress"].orEmpty().filter { it.at.toEpochMilli() in chartStart..chartEnd }
                .map { StressPoint(frac(it.at.toEpochMilli()), it.value / 100.0 * 3.0) }

            val main = sum.sleep.main
            val rest = sum.heart.resting.shown?.value
            val sleep = main?.let { n ->
                val blocks = n.stages.mapNotNull { st ->
                    val s = when (st.code) { 4 -> Stage.Light; 5 -> Stage.Deep; 7 -> Stage.Awake; 8 -> Stage.Rem; else -> null }
                    s?.let { StageBlock(st.startMs, st.endMs, it) }
                }
                val asleep = listOfNotNull(n.remMin, n.lightMin, n.deepMin).sum().takeIf { it > 0 }
                    ?: blocks.filter { it.stage != Stage.Awake }.sumOf { it.end - it.start }.div(60_000).toInt()
                val s0 = n.start.toEpochMilli(); val e0 = n.end.toEpochMilli()
                SleepInfo(
                    minutesAsleep = asleep,
                    bed = n.start.atZone(zone).toLocalTime(),
                    wake = n.end.atZone(zone).toLocalTime(),
                    bedDayLabel = n.start.atZone(zone).dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                    startMs = s0, endMs = e0, stages = blocks,
                    hr = science.daySeries(n.start.atZone(zone).toLocalDate(), listOf("hr")).series["hr"].orEmpty()
                        .plus(series.series["hr"].orEmpty())
                        .filter { it.at.toEpochMilli() in s0..e0 }.distinctBy { it.at }.sortedBy { it.at }
                        .map { it.at.toEpochMilli() to it.value.roundToInt() },
                    resp = series.series["respiratory_rate"].orEmpty().filter { it.at.toEpochMilli() in s0..e0 }.map { it.at.toEpochMilli() to it.value },
                )
            }
            val need = sum.sleep.needMin ?: 480.0
            val strainCard = sum.strain.shown
            val recovery = sum.recovery.shown?.value
            val strain = strainCard?.value ?: if (hr.size > 60) Strain.fromHeartRate(hr, rest ?: 60.0) else null
            val zonesMin = strainCard?.zoneMinutes

            val workouts = data.workouts(dayStart, dayEnd).map { w ->
                val s0 = w.start.toEpochMilli(); val e0 = s0 + w.durationS * 1000L
                val wHr = hr.filter { it.first in s0..e0 }
                Activity(
                    name = sportName(w.sport),
                    strain = (Strain.fromHeartRate(wHr, rest ?: 60.0) * 10).toInt() / 10.0,
                    start = w.start.atZone(zone).toLocalTime(),
                    end = Instant.ofEpochMilli(e0).atZone(zone).toLocalTime(),
                    startMs = s0, endMs = e0, hr = wHr,
                )
            }

            val check = runCatching { science.illnessCheck(day) }.getOrNull()
            val act = sum.activity
            DaySummary(
                date = day,
                recovery = recovery,
                hrv = sum.heart.hrv.shown?.value?.roundToInt(),
                restingHr = rest?.roundToInt(),
                strain = strain?.let { (it * 10).roundToInt() / 10.0 },
                strainTarget = Strain.target(recovery),
                sleepPerformance = sleep?.let { (it.minutesAsleep / need * 100).roundToInt().coerceIn(0, 100) },
                sleep = sleep,
                respRate = store.derived.get(day, "respiratory_rate_sleep")?.value,
                skinTempDelta = check?.let { c -> c.tempNight?.let { n -> c.tempUsual?.let { u -> n - u } } },
                stressNow = stress.lastOrNull()?.v,
                stressUpdated = series.series["stress"].orEmpty().lastOrNull()?.at?.atZone(zone)?.toLocalTime(),
                stressSeries = stress,
                chartStart = CHART_START,
                chartEnd = CHART_END,
                sleepBand = main?.let { Band(frac(it.start.toEpochMilli()), frac(it.end.toEpochMilli())) },
                workoutBands = workouts.map { Band(frac(it.startMs), frac(it.endMs)) },
                activities = workouts,
                steps = act.steps.shown?.value?.roundToInt(),
                moderateMin = zonesMin?.let { (it.getOrNull(1) ?: 0) + (it.getOrNull(2) ?: 0) },
                vigorousMin = zonesMin?.let { (it.getOrNull(3) ?: 0) + (it.getOrNull(4) ?: 0) },
                strengthMin = 0,
                maxHr = sum.heart.today.shown?.max?.roundToInt(),
                extras = StrapExtras(
                    skinTempNight = check?.tempNight,
                    recoveryParts = sum.recovery.shown?.factors.orEmpty().map { RecoveryPart(it.key, it.sub, it.weight, it.value, it.baseline) },
                    recoveryWithheld = (sum.recovery as? Card.Withheld)?.message,
                    sleepNeedMin = sum.sleep.needMin?.roundToInt(),
                    sleepDebtMin = sum.sleep.debt.shown?.minutes?.roundToInt(),
                    sleepRegularity = sum.sleep.regularity.shown,
                    sleepEfficiency = sum.sleep.health.shown?.efficiencyPct,
                    deviceSleepScore = main?.deviceScore,
                    breathingPerHour = sum.sleep.breathing.shown?.value,
                    breathingUsual = sum.sleep.breathing.shown?.usual?.median,
                    spo2Night = store.derived.get(day, "spo2_overnight")?.value,
                    spo2Min = store.derived.get(day, "spo2_overnight_min")?.value,
                    steps = act.steps.shown?.value?.roundToInt(),
                    stepsUsualByNow = act.usualByNow?.median?.roundToInt(),
                    activeCalories = act.activeCalories.shown?.value?.roundToInt(),
                    totalCalories = act.totalCalories.shown?.value?.roundToInt(),
                    distanceKm = act.distanceM.shown?.value?.div(1000.0),
                    pai = sum.pai.shown?.value, paiToday = sum.pai.shown?.today,
                    strapStress = series.series["stress"].orEmpty().lastOrNull()?.value?.roundToInt(),
                    strainWithheld = (sum.strain as? Card.Withheld)?.message,
                    illness = sum.illness?.framing, illnessSevere = sum.illness?.severity == "high",
                    cardioLoad = strainCard?.cardioLoad, typicalLoad = strainCard?.typicalLoad,
                ),
            )
        }
    }

    /** Best-guess Zepp sport codes (unverified on the Helio); anything else shows as a plain workout. */
    private fun sportName(code: Int): String = when (code) {
        1 -> "Run"; 6 -> "Walk"; 9 -> "Cycling"; 18 -> "Tennis"; 23 -> "Cross"; 52 -> "Strength"; 72 -> "Muay Thai"
        else -> "Workout"
    }
}
