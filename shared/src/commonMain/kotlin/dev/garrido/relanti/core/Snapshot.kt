package dev.garrido.relanti.core

enum class TramoStatus {
    /** Toll ticket earned. */
    LLEGADO,
    /** The Retrovisor wants it back. */
    RETENCION,
    /** The one the GPS suggests now. */
    EN_MARCHA,
    /** Open road. */
    FLUIDO,
    /** Part of the paid motorway and not started. */
    PEAJE,
}

enum class SemaforoLevel { ROJO, AMBAR, VERDE }

enum class NextKind { TRAMO, RETROVISOR, SIMULACRO }

class TramoView(
    val tramo: Tramo,
    val status: TramoStatus,
    /** Different days with a good round (0, 1 or 2+). */
    val goodDays: Int,
    val goodToday: Boolean,
    val ticketed: Boolean,
    val open: Boolean,
    val questionCount: Int,
) {
    val statusLabel: String
        get() = when (status) {
            TramoStatus.LLEGADO -> "LLEGADO ✓"
            TramoStatus.RETENCION -> "RETENCIÓN"
            TramoStatus.EN_MARCHA -> "EN MARCHA"
            TramoStatus.FLUIDO -> if (goodDays == 1) "1 DE 2 DÍAS" else "FLUIDO"
            TramoStatus.PEAJE -> "PEAJE"
        }
}

class EtapaView(
    val etapa: Etapa,
    /** Road position, 1-based, used for the exit sign ("Salida 7"). */
    val salida: Int,
    val tramos: List<TramoView>,
    val open: Boolean,
    val magnet: Boolean,
    val started: Boolean,
) {
    val ticketed: Int get() = tramos.count { it.ticketed }
}

class TicketView(val tramo: Tramo, val etapa: Etapa, val day: Long, val dayLabel: String, val kmAt: Int)

class NextStep(val kind: NextKind, val tramo: Tramo?, val line: String)

class Semaforo(val level: SemaforoLevel, val title: String, val message: String)

class SimResult(val day: Long, val dayLabel: String, val mistakes: Int, val total: Int, val passed: Boolean)

class PuntoNegro(val item: Item, val misses: Int)

/** Everything a screen needs, computed from the log and the pack. */
class Snapshot(
    val pack: Pack,
    val settings: Settings?,
    val today: Long,
    val etapas: List<EtapaView>,
    val tickets: List<TicketView>,
    val magnets: List<Etapa>,
    val points: Int,
    val pointKeys: List<String>,
    val upgrade: Upgrade,
    val nextUpgrade: Upgrade?,
    val km: Int,
    val kmToExam: Int,
    val rango: Rango,
    val nextRango: Rango?,
    val streak: Int,
    val streakBroken: Boolean,
    val playedToday: Boolean,
    val retrovisorDue: Int,
    val puntosNegros: List<PuntoNegro>,
    val semaforo: Semaforo,
    val next: NextStep,
    /** Two other etapas to offer as a desvío, or empty. */
    val desvio: List<EtapaView>,
    val desvioLine: String,
    /** Landscape for La Ruta: ciudad, meseta, costa or montana. */
    val landscape: String,
    val simulacros: List<SimResult>,
    val simulacrosToday: Int,
    val canStartSimulacro: Boolean,
    val aprobado: Boolean,
) {
    val onboarded: Boolean get() = settings != null
    val gpsName: String get() = settings?.gpsName?.ifBlank { null } ?: "Turbo"
    val totalTramos: Int get() = etapas.sumOf { it.tramos.size }
    val placeholder: Boolean get() = pack.placeholder
    val daysToExam: Long get() = settings?.examDay?.takeIf { it >= 0 }?.let { it - today } ?: -1
    val upgradeIndex: Int get() = Rules.UPGRADES.indexOf(upgrade)
    val examDayLabel: String get() = settings?.examDay?.takeIf { it >= 0 }?.let { Days.long(it) } ?: ""
}

internal class ItemState {
    var step = -1
    var due = Long.MAX_VALUE
    var misses = 0
    var lastCorrect = true
    var seen = false
}

internal object Compute {

    fun itemStates(log: ProgressLog): Map<String, ItemState> {
        val states = mutableMapOf<String, ItemState>()
        log.rounds.sortedBy { it.startedAt }.forEach { r ->
            r.answers.forEach { a ->
                val s = states.getOrPut(a.item) { ItemState() }
                s.seen = true
                s.lastCorrect = a.correct
                if (a.correct) {
                    s.step = minOf(s.step + 1, Rules.REVIEW_INTERVALS.lastIndex)
                    s.due = r.day + Rules.REVIEW_INTERVALS[s.step]
                } else {
                    s.misses++
                    s.step = -1
                    s.due = r.day + 1
                }
            }
        }
        return states
    }

    fun expectedQuestions(pack: Pack, tramo: String) = minOf(Rules.TRAMO_QUESTIONS, pack.itemsOf(tramo).size)

    /** Different days with a good round, per tramo, in order. */
    fun goodDays(pack: Pack, log: ProgressLog): Map<String, List<Long>> =
        log.rounds.filter { it.kind == RoundKind.TRAMO && it.tramo != null }
            .sortedBy { it.startedAt }
            .groupBy { it.tramo!! }
            .mapValues { (tramo, rounds) ->
                rounds.filter { Rules.isGoodTramoRound(it, expectedQuestions(pack, tramo)) }.map { it.day }.distinct()
            }

    fun ticketed(pack: Pack, log: ProgressLog): Set<String> =
        goodDays(pack, log).filterValues { it.size >= Rules.TICKET_DAYS }.keys

    fun magnets(pack: Pack, ticketed: Set<String>): List<Etapa> =
        pack.road.filter { e ->
            val tramos = pack.tramosOf(e.code)
            tramos.isNotEmpty() && tramos.all { it.code in ticketed }
        }

    fun puntosNegros(pack: Pack, states: Map<String, ItemState>): List<PuntoNegro> =
        states.filter { (_, s) -> s.misses >= Rules.PUNTO_NEGRO_MISSES && !s.lastCorrect }
            .mapNotNull { (id, s) -> pack.item(id)?.let { PuntoNegro(it, s.misses) } }
            .sortedByDescending { it.misses }

    fun simResults(log: ProgressLog): List<SimResult> =
        log.rounds.filter { it.kind == RoundKind.SIMULACRO }.sortedBy { it.startedAt }
            .map { SimResult(it.day, Days.short(it.day), it.mistakes, it.answers.size, Rules.simPassed(it)) }

    fun semaforo(sims: List<SimResult>, puntosNegros: Int): Semaforo {
        if (sims.isEmpty()) return Semaforo(
            SemaforoLevel.ROJO, "rojo",
            "Haz tu primer simulacro para encender el semáforo.",
        )
        val trailingPassed = sims.reversed().takeWhile { it.passed }.size
        if (trailingPassed >= 2 && puntosNegros == 0) return Semaforo(
            SemaforoLevel.VERDE, "verde",
            "Dos simulacros seguidos sin pasar de 3 fallos. Estás listo.",
        )
        val parts = mutableListOf<String>()
        val need = (2 - trailingPassed).coerceAtLeast(0)
        if (need > 0) parts += "$need ${if (need == 1) "simulacro más" else "simulacros más"} sin pasar de 3 fallos"
        if (puntosNegros > 0) parts += "repasar $puntosNegros ${if (puntosNegros == 1) "punto negro" else "puntos negros"}"
        val msg = parts.joinToString(" y ").replaceFirstChar { it.uppercase() }
        return Semaforo(SemaforoLevel.AMBAR, "ámbar", "Te falta: $msg.")
    }

    /** Point milestone keys the learner has earned according to the log right now. */
    fun earnedPointKeys(pack: Pack, log: ProgressLog): List<String> {
        val keys = mutableListOf<String>()
        val ticketed = ticketed(pack, log)
        magnets(pack, ticketed).forEach { keys += MilestoneKeys.etapa(it.code) }
        val sims = simResults(log)
        if (sims.any { it.passed }) keys += MilestoneKeys.SIM_FIRST
        var run = 0
        var three = false
        sims.forEach { run = if (it.passed) run + 1 else 0; if (run >= 3) three = true }
        if (three) keys += MilestoneKeys.SIM_THREE
        val pn = puntosNegros(pack, itemStates(log)).size
        if (semaforo(sims, pn).level == SemaforoLevel.VERDE) keys += MilestoneKeys.SEMAFORO_GREEN
        return keys
    }

    fun streak(days: Set<Long>, today: Long): Pair<Int, Boolean> {
        var d = if (today in days) today else today - 1
        var count = 0
        var lastBridge = Long.MAX_VALUE
        while (true) {
            if (d in days) { count++; d-- }
            else if (count > 0 && (d - 1) in days && lastBridge - d >= 7) { lastBridge = d; d-- }
            else break
        }
        val broken = count == 0 && days.isNotEmpty()
        return count to broken
    }

    fun snapshot(pack: Pack, log: ProgressLog, today: Long): Snapshot {
        val settings = log.settings
        val entitled = settings?.autopista == true
        val states = itemStates(log)
        val goodDays = goodDays(pack, log)
        val ticketed = goodDays.filterValues { it.size >= Rules.TICKET_DAYS }.keys
        val tramoRounds = log.rounds.filter { it.kind == RoundKind.TRAMO && it.tramo != null }.sortedBy { it.startedAt }
        val startedTramos = tramoRounds.mapNotNull { it.tramo }.toSet()
        val startedEtapas = startedTramos.mapNotNull { pack.tramo(it)?.etapa }.toSet()
        val dueItems = states.filter { (id, s) -> s.due <= today && pack.item(id) != null }
        val dueTramos = dueItems.keys.mapNotNull { pack.item(it)?.tramo }.toSet()
        val pn = puntosNegros(pack, states)
        val sims = simResults(log)
        val semaforo = semaforo(sims, pn.size)
        val magnets = magnets(pack, ticketed)

        fun etapaOpen(e: Etapa) = entitled || e.free || e.code in startedEtapas

        // What the GPS suggests next.
        val lastEtapa = tramoRounds.lastOrNull()?.tramo?.let { pack.tramo(it)?.etapa }
        val currentEtapa = lastEtapa?.let { pack.etapa(it) } ?: pack.road.firstOrNull()
        fun candidate(t: Tramo): Boolean {
            val days = goodDays[t.code].orEmpty()
            return t.code !in ticketed && today !in days && pack.etapa(t.etapa)?.let { etapaOpen(it) } == true &&
                pack.itemsOf(t.code).isNotEmpty()
        }
        val nextTramo = currentEtapa?.let { e -> pack.tramosOf(e.code).firstOrNull { candidate(it) } }
            ?: pack.road.flatMap { pack.tramosOf(it.code) }.firstOrNull { candidate(it) }
        val next = when {
            dueItems.size >= 4 && nextTramo != null && tramoRounds.lastOrNull()?.day == today ->
                NextStep(NextKind.RETROVISOR, null, Lines.RETROVISOR)
            nextTramo != null -> NextStep(NextKind.TRAMO, nextTramo, Lines.nextTramo(nextTramo.title))
            dueItems.isNotEmpty() -> NextStep(NextKind.RETROVISOR, null, Lines.RETROVISOR)
            else -> NextStep(NextKind.SIMULACRO, null, Lines.SIMULACRO_NEXT)
        }

        val etapaViews = pack.road.mapIndexed { idx, e ->
            val open = etapaOpen(e)
            val tramos = pack.tramosOf(e.code).map { t ->
                val days = goodDays[t.code].orEmpty()
                val isTicketed = t.code in ticketed
                val status = when {
                    !open -> TramoStatus.PEAJE
                    t.code in dueTramos && days.isNotEmpty() -> TramoStatus.RETENCION
                    isTicketed -> TramoStatus.LLEGADO
                    next.tramo?.code == t.code -> TramoStatus.EN_MARCHA
                    else -> TramoStatus.FLUIDO
                }
                TramoView(t, status, days.size, today in days, isTicketed, open, pack.itemsOf(t.code).size)
            }
            EtapaView(e, idx + 1, tramos, open, magnets.any { it.code == e.code }, e.code in startedEtapas)
        }

        // Desvío: three tramo rounds in a row in the same etapa.
        val lastThree = tramoRounds.takeLast(Rules.SAME_ETAPA_NUDGE).mapNotNull { r -> r.tramo?.let { pack.tramo(it)?.etapa } }
        val desvio = if (lastThree.size == Rules.SAME_ETAPA_NUDGE && lastThree.toSet().size == 1) {
            val here = etapaViews.indexOfFirst { it.etapa.code == lastThree.first() }
            (1 until etapaViews.size).map { etapaViews[(here + it) % etapaViews.size] }
                .filter { v -> v.tramos.any { !it.ticketed } }
                .take(2)
        } else emptyList()
        val desvioLine = if (desvio.isNotEmpty()) Lines.sameEtapa(pack.etapa(lastThree.first())?.name ?: "") else ""

        // Tickets, in the order they were earned.
        var kmRunning = 0
        val ticketDayKm = mutableMapOf<String, Pair<Long, Int>>()
        val goodSoFar = mutableMapOf<String, MutableSet<Long>>()
        log.rounds.sortedBy { it.startedAt }.forEach { r ->
            kmRunning += Rules.kmFor(r)
            val t = r.tramo
            if (r.kind == RoundKind.TRAMO && t != null && Rules.isGoodTramoRound(r, expectedQuestions(pack, t))) {
                val set = goodSoFar.getOrPut(t) { mutableSetOf() }
                set += r.day
                if (set.size == Rules.TICKET_DAYS && t !in ticketDayKm) ticketDayKm[t] = r.day to kmRunning
            }
        }
        val tickets = ticketDayKm.entries.sortedBy { it.value.second }.mapNotNull { (code, v) ->
            val t = pack.tramo(code) ?: return@mapNotNull null
            val e = pack.etapa(t.etapa) ?: return@mapNotNull null
            TicketView(t, e, v.first, Days.short(v.first), v.second)
        }

        val pointKeys = log.milestones.filter { it.kind == MilestoneKind.POINT }.map { it.key }.distinct()
        val points = pointKeys.size.coerceAtMost(Rules.MAX_POINTS)
        val upgrade = Rules.UPGRADES.last { it.points <= points }
        val nextUpgrade = Rules.UPGRADES.firstOrNull { it.points > points }
        val km = kmRunning
        val rango = Rules.RANGOS.last { it.km <= km }
        val nextRango = Rules.RANGOS.firstOrNull { it.km > km }
        val playDays = log.rounds.map { it.day }.toSet()
        val (streak, broken) = streak(playDays, today)
        val totalTramos = etapaViews.sumOf { it.tramos.size }
        val remainingTramos = totalTramos - ticketed.count { pack.tramo(it) != null }
        val kmToExam = remainingTramos * Rules.KM_PER_TRAMO +
            if (semaforo.level == SemaforoLevel.VERDE) 0 else Rules.KM_FINAL_STRETCH
        val simsToday = sims.count { it.day == today }
        val landscape = (next.tramo?.let { pack.etapa(it.etapa) } ?: currentEtapa)?.landscape ?: "ciudad"

        return Snapshot(
            pack = pack,
            settings = settings,
            today = today,
            etapas = etapaViews,
            tickets = tickets,
            magnets = magnets,
            points = points,
            pointKeys = pointKeys,
            upgrade = upgrade,
            nextUpgrade = nextUpgrade,
            km = km,
            kmToExam = kmToExam,
            rango = rango,
            nextRango = nextRango,
            streak = streak,
            streakBroken = broken,
            playedToday = today in playDays,
            retrovisorDue = dueItems.size,
            puntosNegros = pn,
            semaforo = semaforo,
            next = next,
            desvio = desvio,
            desvioLine = desvioLine,
            landscape = landscape,
            simulacros = sims,
            simulacrosToday = simsToday,
            canStartSimulacro = entitled || simsToday < Rules.FREE_SIMULACROS_PER_DAY,
            aprobado = log.milestones.any { it.kind == MilestoneKind.APROBADO },
        )
    }
}
