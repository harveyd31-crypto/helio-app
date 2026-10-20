package app.helio.data

import app.helio.logic.Strain
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.sin

/** Demo days shown until Health Connect has data. Deterministic, so screenshots stay stable. */
object SampleData {
    private val names = listOf("Running", "Cycling", "Walking", "Strength", "Tennis")

    fun days(today: LocalDate = LocalDate.now(), count: Int = 35): List<DaySummary> =
        (count - 1 downTo 0).map { back -> day(today.minusDays(back.toLong()), back) }

    private fun day(date: LocalDate, seed: Int): DaySummary {
        val s = seed + 3
        val recovery = (62 + 26 * sin(s * 1.3) + 8 * sin(s * 3.7)).toInt().coerceIn(18, 96)
        val strain = (11.5 + 5.5 * sin(s * 0.9 + 1) + 1.5 * sin(s * 2.3)).coerceIn(4.0, 20.5)
        val sleepPerf = (82 + 12 * sin(s * 1.7)).toInt().coerceIn(55, 100)
        val asleep = (300 + sleepPerf * 1.9).toInt()
        val wake = LocalTime.of(7, 0).plusMinutes(((s * 7) % 40).toLong())
        val bed = wake.minusMinutes(asleep.toLong() + 25)
        val actStart = LocalTime.of(17, 30).plusMinutes(((s * 13) % 90).toLong())
        val actLen = 35L + (s * 11) % 50
        val woStart = (actStart.toSecondOfDay() - 4.5 * 3600) / (18.5 * 3600)
        val woEnd = woStart + actLen * 60 / (18.5 * 3600)

        val sleepEnd = ((wake.toSecondOfDay() - 4.5 * 3600) / (18.5 * 3600)).toFloat().coerceIn(0f, 1f)
        val series = (0..180).map { k ->
            val f = k / 180f
            var v = if (f < sleepEnd) 0.95 else 1.4 + 0.5 * f
            if (f in woStart.toFloat()..woEnd.toFloat()) v = 2.6
            v += 0.32 * sin(k * 1.7 + s) + 0.22 * sin(k * 3.1 + 1 + s) + 0.16 * sin(k * 7.3 + 2 * s)
            StressPoint(f, v.coerceIn(0.2, 2.98))
        }

        return DaySummary(
            date = date,
            recovery = recovery,
            hrv = 40 + recovery / 3,
            restingHr = 62 - recovery / 10,
            strain = (strain * 10).toInt() / 10.0,
            strainTarget = Strain.target(recovery),
            sleepPerformance = sleepPerf,
            sleep = SleepInfo(asleep, bed, wake, date.minusDays(1).dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }),
            respRate = 14.6 + (s % 5) * 0.3,
            skinTempDelta = ((s % 7) - 3) / 10.0,
            stressNow = series.last().v,
            stressUpdated = LocalTime.of(22, 30),
            stressSeries = series,
            chartStart = LocalTime.of(4, 30),
            chartEnd = LocalTime.of(23, 0),
            sleepBand = Band(0f, sleepEnd),
            workoutBands = listOf(Band(woStart.toFloat(), woEnd.toFloat().coerceAtMost(1f))),
            activities = listOf(
                Activity(names[s % names.size], (strain * 0.7 * 10).toInt() / 10.0, actStart, actStart.plusMinutes(actLen)),
            ),
        )
    }
}
