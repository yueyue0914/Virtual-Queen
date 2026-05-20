package com.dianziqueen.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.MediaStore
import org.json.JSONArray

/**
 * 记录并清理本 App 注入到系统相册、系统日历的内容（不含 App 内加密相册）。
 */
object QueenInjectionRegistry {

    private const val KEY_CALENDAR_EVENT_IDS = "injected_calendar_event_ids_json"
    private const val KEY_GALLERY_URIS = "injected_gallery_uris_json"
    private const val GALLERY_FOLDER = "DianziQueen"
    private const val GALLERY_NAME_PREFIX = "queen_"

    data class CleanupResult(
        val calendarDeleted: Int,
        val galleryDeleted: Int,
    )

    fun recordCalendarEvent(context: Context, eventId: Long) {
        if (eventId <= 0L) return
        val ids = loadCalendarIds(context).toMutableSet()
        if (ids.add(eventId)) {
            saveCalendarIds(context, ids)
        }
    }

    fun recordGalleryUri(context: Context, uri: Uri) {
        val uris = loadGalleryUris(context).toMutableSet()
        if (uris.add(uri.toString())) {
            saveGalleryUris(context, uris)
        }
    }

    fun getCalendarCount(context: Context): Int = loadCalendarIds(context).size

    fun getGalleryCount(context: Context): Int = loadGalleryUris(context).size

    fun clearRecords(context: Context) {
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CALENDAR_EVENT_IDS)
            .remove(KEY_GALLERY_URIS)
            .apply()
    }

    /** 删除已记录 + 按标记扫尾的注入内容。 */
    fun deleteAllInjected(context: Context): CleanupResult {
        var calDeleted = 0
        var galleryDeleted = 0
        if (CalendarInjector.hasCalendarPermission(context)) {
            calDeleted += deleteRecordedCalendarEvents(context)
            calDeleted += CalendarInjector.deleteEventsByQueenTag(context)
        }
        galleryDeleted += deleteRecordedGalleryItems(context)
        galleryDeleted += deleteGallerySweep(context)
        clearRecords(context)
        return CleanupResult(calDeleted, galleryDeleted)
    }

    private fun deleteRecordedCalendarEvents(context: Context): Int {
        val resolver = context.contentResolver
        var deleted = 0
        for (id in loadCalendarIds(context)) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
            try {
                val rows = resolver.delete(uri, null, null)
                if (rows > 0) deleted++
            } catch (_: Exception) { }
        }
        return deleted
    }

    private fun deleteRecordedGalleryItems(context: Context): Int {
        val resolver = context.contentResolver
        var deleted = 0
        for (uriString in loadGalleryUris(context)) {
            try {
                val uri = Uri.parse(uriString)
                val rows = resolver.delete(uri, null, null)
                if (rows > 0) deleted++
            } catch (_: Exception) { }
        }
        return deleted
    }

    private fun deleteGallerySweep(context: Context): Int {
        val resolver = context.contentResolver
        var deleted = 0
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        } else {
            "${MediaStore.Images.Media.DATA} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        }
        val args = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("%$GALLERY_FOLDER%", "$GALLERY_NAME_PREFIX%")
        } else {
            arrayOf("%$GALLERY_FOLDER%", "$GALLERY_NAME_PREFIX%")
        }
        try {
            resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    try {
                        if (resolver.delete(uri, null, null) > 0) deleted++
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
        return deleted
    }

    private fun loadCalendarIds(context: Context): Set<Long> {
        val raw = prefs(context).getString(KEY_CALENDAR_EVENT_IDS, null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val id = arr.optLong(i, -1L)
                    if (id > 0L) add(id)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun saveCalendarIds(context: Context, ids: Set<Long>) {
        val arr = JSONArray()
        ids.sorted().forEach { arr.put(it) }
        prefs(context).edit().putString(KEY_CALENDAR_EVENT_IDS, arr.toString()).apply()
    }

    private fun loadGalleryUris(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_GALLERY_URIS, null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "").trim()
                    if (s.isNotEmpty()) add(s)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun saveGalleryUris(context: Context, uris: Set<String>) {
        val arr = JSONArray()
        uris.sorted().forEach { arr.put(it) }
        prefs(context).edit().putString(KEY_GALLERY_URIS, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
}
