package app.helio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import app.helio.ble.LiveHeartRateService
import app.helio.data.HealthConnectSource
import app.helio.data.Repository
import app.helio.ui.screens.FoldMode
import app.helio.ui.screens.HomeScreen
import app.helio.ui.screens.LiveScreen
import app.helio.ui.screens.ProfileDialog
import app.helio.ui.theme.HelioTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Hinge position when half-folded, in pixels from the top of the window. */
    private val hingeTopPx = mutableStateOf<Int?>(null)
    private val tabletop = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        CrashCatcher.install(this)
        CrashCatcher.take(this)?.let { crash ->
            // The last launch crashed: show what happened instead of trying again blindly.
            setContent { HelioTheme { CrashScreen(crash) { recreate() } } }
            return
        }

        lifecycleScope.launch {
            WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { info ->
                val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                val half = fold != null && fold.state == FoldingFeature.State.HALF_OPENED &&
                    fold.orientation == FoldingFeature.Orientation.HORIZONTAL
                tabletop.value = half
                hingeTopPx.value = if (half) fold!!.bounds.top else null
            }
        }
        Repository.loadProfile(this)
        Repository.loadTests(this)
        app.helio.data.Workouts.load(this)
        app.helio.data.RoundTimer.load(this)
        lifecycleScope.launch { Repository.refresh(this@MainActivity) }
        if ((LiveHeartRateService.rememberedDevice(this) != null || app.helio.strap.StrapManager.pairing(this) != null) && LiveHeartRateService.hasPermissions(this)) {
            LiveHeartRateService.start(this)
        }

        setContent { HelioTheme { App() } }
    }

    @Composable
    private fun App() {
        val days by Repository.days.collectAsState()
        val live by Repository.live.collectAsState()
        val source by Repository.source.collectAsState()
        val profile by Repository.profile.collectAsState()
        val tests by Repository.tests.collectAsState()
        val workouts by app.helio.data.Workouts.all.collectAsState()
        val activeWorkout by app.helio.data.Workouts.active.collectAsState()
        val testState by app.helio.ble.RunTestService.state.collectAsState()
        val scope = rememberCoroutineScope()
        var selected by remember { mutableIntStateOf(-1) }
        if (selected !in days.indices) selected = days.lastIndex
        var forceLive by remember { mutableStateOf(false) }
        var forceRounds by remember { mutableStateOf(false) }
        if (activeWorkout == null) forceRounds = false

        val width = LocalConfiguration.current.screenWidthDp
        val fold = when {
            tabletop.value -> FoldMode.Tabletop
            width >= 600 -> FoldMode.Open
            else -> FoldMode.Cover
        }

        val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            scope.launch { Repository.refresh(this@MainActivity) }
        }
        val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            if (res[Manifest.permission.BLUETOOTH_CONNECT] == true && res[Manifest.permission.BLUETOOTH_SCAN] == true) {
                LiveHeartRateService.start(this@MainActivity)
            }
        }
        LaunchedEffect(Unit) {
            if (HealthConnectSource.available(this@MainActivity) && !HealthConnectSource(this@MainActivity).hasHeartRate()) {
                hcLauncher.launch(HealthConnectSource.PERMISSIONS)
            }
        }

        fun connect() {
            val perms = buildList {
                add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (perms.all { ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED }) {
                LiveHeartRateService.start(this@MainActivity)
            } else btLauncher.launch(perms.toTypedArray())
        }
        var editProfile by remember { mutableStateOf(false) }
        var importStatus by remember { mutableStateOf<String?>(null) }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                importStatus = "Reading the file…"
                importStatus = try {
                    val r = app.helio.strap.ZeppImport.run(this@MainActivity, uri) { p -> importStatus = p }
                    importStatus = "Working out every day again…"
                    Repository.refresh(this@MainActivity)
                    "Imported ${r.first ?: "?"} to ${r.last ?: "?"}: ${String.format("%,d", r.samples)} readings, ${r.nights} nights, ${r.days} day totals. Days the strap already had were kept."
                } catch (e: Exception) {
                    "Import failed: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
        if (editProfile) {
            val (mac0, key0) = app.helio.strap.StrapManager.pairingText(this@MainActivity)
            ProfileDialog(profile, mac0, key0, onSave = { p, mac, key ->
                val err = app.helio.strap.StrapManager.savePairing(this@MainActivity, mac, key)
                if (err == null) {
                    Repository.saveProfile(this@MainActivity, p)
                    editProfile = false
                    if (app.helio.strap.StrapManager.pairing(this@MainActivity) != null && (mac != mac0 || key != key0)) {
                        LiveHeartRateService.stop(this@MainActivity)
                        connect()
                    }
                    scope.launch { Repository.refresh(this@MainActivity) }
                }
                err
            }, onDismiss = { editProfile = false }, importStatus = importStatus, onImport = { importLauncher.launch(arrayOf("*/*")) })
        }

        val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) app.helio.ble.RunTestService.start(this@MainActivity)
        }
        fun startTest() {
            if (app.helio.ble.RunTestService.hasPermission(this@MainActivity)) app.helio.ble.RunTestService.start(this@MainActivity)
            else locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        val density = LocalDensity.current
        val liveContent: @Composable () -> Unit = {
            LiveScreen(
                live = live,
                topHeight = hingeTopPx.value?.let { with(density) { it.toDp() } },
                onClose = { forceLive = false },
                onConnect = { connect() },
                onDisconnect = { LiveHeartRateService.stop(this@MainActivity) },
                onSync = { app.helio.strap.StrapManager.syncNow(); scope.launch { Repository.refresh(this@MainActivity) } },
            )
        }

        fun endWorkout(): app.helio.data.Workout? {
            app.helio.data.RoundTimer.closeOpenRound()
            val w = app.helio.data.Workouts.stop(this@MainActivity)
            app.helio.data.RoundTimer.reset()
            return w
        }
        // Half-folded during an activity → round timer; half-folded otherwise → live stress
        val mode = when {
            activeWorkout != null && (fold == FoldMode.Tabletop || forceRounds) -> 2
            fold == FoldMode.Tabletop || forceLive -> 1
            else -> 0
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = { (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.97f)) togetherWith fadeOut(tween(200)) },
            label = "mode",
        ) { m ->
            if (m == 2) activeWorkout?.let { a ->
                app.helio.ui.screens.RoundsScreen(
                    live = live, active = a,
                    hrMax = app.helio.logic.Vo2Max.hrMax(profile, days, java.time.LocalDate.now()).first,
                    topHeight = if (fold == FoldMode.Tabletop) hingeTopPx.value?.let { with(density) { it.toDp() } } else null,
                    onClose = { forceRounds = false },
                    onEnd = { endWorkout(); forceRounds = false },
                )
            }
            else if (m == 1) liveContent()
            else HomeScreen(
                days = days,
                selected = selected,
                onSelect = { selected = it.coerceIn(0, days.lastIndex) },
                live = live,
                source = source,
                fold = fold,
                profile = profile,
                onEditProfile = { editProfile = true },
                tests = tests,
                testState = testState,
                onStartTest = { startTest() },
                onCancelTest = { app.helio.ble.RunTestService.cancel(this@MainActivity) },
                workouts = workouts,
                activeWorkout = activeWorkout,
                onStartWorkout = { t ->
                    if (live.status != app.helio.data.LinkStatus.Live) connect()
                    app.helio.data.Workouts.start(t)
                },
                onEndWorkout = { endWorkout() },
                onDiscardWorkout = { app.helio.data.RoundTimer.reset(); app.helio.data.Workouts.discard() },
                onDeleteWorkout = { app.helio.data.Workouts.delete(this@MainActivity, it) },
                onSimulateTest = {
                    val today = java.time.LocalDate.now()
                    val hrMax = app.helio.logic.Vo2Max.hrMax(profile, days, today).first
                    Repository.addTest(this@MainActivity, app.helio.logic.RunTest.simulate(hrMax, today.toEpochDay()))
                },
                onOpenLive = { forceLive = true },
                onOpenRounds = { forceRounds = true },
                liveContent = liveContent,
            )
        }
    }
}

