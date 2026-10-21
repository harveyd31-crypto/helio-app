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

    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private const val PREFS = "helio_profile"

    fun loadProfile(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _profile.value = Profile(
            birthYear = p.getInt("birthYear", 0).takeIf { it > 1900 },
            male = if (p.contains("male")) p.getBoolean("male", true) else null,
            heightCm = p.getInt("heightCm", 0).takeIf { it > 0 },
            weightKg = p.getFloat("weightKg", 0f).takeIf { it > 0 }?.toDouble(),
        )
    }

    fun saveProfile(ctx: Context, profile: Profile) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putInt("birthYear", profile.birthYear ?: 0)
            if (profile.male != null) putBoolean("male", profile.male) else remove("male")
            putInt("heightCm", profile.heightCm ?: 0)
            putFloat("weightKg", (profile.weightKg ?: 0.0).toFloat())
        }.apply()
        _profile.value = profile
    }

    /** Reloads days from Health Connect when it is available and allowed; keeps sample data otherwise. */
    suspend fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        if (HealthConnectSource.available(app)) {
            val hc = HealthConnectSource(app)
            val allowed = try { hc.hasHeartRate() } catch (e: Exception) { false }
            if (allowed) {
                val hrMax = _profile.value.age?.let { 208 - 0.7 * it } ?: 185.0
                val loaded = try { hc.loadDays(hrMax = hrMax) } catch (e: Exception) { emptyList() }
                if (loaded.any { it.strain != null || it.sleep != null }) {
                    _days.value = loaded
                    _source.value = DataSource.HealthConnect
                }
            }
        }
        WidgetStore.saveDay(app, _days.value.last())
    }
}
