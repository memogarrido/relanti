package dev.garrido.relanti.android

import android.app.DatePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garrido.relanti.core.CarBody
import dev.garrido.relanti.core.CarColour
import dev.garrido.relanti.core.Days
import dev.garrido.relanti.core.Lines
import dev.garrido.relanti.core.Rules
import dev.garrido.relanti.core.Settings
import dev.garrido.relanti.core.TurboMood
import java.util.Calendar

/** 1 · Subir al coche: car, colour, names and an optional exam date. Also the Taller from Yo. */
@Composable
fun OnboardingScreen(initial: Settings, editing: Boolean, onDone: (Settings) -> Unit, onCancel: () -> Unit) {
    var body by remember { mutableStateOf(initial.carBody) }
    var colour by remember { mutableStateOf(initial.carColour) }
    var carName by remember { mutableStateOf(initial.carName) }
    var gpsName by remember { mutableStateOf(initial.gpsName.ifBlank { "Turbo" }) }
    var examDay by remember { mutableLongStateOf(initial.examDay) }
    val context = LocalContext.current
    val p = pal()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Taller", fontSize = 28.sp, fontWeight = FontWeight.Black, color = p.ink, modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancelar") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GpsFace(TurboMood.IDLE, Modifier.width(110.dp))
                Spacer(Modifier.width(10.dp))
                Bubble(Lines.hello(gpsName.ifBlank { "Turbo" }), Modifier.weight(1f))
            }
        }

        SectionTitle("Elige tu Relanti")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CarBody.entries.forEach { b ->
                val selected = b == body
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) p.blueSoft else p.surface)
                        .border(if (selected) 2.dp else 1.dp, if (selected) p.blue else p.line, RoundedCornerShape(14.dp))
                        .clickable { body = b }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    rememberAsset(carAsset(b))?.let {
                        Image(it, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    }
                    Text(bodyName(b), fontWeight = FontWeight.SemiBold, color = p.ink, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        SectionTitle("Color")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CarColour.all.forEach { c ->
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(c.hex))
                        .border(if (c.id == colour) 3.dp else 1.dp, if (c.id == colour) p.blue else p.line, CircleShape)
                        .clickable { colour = c.id },
                )
            }
        }
        Text(
            CarColour.byId(colour).name + if (!editing) " · empieza apagado y brilla con Pintura nueva" else "",
            color = p.ink2, fontSize = 13.sp,
        )

        OutlinedTextField(
            value = carName, onValueChange = { carName = it.take(24) },
            label = { Text("Nombre de tu coche (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("¿Cómo se llama tu GPS?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Rules.GPS_NAME_SUGGESTIONS.forEach { n ->
                val selected = n == gpsName
                Text(
                    n,
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) p.blue else p.surface)
                        .border(1.dp, if (selected) p.blue else p.line, RoundedCornerShape(50))
                        .clickable { gpsName = n }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (selected) Color.White else p.ink, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        OutlinedTextField(
            value = gpsName, onValueChange = { gpsName = it.take(16) },
            label = { Text("O escribe el tuyo") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("Fecha del examen (opcional)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (examDay >= 0) Days.long(examDay) else "Sin fecha",
                color = p.ink, fontSize = 17.sp, modifier = Modifier.weight(1f),
            )
            if (examDay >= 0) TextButton(onClick = { examDay = -1 }) { Text("Quitar") }
            TextButton(onClick = {
                val cal = Calendar.getInstance()
                DatePickerDialog(context, { _, y, m, d -> examDay = Days.fromCivil(y, m + 1, d) },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }) { Text("Elegir") }
        }

        Spacer(Modifier.height(4.dp))
        PrimaryButton(if (editing) "Guardar" else "Arrancar") {
            onDone(
                initial.copy(
                    carBody = body, carColour = colour, carName = carName.trim(),
                    gpsName = gpsName.trim().ifBlank { "Turbo" }, examDay = examDay,
                ),
            )
        }
        if (!editing) Text("No hace falta cuenta. Todo se guarda en tu móvil.", color = p.ink3, fontSize = 13.sp)
    }
}

fun bodyName(b: CarBody) = when (b) {
    CarBody.UTILITARIO -> "Utilitario"
    CarBody.FAMILIAR -> "Familiar"
    CarBody.FURGO -> "Furgo"
}
