package dev.garrido.relanti.core

/** Every rule the apps follow, in one place. Both apps read these; neither re-implements them. */
object Rules {
    /** Questions in a tramo round, as in the DGT format. */
    const val TRAMO_QUESTIONS = 8

    /** A good round has at most this many mistakes. */
    const val GOOD_ROUND_MAX_MISTAKES = 1

    /** Good rounds on this many different days earn the toll ticket. */
    const val TICKET_DAYS = 2

    /** Retrovisor intervals in days after each correct answer. A miss comes back tomorrow. */
    val REVIEW_INTERVALS = listOf(1, 3, 7, 14, 30)
    const val RETROVISOR_SIZE = 10

    /** A question becomes a punto negro after this many misses, while its last answer is wrong. */
    const val PUNTO_NEGRO_MISSES = 2

    /** The real exam: 30 questions, 30 minutes, at most 3 mistakes. */
    const val SIM_QUESTIONS = 30
    const val SIM_MAX_MISTAKES = 3
    const val SIM_SECONDS = 30 * 60

    /** Free tier: one simulacro a day. */
    const val FREE_SIMULACROS_PER_DAY = 1

    const val MAX_POINTS = 15

    /** After this many tramo rounds in a row in one etapa, the GPS suggests a desvío. */
    const val SAME_ETAPA_NUDGE = 3

    /** Road length per tramo and for the final simulacro stretch, for the EXAMEN sign. */
    const val KM_PER_TRAMO = 5
    const val KM_FINAL_STRETCH = 6

    val UPGRADES = listOf(
        Upgrade(0, "segunda-mano", "De segunda mano", "Tu color, aún apagado"),
        Upgrade(3, "pintura", "Pintura nueva", "Tu color y la L"),
        Upgrade(6, "llantas", "Llantas", "Llantas de aleación"),
        Upgrade(8, "listo", "Listo para el examen", "Lunas tintadas y franja"),
        Upgrade(12, "ruta-larga", "Ruta larga", "Baca y antinieblas"),
        Upgrade(15, "completo", "Carnet completo", "Alerón, luz de bajos y adiós a la L"),
    )

    val RANGOS = listOf(
        Rango("copiloto", "Copiloto", 0),
        Rango("aprendiz", "Aprendiz", 100),
        Rango("rodando", "Rodando", 400),
        Rango("conductor", "Conductor", 1_200),
        Rango("rutalarga", "Ruta larga", 3_000),
        Rango("leyenda", "Leyenda de la carretera", 8_000),
    )

    val GPS_NAME_SUGGESTIONS = listOf("Turbo", "Gepe", "Kilo", "Brújula")

    /** About 10 km per active minute, and never less than 2 km per answer. */
    fun kmFor(round: Round): Int = maxOf(round.answers.size * 2, (round.activeMs / 6_000).toInt())

    fun isGoodTramoRound(round: Round, expectedQuestions: Int): Boolean =
        round.kind == RoundKind.TRAMO &&
            round.answers.size >= expectedQuestions &&
            round.mistakes <= GOOD_ROUND_MAX_MISTAKES

    fun simPassed(round: Round): Boolean = round.kind == RoundKind.SIMULACRO && round.mistakes <= SIM_MAX_MISTAKES
}

class Upgrade(val points: Int, val id: String, val name: String, val detail: String)

class Rango(val id: String, val name: String, val km: Int)

object MilestoneKeys {
    fun etapa(code: String) = "etapa:$code"
    const val SIM_FIRST = "sim:first"
    const val SIM_THREE = "sim:three"
    const val SEMAFORO_GREEN = "semaforo:green"
    const val APROBADO = "aprobado"

    fun label(key: String, pack: Pack): String = when {
        key.startsWith("etapa:") -> "Imán de ${pack.etapa(key.removePrefix("etapa:"))?.name ?: key}"
        key == SIM_FIRST -> "Primer simulacro aprobado"
        key == SIM_THREE -> "Tres simulacros seguidos"
        key == SEMAFORO_GREEN -> "Semáforo en verde"
        else -> key
    }
}
