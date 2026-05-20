package com.dianziqueen.app

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 后台采集本应用 logcat 中 E 级日志（及未捕获崩溃），写入存储目录 [LOG_DIR_NAME]。
 * 无 App 内查看界面。
 */
object QueenErrorLogCollector {

    private const val TAG = "QueenErrorLog"
    private const val LOG_DIR_NAME = "Dianzinvwang"
    private const val LOG_FILE_PREFIX = "dianziqueen_error_"
    private const val LOG_FILE_SUFFIX = ".log"
    private const val MAX_FILE_BYTES = 5 * 1024 * 1024L

    private val started = AtomicBoolean(false)
    private val writeLock = Any()

    private lateinit var appContext: Context
    private var logDir: File? = null

    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val lineDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        appContext = context.applicationContext
        logDir = resolveLogDirectory(appContext)
        installUncaughtExceptionHandler()
        startLogcatReader()
    }

    private fun resolveLogDirectory(context: Context): File? {
        val candidates = listOf(
            File(Environment.getExternalStorageDirectory(), LOG_DIR_NAME),
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                LOG_DIR_NAME,
            ),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), LOG_DIR_NAME),
            File(context.getExternalFilesDir(null), LOG_DIR_NAME),
            File(context.filesDir, LOG_DIR_NAME),
        )
        for (dir in candidates) {
            try {
                if (dir.exists() || dir.mkdirs()) {
                    if (dir.isDirectory && dir.canWrite()) {
                        return dir
                    }
                }
            } catch (_: Exception) { }
        }
        return null
    }

    private fun installUncaughtExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordThrowable("UNCAUGHT", thread.name, throwable)
            } catch (_: Exception) { }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun startLogcatReader() {
        thread(name = "QueenErrorLogcat", isDaemon = true) {
            while (true) {
                try {
                    drainLogcatProcess()
                } catch (e: Exception) {
                    Log.w(TAG, "logcat reader stopped, retry in 8s: ${e.message}")
                    try {
                        Thread.sleep(8_000L)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
    }

    private fun drainLogcatProcess() {
        val pid = android.os.Process.myPid()
        val process = ProcessBuilder(
            "logcat",
            "-v",
            "threadtime",
            "--pid=$pid",
            "*:E",
        )
            .redirectErrorStream(true)
            .start()

        process.inputStream.bufferedReader().use { reader ->
            readLogcatLines(reader)
        }
        process.destroy()
    }

    private fun readLogcatLines(reader: BufferedReader) {
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val text = line ?: continue
            if (shouldCaptureLogcatLine(text)) {
                appendLine(text)
            }
        }
    }

    /** 本进程 *:E 输出中，再筛 dianziqueen 包名/标签相关行。 */
    private fun shouldCaptureLogcatLine(line: String): Boolean {
        if (!line.contains(" E/") && !line.contains("\tE ")) return false
        val lower = line.lowercase(Locale.US)
        return lower.contains("dianziqueen") ||
            lower.contains("com.dianziqueen.app") ||
            lower.contains("queenerrorlog") ||
            lower.contains("queen")
    }

    private fun recordThrowable(kind: String, threadName: String, throwable: Throwable) {
        val header = "${lineDateFormat.format(Date())} $kind/$threadName: ${throwable.javaClass.simpleName}: ${throwable.message}"
        appendLine(header)
        val stack = throwable.stackTraceToString()
        stack.lineSequence().forEach { appendLine(it) }
    }

    private fun appendLine(line: String) {
        val dir = logDir ?: resolveLogDirectory(appContext).also { logDir = it } ?: return
        val file = currentLogFile(dir) ?: return
        val stamped = if (line.length >= 18 && line[4] == '-' && line[7] == '-') {
            line
        } else {
            "${lineDateFormat.format(Date())} $line"
        }
        synchronized(writeLock) {
            try {
                rotateIfNeeded(file)
                FileOutputStream(file, true).use { fos ->
                    OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                        writer.append(stamped)
                        writer.append('\n')
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "write log failed: ${e.message}")
            }
        }
    }

    private fun currentLogFile(dir: File): File? {
        val name = LOG_FILE_PREFIX + fileDateFormat.format(Date()) + LOG_FILE_SUFFIX
        return File(dir, name)
    }

    private fun rotateIfNeeded(file: File) {
        if (file.exists() && file.length() >= MAX_FILE_BYTES) {
            val rotated = File(
                file.parentFile,
                file.nameWithoutExtension + "_${System.currentTimeMillis()}" + LOG_FILE_SUFFIX,
            )
            file.renameTo(rotated)
        }
    }
}
