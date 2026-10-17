package fr.dati.app

import fr.dati.core.DatiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** État courant publié par [DatiService] et observé par l'interface. */
object DatiStatus {
    private val mutableState = MutableStateFlow<DatiState>(DatiState.Idle)
    val state: StateFlow<DatiState> = mutableState.asStateFlow()

    /** Horodatage de la dernière alarme pour laquelle un appel a déjà été passé. */
    @Volatile
    var lastCalledAlarmAt: Long? = null

    internal fun publish(state: DatiState) {
        mutableState.value = state
    }
}
