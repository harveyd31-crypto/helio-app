package app.helio.logic

import kotlin.math.ln

import kotlin.math.exp

/**
 * Stress 0–3 from heart rate against a personal resting daytime baseline.
 *
 * Same shape as Pulse's Stress Monitor (src/core/algorithms/stress.ts, PolyForm Noncommercial,
 * © Aditya Jindal / NoopApp): the heart rate becomes a z-score, then a logistic curve maps it onto
 * 0..3 so a still minute at baseline reads about 0.3 and baseline + 15 bpm reads 2.0.
 * The Helio only broadcasts heart rate (no beat-to-beat intervals), so live stress is HR-based;
 * the strap's own HRV-based stress arrives later through the stored-data sync.
 */
object Stress {
    private const val K = 1.49
    private const val Z0 = 1.5
    /** bpm per z-unit while no personal spread is known: puts baseline + 15 bpm at 2.0. */
    const val DEFAULT_SIGMA = 7.63

    fun fromHr(hr: Double, baseline: Double, sigma: Double = DEFAULT_SIGMA): Double {
        val z = (hr - baseline) / sigma
        return 3.0 / (1.0 + exp(-K * (z - Z0)))
    }

    fun label(v: Double?): String = when {
        v == null -> "—"
        v < 1.0 -> "LOW"
        v <= 2.0 -> "MEDIUM"
        else -> "HIGH"
    }
}

/**
 * Banister-style training impulse turned into a 0–21 strain score.
 * V1 approximation until Pulse's strain engine is ported.
 */
object Strain {
    fun fromHeartRate(
        samples: List<Pair<Long, Int>>,
        restingHr: Double,
        maxHr: Double = 190.0,
    ): Double {
        if (samples.size < 2) return 0.0
        var trimp = 0.0
        for (i in 1 until samples.size) {
            val dtMin = ((samples[i].first - samples[i - 1].first) / 60000.0).coerceIn(0.0, 5.0)
            val hrr = ((samples[i].second - restingHr) / (maxHr - restingHr)).coerceIn(0.0, 1.0)
            trimp += dtMin * hrr * 0.64 * exp(1.92 * hrr)
        }
        return 21.0 * (1.0 - exp(-trimp / 120.0))
    }

    /**
     * Day strain, WHOOP-style: every waking minute your heart works above rest counts (Banister
     * TRIMP on heart-rate reserve; minutes under 10 % of reserve, i.e. truly at rest, and sleep
     * don't), on a 0–21 scale that gets harder to climb: 21·ln(1 + TRIMP/40)/ln(11).
     * Roughly: a day at home 4–7, with a walk 7–9, a hard 75-min session 14–16, a huge day 18+.
     */
    fun day(samples: List<Pair<Long, Int>>, restingHr: Double, maxHr: Double, sleep: List<Pair<Long, Long>> = emptyList()): Double {
        if (samples.size < 2 || maxHr - restingHr < 10) return 0.0
        var trimp = 0.0
        for (i in 1 until samples.size) {
            val t = samples[i].first
            if (sleep.any { t >= it.first && t < it.second }) continue
            val dtMin = ((t - samples[i - 1].first) / 60000.0).coerceIn(0.0, 5.0)
            val hrr = ((samples[i].second - restingHr) / (maxHr - restingHr)).coerceIn(0.0, 1.0)
            if (hrr < 0.10) continue
            trimp += dtMin * hrr * 0.64 * exp(1.92 * hrr)
        }
        return (21.0 * ln(1 + trimp / 40.0) / ln(11.0)).coerceIn(0.0, 21.0)
    }

    /** Today's target from recovery: a rested body can take more. */
    fun target(recovery: Int?): Double = 8.0 + (recovery ?: 50) / 100.0 * 10.0
}
