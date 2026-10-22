package fr.dati.app

import fr.dati.core.Beacons
import fr.dati.core.GeoPoint
import fr.dati.core.RiskZone
import fr.dati.core.ZoneShape
import org.json.JSONArray
import org.json.JSONObject

/** Conversion des zones à risque reçues du serveur (format JSON de la console). */
object ZoneCodec {
    /** Les zones mal formées sont ignorées plutôt que de faire échouer tout le lot. */
    fun parse(json: String): List<RiskZone> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i -> runCatching { zone(array.getJSONObject(i)) }.getOrNull() }
    }

    private fun zone(o: JSONObject): RiskZone {
        val shape = when (o.getString("type")) {
            "circle" -> ZoneShape.Circle(point(o.getJSONObject("center")), o.getDouble("radius"))
            "polygon" -> {
                val points = o.getJSONArray("points")
                ZoneShape.Polygon((0 until points.length()).map { point(points.getJSONObject(it)) })
            }
            else -> throw IllegalArgumentException("Type de zone inconnu")
        }
        val beacons = o.optJSONArray("beacons")
        return RiskZone(
            id = o.getString("id"),
            name = o.getString("name"),
            shape = shape,
            maxDwellMs = (o.getDouble("maxMinutes") * 60_000).toLong(),
            beacons = if (beacons == null) emptySet() else {
                (0 until beacons.length()).mapNotNullTo(mutableSetOf()) { Beacons.normalize(beacons.getString(it)) }
            },
            minRssi = o.optInt("minRssi", -75),
        )
    }

    private fun point(o: JSONObject) = GeoPoint(o.getDouble("lat"), o.getDouble("lon"))
}
