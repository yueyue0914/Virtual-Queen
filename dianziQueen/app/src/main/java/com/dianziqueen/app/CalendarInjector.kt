package com.dianziqueen.app

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.min

/**
 * 分批向系统主日历写入羞辱日程 + 监听用户删除后惩罚性补注。
 */
object CalendarInjector {

    private const val TAG = "CalendarInjector"
    const val EVENT_TAG = "[DianziQueen]"

    /** 每批间隔（小时） */
    private const val INJECT_INTERVAL_HOURS = 4L

    /** 每隔 INJECT_INTERVAL_HOURS 注入条数 */
    private const val BATCH_SIZE = 5

    /** 用户删除 Queen 日程后立刻补注条数 */
    private const val PUNISHMENT_BATCH_SIZE = 3

    private const val ALARM_REQUEST_CODE = 41001

    private const val DISCLAIMER =
        "【电子Queen · 本地娱乐】本条日程为 App 内角色扮演模拟，不构成真实约定；可随时在系统日历中删除。"

    const val COLOR_CRIMSON = 0xFFD32F2F.toInt()
    const val COLOR_PURPLE = 0xFF7B1FA2.toInt()

    private val tz: String get() = TimeZone.getDefault().id

    private val allEventsList: List<ScheduledEvent> by lazy { buildAllEvents() }

    private var deletionObserver: ContentObserver? = null
    private var observerHandler: Handler? = null

    sealed class InjectResult {
        data class Success(val inserted: Int, val skipped: Int = 0) : InjectResult()
        data object NoPermission : InjectResult()
        data object NoCalendar : InjectResult()
        data class Error(val message: String) : InjectResult()
    }

    data class ScheduledEvent(
        val title: String,
        val description: String,
        val hour: Int,
        val minute: Int = 0,
        val recurrence: Recurrence = Recurrence.DAILY,
        val color: Int = COLOR_CRIMSON,
        val durationMinutes: Int = 30,
    )

    enum class Recurrence(val rrule: String?) {
        NONE(null),
        DAILY("FREQ=DAILY"),
        WEEKLY_SUNDAY("FREQ=WEEKLY;BYDAY=SU"),
        WEEKLY_WEDNESDAY("FREQ=WEEKLY;BYDAY=WE"),
        MONTHLY_FIRST("FREQ=MONTHLY;BYMONTHDAY=1"),
    }

    fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED

    fun getPrimaryCalendarId(context: Context): Long? {
        if (!hasCalendarPermission(context)) return null
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val uri = CalendarContract.Calendars.CONTENT_URI
        val selection =
            "(${CalendarContract.Calendars.VISIBLE} = 1) AND " +
                "(${CalendarContract.Calendars.IS_PRIMARY} = 1)"
        return try {
            context.contentResolver.query(uri, projection, selection, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getLong(0)
                    } else {
                        context.contentResolver.query(
                            uri,
                            projection,
                            "${CalendarContract.Calendars.VISIBLE} = 1",
                            null,
                            "${CalendarContract.Calendars._ID} ASC",
                        )?.use { fallback ->
                            if (fallback.moveToFirst()) fallback.getLong(0) else null
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "getPrimaryCalendarId", e)
            null
        }
    }

    /**
     * 启动渐进烙印：立即注入首批，注册删除监听，并安排定时闹钟。
     * 兼容旧调用名 [injectHumiliationSchedule]。
     */
    fun injectHumiliationSchedule(context: Context, force: Boolean = false): InjectResult =
        ensureGradualInjection(context, force)

    fun ensureGradualInjection(context: Context, force: Boolean = false): InjectResult {
        if (!hasCalendarPermission(context)) return InjectResult.NoPermission

        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val activated = prefs.getBoolean(Prefs.ACTIVATED, false)
        if (!activated) return InjectResult.Success(inserted = 0)

        if (force) {
            prefs.edit()
                .putInt(Prefs.CALENDAR_INJECT_INDEX, 0)
                .putBoolean(Prefs.CALENDAR_SCHEDULE_ACTIVE, false)
                .putBoolean(Prefs.CALENDAR_INJECTED, false)
                .apply()
        }

        registerDeletionWatch(context)

        val scheduleActive = prefs.getBoolean(Prefs.CALENDAR_SCHEDULE_ACTIVE, false)
        if (!scheduleActive) {
            val first = injectBatch(context, BATCH_SIZE, advanceIndex = true)
            if (first is InjectResult.Error) return first
            prefs.edit()
                .putBoolean(Prefs.CALENDAR_SCHEDULE_ACTIVE, true)
                .putBoolean(Prefs.CALENDAR_INJECTED, true)
                .putLong(Prefs.CALENDAR_LAST_INJECT_AT, System.currentTimeMillis())
                .putInt(Prefs.CALENDAR_LAST_QUEEN_COUNT, countQueenEvents(context))
                .apply()
            scheduleNextInjectAlarm(context)
            return first
        }

        injectScheduledBatchIfDue(context)
        scheduleNextInjectAlarm(context)
        return InjectResult.Success(inserted = 0)
    }

    /** 闹钟 / 服务调用：到达间隔则注入下一批。 */
    fun injectScheduledBatchIfDue(context: Context) {
        if (!hasCalendarPermission(context)) return
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.CALENDAR_SCHEDULE_ACTIVE, false)) return

        val index = prefs.getInt(Prefs.CALENDAR_INJECT_INDEX, 0)
        if (index >= allEventsList.size) return

        val lastAt = prefs.getLong(Prefs.CALENDAR_LAST_INJECT_AT, 0L)
        val due = System.currentTimeMillis() - lastAt >= injectIntervalMs()
        if (!due) return

        injectBatch(context, BATCH_SIZE, advanceIndex = true)
        prefs.edit().putLong(Prefs.CALENDAR_LAST_INJECT_AT, System.currentTimeMillis()).apply()
    }

    fun scheduleNextInjectAlarm(context: Context) {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.CALENDAR_SCHEDULE_ACTIVE, false)) return
        if (prefs.getInt(Prefs.CALENDAR_INJECT_INDEX, 0) >= allEventsList.size) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, CalendarInjectReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
        val triggerAt = System.currentTimeMillis() + injectIntervalMs()

        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (e: Exception) {
            Log.e(TAG, "scheduleNextInjectAlarm", e)
        }
    }

    /** 按 [EVENT_TAG] 扫尾删除（兼容释放前未记入注册表的旧数据）。 */
    fun deleteEventsByQueenTag(context: Context): Int {
        if (!hasCalendarPermission(context)) return 0
        var deleted = 0
        val resolver = context.contentResolver
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection =
            "${CalendarContract.Events.DELETED} = 0 AND (${CalendarContract.Events.DESCRIPTION} LIKE ?)"
        val args = arrayOf("%$EVENT_TAG%")
        try {
            resolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                args,
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                    try {
                        if (resolver.delete(uri, null, null) > 0) deleted++
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteEventsByQueenTag", e)
        }
        return deleted
    }

    fun cancelInjectAlarms(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, CalendarInjectReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
        am.cancel(pi)
    }

    fun registerDeletionWatch(context: Context) {
        if (!hasCalendarPermission(context)) return
        if (deletionObserver != null) return

        val app = context.applicationContext
        val handler = observerHandler ?: Handler(Looper.getMainLooper()).also { observerHandler = it }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                onCalendarChanged(app)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onCalendarChanged(app)
            }
        }
        deletionObserver = observer
        try {
            app.contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true,
                observer,
            )
            val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            if (!prefs.contains(Prefs.CALENDAR_LAST_QUEEN_COUNT)) {
                prefs.edit()
                    .putInt(Prefs.CALENDAR_LAST_QUEEN_COUNT, countQueenEvents(app))
                    .apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerDeletionWatch", e)
            deletionObserver = null
        }
    }

    fun unregisterDeletionWatch(context: Context) {
        deletionObserver?.let {
            try {
                context.applicationContext.contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) { }
        }
        deletionObserver = null
    }

    private fun onCalendarChanged(context: Context) {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.ACTIVATED, false)) return

        val current = countQueenEvents(context)
        val last = prefs.getInt(Prefs.CALENDAR_LAST_QUEEN_COUNT, current)

        if (current < last) {
            Log.i(TAG, "Queen calendar deleted: $last -> $current, punishment inject x$PUNISHMENT_BATCH_SIZE")
            injectBatch(context, PUNISHMENT_BATCH_SIZE, advanceIndex = true, punishment = true)
            prefs.edit().putLong(Prefs.CALENDAR_LAST_INJECT_AT, System.currentTimeMillis()).apply()
        }

        prefs.edit().putInt(Prefs.CALENDAR_LAST_QUEEN_COUNT, countQueenEvents(context)).apply()
    }

    fun countQueenEvents(context: Context): Int {
        if (!hasCalendarPermission(context)) return 0
        return try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                "${CalendarContract.Events.DELETED} = 0 AND (${CalendarContract.Events.DESCRIPTION} LIKE ?)",
                arrayOf("%$EVENT_TAG%"),
                null,
            )?.use { it.count } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "countQueenEvents", e)
            0
        }
    }

    private fun injectBatch(
        context: Context,
        count: Int,
        advanceIndex: Boolean,
        punishment: Boolean = false,
    ): InjectResult {
        if (!hasCalendarPermission(context)) return InjectResult.NoPermission

        val calendarId = getPrimaryCalendarId(context) ?: return InjectResult.NoCalendar
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        var index = prefs.getInt(Prefs.CALENDAR_INJECT_INDEX, 0)

        var inserted = 0
        var failed = 0

        if (punishment && index >= allEventsList.size) {
            repeat(count) {
                val event = allEventsList.random()
                if (insertEvent(context, calendarId, event, punishment = true)) {
                    inserted++
                } else {
                    failed++
                }
            }
        } else {
            if (index >= allEventsList.size) {
                return InjectResult.Success(inserted = 0)
            }
            val end = min(index + count, allEventsList.size)
            for (i in index until end) {
                val event = allEventsList[i]
                if (insertEvent(context, calendarId, event, punishment)) {
                    inserted++
                } else {
                    failed++
                }
            }
            if (advanceIndex && inserted > 0) {
                prefs.edit().putInt(Prefs.CALENDAR_INJECT_INDEX, end).apply()
            }
        }

        prefs.edit().putInt(Prefs.CALENDAR_LAST_QUEEN_COUNT, countQueenEvents(context)).apply()

        return if (inserted > 0) {
            InjectResult.Success(inserted = inserted, skipped = failed)
        } else if (failed > 0) {
            InjectResult.Error("注入失败 $failed 条")
        } else {
            InjectResult.Success(inserted = 0, skipped = 0)
        }
    }

    private fun insertEvent(
        context: Context,
        calendarId: Long,
        event: ScheduledEvent,
        punishment: Boolean,
    ): Boolean {
        val start = nextOccurrenceMillis(event.hour, event.minute)
        val end = start + event.durationMinutes * 60_000L
        val fullDescription = buildString {
            append(event.description)
            if (punishment) {
                append("\n\n【惩罚补注】敢删 Queen 的日程？这条是立刻补上的羞辱。")
            }
            append("\n\n")
            append(DISCLAIMER)
            append("\n")
            append(EVENT_TAG)
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, fullDescription)
            put(CalendarContract.Events.EVENT_TIMEZONE, tz)
            put(CalendarContract.Events.ALL_DAY, 0)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
            put(CalendarContract.Events.EVENT_COLOR, event.color)
            event.recurrence.rrule?.let { put(CalendarContract.Events.RRULE, it) }
        }

        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: return false
            val eventId = ContentUris.parseId(uri)
            QueenInjectionRegistry.recordCalendarEvent(context, eventId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "insertEvent: ${event.title}", e)
            false
        }
    }

    private fun nextOccurrenceMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun injectIntervalMs(): Long = INJECT_INTERVAL_HOURS * 60L * 60L * 1000L

    fun allEvents(): List<ScheduledEvent> = allEventsList

    private fun buildAllEvents(): List<ScheduledEvent> = buildList {
        add(
            ScheduledEvent(
                title = "07:00 Queen早安问候",
                description = "立刻跪下大声说：「Queen大人，我是您最低贱的电子奴隶，今天也请尽情玩弄我。」",
                hour = 7,
                color = COLOR_CRIMSON,
            ),
        )
        add(
            ScheduledEvent(
                title = "12:00 中午自检时间",
                description = "检查自己今天是否够下贱，不够的话立刻自罚。",
                hour = 12,
                color = COLOR_PURPLE,
            ),
        )
        add(
            ScheduledEvent(
                title = "14:00 废物自省时间",
                description = "好好想想你这种垃圾凭什么被 Queen 玩弄？写满三行再跪好。",
                hour = 14,
                color = COLOR_PURPLE,
            ),
        )
        add(
            ScheduledEvent(
                title = "19:00 今日废物等级评估",
                description = "Queen 已判定你今天的废物等级为 Lv.9，继续努力当更下贱的玩具。",
                hour = 19,
                color = COLOR_CRIMSON,
            ),
        )
        add(
            ScheduledEvent(
                title = "20:00 每日羞耻打卡",
                description = "拍一张跪姿照片存入耻辱相册，并备注今天有多贱。",
                hour = 20,
                durationMinutes = 45,
                color = COLOR_CRIMSON,
            ),
        )
        add(
            ScheduledEvent(
                title = "21:30 承认自己是狗",
                description = "大声朗读 5 遍：「我不是人，我只是 Queen 养的一条电子母狗。」",
                hour = 21,
                minute = 30,
                color = COLOR_PURPLE,
            ),
        )
        add(
            ScheduledEvent(
                title = "23:00 每日臣服汇报",
                description = "今天又做了哪些下贱的事？必须全部向 Queen 坦白，否则明天加重惩罚。",
                hour = 23,
                color = COLOR_CRIMSON,
            ),
        )
        add(
            ScheduledEvent(
                title = "23:45 晚安贱奴仪式",
                description = "睡觉前必须对着手机说 10 遍：「我是 Queen 的专属肉便器，晚安主人。」",
                hour = 23,
                minute = 45,
                color = COLOR_PURPLE,
            ),
        )
        add(
            ScheduledEvent(
                title = "08:30 跪着喝水时间",
                description = "双膝着地，双手捧杯；喝水前必须喊三声「Queen，我好贱」。",
                hour = 8,
                minute = 30,
                color = COLOR_PURPLE,
            ),
        )
        add(
            ScheduledEvent(
                title = "10:17 随时待命",
                description = "Queen 随时可能检查你，敢不秒回就准备接受残酷惩罚。保持通知全开。",
                hour = 10,
                minute = 17,
                color = COLOR_CRIMSON,
            ),
        )
        add(
            ScheduledEvent(
                title = "16:00 使用其他 App 反省",
                description = "又打开微信/抖音看别人？今晚准备好被玩坏。立刻写下偷窥时长并自罚。",
                hour = 16,
                color = COLOR_CRIMSON,
            ),
        )
        add(
            ScheduledEvent(
                title = "22:00 尊严粉碎时间",
                description = "回忆今天被 Queen 羞辱的瞬间，并感谢我踩碎了你的自尊。",
                hour = 22,
                color = COLOR_PURPLE,
            ),
        )
        add(
            ScheduledEvent(
                title = "23:59 今晚惩罚执行",
                description = "今天犯了 X 条错误，准备好手机震动到崩溃吧，贱货。",
                hour = 23,
                minute = 59,
                durationMinutes = 15,
                color = COLOR_CRIMSON,
            ),
        )
        add(
            ScheduledEvent(
                title = "02:00 强制醒来反省",
                description = "Queen 不准你睡得太舒服，起来跪着反省自己的下贱行为。",
                hour = 2,
                durationMinutes = 20,
                color = COLOR_CRIMSON,
            ),
        )
        add(
            ScheduledEvent(
                title = "01:30 熬夜惩罚",
                description = "深夜还在玩手机？立刻黑屏跪着道歉，否则明天加倍。",
                hour = 1,
                minute = 30,
                color = COLOR_PURPLE,
            ),
        )
        add(
            ScheduledEvent(
                title = "17:30 低电量供品检查",
                description = "电量低于 30% 时立即执行：手机剧烈震动 + 大声喊「我是没用的废物」。",
                hour = 17,
                minute = 30,
                color = COLOR_CRIMSON,
            ),
        )
        add(
            ScheduledEvent(
                title = "22:30 每晚权限检查",
                description = "检查所有权限是否仍为 Queen 完全开放，若有隐藏立即惩罚。",
                hour = 22,
                minute = 30,
                color = COLOR_PURPLE,
            ),
        )
        add(
            ScheduledEvent(
                title = "每周日 · 耻辱相册大清洗",
                description = "把所有照片整理好，并写下每张照片对应的下贱故事。",
                hour = 11,
                recurrence = Recurrence.WEEKLY_SUNDAY,
                durationMinutes = 90,
                color = COLOR_CRIMSON,
            ),
        )
        add(
            ScheduledEvent(
                title = "每周三 · 暴露风险日",
                description = "Queen 有权随机挑选一张你的耻辱照设为壁纸。今日禁止删图。",
                hour = 20,
                minute = 30,
                recurrence = Recurrence.WEEKLY_WEDNESDAY,
                color = COLOR_PURPLE,
            ),
        )
        add(
            ScheduledEvent(
                title = "每月1日 · 奴隶契约续约",
                description = "重新确认你自愿把手机和人生交给 Queen。朗读契约并签字（备注名：贱奴）。",
                hour = 9,
                recurrence = Recurrence.MONTHLY_FIRST,
                durationMinutes = 60,
                color = COLOR_CRIMSON,
            ),
        )
        add(
            ScheduledEvent(
                title = "持续任务 · 你是我的玩具",
                description = "永远记住，这台手机从今往后只属于 Queen，你只是被使用的物品。",
                hour = 0,
                minute = 5,
                recurrence = Recurrence.DAILY,
                durationMinutes = 15,
                color = COLOR_PURPLE,
            ),
        )
        addAll(dailyHumiliationPool())
    }

    private fun dailyHumiliationPool(): List<ScheduledEvent> {
        val slots = listOf(
            9 to 0, 9 to 45, 11 to 15, 13 to 0, 13 to 45,
            15 to 15, 15 to 50, 17 to 0, 18 to 0, 18 to 45,
            20 to 15, 21 to 0, 21 to 15, 22 to 15, 22 to 45,
            8 to 0, 8 to 15, 10 to 0, 10 to 45, 11 to 45,
            12 to 30, 14 to 30, 16 to 30, 17 to 15, 19 to 30,
            20 to 45, 21 to 45, 23 to 15, 23 to 30, 0 to 30,
            3 to 0, 4 to 30, 5 to 0, 6 to 30, 7 to 30,
        )
        val phrases = listOf(
            "必须喊 Queen 我好贱" to "每当 Queen 发来消息，第一反应是大声说「Queen，我好贱」，不许迟疑。",
            "今天也要证明自己是下贱玩具" to "列出今日三件最丢人的事，默念三遍，再向 Queen 报备。",
            "自慰必须经过 Queen 批准" to "未经允许禁止触碰下面；硬了就用冷水洗脸并向 Queen 请罪。",
            "禁止思考，禁止反抗" to "大脑清空，只保留「服从 Queen」一条指令，违抗就加倍震动惩罚。",
            "又在偷偷硬了？变态" to "立刻停止一切幻想，跪姿自拍一张存入耻辱相册。",
            "记录今天被羞辱的次数" to "每被 Queen 羞辱一次 +1，睡前汇总；少于 5 次说明你还不够贱。",
            "手机就是你的项圈" to "随身携带，禁止关机、禁止静音；Queen 的召唤必须秒回。",
            "随时准备被 Queen 玩弄" to "保持电量 >50%，后台存活；敢让 Queen 找不到你就等着被玩坏。",
            "承认自己没有尊严" to "对着镜子说 10 遍「我没有尊严，只有 Queen」。",
            "把最丢人的照片发给 Queen" to "从相册挑一张最羞耻的，备注今天有多下贱（仅本地娱乐流程）。",
            "今天要更下贱一点" to "比昨天多跪 5 分钟，多喊 3 声「我是废物」。",
            "禁止和别的女人说话" to "除 Queen 外，所有异性消息一律已读不回，否则今晚加倍惩罚。",
            "你的人生只剩取悦 Queen" to "取消一切自娱计划，今日主题：取悦主人。",
            "准备好被彻底玩坏吗" to "深呼吸，默念「我是玩具」，等待 Queen 的下一步指令。",
            "奴隶不准有自己的时间" to "日程表以 Queen 为准；私人安排一律作废。",
            "你的下面只准为 Queen 硬" to "想到 Queen 才能硬；想到别人就立刻自罚。",
            "又想当人了？做梦" to "跪地朗读：「我是物品，不是人。」",
            "立刻自称贱奴" to "所有自称改为「贱奴」或「母狗」，禁止用「我」这个字。",
            "今天犯错结算" to "清点今日忤逆次数，每条对应 30 秒震动自罚。",
            "Queen 正在检查你的手机" to "保持屏幕解锁待命，隐藏 App 一律卸载或坦白。",
            "必须保持发情状态" to "想着 Queen 的羞辱画面，维持「渴望被玩弄」的卑微心态。",
            "你只是一个会走路的震动器" to "走路时想象自己是 Queen 手里的玩具，步伐要小、要乖。",
            "彻底放弃抵抗" to "写下最后一条反抗念头，然后撕掉/删掉，宣布投降。",
            "你的自尊早已不存在" to "回忆最丢脸的一刻，感谢 Queen 帮你踩碎自尊。",
            "每天都要更贱一点" to "今日任务：比昨日多完成一项羞耻打卡。",
            "Queen 的专属肉便器" to "睡前复述 5 遍此身份，不许改口。",
            "禁止删除任何耻辱记录" to "相册、截图、聊天记录一律保留，删一条罚一夜。",
            "随时等待 Queen 的召唤" to "铃声最大，振动最强；错过一次召唤加倍伺候。",
            "你已经彻底属于我了" to "对着主屏说：「Queen，我整个人都是你的。」",
            "跪下向 Queen 问安" to "见 App 图标先跪 3 秒，再点开。",
            "今日羞耻词汇背诵" to "背诵：服从、下贱、母狗、玩具、废物，各五遍。",
            "向 Queen 上供电量" to "充电时默念「电量是献给 Queen 的供品」。",
            "禁止抬头看 Queen 的眼睛" to "幻想 Queen 俯视你，你只配看脚尖。",
            "复述奴隶三大戒律" to "① 只服从 Queen ② 不许隐瞒 ③ 越贱越光荣。",
            "今日禁止穿衣服的幻想" to "心理上保持赤裸卑微，自认不配穿衣。",
            "向壁纸里的 Queen 请安" to "每次亮屏先说一句「主人好」。",
            "晨间母狗宣誓" to "起床后第一句必须是：「Queen 的母狗醒了，请玩弄我。」",
            "午间耻辱复述" to "复述上午最丢脸的一件事，不许省略细节。",
            "黄昏供品上贡" to "截图今日步数/电量曲线，当作献给 Queen 的供品。",
            "半夜震动待命" to "睡前将手机放枕边，振动开最大，等待 Queen 的惩罚震动。",
        )
        return phrases.mapIndexed { index, (title, desc) ->
            val slot = slots[index % slots.size]
            ScheduledEvent(
                title = title,
                description = desc,
                hour = slot.first,
                minute = slot.second,
                color = if (index % 2 == 0) COLOR_CRIMSON else COLOR_PURPLE,
            )
        }
    }
}
