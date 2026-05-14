package com.safeguard.app.location

import android.content.Context
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import java.util.Locale

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val accuracy: Float = 0f,
    val isOffline: Boolean = false
)

class LocationManager(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastLocation: LocationData? = null

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 10_000L
    ).apply {
        setMinUpdateIntervalMillis(5_000L)
        setWaitForAccurateLocation(false)
    }.build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                val offline = !isInternetAvailable()
                val address = if (!offline) {
                    reverseGeocode(loc.latitude, loc.longitude)
                } else null
                lastLocation = LocationData(
                    loc.latitude, loc.longitude,
                    address, loc.accuracy, offline
                )
                Log.d("LocationManager", "📍 Location updated: ${loc.latitude}, ${loc.longitude} offline=$offline")
            }
        }
    }

    fun start() {
        try {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    val offline = !isInternetAvailable()
                    val address = if (!offline) reverseGeocode(it.latitude, it.longitude) else null
                    lastLocation = LocationData(it.latitude, it.longitude, address, it.accuracy, offline)
                }
            }
            fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("LocationManager", "Permission not granted")
        }
    }

    fun stop() = fusedClient.removeLocationUpdates(locationCallback)

    fun getLastLocation(): LocationData? = lastLocation

    fun isInternetAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun reverseGeocode(lat: Double, lng: Double): String? {
        return try {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(lat, lng, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        } catch (e: Exception) {
            null
        }
    }
}