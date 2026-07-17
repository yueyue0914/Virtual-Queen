package com.dianziqueen.app



import android.annotation.SuppressLint

import android.content.Context

import android.content.Intent

import android.net.Uri

import android.os.PowerManager

import android.provider.Settings

import androidx.core.app.NotificationCompat



/**

 * 检测是否已关闭电池优化（允许后台高耗电）；未豁免时引导用户授权。

 */

object QueenBatteryHelper {



    private const val NOTIFY_ID_BATTERY = 2006



    fun isExemptFromBatteryOptimizations(context: Context): Boolean =

        RomPermissionProbe.isBatteryExempt(context)



    /** 小米：已选「不优化」但尚未完成「省电策略 → 无限制」时返回 true。 */

    fun needsXiaomiPowerStrategyStep(context: Context): Boolean {

        if (!RomPermissionUtils.isXiaomi()) return false

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false

        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) return false

        return !isExemptFromBatteryOptimizations(context)

    }



    /**

     * 弹出系统对本 App 的电池优化对话框（选「不优化」并点「完成」）。

     * 比打开优化列表更可靠，能正确写入 [PowerManager.isIgnoringBatteryOptimizations]。

     */

    @SuppressLint("BatteryLife")

    fun openBatteryExemptionRequest(context: Context): Boolean {

        val pkg = context.packageName

        try {

            context.startActivity(

                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {

                    data = Uri.parse("package:$pkg")

                    if (context !is android.app.Activity) {

                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    }

                },

            )

            return true

        } catch (_: Exception) { }

        return openBatteryOptimizationList(context)

    }



    /** 打开系统电池优化 App 列表（备用）。 */

    fun openBatteryOptimizationList(context: Context): Boolean {

        try {

            context.startActivity(

                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {

                    if (context !is android.app.Activity) {

                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    }

                },

            )

            return true

        } catch (_: Exception) { }

        openApplicationDetails(context)

        return false

    }



    /** 按机型引导：先系统对话框，小米第二步跳转省电策略。 */

    fun openBatteryExemptionSettings(context: Context) {

        if (needsXiaomiPowerStrategyStep(context)) {

            RomPermissionUtils.openXiaomiPowerStrategySettings(context)

        } else {

            openBatteryExemptionRequest(context)

        }

    }



    fun openApplicationDetails(context: Context) {

        try {

            context.startActivity(

                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {

                    data = Uri.parse("package:${context.packageName}")

                    if (context !is android.app.Activity) {

                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    }

                },

            )

        } catch (_: Exception) {

            context.startActivity(

                Intent(Settings.ACTION_SETTINGS).apply {

                    if (context !is android.app.Activity) {

                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    }

                },

            )

        }

    }



    fun notifyBatteryOptimizationRequired(context: Context) {

        if (!NotificationHelper.hasEarlyNotificationsReady(context)) return

        val open = Intent(context, MainActivity::class.java).apply {

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            putExtra(MainActivity.EXTRA_OPEN_BATTERY_SETTINGS, true)

        }

        val pi = android.app.PendingIntent.getActivity(

            context,

            0,

            open,

            android.app.PendingIntent.FLAG_UPDATE_CURRENT or

                android.app.PendingIntent.FLAG_IMMUTABLE,

        )

        val n = NotificationCompat.Builder(context, DianziQueenApp.CHANNEL_TEASING)

            .setSmallIcon(android.R.drawable.ic_dialog_alert)

            .setContentTitle(context.getString(R.string.battery_notify_title))

            .setContentText(context.getString(R.string.battery_notify_text))

            .setStyle(

                NotificationCompat.BigTextStyle()

                    .bigText(context.getString(R.string.battery_notify_big_text)),

            )

            .setContentIntent(pi)

            .setAutoCancel(true)

            .setPriority(NotificationCompat.PRIORITY_HIGH)

            .build()

        NotificationHelper.notify(context, NOTIFY_ID_BATTERY, n)

    }



    fun cancelBatteryNotification(context: Context) {

        NotificationHelper.cancel(context, NOTIFY_ID_BATTERY)

    }

}

