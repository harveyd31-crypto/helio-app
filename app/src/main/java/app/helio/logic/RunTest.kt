package app.helio.logic

import app.helio.data.TestResult
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The guided VO2 test: 2 min warm-up, then 3 min easy, 3.5 min steady, 3.5 min comfortably hard.
 * For each pace only the last 2 minutes count (heart rate has settled). Each pace gives one point:
 * oxygen cost from the ACSM running equation, against the mean heart rate. A least-squares line
 * through the three points is extended to your max heart rate.
 */
object RunTest {
    data class Phase(val name: String, val cue: String, val startS: Int, val endS: Int)

    val phases = listOf(
        Phase("Warm-up", "Warm up. Easy jog for two minutes.", 0, 120),
        Phase("Easy", "Test starts. Easy pace. You could chat comfortably.", 120, 300),
        Phase("Steady", "Pick it up. Steady pace. You can still talk in short sentences.", 300, 510),
        Phase("Hard", "Last stage. Comfortably hard. Not a sprint.", 510, 720),
    )
    const val TOTAL_S = 720
    private const val WINDOW_S = 120

    data class Fix(val t: Long, val lat: Double, val lon: Double, val alt: Double, val acc: Float)

    private fun dist(a: Fix, b: Fix): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    fun analyse(startMs: Long, fixes: List<Fix>, hr: List<Pair<Long, Int>>, hrMax: Double, epochDay: Long): TestResult {
        val problems = mutableListOf<String>()
        val points = mutableListOf<Triple<Double, Double, Double>>()
        val good = fixes.filter { it.acc <= 20f }
        if (fixes.isNotEmpty() && good.size < fixes.size * 0.7) problems += "GPS was weak (open sky works best)."

        var total = 0.0
        for (i in 1 until good.size) total += dist(good[i - 1], good[i])

        for (ph in phases.drop(1)) {
            val from = startMs + (ph.endS - WINDOW_S) * 1000L
            val to = startMs + ph.endS * 1000L
            val g = good.filter { it.t in from..to }
            val h = hr.filter { it.first in from..to }.map { it.second }
            if (g.size < 40 || h.size < 40) { problems += "${ph.name}: not enough GPS or heart-rate data."; continue }
            var d = 0.0
            for (i in 1 until g.size) d += dist(g[i - 1], g[i])
            val secs = (g.last().t - g.first().t) / 1000.0
            val speed = d / secs
            val altStart = g.take(15).map { it.alt }.average(); val altEnd = g.takeLast(15).map { it.alt }.average()
            val grade = if (d > 50) ((altEnd - altStart) / d).coerceIn(-0.05, 0.05) else 0.0
            val v = speed * 60
            val vo2 = 3.5 + 0.2 * v + 0.9 * v * grade
            if (speed < 1.5) problems += "${ph.name}: too slow to count as running."
            points += Triple(speed, h.average(), vo2)
        }

        if (points.size < 3) return TestResult(epochDay, null, 0.0, points, total, problems.ifEmpty { listOf("Test incomplete.") })

        val xs = points.map { it.second }; val ys = points.map { it.third }
        val mx = xs.average(); val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }; val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val b = if (sxx > 0) sxy / sxx else 0.0
        val a = my - b * mx
        val ssTot = ys.sumOf { (it - my) * (it - my) }
        val ssRes = xs.indices.sumOf { val e = ys[it] - (a + b * xs[it]); e * e }
        val r2 = if (ssTot > 0) 1 - ssRes / ssTot else 0.0

        if (xs.last() - xs.first() < 8) problems += "Heart rate didn't rise enough between paces: make the paces more different."
        if (b <= 0) problems += "Faster pace didn't raise heart rate: check the strap fit."
        if (r2 < 0.8) problems += String.format("The three points don't line up well (fit %.2f).", r2)
        val vo2max = if (b > 0) a + b * hrMax else null
        if (vo2max != null && vo2max !in 20.0..90.0) problems += "Result outside a realistic range."
        return TestResult(epochDay, vo2max, r2, points, total, problems)
    }

    /** Fake but realistic recording, for trying the screen on the emulator. */
    fun simulate(hrMax: Double, epochDay: Long): TestResult {
        val start = 0L
        val fixes = mutableListOf<Fix>(); val hr = mutableListOf<Pair<Long, Int>>()
        var lat = 33.5731; var alt = 50.0
        for (s in 0..TOTAL_S) {
            val speed = when { s < 120 -> 2.3; s < 300 -> 2.6; s < 510 -> 3.1; else -> 3.6 }
            val bpm = when { s < 120 -> 120; s < 300 -> 136; s < 510 -> 152; else -> 167 } + (s % 7) - 3
            lat += speed / 111_000.0
            alt += 0.0
            fixes += Fix(start + s * 1000L, lat, -7.5898, alt, 6f)
            hr += (start + s * 1000L) to bpm
        }
        return analyse(start, fixes, hr, hrMax, epochDay)
    }
}
