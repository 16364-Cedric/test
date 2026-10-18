package fr.dati.core

object PhoneNumbers {
    private val VALID = Regex("""^\+?[0-9]{3,15}$""")

    /** Retire espaces, points et tirets ; renvoie `null` si le résultat n'est pas un numéro plausible. */
    fun normalize(raw: String): String? =
        raw.filterNot { it.isWhitespace() || it == '.' || it == '-' }.takeIf { VALID.matches(it) }
}
