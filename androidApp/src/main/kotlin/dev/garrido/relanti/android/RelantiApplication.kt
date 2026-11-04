package dev.garrido.relanti.android

import android.app.Application
import dev.garrido.relanti.core.PlatformClock
import dev.garrido.relanti.core.ProgressStorage
import dev.garrido.relanti.core.RelantiCore
import java.io.File
import java.util.TimeZone

class RelantiApplication : Application() {
    lateinit var core: RelantiCore
        private set

    override fun onCreate() {
        super.onCreate()
        val pack = assets.open("relanti-b.json").bufferedReader().use { it.readText() }
        core = RelantiCore(pack, FileStorage(File(filesDir, "progress.json")), AndroidClock)
    }
}

/** The progress log lives in one JSON file in the app's private storage. */
class FileStorage(private val file: File) : ProgressStorage {
    override fun load(): String? = if (file.exists()) file.readText() else null
    override fun save(json: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        tmp.renameTo(file)
    }
}

object AndroidClock : PlatformClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun utcOffsetMinutes(atMillis: Long): Int = TimeZone.getDefault().getOffset(atMillis) / 60_000
}
