package dev.garrido.relanti.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the device stores: an append-only log of rounds and milestones, plus
 * one small settings record. Everything the learner sees is computed from it.
 */
@Serializable
data class ProgressLog(
    val version: Int = 1,
    val rounds: List<Round> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val settings: Settings? = null,
) {
    fun encode(): String = codec.encodeToString(serializer(), this)

    companion object {
        internal val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun decode(text: String?): ProgressLog =
            if (text.isNullOrBlank()) ProgressLog()
            else try { codec.decodeFromString(serializer(), text) } catch (e: Exception) { ProgressLog() }
    }
}

enum class RoundKind { TRAMO, RETROVISOR, ITV, SIMULACRO }

@Serializable
data class Round(
    val id: String,
    val kind: RoundKind,
    val tramo: String? = null,
    val startedAt: Long,
    /** Local calendar day (days since 1970-01-01 in the learner's time zone). */
    val day: Long,
    val utcOffsetMinutes: Int,
    val contentVersion: Int,
    val answers: List<Answer>,
) {
    val mistakes: Int get() = answers.count { !it.correct }
    val activeMs: Long get() = answers.sumOf { minOf(it.ms, 90_000L) }
}

@Serializable
data class Answer(
    val item: String,
    /** -1 when the time ran out before answering. */
    val choice: Int,
    val correct: Boolean,
    val ms: Long,
)

enum class MilestoneKind { POINT, UPGRADE, APROBADO }

@Serializable
data class Milestone(
    val id: String,
    val kind: MilestoneKind,
    val key: String,
    val at: Long,
)

enum class CarBody { UTILITARIO, FAMILIAR, FURGO }

@Serializable
data class Settings(
    val carBody: CarBody = CarBody.UTILITARIO,
    /** One of [CarColour.all] ids. */
    val carColour: String = "coral",
    val carName: String = "",
    val gpsName: String = "Turbo",
    /** Exam date as a local day number, or -1 when not set. */
    val examDay: Long = -1,
    val voice: Boolean = false,
    /** "system", "light" or "dark". */
    val theme: String = "system",
    /** Test switch until store purchases exist: opens every etapa. */
    val autopista: Boolean = false,
    val updatedAt: Long = 0,
)

class CarColour(val id: String, val name: String, val hex: Long) {
    companion object {
        val all = listOf(
            CarColour("coral", "Coral", 0xFFE5574B),
            CarColour("autopista", "Azul autopista", 0xFF1A56B0),
            CarColour("linea", "Amarillo línea", 0xFFF2B705),
            CarColour("menta", "Menta", 0xFF3BAE8A),
            CarColour("grafito", "Grafito", 0xFF3A3F48),
            CarColour("blanco", "Blanco", 0xFFF2F2EE),
        )
        fun byId(id: String): CarColour = all.firstOrNull { it.id == id } ?: all.first()
    }
}

/** Platform storage for the progress log. Implemented by each app. */
interface ProgressStorage {
    fun load(): String?
    fun save(json: String)
}

/** Platform clock, so "two different days" uses the learner's own time zone. */
interface PlatformClock {
    fun nowMillis(): Long
    fun utcOffsetMinutes(atMillis: Long): Int
}

class InMemoryStorage(private var text: String? = null) : ProgressStorage {
    override fun load(): String? = text
    override fun save(json: String) { text = json }
}
