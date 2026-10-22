package fr.dati.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lon: Double)

sealed interface ZoneShape {
    data class Circle(val center: GeoPoint, val radiusM: Double) : ZoneShape

    /** Polygone simple, sommets dans l'ordre (sens indifférent). */
    data class Polygon(val points: List<GeoPoint>) : ZoneShape
}

/**
 * Zone à risque (ATEX…) où la durée de présence est limitée.
 *
 * Si des balises Bluetooth sont associées à la zone ([beacons]), la présence est déterminée par
 * elles seules (zones intérieures, où le GPS ne fonctionne pas) ; la forme ne sert alors qu'à
 * l'affichage sur la carte.
 */
data class RiskZone(
    val id: String,
    val name: String,
    val shape: ZoneShape,
    val maxDwellMs: Long,
    val beacons: Set<String> = emptySet(),
    val minRssi: Int = -75,
) {
    val usesBeacons: Boolean get() = beacons.isNotEmpty()

    fun contains(point: GeoPoint): Boolean = when (shape) {
        is ZoneShape.Circle -> Geo.distanceM(shape.center, point) <= shape.radiusM
        is ZoneShape.Polygon -> Geo.polygonContains(shape.points, point)
    }
}

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceAtMost(1.0)))
    }

    /**
     * Test du rayon (ray casting) dans un repère local en mètres centré sur le point : la
     * projection équirectangulaire est précise à l'échelle d'un site industriel.
     */
    fun polygonContains(points: List<GeoPoint>, point: GeoPoint): Boolean {
        if (points.size < 3) return false
        val cosLat = cos(Math.toRadians(point.lat))
        fun x(p: GeoPoint) = (p.lon - point.lon) * cosLat
        fun y(p: GeoPoint) = p.lat - point.lat
        var inside = false
        var j = points.lastIndex
        for (i in points.indices) {
            val xi = x(points[i])
            val yi = y(points[i])
            val xj = x(points[j])
            val yj = y(points[j])
            if ((yi > 0) != (yj > 0) && 0 < (xj - xi) * (0 - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}
