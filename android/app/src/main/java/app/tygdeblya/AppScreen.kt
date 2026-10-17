package app.tygdeblya

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AppScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()

    val wtfay by vm.wtfay.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val look by vm.look.collectAsStateWithLifecycle()
    val layers by vm.layers.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val meWater by vm.meWater.collectAsStateWithLifecycle()
    val nemo by vm.nemo.collectAsStateWithLifecycle()

    var controller by remember { mutableStateOf<MapController?>(null) }
    var granted by remember { mutableStateOf(vm.locator.allowed()) }
    var searchFocused by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf<Selection?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        granted = res.values.any { it }
        if (granted) vm.locateMe() else vm.toastLater("Без геолокации никак, бля")
    }

    fun here() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        focus.clearFocus()
        vm.clearSearch()
        if (granted) vm.locateMe()
        else permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    // keep the blue dot fresh while the app is on screen
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.locator.updates().collect { vm.onLocation(it) }
        }
    }

    LaunchedEffect(Unit) { vm.toast.collect { toast = it } }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2600); toast = null
        }
    }
    LaunchedEffect(selection?.square) {
        if (selection?.origin == Origin.ME) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    // push state into the map
    LaunchedEffect(controller, look) { controller?.setLook(look) }
    LaunchedEffect(controller, layers) { controller?.setLayers(layers) }
    LaunchedEffect(controller, wtfay) { controller?.setWtfay(wtfay) }
    LaunchedEffect(controller, selection) { controller?.setSelection(selection?.square) }
    LaunchedEffect(controller, saved) { controller?.setSaved(saved) }
    LaunchedEffect(controller, me) { controller?.setMe(me?.let { Geo(it.latitude, it.longitude) }, me?.accuracy ?: 0f) }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        vm.camera.collect { c.flyTo(it.geo, it.zoom); vm.cameraDone() }
    }

    // sea easter eggs: what water the selected square and you are in
    LaunchedEffect(controller, selection?.square) {
        val c = controller ?: return@LaunchedEffect
        val sel = selection ?: return@LaunchedEffect
        // wait for the camera to fly there, then once more for the tiles
        if (sel.origin != Origin.TAP) delay(1700)
        repeat(2) {
            c.waterAt(sel.square.center)?.let { vm.setWater(sel.square, it); return@LaunchedEffect }
            delay(1200)
        }
    }
    LaunchedEffect(controller, me) {
        val c = controller ?: return@LaunchedEffect
        val g = me?.let { Geo(it.latitude, it.longitude) } ?: return@LaunchedEffect
        if (c.onScreen(g)) vm.setMeWater(c.waterAt(g))
    }

    // shake: a storm rocks the map and the sea has something to say
    ShakeListener {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        toast = "🌊 " + Sea.SAYINGS.random()
        controller?.let { c -> scope.launch { c.storm() } }
    }

    BackHandler(enabled = nemo) { vm.closeNemo() }
    BackHandler(enabled = searchFocused || search.query.isNotEmpty()) {
        vm.clearSearch(); focus.clearFocus()
    }
    BackHandler(enabled = !searchFocused && search.query.isEmpty() && selection != null) { vm.clearSelection() }

    val topInset = WindowInsets.statusBars.getTop(density)
    val bottomInset = WindowInsets.navigationBars.getBottom(density)
    val heading = rememberHeading(enabled = selection != null && me != null)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > PANEL_WIDTH + 200.dp
        MapHost(
            modifier = Modifier.fillMaxSize(),
            topInset = topInset,
            bottomInset = bottomInset,
            onTap = { geo ->
                focus.clearFocus()
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                vm.onMapTap(geo)
            },
            onReady = { c -> controller = c },
        )

        // right-hand tools
        Column(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 80.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassIconButton(Icons.Rounded.Layers, "Слои") { showLayers = true }
            GlassIconButton(Icons.Rounded.Star, "Мои места", tint = Brand.Gold) { showSaved = true }
        }

        // bottom: the big button or the address card; on tablets a 560 dp column at the start
        Column(
            Modifier.align(if (wide) Alignment.BottomStart else Alignment.BottomCenter)
                .then(if (wide) Modifier.width(PANEL_WIDTH) else Modifier.fillMaxWidth())
                .navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = selection != null,
                modifier = Modifier.align(Alignment.End),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                SmallHereButton(locating, onClick = ::here, modifier = Modifier.padding(bottom = 10.dp, end = 2.dp))
            }
            AnimatedVisibility(
                visible = selection == null && !searchFocused,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                HereButton(locating, onWater = meWater?.sailable == true, onClick = ::here)
            }
            AnimatedVisibility(
                visible = selection != null,
                enter = slideInVertically(tween(380)) { it } + fadeIn(),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(),
            ) {
                // keep the last selection on screen while the card slides away
                var last by remember { mutableStateOf(selection) }
                if (selection != null) last = selection
                last?.let { sel ->
                    AddressCard(
                        sel = sel,
                        saved = saved.any { it.words == sel.square.text },
                        distance = me?.let { distanceTo(it, sel.square.center) },
                        arrow = me?.let { bearingTo(it, sel.square.center) - (heading ?: controller?.bearing?.toFloat() ?: 0f) },
                        onClose = vm::clearSelection,
                        onCopy = {
                            copy(context, "///${sel.square.text}")
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            toast = "Скопировал ///${sel.square.text}"
                        },
                        onShare = { share(context, vm.shareText(sel)) },
                        onRoute = { route(context, sel.square) },
                        onSpeak = { vm.speak(sel.square) },
                        onStar = {
                            if (saved.any { it.words == sel.square.text }) vm.unsave(sel.square.text) else saving = sel
                        },
                        onFocus = { controller?.flyTo(sel.square.center, 19.0) },
                    )
                }
            }
        }

        // search on top of everything else
        SearchPanel(
            state = search,
            ready = wtfay != null,
            onQuery = vm::onQuery,
            onPick = { item -> focus.clearFocus(); vm.pick(item) },
            onClear = { vm.clearSearch() },
            onFocus = { focused ->
                searchFocused = focused
                if (focused) controller?.let { vm.mapCenter = it.center }
            },
            modifier = Modifier.align(if (wide) Alignment.TopStart else Alignment.TopCenter)
                .then(if (wide) Modifier.width(PANEL_WIDTH) else Modifier)
                .statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        )

        Toast(toast, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 84.dp))

        // dark status bar icons over light maps
        val view = LocalView.current
        LaunchedEffect(look) {
            val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !look.dark
        }

        NemoOverlay(nemo, onClose = vm::closeNemo)

        LoadingOverlay(visible = wtfay == null)
    }

    if (showLayers) LayersSheet(look, layers, onLook = vm::setLook, onToggle = vm::toggleLayer) { showLayers = false }
    if (showSaved) SavedSheet(
        saved, me,
        onOpen = { showSaved = false; vm.open(it) },
        onDelete = { vm.unsave(it.words) },
    ) { showSaved = false }
    saving?.let { sel ->
        SaveDialog(suggested = sel.near?.substringBefore(',') ?: "", onSave = { name -> vm.save(sel.square, name); saving = null }) {
            saving = null
        }
    }
}

private val PANEL_WIDTH = 560.dp

// ── helpers ─────────────────────────────────────────────────────────────────

fun distanceTo(from: Location, to: Geo): Float {
    val out = FloatArray(1)
    Location.distanceBetween(from.latitude, from.longitude, to.lat, to.lon, out)
    return out[0]
}

fun bearingTo(from: Location, to: Geo): Float {
    val out = FloatArray(2)
    Location.distanceBetween(from.latitude, from.longitude, to.lat, to.lon, out)
    return out[1]
}

fun formatDistance(m: Float): String = when {
    m < 1000 -> "${m.toInt()} м"
    m < 100_000 -> "%.1f км".format(Locale.forLanguageTag("ru"), m / 1000)
    else -> "${(m / 1000).toInt()} км"
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Адрес", text))
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Кинуть адрес"))
}

private fun route(context: Context, sq: Square) {
    val c = sq.center
    val ll = "%.6f,%.6f".format(Locale.US, c.lat, c.lon)
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$ll?q=$ll(${Uri.encode("///" + sq.text)})"))
    runCatching { context.startActivity(Intent.createChooser(geo, "Чем ехать?")) }
}

/** Compass heading in degrees while [enabled], or null without a sensor. */
@Composable
private fun rememberHeading(enabled: Boolean): Float? {
    val context = LocalContext.current
    var heading by remember { mutableFloatStateOf(Float.NaN) }
    DisposableEffect(enabled) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!enabled || sensor == null) return@DisposableEffect onDispose {}
        val rot = FloatArray(9)
        val orient = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, orient)
                val deg = Math.toDegrees(orient[0].toDouble()).toFloat()
                // light smoothing across the ±180 seam
                heading = if (heading.isNaN()) deg else {
                    var d = deg - heading
                    if (d > 180) d -= 360
                    if (d < -180) d += 360
                    heading + d * 0.15f
                }
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return heading.takeUnless { it.isNaN() }
}
