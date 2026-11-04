package dev.garrido.relanti.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garrido.relanti.core.NextKind
import dev.garrido.relanti.core.Snapshot
import dev.garrido.relanti.core.TramoStatus
import dev.garrido.relanti.core.TurboMood
import java.util.Calendar

/** 2 · La Ruta: the windscreen, what's next, and the traffic light. */
@Composable
fun RutaScreen(model: AppModel) {
    val s = model.snapshot
    val p = pal()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (s.placeholder) Placeholder()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (s.streakBroken) "Nueva ruta: día 1" else "${s.streak} ${if (s.streak == 1) "día" else "días"} al volante",
                color = p.sunset, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            Text("${s.km.kmLabel()} km", color = p.yellowInk, fontWeight = FontWeight.Black)
        }
        Windscreen(s.landscape, s.kmToExam, Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Modifier.fillMaxWidth())
        if (s.daysToExam >= 0) {
            Text(
                "Examen el ${s.examDayLabel} · ${if (s.daysToExam == 0L) "¡hoy!" else "faltan ${s.daysToExam} días"}",
                color = p.ink2, fontSize = 13.sp,
            )
        }

        Tile {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GpsFace(TurboMood.IDLE, Modifier.width(64.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${s.gpsName} dice", color = p.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(s.next.line, color = p.ink, fontSize = 16.sp)
                    }
                }
                PrimaryButton("Arrancar") {
                    when (s.next.kind) {
                        NextKind.TRAMO -> s.next.tramo?.let { model.startTramo(it.code) }
                        NextKind.RETROVISOR -> model.open(model.core.startRetrovisor())
                        NextKind.SIMULACRO -> {
                            if (s.canStartSimulacro) model.open(model.core.startSimulacro())
                            else model.message = "Ya has hecho el simulacro gratis de hoy. Mañana hay otro."
                        }
                    }
                }
            }
        }

        if (s.desvio.isNotEmpty()) {
            Tile(color = p.blueSoft) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.desvioLine, color = p.ink, fontWeight = FontWeight.SemiBold)
                    s.desvio.forEach { e ->
                        val first = e.tramos.firstOrNull { !it.ticketed }
                        ExitSign(e.salida, e.etapa.name, if (e.open) e.etapa.topic else "Autopista de peaje", !e.open) {
                            first?.let { model.startTramo(it.tramo.code) }
                        }
                    }
                }
            }
        }

        Tile {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SemaforoLight(s.semaforo.level)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Semáforo de examen: ${s.semaforo.title}", fontWeight = FontWeight.Bold, color = p.ink)
                    Text(s.semaforo.message, color = p.ink2, fontSize = 14.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Panel de tramos", Modifier.weight(1f)) { model.screen = Screen.Panel }
            SecondaryButton(
                "Retrovisor${if (s.retrovisorDue > 0) " (${s.retrovisorDue})" else ""}",
                Modifier.weight(1f), enabled = s.retrovisorDue > 0,
            ) { model.open(model.core.startRetrovisor()) }
        }
    }
}

/** 3 · Panel: every tramo on an amber LED motorway panel, with an exit sign per etapa. */
@Composable
fun PanelScreen(s: Snapshot, onBack: () -> Unit, onTramo: (String) -> Unit) {
    val p = pal()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Ruta") }
            Text(
                "Panel · ${s.etapas.size} etapas · ${s.totalTramos} tramos",
                fontWeight = FontWeight.Bold, color = p.ink,
            )
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(s.etapas, key = { it.etapa.code }) { e ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExitSign(
                        e.salida, e.etapa.name,
                        when {
                            !e.open -> "Autopista de peaje · ${e.etapa.topic}"
                            e.tramos.isEmpty() -> "${e.etapa.topic} · próximamente"
                            else -> "${e.etapa.topic} · ${e.ticketed} de ${e.tramos.size}" + if (e.magnet) " · imán ✓" else ""
                        },
                        toll = !e.open,
                    ) { e.tramos.firstOrNull { !it.ticketed }?.let { onTramo(it.tramo.code) } }
                    if (e.tramos.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.ledBg).padding(vertical = 4.dp),
                        ) {
                            e.tramos.forEach { t ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { onTramo(t.tramo.code) }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(t.tramo.code, color = p.led, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(t.tramo.title, color = Color(0xFFFFD27A), modifier = Modifier.weight(1f), fontSize = 14.sp)
                                    Text(
                                        t.statusLabel,
                                        color = when (t.status) {
                                            TramoStatus.LLEGADO -> Color(0xFF7CE0A3)
                                            TramoStatus.RETENCION -> Color(0xFFFF8A5C)
                                            TramoStatus.EN_MARCHA -> Color.White
                                            TramoStatus.PEAJE -> Color(0xFF9AA0AA)
                                            TramoStatus.FLUIDO -> p.led
                                        },
                                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Box(Modifier.padding(8.dp)) }
        }
    }
}
