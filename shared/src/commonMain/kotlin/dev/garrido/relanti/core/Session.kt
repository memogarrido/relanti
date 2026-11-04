package dev.garrido.relanti.core

enum class SessionPhase {
    /** Turbo's truco before the first question. */
    INTRO,
    QUESTION,
    /** Right or wrong shown, waiting for "Continuar". Never used in simulacros. */
    FEEDBACK,
    DONE,
}

/**
 * One round: a tramo, a Retrovisor, Puntos negros or a simulacro.
 * The UI calls [start], [answer], [next] and [timeUp], and draws from the properties.
 */
class QuizSession internal constructor(
    val kind: RoundKind,
    val tramo: Tramo?,
    val truco: Truco?,
    val items: List<Item>,
    val startedAt: Long,
    val gpsName: String,
    /** Title for the header, e.g. "AD-102 · Adelantamientos" or "Retrovisor". */
    val title: String,
) {
    val isSimulacro: Boolean get() = kind == RoundKind.SIMULACRO
    /** Simulacros give no feedback until the end, like the real exam. */
    val showsFeedback: Boolean get() = !isSimulacro
    val timeLimitSeconds: Int get() = if (isSimulacro) Rules.SIM_SECONDS else 0
    val total: Int get() = items.size

    var phase: SessionPhase = if (truco != null && kind == RoundKind.TRAMO) SessionPhase.INTRO else SessionPhase.QUESTION
        private set
    var index: Int = 0
        private set
    var mood: TurboMood = when {
        isSimulacro -> TurboMood.SILENT
        phase == SessionPhase.INTRO -> TurboMood.EXPLAIN
        else -> TurboMood.IDLE
    }
        private set

    /** The learner's choice on the current question, or -1. */
    var lastChoice: Int = -1
        private set
    var lastCorrect: Boolean = false
        private set

    private val _answers = mutableListOf<Answer>()
    val answers: List<Answer> get() = _answers.toList()

    val current: Item get() = items[index.coerceAtMost(items.lastIndex)]
    val mistakes: Int get() = _answers.count { !it.correct }
    val correctCount: Int get() = _answers.count { it.correct }
    val answeredCount: Int get() = _answers.size

    /** "AD-102 · 5 de 8" */
    val progressLabel: String
        get() = "${tramo?.code ?: title} · ${minOf(index + 1, total)} de $total"

    /** What the GPS says now. */
    val gpsLine: String
        get() = when {
            isSimulacro -> Lines.silent(gpsName)
            phase == SessionPhase.FEEDBACK && !lastCorrect -> Lines.miss(current)
            phase == SessionPhase.FEEDBACK -> Lines.happy(index)
            else -> ""
        }

    fun start() {
        if (phase == SessionPhase.INTRO) {
            phase = SessionPhase.QUESTION
            mood = TurboMood.IDLE
        }
    }

    /** Records an answer. [ms] is the time spent on this question. */
    fun answer(choice: Int, ms: Long) {
        if (phase != SessionPhase.QUESTION) return
        val item = current
        lastChoice = choice
        lastCorrect = choice == item.answer
        _answers += Answer(item.id, choice, lastCorrect, ms.coerceAtLeast(0))
        if (showsFeedback) {
            phase = SessionPhase.FEEDBACK
            mood = if (lastCorrect) TurboMood.HAPPY else TurboMood.RECALCULANDO
        } else {
            advance()
        }
    }

    fun next() {
        if (phase == SessionPhase.FEEDBACK) advance()
    }

    /** Simulacro clock ran out: every unanswered question counts as a mistake. */
    fun timeUp() {
        if (phase == SessionPhase.DONE) return
        if (phase == SessionPhase.FEEDBACK) index++
        while (index < items.size) {
            _answers += Answer(items[index].id, -1, false, 0)
            index++
        }
        phase = SessionPhase.DONE
    }

    private fun advance() {
        index++
        lastChoice = -1
        if (index >= items.size) {
            phase = SessionPhase.DONE
            mood = if (isSimulacro) TurboMood.SILENT else TurboMood.HAPPY
        } else {
            phase = SessionPhase.QUESTION
            mood = if (isSimulacro) TurboMood.SILENT else TurboMood.IDLE
        }
    }
}

class ReviewLine(val item: Item, val choice: Int, val correct: Boolean) {
    val choiceLabel: String get() = if (choice < 0) "Sin responder" else "${item.letter(choice)}. ${item.options[choice]}"
    val answerLabel: String get() = "${item.letter(item.answer)}. ${item.options[item.answer]}"
}

/** The Llegada screen: the result of a finished round. */
class Llegada(
    val kind: RoundKind,
    val tramo: Tramo?,
    val total: Int,
    val correct: Int,
    val mistakes: Int,
    /** Tramo: at most one mistake. Simulacro: passed. Reviews: always true. */
    val good: Boolean,
    val km: Int,
    val goodDays: Int,
    val ticketEarned: Boolean,
    val newPoints: List<String>,
    val pointsAfter: Int,
    /** A car upgrade unlocked by this round, or null. */
    val upgrade: Upgrade?,
    val line: String,
    val review: List<ReviewLine>,
    val dayLabel: String,
    val kmTotal: Int,
)
