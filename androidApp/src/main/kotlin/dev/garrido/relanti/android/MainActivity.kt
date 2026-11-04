package dev.garrido.relanti.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.garrido.relanti.core.Llegada
import dev.garrido.relanti.core.QuizSession
import dev.garrido.relanti.core.RelantiCore
import dev.garrido.relanti.core.Snapshot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val core = (application as RelantiApplication).core
        setContent { RelantiRoot(core) }
    }
}

sealed interface Screen {
    data object Onboarding : Screen
    data object Taller : Screen
    data object Home : Screen
    data object Panel : Screen
    class Quiz(val session: QuizSession) : Screen
    class Arrival(val llegada: Llegada) : Screen
}

/** Holds the core and the latest snapshot, and starts sessions. */
class AppModel(val core: RelantiCore) {
    var snapshot by mutableStateOf(core.snapshot())
        private set
    var screen by mutableStateOf<Screen>(if (core.isOnboarded) Screen.Home else Screen.Onboarding)
    var message by mutableStateOf<String?>(null)

    fun refresh() { snapshot = core.snapshot() }

    fun open(session: QuizSession?) {
        if (session == null) {
            message = "No hay preguntas para eso todavía."
        } else {
            screen = Screen.Quiz(session)
        }
    }

    fun startTramo(code: String) {
        val view = snapshot.etapas.flatMap { it.tramos }.firstOrNull { it.tramo.code == code }
        if (view != null && !view.open) {
            message = "Autopista de peaje: esta etapa se abre con el pago único. Para probarla, activa el pase en Yo."
            return
        }
        open(core.startTramo(code))
    }

    fun finish(session: QuizSession) {
        val llegada = core.finish(session)
        refresh()
        screen = if (llegada != null) Screen.Arrival(llegada) else Screen.Home
    }
}

@Composable
fun RelantiRoot(core: RelantiCore) {
    val model = remember { AppModel(core) }
    val snapshot = model.snapshot
    val dark = when (snapshot.settings?.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    RelantiTheme(dark) {
        Box(Modifier.fillMaxSize().background(pal().bg).safeDrawingPadding()) {
            when (val s = model.screen) {
                Screen.Onboarding, Screen.Taller -> OnboardingScreen(
                    initial = core.settingsOrDefault(),
                    editing = s == Screen.Taller,
                    onDone = { core.saveSettings(it); model.refresh(); model.screen = Screen.Home },
                    onCancel = { model.screen = Screen.Home },
                )
                Screen.Home -> MainTabs(model)
                Screen.Panel -> {
                    BackHandler { model.screen = Screen.Home }
                    PanelScreen(snapshot, onBack = { model.screen = Screen.Home }, onTramo = model::startTramo)
                }
                is Screen.Quiz -> QuizScreen(
                    session = s.session,
                    onDone = { model.finish(s.session) },
                    onExit = { model.finish(s.session) },
                )
                is Screen.Arrival -> {
                    BackHandler { model.screen = Screen.Home }
                    LlegadaScreen(s.llegada, snapshot, onContinue = { model.screen = Screen.Home })
                }
            }
            model.message?.let { msg -> InfoDialog(msg) { model.message = null } }
        }
    }
}

@Composable
fun MainTabs(model: AppModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Ruta" to "🛣", "Examen" to "🚦", "Guantera" to "🎫", "Yo" to "🪪")
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> RutaScreen(model)
                1 -> ExamenScreen(model)
                2 -> GuanteraScreen(model)
                else -> YoScreen(model)
            }
        }
        NavigationBar(containerColor = pal().surface) {
            tabs.forEachIndexed { i, (label, icon) ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Text(icon, fontSize = 20.sp) },
                    label = { Text(label, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = pal().blueSoft),
                )
            }
        }
    }
}

@Composable
fun InfoDialog(text: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Entendido") } },
        text = { Text(text) },
        containerColor = pal().surface,
    )
}
