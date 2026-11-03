package id.tege.video

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import id.tege.video.core.GeoFix
import id.tege.video.core.PlaceName
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Position for the location stamp. Uses the phone's own location service (GPS and network), no extra libraries.
 * The place name comes from the phone's address lookup and needs internet; without it the stamp shows coordinates.
 * Only runs while the stamp is switched on and the app is open.
 */
internal class LocationSource(private val context: Context, private val io: Executor) {
    private val manager: LocationManager? = context.getSystemService(LocationManager::class.java)
    @Volatile private var best: Location? = null
    @Volatile var place: String? = null; private set
    /** True when a place name was wanted but the address lookup failed or is not available on this phone. */
    @Volatile var placeUnavailable = false; private set
    private var running = false
    private var attemptedFor: Location? = null
    private var placeFor: Location? = null
    private var geocodeTriedAt = 0L
    private var geocoding = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) { accept(location); lookUpPlace() }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** True when the phone's Location switch is on. */
    fun providerOn(): Boolean = runCatching { manager?.isLocationEnabled == true }.getOrDefault(false)

    /** The permission is checked by [hasPermission] just below; lint cannot see through that helper. */
    @SuppressLint("MissingPermission")
    @Synchronized fun start() {
        val m = manager ?: return
        if (running || !hasPermission()) return
        running = true
        runCatching {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (!m.isProviderEnabled(provider)) continue
                m.getLastKnownLocation(provider)?.let { accept(it) }
                m.requestLocationUpdates(provider, 2_000L, 0f, listener, Looper.getMainLooper())
            }
        }.onFailure { running = false }
        lookUpPlace()
    }

    @Synchronized fun stop() {
        if (!running) return
        running = false
        runCatching { manager?.removeUpdates(listener) }
    }

    /** The best known position with its age right now, or null when none was received yet. */
    fun fix(): GeoFix? {
        val l = best ?: return null
        val ageMs = ((SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)
        return GeoFix(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else null, ageMs)
    }

    private fun accept(new: Location) {
        val cur = best
        if (cur == null) { best = new; return }
        val dt = (new.elapsedRealtimeNanos - cur.elapsedRealtimeNanos) / 1_000_000L
        if (dt < -30_000L) return
        val accNew = if (new.hasAccuracy()) new.accuracy else Float.MAX_VALUE
        val accCur = if (cur.hasAccuracy()) cur.accuracy else Float.MAX_VALUE
        // A fresher fix wins unless it is clearly less exact than a recent one.
        if (accNew <= accCur || dt > 8_000L) best = new
    }

    /**
     * Looks up the place name when the position moved about 75 m since the last try, or the last try is old
     * (30 seconds after a failure, 2 minutes after a success). Runs off the main thread.
     */
    @Suppress("DEPRECATION")
    private fun lookUpPlace() {
        val l = best ?: return
        val now = SystemClock.elapsedRealtime()
        val tried = attemptedFor
        val moved = tried == null || l.distanceTo(tried) > 75f
        val wait = if (place == null) 30_000L else 120_000L
        if (geocoding || (!moved && now - geocodeTriedAt < wait)) return
        if (!Geocoder.isPresent()) { placeUnavailable = true; return }
        geocoding = true; geocodeTriedAt = now; attemptedFor = l
        io.execute {
            val result: List<Address>? = try {
                Geocoder(context, Locale.forLanguageTag("id-ID")).getFromLocation(l.latitude, l.longitude, 1)
            } catch (e: Exception) { null }
            val a = result?.firstOrNull()
            val name = a?.let { PlaceName.build(it.thoroughfare, it.subLocality, it.locality, it.subAdminArea, it.adminArea) }
            if (name != null) { place = name; placeUnavailable = false; placeFor = l }
            else {
                placeUnavailable = true
                // An old name for a position far from here would mislead, so it is dropped.
                val from = placeFor
                if (from == null || l.distanceTo(from) > 200f) place = null
            }
            geocoding = false
        }
    }
}
