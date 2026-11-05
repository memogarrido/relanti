package dev.garrido.relanti.android

import android.graphics.Bitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import dev.garrido.relanti.core.Fixtures
import dev.garrido.relanti.core.RelantiCore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Plays the app the way a learner does, from the shared fixtures, and saves a
 * screenshot of every screen to build/screenshots (uploaded by CI).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class PlaythroughTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val pack: String by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "content/packs/relanti-b.json") }
            .first { it.exists() }
            .readText()
    }

    @Before
    fun hooks() {
        TestHooks.oracle = true
        TestHooks.still = true
    }

    private fun start(state: String): RelantiCore {
        val core = Fixtures.core(pack, state)
        rule.setContent { RelantiRoot(core) }
        rule.waitForIdle()
        return core
    }

    private fun shoot(name: String) {
        rule.waitForIdle()
        val dir = File("build/screenshots").apply { mkdirs() }
        // captureToImage times out under Robolectric; draw the window instead.
        repeat(10) {
            rule.mainClock.advanceTimeByFrame()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bitmap))
        File(dir, "android-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun text(t: String): SemanticsNodeInteraction = rule.onNodeWithText(t, substring = true)

    private fun click(t: String) {
        val node = text(t)
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    private fun oracle(): Int {
        val node = rule.onNodeWithTag("quiz.oracle", useUnmergedTree = true).fetchSemanticsNode()
        return node.config[SemanticsProperties.Text].first().text.toInt()
    }

    private fun answer(right: Boolean) {
        val correct = oracle()
        val pick = if (right) correct else (correct + 1) % 3
        val node = rule.onNodeWithTag("option.$pick")
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    /** Fails with the screen's semantics tree, so a CI log says what was there instead. */
    private fun expect(t: String) {
        if (!exists(t)) throw AssertionError("\"$t\" is not on screen:\n" + rule.onRoot(useUnmergedTree = true).printToString(maxDepth = 30))
    }

    private fun exists(t: String): Boolean = rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun firstLaunchToLaRuta() {
        start(Fixtures.FRESH)
        expect("Te llevo hasta el examen")
        shoot("01-subir-al-coche")
        click("Arrancar")
        expect("dice")
        shoot("02-la-ruta-dia-1")
    }

    @Test
    fun secondGoodDayEarnsTheTollTicket() {
        start(Fixtures.TICKET_READY)
        click("Arrancar")
        expect("Entendido, ¡vamos!")
        shoot("03-truco")
        click("Entendido, ¡vamos!")
        shoot("04-pregunta")
        // One miss is still a good round.
        answer(right = false)
        expect("Recalculando")
        shoot("05-recalculando")
        click("Continuar")
        repeat(7) { i ->
            answer(right = true)
            if (i == 0) shoot("06-acierto")
            click(if (i == 6) "Llegar" else "Continuar")
        }
        expect("Ticket de peaje")
        shoot("07-llegada-ticket")
        click("Guardar en el parasol")
        rule.onNodeWithTag("tab.Guantera").performClick()
        rule.waitForIdle()
        expect("AUTOPISTA RELANTI")
        shoot("08-guantera")
    }

    @Test
    fun midweekScreens() {
        start(Fixtures.MIDWEEK)
        shoot("10-la-ruta-midweek")
        click("Panel de tramos")
        shoot("11-panel")
        click("‹ Ruta")
        listOf("Examen", "Guantera", "Yo").forEachIndexed { i, tab ->
            rule.onNodeWithTag("tab.$tab").performClick()
            rule.waitForIdle()
            shoot("${12 + i}-${tab.lowercase()}")
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun midweekDark() {
        start(Fixtures.MIDWEEK)
        shoot("20-la-ruta-oscuro")
        click("Panel de tramos")
        shoot("21-panel-oscuro")
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    fun smallPhone() {
        start(Fixtures.TICKET_READY)
        shoot("30-la-ruta-pequeno")
        click("Arrancar")
        click("Entendido, ¡vamos!")
        answer(right = false)
        shoot("31-recalculando-pequeno")
    }
}
