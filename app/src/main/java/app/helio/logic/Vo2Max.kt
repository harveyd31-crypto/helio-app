package app.helio.logic

import app.helio.data.DaySummary
import app.helio.data.Profile
import app.helio.data.RunEffort
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Helio VO2 max: three sources blended, best evidence weighted most.
 *
 * 1. Resting (every day, weight 0.3) — Uth et al. 2004: VO2max ≈ 15.3 × HRmax / HRrest.
 *    Cheap and always available, but the least accurate (studies: ~10 ml/kg/min typical error).
 * 2. Runs (weight 1.0) — each steady run is one point on your heart-rate-vs-oxygen line.
 *    Oxygen cost of the run from the ACSM running equation: VO2 = 3.5 + 0.2·v + 0.9·v·grade
 *    (v in m/min). Heart-rate reserve tracks oxygen-uptake reserve (Swain), so the line from
 *    (resting HR, 3.5) through the run's point is extended to your max HR. This is the same family
 *    of method as Garmin / Polar / Apple (pace + HR), which studies find clearly better than
 *    resting-only (~6–7 ml/kg/min typical error).
 * 3. Guided test runs (weight 2.0) — the same maths on a run the app records under controlled
 *    conditions (flat, steady stages), so the point is cleaner.
 *
 * Estimates from the last 42 days are averaged with a 21-day recency fade.
 * Max HR: the higher of Tanaka (208 − 0.7 × age) and your observed max (second-highest daily max
 * over 90 days), because a too-low max HR is the biggest source of error.
 */
object Vo2Max {
    enum class Method(val label: String) { Resting("Resting heart rate"), Run("Run"), Test("Running test") }

    data class Estimate(val date: LocalDate, val value: Double, val method: Method, val detail: String)

    data class Result(
        val value: Double?,
        val plusMinus: Double,
        val hrMax: Double,
        val hrMaxSource: String,
        val hrRest: Double?,
        val history: List<Pair<LocalDate, Double>>,
        val estimates: List<Estimate>,
        val category: String?,
        val percentile: Int?,
        val runsUsed: Int,
    )

    private fun weight(m: Method) = when (m) { Method.Resting -> 0.3; Method.Run -> 1.0; Method.Test -> 2.0 }

    fun hrMax(profile: Profile, days: List<DaySummary>, asOf: LocalDate): Pair<Double, String> {
        val tanaka = profile.age?.let { 208 - 0.7 * it }
        val maxes = days.filter { !it.date.isAfter(asOf) && it.date.isAfter(asOf.minusDays(90)) }.mapNotNull { it.maxHr }.sortedDescending()
        val observed = maxes.getOrNull(1)?.toDouble()
        return when {
            observed != null && observed in 120.0..225.0 && (tanaka == null || observed > tanaka) -> observed to "Observed max (90 days)"
            tanaka != null -> tanaka to "Age formula (208 − 0.7 × age)"
            observed != null -> observed to "Observed max (90 days)"
            else -> 185.0 to "Default (add your age in Profile)"
        }
    }

    fun fromRun(run: RunEffort, hrMax: Double, hrRest: Double): Double? {
        if (run.minutes < 12 || run.speedMps < 1.6) return null
        val v = run.speedMps * 60.0
        val vo2 = 3.5 + 0.2 * v + 0.9 * v * (run.gradePct / 100.0)
        val frac = (run.avgHr - hrRest) / (hrMax - hrRest)
        if (frac !in 0.5..0.95) return null
        return 3.5 + (vo2 - 3.5) / frac
    }

    private fun restHrAround(days: List<DaySummary>, d: LocalDate): Double? {
        val xs = days.filter { !it.date.isAfter(d) && it.date.isAfter(d.minusDays(7)) }.mapNotNull { it.restingHr?.toDouble() }.sorted()
        return if (xs.isEmpty()) null else xs[xs.size / 2]
    }

    fun compute(days: List<DaySummary>, profile: Profile, asOf: LocalDate): Result {
        val (hrMax, src) = hrMax(profile, days, asOf)
        val estimates = mutableListOf<Estimate>()
        for (d in days.filter { !it.date.isAfter(asOf) && it.date.isAfter(asOf.minusDays(90)) }) {
            val rest = restHrAround(days, d.date) ?: continue
            estimates += Estimate(d.date, 15.3 * hrMax / rest, Method.Resting, "15.3 × ${hrMax.roundToInt()} / ${rest.roundToInt()} bpm")
            d.runs.forEach { r ->
                fromRun(r, hrMax, rest)?.let { v ->
                    val pace = 1000.0 / r.speedMps
                    estimates += Estimate(
                        d.date, v, if (r.test) Method.Test else Method.Run,
                        String.format("%d min at %d:%02d /km, %d bpm", r.minutes.roundToInt(), (pace / 60).toInt(), (pace % 60).toInt(), r.avgHr.roundToInt()),
                    )
                }
            }
        }

        fun valueAt(day: LocalDate): Pair<Double, Boolean>? {
            val window = estimates.filter { !it.date.isAfter(day) && it.date.isAfter(day.minusDays(42)) }
            if (window.isEmpty()) return null
            var sw = 0.0; var s = 0.0
            window.forEach { e ->
                val w = weight(e.method) * exp(-(day.toEpochDay() - e.date.toEpochDay()) / 21.0)
                sw += w; s += w * e.value
            }
            return s / sw to window.any { it.method != Method.Resting }
        }

        val history = (89 downTo 0).mapNotNull { k -> val d = asOf.minusDays(k.toLong()); valueAt(d)?.let { d to it.first } }
        val now = valueAt(asOf)
        val pct = if (now != null && profile.age != null && profile.male != null) Fitness.percentile(now.first, profile.age!!, profile.male) else null
        return Result(
            value = now?.first,
            plusMinus = if (now?.second == true) 4.0 else 8.0,
            hrMax = hrMax,
            hrMaxSource = src,
            hrRest = restHrAround(days, asOf),
            history = history,
            estimates = estimates.filter { it.date.isAfter(asOf.minusDays(42)) }.sortedByDescending { it.date },
            category = pct?.let { Fitness.category(it) },
            percentile = pct?.roundToInt(),
            runsUsed = estimates.count { it.method != Method.Resting && it.date.isAfter(asOf.minusDays(42)) },
        )
    }
}

/**
 * Where a VO2 max sits for your age and sex: FRIEND treadmill reference standards
 * (Kaminsky et al. 2015, Mayo Clin Proc 90(11):1515–1523), as tabulated in Pulse's fitnessLevel.ts.
 */
object Fitness {
    private val pcts = doubleArrayOf(5.0, 10.0, 25.0, 50.0, 75.0, 90.0, 95.0)
    private val male = arrayOf(
        doubleArrayOf(29.0, 32.1, 40.1, 48.0, 55.2, 61.8, 66.3),
        doubleArrayOf(27.2, 30.2, 35.9, 42.4, 49.2, 56.5, 59.8),
        doubleArrayOf(24.2, 26.8, 31.9, 37.8, 45.0, 52.1, 55.6),
        doubleArrayOf(20.9, 22.8, 27.1, 32.6, 39.7, 45.6, 50.7),
        doubleArrayOf(17.4, 19.8, 23.7, 28.2, 34.5, 40.3, 43.0),
        doubleArrayOf(16.3, 17.1, 20.4, 24.4, 30.4, 36.6, 39.7),
    )
    private val female = arrayOf(
        doubleArrayOf(21.7, 23.9, 30.5, 37.6, 44.7, 51.3, 56.0),
        doubleArrayOf(19.0, 20.9, 25.3, 30.2, 36.1, 41.4, 45.8),
        doubleArrayOf(17.0, 18.8, 22.1, 26.7, 32.4, 38.4, 41.7),
        doubleArrayOf(16.0, 17.3, 19.9, 23.4, 27.6, 32.0, 35.9),
        doubleArrayOf(13.4, 14.6, 17.2, 20.0, 23.8, 27.0, 29.4),
        doubleArrayOf(13.1, 13.6, 15.6, 18.3, 20.8, 23.1, 24.1),
    )

    fun row(age: Int, isMale: Boolean): DoubleArray = (if (isMale) male else female)[((age - 20) / 10).coerceIn(0, 5)]

    fun percentile(vo2: Double, age: Int, isMale: Boolean): Double {
        val r = row(age, isMale)
        if (vo2 <= r[0]) return 5.0 * vo2 / r[0]
        for (i in 1 until r.size) if (vo2 <= r[i]) return pcts[i - 1] + (pcts[i] - pcts[i - 1]) * (vo2 - r[i - 1]) / (r[i] - r[i - 1])
        return 99.0
    }

    /** Garmin's published cut-offs on the percentiles (as in Pulse). */
    fun category(p: Double) = when {
        p >= 95 -> "Superior"; p >= 80 -> "Excellent"; p >= 60 -> "Good"; p >= 40 -> "Fair"; else -> "Poor"
    }
}
