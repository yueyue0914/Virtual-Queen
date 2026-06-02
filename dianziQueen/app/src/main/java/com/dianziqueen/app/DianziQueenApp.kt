package com.dianziqueen.app

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

class DianziQueenApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        registerScreenshotBlockForAllActivities()
        initQueenMessageLibrary()
        QueenErrorLogCollector.start(this)
        UninstallGuard.onAppColdStart(this)
    }

    /** 在此追加女王消息池文案（与 [QueenInsultLibrary] 默认库合并）。 */
    private fun initQueenMessageLibrary() {
        QueenInsultLibrary.addMessages(
            // "新加的消息1",
            // "新加的消息2",
            // "Queen今天心情很差，你要遭殃了。",
        )
    }

    /** 全 App 界面禁止系统截图与录屏（FLAG_SECURE）。 */
    private fun registerScreenshotBlockForAllActivities() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                applySecureWindow(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                applySecureWindow(activity)
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) {
                if (activity.applicationContext
                        .getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
                        .getBoolean(Prefs.ACTIVATED, false)
                ) {
                    QueenKeepAlive.ensureRunning(activity.applicationContext, notifyIfRestored = false)
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun applySecureWindow(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return

        val serviceCh = NotificationChannel(
            CHANNEL_SERVICE,
            getString(R.string.queen_service_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.queen_service_channel_desc)
            enableLights(true)
            lightColor = Color.RED
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val notifyAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /** IMPORTANCE_HIGH + 声音/振动 才容易触发悬浮横幅（heads-up）；渠道 id 换新以免继承旧「默认」等级。 */
        val teasingCh = NotificationChannel(
            CHANNEL_TEASING,
            getString(R.string.queen_teasing_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "定时提示（悬浮横幅与锁屏可见）"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), notifyAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 120, 60, 120)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        nm.createNotificationChannel(serviceCh)
        nm.createNotificationChannel(teasingCh)
    }

    companion object {
        const val CHANNEL_SERVICE = "queen_service_high_v1"
        /** 与旧版 `queen_teasing_channel` 区分，避免系统沿用用户/旧版的「静默」渠道设置 */
        const val CHANNEL_TEASING = "queen_teasing_heads_up_v1"
    }
}