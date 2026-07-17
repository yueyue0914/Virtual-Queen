package com.dianziqueen.app

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

/** 短 Toast（默认不 Honorific，避免无界面 Context 额外开销）。 */
fun Context.toastShort(@StringRes resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}

fun Context.toastShort(message: CharSequence) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.toastLong(@StringRes resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
}

fun Context.toastLong(message: CharSequence) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

/**
 * 执行可能失败的操作：失败时打统一日志，不抛出。
 * @return 成功为 true
 */
inline fun runCatchingQueen(
    tag: String,
    action: String,
    block: () -> Unit,
): Boolean {
    return try {
        block()
        true
    } catch (e: SecurityException) {
        QueenLogger.w(tag, "$action: SecurityException ${e.message}", e)
        false
    } catch (e: IllegalArgumentException) {
        QueenLogger.w(tag, "$action: IllegalArgument ${e.message}", e)
        false
    } catch (e: IllegalStateException) {
        QueenLogger.w(tag, "$action: IllegalState ${e.message}", e)
        false
    } catch (e: Exception) {
        QueenLogger.e(tag, "$action failed", e)
        false
    }
}

/** 需要返回值的安全执行；失败返回 null。 */
inline fun <T> runCatchingQueenValue(
    tag: String,
    action: String,
    block: () -> T,
): T? {
    return try {
        block()
    } catch (e: SecurityException) {
        QueenLogger.w(tag, "$action: SecurityException ${e.message}", e)
        null
    } catch (e: IllegalArgumentException) {
        QueenLogger.w(tag, "$action: IllegalArgument ${e.message}", e)
        null
    } catch (e: IllegalStateException) {
        QueenLogger.w(tag, "$action: IllegalState ${e.message}", e)
        null
    } catch (e: Exception) {
        QueenLogger.e(tag, "$action failed", e)
        null
    }
}
