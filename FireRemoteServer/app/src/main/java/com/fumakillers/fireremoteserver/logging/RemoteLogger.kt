package com.fumakillers.fireremoteserver.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel(val label: String) {
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),
}

data class RecentLogRecord(
    val id: Long,
    val displayText: String,
    val lineCount: Int,
)

object RemoteLogger {
    private const val MAX_RECENT_LINES = 500
    private const val MAX_RECENT_STACK_LINES = 40
    private const val MAX_FILE_BYTES = 2L * 1024L * 1024L
    private const val MAX_ARCHIVE_FILES = 3
    private const val LOG_FILE_NAME = "fire-remote.log"

    private val displayTimestamp = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val fileTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val nextId = AtomicLong()
    private val recentRecords = ArrayDeque<RecentLogRecord>()
    private val listeners = CopyOnWriteArrayList<(RecentLogRecord) -> Unit>()
    private val fileExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "FireRemoteLogWriter").apply { isDaemon = true }
    }
    private val listenerExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "FireRemoteLogNotifier").apply { isDaemon = true }
    }
    private val recentLock = Any()

    @Volatile
    private var logDirectory: File? = null

    private var recentLineCount = 0

    fun initialize(context: Context) {
        if (logDirectory != null) return
        synchronized(this) {
            if (logDirectory == null) {
                logDirectory = File(context.applicationContext.filesDir, "logs")
            }
        }
    }

    fun debug(category: String, message: String) = log(LogLevel.DEBUG, category, message)

    fun info(category: String, message: String) = log(LogLevel.INFO, category, message)

    fun warn(category: String, message: String, error: Throwable? = null) =
        log(LogLevel.WARN, category, message, error)

    fun error(category: String, message: String, error: Throwable? = null) =
        log(LogLevel.ERROR, category, message, error)

    fun latestRecordId(): Long = nextId.get()

    fun subscribe(listener: (RecentLogRecord) -> Unit): List<RecentLogRecord> =
        synchronized(recentLock) {
            listeners.addIfAbsent(listener)
            recentRecords.toList()
        }

    fun removeListener(listener: (RecentLogRecord) -> Unit) {
        listeners.remove(listener)
    }

    private fun log(
        level: LogLevel,
        category: String,
        message: String,
        error: Throwable? = null,
    ) {
        writeLogcat(level, category, message, error)

        val now = LocalDateTime.now()
        val pendingRecord = createRecentRecord(now, level, category, message, error)
        val fileText = createFileText(now, level, category, message, error)
        synchronized(recentLock) {
            val record = pendingRecord.copy(id = nextId.incrementAndGet())
            recentRecords.addLast(record)
            recentLineCount += record.lineCount
            while (recentLineCount > MAX_RECENT_LINES && recentRecords.size > 1) {
                recentLineCount -= recentRecords.removeFirst().lineCount
            }
            val listenersToNotify = listeners.toList()
            listenerExecutor.execute {
                listenersToNotify.forEach { listener ->
                    try {
                        listener(record)
                    } catch (listenerError: RuntimeException) {
                        Log.e("FireRemoteLogger", "Recent log listener failed", listenerError)
                    }
                }
            }
            fileExecutor.execute { appendToFile(fileText) }
        }
    }

    private fun writeLogcat(
        level: LogLevel,
        category: String,
        message: String,
        error: Throwable?,
    ) {
        when (level) {
            LogLevel.DEBUG -> Log.d(category, message, error)
            LogLevel.INFO -> Log.i(category, message, error)
            LogLevel.WARN -> Log.w(category, message, error)
            LogLevel.ERROR -> Log.e(category, message, error)
        }
    }

    private fun createRecentRecord(
        now: LocalDateTime,
        level: LogLevel,
        category: String,
        message: String,
        error: Throwable?,
    ): RecentLogRecord {
        val prefix = "${now.format(displayTimestamp)} ${level.label.padEnd(5)} $category - "
        val lines = mutableListOf<String>()
        val messageLines = message.lineSequence().toList().ifEmpty { listOf("") }
        lines += prefix + messageLines.first()
        lines += messageLines.drop(1).map { "    $it" }
        if (error != null) {
            lines += error.stackTraceToString()
                .lineSequence()
                .take(MAX_RECENT_STACK_LINES)
                .map { "    $it" }
                .toList()
        }
        val boundedLines = lines.take(MAX_RECENT_LINES)
        return RecentLogRecord(
            id = 0,
            displayText = boundedLines.joinToString("\n"),
            lineCount = boundedLines.size,
        )
    }

    private fun createFileText(
        now: LocalDateTime,
        level: LogLevel,
        category: String,
        message: String,
        error: Throwable?,
    ): String = buildString {
        append(now.format(fileTimestamp))
        append(' ')
        append(level.label.padEnd(5))
        append(' ')
        append(category)
        append(" - ")
        append(message)
        appendLine()
        if (error != null) appendLine(error.stackTraceToString())
    }

    private fun appendToFile(text: String) {
        val directory = logDirectory ?: return
        try {
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Could not create ${directory.absolutePath}")
            }
            val current = File(directory, LOG_FILE_NAME)
            if (current.exists() && current.length() + text.toByteArray().size > MAX_FILE_BYTES) {
                rotateFiles(directory)
            }
            current.appendText(text)
        } catch (error: IOException) {
            Log.e("FireRemoteLogger", "Could not write persistent log", error)
        } catch (error: SecurityException) {
            Log.e("FireRemoteLogger", "Persistent log access was denied", error)
        }
    }

    private fun rotateFiles(directory: File) {
        File(directory, "$LOG_FILE_NAME.$MAX_ARCHIVE_FILES").delete()
        for (index in MAX_ARCHIVE_FILES - 1 downTo 1) {
            val source = File(directory, "$LOG_FILE_NAME.$index")
            if (source.exists()) source.renameTo(File(directory, "$LOG_FILE_NAME.${index + 1}"))
        }
        val current = File(directory, LOG_FILE_NAME)
        if (current.exists()) current.renameTo(File(directory, "$LOG_FILE_NAME.1"))
    }
}
