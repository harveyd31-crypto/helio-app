package app.helio.data

import app.helio.logic.Strain
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.sin

/** Demo days shown until Health Connect has data. Deterministic, so screenshots stay stable. */
object SampleData {
    private fun sampleSleep(date: LocalDate, bed: LocalTime, wake: LocalTime, asleep: Int, s: Int): SleepInfo {
        val zone = java.time.ZoneId.systemDefault()
        val bedDate = if (bed.hour >= 12) date.minusDays(1) else date
        val start = bedDate.atTime(bed).atZone(zone).toInstant().toEpochMilli()
        val end = date.atTime(wake).atZone(zone).toInstant().toEpochMilli()
        val blocks = mutableListOf<StageBlock>()
        var t = start
        fun add(min: Int, st: Stage) { if (min <= 0 || t >= end) return; val e = minOf(end, t + min * 60_000L); blocks += StageBlock(t, e, st); t = e }
        add(12, Stage.Light)
        var cycle = 0
        while (t < end) {
            add(listOf(42, 30, 16, 6, 0, 0)[cycle.coerceAtMost(5)] + (s % 4) * 2, Stage.Deep)
            add(22 + (cycle * 3 + s) % 10, Stage.Light)
            add(listOf(10, 18, 24, 30, 34, 36)[cycle.coerceAtMost(5)], Stage.Rem)
            if ((cycle + s) % 3 == 0) add(3 + s % 4, Stage.Awake)
            add(14, Stage.Light)
            cycle++
        }
        val hr = mutableListOf<Pair<Long, Int>>()
        val resp = mutableListOf<Pair<Long, Double>>()
        var k = 0
        var m = start
        while (m < end) {
            val st = blocks.firstOrNull { m >= it.start && m < it.end }?.stage ?: Stage.Light
            val base = when (st) { Stage.Deep -> 50; Stage.Light -> 54; Stage.Rem -> 59; Stage.Awake -> 64 }
            hr += m to (base + 3 * sin(k * 0.9 + s) + 2 * sin(k * 2.3)).toInt()
            resp += m to 14.6 + (if (st == Stage.Rem) 0.9 else 0.0) + 0.4 * sin(k * 0.7 + s)
            m += 3 * 60_000L; k++
        }
        return SleepInfo(
            minutesAsleep = asleep, bed = bed, wake = wake,
            bedDayLabel = bedDate.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
            startMs = start, endMs = end, stages = blocks, hr = hr, resp = resp,
        )
    }

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
            sleep = sampleSleep(date, bed, wake, asleep, s),
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
            steps = (7200 + 2600 * sin(s * 0.7)).toInt(),
            moderateMin = (28 + 18 * sin(s * 1.1)).toInt().coerceAtLeast(0),
            vigorousMin = (8 + 8 * sin(s * 0.9 + 1)).toInt().coerceAtLeast(0),
            strengthMin = if (names[s % names.size] == "Strength") actLen.toInt() else 0,
            maxHr = (168 + 12 * sin(s * 0.9 + 1)).toInt(),
            runs = if (names[s % names.size] == "Running") listOf(
                RunEffort(actLen.toDouble(), 2.85 + 0.12 * sin(s * 2.1) + (35 - seed) * 0.004, 148 + 5 * sin(s * 1.3)),
            ) else emptyList(),
        )
    }
}
