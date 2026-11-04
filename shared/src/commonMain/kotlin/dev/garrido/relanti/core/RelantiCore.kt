package dev.garrido.relanti.core

import kotlin.random.Random

/**
 * The one object each app creates at start-up. It owns the pack and the
 * progress log, and hands out sessions and snapshots.
 */
class RelantiCore(
    packJson: String,
    private val storage: ProgressStorage,
    private val clock: PlatformClock,
    seed: Int,
) {
    constructor(packJson: String, storage: ProgressStorage, clock: PlatformClock) :
        this(packJson, storage, clock, -1)

    val pack: Pack = Pack.parse(packJson)
    private val random: Random = if (seed >= 0) Random(seed) else Random.Default
    var log: ProgressLog = ProgressLog.decode(storage.load())
        private set

    fun today(): Long = clock.nowMillis().let { Days.dayOf(it, clock.utcOffsetMinutes(it)) }

    fun snapshot(): Snapshot = Compute.snapshot(pack, log, today())

    val isOnboarded: Boolean get() = log.settings != null

    fun saveSettings(settings: Settings) {
        update(log.copy(settings = settings.copy(updatedAt = clock.nowMillis())))
    }

    fun settingsOrDefault(): Settings = log.settings ?: Settings()

    // Sessions -------------------------------------------------------------

    fun startTramo(code: String): QuizSession? {
        val tramo = pack.tramo(code) ?: return null
        val all = pack.itemsOf(code)
        if (all.isEmpty()) return null
        val states = Compute.itemStates(log)
        // Questions not yet answered right come first, then the rest at random.
        val (fresh, known) = all.shuffled(random).partition { states[it.id]?.let { s -> !s.lastCorrect || !s.seen } ?: true }
        val items = (fresh + known).take(Rules.TRAMO_QUESTIONS).shuffled(random)
        return QuizSession(
            RoundKind.TRAMO, tramo, pack.truco(tramo.truco), items, clock.nowMillis(), gpsName(),
            "${tramo.code} · ${tramo.title}",
        )
    }

    fun startRetrovisor(): QuizSession? {
        val today = today()
        val due = Compute.itemStates(log).filter { (id, s) -> s.due <= today && pack.item(id) != null }
            .entries.sortedBy { it.value.due }.take(Rules.RETROVISOR_SIZE).mapNotNull { pack.item(it.key) }
        if (due.isEmpty()) return null
        return QuizSession(RoundKind.RETROVISOR, null, null, due.shuffled(random), clock.nowMillis(), gpsName(), "Retrovisor")
    }

    fun startPuntosNegros(): QuizSession? {
        val items = Compute.puntosNegros(pack, Compute.itemStates(log)).map { it.item }.take(Rules.RETROVISOR_SIZE)
        if (items.isEmpty()) return null
        return QuizSession(RoundKind.RETROVISOR, null, null, items.shuffled(random), clock.nowMillis(), gpsName(), "Puntos negros")
    }

    fun startSimulacro(): QuizSession? {
        if (!snapshot().canStartSimulacro) return null
        // Mix every etapa, as the real exam does, spreading questions across tramos.
        val byTramo = pack.items.groupBy { it.tramo }.mapValues { it.value.shuffled(random).toMutableList() }
        val picked = mutableListOf<Item>()
        val queues = byTramo.values.shuffled(random)
        while (picked.size < Rules.SIM_QUESTIONS && queues.any { it.isNotEmpty() }) {
            queues.forEach { q -> if (q.isNotEmpty() && picked.size < Rules.SIM_QUESTIONS) picked += q.removeAt(0) }
        }
        return QuizSession(RoundKind.SIMULACRO, null, null, picked.shuffled(random), clock.nowMillis(), gpsName(), "Simulacro")
    }

    /** Records a finished (or abandoned) session and returns the Llegada. Returns null if nothing was answered. */
    fun finish(session: QuizSession): Llegada? {
        val answers = session.answers
        if (answers.isEmpty()) return null
        val now = clock.nowMillis()
        val offset = clock.utcOffsetMinutes(session.startedAt)
        val before = Compute.snapshot(pack, log, today())
        val round = Round(
            id = newId(),
            kind = session.kind,
            tramo = session.tramo?.code,
            startedAt = session.startedAt,
            day = Days.dayOf(session.startedAt, offset),
            utcOffsetMinutes = offset,
            contentVersion = pack.contentVersion,
            answers = answers,
        )
        var next = log.copy(rounds = log.rounds + round)

        // Points are milestones: once earned they are kept, whatever changes later.
        val have = next.milestones.filter { it.kind == MilestoneKind.POINT }.map { it.key }.toSet()
        val newKeys = Compute.earnedPointKeys(pack, next).filter { it !in have }
        next = next.copy(milestones = next.milestones + newKeys.map { Milestone(newId(), MilestoneKind.POINT, it, now) })
        update(next)

        val after = Compute.snapshot(pack, log, today())
        val tramoCode = session.tramo?.code
        val goodDays = tramoCode?.let { c -> after.etapas.flatMap { it.tramos }.firstOrNull { it.tramo.code == c }?.goodDays } ?: 0
        val wasTicketed = tramoCode != null && before.tickets.any { it.tramo.code == tramoCode }
        val isTicketed = tramoCode != null && after.tickets.any { it.tramo.code == tramoCode }
        val good = when (session.kind) {
            RoundKind.TRAMO -> Rules.isGoodTramoRound(round, Compute.expectedQuestions(pack, tramoCode!!))
            RoundKind.SIMULACRO -> Rules.simPassed(round)
            else -> true
        }
        val line = when (session.kind) {
            RoundKind.TRAMO -> Lines.llegada(good, goodDays, isTicketed && !wasTicketed, wasTicketed)
            RoundKind.SIMULACRO -> Lines.simulacro(good, round.mistakes)
            else -> if (round.mistakes == 0) "Retrovisor limpio. Todo en su sitio." else
                "Lo que has fallado vuelve mañana. Así se fija."
        }
        val upgrade = Rules.UPGRADES.firstOrNull { it.points > before.points && it.points <= after.points }
        return Llegada(
            kind = session.kind,
            tramo = session.tramo,
            total = answers.size,
            correct = answers.count { it.correct },
            mistakes = round.mistakes,
            good = good,
            km = Rules.kmFor(round),
            goodDays = goodDays,
            ticketEarned = isTicketed && !wasTicketed,
            newPoints = newKeys.map { MilestoneKeys.label(it, pack) },
            pointsAfter = after.points,
            upgrade = upgrade,
            line = line,
            review = answers.mapNotNull { a -> pack.item(a.item)?.let { ReviewLine(it, a.choice, a.correct) } },
            dayLabel = Days.short(round.day),
            kmTotal = after.km,
        )
    }

    // Other actions --------------------------------------------------------

    fun markAprobado() {
        if (log.milestones.any { it.kind == MilestoneKind.APROBADO }) return
        update(log.copy(milestones = log.milestones + Milestone(newId(), MilestoneKind.APROBADO, MilestoneKeys.APROBADO, clock.nowMillis())))
    }

    /** "Descargar mis datos": the whole log as JSON. */
    fun exportJson(): String = log.encode()

    /** "Borrar todo". */
    fun resetAll() = update(ProgressLog())

    /** Imports a log, used by tests and later by sync. */
    fun replaceLog(json: String) = update(ProgressLog.decode(json))

    private fun gpsName() = log.settings?.gpsName?.ifBlank { null } ?: "Turbo"

    private fun update(next: ProgressLog) {
        log = next
        storage.save(next.encode())
    }

    private fun newId(): String {
        val hex = "0123456789abcdef"
        return buildString { repeat(32) { append(hex[random.nextInt(16)]); if (it == 7 || it == 11 || it == 15 || it == 19) append('-') } }
    }
}
