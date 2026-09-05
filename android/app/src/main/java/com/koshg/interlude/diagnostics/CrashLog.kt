package com.koshg.interlude.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Keeps the stack trace of the last crash so it can be read back inside the app.
 *
 * This app is installed from a GitHub release, not from Play, so there are no Play Console
 * vitals and no automatic crash reports -- when something goes wrong the only evidence is a
 * phone that closed itself, which is not enough to fix anything from. Recording the trace turns
 * "it crashes sometimes" into a stack trace someone can act on.
 *
 * Deliberately not in the Auto Backup allow-list (see res/xml/data_extraction_rules.xml, which
 * names files explicitly): a crash trace describes this install and should not travel to the
 * next device. The file lives in the app's private storage and goes away with the app.
 */
class CrashLog(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    /** The recorded trace, or null when nothing has crashed since it was last cleared. */
    fun read(): String? = runCatching {
        file.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }

    private fun record(throwable: Throwable) {
        runCatching {
            val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
            file.writeText("${LocalDateTime.now().format(STAMP)}\n\n$stack")
        }
    }

    companion object {
        private const val FILE_NAME = "last-crash.txt"
        private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /**
         * Records the trace and then hands the crash on to whatever handler was already there.
         *
         * Chaining rather than replacing matters: the default handler is what actually ends the
         * process and shows the system dialog. Swallowing the exception here would leave the app
         * running in a broken, half-torn-down state, which is worse than crashing.
         */
        fun install(context: Context) {
            val log = CrashLog(context.applicationContext)
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                log.record(throwable)
                previous?.uncaughtException(thread, throwable)
            }
        }
    }
}
