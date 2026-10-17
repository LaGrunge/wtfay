package app.tygdeblya

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Where the phone is, via the platform LocationManager (no Play services needed). */
class Locator(private val context: Context) {
    private val lm = context.getSystemService(LocationManager::class.java)

    fun allowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    fun enabled() = LocationManagerCompat.isLocationEnabled(lm)

    private fun provider(): String = when {
        Build.VERSION.SDK_INT >= 31 && lm.hasProvider(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        else -> LocationManager.NETWORK_PROVIDER
    }

    /** A fresh fix, or the last known one if a fresh fix takes too long. */
    @SuppressLint("MissingPermission")
    suspend fun current(): Location? {
        if (!allowed()) return null
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, provider(), signal, ContextCompat.getMainExecutor(context)) {
                    if (cont.isActive) cont.resume(it)
                }
            }
        }
        return fresh ?: lastKnown()
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? = lm.getProviders(true)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }

    /** Location updates while collected. */
    @SuppressLint("MissingPermission")
    fun updates(): Flow<Location> = callbackFlow {
        if (!allowed()) {
            close(); return@callbackFlow
        }
        lastKnown()?.let { trySend(it) }
        val listener = LocationListenerCompat { trySend(it) }
        val request = LocationRequestCompat.Builder(2_000).setMinUpdateDistanceMeters(1f)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY).build()
        LocationManagerCompat.requestLocationUpdates(lm, provider(), request, listener, context.mainLooper)
        awaitClose { LocationManagerCompat.removeUpdates(lm, listener) }
    }
}
