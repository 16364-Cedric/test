package fr.dati.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import fr.dati.app.databinding.ActivityMapBinding
import fr.dati.app.databinding.DialogZoneBinding
import fr.dati.core.DatiState
import fr.dati.core.RiskZone
import fr.dati.core.ZoneShape
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.util.UUID
import fr.dati.core.GeoPoint as ZonePoint
import org.osmdroid.util.GeoPoint as MapPoint

/**
 * Carte de la position du salarié et des zones à risque (serveur et téléphone).
 * Un appui long sur la carte crée une zone circulaire sur le téléphone.
 */
class MapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMapBinding
    private lateinit var settings: DatiSettings
    private lateinit var myLocation: MyLocationNewOverlay

    private val zoneOverlays = mutableMapOf<String, Polygon>()
    private var zones: List<RiskZone> = emptyList()
    private var centered = false

    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            renderStatus()
            handler.postDelayed(this, 1_000)
        }
    }

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startLocation() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
            setUserAgentValue(packageName)
            setOsmdroidBasePath(File(cacheDir, "osmdroid"))
            setOsmdroidTileCache(File(cacheDir, "osmdroid/tiles"))
        }
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        settings = DatiSettings(this)

        val map = binding.map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        map.controller.setZoom(17.0)
        map.controller.setCenter(initialCenter())

        map.overlays.add(
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: MapPoint?) = false

                override fun longPressHelper(p: MapPoint?): Boolean {
                    p?.let(::askNewZone)
                    return true
                }
            })
        )
        myLocation = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        map.overlays.add(myLocation)

        binding.centerButton.setOnClickListener {
            val here = myLocation.myLocation
            if (here == null) {
                Toast.makeText(this, R.string.map_no_position, Toast.LENGTH_SHORT).show()
            } else {
                map.controller.setZoom(18.0)
                map.controller.animateTo(here)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        drawZones()
        if (hasLocationPermission()) startLocation() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        myLocation.disableMyLocation()
        binding.map.onPause()
        super.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startLocation() {
        myLocation.enableMyLocation()
        myLocation.runOnFirstFix {
            runOnUiThread {
                val here = myLocation.myLocation
                if (!centered && here != null) {
                    centered = true
                    binding.map.controller.animateTo(here)
                }
            }
        }
    }

    /** Dernière position connue, sinon première zone, sinon la France. */
    private fun initialCenter(): MapPoint {
        AlertSender(this, settings, EventLog(this)).lastKnownLocation()?.let { return MapPoint(it.latitude, it.longitude) }
        settings.allZones().firstOrNull()?.let { return MapPoint(center(it).lat, center(it).lon) }
        binding.map.controller.setZoom(6.0)
        return MapPoint(46.6, 2.4)
    }

    private fun center(zone: RiskZone): ZonePoint = when (val shape = zone.shape) {
        is ZoneShape.Circle -> shape.center
        is ZoneShape.Polygon -> ZonePoint(shape.points.map { it.lat }.average(), shape.points.map { it.lon }.average())
    }

    private fun drawZones() {
        val map = binding.map
        map.overlays.removeAll(zoneOverlays.values.toSet())
        zoneOverlays.clear()
        zones = settings.allZones()
        val localIds = ZoneCodec.parse(settings.localZonesJson).map { it.id }.toSet()
        for (zone in zones) {
            val polygon = Polygon(map)
            polygon.setPoints(
                when (val shape = zone.shape) {
                    is ZoneShape.Circle -> Polygon.pointsAsCircle(MapPoint(shape.center.lat, shape.center.lon), shape.radiusM)
                    is ZoneShape.Polygon -> shape.points.map { MapPoint(it.lat, it.lon) }
                }
            )
            polygon.fillPaint.color = ZONE_FILL
            polygon.outlinePaint.color = ZONE_OUTLINE
            polygon.outlinePaint.strokeWidth = 5f
            polygon.setTitle(zone.name)
            polygon.setOnClickListener { _, _, _ ->
                showZone(zone, zone.id in localIds)
                true
            }
            zoneOverlays[zone.id] = polygon
        }
        // Les zones sous la position, au-dessus de la couche qui capte l'appui long.
        map.overlays.addAll(1, zoneOverlays.values)
        map.invalidate()
        renderStatus()
    }

    private fun renderStatus() {
        val fix = myLocation.lastFix
        val point = fix?.takeIf { it.accuracy <= MAX_ACCURACY_M }?.let { ZonePoint(it.latitude, it.longitude) }
        val presence = DatiStatus.zonePresence?.takeIf { DatiStatus.state.value != DatiState.Idle }

        // Zones où se trouve le salarié : décompte du service, sinon calcul local d'après le GPS.
        val inside = zones.filter { zone ->
            zone.name == presence?.name || (!zone.usesBeacons && point != null && zone.contains(point))
        }
        for ((id, polygon) in zoneOverlays) {
            polygon.outlinePaint.color = if (inside.any { it.id == id }) ZONE_INSIDE else ZONE_OUTLINE
        }
        binding.map.invalidate()

        val lines = mutableListOf<String>()
        lines += fix?.let { getString(R.string.map_accuracy, it.accuracy.toInt()) } ?: getString(R.string.map_searching)
        lines += when {
            presence != null -> getString(
                R.string.zone_status,
                presence.name,
                formatDuration(DatiService.now() - presence.sinceMs),
                formatDuration(presence.maxDwellMs),
            )
            inside.isNotEmpty() && DatiStatus.state.value == DatiState.Idle ->
                getString(R.string.map_inside_idle, inside.first().name)
            inside.isNotEmpty() -> getString(R.string.map_inside, inside.first().name)
            else -> getString(R.string.map_outside)
        }
        val local = ZoneCodec.parse(settings.localZonesJson).size
        lines += getString(R.string.map_zone_count, zones.size, zones.size - local, local)
        binding.statusText.text = lines.joinToString("\n")
    }

    private fun askNewZone(at: MapPoint) {
        val form = DialogZoneBinding.inflate(layoutInflater)
        form.radiusInput.setText("30")
        form.minutesInput.setText("5")
        AlertDialog.Builder(this)
            .setTitle(R.string.map_new_zone)
            .setView(form.root)
            .setPositiveButton(R.string.settings_save) { _, _ ->
                val name = form.nameInput.text.toString().trim()
                val radius = form.radiusInput.text.toString().toDoubleOrNull()
                val minutes = form.minutesInput.text.toString().toIntOrNull()
                if (name.isEmpty() || radius == null || radius !in 5.0..2000.0 || minutes == null || minutes !in 1..480) {
                    Toast.makeText(this, R.string.map_zone_invalid, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val zone = RiskZone(
                    id = "local-${UUID.randomUUID()}",
                    name = name,
                    shape = ZoneShape.Circle(ZonePoint(at.latitude, at.longitude), radius),
                    maxDwellMs = minutes * 60_000L,
                )
                saveLocalZones(ZoneCodec.parse(settings.localZonesJson) + zone)
                EventLog(this).add("Zone à risque créée sur le téléphone : $name")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showZone(zone: RiskZone, isLocal: Boolean) {
        val details = listOf(
            getString(R.string.map_zone_max, zone.maxDwellMs / 60_000),
            getString(if (zone.usesBeacons) R.string.map_zone_beacons else R.string.map_zone_gps),
            getString(if (isLocal) R.string.map_zone_local else R.string.map_zone_server),
        ).joinToString("\n")
        val dialog = AlertDialog.Builder(this)
            .setTitle(zone.name)
            .setMessage(details)
            .setPositiveButton(android.R.string.ok, null)
        if (isLocal) {
            dialog.setNegativeButton(R.string.map_zone_delete) { _, _ ->
                saveLocalZones(ZoneCodec.parse(settings.localZonesJson).filter { it.id != zone.id })
                EventLog(this).add("Zone à risque supprimée du téléphone : ${zone.name}")
            }
        }
        dialog.show()
    }

    private fun saveLocalZones(local: List<RiskZone>) {
        settings.localZonesJson = ZoneCodec.encode(local)
        if (DatiStatus.state.value != DatiState.Idle) DatiService.send(this, DatiService.ACTION_RELOAD)
        drawZones()
    }

    private companion object {
        const val MAX_ACCURACY_M = 50f
        val ZONE_FILL = Color.parseColor("#40E65100")
        val ZONE_OUTLINE = Color.parseColor("#E65100")
        val ZONE_INSIDE = Color.parseColor("#C62828")
    }
}
