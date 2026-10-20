package app.helio.data

import android.content.Context
import app.helio.widget.WidgetStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single place the screens, the Bluetooth service and the widget read from. */
object Repository {
    private val _days = MutableStateFlow(SampleData.days())
    val days: StateFlow<List<DaySummary>> = _days.asStateFlow()

    private val _source = MutableStateFlow(DataSource.Sample)
    val source: StateFlow<DataSource> = _source.asStateFlow()

    val live = MutableStateFlow(LiveState())

    /** Reloads days from Health Connect when it is available and allowed; keeps sample data otherwise. */
    suspend fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        if (HealthConnectSource.available(app)) {
            val hc = HealthConnectSource(app)
            val allowed = try { hc.hasHeartRate() } catch (e: Exception) { false }
            if (allowed) {
                val loaded = try { hc.loadDays() } catch (e: Exception) { emptyList() }
                if (loaded.any { it.strain != null || it.sleep != null }) {
                    _days.value = loaded
                    _source.value = DataSource.HealthConnect
                }
            }
        }
        WidgetStore.saveDay(app, _days.value.last())
    }
}
