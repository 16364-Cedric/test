package fr.dati.app

import fr.dati.core.DatiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** État courant publié par [DatiService] et observé par l'interface. */
object DatiStatus {
    private val mutableState = MutableStateFlow<DatiState>(DatiState.Idle)
    val state: StateFlow<DatiState> = mutableState.asStateFlow()

    private val mutableAckedBy = MutableStateFlow<String?>(null)

    /** Nom du superviseur qui a pris en charge l'alarme en cours, transmis par le serveur. */
    val ackedBy: StateFlow<String?> = mutableAckedBy.asStateFlow()

    /** Résultat du dernier échange avec le serveur de supervision ; `null` si aucun échange. */
    @Volatile
    var serverReachable: Boolean? = null

    /** Horodatage de la dernière alarme pour laquelle un appel a déjà été passé. */
    @Volatile
    var lastCalledAlarmAt: Long? = null

    internal fun publish(state: DatiState) {
        mutableState.value = state
    }

    internal fun publishAck(by: String?) {
        mutableAckedBy.value = by
    }
}
