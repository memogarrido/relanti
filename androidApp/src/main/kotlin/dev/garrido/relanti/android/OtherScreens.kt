package dev.garrido.relanti.android

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garrido.relanti.core.CarColour
import dev.garrido.relanti.core.Lines
import dev.garrido.relanti.core.Llegada
import dev.garrido.relanti.core.RoundKind
import dev.garrido.relanti.core.Rules
import dev.garrido.relanti.core.Snapshot
import dev.garrido.relanti.core.TurboMood

/** 6 · Llegada: km for effort, a clear result, and the toll ticket on the second good day. */
@Composable
fun LlegadaScreen(l: Llegada, s: Snapshot, onContinue: () -> Unit) {
    val p = pal()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GpsFace(if (l.good) TurboMood.HAPPY else TurboMood.IDLE, Modifier.width(150.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat("+${l.km} km", "kilómetros", color = p.yellowInk)
            Stat("${l.correct} de ${l.total}", "aciertos", color = if (l.good) p.go else p.ink)
        }
        Text(
            when (l.kind) {
                RoundKind.SIMULACRO -> if (l.good) "¡Apto!" else "No apto esta vez"
                RoundKind.TRAMO -> if (l.good) "¡Tramo superado!" else "Recalculando ruta"
                else -> "Retrovisor hecho"
            },
            fontSize = 28.sp, fontWeight = FontWeight.Black, color = p.ink, textAlign = TextAlign.Center,
        )
        Text(l.line, color = p.ink2, fontSize = 16.sp, textAlign = TextAlign.Center)

        if (l.kind == RoundKind.TRAMO && l.tramo != null && !l.ticketEarned && l.good && l.goodDays < Rules.TICKET_DAYS) {
            Text("Día ${l.goodDays} de ${Rules.TICKET_DAYS} para el ticket de peaje", color = p.ink3, fontSize = 13.sp)
        }

        if (l.ticketEarned && l.tramo != null) TollTicket(l.tramo!!.code, l.tramo!!.title, l.dayLabel, l.kmTotal)

        l.newPoints.forEach { label ->
            Tile(color = p.warnSoft) { Text("+1 punto del carnet · $label", color = p.ink, fontWeight = FontWeight.Bold) }
        }
        l.upgrade?.let { u ->
            Tile(color = p.blueSoft) {
                Column {
                    Text("Mejora para tu Relanti: ${u.name}", color = p.ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(u.detail, color = p.ink2)
                }
            }
        }

        val misses = l.review.filter { !it.correct }
        if (l.kind == RoundKind.SIMULACRO && misses.isNotEmpty()) {
            SectionTitle("Repaso de fallos", Modifier.fillMaxWidth())
            misses.forEach { r ->
                Tile {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${s.pack.tramo(r.item.tramo)?.code ?: ""} · ${r.item.question}", color = p.ink, fontWeight = FontWeight.SemiBold)
                        Text("Tu respuesta: ${r.choiceLabel}", color = p.stop, fontSize = 14.sp)
                        Text("Correcta: ${r.answerLabel}", color = p.go, fontSize = 14.sp)
                        if (r.item.explanation.isNotBlank()) Text(r.item.explanation, color = p.ink2, fontSize = 14.sp)
                        r.item.source?.let { Text(it.label, color = p.ink3, fontSize = 12.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton(if (l.ticketEarned) "Guardar en el parasol" else "Continuar", onClick = onContinue)
    }
}

@Composable
fun TollTicket(code: String, title: String, day: String, km: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF6D6))
            .border(1.dp, Color(0xFFE2C46A), RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        Text("AUTOPISTA RELANTI · TICKET", color = Color(0xFF7A5600), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("$code $title", color = Color(0xFF1F232A), fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text("$day · km ${km.kmLabel()}", color = Color(0xFF535A66), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Centro de examen: simulacros, the Semáforo and Puntos negros. */
@Composable
fun ExamenScreen(model: AppModel) {
    val s = model.snapshot
    val p = pal()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Centro de examen", fontSize = 28.sp, fontWeight = FontWeight.Black, color = p.ink)
        Tile {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SemaforoLight(s.semaforo.level, size = 20.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Semáforo: ${s.semaforo.title}", fontWeight = FontWeight.Black, color = p.ink, fontSize = 18.sp)
                    Text(s.semaforo.message, color = p.ink2)
                }
            }
        }
        Tile {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Simulacro", fontWeight = FontWeight.Black, color = p.ink, fontSize = 20.sp)
                Text(
                    "${Rules.SIM_QUESTIONS} preguntas · ${Rules.SIM_SECONDS / 60} minutos · máximo ${Rules.SIM_MAX_MISTAKES} fallos. " +
                        "${s.gpsName} va en silencio y repasa cada fallo al final.",
                    color = p.ink2,
                )
                if (s.pack.items.size < Rules.SIM_QUESTIONS) {
                    Text("Con el paquete de muestra salen ${s.pack.items.size} preguntas.", color = p.warn, fontSize = 13.sp)
                }
                PrimaryButton("Empezar simulacro", enabled = s.canStartSimulacro) { model.open(model.core.startSimulacro()) }
                if (!s.canStartSimulacro) {
                    Text("Ya has hecho el simulacro gratis de hoy. Mañana hay otro, o abre la autopista.", color = p.ink3, fontSize = 13.sp)
                }
            }
        }
        if (s.puntosNegros.isNotEmpty()) {
            SecondaryButton("Practicar puntos negros (${s.puntosNegros.size})") { model.open(model.core.startPuntosNegros()) }
        }
        if (s.simulacros.isNotEmpty()) {
            SectionTitle("Tus simulacros")
            s.simulacros.reversed().forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(r.dayLabel, color = p.ink2, modifier = Modifier.width(70.dp))
                    Text("${r.mistakes} ${if (r.mistakes == 1) "fallo" else "fallos"} de ${r.total}", color = p.ink, modifier = Modifier.weight(1f))
                    Text(if (r.passed) "APTO" else "NO APTO", color = if (r.passed) p.go else p.stop, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/** 10 · Guantera: tickets on the sun visor, magnets on the fridge, trucos and puntos negros. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuanteraScreen(model: AppModel) {
    val s = model.snapshot
    val p = pal()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Guantera", fontSize = 28.sp, fontWeight = FontWeight.Black, color = p.ink, modifier = Modifier.weight(1f))
            Text("${s.tickets.size} de ${s.totalTramos}", color = p.ink2, fontWeight = FontWeight.Bold)
        }
        SectionTitle("Parasol · tickets de peaje")
        if (s.tickets.isEmpty()) {
            Text("Supera un tramo en dos días distintos y su ticket aparece aquí.", color = p.ink3)
        }
        s.tickets.forEachIndexed { i, t ->
            TollTicket(t.tramo.code, t.tramo.title, t.dayLabel, t.kmAt, Modifier.rotate(if (i % 2 == 0) -1f else 1f))
        }
        SectionTitle("Nevera · imanes de etapa · ${s.magnets.size} de ${s.etapas.size}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.etapas.forEach { e ->
                val has = e.magnet
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (has) p.sunset else p.surface)
                        .border(1.dp, if (has) p.sunset else p.line, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (has) e.etapa.name.uppercase().split(" ").last() else "?",
                        color = if (has) Color.White else p.ink3, fontWeight = FontWeight.Black,
                        fontSize = if (has) 11.sp else 22.sp, textAlign = TextAlign.Center,
                    )
                }
            }
        }
        val started = s.etapas.flatMap { it.tramos }.filter { it.goodDays > 0 || it.ticketed }.mapNotNull { s.pack.truco(it.tramo.truco) }
        SectionTitle("Trucos de ${s.gpsName} · ${started.size}")
        if (started.isEmpty()) Text("Cada tramo empieza con un truco. Los que veas se guardan aquí.", color = p.ink3)
        started.forEach { t ->
            Tile {
                Column {
                    Text(t.title, fontWeight = FontWeight.Bold, color = p.ink)
                    Text(t.body, color = p.ink2, fontSize = 14.sp)
                    t.source?.let { Text(it.label, color = p.ink3, fontSize = 12.sp) }
                }
            }
        }
        SectionTitle("Puntos negros · ${s.puntosNegros.size}")
        if (s.puntosNegros.isEmpty()) Text("Las preguntas que falles a menudo aparecerán aquí.", color = p.ink3)
        s.puntosNegros.forEach { pn ->
            Tile(color = p.stopSoft) {
                Column {
                    Text(pn.item.question, color = p.ink, fontWeight = FontWeight.SemiBold)
                    Text("Correcta: ${pn.item.letter(pn.item.answer)}. ${pn.item.options[pn.item.answer]}", color = p.go, fontSize = 14.sp)
                    Text("Fallada ${pn.misses} veces", color = p.ink3, fontSize = 12.sp)
                }
            }
        }
    }
}

/** 11 · Yo: your licence card, your car and the settings. */
@Composable
fun YoScreen(model: AppModel) {
    val s = model.snapshot
    val p = pal()
    val settings = s.settings ?: model.core.settingsOrDefault()
    var confirmReset by remember { mutableStateOf(false) }
    var aprobado by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun save(block: (dev.garrido.relanti.core.Settings) -> dev.garrido.relanti.core.Settings) {
        model.core.saveSettings(block(settings))
        model.refresh()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Yo", fontSize = 28.sp, fontWeight = FontWeight.Black, color = p.ink, modifier = Modifier.weight(1f))
            Text("Rango: ${s.rango.name}", color = p.ink2, fontWeight = FontWeight.Bold)
        }
        // The licence card.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFF4E6C8)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PERMISO B · RELANTI", color = Color(0xFF14438A), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text("${s.points} / ${Rules.MAX_POINTS}", color = Color(0xFF1F232A), fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(Rules.MAX_POINTS) { i ->
                    Box(
                        Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (i < s.points) Color(0xFF1A56B0) else Color(0x331A56B0)),
                    )
                }
            }
            Text(
                "Cómo se ganan: 1 punto por etapa completa y 3 por hitos de simulacro. Aquí nunca se pierden, al revés que en la vida real.",
                color = Color(0xFF535A66), fontSize = 13.sp,
            )
        }

        Tile {
            Row(verticalAlignment = Alignment.CenterVertically) {
                rememberAsset(carAsset(settings.carBody))?.let {
                    Image(
                        it, null,
                        Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
                            .alpha(if (s.upgradeIndex == 0) 0.6f else 1f),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(settings.carName.ifBlank { "Tu Relanti" }, fontWeight = FontWeight.Black, color = p.ink, fontSize = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val colour = CarColour.byId(settings.carColour)
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(colour.hex)).alpha(if (s.upgradeIndex == 0) 0.5f else 1f))
                        Spacer(Modifier.width(6.dp))
                        Text("${bodyName(settings.carBody)} · ${colour.name}", color = p.ink2, fontSize = 14.sp)
                    }
                    Text("Estado: ${s.upgrade.name}", color = p.ink, fontSize = 14.sp)
                    s.nextUpgrade?.let { Text("Próxima mejora a ${it.points} pts: ${it.name.lowercase()}", color = p.ink3, fontSize = 13.sp) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("${s.km.kmLabel()}", "km", Modifier.weight(1f), color = p.yellowInk)
            Stat("${s.streak}", "días al volante", Modifier.weight(1f), color = p.sunset)
            Stat("${s.tickets.size}", "tickets", Modifier.weight(1f))
        }
        SecondaryButton("Taller: color, nombre y carrocería") { model.screen = Screen.Taller }

        SectionTitle("Apariencia")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "Sistema", "light" to "Claro", "dark" to "Oscuro").forEach { (id, label) ->
                val selected = settings.theme == id
                Text(
                    label,
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) p.blue else p.surface)
                        .border(1.dp, if (selected) p.blue else p.line, RoundedCornerShape(10.dp))
                        .clickable { save { it.copy(theme = id) } }
                        .padding(vertical = 10.dp),
                    color = if (selected) Color.White else p.ink, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                )
            }
        }

        SectionTitle("Pase de autopista (prueba)")
        Tile {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Autopista abierta", fontWeight = FontWeight.Bold, color = p.ink)
                    Text(
                        "Mientras no hay pagos, este interruptor abre todas las etapas y los simulacros sin límite.",
                        color = p.ink2, fontSize = 13.sp,
                    )
                }
                Switch(checked = settings.autopista, onCheckedChange = { on -> save { it.copy(autopista = on) } })
            }
        }

        SectionTitle("¿Ya tienes la L?")
        SecondaryButton(if (s.aprobado) "¡Aprobado! 🎉" else "He aprobado el examen") {
            model.core.markAprobado(); model.refresh(); aprobado = true
        }

        SectionTitle("Fuentes")
        Text(
            listOfNotNull(
                s.pack.attribution?.boe,
                s.pack.attribution?.consolidatedNotice,
                "Paquete de contenido v${s.pack.contentVersion}" + if (s.placeholder) " (muestra)" else "",
            ).joinToString("\n"),
            color = p.ink2, fontSize = 13.sp,
        )

        SectionTitle("Tus datos")
        SecondaryButton("Descargar mis datos (JSON)") {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TEXT, model.core.exportJson())
            }
            context.startActivity(Intent.createChooser(send, "Mis datos de Relanti"))
        }
        TextButton(onClick = { confirmReset = true }) { Text("Borrar todo", color = p.stop) }
        Spacer(Modifier.height(12.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("¿Borrar todo?") },
            text = { Text("Se borran tu progreso, tu coche y tus ajustes de este móvil. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    model.core.resetAll(); model.refresh(); model.screen = Screen.Onboarding
                }) { Text("Borrar", color = p.stop) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } },
            containerColor = p.surface,
        )
    }
    if (aprobado) {
        AlertDialog(
            onDismissRequest = { aprobado = false },
            title = { Text("¡APROBADO!") },
            text = { Text(Lines.APROBADO) },
            confirmButton = { TextButton(onClick = { aprobado = false }) { Text("¡Vamos!") } },
            containerColor = p.surface,
        )
    }
}
