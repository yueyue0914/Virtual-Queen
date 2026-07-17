package com.dianziqueen.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * 每日随机时刻要求上缴「贱奴自拍凭证」；到点且当日未交则强制弹窗。
 */
object DailySelfieScheduler {

    private const val TAG = "DailySelfieScheduler"
    private const val ALARM_REQUEST_CODE = 41_003
    private const val WINDOW_START_HOUR = 8
    private const val WINDOW_END_HOUR = 23
    /** 上交权限并激活后，首次强制上缴自拍凭证的延迟 */
    const val ACTIVATION_SELFIE_DELAY_MS = 60_000L

    private val dayFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun todayKey(): String = LocalDate.now().format(dayFormatter)

    fun isActivated(context: Context): Boolean =
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getBoolean(Prefs.ACTIVATED, false)

    fun isSubmittedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        return prefs.getString(Prefs.QUEEN_DAILY_SELFIE_SUBMITTED_DAY, null) == todayKey()
    }

    fun shouldEnforce(context: Context): Boolean {
        if (!isActivated(context)) return false
        if (isSubmittedToday(context)) return false
        val triggerAt = ensureTodaySchedule(context)
        return System.currentTimeMillis() >= triggerAt
    }

    /** 确保今日随机触发时刻已生成，并预约闹钟。 */
    fun ensureTodaySchedule(context: Context): Long {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val today = todayKey()
        val scheduledDay = prefs.getString(Prefs.QUEEN_DAILY_SELFIE_SCHEDULE_DAY, null)
        if (scheduledDay == today) {
            return prefs.getLong(Prefs.QUEEN_DAILY_SELFIE_TRIGGER_AT, Long.MAX_VALUE)
        }
        val triggerAt = computeRandomTriggerMillis(today)
        prefs.edit()
            .putString(Prefs.QUEEN_DAILY_SELFIE_SCHEDULE_DAY, today)
            .putLong(Prefs.QUEEN_DAILY_SELFIE_TRIGGER_AT, triggerAt)
            .apply()
        scheduleAlarm(context, triggerAt)
        return triggerAt
    }

    fun markSubmittedToday(context: Context) {
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(Prefs.QUEEN_DAILY_SELFIE_SUBMITTED_DAY, todayKey())
            .apply()
        cancelAlarm(context)
    }

    private fun computeRandomTriggerMillis(today: String): Long {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.parse(today)
        val hour = WINDOW_START_HOUR + Random.nextInt(WINDOW_END_HOUR - WINDOW_START_HOUR)
        val minute = Random.nextInt(60)
        var trigger = date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        if (trigger <= now) {
            trigger = now + 3_000L
        }
        return trigger
    }

    fun scheduleAlarm(context: Context, triggerAt: Long) {
        if (!isActivated(context)) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val app = context.applicationContext
        val intent = Intent(app, DailySelfieAlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(app, ALARM_REQUEST_CODE, intent, flags)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (e: Exception) {
            Log.e(TAG, "scheduleAlarm", e)
        }
    }

    fun cancelAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val app = context.applicationContext
        val intent = Intent(app, DailySelfieAlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(app, ALARM_REQUEST_CODE, intent, flags)
        am.cancel(pi)
    }

    /**
     * 激活当日：在约 1 分钟后强制拉起今日自拍凭证（覆盖当日随机时刻）。
     * 完成此次上缴即视为今日已交，次日再按随机时刻执行。
     */
    fun scheduleActivationDaySelfie(
        context: Context,
        delayMs: Long = ACTIVATION_SELFIE_DELAY_MS,
    ) {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val today = todayKey()
        val triggerAt = System.currentTimeMillis() + delayMs
        prefs.edit()
            .putString(Prefs.QUEEN_DAILY_SELFIE_SCHEDULE_DAY, today)
            .putLong(Prefs.QUEEN_DAILY_SELFIE_TRIGGER_AT, triggerAt)
            .apply()
        scheduleAlarm(context, triggerAt)
    }
}
