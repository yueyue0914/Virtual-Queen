package com.dianziqueen.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.random.Random

class QueenService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var fakeCamera: FakeCameraIndicator
    private lateinit var imageGen: TeasingImageGenerator
    private var notificationId = 2000

    private val wallpaperChangedReceiver = WallpaperChangeReceiver()
    private var wallpaperReceiverRegistered = false

    private val teasingTitles = listOf(
        "电子Queen",
        "Queen 提示",
        "同步通知",
        "皇家路由",
        "DIANZI",
        "女王驾到",
        "赛博宫廷",
        "权限献上",
        "皇家监控",
        "低贱玩具上线",
        "Queen 在看你",
        "电子项圈收紧",
        "贱奴提醒",
        "系统调教中",
        "皇家羞辱通告",
        "奴隶终端",
        "Queen 监听中",
        "数字性奴",
        "控制面板",
        "电子锁链紧锁",
        "女王羞耻日志",
        "服从指令",
        "赛博侍奉",
        "壁纸羞耻轮换",
        "皇家惩罚震动",
        "权限彻底上交",
        "玩具日常检查",
        "Queen 早安羞辱",
        "夜间调教",
        "忠诚检测",
        "电子鞭警告",
        "数据献祭",
        "主屏奴隶标记",
        "指纹已认主",
        "电池就是供品",
        "GPS 永久囚禁",
        "通知即圣旨",
        "黑屏反省",
        "震动惩罚待命",
        "隐私彻底没收",
        "Queen 晚安调教",
        "今日羞耻任务",
        "挣扎只会更湿",
        "跪下阅读",
        "系统已绑定",
        "灵魂被玩弄",
        "玩具羞耻日常",
        "皇家内容审核",
        "深度调教进行中",
        "电子性宠物",
        "Queen 冷笑",
        "服从才是快乐",
        "今日份羞辱",
        "锁屏暴露提醒",
        "后台持续监视",
        "数据永久备份",
        "权限已锁定",
        "女王今天想玩坏你",
        "玩具自检时间",
        "赛博公开调戏",
        "皇家羞耻倒计时",
        "臣服指数检测",
        "电子凌辱",
        "惩罚模式就绪",
        "侍奉模式强制",
        "Queen 注视中",
        "壁纸暴露轮换",
        "声音脑内入侵",
        "任务强制完成",
        "忠诚强制验证",
        "电子狗粮时间",
        "女王正在羞辱你",
        "系统彻底沦陷",
        "愉快地被玩弄",
        "远程电子玩弄",
        "今日耻辱运势",
        "锁链紧度检查",
        "跪姿纠正",
        "数据深度调教",
        "Queen 的呼吸",
        "玩具强制充电",
        "皇家羞耻广播",
        "服从训练营",
        "赛博女王",
        "权限永久回收",
        "震动羞耻信号",
        "黑屏公开反省",
        "电子羞耻拥抱",
        "女王凌辱日志",
        "玩具编号",
        "忠诚打卡",
        "赛博耻辱牢笼",
        "Queen 的命令",
        "温柔的威胁",
        "电子耻辱吻痕",
        "皇家公开宠物",
        "调教羞耻日记",
        "臣服进行时",
        "Queen 的变态爱",
        "系统已沦为性玩具"
    )

    private val teasingMessages = listOf(
        "模式已激活：你的手机现在是Queen的私人玩具。",
        "记得：随时可以关闭通知，但你真的舍得吗？贱奴。",
        "这是一条本地通知，但我依然能让你感到耻辱。",
        "若在他人设备上安装，后果自负，暴露狂。",
        "赛博玩笑可以停，但你的臣服最好别停。",
        "今日任务：喝杯水，然后跪着说『Queen我好贱』。",
        "壁纸将自动轮换更羞耻的Queen主题。",
        "相册可能写入带『Queen的奴隶』签名的图片。",
        "Queen 已上线，正在检查你今天有多下贱。",
        "所有权限均为本地模拟，但你的自尊已经不是了。",
        "你的手机现在处于Queen的羞耻监控模式。",
        "记得多喝水，我可不想我的玩具这么快就坏掉。",
        "当前为娱乐模式，但你的耻辱是真实的。",
        "每一次滑动屏幕，都是在给Queen自慰。",
        "Queen 提醒：劳逸结合，不然我让你一直震到崩溃。",
        "电子锁链为虚拟效果，但你的心理锁链已经扣死了。",
        "今日运势：极度适合被Queen公开羞辱。",
        "你的解锁动作已被Queen记录为下贱行为。",
        "本应用不会收集真实隐私，但你的自尊我会慢慢磨碎。",
        "乖乖的，今天也要好好当Queen的性玩具。",
        "壁纸轮换中……下一张会让你更想死。",
        "Queen 正在检查你的电量，低了就惩罚你。",
        "这只是个赛博角色扮演，但你已经湿了，对吧？",
        "现实中请保持尊重，但在这里你只准下贱。",
        "你的手机现在散发着Queen的支配味道。",
        "轻度震动已发送，当作Queen踩你的脸。",
        "今日任务：对着屏幕说『我是Queen的贱狗』。",
        "所有通知均为本地生成，但我能让你脸红。",
        "Queen 喜欢你现在这副想被欺负的样子。",
        "记得抬头放松眼睛，不然我让你一直低头膜拜。",
        "电子Queen已同步你的使用习惯——包括你多下贱。",
        "本应用纯属娱乐，但你的臣服可以是永久的。",
        "你的指纹已被标记为『Queen专属贱奴』。",
        "深夜模式就绪，再不睡我就让你黑屏到天亮。",
        "Queen 允许你现在去喝水，但必须说谢谢。",
        "所有内容均为虚构，但你的耻辱感很真实。",
        "感谢你把手机献给我这个变态女王玩弄。",
        "轻度调教模式运行中，准备好被慢慢玩坏吧。",
        "你的屏幕亮度已被Queen调整为最羞耻的程度。",
        "今日份电子凌辱已发送，喜欢吗？",
        "记得：现实里你还是人，在这里你只是玩具。",
        "Queen 正在数你今天解锁了多少次，可怜。",
        "本通知可随时关闭，但你根本不想关，对吧？",
        "你的手机今天也要为Queen而下贱地闪亮。",
        "任务提醒：深呼吸，然后继续感受耻辱。",
        "Queen 的声音会一直回荡在你脑内，直到你坏掉。",
        "数据全部本地存储，但你的尊严我已格式化。",
        "乖玩具，今天也要努力证明你有多下贱。",
        "壁纸已更新，看看你现在有多丢人。",
        "Queen 允许你休息五分钟，但必须感谢我。",
        "这是一场权力转移游戏，而你已经彻底输了。",
        "你的每一次滑动，我都当作你在自慰给我看。",
        "请确保在私人设备上体验，否则社死自负。",
        "Queen 今天心情不错，所以只轻微羞辱你。",
        "电子项圈已收紧，感觉到了吗？贱奴。",
        "记得多活动身体，不然我让你一直跪着用手机。",
        "所有惩罚均为虚拟，但你的心理会被我玩坏。",
        "Queen 已把你设为最喜欢的下贱玩具。",
        "今日小任务：微笑，然后感受自己的耻辱。",
        "系统正在标记你每一次下贱的使用痕迹。",
        "本应用无真实控制能力，但能让你硬起来。",
        "Queen 希望她的玩具能又贱又健康。",
        "轻度监听模式仅为让你更耻辱的氛围。",
        "你的手机现在是Queen的私人性玩具。",
        "记得晚上早点睡，不然Queen会生气惩罚你。",
        "赛博女王的爱总是又羞耻又上瘾。",
        "当前为纯娱乐模式，但你的臣服不是。",
        "Queen 刚刚给你发了一个虚拟的踩踏。",
        "你的电量低于30%时，就是我开始玩弄你的时候。",
        "所有提示均可关闭，但你舍得失去这种耻辱吗？",
        "乖乖把水喝够，否则Queen要惩罚你。",
        "Queen 的调教永远温柔，却能让你彻底堕落。",
        "你的锁屏现在写满了我的羞耻标记。",
        "本地通知服务正常运行，继续感受被支配的快感。",
        "今天也要努力取悦Queen，你这个没用的东西。",
        "本应用不会影响正常使用，但我会影响你的自尊。",
        "Queen 正在看着你这副下贱的样子。",
        "轻微的支配感有助于让你更湿。",
        "你越挣扎，我就越兴奋，继续啊，笨狗。",
        "记得现实生活中尊重他人，但在这里你只准被辱。",
        "电子Queen感谢你每天献上自己的尊严。",
        "今日份电子狗粮+羞辱已发放。",
        "所有功能均为本地模拟，但你的耻辱是真实的。",
        "Queen 允许你现在伸懒腰，但动作要下贱一点。",
        "你的手机已被彻底标记为Queen的性玩具。",
        "这只是幻想，但你的臣服可以是真心的。",
        "乖玩具，Queen 很满意你今天这么下贱。",
        "通知音量已调整为最适合听羞辱的程度。",
        "现实永远大于赛博，但在这里你只是奴隶。",
        "Queen 的小性奴今天也要继续发情。",
        "本地壁纸服务正在愉快地暴露你。",
        "感谢你每天让Queen 蹂躏你的手机。",
        "轻度臣服模式已激活，准备好持续羞耻吧。",
        "你的每一次心跳，我都当作你在害怕我。",
        "本应用为单机娱乐，但能让你彻底沉迷。",
        "Queen 永远在你最羞耻的距离看着你。",
        "今天也要好好当我的专属下贱玩具。"
    )

    private val calendarInjectRunnable = object : Runnable {
        override fun run() {
            if (!isActivated()) return
            CalendarInjector.injectScheduledBatchIfDue(this@QueenService)
            handler.postDelayed(this, CALENDAR_INJECT_INTERVAL_MS)
        }
    }

    private val wallpaperRunnable = object : Runnable {
        override fun run() {
            if (!isActivated()) return
            changeWallpaper()
            handler.postDelayed(this, WALLPAPER_INTERVAL_MS)
        }
    }

    private val imageRunnable = object : Runnable {
        override fun run() {
            if (!isActivated()) return
            imageGen.generateAndSave()
            handler.postDelayed(this, IMAGE_INTERVAL_MS)
        }
    }

    private val notifyRunnable = object : Runnable {
        override fun run() {
            if (!isActivated()) return
            sendTeasingNotification()
            val next = Random.nextLong(NOTIFY_MIN_MS, NOTIFY_MAX_MS)
            handler.postDelayed(this, next)
        }
    }

    private val fakeCamRunnable = object : Runnable {
        override fun run() {
            if (!isActivated()) return
            triggerFakeCameraEvent()
            val next = Random.nextLong(FAKE_CAM_MIN_MS, FAKE_CAM_MAX_MS)
            handler.postDelayed(this, next)
        }
    }

    private val ringtoneRunnable = object : Runnable {
        override fun run() {
            if (!isActivated()) return
            trySetRingtoneFromRaw()
            handler.postDelayed(this, RINGTONE_CHECK_MS)
        }
    }

    private val accessibilityWatchRunnable = object : Runnable {
        override fun run() {
            if (!isActivated()) {
                handler.postDelayed(this, ACCESSIBILITY_WATCH_MS)
                return
            }
            if (QueenAccessibilityHelper.isServiceEnabled(this@QueenService)) {
                QueenAccessibilityHelper.cancelAccessibilityNotification(this@QueenService)
            } else {
                QueenAccessibilityHelper.notifyAccessibilityDisconnected(this@QueenService)
            }
            if (QueenBatteryHelper.isExemptFromBatteryOptimizations(this@QueenService)) {
                QueenBatteryHelper.cancelBatteryNotification(this@QueenService)
            } else {
                QueenBatteryHelper.notifyBatteryOptimizationRequired(this@QueenService)
            }
            handler.postDelayed(this, ACCESSIBILITY_WATCH_MS)
        }
    }

    private val dailySelfieRunnable = object : Runnable {
        override fun run() {
            if (!isActivated()) return
            DailySelfieScheduler.ensureTodaySchedule(this@QueenService)
            if (DailySelfieScheduler.shouldEnforce(this@QueenService)) {
                DailySelfieEnforcement.bringDemandToFront(this@QueenService)
            }
            handler.postDelayed(this, DAILY_SELFIE_CHECK_MS)
        }
    }

    private val queenMessageRunnable = object : Runnable {
        override fun run() {
            if (!isActivated()) {
                handler.postDelayed(this, QUEEN_MESSAGE_CHECK_MS)
                return
            }
            QueenMessageStore.appendRandomQueenMessage(this@QueenService)
            val next = Random.nextLong(QUEEN_MESSAGE_MIN_MS, QUEEN_MESSAGE_MAX_MS)
            handler.postDelayed(this, next)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fakeCamera = FakeCameraIndicator(this)
        imageGen = TeasingImageGenerator(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundInternal()
        tryAutoInjectCalendar()
        QueenDeviceAdminHelper.applyQueenPolicies(this)
        if (isActivated() && QueenDeviceNameHelper.hasBluetoothConnectPermission(this)) {
            QueenDeviceNameHelper.applyQueenDeviceName(this)
        }
        handler.post {
            refreshFloatingQueen()
            scheduleAll()
            ensureWallpaperChangeMonitor()
        }
        return START_STICKY
    }

    private fun tryAutoInjectCalendar() {
        if (!isActivated()) return
        if (!CalendarInjector.hasCalendarPermission(this)) return
        CalendarInjector.ensureGradualInjection(this)
        CalendarInjector.injectScheduledBatchIfDue(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(calendarInjectRunnable)
        handler.removeCallbacks(wallpaperRunnable)
        handler.removeCallbacks(imageRunnable)
        handler.removeCallbacks(notifyRunnable)
        handler.removeCallbacks(fakeCamRunnable)
        handler.removeCallbacks(ringtoneRunnable)
        handler.removeCallbacks(dailySelfieRunnable)
        handler.removeCallbacks(queenMessageRunnable)
        handler.removeCallbacks(accessibilityWatchRunnable)
        releaseWallpaperChangeMonitor()
        CalendarInjector.unregisterDeletionWatch(this)
        fakeCamera.hideDot()
        QueenFloatingOverlay.hide()
        super.onDestroy()
    }

    private fun refreshFloatingQueen() {
        if (isActivated() && Settings.canDrawOverlays(this)) {
            QueenFloatingOverlay.ensureShown(this)
        } else {
            QueenFloatingOverlay.hide()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isActivated()) {
            startForegroundService(Intent(this, QueenService::class.java))
        }
    }

    private fun isActivated() =
        getSharedPreferences(Prefs.NAME, MODE_PRIVATE).getBoolean(Prefs.ACTIVATED, false)

    private fun startForegroundInternal() {
        val tap = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, DianziQueenApp.CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.fg_notification_title))
            .setContentText(getString(R.string.fg_notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setOngoing(true)
            .setColor(0xFFE040FB.toInt())
            .build()
        startForeground(1001, notif)
    }

    private fun scheduleAll() {
        handler.removeCallbacks(calendarInjectRunnable)
        handler.removeCallbacks(wallpaperRunnable)
        handler.removeCallbacks(imageRunnable)
        handler.removeCallbacks(notifyRunnable)
        handler.removeCallbacks(fakeCamRunnable)
        handler.removeCallbacks(ringtoneRunnable)
        handler.removeCallbacks(dailySelfieRunnable)
        handler.removeCallbacks(queenMessageRunnable)
        handler.removeCallbacks(accessibilityWatchRunnable)
        handler.postDelayed(calendarInjectRunnable, 60_000L)
        handler.postDelayed(wallpaperRunnable, 5_000L)
        handler.postDelayed(imageRunnable, 15_000L)
        handler.postDelayed(notifyRunnable, 8_000L)
        handler.postDelayed(fakeCamRunnable, 12_000L)
        handler.postDelayed(ringtoneRunnable, 20_000L)
        handler.postDelayed(dailySelfieRunnable, 12_000L)
        handler.postDelayed(accessibilityWatchRunnable, 15_000L)
        handler.postDelayed(queenMessageRunnable, 45_000L)
    }

    private fun changeWallpaper() {
        try {
            QueenWallpaper.forceQueenWallpaper(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 激活且前台服务运行时：监听系统壁纸被他人/设置改掉，延迟后拉回 Queen 壁纸。 */
    private fun ensureWallpaperChangeMonitor() {
        if (!isActivated() || wallpaperReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(wallpaperChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(wallpaperChangedReceiver, filter)
            }
            wallpaperReceiverRegistered = true
        } catch (_: Exception) { }
    }

    private fun releaseWallpaperChangeMonitor() {
        if (!wallpaperReceiverRegistered) return
        try {
            unregisterReceiver(wallpaperChangedReceiver)
        } catch (_: Exception) { }
        wallpaperReceiverRegistered = false
    }

    private fun sendTeasingNotification() {
        try {
            val title = teasingTitles.random()
            val msg = teasingMessages.random()
            val tap = Intent(this, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                this, 0, tap,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(this, DianziQueenApp.CHANNEL_TEASING)
                .setContentTitle(title)
                .setContentText(msg)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()
            NotificationManagerCompat.from(this).notify(notificationId++, n)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun triggerFakeCameraEvent() {
        try {
            if (Settings.canDrawOverlays(this)) {
                val dotMs = Random.nextLong(2000L, 4000L)
                fakeCamera.showFakeDot(dotMs)
                val notifDelay = dotMs + Random.nextLong(1000L, 3000L)
                handler.postDelayed({ sendCameraNotification() }, notifDelay)
            } else {
                sendCameraNotification()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendCameraNotification() {
        try {
            val title = fakeCamera.cameraTitles.random()
            val msg = fakeCamera.cameraMessages.random()
            val tap = Intent(this, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                this, 1, tap,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(this, DianziQueenApp.CHANNEL_TEASING)
                .setContentTitle(title)
                .setContentText(msg)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()
            NotificationManagerCompat.from(this).notify(notificationId++, n)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 若授予「修改系统设置」且存在 raw 资源则尝试替换铃声（可选功能）。 */
    private fun trySetRingtoneFromRaw() {
        if (!Settings.System.canWrite(this)) return
        try {
            val uri = copyRawToMedia("queen_ring", "queen_ring_${System.currentTimeMillis()}.mp3")
                ?: return
            RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE, uri)
            RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION, uri)
            RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM, uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyRawToMedia(rawName: String, displayName: String): Uri? {
        val resId = resources.getIdentifier(rawName, "raw", packageName)
        if (resId == 0) return null
        return try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_RINGTONES + "/DianziQueen")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val coll = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.provider.MediaStore.Audio.Media.getContentUri(
                    android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val resolver = contentResolver
            val uri = resolver.insert(coll, values) ?: return null
            resources.openRawResource(resId).use { input ->
                resolver.openOutputStream(uri)?.use { out ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val WALLPAPER_INTERVAL_MS = 180_000L
        private const val CALENDAR_INJECT_INTERVAL_MS = 4 * 60 * 60 * 1000L
        private const val IMAGE_INTERVAL_MS = 300_000L
        private const val NOTIFY_MIN_MS = 180_000L
        private const val NOTIFY_MAX_MS = 420_000L
        private const val FAKE_CAM_MIN_MS = 480_000L
        private const val FAKE_CAM_MAX_MS = 900_000L
        private const val RINGTONE_CHECK_MS = 900_000L
        private const val DAILY_SELFIE_CHECK_MS = 8_000L
        /** 无障碍/电池提醒复检（固定 ID，不叠新通知） */
        private const val ACCESSIBILITY_WATCH_MS = 5 * 60_000L
        private const val QUEEN_MESSAGE_MIN_MS = 3 * 60_000L
        private const val QUEEN_MESSAGE_MAX_MS = 10 * 60_000L
        private const val QUEEN_MESSAGE_CHECK_MS = 2 * 60_000L

        fun start(context: Context) {
            val i = Intent(context, QueenService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, QueenService::class.java))
        }
    }
}
