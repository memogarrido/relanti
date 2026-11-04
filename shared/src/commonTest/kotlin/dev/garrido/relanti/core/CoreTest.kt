package dev.garrido.relanti.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeClock(var millis: Long, val offset: Int = 60) : PlatformClock {
    override fun nowMillis() = millis
    override fun utcOffsetMinutes(atMillis: Long) = offset
    fun setDay(iso: String, hour: Int = 10) {
        millis = Days.parseIso(iso) * 86_400_000L + hour * 3_600_000L - offset * 60_000L
    }
}

object TestPack {
    private fun items(tramo: String, n: Int) = (1..n).joinToString(",") { i ->
        """{"id":"$tramo-${i.toString().padStart(2, '0')}","tramo":"$tramo","question":"Q$i","options":["a","b","c"],"answer":1,"explanation":"porque sí"}"""
    }
    val json = """
    {"format":"relanti-pack","schemaVersion":1,"contentVersion":7,"placeholder":true,"futureField":{"x":1},
     "etapas":[{"code":"SE","order":1,"name":"Villaseñal","free":true},
               {"code":"PR","order":2,"name":"Cruce","free":true},
               {"code":"AD","order":3,"name":"Paso del Adelanto","free":false,"landscape":"costa"}],
     "tramos":[{"code":"SE-101","etapa":"SE","order":1,"title":"Peligro","truco":"T1"},
               {"code":"PR-101","etapa":"PR","order":1,"title":"Glorietas"},
               {"code":"AD-101","etapa":"AD","order":1,"title":"Adelantar"}],
     "trucos":[{"id":"T1","title":"Ojo","body":"Siempre y nunca"}],
     "items":[${items("SE-101", 10)},${items("PR-101", 8)},${items("AD-101", 8)}]}
    """.trimIndent()
}

class CoreTest {
    private fun core(clock: FakeClock, storage: ProgressStorage = InMemoryStorage()) =
        RelantiCore(TestPack.json, storage, clock, 42).also { it.saveSettings(Settings(gpsName = "Kilo")) }

    private fun QuizSession.play(mistakes: Int) {
        start()
        var missed = 0
        while (phase != SessionPhase.DONE) {
            val wrong = missed < mistakes
            answer(if (wrong) (current.answer + 1) % 3 else current.answer, 5_000)
            if (wrong) missed++
            next()
        }
    }

    @Test
    fun packParsesAndIgnoresUnknownFields() {
        val pack = Pack.parse(TestPack.json)
        assertEquals(3, pack.etapas.size)
        assertTrue(pack.placeholder)
        assertTrue(pack.validate().isEmpty())
    }

    @Test
    fun brokenPackIsRejected() {
        val bad = TestPack.json.replace("\"answer\":1", "\"answer\":5")
        val e = runCatching { Pack.parse(bad) }.exceptionOrNull()
        assertTrue(e is PackException)
    }

    @Test
    fun eightQuestionsOneMissOnTwoDaysEarnsTicket() {
        // Volantón's rule, now Relanti's: "8 questions, 1 miss, on 3 Nov and again on 5 Nov earns ticket SE-101".
        val clock = FakeClock(0).apply { setDay("2026-11-03") }
        val core = core(clock)
        val s1 = core.startTramo("SE-101")!!
        assertEquals(SessionPhase.INTRO, s1.phase)
        assertEquals(8, s1.total)
        s1.play(mistakes = 1)
        val l1 = core.finish(s1)!!
        assertTrue(l1.good)
        assertFalse(l1.ticketEarned)
        assertEquals(1, l1.goodDays)

        // Same day again does not count twice.
        val again = core.startTramo("SE-101")!!
        again.play(0)
        assertFalse(core.finish(again)!!.ticketEarned)

        clock.setDay("2026-11-05")
        val s2 = core.startTramo("SE-101")!!
        s2.play(mistakes = 1)
        val l2 = core.finish(s2)!!
        assertTrue(l2.ticketEarned)
        // SE has one tramo, so its magnet is a licence point.
        assertEquals(1, l2.pointsAfter)
        assertEquals(listOf("Imán de Villaseñal"), l2.newPoints)
        val snap = core.snapshot()
        assertEquals(1, snap.tickets.size)
        assertTrue(snap.etapas[0].tramos[0].ticketed)
    }

    @Test
    fun twoMistakesIsNotAGoodRound() {
        val clock = FakeClock(0).apply { setDay("2026-11-03") }
        val core = core(clock)
        val s = core.startTramo("PR-101")!!
        s.play(mistakes = 2)
        val l = core.finish(s)!!
        assertFalse(l.good)
        assertEquals(0, l.goodDays)
    }

    @Test
    fun missComesBackTomorrowAndBecomesPuntoNegro() {
        val clock = FakeClock(0).apply { setDay("2026-11-03") }
        val core = core(clock)
        val s = core.startTramo("PR-101")!!
        s.play(mistakes = 1)
        core.finish(s)
        assertEquals(0, core.snapshot().retrovisorDue)
        clock.setDay("2026-11-04")
        assertEquals(8, core.snapshot().retrovisorDue) // 1 day after the first answer, all due
        val r = core.startRetrovisor()!!
        // miss everything in the Retrovisor: the item missed yesterday is now missed twice.
        r.start()
        while (r.phase != SessionPhase.DONE) { r.answer((r.current.answer + 1) % 3, 1000); r.next() }
        core.finish(r)
        val pn = core.snapshot().puntosNegros
        assertEquals(1, pn.size)
        assertEquals(2, pn[0].misses)
    }

    @Test
    fun paidEtapaIsTollUntilUnlocked() {
        val clock = FakeClock(0).apply { setDay("2026-11-03") }
        val core = core(clock)
        val ad = core.snapshot().etapas.first { it.etapa.code == "AD" }
        assertFalse(ad.open)
        assertEquals(TramoStatus.PEAJE, ad.tramos[0].status)
        core.saveSettings(core.settingsOrDefault().copy(autopista = true))
        assertTrue(core.snapshot().etapas.first { it.etapa.code == "AD" }.open)
    }

    @Test
    fun simulacroIsSilentHasNoFeedbackAndOnePerDayFree() {
        val clock = FakeClock(0).apply { setDay("2026-11-03") }
        val core = core(clock)
        val sim = core.startSimulacro()!!
        assertEquals(26, sim.total) // all items, fewer than 30
        assertEquals(TurboMood.SILENT, sim.mood)
        sim.answer(sim.current.answer, 1000)
        assertEquals(SessionPhase.QUESTION, sim.phase) // no feedback screen
        sim.timeUp()
        val l = core.finish(sim)!!
        assertEquals(25, l.mistakes)
        assertFalse(l.good)
        assertNull(core.startSimulacro())
        assertFalse(core.snapshot().canStartSimulacro)
        clock.setDay("2026-11-04")
        assertNotNull(core.startSimulacro())
    }

    @Test
    fun semaforoGoesGreenAfterTwoPassedSimulacros() {
        val clock = FakeClock(0).apply { setDay("2026-11-03") }
        val core = core(clock)
        core.saveSettings(core.settingsOrDefault().copy(autopista = true))
        assertEquals(SemaforoLevel.ROJO, core.snapshot().semaforo.level)
        repeat(3) {
            val sim = core.startSimulacro()!!
            sim.play(mistakes = 2)
            core.finish(sim)
        }
        val snap = core.snapshot()
        assertEquals(SemaforoLevel.VERDE, snap.semaforo.level)
        // first passed, three in a row, green light
        assertEquals(3, snap.points)
        assertEquals("pintura", snap.upgrade.id)
    }

    @Test
    fun pointsNeverGoDown() {
        val clock = FakeClock(0).apply { setDay("2026-11-03") }
        val core = core(clock)
        core.saveSettings(core.settingsOrDefault().copy(autopista = true))
        repeat(2) { core.startSimulacro()!!.also { it.play(0); core.finish(it) } }
        assertEquals(2, core.snapshot().points) // first passed + green
        core.startSimulacro()!!.also { it.play(10); core.finish(it) } // a bad one turns the light amber
        val snap = core.snapshot()
        assertEquals(SemaforoLevel.AMBAR, snap.semaforo.level)
        assertEquals(2, snap.points)
    }

    @Test
    fun progressSurvivesRestart() {
        val clock = FakeClock(0).apply { setDay("2026-11-03") }
        val storage = InMemoryStorage()
        val core = core(clock, storage)
        core.startTramo("SE-101")!!.also { it.play(0); core.finish(it) }
        val reopened = RelantiCore(TestPack.json, storage, clock)
        assertEquals(1, reopened.log.rounds.size)
        assertEquals("Kilo", reopened.snapshot().gpsName)
    }

    @Test
    fun streakBridgesOneMissingDay() {
        assertEquals(3 to false, Compute.streak(setOf(10L, 11L, 13L), 13L))
        assertEquals(2 to false, Compute.streak(setOf(10L, 11L), 12L))
        assertEquals(0 to true, Compute.streak(setOf(10L), 14L))
    }

    @Test
    fun desvioAfterThreeRoundsInOneEtapa() {
        val clock = FakeClock(0).apply { setDay("2026-11-03") }
        val core = core(clock)
        repeat(3) { core.startTramo("SE-101")!!.also { it.play(3); core.finish(it) } }
        val snap = core.snapshot()
        assertEquals(listOf("PR", "AD"), snap.desvio.map { it.etapa.code })
        assertTrue(snap.desvioLine.contains("Villaseñal"))
    }

    @Test
    fun days() {
        assertEquals("2026-11-04", Days.iso(Days.parseIso("2026-11-04")))
        assertEquals("4 nov", Days.short(Days.parseIso("2026-11-04")))
        assertEquals(0L, Days.parseIso("1970-01-01"))
        // 23:30 in Madrid (UTC+1) on 4 Nov is still 4 Nov.
        val utc = Days.parseIso("2026-11-04") * 86_400_000L + 22 * 3_600_000L + 30 * 60_000L
        assertEquals(Days.parseIso("2026-11-04"), Days.dayOf(utc, 60))
        assertEquals(Days.parseIso("2026-11-05"), Days.dayOf(utc, 120))
    }
}
