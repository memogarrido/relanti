package dev.garrido.relanti.core

import kotlin.time.TimeSource

/**
 * Ready-made learner states for UI tests and screenshots. Both apps start
 * from the same fixtures (iOS `--uitest-state=<name>`, Android extra
 * `uitest_state`), so their tests check the same situations.
 *
 * Every fixture runs on in-memory storage and a clock pinned to [TODAY]
 * at 10:00 in Madrid, so day-based rules (tickets, Retrovisor, streaks)
 * behave the same on every run.
 */
object Fixtures {
    const val FRESH = "fresh"
    const val ONBOARDED = "onboarded"
    const val TICKET_READY = "ticket-ready"
    const val MIDWEEK = "midweek"
    const val AUTOPISTA = "autopista"
    val names = listOf(FRESH, ONBOARDED, TICKET_READY, MIDWEEK, AUTOPISTA)

    const val TODAY = "2026-11-04"

    /** A core in the named state. Unknown names give [ONBOARDED]. */
    fun core(packJson: String, state: String): RelantiCore {
        val clock = FixtureClock(Days.parseIso(TODAY))
        val core = RelantiCore(packJson, InMemoryStorage(), clock, 7)
        if (state == FRESH) return core
        core.saveSettings(Settings(carName = "El Rojito", gpsName = "Turbo", autopista = state == AUTOPISTA))
        val tramos = core.pack.road.flatMap { core.pack.tramosOf(it.code) }
            .filter { core.pack.itemsOf(it.code).size >= Rules.TRAMO_QUESTIONS }
        when (state) {
            TICKET_READY -> {
                // One good day on the first tramo yesterday: a good round today earns its ticket.
                clock.atDay(-1) { play(core, core.startTramo(tramos[0].code), 0) }
            }
            MIDWEEK, AUTOPISTA -> {
                clock.atDay(-5) { play(core, core.startTramo(tramos[0].code), 1) }
                clock.atDay(-4) { play(core, core.startTramo(tramos[0].code), 0) }
                clock.atDay(-3) { play(core, core.startTramo(tramos[1].code), 2) }
                clock.atDay(-2) { play(core, core.startTramo(tramos[1].code), 1) }
                clock.atDay(-2) { play(core, core.startSimulacro(), 2) }
                clock.atDay(-1) { play(core, core.startTramo(tramos[2].code), 0) }
            }
        }
        return core
    }

    /** Answers a session with exactly [mistakes] wrong answers, then records it. */
    fun play(core: RelantiCore, session: QuizSession?, mistakes: Int): Llegada? {
        session ?: return null
        session.start()
        var missed = 0
        while (session.phase != SessionPhase.DONE) {
            val item = session.current
            val wrong = missed < mistakes
            session.answer(if (wrong) (item.answer + 1) % item.options.size else item.answer, 6_000)
            if (wrong) missed++
            session.next()
        }
        return core.finish(session)
    }
}

/** Pinned to 10:00 local time on a day, moving forward with real time so answer timings work. */
internal class FixtureClock(private val today: Long) : PlatformClock {
    private val mark = TimeSource.Monotonic.markNow()
    private var dayShift = 0L

    private fun base(): Long = (today + dayShift) * 86_400_000L + 10 * 3_600_000L - OFFSET_MS

    override fun nowMillis(): Long = base() + mark.elapsedNow().inWholeMilliseconds
    override fun utcOffsetMinutes(atMillis: Long): Int = 60

    fun atDay(shift: Long, block: () -> Unit) {
        dayShift = shift
        try { block() } finally { dayShift = 0 }
    }

    private companion object { const val OFFSET_MS = 60 * 60_000L }
}
