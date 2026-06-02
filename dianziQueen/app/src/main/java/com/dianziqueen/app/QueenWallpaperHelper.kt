package com.dianziqueen.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object QueenWallpaperHelper {

    /** 是否具备设置壁纸权限（安装时授予；双通道检测避免误报）。 */
    fun hasSetWallpaperPermission(context: Context): Boolean {
        if (context.packageManager.checkPermission(
                Manifest.permission.SET_WALLPAPER,
                context.packageName,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SET_WALLPAPER,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
