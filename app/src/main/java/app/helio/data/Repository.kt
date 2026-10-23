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

    private val _tests = MutableStateFlow<List<TestResult>>(emptyList())
    val tests: StateFlow<List<TestResult>> = _tests.asStateFlow()

    fun loadTests(ctx: Context) {
        val raw = ctx.getSharedPreferences("helio_tests", Context.MODE_PRIVATE).getString("tests", "[]") ?: "[]"
        _tests.value = try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val st = o.getJSONArray("stages")
                TestResult(
                    dateEpochDay = o.getLong("day"),
                    vo2max = if (o.isNull("vo2")) null else o.getDouble("vo2"),
                    r2 = o.getDouble("r2"),
                    stages = (0 until st.length()).map { k -> st.getJSONArray(k).let { Triple(it.getDouble(0), it.getDouble(1), it.getDouble(2)) } },
                    distanceM = o.getDouble("dist"),
                    problems = o.getJSONArray("problems").let { a -> (0 until a.length()).map { a.getString(it) } },
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun addTest(ctx: Context, t: TestResult) {
        val all = _tests.value + t
        val arr = org.json.JSONArray()
        all.forEach { r ->
            arr.put(org.json.JSONObject().apply {
                put("day", r.dateEpochDay); put("vo2", r.vo2max ?: org.json.JSONObject.NULL); put("r2", r.r2); put("dist", r.distanceM)
                put("stages", org.json.JSONArray().apply { r.stages.forEach { (a, b, c) -> put(org.json.JSONArray().put(a).put(b).put(c)) } })
                put("problems", org.json.JSONArray().apply { r.problems.forEach { put(it) } })
            })
        }
        ctx.getSharedPreferences("helio_tests", Context.MODE_PRIVATE).edit().putString("tests", arr.toString()).apply()
        _tests.value = all
    }

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
        // 1. Direct from the strap (Ridge protocol + science), when paired and synced
        if (app.helio.strap.StrapScience.hasData(app)) {
            val d = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { app.helio.strap.StrapScience.days(app, _profile.value) }
            } catch (e: Exception) { android.util.Log.w("helio", "strap science failed", e); emptyList() }
            if (d.isNotEmpty()) {
                _days.value = d
                _source.value = DataSource.Strap
                WidgetStore.saveDay(app, d.last())
                return
            }
        }
        // 2. Health Connect (what Zepp writes), 3. sample data
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
