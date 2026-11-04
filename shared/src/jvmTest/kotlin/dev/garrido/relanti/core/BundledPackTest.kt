package dev.garrido.relanti.core

import java.io.File

import kotlin.test.Test
import kotlin.test.assertTrue

/** The pack both apps bundle must load. */
class BundledPackTest {
    private val packText: String by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "content/packs/relanti-b.json") }
            .first { it.exists() }
            .readText()
    }

    @Test
    fun fixturesWorkOnTheBundledPack() {
        Fixtures.names.forEach { Fixtures.core(packText, it).snapshot() }
        val core = Fixtures.core(packText, Fixtures.TICKET_READY)
        val code = core.snapshot().next.tramo!!.code
        assertTrue(Fixtures.play(core, core.startTramo(code), 0)!!.ticketEarned)
    }

    @Test
    fun bundledPackLoads() {
        val pack = Pack.parse(packText)
        assertTrue(pack.items.isNotEmpty())
        pack.tramos.forEach { t ->
            assertTrue(pack.itemsOf(t.code).size >= Rules.TRAMO_QUESTIONS, "${t.code} needs ${Rules.TRAMO_QUESTIONS} items")
        }
    }
}
