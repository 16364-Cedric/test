package fr.dati.app

import android.content.Context
import android.content.RestrictionsManager
import android.util.Log
import fr.dati.core.DeploymentConfig
import java.security.MessageDigest

/** Configuration poussée par l'outil de gestion de flotte de l'entreprise (MDM / Android Enterprise). */
object ManagedConfig {
    private const val TAG = "ManagedConfig"

    /** Applique la configuration MDM si elle existe ; renvoie `true` si quelque chose a été appliqué. */
    fun apply(context: Context, settings: DatiSettings): Boolean {
        val restrictions = context.getSystemService(RestrictionsManager::class.java)?.applicationRestrictions
        if (restrictions == null || restrictions.isEmpty) return false
        val values = restrictions.keySet().mapNotNull { key ->
            @Suppress("DEPRECATION")
            restrictions.get(key)?.let { key to it.toString() }
        }.toMap()
        // Même configuration que la dernière fois : rien à faire (évite de recalculer
        // l'empreinte du mot de passe administrateur à chaque ouverture de l'application).
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(values.toSortedMap().toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        if (fingerprint == settings.managedConfigFingerprint) return false
        val config = try {
            DeploymentConfig.fromMap(values)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Configuration MDM refusée", e)
            EventLog(context).add("Configuration MDM refusée : ${e.message}")
            return false
        }
        settings.managedConfigFingerprint = fingerprint
        if (config.isEmpty) return false
        settings.apply(config)
        EventLog(context).add("Configuration MDM appliquée")
        return true
    }
}
