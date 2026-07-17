package com.dianziqueen.app

import android.util.Log

/**
 * 统一日志前缀，便于 logcat 过滤 `Queen-`。
 * 文件级错误采集仍由 [QueenErrorLogCollector] 负责。
 */
object QueenLogger {

    private const val PREFIX = "Queen-"

    fun d(tag: String, msg: String) {
        Log.d(PREFIX + tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(PREFIX + tag, msg)
    }

    fun w(tag: String, msg: String, e: Throwable? = null) {
        if (e != null) Log.w(PREFIX + tag, msg, e) else Log.w(PREFIX + tag, msg)
    }

    fun e(tag: String, msg: String, e: Throwable? = null) {
        if (e != null) Log.e(PREFIX + tag, msg, e) else Log.e(PREFIX + tag, msg)
    }
}
