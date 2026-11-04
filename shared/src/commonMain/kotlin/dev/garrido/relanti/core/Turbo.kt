package dev.garrido.relanti.core

/** The GPS's expressions. Both apps draw the eyes from this. */
enum class TurboMood { IDLE, CURIOUS, HAPPY, EXPLAIN, RECALCULANDO, SILENT }

/** Everything the GPS says, so both apps say the same thing. */
object Lines {
    fun hello(gps: String) = "¡Hola! Soy $gps. Te llevo hasta el examen. Primero, ¿en qué coche vamos?"
    fun nextTramo(title: String) = "Siguiente tramo: $title. Unos 6 minutos."
    const val RETROVISOR = "Toca mirar por el retrovisor: repasa lo que ya pasaste."
    const val SIMULACRO_NEXT = "Ruta completa por hoy. ¿Probamos un simulacro?"
    fun sameEtapa(etapa: String) = "Llevas tres vueltas en $etapa. ¿Cambiamos de aires?"
    const val RECALCULANDO = "Recalculando…"
    fun miss(item: Item): String {
        val right = "La buena es la ${item.letter(item.answer)}"
        return if (item.explanation.isBlank()) "Casi. $right." else "Casi. $right: ${item.explanation}"
    }
    private val happy = listOf("¡Bien!", "¡Eso es!", "Perfecto.", "Vas fino.", "Correcto.")
    fun happy(index: Int) = happy[index.mod(happy.size)]
    fun silent(gps: String) = "$gps en silencio"

    fun llegada(good: Boolean, goodDays: Int, ticketEarned: Boolean, alreadyTicketed: Boolean): String = when {
        ticketEarned -> "¡Ticket de peaje! Este tramo ya es tuyo para siempre."
        alreadyTicketed && good -> "Tramo repasado. Sigue siendo tuyo."
        good && goodDays >= 1 -> "¡Tramo superado! Vuelve otro día y es tuyo para siempre."
        else -> "Este tramo pide otra vuelta. Sin prisa: el GPS ya ha recalculado."
    }

    fun simulacro(passed: Boolean, mistakes: Int) =
        if (passed) "¡Apto! $mistakes ${if (mistakes == 1) "fallo" else "fallos"}. Así se llega al examen."
        else "Esta vez $mistakes fallos. Repasamos cada uno y volvemos a intentarlo."

    val recalculando: String get() = RECALCULANDO
    val aprobado: String get() = APROBADO

    const val APROBADO = "Llegamos. Ahora conduces tú. Yo me quedo en el salpicadero por si me necesitas."
}
