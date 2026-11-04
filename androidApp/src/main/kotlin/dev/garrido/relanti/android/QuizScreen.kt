package dev.garrido.relanti.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garrido.relanti.core.Lines
import dev.garrido.relanti.core.QuizSession
import dev.garrido.relanti.core.SessionPhase
import dev.garrido.relanti.core.TurboMood
import kotlinx.coroutines.delay

/** 4, 5, 8 and 9 · Truco, question, miss and simulacro, all in one question frame. */
@Composable
fun QuizScreen(session: QuizSession, onDone: () -> Unit, onExit: () -> Unit) {
    // The session is plain Kotlin; bump this after each call so Compose redraws.
    var tick by remember { mutableIntStateOf(0) }
    var askExit by remember { mutableStateOf(false) }
    var questionStart by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var secondsLeft by remember { mutableIntStateOf(session.timeLimitSeconds) }
    val p = pal()
    tick.let { } // read so the screen recomposes

    BackHandler { askExit = true }

    if (session.isSimulacro) {
        LaunchedEffect(session) {
            while (secondsLeft > 0 && session.phase != SessionPhase.DONE) {
                delay(1_000)
                secondsLeft--
            }
            if (session.phase != SessionPhase.DONE) {
                session.timeUp()
                tick++
            }
        }
    }
    LaunchedEffect(session.phase, tick) {
        if (session.phase == SessionPhase.DONE) onDone()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { askExit = true }) { Text("✕", fontSize = 20.sp, color = p.ink2) }
            Text(
                if (session.phase == SessionPhase.INTRO) session.title else session.progressLabel,
                fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f),
            )
            if (session.isSimulacro) {
                Text(clock(secondsLeft), color = if (secondsLeft < 300) p.stop else p.ink2, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
            }
        }
        LinearProgressIndicator(
            progress = { if (session.total == 0) 0f else session.answeredCount.toFloat() / session.total },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = p.blue, trackColor = p.line,
        )

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (session.phase) {
                SessionPhase.INTRO -> TrucoIntro(session) { session.start(); questionStart = System.currentTimeMillis(); tick++ }
                SessionPhase.QUESTION, SessionPhase.FEEDBACK -> QuestionView(
                    session, clock(secondsLeft),
                    onAnswer = { choice ->
                        session.answer(choice, System.currentTimeMillis() - questionStart)
                        questionStart = System.currentTimeMillis()
                        tick++
                    },
                    onNext = { session.next(); questionStart = System.currentTimeMillis(); tick++ },
                )
                SessionPhase.DONE -> Text("…", color = p.ink2)
            }
        }
    }

    if (askExit) {
        AlertDialog(
            onDismissRequest = { askExit = false },
            title = { Text("¿Salir del tramo?") },
            text = {
                Text(
                    if (session.answeredCount > 0) "Guardamos lo que llevas respondido. El tramo cuenta entero solo si lo terminas."
                    else "Aún no has respondido nada.",
                )
            },
            confirmButton = { TextButton(onClick = { askExit = false; onExit() }) { Text("Salir") } },
            dismissButton = { TextButton(onClick = { askExit = false }) { Text("Seguir") } },
            containerColor = p.surface,
        )
    }
}

private fun clock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun TrucoIntro(session: QuizSession, onGo: () -> Unit) {
    val p = pal()
    val truco = session.truco ?: return
    GpsFace(TurboMood.EXPLAIN, Modifier.width(170.dp))
    Tile {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Truco de ${session.gpsName} · ${session.tramo?.code ?: ""}", color = p.ink2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(truco.title, color = p.ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text(truco.body, color = p.ink, fontSize = 16.sp)
            truco.source?.let { Text(it.label, color = p.ink3, fontSize = 13.sp) }
        }
    }
    PrimaryButton("Entendido, ¡vamos!", onClick = onGo)
}

@Composable
private fun QuestionView(session: QuizSession, clockText: String, onAnswer: (Int) -> Unit, onNext: () -> Unit) {
    val p = pal()
    val item = session.current
    val feedback = session.phase == SessionPhase.FEEDBACK
    Row(verticalAlignment = Alignment.Top) {
        Text(item.question, color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        // The GPS always sits in the corner, watching the road with you.
        GpsFace(session.mood, Modifier.width(76.dp), clock = clockText)
    }
    if (item.novedad) {
        Text("NOVEDAD", color = p.sunset, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
    item.options.forEachIndexed { i, option ->
        val isAnswer = i == item.answer
        val isChoice = i == session.lastChoice
        val (bg, border) = when {
            feedback && isAnswer -> p.goSoft to p.go
            feedback && isChoice -> p.stopSoft to p.stop
            else -> p.surface to p.line
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .border(if (feedback && (isAnswer || isChoice)) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
                .clickable(enabled = !feedback) { onAnswer(i) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (feedback && isAnswer) p.go else p.blueSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.letter(i), fontWeight = FontWeight.Black,
                    color = if (feedback && isAnswer) Color.White else p.blueInk,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(option, color = p.ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (feedback && isAnswer) Text("✓", color = p.go, fontWeight = FontWeight.Black, fontSize = 20.sp)
            if (feedback && isChoice && !isAnswer) Text("✕", color = p.stop, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    }
    if (feedback) {
        Tile(color = if (session.lastCorrect) p.goSoft else p.blueSoft) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (session.lastCorrect) session.gpsLine else Lines.RECALCULANDO,
                    fontWeight = FontWeight.Black, color = if (session.lastCorrect) p.go else p.blueInk, fontSize = 18.sp,
                )
                if (!session.lastCorrect) Text(session.gpsLine, color = p.ink, fontSize = 16.sp)
                else if (item.explanation.isNotBlank()) Text(item.explanation, color = p.ink, fontSize = 15.sp)
                item.source?.let { Text(it.label, color = p.ink3, fontSize = 13.sp) }
            }
        }
        PrimaryButton(if (session.index + 1 >= session.total) "Llegar" else "Continuar", onClick = onNext)
    } else if (session.isSimulacro) {
        Spacer(Modifier.height(4.dp))
        Text("${session.gpsName} va en silencio: como en el examen, la corrección llega al final.", color = p.ink3, fontSize = 13.sp)
    }
}
