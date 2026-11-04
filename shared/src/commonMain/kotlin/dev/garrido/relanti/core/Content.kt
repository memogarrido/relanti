package dev.garrido.relanti.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A content pack: every etapa, tramo, question and truco the app can show.
 * The format is shared with the content pipeline (content/packs/relanti-b.json).
 * Unknown fields are ignored so the pipeline can add data before the apps use it.
 */
@Serializable
data class Pack(
    val format: String = "relanti-pack",
    val schemaVersion: Int,
    val contentVersion: Int,
    val licence: String = "B",
    /** True for sample content that is not checked against the official sources. */
    val placeholder: Boolean = false,
    val etapas: List<Etapa>,
    val tramos: List<Tramo>,
    val trucos: List<Truco> = emptyList(),
    val items: List<Item>,
    val attribution: Attribution? = null,
) {
    fun etapa(code: String): Etapa? = etapas.firstOrNull { it.code == code }
    fun tramo(code: String): Tramo? = tramos.firstOrNull { it.code == code }
    fun truco(id: String?): Truco? = id?.let { i -> trucos.firstOrNull { it.id == i } }
    fun item(id: String): Item? = itemsById[id]
    fun tramosOf(etapa: String): List<Tramo> = tramos.filter { it.etapa == etapa }.sortedBy { it.order }
    fun itemsOf(tramo: String): List<Item> = items.filter { it.tramo == tramo }
    fun etapaOfTramo(tramo: String): Etapa? = tramo(tramo)?.let { etapa(it.etapa) }
    fun etapaOfItem(item: Item): Etapa? = etapaOfTramo(item.tramo)

    /** Etapas in road order. */
    val road: List<Etapa> get() = etapas.sortedBy { it.order }

    private val itemsById: Map<String, Item> by lazy { items.associateBy { it.id } }

    companion object {
        const val SUPPORTED_SCHEMA = 1

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        /** Parses and validates a pack. Throws [PackException] when it can't be used. */
        fun parse(text: String): Pack {
            val pack = try {
                json.decodeFromString(serializer(), text)
            } catch (e: Exception) {
                throw PackException("El paquete de contenido no se puede leer: ${e.message}")
            }
            val problems = pack.validate()
            if (problems.isNotEmpty()) throw PackException(problems.joinToString("\n"))
            return pack
        }
    }

    /** Returns every problem found; an empty list means the pack is usable. */
    fun validate(): List<String> {
        val problems = mutableListOf<String>()
        if (format != "relanti-pack") problems += "format must be relanti-pack, was $format"
        if (schemaVersion > SUPPORTED_SCHEMA) problems += "schemaVersion $schemaVersion is newer than this app ($SUPPORTED_SCHEMA)"
        val etapaCodes = etapas.map { it.code }
        if (etapaCodes.toSet().size != etapaCodes.size) problems += "duplicate etapa codes"
        val tramoCodes = tramos.map { it.code }
        if (tramoCodes.toSet().size != tramoCodes.size) problems += "duplicate tramo codes"
        val ids = items.map { it.id }
        if (ids.toSet().size != ids.size) problems += "duplicate item ids"
        tramos.forEach { t ->
            if (t.etapa !in etapaCodes) problems += "tramo ${t.code}: unknown etapa ${t.etapa}"
            if (t.truco != null && trucos.none { it.id == t.truco }) problems += "tramo ${t.code}: unknown truco ${t.truco}"
        }
        items.forEach { i ->
            if (i.tramo !in tramoCodes) problems += "item ${i.id}: unknown tramo ${i.tramo}"
            if (i.options.size < 2) problems += "item ${i.id}: needs at least 2 options"
            if (i.answer !in i.options.indices) problems += "item ${i.id}: answer ${i.answer} out of range"
        }
        return problems
    }
}

class PackException(message: String) : Exception(message)

@Serializable
data class Etapa(
    val code: String,
    val order: Int,
    val name: String,
    val topic: String = "",
    /** ciudad, meseta, costa or montana: picks the La Ruta landscape. */
    val landscape: String = "ciudad",
    /** Part of the free carretera nacional. */
    val free: Boolean = false,
)

@Serializable
data class Tramo(
    val code: String,
    val etapa: String,
    val order: Int,
    val title: String,
    val truco: String? = null,
)

@Serializable
data class Truco(
    val id: String,
    val kind: String = "reading",
    val title: String,
    val body: String,
    val source: Source? = null,
)

@Serializable
data class Item(
    val id: String,
    val tramo: String,
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String = "",
    val image: String? = null,
    val novedad: Boolean = false,
    val source: Source? = null,
) {
    fun letter(index: Int): String = ('A' + index).toString()
}

@Serializable
data class Source(
    val law: String = "",
    val boe: String = "",
    val block: String = "",
    val ref: String? = null,
    val url: String? = null,
    val checkedOn: String? = null,
    val note: String? = null,
) {
    /** Short human label, e.g. "RGC, art. 85". */
    val label: String
        get() = ref ?: listOf(law, block.removePrefix("a").takeIf { it.isNotEmpty() }?.let { "art. $it" })
            .filterNotNull().filter { it.isNotEmpty() }.joinToString(", ")
}

@Serializable
data class Attribution(
    val boe: String? = null,
    val boeUrl: String? = null,
    @SerialName("consolidatedNotice") val consolidatedNotice: String? = null,
)
