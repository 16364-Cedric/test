package fr.dati.app

import android.location.Location
import android.location.LocationListener
import android.os.Bundle

/**
 * Crée un [LocationListener] qui implémente toutes ses méthodes : avant Android 11 elles n'ont pas
 * d'implémentation par défaut et leur absence provoque un plantage.
 */
fun locationListener(onLocation: (Location) -> Unit): LocationListener = object : LocationListener {
    override fun onLocationChanged(location: Location) = onLocation(location)

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
}
