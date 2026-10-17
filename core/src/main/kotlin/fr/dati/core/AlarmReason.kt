package fr.dati.core

/** Cause d'une pré-alarme ou d'une alarme. */
enum class AlarmReason {
    /** Chute détectée (chute libre suivie d'un choc). */
    FALL,

    /** Aucun mouvement pendant la durée configurée (perte de mobilité). */
    IMMOBILITY,

    /** L'utilisateur n'a pas confirmé sa présence à temps. */
    MISSED_CHECK_IN,

    /** Appel volontaire au secours. */
    SOS,
}
