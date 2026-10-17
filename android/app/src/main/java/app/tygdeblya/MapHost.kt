package app.tygdeblya

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

/** MapLibre's MapView inside Compose; hands out a [MapController] once the map is ready. */
@Composable
fun MapHost(
    modifier: Modifier,
    topInset: Int,
    bottomInset: Int,
    onTap: (Geo) -> Unit,
    onReady: (MapController) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    var controller by remember { mutableStateOf<MapController?>(null) }

    val mapView = remember {
        val options = MapLibreMapOptions.createFromAttributes(context)
            .camera(CameraPosition.Builder().target(LatLng(55.7539, 37.6208)).zoom(10.5).build())
            .textureMode(false)
        MapView(context, options).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                val c = MapController(map, scope, onTap)
                controller = c
                onReady(c)
            }
        }
    }

    LaunchedEffect(controller, topInset, bottomInset) {
        controller?.setInsets(topInset, bottomInset, density)
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
