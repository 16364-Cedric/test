package fr.dati.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import fr.dati.core.Beacons
import java.util.Locale

/**
 * Écoute les balises Bluetooth des zones à risque intérieures. Le scan est filtré (iBeacon et
 * adresses connues) : Android interrompt les scans non filtrés quand l'écran est éteint.
 */
class BeaconScanner(context: Context, private val onBeacon: (id: String, rssi: Int) -> Unit) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private var callback: ScanCallback? = null
    private var scanningIds: Set<String> = emptySet()

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    /** Démarre (ou redémarre si la liste a changé) l'écoute ; renvoie `false` si impossible. */
    @SuppressLint("MissingPermission")
    fun start(ids: Set<String>, force: Boolean = false): Boolean {
        if (!force && ids == scanningIds && callback != null) return true
        stop()
        if (ids.isEmpty()) return true
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return false
        if (!hasPermission(appContext)) return false

        val filters = buildList {
            if (ids.any { !MAC.matches(it) }) {
                add(ScanFilter.Builder().setManufacturerData(APPLE_ID, IBEACON_PREFIX, IBEACON_MASK).build())
            }
            ids.filter { MAC.matches(it) }.forEach { add(ScanFilter.Builder().setDeviceAddress(it.uppercase(Locale.ROOT)).build()) }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = report(result)

            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::report)

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "Échec du scan Bluetooth (code $errorCode)")
            }
        }
        return try {
            scanner.startScan(filters, settings, cb)
            callback = cb
            scanningIds = ids
            true
        } catch (e: Exception) {
            Log.w(TAG, "Scan Bluetooth impossible", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val cb = callback ?: return
        callback = null
        scanningIds = emptySet()
        runCatching { adapter?.bluetoothLeScanner?.stopScan(cb) }
    }

    private fun report(result: ScanResult) {
        onBeacon(result.device.address.lowercase(Locale.ROOT), result.rssi)
        val data = result.scanRecord?.getManufacturerSpecificData(APPLE_ID) ?: return
        if (data.size < 23 || data[0] != IBEACON_PREFIX[0] || data[1] != IBEACON_PREFIX[1]) return
        val hex = data.copyOfRange(2, 18).joinToString("") { "%02x".format(it) }
        val uuid = "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
        val major = (data[18].toInt() and 0xff shl 8) or (data[19].toInt() and 0xff)
        val minor = (data[20].toInt() and 0xff shl 8) or (data[21].toInt() and 0xff)
        onBeacon(Beacons.iBeaconId(uuid, major, minor), result.rssi)
    }

    companion object {
        private const val TAG = "BeaconScanner"
        private const val APPLE_ID = 0x004C
        private val IBEACON_PREFIX = byteArrayOf(0x02, 0x15)
        private val IBEACON_MASK = byteArrayOf(-1, -1)
        private val MAC = Regex("^([0-9a-f]{2}:){5}[0-9a-f]{2}$")

        fun hasPermission(context: Context): Boolean {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_SCAN
            } else {
                Manifest.permission.ACCESS_FINE_LOCATION
            }
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
