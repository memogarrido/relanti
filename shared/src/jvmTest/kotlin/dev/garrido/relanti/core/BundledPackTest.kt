package dev.garrido.relanti.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The pack both apps bundle must load. */
class BundledPackTest {
    @Test
    fun bundledPackLoads() {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "content/packs/relanti-b.json") }
            .first { it.exists() }
        val pack = Pack.parse(file.readText())
        assertTrue(pack.items.isNotEmpty())
        pack.tramos.forEach { t ->
            assertTrue(pack.itemsOf(t.code).size >= Rules.TRAMO_QUESTIONS, "${t.code} needs ${Rules.TRAMO_QUESTIONS} items")
        }
    }
}
