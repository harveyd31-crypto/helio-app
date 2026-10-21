package app.helio.logic

import app.helio.data.DaySummary
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Estimated (biological) age: your age plus the years nine habits and vitals add or take away.
 * Each factor's 28-day value becomes a hazard ratio against a reference for your age and sex, and
 * a hazard ratio becomes years as 10 × ln(HR). Ported from Pulse's Pulse Age engine
 * (src/core/engine/pulseAge.ts, PolyForm Noncommercial, © Aditya Jindal / NoopApp).
 * Needs at least 5 of the 9 factors; missing ones are filled with the mean of the present ones.
 */
object BodyAge {
    enum class Factor(val label: String, val unit: String) {
        SleepHours("Sleep", "h / night"),
        Consistency("Sleep consistency", "/ 100"),
        Steps("Steps", "/ day"),
        Moderate("Moderate activity", "min / week"),
        Vigorous("Vigorous activity", "min / week"),
        Strength("Strength training", "min / week"),
        RestingHr("Resting heart rate", "bpm"),
        Vo2Max("VO2 max", "ml/kg/min"),
        LeanMass("Lean mass", "%"),
    }

    data class FactorResult(val factor: Factor, val value: Double?, val reference: Double, val years: Double?)
    data class Result(val age: Double, val chronological: Int, val delta: Double, val pace: Double, val present: Int, val factors: List<FactorResult>)

    private const val WINDOW = 28
    private const val MIN_FACTORS = 5

    private val rhrRef = listOf(Triple(0..25, 61.0, 65.0), Triple(26..35, 61.0, 64.0), Triple(36..45, 62.0, 64.0), Triple(46..55, 63.0, 65.0), Triple(56..65, 61.0, 64.0))
    private fun rhrRef(age: Int, male: Boolean) = rhrRef.firstOrNull { age in it.first }?.let { if (male) it.second else it.third } ?: if (male) 62.0 else 64.0
    private val vo2Ref = listOf(Triple(0..29, 48.0, 37.6), Triple(30..39, 42.4, 30.2), Triple(40..49, 37.8, 26.7), Triple(50..59, 32.6, 23.4), Triple(60..69, 28.2, 20.0), Triple(70..200, 24.4, 18.3))
    private fun vo2Ref(age: Int, male: Boolean) = vo2Ref.first { age in it.first }.let { if (male) it.second else it.third }

    private fun reference(f: Factor, age: Int, male: Boolean): Double = when (f) {
        Factor.SleepHours -> 7.5
        Factor.Consistency -> 70.0
        Factor.Steps -> if (age < 60) 8000.0 else 6000.0
        Factor.Moderate -> 246 * ln(0.46 / 0.25)
        Factor.Vigorous -> 20 * ln(0.08041 / 0.04877)
        Factor.Strength -> 15.14 * ln(0.3348 / 0.173)
        Factor.RestingHr -> rhrRef(age, male)
        Factor.Vo2Max -> vo2Ref(age, male)
        Factor.LeanMass -> 100 - if (male) 25.0 else 36.0
    }

    private fun hazard(f: Factor, x: Double, age: Int, male: Boolean): Double = when (f) {
        Factor.SleepHours -> exp(0.05 * (x - 7.5) * (x - 9)).coerceIn(0.9802, 1.24608)
        Factor.Consistency -> exp((70 - x) * if (x > 70) 0.007 else 0.015).coerceIn(0.74082, 1.16183)
        Factor.Steps -> {
            val (target, cap) = if (age < 60) 8000.0 to 10000.0 else 6000.0 to 8000.0
            (x.coerceIn(1.0, cap) / target).pow(-0.26).coerceIn(0.90484, 1.24608)
        }
        Factor.Moderate -> (0.75 + 0.46 * exp(-x / 246)).coerceIn(0.74082, 1.10517)
        Factor.Vigorous -> (0.95123 + 0.08041 * exp(-x / 20)).coerceIn(0.95123, 1.03045)
        Factor.Strength -> (0.827 + 0.3348 * exp(-x / 15.14)).coerceIn(0.84366, 1.10517)
        Factor.RestingHr -> exp(0.012 * (x - rhrRef(age, male))).coerceIn(0.63763, 1.2214)
        Factor.Vo2Max -> max(0.3, exp(0.038 * (vo2Ref(age, male) - x))).coerceIn(0.60653, 1.28403)
        Factor.LeanMass -> 1.0
    }

    /** Sleep consistency 0–100 from how much bed and wake times move around (simplified). */
    private fun consistency(days: List<DaySummary>): Double? {
        val s = days.mapNotNull { it.sleep }
        if (s.size < 5) return null
        fun sd(xs: List<Double>): Double { val m = xs.average(); return sqrt(xs.sumOf { (it - m) * (it - m) } / xs.size) }
        val bed = s.map { b -> val m = b.bed.toSecondOfDay() / 60.0; if (m < 12 * 60) m + 24 * 60 else m }
        val wake = s.map { it.wake.toSecondOfDay() / 60.0 }
        return (100 - (sd(bed) + sd(wake)) / 2 / 1.5).coerceIn(0.0, 100.0)
    }

    fun compute(days: List<DaySummary>, asOf: LocalDate, age: Int, male: Boolean, vo2: Double?): Result? {
        val w = days.filter { !it.date.isAfter(asOf) && it.date.isAfter(asOf.minusDays(WINDOW.toLong())) }
        fun mean(xs: List<Double>) = if (xs.size >= 5) xs.average() else null
        fun weekly(xs: List<Double>) = if (xs.size >= 5) xs.sum() * 7.0 / xs.size else null
        val values: Map<Factor, Double?> = mapOf(
            Factor.SleepHours to mean(w.mapNotNull { it.sleep?.minutesAsleep?.div(60.0) }),
            Factor.Consistency to consistency(w),
            Factor.Steps to mean(w.mapNotNull { it.steps?.toDouble() }),
            Factor.Moderate to weekly(w.mapNotNull { it.moderateMin?.toDouble() }),
            Factor.Vigorous to weekly(w.mapNotNull { it.vigorousMin?.toDouble() }),
            Factor.Strength to weekly(w.mapNotNull { it.strengthMin?.toDouble() }),
            Factor.RestingHr to mean(w.mapNotNull { it.restingHr?.toDouble() }),
            Factor.Vo2Max to vo2,
            Factor.LeanMass to null,
        )
        val raw = Factor.entries.map { f ->
            val x = values[f]
            FactorResult(f, x, reference(f, age, male), x?.let { 10 * ln(hazard(f, it, age, male)) })
        }
        val present = raw.count { it.years != null }
        if (present < MIN_FACTORS) return null
        val scale = Factor.entries.size.toDouble() / present
        val factors = raw.map { it.copy(years = it.years?.times(scale)) }
        val delta = factors.sumOf { it.years ?: 0.0 }
        val bio = (age + delta).coerceIn(0.0, 150.0)
        return Result(bio, age, bio - age, (bio / age).coerceIn(0.6, 1.6), present, factors)
    }
}
