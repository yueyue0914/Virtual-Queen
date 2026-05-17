package com.dianziqueen.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object QueenWallpaperHelper {

    /** 是否具备设置壁纸权限（安装时授予；仅检查，不会在此刻换壁纸）。 */
    fun hasSetWallpaperPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SET_WALLPAPER,
        ) == PackageManager.PERMISSION_GRANTED
}
