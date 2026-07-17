package com.dianziqueen.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 华米 OV 等品牌权限设置页精准跳转合集。
 */
object RomPermissionUtils {

    fun openOverlaySettings(context: Context) {
        FloatingWindowPermissionHelper.openOverlaySettings(context)
    }

    fun openAppDetails(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ).apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                },
            )
        } catch (_: Exception) { }
    }

    /** 各品牌自启动管理（无法代码检测，仅跳转）。 */
    fun openAutoStartSettings(context: Context) {
        val pkg = context.packageName
        val intent = when {
            isXiaomi() -> Intent("miui.intent.action.OP_AUTO_START").apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra("extra_pkgname", pkg)
                putExtra("packageName", pkg)
            }
            isHuawei() -> Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                )
            }
            isOppo() -> Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                )
                putExtra("packageName", pkg)
            }
            isVivo() -> Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                )
                putExtra("packageName", pkg)
            }
            else -> appDetailsIntent(context)
        }
        launchOrFallback(context, intent, appDetailsIntent(context))
    }

    /** 打开厂商权限中心（自启动 / 后台 / 电池等综合入口）。 */
    fun openRomExtraPermissionHub(context: Context): Boolean {
        return when {
            isXiaomi() -> FloatingWindowPermissionHelper.tryOpenXiaomiExtraSettings(context)
            isHuawei() -> openHuaweiPermissionHub(context)
            isOppo() -> openOppoPermissionHub(context)
            isVivo() -> openVivoPermissionHub(context)
            else -> false
        }
    }

    fun openQueenPermissionHub(context: Context) {
        openOverlaySettings(context)
        if (!openRomExtraPermissionHub(context)) {
            openAutoStartSettings(context)
        }
    }

    private fun openHuaweiPermissionHub(context: Context): Boolean {
        val pkg = context.packageName
        return launchFirstResolved(
            context,
            listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                    )
                },
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.permissionmanager.ui.MainActivity",
                    )
                    putExtra("packageName", pkg)
                },
            ),
        )
    }

    private fun openOppoPermissionHub(context: Context): Boolean {
        val pkg = context.packageName
        return launchFirstResolved(
            context,
            listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    )
                    putExtra("packageName", pkg)
                },
                Intent().apply {
                    component = ComponentName(
                        "com.oplus.safecenter",
                        "com.oplus.safecenter.startupapp.StartupAppListActivity",
                    )
                    putExtra("packageName", pkg)
                },
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity",
                    )
                    putExtra("packageName", pkg)
                },
            ),
        )
    }

    private fun openVivoPermissionHub(context: Context): Boolean {
        val pkg = context.packageName
        return launchFirstResolved(
            context,
            listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                    )
                    putExtra("packageName", pkg)
                },
                Intent().apply {
                    component = ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
                    )
                },
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity",
                    )
                    putExtra("packagename", pkg)
                },
            ),
        )
    }

    private fun launchOrFallback(context: Context, primary: Intent, fallback: Intent) {
        if (!launchFirstResolved(context, listOf(primary))) {
            try {
                context.startActivity(fallback)
            } catch (_: Exception) { }
        }
    }

    private fun launchFirstResolved(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                val launch = intent.apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                if (launch.resolveActivity(context.packageManager) != null) {
                    context.startActivity(launch)
                    return true
                }
            } catch (_: Exception) { }
        }
        return false
    }

    private fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

    fun isXiaomi(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ||
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi")
    }

    fun isHuawei(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return brand.contains("huawei") || brand.contains("honor") ||
            manufacturer.contains("huawei")
    }

    fun isOppo(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme") ||
            manufacturer.contains("oppo")
    }

    fun isVivo(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return brand.contains("vivo") || brand.contains("iqoo") ||
            manufacturer.contains("vivo")
    }

    fun isDomesticRom(): Boolean = isXiaomi() || isHuawei() || isOppo() || isVivo()

    /** 小米/红米：应用详情 → 省电策略 → 无限制。 */
    fun openXiaomiPowerStrategySettings(context: Context): Boolean {
        if (!isXiaomi()) return false
        val pkg = context.packageName
        return launchFirstResolved(
            context,
            listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                    )
                    putExtra("package_name", pkg)
                    putExtra("pkg_name", pkg)
                    putExtra("extra_pkgname", pkg)
                    putExtra("packageName", pkg)
                },
                Intent().apply {
                    component = ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.PowerSettings",
                    )
                    putExtra("package_name", pkg)
                    putExtra("pkg_name", pkg)
                    putExtra("extra_pkgname", pkg)
                    putExtra("packageName", pkg)
                },
                Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").apply {
                    putExtra("extra_pkgname", pkg)
                    putExtra("packageName", pkg)
                },
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity",
                    )
                    putExtra("extra_pkgname", pkg)
                    putExtra("packageName", pkg)
                },
                appDetailsIntent(context),
            ),
        )
    }
}
