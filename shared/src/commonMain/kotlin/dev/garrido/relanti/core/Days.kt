package dev.garrido.relanti.core

/** Calendar helpers on local day numbers (days since 1970-01-01). */
object Days {
    private const val MS_PER_DAY = 86_400_000L

    fun dayOf(millis: Long, utcOffsetMinutes: Int): Long =
        (millis + utcOffsetMinutes * 60_000L).floorDiv(MS_PER_DAY)

    /** Year, month (1-12) and day of month for a day number. */
    fun civil(day: Long): Triple<Int, Int, Int> {
        // Howard Hinnant's civil_from_days.
        val z = day + 719_468
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Triple((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
    }

    fun fromCivil(year: Int, month: Int, dayOfMonth: Int): Long {
        val y = if (month <= 2) year - 1L else year.toLong()
        val era = y.floorDiv(400L)
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + dayOfMonth - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    private val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
    private val monthsLong = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    /** "4 nov" */
    fun short(day: Long): String = civil(day).let { (_, m, d) -> "$d ${months[m - 1]}" }

    /** "12 de diciembre" */
    fun long(day: Long): String = civil(day).let { (_, m, d) -> "$d de ${monthsLong[m - 1]}" }

    /** Day number for an ISO date "2026-12-12", or -1. */
    fun parseIso(text: String): Long {
        val parts = text.trim().split("-")
        if (parts.size != 3) return -1
        val (y, m, d) = parts.map { it.toIntOrNull() ?: return -1 }
        if (m !in 1..12 || d !in 1..31) return -1
        return fromCivil(y, m, d)
    }

    fun iso(day: Long): String = civil(day).let { (y, m, d) ->
        "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }
}
