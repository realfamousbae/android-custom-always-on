package com.realfamousbae.standby.dock.diag

import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Журнал диагностики для spike и отладки на устройстве: дублирует logcat и хранит
 * последние строки в файле приложения, чтобы их можно было посмотреть на экране
 * диагностики без компьютера. Файл лежит только на телефоне, в репозиторий не попадает.
 */
class DiagLog(private val file: File, private val maxLines: Int = 300) {

    private val lock = Any()

    fun log(message: String) {
        Log.i(TAG, message)
        val line = "${LocalDateTime.now().format(FORMAT)} $message"
        synchronized(lock) {
            val kept = readUnlocked().takeLast(maxLines - 1)
            file.writeText((kept + line).joinToString("\n"))
        }
    }

    fun lines(): List<String> = synchronized(lock) { readUnlocked() }

    fun clear() = synchronized(lock) { file.delete(); Unit }

    private fun readUnlocked(): List<String> =
        if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()

    companion object {
        const val TAG = "StandBy"
        private val FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    }
}
