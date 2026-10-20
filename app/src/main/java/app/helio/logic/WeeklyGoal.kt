package app.helio.logic

import app.helio.data.DaySummary
import app.helio.data.WeekGoal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/**
 * Weekly strain goal.
 *
 * Set from the four weeks before this Monday (your usual weekly load), nudged by the last seven
 * days of recovery: well recovered → up to +10 %, run down → down to −30 %. The ring's full circle is
 * 1.25 × the goal so going over the goal still shows. Pace = where you would be by today if you
 * spread the goal evenly over the week.
 */
object WeeklyGoal {
    private const val DEFAULT_WEEKLY = 90.0
    private const val MIN_GOAL = 50.0

    fun compute(days: List<DaySummary>, selected: LocalDate): WeekGoal {
        val monday = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val byDate = days.associateBy { it.date }

        val chronicDays = (1..28).mapNotNull { byDate[monday.minusDays(it.toLong())]?.strain }
        val chronicWeekly = if (chronicDays.size >= 7) chronicDays.sum() / chronicDays.size * 7 else DEFAULT_WEEKLY

        val recent = (0..6).mapNotNull { byDate[selected.minusDays(it.toLong())]?.recovery }
        val avgRec = if (recent.isNotEmpty()) recent.average() else 60.0
        val factor = 0.9 + 0.2 * ((avgRec - 50.0) / 50.0).coerceIn(-1.0, 1.0)

        val goal = (chronicWeekly * factor).coerceAtLeast(MIN_GOAL).roundToInt().toDouble()
        val dayIndex = (selected.toEpochDay() - monday.toEpochDay()).toInt()
        val daily = (0..6).map { k -> if (k <= dayIndex) byDate[monday.plusDays(k.toLong())]?.strain ?: 0.0 else null }
        val cumulative = daily.filterNotNull().sum()
        val pace = goal * (dayIndex + 1) / 7.0
        val left = (goal - cumulative).coerceAtLeast(0.0)
        val perDay = if (dayIndex < 6) left / (6 - dayIndex) else 0.0

        return WeekGoal(
            goal = goal,
            scale = goal * 1.25,
            cumulative = cumulative,
            pace = pace,
            dayIndex = dayIndex,
            daily = daily,
            perDayLeft = perDay,
        )
    }
}
