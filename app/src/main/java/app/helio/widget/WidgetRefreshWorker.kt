package app.helio.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.helio.data.Repository
import app.helio.data.Workouts
import app.helio.strap.StrapScience
import java.util.concurrent.TimeUnit

/**
 * Safety net for the widget: every 30 minutes, even if the strap connection was stopped by the
 * phone, the widget is redrawn from what the phone holds. Light: only the last five weeks are
 * worked out, and only days with new data are re-derived.
 */
class WidgetRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        Repository.loadProfile(app)
        Workouts.load(app)
        try {
            if (StrapScience.hasData(app)) {
                val days = StrapScience.days(app, Repository.profile.value, 35)
                if (days.isNotEmpty()) WidgetStore.saveDays(app, days)
            } else Repository.refresh(app)
        } catch (e: Exception) {
            android.util.Log.w("helio", "widget refresh failed", e)
        }
        WidgetStore.refreshWidgets(app)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "helio-widget", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build(),
            )
        }
    }
}
