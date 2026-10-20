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
import app.helio.ui.theme.HelioTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Hinge position when half-folded, in pixels from the top of the window. */
    private val hingeTopPx = mutableStateOf<Int?>(null)
    private val tabletop = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { info ->
                val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                val half = fold != null && fold.state == FoldingFeature.State.HALF_OPENED &&
                    fold.orientation == FoldingFeature.Orientation.HORIZONTAL
                tabletop.value = half
                hingeTopPx.value = if (half) fold!!.bounds.top else null
            }
        }
        lifecycleScope.launch { Repository.refresh(this@MainActivity) }
        if (LiveHeartRateService.rememberedDevice(this) != null && LiveHeartRateService.hasPermissions(this)) {
            LiveHeartRateService.start(this)
        }

        setContent { HelioTheme { App() } }
    }

    @Composable
    private fun App() {
        val days by Repository.days.collectAsState()
        val live by Repository.live.collectAsState()
        val source by Repository.source.collectAsState()
        val scope = rememberCoroutineScope()
        var selected by remember { mutableIntStateOf(-1) }
        if (selected !in days.indices) selected = days.lastIndex
        var forceLive by remember { mutableStateOf(false) }

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

        val density = LocalDensity.current
        val liveContent: @Composable () -> Unit = {
            LiveScreen(
                live = live,
                topHeight = hingeTopPx.value?.let { with(density) { it.toDp() } },
                onClose = { forceLive = false },
                onConnect = { connect() },
                onDisconnect = { LiveHeartRateService.stop(this@MainActivity) },
                onSync = { scope.launch { Repository.refresh(this@MainActivity) } },
            )
        }

        val showLive = fold == FoldMode.Tabletop || forceLive
        AnimatedContent(
            targetState = showLive,
            transitionSpec = { (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.97f)) togetherWith fadeOut(tween(200)) },
            label = "mode",
        ) { liveMode ->
            if (liveMode) liveContent()
            else HomeScreen(
                days = days,
                selected = selected,
                onSelect = { selected = it.coerceIn(0, days.lastIndex) },
                live = live,
                source = source,
                fold = fold,
                onOpenLive = { forceLive = true },
                liveContent = liveContent,
            )
        }
    }
}
