package fr.dati.core

/** Cause d'une pré-alarme ou d'une alarme. */
enum class AlarmReason {
    /** Chute détectée (chute libre suivie d'un choc). */
    FALL,

    /** Aucun mouvement pendant la durée configurée (perte de mobilité). */
    IMMOBILITY,

    /** Le téléphone, porté verticalement, est resté couché trop longtemps (personne à terre). */
    LOSS_OF_VERTICALITY,

    /** L'utilisateur n'a pas confirmé sa présence à temps. */
    MISSED_CHECK_IN,

    /** Appel volontaire au secours. */
    SOS,

    /** Essai déclenché par l'utilisateur pour vérifier la chaîne d'alerte. */
    TEST,
}
