package dev.garrido.relanti.android

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garrido.relanti.core.CarBody
import dev.garrido.relanti.core.SemaforoLevel
import dev.garrido.relanti.core.TurboMood
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun rememberAsset(name: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(name) {
        runCatching { context.assets.open(name).use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull()
    }
}

fun carAsset(body: CarBody): String = when (body) {
    CarBody.UTILITARIO -> "relanti-city.jpg"
    CarBody.FAMILIAR -> "relanti-estate.jpg"
    CarBody.FURGO -> "relanti-van.jpg"
}

/**
 * The GPS: a rendered device with eyes drawn on its screen, so they can blink,
 * squint and spin. [clock] is shown instead of eyes in simulacros.
 */
@Composable
fun GpsFace(mood: TurboMood, modifier: Modifier = Modifier, clock: String = "") {
    val device = rememberAsset("satnav-blank.jpg")
    val p = pal()
    val blink = remember { Animatable(1f) }
    LaunchedEffect(mood) {
        blink.snapTo(1f)
        if (TestHooks.still) return@LaunchedEffect
        if (mood == TurboMood.IDLE || mood == TurboMood.CURIOUS || mood == TurboMood.EXPLAIN) {
            while (true) {
                delay(Random.nextLong(3_000, 5_000))
                blink.animateTo(0.08f, tween(70))
                blink.animateTo(1f, tween(110))
            }
        }
    }
    var angle = 45f
    var bob = 0f
    if (!TestHooks.still) {
        val spinner = rememberInfiniteTransition(label = "spin")
        angle = spinner.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle").value
        bob = spinner.animateFloat(-1f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "bob").value
    }
    val glow = p.glow

    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp))) {
        if (device != null) Image(device, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val cy = w * 0.45f
            val left = Offset(w * 0.40f, cy)
            val right = Offset(w * 0.62f, cy)
            val ew = w * 0.075f
            val eh = w * 0.14f
            fun eye(c: Offset, scaleY: Float, dx: Float = 0f, dy: Float = 0f, grow: Float = 1f) {
                val h = eh * scaleY * grow
                drawRoundRect(
                    glow,
                    topLeft = Offset(c.x - ew * grow / 2 + dx, c.y - h / 2 + dy),
                    size = Size(ew * grow, h),
                    cornerRadius = CornerRadius(ew * grow / 2),
                )
            }
            // A small smile under the eyes: the GPS is never sad.
            fun smile(r: Float = w * 0.075f, from: Float = 25f, sweep: Float = 130f) {
                val c = Offset(w * 0.51f, w * 0.54f)
                drawArc(
                    glow, from, sweep, false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(width = ew * 0.42f, cap = StrokeCap.Round),
                )
            }
            when (mood) {
                TurboMood.IDLE -> { eye(left, blink.value); eye(right, blink.value); smile() }
                TurboMood.CURIOUS -> {
                    eye(left, blink.value, w * 0.015f, -w * 0.015f)
                    eye(right, blink.value, w * 0.015f, -w * 0.02f, 1.15f)
                    smile(w * 0.06f)
                }
                TurboMood.EXPLAIN -> {
                    eye(left, blink.value * 0.8f, -w * 0.02f + bob * w * 0.004f)
                    eye(right, blink.value * 0.8f, -w * 0.02f + bob * w * 0.004f)
                    smile()
                }
                TurboMood.HAPPY -> {
                    listOf(left, right).forEach { c ->
                        drawArc(
                            glow, 200f, 140f, false,
                            topLeft = Offset(c.x - ew * 0.9f, c.y - ew * 0.5f),
                            size = Size(ew * 1.8f, ew * 1.6f),
                            style = Stroke(width = ew * 0.45f, cap = StrokeCap.Round),
                        )
                    }
                    smile(w * 0.09f, 15f, 150f)
                }
                TurboMood.RECALCULANDO -> {
                    val r = w * 0.09f
                    val c = Offset(w * 0.51f, cy)
                    rotate(angle, c) {
                        drawArc(
                            glow, 0f, 270f, false,
                            topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                            style = Stroke(width = w * 0.03f, cap = StrokeCap.Round),
                        )
                    }
                }
                TurboMood.SILENT -> {
                    val paint = Paint().apply {
                        color = glow.toArgb()
                        textSize = w * 0.13f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    drawContext.canvas.nativeCanvas.drawText(clock, w * 0.51f, cy + w * 0.045f, paint)
                }
            }
        }
    }
}

/** Where the EXAMEN sign sits in each windscreen render (centre x, centre y, width, height). */
private val signSpots = mapOf(
    "ciudad" to floatArrayOf(0.50f, 0.355f, 0.27f, 0.06f),
    "meseta" to floatArrayOf(0.495f, 0.335f, 0.34f, 0.075f),
    "costa" to floatArrayOf(0.50f, 0.32f, 0.38f, 0.085f),
    "montana" to floatArrayOf(0.50f, 0.30f, 0.42f, 0.095f),
)

/** La Ruta: the view through the windscreen, with the km left to the exam and the light of the hour. */
@Composable
fun Windscreen(landscape: String, kmToExam: Int, hour: Int, modifier: Modifier = Modifier) {
    val key = if (landscape in signSpots) landscape else "ciudad"
    val img = rememberAsset("ruta-$key.jpg")
    val spot = signSpots.getValue(key)
    val tint = when (hour) {
        in 6..8 -> Color(0x33FFB27A)
        in 9..17 -> Color.Transparent
        in 18..20 -> Color(0x40EA5B2A)
        else -> Color(0x660F2240)
    }
    BoxWithConstraints(modifier.aspectRatio(1f).clip(RoundedCornerShape(20.dp))) {
        val w = maxWidth
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(tint))
        val sw = w * spot[2]
        val sh = w * spot[3]
        Box(
            Modifier
                .offset(x = w * spot[0] - sw / 2, y = w * spot[1] - sh / 2)
                .size(sw, sh)
                .background(Color(0xFF1A56B0), RoundedCornerShape(3.dp))
                .border(1.dp, Color.White, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "EXAMEN $kmToExam km", color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = (sh.value * 0.5f).sp, maxLines = 1,
            )
        }
    }
}

@Composable
fun Tile(modifier: Modifier = Modifier, color: Color = pal().surface, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .border(1.dp, pal().line, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier.padding(top = 8.dp, bottom = 6.dp),
        color = pal().ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = pal().blue, contentColor = Color.White),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
    ) { Text(text, fontWeight = FontWeight.SemiBold, color = pal().blueInk) }
}

/** A blue motorway exit sign: "Salida 7 · Taller · Tipos de vehículo". Paid ones are grey. */
@Composable
fun ExitSign(number: Int, name: String, detail: String, toll: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (toll) Color(0xFF6B717C) else Color(0xFF1A56B0)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Text("$number", color = bg, fontWeight = FontWeight.Black, fontSize = 18.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(detail, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
        Text("↗", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SemaforoLight(level: SemaforoLevel, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF16181C)).padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            SemaforoLevel.ROJO to Color(0xFFE5484D),
            SemaforoLevel.AMBAR to Color(0xFFF5B92E),
            SemaforoLevel.VERDE to Color(0xFF3DBB78),
        ).forEach { (l, c) ->
            Box(Modifier.size(size).clip(CircleShape).background(if (l == level) c else c.copy(alpha = 0.18f)))
        }
    }
}

@Composable
fun Placeholder(modifier: Modifier = Modifier) {
    Text(
        "Contenido de MUESTRA: preguntas de prueba, sin verificar con el BOE.",
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(pal().warnSoft)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = pal().warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
    )
}

@Composable
fun Bubble(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(pal().blueSoft)
            .padding(12.dp),
        color = pal().ink, fontSize = 16.sp,
    )
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = pal().ink) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(label, color = pal().ink2, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

fun Long.kmLabel(): String = "%,d".format(this).replace(',', '.')
fun Int.kmLabel(): String = toLong().kmLabel()
